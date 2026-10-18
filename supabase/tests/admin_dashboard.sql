-- Run after the migrations. Fixtures and all status changes are rolled back.
begin;
create function pg_temp.check_true(ok boolean, label text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'FAILED: %', label; end if; end $$;

insert into auth.users (id, email, raw_user_meta_data) values
('10000000-0000-4000-8000-000000000011','sql-admin-dashboard@example.invalid','{}'),
('10000000-0000-4000-8000-000000000012','sql-admin-engineer@example.invalid','{}'),
('10000000-0000-4000-8000-000000000013','sql-admin-other@example.invalid','{}'),
('10000000-0000-4000-8000-000000000014','sql-admin-target@example.invalid','{}');
insert into public.company(id, name, status)
values ('20000000-0000-4000-8000-000000000011','Admin test company','active');
insert into public.team(id, company_id, name)
values ('30000000-0000-4000-8000-000000000011','20000000-0000-4000-8000-000000000011','Admin test team');
update public."user"
set role='admin', status='active', onboarding_done=true
where id='10000000-0000-4000-8000-000000000011';
update public."user"
set status='active', onboarding_done=true, company_id='20000000-0000-4000-8000-000000000011',
  team_id='30000000-0000-4000-8000-000000000011'
where id='10000000-0000-4000-8000-000000000012';
update public."user"
set role='manager', status='active', onboarding_done=true, company_id='20000000-0000-4000-8000-000000000011'
where id='10000000-0000-4000-8000-000000000013';
update public."user"
set role='admin', status='active', onboarding_done=true
where id='10000000-0000-4000-8000-000000000014';
insert into public.project(user_id, name, deadline)
values ('10000000-0000-4000-8000-000000000012','Admin test project',current_date);
insert into public.check_in(user_id, responses)
values ('10000000-0000-4000-8000-000000000012','{"wellbeing":1,"note":"private"}');

select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000013',true);
set local role authenticated;
select pg_temp.check_true(
  (select count(*)=0 from public.check_in where user_id='10000000-0000-4000-8000-000000000012'),
  'manager cannot read an engineer check-in'
);
reset role;

select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000012',true);
set local role authenticated;
select pg_temp.check_true((select count(*)=1 from public.project), 'active user reads own project');
do $$ begin
  begin perform public.admin_dashboard(); raise exception 'FAILED: non-admin dashboard access'; exception when sqlstate 'PT403' then null; end;
  begin perform public.admin_set_user_status('10000000-0000-4000-8000-000000000013','suspended'); raise exception 'FAILED: non-admin status change'; exception when sqlstate 'PT403' then null; end;
end $$;
reset role;

select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000011',true);
set local role authenticated;
select pg_temp.check_true(
  (public.admin_dashboard()->'users') @> '[{"id":"10000000-0000-4000-8000-000000000012","email":"sql-admin-engineer@example.invalid"}]'::jsonb,
  'active admin sees account identity fields'
);
select pg_temp.check_true(
  (public.admin_dashboard()->'users')::text not ilike '%responses%' and
  (public.admin_dashboard()->'users')::text not ilike '%note%',
  'admin dashboard does not return wellbeing data'
);
do $$ begin
  begin perform public.admin_set_user_status('10000000-0000-4000-8000-000000000011','suspended'); raise exception 'FAILED: self suspension'; exception when sqlstate 'PT403' then null; end;
  begin perform public.admin_set_user_status('10000000-0000-4000-8000-000000000014','suspended'); raise exception 'FAILED: admin suspension'; exception when sqlstate 'PT403' then null; end;
end $$;
select public.admin_set_user_status('10000000-0000-4000-8000-000000000012','suspended');
reset role;
select pg_temp.check_true(
  (select status='suspended' from public."user" where id='10000000-0000-4000-8000-000000000012'),
  'admin suspends an active non-admin account'
);
select pg_temp.check_true(
  (select count(*)=1 from private.admin_account_status_audit
   where actor_id='10000000-0000-4000-8000-000000000011'
     and target_user_id='10000000-0000-4000-8000-000000000012'
     and new_status='suspended'),
  'status change is audited'
);
set local role authenticated;

select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000012',true);
set local role authenticated;
select pg_temp.check_true((select count(*)=0 from public.project), 'suspension blocks existing project reads');
select pg_temp.check_true((select count(*)=0 from public.check_in where user_id=auth.uid()), 'suspension blocks existing check-in reads');
do $$ declare changed_rows integer;
begin
  update public.check_in set responses='{"wellbeing":5}' where user_id=auth.uid();
  get diagnostics changed_rows = row_count;
  if changed_rows <> 0 then raise exception 'FAILED: suspended check-in update'; end if;
end $$;
do $$ begin
  begin
    insert into public.project(user_id, name, deadline)
    values (auth.uid(),'Suspended project',current_date);
    raise exception 'FAILED: suspended project insert';
  exception when insufficient_privilege then null;
  end;
  begin
    insert into public.check_in(user_id, responses)
    values (auth.uid(),'{"wellbeing":5}');
    raise exception 'FAILED: suspended check-in insert';
  exception when insufficient_privilege then null;
  end;
end $$;
reset role;

select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000011',true);
set local role authenticated;
select public.admin_set_user_status('10000000-0000-4000-8000-000000000012','active');
reset role;
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000012',true);
set local role authenticated;
select pg_temp.check_true((select count(*)=1 from public.project), 'reactivation restores protected access');
select pg_temp.check_true((select count(*)=1 from public.check_in where user_id=auth.uid()), 'reactivation restores check-in access');
reset role;
select 'PASS: admin authorization, safe listing, self-protection, audited status changes and active-account RLS' as result;
rollback;
