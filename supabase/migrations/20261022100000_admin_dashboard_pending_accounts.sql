-- Replace the ambiguous "Users" tile with pending accounts, and name the
-- join-request tile for what it actually counts (team join requests, not accounts).
create or replace function private.admin_dashboard() returns jsonb
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
    'Pending accounts', count(*) filter (where status = 'pending'),
    'Active accounts', count(*) filter (where status = 'active'),
    'Suspended accounts', count(*) filter (where status = 'suspended'),
    'Companies', (select count(*) from public.company),
    'Teams', (select count(*) from public.team),
    'Pending join requests', (select count(*) from public.join_request where status = 'pending')
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
