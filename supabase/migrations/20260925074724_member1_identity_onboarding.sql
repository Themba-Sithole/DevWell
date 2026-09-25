-- Member 1: identity, directory and transactional join requests.
-- Only the small SECURITY INVOKER wrappers below are exposed through the Data API.
create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated;

create table public.company (
  id uuid primary key default gen_random_uuid(),
  name text not null check (length(trim(name)) between 1 and 150),
  status text not null default 'pending' check (status in ('pending','active')),
  email_domain text,
  created_at timestamptz not null default now()
);
create table public.team (
  id uuid primary key default gen_random_uuid(),
  company_id uuid not null references public.company(id),
  name text not null check (length(trim(name)) between 1 and 150),
  manager_id uuid,
  created_at timestamptz not null default now(),
  unique (id, company_id),
  unique (company_id, name)
);
create table public."user" (
  id uuid primary key references auth.users(id) on delete cascade,
  name text not null,
  email text not null,
  role text not null default 'engineer' check (role in ('engineer','manager','admin')),
  status text not null default 'pending' check (status in ('pending','active','rejected')),
  company_id uuid references public.company(id),
  team_id uuid,
  onboarding_done boolean not null default false,
  created_at timestamptz not null default now(),
  foreign key (team_id, company_id) references public.team(id, company_id),
  check (team_id is null or company_id is not null),
  check (status <> 'active' or onboarding_done),
  check (role <> 'engineer' or status <> 'active' or (company_id is not null and team_id is not null))
);
alter table public.team add constraint team_manager_id_fkey
  foreign key (manager_id) references public."user"(id) on delete set null;
create table public.join_request (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public."user"(id) on delete cascade,
  company_id uuid not null references public.company(id),
  team_id uuid not null,
  status text not null default 'pending' check (status in ('pending','approved','rejected')),
  created_at timestamptz not null default now(),
  reviewed_by uuid references public."user"(id) on delete set null,
  reviewed_at timestamptz,
  foreign key (team_id, company_id) references public.team(id, company_id),
  check ((status = 'pending' and reviewed_at is null and reviewed_by is null)
    or (status <> 'pending' and reviewed_at is not null))
);
create unique index join_request_one_pending on public.join_request(user_id) where status = 'pending';
create index join_request_user_idx on public.join_request(user_id);
create index join_request_team_company_idx on public.join_request(team_id, company_id);
create index join_request_company_idx on public.join_request(company_id);
create index join_request_reviewer_idx on public.join_request(reviewed_by);
create index user_company_idx on public."user"(company_id);
create index user_team_company_idx on public."user"(team_id, company_id);
create index team_manager_idx on public.team(manager_id);

-- Signup metadata may supply a name, never privileges.
create function private.create_profile() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public."user" (id, name, email)
  values (new.id, left(coalesce(nullif(trim(new.raw_user_meta_data->>'name'), ''), 'Student'), 150),
    coalesce(new.email, ''));
  return new;
end;
$$;
revoke all on function private.create_profile() from public, anon, authenticated;
create trigger devwell_create_profile after insert on auth.users
for each row execute function private.create_profile();

create function private.sync_profile_email() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  update public."user" set email = coalesce(new.email, '') where id = new.id;
  return new;
end;
$$;
revoke all on function private.sync_profile_email() from public, anon, authenticated;
create trigger devwell_sync_profile_email after update of email on auth.users
for each row execute function private.sync_profile_email();

-- Safe backfill if this migration is later run on a project with existing Auth users.
insert into public."user" (id, name, email)
select id, left(coalesce(nullif(trim(raw_user_meta_data->>'name'), ''), 'Student'), 150), coalesce(email, '')
from auth.users on conflict (id) do nothing;

create function private.can_review_team(p_team_id uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public."user" u
    join public.team t on t.id = p_team_id
    join public.company c on c.id = t.company_id and c.status = 'active'
    where u.id = (select auth.uid()) and u.status = 'active' and u.onboarding_done
      and ((u.role = 'manager' and t.manager_id = u.id and u.company_id = t.company_id)
        or u.role = 'admin')
  );
$$;
revoke all on function private.can_review_team(uuid) from public, anon;
grant execute on function private.can_review_team(uuid) to authenticated;

alter table public."user" enable row level security;
alter table public.company enable row level security;
alter table public.team enable row level security;
alter table public.join_request enable row level security;
revoke all on public."user", public.company, public.team, public.join_request from anon, authenticated;
grant select on public."user", public.company, public.team, public.join_request to authenticated;
grant all on public."user", public.company, public.team, public.join_request to service_role;
create policy own_profile on public."user" for select to authenticated using (id = (select auth.uid()));
create policy active_companies on public.company for select to authenticated using (status = 'active');
create policy active_company_teams on public.team for select to authenticated using (
  exists (select 1 from public.company c where c.id = company_id and c.status = 'active')
);
create policy own_or_managed_requests on public.join_request for select to authenticated using (
  user_id = (select auth.uid()) or private.can_review_team(team_id)
);

