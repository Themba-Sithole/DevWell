-- Member 1: self-service manager registration.
-- A signup may now ask for the manager role. Only engineer and manager are selectable,
-- so the admin role is still never granted from signup metadata. A manager becomes
-- active by creating their own company and first team, and can then add more teams.
-- Only the small SECURITY INVOKER wrappers below are exposed through the Data API.

-- The profile trigger honours a requested signup role, restricted to engineer/manager.
-- Any other value, including admin, falls back to engineer.
create or replace function private.create_profile() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public."user" (id, name, email, role, status)
  values (
    new.id,
    left(coalesce(nullif(trim(new.raw_user_meta_data->>'name'), ''), 'Student'), 150),
    coalesce(new.email, ''),
    case when lower(trim(coalesce(new.raw_user_meta_data->>'role', ''))) = 'manager'
      then 'manager' else 'engineer' end,
    'pending'
  );
  return new;
end;
$$;
revoke all on function private.create_profile() from public, anon, authenticated;

-- A manager creates their company and their first team in one transaction.
-- The company starts active so engineers can select it and request to join its team.
create function private.create_manager_company(p_company_name text, p_team_name text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_user public."user"%rowtype;
  v_company public.company%rowtype;
  v_team public.team%rowtype;
  v_company_name text;
  v_team_name text;
begin
  if auth.uid() is null then raise sqlstate 'PT401' using message = 'Sign in first.'; end if;
  v_company_name := trim(coalesce(p_company_name, ''));
  v_team_name := trim(coalesce(p_team_name, ''));
  if v_company_name = '' then
    raise sqlstate 'PT400' using message = 'Enter your company name.';
  end if;
  if length(v_company_name) > 150 then
    raise sqlstate 'PT400' using message = 'Company name must be 150 characters or fewer.';
  end if;
  if v_team_name = '' then
    raise sqlstate 'PT400' using message = 'Enter your first team name.';
  end if;
  if length(v_team_name) > 60 then
    raise sqlstate 'PT400' using message = 'Team name must be 60 characters or fewer.';
  end if;
  -- Lock the caller's profile so a repeated tap cannot create two companies.
  select * into v_user from public."user" where id = auth.uid() for update;
  if not found then raise sqlstate 'PT401' using message = 'Sign in first.'; end if;
  if v_user.role <> 'manager' then
    raise sqlstate 'PT403' using message = 'Only manager accounts can create a company.';
  end if;
  if v_user.status in ('suspended', 'rejected') then
    raise sqlstate 'PT403' using message = 'This account cannot create a company.';
  end if;
  if v_user.onboarding_done then
    raise sqlstate 'PT409' using message = 'Your company already exists.';
  end if;
  if v_user.status <> 'pending' then
    raise sqlstate 'PT403' using message = 'This account cannot create a company.';
  end if;
  insert into public.company (name, status) values (v_company_name, 'active') returning * into v_company;
  insert into public.team (company_id, name, manager_id)
    values (v_company.id, v_team_name, auth.uid()) returning * into v_team;
  -- The manager owns the company and its first team, so onboarding is complete.
  update public."user" set company_id = v_company.id, team_id = v_team.id,
    status = 'active', onboarding_done = true where id = v_user.id;
  return jsonb_build_object('company_id', v_company.id, 'team_id', v_team.id,
    'company_name', v_company.name, 'team_name', v_team.name, 'status', 'active');
end;
$$;
revoke all on function private.create_manager_company(text, text) from public, anon;
grant execute on function private.create_manager_company(text, text) to authenticated;

-- An active manager adds another team to their own company.
create function private.create_team(p_team_name text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_user public."user"%rowtype;
  v_company public.company%rowtype;
  v_team public.team%rowtype;
  v_team_name text;
begin
  if auth.uid() is null then raise sqlstate 'PT401' using message = 'Sign in first.'; end if;
  v_team_name := trim(coalesce(p_team_name, ''));
  if v_team_name = '' then
    raise sqlstate 'PT400' using message = 'Enter a team name.';
  end if;
  if length(v_team_name) > 60 then
    raise sqlstate 'PT400' using message = 'Team name must be 60 characters or fewer.';
  end if;
  select * into v_user from public."user" where id = auth.uid();
  if not found then raise sqlstate 'PT401' using message = 'Sign in first.'; end if;
  if v_user.role <> 'manager' or v_user.status <> 'active' or not v_user.onboarding_done then
    raise sqlstate 'PT403' using message = 'Only an active manager can create a team.';
  end if;
  if v_user.company_id is null then
    raise sqlstate 'PT403' using message = 'Your account does not have a company.';
  end if;
  select * into v_company from public.company where id = v_user.company_id;
  if not found or v_company.status <> 'active' then
    raise sqlstate 'PT403' using message = 'Your company is not active.';
  end if;
  if exists (select 1 from public.team where company_id = v_company.id and lower(name) = lower(v_team_name)) then
    raise sqlstate 'PT409' using message = 'Another team in your company already uses this name.';
  end if;
  insert into public.team (company_id, name, manager_id)
    values (v_company.id, v_team_name, auth.uid()) returning * into v_team;
  return jsonb_build_object('team_id', v_team.id, 'company_id', v_company.id, 'team_name', v_team.name);
end;
$$;
revoke all on function private.create_team(text) from public, anon;
grant execute on function private.create_team(text) to authenticated;

-- Public RPC wrappers do not elevate permissions. Private helpers independently
-- authorize auth.uid(), so calling RPC directly cannot bypass those checks.
create function public.create_manager_company(p_company_name text, p_team_name text) returns jsonb
language sql security invoker set search_path = '' as $$
  select private.create_manager_company(p_company_name, p_team_name);
$$;
create function public.create_team(p_team_name text) returns jsonb
language sql security invoker set search_path = '' as $$
  select private.create_team(p_team_name);
$$;
revoke all on function public.create_manager_company(text, text), public.create_team(text) from public, anon;
grant execute on function public.create_manager_company(text, text), public.create_team(text) to authenticated;
