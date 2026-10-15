-- Member 6: expose a privacy-safe admin account dashboard and audited status controls.
alter table public."user" drop constraint if exists user_status_check;
alter table public."user"
  add constraint user_status_check check (status in ('pending', 'active', 'rejected', 'suspended'));

create table private.admin_account_status_audit (
  id bigint generated always as identity primary key,
  actor_id uuid references public."user"(id) on delete set null,
  target_user_id uuid references public."user"(id) on delete cascade,
  previous_status text not null check (previous_status in ('active', 'suspended')),
  new_status text not null check (new_status in ('active', 'suspended')),
  changed_at timestamptz not null default now()
);
revoke all on table private.admin_account_status_audit from public, anon, authenticated;

create function private.admin_dashboard() returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_stats jsonb;
  v_users jsonb;
begin
  if auth.uid() is null then raise sqlstate 'PT401' using message = 'Sign in first.'; end if;
  perform 1 from public."user"
    where id = auth.uid() and role = 'admin' and status = 'active' and onboarding_done
    for share;
  if not found then raise sqlstate 'PT403' using message = 'An active admin account is required.'; end if;

  select jsonb_build_object(
    'Users', count(*),
    'Active accounts', count(*) filter (where status = 'active'),
    'Suspended accounts', count(*) filter (where status = 'suspended'),
    'Companies', (select count(*) from public.company),
    'Teams', (select count(*) from public.team),
    'Pending requests', (select count(*) from public.join_request where status = 'pending')
  )
  into v_stats
  from public."user";

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'id', u.id,
        'name', u.name,
        'email', u.email,
        'role', u.role,
        'status', u.status,
        'companyId', u.company_id,
        'teamId', u.team_id,
        'teamName', t.name,
        'onboardingDone', u.onboarding_done
      ) order by u.created_at desc, u.id
    ),
    '[]'::jsonb
  )
  into v_users
  from public."user" u
  left join public.team t on t.id = u.team_id and t.company_id = u.company_id;

  return jsonb_build_object('stats', v_stats, 'users', v_users);
end;
$$;
revoke all on function private.admin_dashboard() from public, anon;
grant execute on function private.admin_dashboard() to authenticated;

create function private.admin_set_user_status(p_user_id uuid, p_status text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_user public."user"%rowtype;
begin
  if auth.uid() is null then raise sqlstate 'PT401' using message = 'Sign in first.'; end if;
  perform 1 from public."user"
    where id = auth.uid() and role = 'admin' and status = 'active' and onboarding_done
    for share;
  if not found then raise sqlstate 'PT403' using message = 'An active admin account is required.'; end if;
  if p_user_id is null or p_status is null or p_status not in ('active', 'suspended') then
    raise sqlstate 'PT400' using message = 'Choose an account and a valid status.';
  end if;
  if p_user_id = auth.uid() then
    raise sqlstate 'PT403' using message = 'You cannot change your own account status.';
  end if;

  select * into v_user from public."user" where id = p_user_id for update;
  if not found or v_user.role = 'admin' or v_user.status not in ('active', 'suspended') then
    raise sqlstate 'PT403' using message = 'This account cannot be managed here.';
  end if;
  if v_user.status = p_status then
    return jsonb_build_object('user_id', v_user.id, 'status', v_user.status);
  end if;

  update public."user" set status = p_status where id = v_user.id;
  insert into private.admin_account_status_audit(actor_id, target_user_id, previous_status, new_status)
  values (auth.uid(), v_user.id, v_user.status, p_status);

  return jsonb_build_object('user_id', v_user.id, 'status', p_status);
end;
$$;
revoke all on function private.admin_set_user_status(uuid, text) from public, anon;
grant execute on function private.admin_set_user_status(uuid, text) to authenticated;

-- These invoker wrappers expose only functions that independently authorize auth.uid().
create function public.admin_dashboard() returns jsonb
language sql security invoker set search_path = '' as $$
  select private.admin_dashboard();
$$;
create function public.admin_set_user_status(p_user_id uuid, p_status text) returns jsonb
language sql security invoker set search_path = '' as $$
  select private.admin_set_user_status(p_user_id, p_status);
$$;
revoke all on function public.admin_dashboard(), public.admin_set_user_status(uuid, text) from public, anon;
grant execute on function public.admin_dashboard(), public.admin_set_user_status(uuid, text) to authenticated;

-- Recheck account status in the database so a suspended session cannot keep using
-- protected project/task APIs until the client refreshes its profile.
create function private.is_active_account() returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public."user"
    where id = (select auth.uid()) and status = 'active' and onboarding_done
  );
$$;
revoke all on function private.is_active_account() from public, anon;
grant execute on function private.is_active_account() to authenticated;

drop policy own_projects on public.project;
create policy own_projects on public.project
  for all to authenticated
  using (user_id = (select auth.uid()) and private.is_active_account())
  with check (user_id = (select auth.uid()) and private.is_active_account());

drop policy own_project_tasks on public.task;
create policy own_project_tasks on public.task
  for all to authenticated
  using (private.is_project_owner(project_id) and private.is_active_account())
  with check (private.is_project_owner(project_id) and private.is_active_account());
