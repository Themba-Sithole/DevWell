-- Run after the migrations. All fixtures and mutations roll back.
begin;
create function pg_temp.check_true(ok boolean, label text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'FAILED: %', label; end if; end $$;
insert into auth.users (id, email) values
('12000000-0000-4000-8000-000000000001','sql-settings-manager-a@example.invalid'),
('12000000-0000-4000-8000-000000000002','sql-settings-manager-b@example.invalid'),
('12000000-0000-4000-8000-000000000003','sql-settings-engineer@example.invalid'),
('12000000-0000-4000-8000-000000000004','sql-settings-admin@example.invalid');
insert into public.company(id,name,status) values
('22000000-0000-4000-8000-000000000001','Settings Company A','active'),
('22000000-0000-4000-8000-000000000002','Settings Company B','active');
update public."user" set role='manager', status='active', onboarding_done=true,
  company_id='22000000-0000-4000-8000-000000000001' where id='12000000-0000-4000-8000-000000000001';
update public."user" set role='manager', status='active', onboarding_done=true,
  company_id='22000000-0000-4000-8000-000000000002' where id='12000000-0000-4000-8000-000000000002';
update public."user" set role='admin', status='active', onboarding_done=true where id='12000000-0000-4000-8000-000000000004';
insert into public.team(id,company_id,name,manager_id) values
('32000000-0000-4000-8000-000000000001','22000000-0000-4000-8000-000000000001','Settings Team','12000000-0000-4000-8000-000000000001'),
('32000000-0000-4000-8000-000000000002','22000000-0000-4000-8000-000000000001','Other Team','12000000-0000-4000-8000-000000000001'),
('32000000-0000-4000-8000-000000000003','22000000-0000-4000-8000-000000000002','Shared Name','12000000-0000-4000-8000-000000000002'),
('32000000-0000-4000-8000-000000000004','22000000-0000-4000-8000-000000000001',repeat('L',70),'12000000-0000-4000-8000-000000000001');
update public."user" set status='active', onboarding_done=true,
  company_id='22000000-0000-4000-8000-000000000001', team_id='32000000-0000-4000-8000-000000000001'
  where id='12000000-0000-4000-8000-000000000003';
select pg_temp.check_true((select timezone='Africa/Johannesburg' and check_in_cadence='Daily' and low_wellbeing_threshold=2.0
  from public.team where id='32000000-0000-4000-8000-000000000001'), 'new teams get default settings');
do $$ begin
  begin update public.team set check_in_cadence='Hourly' where id='32000000-0000-4000-8000-000000000001'; raise exception 'FAILED: bad cadence stored'; exception when check_violation then null; end;
  begin update public.team set low_wellbeing_threshold=4.5 where id='32000000-0000-4000-8000-000000000001'; raise exception 'FAILED: bad threshold stored'; exception when check_violation then null; end;
end $$;
set local role anon;
do $$ begin
  begin perform public.update_team_settings('32000000-0000-4000-8000-000000000001','Anon','Africa/Johannesburg','Daily',2.0); raise exception 'FAILED: anon RPC'; exception when insufficient_privilege then null; end;
end $$;
reset role;
select set_config('request.jwt.claim.sub','12000000-0000-4000-8000-000000000003',true);
set local role authenticated;
do $$ begin
  begin update public.team set name='Hijacked' where id='32000000-0000-4000-8000-000000000001'; raise exception 'FAILED: direct team update'; exception when insufficient_privilege then null; end;
  begin perform public.update_team_settings('32000000-0000-4000-8000-000000000001','Engineer Team','Africa/Johannesburg','Daily',2.0); raise exception 'FAILED: engineer update'; exception when sqlstate 'PT403' then null; end;
end $$;
select set_config('request.jwt.claim.sub','12000000-0000-4000-8000-000000000004',true);
do $$ begin
  begin perform public.update_team_settings('32000000-0000-4000-8000-000000000001','Admin Team','Africa/Johannesburg','Daily',2.0); raise exception 'FAILED: admin update'; exception when sqlstate 'PT403' then null; end;
end $$;
select set_config('request.jwt.claim.sub','12000000-0000-4000-8000-000000000002',true);
do $$ begin
  begin perform public.update_team_settings('32000000-0000-4000-8000-000000000001','Taken Over','Africa/Johannesburg','Daily',2.0); raise exception 'FAILED: other manager update'; exception when sqlstate 'PT403' then null; end;
end $$;
select set_config('request.jwt.claim.sub','12000000-0000-4000-8000-000000000001',true);
do $$ begin
  begin perform public.update_team_settings('32000000-0000-4000-8000-000000000001','   ','Africa/Johannesburg','Daily',2.0); raise exception 'FAILED: blank name'; exception when sqlstate 'PT400' then null; end;
  begin perform public.update_team_settings('32000000-0000-4000-8000-000000000001',repeat('A',61),'Africa/Johannesburg','Daily',2.0); raise exception 'FAILED: 61 character name'; exception when sqlstate 'PT400' then null; end;
  begin perform public.update_team_settings('32000000-0000-4000-8000-000000000001','Settings Team','Africa/Johannesburg','Hourly',2.0); raise exception 'FAILED: Hourly cadence'; exception when sqlstate 'PT400' then null; end;
  begin perform public.update_team_settings('32000000-0000-4000-8000-000000000001','Settings Team','Africa/Johannesburg','daily',2.0); raise exception 'FAILED: lowercase cadence'; exception when sqlstate 'PT400' then null; end;
  begin perform public.update_team_settings('32000000-0000-4000-8000-000000000001','Settings Team','Africa/Johannesburg','Daily',0.5); raise exception 'FAILED: threshold 0.5'; exception when sqlstate 'PT400' then null; end;
  begin perform public.update_team_settings('32000000-0000-4000-8000-000000000001','Settings Team','Africa/Johannesburg','Daily',4.5); raise exception 'FAILED: threshold 4.5'; exception when sqlstate 'PT400' then null; end;
  begin perform public.update_team_settings('32000000-0000-4000-8000-000000000001','Settings Team','Africa/Johannesburg','Daily',null); raise exception 'FAILED: null threshold'; exception when sqlstate 'PT400' then null; end;
  begin perform public.update_team_settings('32000000-0000-4000-8000-000000000001','Settings Team','Mars/Base','Daily',2.0); raise exception 'FAILED: unknown time zone'; exception when sqlstate 'PT400' then null; end;
  begin perform public.update_team_settings('32000000-0000-4000-8000-000000000001','Other Team','Africa/Johannesburg','Daily',2.0); raise exception 'FAILED: duplicate name'; exception when sqlstate 'PT409' then null; end;
end $$;
select pg_temp.check_true((select name='Settings Team' and timezone='Africa/Johannesburg' and check_in_cadence='Daily' and low_wellbeing_threshold=2.0
  from public.team where id='32000000-0000-4000-8000-000000000001'), 'rejected updates leave the team unchanged');
select pg_temp.check_true((select r->>'name'='Shared Name' and r->>'check_in_cadence'='Weekly' and (r->>'low_wellbeing_threshold')::numeric=3.5
  from (select public.update_team_settings('32000000-0000-4000-8000-000000000001','  Shared Name  ','Europe/London','Weekly',3.5) r) s),
  'manager saves trimmed settings; names are unique per company only');
select pg_temp.check_true((select name='Shared Name' and timezone='Europe/London' and check_in_cadence='Weekly' and low_wellbeing_threshold=3.5
  from public.team where id='32000000-0000-4000-8000-000000000001'), 'saved settings are stored');
select pg_temp.check_true((select public.update_team_settings('32000000-0000-4000-8000-000000000001','Shared Name','Europe/London','Weekly',3.5)->>'name'='Shared Name'),
  'identical retry succeeds');
select pg_temp.check_true((select public.update_team_settings('32000000-0000-4000-8000-000000000004',' '||repeat('L',70)||' ','Africa/Johannesburg','Weekly',2.5)->>'check_in_cadence'='Weekly'),
  'legacy long name can keep its name while other settings change');
select pg_temp.check_true((select name=repeat('L',70) and check_in_cadence='Weekly' and low_wellbeing_threshold=2.5
  from public.team where id='32000000-0000-4000-8000-000000000004'), 'legacy long name is preserved');
do $$ begin
  begin perform public.update_team_settings('32000000-0000-4000-8000-000000000004',repeat('M',70),'Africa/Johannesburg','Weekly',2.5); raise exception 'FAILED: new long name'; exception when sqlstate 'PT400' then null; end;
  begin perform public.update_team_settings('32000000-0000-4000-8000-000000000004',repeat('L',61),'Africa/Johannesburg','Weekly',2.5); raise exception 'FAILED: shortened name still over 60'; exception when sqlstate 'PT400' then null; end;
end $$;
reset role;
select 'PASS: defaults, table checks, privileges, manager-only updates, validation, duplicate names, legacy long names' as result;
rollback;
