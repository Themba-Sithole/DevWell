-- Run after the migration. All fixtures and mutations roll back.
begin;
create function pg_temp.check_true(ok boolean, label text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'FAILED: %', label; end if; end $$;
insert into auth.users (id, email, raw_user_meta_data) values
('10000000-0000-4000-8000-000000000001','sql-engineer1@example.invalid','{"name":"Engineer","role":"admin","status":"active"}'),
('10000000-0000-4000-8000-000000000002','sql-engineer2@example.invalid','{}'),
('10000000-0000-4000-8000-000000000003','sql-manager1@example.invalid','{}'),
('10000000-0000-4000-8000-000000000004','sql-manager2@example.invalid','{}'),
('10000000-0000-4000-8000-000000000005','sql-admin@example.invalid','{}');
select pg_temp.check_true((select role='engineer' and status='pending' and not onboarding_done from public."user" where id='10000000-0000-4000-8000-000000000001'), 'signup ignores privilege metadata');
insert into public.company(id,name,status) values
('20000000-0000-4000-8000-000000000001','Test A','active'),
('20000000-0000-4000-8000-000000000002','Test B','active'),
('20000000-0000-4000-8000-000000000003','Hidden','pending');
insert into public.team(id,company_id,name,manager_id) values
('30000000-0000-4000-8000-000000000001','20000000-0000-4000-8000-000000000001','A1','10000000-0000-4000-8000-000000000003'),
('30000000-0000-4000-8000-000000000002','20000000-0000-4000-8000-000000000001','A2','10000000-0000-4000-8000-000000000003'),
('30000000-0000-4000-8000-000000000003','20000000-0000-4000-8000-000000000002','B1','10000000-0000-4000-8000-000000000004'),
('30000000-0000-4000-8000-000000000004','20000000-0000-4000-8000-000000000003','Hidden',null);
update public."user" set role='manager',status='active',onboarding_done=true,company_id='20000000-0000-4000-8000-000000000001' where id='10000000-0000-4000-8000-000000000003';
update public."user" set role='manager',status='active',onboarding_done=true,company_id='20000000-0000-4000-8000-000000000002' where id='10000000-0000-4000-8000-000000000004';
update public."user" set role='admin',status='active',onboarding_done=true where id='10000000-0000-4000-8000-000000000005';
set local role anon;
do $$ begin
  begin perform * from public."user"; raise exception 'FAILED: anon read'; exception when insufficient_privilege then null; end;
  begin perform public.submit_join_request(null,null); raise exception 'FAILED: anon RPC'; exception when insufficient_privilege then null; end;
end $$;
reset role;
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
set local role authenticated;
select pg_temp.check_true((select count(*)=1 from public."user"), 'profiles are own-user only');
select pg_temp.check_true((select count(*)=2 from public.company), 'hide inactive companies');
select pg_temp.check_true((select count(*)=3 from public.team), 'hide inactive company teams');
do $$ begin
  begin update public."user" set role='admin'; raise exception 'FAILED: self promote'; exception when insufficient_privilege then null; end;
  begin insert into public.join_request(user_id,company_id,team_id) values(auth.uid(),'20000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000001'); raise exception 'FAILED: direct insert'; exception when insufficient_privilege then null; end;
  begin perform public.submit_join_request('20000000-0000-4000-8000-000000000002','30000000-0000-4000-8000-000000000001'); raise exception 'FAILED: mismatch accepted'; exception when sqlstate 'PT400' then null; end;
  begin perform public.submit_join_request('20000000-0000-4000-8000-000000000003','30000000-0000-4000-8000-000000000004'); raise exception 'FAILED: inactive accepted'; exception when sqlstate 'PT400' then null; end;
end $$;
select pg_temp.check_true((select not onboarding_done from public."user"), 'failed requests do not mutate profile');
select public.submit_join_request('20000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000001');
select public.submit_join_request('20000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000001');
select pg_temp.check_true((select count(*)=1 from public.join_request), 'retry creates one request');
select pg_temp.check_true((select status='pending' and onboarding_done and team_id='30000000-0000-4000-8000-000000000001' from public."user"), 'submission updates profile');
select set_config('test.request1',(select id::text from public.join_request),true);
do $$ begin
  begin perform public.submit_join_request('20000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000002'); raise exception 'FAILED: conflicting retry'; exception when sqlstate 'PT409' then null; end;
  begin perform public.review_join_request(current_setting('test.request1')::uuid,true); raise exception 'FAILED: engineer approval'; exception when sqlstate 'PT403' then null; end;
end $$;
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000002',true);
select pg_temp.check_true((select count(*)=0 from public.join_request), 'other engineer sees no request');
select public.submit_join_request('20000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000002');
select set_config('test.request2',(select id::text from public.join_request),true);
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000004',true);
select pg_temp.check_true((select count(*)=0 from public.join_request), 'wrong manager sees no request');
do $$ begin
  begin perform public.review_join_request(current_setting('test.request1')::uuid,true); raise exception 'FAILED: wrong manager approval'; exception when sqlstate 'PT403' then null; end;
end $$;
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000003',true);
select pg_temp.check_true((select count(*)=2 from public.join_request), 'correct manager sees own team requests');
select public.review_join_request(current_setting('test.request1')::uuid,true);
select public.review_join_request(current_setting('test.request1')::uuid,true);
do $$ begin
  begin perform public.review_join_request(current_setting('test.request1')::uuid,false); raise exception 'FAILED: contradictory review'; exception when sqlstate 'PT409' then null; end;
end $$;
select public.review_join_request(current_setting('test.request2')::uuid,false);
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000002',true);
select pg_temp.check_true((select status='rejected' from public."user"), 'rejection updates profile');
do $$ begin
  begin perform public.submit_join_request('20000000-0000-4000-8000-000000000001','30000000-0000-4000-8000-000000000002'); raise exception 'FAILED: rejected resubmit'; exception when sqlstate 'PT403' then null; end;
end $$;
select set_config('request.jwt.claim.sub','10000000-0000-4000-8000-000000000001',true);
select pg_temp.check_true((select status='active' from public."user"), 'approval activates engineer');
reset role;
select pg_temp.check_true((select count(*)=2 from public.join_request where reviewed_by='10000000-0000-4000-8000-000000000003' and reviewed_at is not null), 'review audit fields recorded');
select 'PASS: profile trigger, RLS, privilege protection, transactional submission, retries, cross-team review, approval and rejection' as result;
rollback;
