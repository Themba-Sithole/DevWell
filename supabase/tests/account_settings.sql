-- Run after 20261017090000_member1_account_settings.sql. Rolls back fully.
begin;
create function pg_temp.check_true(ok boolean, label text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'FAILED: %', label; end if; end $$;
insert into auth.users (id, email, raw_user_meta_data) values
('10000000-0000-4000-8000-000000000011','sql-member1@example.invalid','{"name":"Member One"}'),
('10000000-0000-4000-8000-000000000012','sql-member2@example.invalid','{}');
update public."user" set role='manager', status='active', onboarding_done=true
where id='10000000-0000-4000-8000-000000000011';

set local role anon;
do $$ begin
  begin perform public.update_own_profile('Hacker'); raise exception 'FAILED: anon RPC'; exception when insufficient_privilege then null; end;
end $$;
reset role;

select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000011',true);
set local role authenticated;
do $$ begin
  begin perform public.update_own_profile('A'); raise exception 'FAILED: short name'; exception when sqlstate 'PT400' then null; end;
  begin perform public.update_own_profile(null); raise exception 'FAILED: null name'; exception when sqlstate 'PT400' then null; end;
end $$;
select pg_temp.check_true((select name='Member One' from public."user" where id=auth.uid()), 'rejected names do not mutate the profile');
select pg_temp.check_true((select (public.update_own_profile('  Renamed Member  ')->>'name') = 'Renamed Member'), 'RPC returns the trimmed name');
select pg_temp.check_true((select name='Renamed Member' from public."user" where id=auth.uid()), 'name update saved');
select pg_temp.check_true((select role='manager' and status='active' and onboarding_done from public."user" where id=auth.uid()), 'role and status untouched');
select public.update_own_profile(repeat('x',300));
select pg_temp.check_true((select length(name)=150 from public."user" where id=auth.uid()), 'overlong names are truncated');

select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000012',true);
select pg_temp.check_true((select name <> 'Renamed Member' from public."user" where id=auth.uid()), 'another member keeps their own name');
do $$ begin
  begin update public."user" set role='admin' where id=auth.uid(); raise exception 'FAILED: self promote via table'; exception when insufficient_privilege then null; end;
end $$;
reset role;
select pg_temp.check_true((select role='engineer' from public."user" where id='10000000-0000-4000-8000-000000000012'), 'role unchanged without grants');
select 'PASS: name editing RPC, anon lockout, field protection, validation and cross-member isolation' as result;
rollback;
