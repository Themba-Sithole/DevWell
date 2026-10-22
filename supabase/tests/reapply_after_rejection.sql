-- Run after the migrations. All fixtures and mutations roll back.
begin;
create function pg_temp.check_true(ok boolean, label text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'FAILED: %', label; end if; end $$;
insert into auth.users (id, email) values
  ('17000000-0000-4000-8000-000000000001','sql-reapply-manager@example.invalid'),
  ('17000000-0000-4000-8000-000000000002','sql-reapply-manager-b@example.invalid'),
  ('17000000-0000-4000-8000-000000000003','sql-reapply-rejected@example.invalid'),
  ('17000000-0000-4000-8000-000000000004','sql-reapply-pending@example.invalid'),
  ('17000000-0000-4000-8000-000000000005','sql-reapply-active@example.invalid'),
  ('17000000-0000-4000-8000-000000000006','sql-reapply-admin@example.invalid');
insert into public.company(id,name,status) values
  ('27000000-0000-4000-8000-000000000001','Reapply Company A','active'),
  ('27000000-0000-4000-8000-000000000002','Reapply Company B','active');
update public."user" set role='manager', status='active', onboarding_done=true,
    company_id='27000000-0000-4000-8000-000000000001'
  where id='17000000-0000-4000-8000-000000000001';
update public."user" set role='manager', status='active', onboarding_done=true,
    company_id='27000000-0000-4000-8000-000000000002'
  where id='17000000-0000-4000-8000-000000000002';
update public."user" set role='admin', status='active', onboarding_done=true
  where id='17000000-0000-4000-8000-000000000006';
insert into public.team(id,company_id,name,manager_id) values
  ('37000000-0000-4000-8000-000000000001','27000000-0000-4000-8000-000000000001','Alpha','17000000-0000-4000-8000-000000000001'),
  ('37000000-0000-4000-8000-000000000002','27000000-0000-4000-8000-000000000002','Beta','17000000-0000-4000-8000-000000000002');
-- The rejected engineer keeps the rejected placement until they ask to choose again.
update public."user" set name='Rita Rejected', status='rejected', onboarding_done=true,
    company_id='27000000-0000-4000-8000-000000000001', team_id='37000000-0000-4000-8000-000000000001'
  where id='17000000-0000-4000-8000-000000000003';
update public."user" set name='Pam Pending', status='pending', onboarding_done=true,
    company_id='27000000-0000-4000-8000-000000000001', team_id='37000000-0000-4000-8000-000000000001'
  where id='17000000-0000-4000-8000-000000000004';
update public."user" set name='Al Active', status='active', onboarding_done=true,
    company_id='27000000-0000-4000-8000-000000000001', team_id='37000000-0000-4000-8000-000000000001'
  where id='17000000-0000-4000-8000-000000000005';
insert into public.join_request(id,user_id,company_id,team_id,status,reviewed_by,reviewed_at) values
  ('47000000-0000-4000-8000-000000000001','17000000-0000-4000-8000-000000000003','27000000-0000-4000-8000-000000000001','37000000-0000-4000-8000-000000000001','rejected','17000000-0000-4000-8000-000000000001',now()),
  ('47000000-0000-4000-8000-000000000002','17000000-0000-4000-8000-000000000004','27000000-0000-4000-8000-000000000001','37000000-0000-4000-8000-000000000001','pending',null,null),
  ('47000000-0000-4000-8000-000000000003','17000000-0000-4000-8000-000000000005','27000000-0000-4000-8000-000000000001','37000000-0000-4000-8000-000000000001','approved','17000000-0000-4000-8000-000000000001',now());

-- Shape and privileges.
select pg_temp.check_true((select not prosecdef and proconfig @> array['search_path=""']
  from pg_proc where oid = 'public.restart_team_onboarding()'::regprocedure),
  'public wrapper is SECURITY INVOKER with an empty search_path');
select pg_temp.check_true((select prosecdef and proconfig @> array['search_path=""']
  from pg_proc where oid = 'private.restart_team_onboarding()'::regprocedure),
  'private helper is SECURITY DEFINER with an empty search_path');
select pg_temp.check_true(not has_function_privilege('anon','public.restart_team_onboarding()','execute')
  and has_function_privilege('authenticated','public.restart_team_onboarding()','execute'),
  'execute for authenticated only');

-- Anonymous callers are stopped before any profile is read.
set local role anon;
do $$ begin
  begin perform public.restart_team_onboarding(); raise exception 'FAILED: anon restart'; exception when insufficient_privilege then null; end;
end $$;
reset role;

-- Pending, active, admin and even a manager cannot reset their own placement.
select set_config('request.jwt.claim.sub','17000000-0000-4000-8000-000000000004',true);
set local role authenticated;
do $$ begin
  begin perform public.restart_team_onboarding(); raise exception 'FAILED: pending restart'; exception when sqlstate 'PT409' then null; end;
end $$;
select set_config('request.jwt.claim.sub','17000000-0000-4000-8000-000000000005',true);
do $$ begin
  begin perform public.restart_team_onboarding(); raise exception 'FAILED: active restart'; exception when sqlstate 'PT409' then null; end;
end $$;
select set_config('request.jwt.claim.sub','17000000-0000-4000-8000-000000000006',true);
do $$ begin
  begin perform public.restart_team_onboarding(); raise exception 'FAILED: admin restart'; exception when sqlstate 'PT403' then null; end;
end $$;
select set_config('request.jwt.claim.sub','17000000-0000-4000-8000-000000000001',true);
do $$ begin
  begin perform public.restart_team_onboarding(); raise exception 'FAILED: manager restart'; exception when sqlstate 'PT403' then null; end;
end $$;

-- The rejected engineer can reset, then submit a request to a different company/team.
select set_config('request.jwt.claim.sub','17000000-0000-4000-8000-000000000003',true);
select public.restart_team_onboarding();
do $$ begin
  begin perform public.restart_team_onboarding(); raise exception 'FAILED: repeat restart'; exception when sqlstate 'PT409' then null; end;
end $$;
reset role;
select pg_temp.check_true((select company_id is null and team_id is null and status='pending' and onboarding_done=false
  from public."user" where id='17000000-0000-4000-8000-000000000003'), 'rejected engineer returns to onboarding');
select pg_temp.check_true(exists (select 1 from public.join_request
  where id='47000000-0000-4000-8000-000000000001' and status='rejected'),
  'the old rejected request is kept as history');
set local role authenticated;
select public.submit_join_request('27000000-0000-4000-8000-000000000002','37000000-0000-4000-8000-000000000002');
reset role;
select pg_temp.check_true((select company_id='27000000-0000-4000-8000-000000000002'
    and team_id='37000000-0000-4000-8000-000000000002' and status='pending' and onboarding_done=true
  from public."user" where id='17000000-0000-4000-8000-000000000003'),
  'a new request to a different team is accepted');
select pg_temp.check_true((select count(*) = 1 from public.join_request
  where user_id='17000000-0000-4000-8000-000000000003' and status='pending'
    and team_id='37000000-0000-4000-8000-000000000002'),
  'exactly one new pending request exists');
select 'PASS: rejection restart, history retention and re-request to another team' as result;
rollback;
