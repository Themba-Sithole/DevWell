-- Member 5: limited manager reads (FR3, FR8). Only the SECURITY INVOKER wrappers are exposed through the Data API.
-- Returns names/emails of pending requesters and names of active engineers; never check-in or wellbeing data.
create function private.pending_join_requesters(p_team_id uuid)
returns table (request_id uuid, user_id uuid, name text, email text)
language plpgsql stable security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise sqlstate 'PT401' using message = 'Sign in first.'; end if;
  if not exists (select 1 from public."user" u
    join public.team t on t.id = p_team_id and t.manager_id = u.id and t.company_id = u.company_id
    join public.company c on c.id = t.company_id and c.status = 'active'
    where u.id = auth.uid() and u.status = 'active' and u.onboarding_done and u.role = 'manager') then
    raise sqlstate 'PT403' using message = 'You cannot view this team''s join requests.';
  end if;
  return query
    select jr.id, u.id, u.name, u.email
    from public.join_request jr
    join public."user" u on u.id = jr.user_id
    where jr.team_id = p_team_id and jr.status = 'pending'
    order by jr.created_at, jr.id;
end;
$$;
revoke all on function private.pending_join_requesters(uuid) from public, anon;
grant execute on function private.pending_join_requesters(uuid) to authenticated;

create function private.team_roster(p_team_id uuid)
returns table (user_id uuid, name text)
language plpgsql stable security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise sqlstate 'PT401' using message = 'Sign in first.'; end if;
  if not exists (select 1 from public."user" u
    join public.team t on t.id = p_team_id and t.manager_id = u.id and t.company_id = u.company_id
    join public.company c on c.id = t.company_id and c.status = 'active'
    where u.id = auth.uid() and u.status = 'active' and u.onboarding_done and u.role = 'manager') then
    raise sqlstate 'PT403' using message = 'You cannot view this team''s roster.';
  end if;
  return query
    select u.id, u.name
    from public."user" u
    where u.team_id = p_team_id and u.role = 'engineer' and u.status = 'active'
    order by u.name, u.id;
end;
$$;
revoke all on function private.team_roster(uuid) from public, anon;
grant execute on function private.team_roster(uuid) to authenticated;

-- Public RPC wrappers do not elevate permissions; the private helpers authorize auth.uid().
create function public.pending_join_requesters(p_team_id uuid)
returns table (request_id uuid, user_id uuid, name text, email text)
language sql stable security invoker set search_path = '' as $$
  select * from private.pending_join_requesters(p_team_id);
$$;
create function public.team_roster(p_team_id uuid)
returns table (user_id uuid, name text)
language sql stable security invoker set search_path = '' as $$
  select * from private.team_roster(p_team_id);
$$;
revoke all on function public.pending_join_requesters(uuid), public.team_roster(uuid) from public, anon;
grant execute on function public.pending_join_requesters(uuid), public.team_roster(uuid) to authenticated;