-- Lock each engineer's profile to serialize duplicate or conflicting submissions.
create function private.submit_join_request(p_company_id uuid, p_team_id uuid) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_user public."user"%rowtype;
  v_request public.join_request%rowtype;
begin
  if auth.uid() is null then raise sqlstate 'PT401' using message = 'Sign in first.'; end if;
  select * into v_user from public."user" where id = auth.uid() for update;
  if not found or v_user.role <> 'engineer' then
    raise sqlstate 'PT403' using message = 'Only engineers can submit join requests.';
  end if;
  perform 1 from public.company where id = p_company_id and status = 'active' for share;
  if not found then raise sqlstate 'PT400' using message = 'Select an active company.'; end if;
  perform 1 from public.team where id = p_team_id and company_id = p_company_id for share;
  if not found then raise sqlstate 'PT400' using message = 'The team does not belong to this company.'; end if;
  select * into v_request from public.join_request where user_id = v_user.id and status = 'pending';
  if found then
    if v_request.company_id = p_company_id and v_request.team_id = p_team_id
      and v_user.status = 'pending' and v_user.onboarding_done
      and v_user.company_id = p_company_id and v_user.team_id = p_team_id then
      return jsonb_build_object('request_id', v_request.id, 'status', 'pending');
    end if;
    raise sqlstate 'PT409' using message = 'A different request is already pending.';
  end if;
  if v_user.status <> 'pending' or v_user.onboarding_done then
    raise sqlstate 'PT403' using message = 'This account is not eligible for onboarding.';
  end if;
  insert into public.join_request(user_id, company_id, team_id)
  values (v_user.id, p_company_id, p_team_id) returning * into v_request;
  update public."user" set company_id = p_company_id, team_id = p_team_id,
    status = 'pending', onboarding_done = true where id = v_user.id;
  return jsonb_build_object('request_id', v_request.id, 'status', 'pending');
end;
$$;
revoke all on function private.submit_join_request(uuid, uuid) from public, anon;
grant execute on function private.submit_join_request(uuid, uuid) to authenticated;

create function private.review_join_request(p_request_id uuid, p_approve boolean) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_user_id uuid;
  v_user public."user"%rowtype;
  v_request public.join_request%rowtype;
  v_status text;
begin
  if auth.uid() is null then raise sqlstate 'PT401' using message = 'Sign in first.'; end if;
  if p_approve is null then raise sqlstate 'PT400' using message = 'approve must be true or false.'; end if;
  -- Hold reviewer and team stable during authorization and update.
  perform 1 from public."user" where id = auth.uid() and status = 'active'
    and onboarding_done and role in ('manager', 'admin') for share;
  if not found then raise sqlstate 'PT403' using message = 'An active reviewer account is required.'; end if;
  select user_id into v_user_id from public.join_request where id = p_request_id;
  if not found then raise sqlstate 'PT403' using message = 'You cannot review this request.'; end if;
  -- Same target-profile-first lock order as submission.
  select * into v_user from public."user" where id = v_user_id for update;
  select * into v_request from public.join_request where id = p_request_id for update;
  perform 1 from public.company where id = v_request.company_id for share;
  perform 1 from public.team where id = v_request.team_id for share;
  if not private.can_review_team(v_request.team_id) then
    raise sqlstate 'PT403' using message = 'You cannot review this request.';
  end if;
  v_status := case when p_approve then 'approved' else 'rejected' end;
  if v_request.status = v_status then
    return jsonb_build_object('request_id', v_request.id, 'status', v_status);
  end if;
  if v_request.status <> 'pending' then
    raise sqlstate 'PT409' using message = 'This request has already been reviewed.';
  end if;
  if v_user.role <> 'engineer' or v_user.status <> 'pending' or not v_user.onboarding_done
    or v_user.company_id is distinct from v_request.company_id
    or v_user.team_id is distinct from v_request.team_id then
    raise sqlstate 'PT409' using message = 'The profile no longer matches this request.';
  end if;
  update public.join_request set status = v_status, reviewed_by = auth.uid(), reviewed_at = now()
    where id = v_request.id;
  update public."user" set status = case when p_approve then 'active' else 'rejected' end
    where id = v_user_id;
  return jsonb_build_object('request_id', v_request.id, 'status', v_status);
end;
$$;
revoke all on function private.review_join_request(uuid, boolean) from public, anon;
grant execute on function private.review_join_request(uuid, boolean) to authenticated;

-- Public RPC wrappers do not elevate permissions. Private helpers independently
-- authorize auth.uid(), so calling RPC directly cannot bypass the Edge Function.
create function public.submit_join_request(p_company_id uuid, p_team_id uuid) returns jsonb
language sql security invoker set search_path = '' as $$
  select private.submit_join_request(p_company_id, p_team_id);
$$;
create function public.review_join_request(p_request_id uuid, p_approve boolean) returns jsonb
language sql security invoker set search_path = '' as $$
  select private.review_join_request(p_request_id, p_approve);
$$;
revoke all on function public.submit_join_request(uuid, uuid), public.review_join_request(uuid, boolean) from public, anon;
grant execute on function public.submit_join_request(uuid, uuid), public.review_join_request(uuid, boolean) to authenticated;
