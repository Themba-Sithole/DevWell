-- Run after the migrations. All fixtures and mutations roll back.
begin;
create function pg_temp.check_true(ok boolean, label text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'FAILED: %', label; end if; end $$;
insert into auth.users (id, email) values
('15000000-0000-4000-8000-000000000001','sql-roster-manager-a@example.invalid'),
('15000000-0000-4000-8000-000000000002','sql-roster-manager-a2@example.invalid'),
('15000000-0000-4000-8000-000000000003','sql-roster-manager-b@example.invalid'),
('15000000-0000-4000-8000-000000000004','sql-roster-admin@example.invalid'),
('15000000-0000-4000-8000-000000000005','sql-roster-ada@example.invalid'),
('15000000-0000-4000-8000-000000000006','sql-roster-ben@example.invalid'),
('15000000-0000-4000-8000-000000000007','sql-roster-cara@example.invalid'),
('15000000-0000-4000-8000-000000000008','sql-roster-dev@example.invalid'),
('15000000-0000-4000-8000-000000000009','sql-roster-eve@example.invalid'),
('15000000-0000-4000-8000-000000000010','sql-roster-fay@example.invalid'),
('15000000-0000-4000-8000-000000000011','sql-roster-gus@example.invalid');
insert into public.company(id,name,status) values
('25000000-0000-4000-8000-000000000001','Roster Company A','active'),
('25000000-0000-4000-8000-000000000002','Roster Company B','active');
update public."user" set role='manager', status='active', onboarding_done=true,
  company_id='25000000-0000-4000-8000-000000000001' where id in
  ('15000000-0000-4000-8000-000000000001','15000000-0000-4000-8000-000000000002');
update public."user" set role='manager', status='active', onboarding_done=true,
  company_id='25000000-0000-4000-8000-000000000002' where id='15000000-0000-4000-8000-000000000003';
update public."user" set role='admin', status='active', onboarding_done=true where id='15000000-0000-4000-8000-000000000004';
insert into public.team(id,company_id,name,manager_id) values
('35000000-0000-4000-8000-000000000001','25000000-0000-4000-8000-000000000001','Roster Team','15000000-0000-4000-8000-000000000001'),
('35000000-0000-4000-8000-000000000002','25000000-0000-4000-8000-000000000001','Second Team','15000000-0000-4000-8000-000000000001'),
('35000000-0000-4000-8000-000000000003','25000000-0000-4000-8000-000000000001','Colleague Team','15000000-0000-4000-8000-000000000002'),
-- Company A team wrongly assigned to company B's manager: the company match must still block them.
('35000000-0000-4000-8000-000000000004','25000000-0000-4000-8000-000000000001','Cross Company Team','15000000-0000-4000-8000-000000000003');
-- The manager belongs to the team too, to prove managers are not listed in the roster.
update public."user" set team_id='35000000-0000-4000-8000-000000000001' where id='15000000-0000-4000-8000-000000000001';
update public."user" u set name=v.name, status=v.status, onboarding_done=true,
  company_id='25000000-0000-4000-8000-000000000001', team_id=v.team_id
  from (values
    ('15000000-0000-4000-8000-000000000005'::uuid,'Ada','active','35000000-0000-4000-8000-000000000001'::uuid),
    ('15000000-0000-4000-8000-000000000006','Ben','active','35000000-0000-4000-8000-000000000001'),
    ('15000000-0000-4000-8000-000000000007','Cara','pending','35000000-0000-4000-8000-000000000001'),
    ('15000000-0000-4000-8000-000000000008','Dev','pending','35000000-0000-4000-8000-000000000002'),
    ('15000000-0000-4000-8000-000000000009','Eve','rejected','35000000-0000-4000-8000-000000000001'),
    ('15000000-0000-4000-8000-000000000010','Fay','pending','35000000-0000-4000-8000-000000000001'),
    ('15000000-0000-4000-8000-000000000011','Gus','active','35000000-0000-4000-8000-000000000003')
  ) v(id,name,status,team_id) where u.id=v.id;
insert into public.join_request(id,user_id,company_id,team_id,status,reviewed_by,reviewed_at) values
('45000000-0000-4000-8000-000000000001','15000000-0000-4000-8000-000000000007','25000000-0000-4000-8000-000000000001','35000000-0000-4000-8000-000000000001','pending',null,null),
('45000000-0000-4000-8000-000000000002','15000000-0000-4000-8000-000000000008','25000000-0000-4000-8000-000000000001','35000000-0000-4000-8000-000000000002','pending',null,null),
('45000000-0000-4000-8000-000000000003','15000000-0000-4000-8000-000000000009','25000000-0000-4000-8000-000000000001','35000000-0000-4000-8000-000000000001','rejected','15000000-0000-4000-8000-000000000001',now()),
('45000000-0000-4000-8000-000000000004','15000000-0000-4000-8000-000000000005','25000000-0000-4000-8000-000000000001','35000000-0000-4000-8000-000000000001','approved','15000000-0000-4000-8000-000000000001',now());

-- Function shape and privileges.
select pg_temp.check_true((select bool_and(not prosecdef and proconfig::text like '%search_path=""%') from pg_proc
  where oid in ('public.pending_join_requesters(uuid)'::regprocedure,'public.team_roster(uuid)'::regprocedure)),
  'public wrappers are SECURITY INVOKER with an empty search_path');
select pg_temp.check_true((select bool_and(prosecdef and proconfig::text like '%search_path=""%') from pg_proc
  where oid in ('private.pending_join_requesters(uuid)'::regprocedure,'private.team_roster(uuid)'::regprocedure)),
  'private helpers are SECURITY DEFINER with an empty search_path');
select pg_temp.check_true(not has_function_privilege('anon','public.pending_join_requesters(uuid)','execute')
  and not has_function_privilege('anon','public.team_roster(uuid)','execute')
  and has_function_privilege('authenticated','public.pending_join_requesters(uuid)','execute')
  and has_function_privilege('authenticated','public.team_roster(uuid)','execute'), 'execute for authenticated only');

set local role anon;
do $$ begin
  begin perform public.pending_join_requesters('35000000-0000-4000-8000-000000000001'); raise exception 'FAILED: anon requesters'; exception when insufficient_privilege then null; end;
  begin perform public.team_roster('35000000-0000-4000-8000-000000000001'); raise exception 'FAILED: anon roster'; exception when insufficient_privilege then null; end;
end $$;
reset role;

-- Engineer in the team.
select set_config('request.jwt.claim.sub','15000000-0000-4000-8000-000000000005',true);
set local role authenticated;
do $$ begin
  begin perform public.pending_join_requesters('35000000-0000-4000-8000-000000000001'); raise exception 'FAILED: engineer requesters'; exception when sqlstate 'PT403' then null; end;
  begin perform public.team_roster('35000000-0000-4000-8000-000000000001'); raise exception 'FAILED: engineer roster'; exception when sqlstate 'PT403' then null; end;
end $$;
-- Another manager in the same company.
select set_config('request.jwt.claim.sub','15000000-0000-4000-8000-000000000002',true);
do $$ begin
  begin perform public.pending_join_requesters('35000000-0000-4000-8000-000000000001'); raise exception 'FAILED: other manager requesters'; exception when sqlstate 'PT403' then null; end;
  begin perform public.team_roster('35000000-0000-4000-8000-000000000001'); raise exception 'FAILED: other manager roster'; exception when sqlstate 'PT403' then null; end;
end $$;
-- Admin.
select set_config('request.jwt.claim.sub','15000000-0000-4000-8000-000000000004',true);
do $$ begin
  begin perform public.pending_join_requesters('35000000-0000-4000-8000-000000000001'); raise exception 'FAILED: admin requesters'; exception when sqlstate 'PT403' then null; end;
  begin perform public.team_roster('35000000-0000-4000-8000-000000000001'); raise exception 'FAILED: admin roster'; exception when sqlstate 'PT403' then null; end;
end $$;
-- Manager of a different company, including a company A team that names them as manager.
select set_config('request.jwt.claim.sub','15000000-0000-4000-8000-000000000003',true);
do $$ begin
  begin perform public.pending_join_requesters('35000000-0000-4000-8000-000000000001'); raise exception 'FAILED: other company requesters'; exception when sqlstate 'PT403' then null; end;
  begin perform public.team_roster('35000000-0000-4000-8000-000000000001'); raise exception 'FAILED: other company roster'; exception when sqlstate 'PT403' then null; end;
  begin perform public.pending_join_requesters('35000000-0000-4000-8000-000000000004'); raise exception 'FAILED: cross company requesters'; exception when sqlstate 'PT403' then null; end;
  begin perform public.team_roster('35000000-0000-4000-8000-000000000004'); raise exception 'FAILED: cross company roster'; exception when sqlstate 'PT403' then null; end;
end $$;

-- Correct manager.
select set_config('request.jwt.claim.sub','15000000-0000-4000-8000-000000000001',true);
select pg_temp.check_true((select count(*)=1 and bool_and(r.request_id='45000000-0000-4000-8000-000000000001'
  and r.user_id='15000000-0000-4000-8000-000000000007' and r.name='Cara')
  from public.pending_join_requesters('35000000-0000-4000-8000-000000000001') r),
  'only pending requests for this team, with id, user id and name');
select pg_temp.check_true((select bool_and(array(select jsonb_object_keys(to_jsonb(r)) order by 1)=array['name','request_id','user_id'])
  from public.pending_join_requesters('35000000-0000-4000-8000-000000000001') r), 'requesters return no other columns');
select pg_temp.check_true((select array_agg(r.name)=array['Dev'] from public.pending_join_requesters('35000000-0000-4000-8000-000000000002') r),
  'requesters are scoped to the requested team');
select pg_temp.check_true((select array_agg(r.name order by r.name)=array['Ada','Ben']
  from public.team_roster('35000000-0000-4000-8000-000000000001') r),
  'roster lists only active engineers of this team');
select pg_temp.check_true((select bool_and(array(select jsonb_object_keys(to_jsonb(r)) order by 1)=array['name','user_id'])
  from public.team_roster('35000000-0000-4000-8000-000000000001') r), 'roster returns user id and name only');
select pg_temp.check_true((select count(*)=0 from public."user" where id <> '15000000-0000-4000-8000-000000000001'),
  'no new direct table access to other profiles');
do $$ begin
  begin perform public.team_roster('35000000-0000-4000-8000-000000000003'); raise exception 'FAILED: colleague team roster'; exception when sqlstate 'PT403' then null; end;
  begin perform public.team_roster('35000000-0000-4000-8000-0000000000ff'); raise exception 'FAILED: unknown team'; exception when sqlstate 'PT403' then null; end;
end $$;
-- Inactive company blocks even the correct manager.
reset role;
update public.company set status='pending' where id='25000000-0000-4000-8000-000000000001';
set local role authenticated;
do $$ begin
  begin perform public.pending_join_requesters('35000000-0000-4000-8000-000000000001'); raise exception 'FAILED: inactive company requesters'; exception when sqlstate 'PT403' then null; end;
  begin perform public.team_roster('35000000-0000-4000-8000-000000000001'); raise exception 'FAILED: inactive company roster'; exception when sqlstate 'PT403' then null; end;
end $$;
reset role;
select 'PASS: invoker/definer shape, privileges, engineer/manager/admin/other-company denial, scoped name-only results' as result;
rollback;
