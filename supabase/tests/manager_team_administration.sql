-- Run after the migrations. All fixtures and mutations roll back.
begin;
create function pg_temp.check_true(ok boolean, label text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'FAILED: %', label; end if; end $$;
insert into auth.users (id, email) values
  ('16000000-0000-4000-8000-000000000001','sql-admin-manager-a@example.invalid'),
  ('16000000-0000-4000-8000-000000000002','sql-admin-manager-a2@example.invalid'),
  ('16000000-0000-4000-8000-000000000003','sql-admin-manager-b@example.invalid'),
  ('16000000-0000-4000-8000-000000000004','sql-admin-admin@example.invalid'),
  ('16000000-0000-4000-8000-000000000005','sql-admin-ada@example.invalid'),
  ('16000000-0000-4000-8000-000000000006','sql-admin-ben@example.invalid'),
  ('16000000-0000-4000-8000-000000000007','sql-admin-cara@example.invalid'),
  ('16000000-0000-4000-8000-000000000008','sql-admin-dev@example.invalid'),
  ('16000000-0000-4000-8000-000000000009','sql-admin-eve@example.invalid'),
  ('16000000-0000-4000-8000-000000000010','sql-admin-fay@example.invalid'),
  ('16000000-0000-4000-8000-000000000011','sql-admin-gus@example.invalid');
insert into public.company(id,name,status) values
  ('26000000-0000-4000-8000-000000000001','Admin Company A','active'),
  ('26000000-0000-4000-8000-000000000002','Admin Company B','active');
-- m1 and m2 manage company A; m3 manages company B.
update public."user" set role='manager', status='active', onboarding_done=true,
    company_id='26000000-0000-4000-8000-000000000001'
  where id in ('16000000-0000-4000-8000-000000000001','16000000-0000-4000-8000-000000000002');
update public."user" set role='manager', status='active', onboarding_done=true,
    company_id='26000000-0000-4000-8000-000000000002'
  where id='16000000-0000-4000-8000-000000000003';
update public."user" set role='admin', status='active', onboarding_done=true
  where id='16000000-0000-4000-8000-000000000004';
insert into public.team(id,company_id,name,manager_id) values
  ('36000000-0000-4000-8000-000000000001','26000000-0000-4000-8000-000000000001','Mobile','16000000-0000-4000-8000-000000000001'),
  ('36000000-0000-4000-8000-000000000002','26000000-0000-4000-8000-000000000001','Web','16000000-0000-4000-8000-000000000001'),
  ('36000000-0000-4000-8000-000000000003','26000000-0000-4000-8000-000000000001','Data','16000000-0000-4000-8000-000000000002'),
  -- Company A team whose manager_id names a company B manager: the company check must still deny them.
  ('36000000-0000-4000-8000-000000000004','26000000-0000-4000-8000-000000000001','Cross','16000000-0000-4000-8000-000000000003'),
  ('36000000-0000-4000-8000-000000000005','26000000-0000-4000-8000-000000000002','Other','16000000-0000-4000-8000-000000000003');
-- The manager is placed on the team that is deleted below, proving only engineers
-- are sent back to onboarding and the manager moves to their remaining team.
update public."user" set team_id='36000000-0000-4000-8000-000000000002'
  where id='16000000-0000-4000-8000-000000000001';
update public."user" u set name=v.name, status=v.status, onboarding_done=v.onboarding_done,
    company_id='26000000-0000-4000-8000-000000000001', team_id=v.team_id
  from (values
    ('16000000-0000-4000-8000-000000000005'::uuid,'Ada','active',true,'36000000-0000-4000-8000-000000000001'::uuid),
    ('16000000-0000-4000-8000-000000000006','Ben','active',true,'36000000-0000-4000-8000-000000000001'),
    ('16000000-0000-4000-8000-000000000007','Cara','pending',true,'36000000-0000-4000-8000-000000000001'),
    ('16000000-0000-4000-8000-000000000008','Dev','rejected',true,'36000000-0000-4000-8000-000000000001'),
    ('16000000-0000-4000-8000-000000000009','Eve','active',true,'36000000-0000-4000-8000-000000000003'),
    ('16000000-0000-4000-8000-000000000010','Fay','active',true,'36000000-0000-4000-8000-000000000002'),
    ('16000000-0000-4000-8000-000000000011','Gus','pending',true,'36000000-0000-4000-8000-000000000002')
  ) v(id,name,status,onboarding_done,team_id) where u.id=v.id;
insert into public.join_request(id,user_id,company_id,team_id,status,reviewed_by,reviewed_at) values
  ('46000000-0000-4000-8000-000000000001','16000000-0000-4000-8000-000000000005','26000000-0000-4000-8000-000000000001','36000000-0000-4000-8000-000000000001','approved','16000000-0000-4000-8000-000000000001',now()),
  ('46000000-0000-4000-8000-000000000002','16000000-0000-4000-8000-000000000006','26000000-0000-4000-8000-000000000001','36000000-0000-4000-8000-000000000001','approved','16000000-0000-4000-8000-000000000001',now()),
  ('46000000-0000-4000-8000-000000000003','16000000-0000-4000-8000-000000000007','26000000-0000-4000-8000-000000000001','36000000-0000-4000-8000-000000000001','pending',null,null),
  ('46000000-0000-4000-8000-000000000004','16000000-0000-4000-8000-000000000008','26000000-0000-4000-8000-000000000001','36000000-0000-4000-8000-000000000001','rejected','16000000-0000-4000-8000-000000000001',now()),
  ('46000000-0000-4000-8000-000000000005','16000000-0000-4000-8000-000000000009','26000000-0000-4000-8000-000000000001','36000000-0000-4000-8000-000000000003','approved','16000000-0000-4000-8000-000000000002',now()),
  ('46000000-0000-4000-8000-000000000006','16000000-0000-4000-8000-000000000010','26000000-0000-4000-8000-000000000001','36000000-0000-4000-8000-000000000002','approved','16000000-0000-4000-8000-000000000001',now()),
  ('46000000-0000-4000-8000-000000000007','16000000-0000-4000-8000-000000000011','26000000-0000-4000-8000-000000000001','36000000-0000-4000-8000-000000000002','pending',null,null);

-- Function shape and privileges.
select pg_temp.check_true((select bool_and(not prosecdef and proconfig @> array['search_path=""']) from pg_proc
  where oid in ('public.remove_team_member(uuid,uuid)'::regprocedure,'public.delete_team(uuid)'::regprocedure)),
  'public wrappers are SECURITY INVOKER with an empty search_path');
select pg_temp.check_true((select bool_and(prosecdef and proconfig @> array['search_path=""']) from pg_proc
  where oid in ('private.owns_team(uuid)'::regprocedure,'private.remove_team_member(uuid,uuid)'::regprocedure,
    'private.delete_team(uuid)'::regprocedure)),
  'private helpers are SECURITY DEFINER with an empty search_path');
select pg_temp.check_true(not has_function_privilege('anon','public.remove_team_member(uuid,uuid)','execute')
  and not has_function_privilege('anon','public.delete_team(uuid)','execute')
  and has_function_privilege('authenticated','public.remove_team_member(uuid,uuid)','execute')
  and has_function_privilege('authenticated','public.delete_team(uuid)','execute'), 'execute for authenticated only');

set local role anon;
do $$ begin
  begin perform public.remove_team_member('36000000-0000-4000-8000-000000000001','16000000-0000-4000-8000-000000000005'); raise exception 'FAILED: anon remove'; exception when insufficient_privilege then null; end;
  begin perform public.delete_team('36000000-0000-4000-8000-000000000001'); raise exception 'FAILED: anon delete'; exception when insufficient_privilege then null; end;
end $$;
reset role;

-- An engineer, an admin, another manager in the same company, and a cross-company manager are all denied.
select set_config('request.jwt.claim.sub','16000000-0000-4000-8000-000000000005',true);
set local role authenticated;
do $$ begin
  begin perform public.remove_team_member('36000000-0000-4000-8000-000000000001','16000000-0000-4000-8000-000000000006'); raise exception 'FAILED: engineer remove'; exception when sqlstate 'PT403' then null; end;
  begin perform public.delete_team('36000000-0000-4000-8000-000000000001'); raise exception 'FAILED: engineer delete'; exception when sqlstate 'PT403' then null; end;
end $$;
select set_config('request.jwt.claim.sub','16000000-0000-4000-8000-000000000004',true);
do $$ begin
  begin perform public.remove_team_member('36000000-0000-4000-8000-000000000001','16000000-0000-4000-8000-000000000006'); raise exception 'FAILED: admin remove'; exception when sqlstate 'PT403' then null; end;
  begin perform public.delete_team('36000000-0000-4000-8000-000000000001'); raise exception 'FAILED: admin delete'; exception when sqlstate 'PT403' then null; end;
end $$;
select set_config('request.jwt.claim.sub','16000000-0000-4000-8000-000000000002',true);
do $$ begin
  begin perform public.remove_team_member('36000000-0000-4000-8000-000000000001','16000000-0000-4000-8000-000000000006'); raise exception 'FAILED: colleague remove'; exception when sqlstate 'PT403' then null; end;
  begin perform public.delete_team('36000000-0000-4000-8000-000000000001'); raise exception 'FAILED: colleague delete'; exception when sqlstate 'PT403' then null; end;
end $$;
select set_config('request.jwt.claim.sub','16000000-0000-4000-8000-000000000003',true);
do $$ begin
  begin perform public.remove_team_member('36000000-0000-4000-8000-000000000001','16000000-0000-4000-8000-000000000006'); raise exception 'FAILED: cross-company remove'; exception when sqlstate 'PT403' then null; end;
  begin perform public.delete_team('36000000-0000-4000-8000-000000000001'); raise exception 'FAILED: cross-company delete'; exception when sqlstate 'PT403' then null; end;
  begin perform public.remove_team_member('36000000-0000-4000-8000-000000000004','16000000-0000-4000-8000-000000000006'); raise exception 'FAILED: named-manager remove'; exception when sqlstate 'PT403' then null; end;
  begin perform public.delete_team('36000000-0000-4000-8000-000000000004'); raise exception 'FAILED: named-manager delete'; exception when sqlstate 'PT403' then null; end;
end $$;

-- Correct manager: removing Ada ends only her placement and approved request.
reset role;
select set_config('request.jwt.claim.sub','16000000-0000-4000-8000-000000000001',true);
set local role authenticated;
select public.remove_team_member('36000000-0000-4000-8000-000000000001','16000000-0000-4000-8000-000000000005');
do $$ begin
  begin perform public.remove_team_member('36000000-0000-4000-8000-000000000001','16000000-0000-4000-8000-000000000005'); raise exception 'FAILED: repeat remove'; exception when sqlstate 'PT404' then null; end;
  begin perform public.remove_team_member('36000000-0000-4000-8000-000000000001','16000000-0000-4000-8000-000000000007'); raise exception 'FAILED: pending requester remove'; exception when sqlstate 'PT404' then null; end;
  begin perform public.remove_team_member('36000000-0000-4000-8000-000000000001','16000000-0000-4000-8000-000000000009'); raise exception 'FAILED: other team member remove'; exception when sqlstate 'PT404' then null; end;
end $$;
reset role;
select pg_temp.check_true((select company_id is null and team_id is null and status='pending' and onboarding_done=false
  from public."user" where id='16000000-0000-4000-8000-000000000005'), 'removed engineer returns to onboarding');
select pg_temp.check_true(not exists (select 1 from public.join_request where user_id='16000000-0000-4000-8000-000000000005'
  and team_id='36000000-0000-4000-8000-000000000001'), 'removed engineer has no membership request left');
select pg_temp.check_true((select team_id='36000000-0000-4000-8000-000000000001' and status='active'
  from public."user" where id='16000000-0000-4000-8000-000000000006'), 'other member is untouched');
select pg_temp.check_true((select team_id='36000000-0000-4000-8000-000000000003'
  from public."user" where id='16000000-0000-4000-8000-000000000009'), 'other team is untouched');
-- Deleting the Web team resets its active and pending engineers, clears the rejected
-- engineer without reactivating them, removes its requests, and moves the manager to
-- the team they still own.
set local role authenticated;
select public.delete_team('36000000-0000-4000-8000-000000000002');
do $$ begin
  begin perform public.delete_team('36000000-0000-4000-8000-000000000002'); raise exception 'FAILED: repeat delete'; exception when sqlstate 'PT404' then null; end;
  begin perform public.delete_team('36000000-0000-4000-8000-000000000003'); raise exception 'FAILED: colleague team delete'; exception when sqlstate 'PT403' then null; end;
end $$;
reset role;
select pg_temp.check_true(not exists (select 1 from public.team where id='36000000-0000-4000-8000-000000000002'), 'team is deleted');
select pg_temp.check_true((select company_id is null and team_id is null and status='pending' and onboarding_done=false
  from public."user" where id='16000000-0000-4000-8000-000000000010'), 'active member returns to onboarding');
select pg_temp.check_true((select company_id is null and team_id is null and status='pending' and onboarding_done=false
  from public."user" where id='16000000-0000-4000-8000-000000000011'), 'pending requester returns to onboarding');
select pg_temp.check_true(not exists (select 1 from public.join_request where team_id='36000000-0000-4000-8000-000000000002'), 'team requests are deleted');
select pg_temp.check_true((select team_id='36000000-0000-4000-8000-000000000001'
  from public."user" where id='16000000-0000-4000-8000-000000000001'), 'manager moves to their remaining team');
select pg_temp.check_true((select team_id='36000000-0000-4000-8000-000000000003'
  from public."user" where id='16000000-0000-4000-8000-000000000009'), 'another manager''s member is untouched');

-- Deleting the manager's last team clears their own team reference too.
set local role authenticated;
select public.delete_team('36000000-0000-4000-8000-000000000001');
reset role;
select pg_temp.check_true((select team_id is null and company_id='26000000-0000-4000-8000-000000000001'
  and status='active' from public."user" where id='16000000-0000-4000-8000-000000000001'),
  'manager keeps the company and loses the last team reference');
select pg_temp.check_true((select status='pending' and onboarding_done=false
  from public."user" where id='16000000-0000-4000-8000-000000000006'), 'remaining member returns to onboarding');
select pg_temp.check_true((select status='pending' and onboarding_done=false
  from public."user" where id='16000000-0000-4000-8000-000000000007'), 'pending requester still needs a new team');
select pg_temp.check_true((select team_id is null and status='rejected' and onboarding_done=true
  from public."user" where id='16000000-0000-4000-8000-000000000008'), 'rejected engineer is not reactivated');
select 'PASS: ownership, denial paths, member reset, request cleanup, manager team reassignment' as result;
rollback;
