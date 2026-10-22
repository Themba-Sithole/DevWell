-- Member 5: headcount per team for the onboarding team picker. Only the SECURITY INVOKER wrapper is exposed through the Data API.
-- Returns team id and count only; never names, user ids or wellbeing data. Members match team_roster: active engineers.
create function private.team_member_counts()
returns table (team_id uuid, member_count integer)
language plpgsql stable security definer set search_path = '' as $$
begin
  if auth.uid() is null then raise sqlstate 'PT401' using message = 'Sign in first.'; end if;
  return query
    select t.id, count(u.id)::integer
    from public.team t
    join public.company c on c.id = t.company_id and c.status = 'active'
    left join public."user" u on u.team_id = t.id and u.role = 'engineer'
      and u.status = 'active' and u.onboarding_done
    group by t.id;
end;
$$;
revoke all on function private.team_member_counts() from public, anon;
grant execute on function private.team_member_counts() to authenticated;

-- Public RPC wrapper does not elevate permissions; the private helper checks auth.uid().
create function public.team_member_counts()
returns table (team_id uuid, member_count integer)
language sql stable security invoker set search_path = '' as $$
  select * from private.team_member_counts();
$$;
revoke all on function public.team_member_counts() from public, anon;
grant execute on function public.team_member_counts() to authenticated;
