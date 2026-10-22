-- Member 1: an engineer whose request was not approved can ask to join a different
-- company and team. A rejection is final for that request, but it must not lock the
-- account out of onboarding forever. This clears the rejected placement and returns
-- the profile to the same state a brand-new engineer has, so the normal join-request
-- flow can record the next request. The old rejected request stays with its team as
-- history for that manager.
-- Only the SECURITY INVOKER wrapper below is exposed through the Data API.

create function private.restart_team_onboarding() returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_user public."user"%rowtype;
begin
  if auth.uid() is null then raise sqlstate 'PT401' using message = 'Sign in first.'; end if;
  select * into v_user from public."user" where id = (select auth.uid()) for update;
  if not found or v_user.role <> 'engineer' then
    raise sqlstate 'PT403' using message = 'Only an engineer can choose a new team.';
  end if;
  if v_user.status = 'suspended' then
    raise sqlstate 'PT409' using message = 'This account is suspended. Contact your administrator.';
  end if;
  if v_user.status <> 'rejected' then
    raise sqlstate 'PT409' using message = 'Only a request that was not approved can choose another team.';
  end if;
  update public."user" set company_id = null, team_id = null,
    status = 'pending', onboarding_done = false
    where id = v_user.id;
  return jsonb_build_object('restarted', true);
end;
$$;
revoke all on function private.restart_team_onboarding() from public, anon;
grant execute on function private.restart_team_onboarding() to authenticated;

create function public.restart_team_onboarding() returns jsonb
language sql security invoker set search_path = '' as $$
  select private.restart_team_onboarding();
$$;
revoke all on function public.restart_team_onboarding() from public, anon;
grant execute on function public.restart_team_onboarding() to authenticated;
