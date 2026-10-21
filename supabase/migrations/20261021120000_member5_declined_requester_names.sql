-- Member 5: pending_join_requesters now also returns declined requests, so managers keep seeing the name.
-- Names only; same manager check as before.
create or replace function private.pending_join_requesters(p_team_id uuid)
returns table (request_id uuid, user_id uuid, name text)
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
    select jr.id, u.id, u.name
    from public.join_request jr
    join public."user" u on u.id = jr.user_id
    where jr.team_id = p_team_id and jr.status in ('pending', 'rejected')
    order by jr.created_at, jr.id;
end;
$$;
revoke all on function private.pending_join_requesters(uuid) from public, anon;
grant execute on function private.pending_join_requesters(uuid) to authenticated;
