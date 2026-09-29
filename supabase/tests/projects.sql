begin;
create function pg_temp.check_true(ok boolean, label text) returns void language plpgsql as $$
begin if ok is distinct from true then raise exception 'FAILED: %', label; end if; end $$;
insert into auth.users (id, email) values
('11000000-0000-4000-8000-000000000001','sql-projects1@example.invalid'),
('11000000-0000-4000-8000-000000000002','sql-projects2@example.invalid'),
('11000000-0000-4000-8000-000000000003','sql-manager@example.invalid');
insert into public.company(id,name,status) values
('21000000-0000-4000-8000-000000000001','Project Test Company','active');
insert into public.team(id,company_id,name) values
('31000000-0000-4000-8000-000000000001','21000000-0000-4000-8000-000000000001','Project Test Team');
update public."user" set status='active', onboarding_done=true,
  company_id='21000000-0000-4000-8000-000000000001', team_id='31000000-0000-4000-8000-000000000001'
  where id in ('11000000-0000-4000-8000-000000000001','11000000-0000-4000-8000-000000000002');
update public."user" set role='manager', status='active', onboarding_done=true where id='11000000-0000-4000-8000-000000000003';
set local request.jwt.claim.sub='11000000-0000-4000-8000-000000000001';
set local role authenticated;
insert into public.project(user_id,name,description,colour,deadline) values
(auth.uid(),'Engineer A project','private','#174A35','2026-09-30');
insert into public.task(project_id,title,deadline,priority,note,status,position)
select id,'Ship task','2026-09-20','HIGH','first','DONE',0
from public.project where user_id=auth.uid();
insert into public.task(project_id,title,deadline,priority,note,status,position)
select id,'Open task','2026-09-21','LOW','second','OPEN',1
from public.project where user_id=auth.uid();
select pg_temp.check_true((select progress=50 from public.project where user_id=auth.uid()), 'progress derives from completed tasks');
do $$ begin
  begin
    update public.project set progress=99 where user_id=auth.uid();
    raise exception 'FAILED: direct progress update';
  exception when insufficient_privilege then null;
  end;
end $$;
select pg_temp.check_true((select progress=50 from public.project where user_id=auth.uid()), 'progress remains server-derived');
update public.task set status='DONE' where title='Open task';
select pg_temp.check_true((select progress=100 from public.project where user_id=auth.uid()), 'progress updates after task edit');
delete from public.task where title='Open task';
select pg_temp.check_true((select progress=100 from public.project where user_id=auth.uid()), 'progress updates after task delete');
delete from public.task where title='Ship task';
select pg_temp.check_true((select progress=0 from public.project where user_id=auth.uid()), 'empty project progress is zero');
insert into public.project(user_id,name,deadline) values(auth.uid(),'Second project','2026-10-01');
insert into public.task(project_id,title,deadline,status,position)
select id,'Second task','2026-10-02','OPEN',0
from public.project where user_id=auth.uid() and name='Second project';
select set_config('test.project_a',(select id::text from public.project where name='Engineer A project'),true);
select set_config('request.jwt.claim.sub','11000000-0000-4000-8000-000000000002',true);
select pg_temp.check_true((select count(*)=0 from public.project), 'other engineer sees no projects');
select pg_temp.check_true((select count(*)=0 from public.task), 'other engineer sees no tasks');
do $$ begin
  begin
    insert into public.task(project_id,title,deadline)
    values(current_setting('test.project_a')::uuid,'Intruder task','2026-10-03');
    raise exception 'FAILED: cross-owner task insert';
  exception when insufficient_privilege then null;
  end;
end $$;
select set_config('request.jwt.claim.sub','11000000-0000-4000-8000-000000000003',true);
select pg_temp.check_true((select count(*)=0 from public.project), 'manager sees no engineer projects');
select pg_temp.check_true((select count(*)=0 from public.task), 'manager sees no engineer tasks');
reset role;
select 'PASS: owner isolation, manager isolation, task CRUD and derived progress' as result;
rollback;
