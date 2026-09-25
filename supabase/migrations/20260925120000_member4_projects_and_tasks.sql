create table public.project (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public."user"(id) on delete cascade,
  name text not null check (length(trim(name)) between 1 and 150),
  description text not null default '',
  colour text not null default '#174A35' check (colour ~ '^#[0-9A-Fa-f]{6}$'),
  deadline date not null,
  progress integer not null default 0 check (progress between 0 and 100),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create table public.task (
  id uuid primary key default gen_random_uuid(),
  project_id uuid not null references public.project(id) on delete cascade,
  title text not null check (length(trim(title)) between 1 and 200),
  deadline date not null,
  priority text not null default 'MEDIUM' check (priority in ('LOW','MEDIUM','HIGH')),
  note text not null default '',
  status text not null default 'OPEN' check (status in ('OPEN','DONE')),
  position integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index project_user_idx on public.project(user_id);
create index task_project_idx on public.task(project_id);

create function private.is_project_owner(p_project_id uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.project p
    where p.id = p_project_id
      and p.user_id = (select auth.uid())
  );
$$;
revoke all on function private.is_project_owner(uuid) from public, anon;
grant execute on function private.is_project_owner(uuid) to authenticated;

create function private.recompute_project_progress() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  v_project_id uuid;
begin
  if tg_op = 'DELETE' then
    v_project_id := old.project_id;
  else
    v_project_id := new.project_id;
  end if;

  update public.project p
  set progress = (
    select case
      when count(*) = 0 then 0
      else round(count(*) filter (where t.status = 'DONE')::numeric * 100 / count(*))::integer
    end
    from public.task t
    where t.project_id = v_project_id
  )
  where p.id = v_project_id;
  return null;
end;
$$;
revoke all on function private.recompute_project_progress() from public, anon;

create trigger project_progress_on_change
after insert or update or delete on public.task
for each row
execute function private.recompute_project_progress();

alter table public.project enable row level security;
alter table public.task enable row level security;
revoke all on public.project, public.task from anon, authenticated;
grant all on public.project, public.task to service_role;
grant select on public.project, public.task to authenticated;
grant insert (user_id, name, description, colour, deadline) on public.project to authenticated;
grant update (name, description, colour, deadline) on public.project to authenticated;
grant delete on public.project to authenticated;
grant insert (project_id, title, deadline, priority, note, status, position) on public.task to authenticated;
grant update (title, deadline, priority, note, status) on public.task to authenticated;
grant delete on public.task to authenticated;

create policy own_projects on public.project
  for all to authenticated
  using (user_id = (select auth.uid()))
  with check (user_id = (select auth.uid()));

create policy own_project_tasks on public.task
  for all to authenticated
  using (private.is_project_owner(project_id))
  with check (private.is_project_owner(project_id));
