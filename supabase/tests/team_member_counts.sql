-- Run after the migrations. All fixtures and mutations roll back.
begin;
create function pg_temp.check_true(ok boolean, label text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'FAILED: %', label; end if; end $$;
insert into auth.users (id, email) values
('16000000-0000-4000-8000-000000000001','sql-count-manager@example.invalid'),
('16000000-0000-4000-8000-000000000002','sql-count-newcomer@example.invalid'),
('16000000-0000-4000-8000-000000000003','sql-count-ada@example.invalid'),
('16000000-0000-4000-8000-000000000004','sql-count-ben@example.invalid'),
('16000000-0000-4000-8000-000000000005','sql-count-cara@example.invalid'),
('16000000-0000-4000-8000-000000000006','sql-count-eve@example.invalid'),
('16000000-0000-4000-8000-000000000007','sql-count-gus@example.invalid');
insert into public.company(id,name,status) values
('26000000-0000-4000-8000-000000000001','Count Company Active','active'),
('26000000-0000-4000-8000-000000000002','Count Company Pending','pending');
update public."user" set role='manager', status='active', onboarding_done=true,
  company_id='26000000-0000-4000-8000-000000000001' where id='16000000-0000-4000-8000-000000000001';
insert into public.team(id,company_id,name,manager_id) values
('36000000-0000-4000-8000-000000000001','26000000-0000-4000-8000-000000000001','Count Team','16000000-0000-4000-8000-000000000001'),
('36000000-0000-4000-8000-000000000002','26000000-0000-4000-8000-000000000001','Empty Team','16000000-0000-4000-8000-000000000001'),
('36000000-0000-4000-8000-000000000003','26000000-0000-4000-8000-000000000002','Pending Company Team',null);
-- The manager belongs to the team too, to prove managers are not counted.
update public."user" set team_id='36000000-0000-4000-8000-000000000001' where id='16000000-0000-4000-8000-000000000001';
update public."user" u set name=v.name, status=v.status, onboarding_done=true,
  company_id=v.company_id, team_id=v.team_id
  from (values
    ('16000000-0000-4000-8000-000000000003'::uuid,'Ada','active','26000000-0000-4000-8000-000000000001'::uuid,'36000000-0000-4000-8000-000000000001'::uuid),
    ('16000000-0000-4000-8000-000000000004','Ben','active','26000000-0000-4000-8000-000000000001','36000000-0000-4000-8000-000000000001'),
    ('16000000-0000-4000-8000-000000000005','Cara','pending','26000000-0000-4000-8000-000000000001','36000000-0000-4000-8000-000000000001'),
    ('16000000-0000-4000-8000-000000000006','Eve','rejected','26000000-0000-4000-8000-000000000001','36000000-0000-4000-8000-000000000001'),
    ('16000000-0000-4000-8000-000000000007','Gus','active','26000000-0000-4000-8000-000000000002','36000000-0000-4000-8000-000000000003')
  ) v(id,name,status,company_id,team_id) where u.id=v.id;

-- Function shape and privileges.
select pg_temp.check_true((select not prosecdef and proconfig @> array['search_path=""'] from pg_proc
  where oid='public.team_member_counts()'::regprocedure), 'public wrapper is SECURITY INVOKER with an empty search_path');
select pg_temp.check_true((select prosecdef and proconfig @> array['search_path=""'] from pg_proc
  where oid='private.team_member_counts()'::regprocedure), 'private helper is SECURITY DEFINER with an empty search_path');
select pg_temp.check_true(not has_function_privilege('anon','public.team_member_counts()','execute')
  and not has_function_privilege('anon','private.team_member_counts()','execute')
  and has_function_privilege('authenticated','public.team_member_counts()','execute'), 'execute for authenticated only');

set local role anon;
do $$ begin
  begin perform public.team_member_counts(); raise exception 'FAILED: anon counts'; exception when insufficient_privilege then null; end;
end $$;
reset role;

-- Authenticated role without a user id.
select set_config('request.jwt.claim.sub','',true);
set local role authenticated;
do $$ begin
  begin perform public.team_member_counts(); raise exception 'FAILED: missing user id'; exception when sqlstate 'PT401' then null; end;
end $$;
reset role;

-- A new engineer still choosing a team.
select set_config('request.jwt.claim.sub','16000000-0000-4000-8000-000000000002',true);
set local role authenticated;
select pg_temp.check_true((select r.member_count=2 from public.team_member_counts() r
  where r.team_id='36000000-0000-4000-8000-000000000001'),
  'counts only active engineers, not the manager or pending and rejected users');
select pg_temp.check_true((select r.member_count=0 from public.team_member_counts() r
  where r.team_id='36000000-0000-4000-8000-000000000002'), 'empty team is returned with 0');
select pg_temp.check_true((select count(*)=0 from public.team_member_counts() r
  where r.team_id='36000000-0000-4000-8000-000000000003'), 'teams of inactive companies are not returned');
select pg_temp.check_true((select bool_and(array(select jsonb_object_keys(to_jsonb(r)) order by 1)=array['member_count','team_id'])
  from public.team_member_counts() r), 'counts return no other columns');
select pg_temp.check_true((select count(*)=0 from public."user" where id <> '16000000-0000-4000-8000-000000000002'),
  'no new direct table access to other profiles');
reset role;
select 'PASS: invoker/definer shape, privileges, anon and missing-user denial, active-engineer headcount only' as result;
rollback;
