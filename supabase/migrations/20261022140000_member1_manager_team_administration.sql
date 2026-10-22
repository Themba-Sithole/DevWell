-- Member 1: managers administer the teams they created.
-- A manager can remove an engineer from their own team, or delete that team.
-- Both actions end the engineer's placement: the profile is cleared back to the
-- onboarding state so the engineer must choose a company and team again, and the
-- existing join-request flow records the new request for approval.
-- Only the SECURITY INVOKER wrappers below are exposed through the Data API.

-- Same ownership rule as private.pending_join_requesters / private.team_roster:
-- active, onboarded manager of this team, in the team's company, active company.
-- Admins are deliberately excluded: they manage teams through the admin RPCs.
create function private.owns_team(p_team_id uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (
    select 1
    from public."user" u
    join public.team t on t.id = p_team_id and t.manager_id = u.id
      and t.company_id = u.company_id
    join public.company c on c.id = t.company_id and c.status = 'active'
    where u.id = (select auth.uid()) and u.role = 'manager'
      and u.status = 'active' and u.onboarding_done
  );
$$;
revoke all on function private.owns_team(uuid) from public, anon;

-- Removes one active engineer from a team the manager owns. The engineer keeps
-- their account and private check-ins, but loses the placement and the approved
-- request, so they must choose a company and team again before using the app.
create function private.remove_team_member(p_team_id uuid, p_user_id uuid) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_member public."user"%rowtype;
begin
  if auth.uid() is null then raise sqlstate 'PT401' using message = 'Sign in first.'; end if;
  if p_team_id is null or p_user_id is null then
    raise sqlstate 'PT400' using message = 'Choose a team member to remove.';
  end if;
  if not private.owns_team(p_team_id) then
    raise sqlstate 'PT403' using message = 'You can only manage a team you created.';
  end if;
  -- Same lock order as join-request review: the profile first, then the team.
  select * into v_member from public."user" where id = p_user_id for update;
  if not found or v_member.role <> 'engineer' or v_member.status <> 'active'
    or v_member.team_id is distinct from p_team_id then
    raise sqlstate 'PT404' using message = 'That member is no longer on this team.';
  end if;
  perform 1 from public.team where id = p_team_id for share;
  if not found then raise sqlstate 'PT404' using message = 'Team not found.'; end if;
  update public."user" set company_id = null, team_id = null,
    status = 'pending', onboarding_done = false where id = v_member.id;
  -- The approved request is this membership's record; delete it so the engineer
  -- can submit a fresh request (to this team or another one) after choosing again.
  delete from public.join_request where user_id = v_member.id and team_id = p_team_id;
  return jsonb_build_object('removed', true, 'user_id', v_member.id);
end;
$$;
revoke all on function private.remove_team_member(uuid, uuid) from public, anon;
grant execute on function private.remove_team_member(uuid, uuid) to authenticated;

-- Deletes a team the manager owns. Every engineer placed on it (active or still
-- waiting) goes back to onboarding; rejected engineers keep that status but lose
-- the now-dangling placement. Pending and past requests for the team are removed.
-- The manager's own profile moves to another team they own, if one exists.
create function private.delete_team(p_team_id uuid) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_team public.team%rowtype;
  v_next_team uuid;
  v_members_reset integer;
begin
  if auth.uid() is null then raise sqlstate 'PT401' using message = 'Sign in first.'; end if;
  if p_team_id is null then raise sqlstate 'PT400' using message = 'Choose a team to delete.'; end if;
  perform 1 from public.team where id = p_team_id;
  if not found then raise sqlstate 'PT404' using message = 'Team not found.'; end if;
  if not private.owns_team(p_team_id) then
    raise sqlstate 'PT403' using message = 'You can only delete a team you created.';
  end if;
  -- Lock the affected profiles first, then the team, matching join-request review.
  perform 1 from public."user" where team_id = p_team_id order by id for update;
  select * into v_team from public.team where id = p_team_id for update;
  if not found then raise sqlstate 'PT404' using message = 'Team not found.'; end if;
  update public."user" set company_id = null, team_id = null,
    status = case when status in ('active', 'pending') then 'pending' else status end,
    onboarding_done = case when status in ('active', 'pending') then false else onboarding_done end
    where team_id = p_team_id and role = 'engineer';
  get diagnostics v_members_reset = row_count;
  delete from public.join_request where team_id = p_team_id;
  -- Any other profile (an admin, for example) that points at this team loses the reference.
  update public."user" set team_id = null where team_id = p_team_id;
  -- Keep the manager on a team they still own so their dashboard keeps working.
  select t.id into v_next_team from public.team t
    where t.company_id = v_team.company_id and t.manager_id = (select auth.uid())
      and t.id <> p_team_id
    order by t.name, t.id limit 1;
  if v_next_team is not null then
    update public."user" set team_id = v_next_team
      where id = (select auth.uid()) and team_id is null;
  end if;
  delete from public.team where id = p_team_id;
  return jsonb_build_object('deleted', true, 'team_id', p_team_id,
    'members_reset', v_members_reset);
end;
$$;
revoke all on function private.delete_team(uuid) from public, anon;
grant execute on function private.delete_team(uuid) to authenticated;

-- Public RPC wrappers do not elevate permissions. The private helpers independently
-- authorize auth.uid(), so calling the RPC directly cannot bypass those checks.
create function public.remove_team_member(p_team_id uuid, p_user_id uuid) returns jsonb
language sql security invoker set search_path = '' as $$
  select private.remove_team_member(p_team_id, p_user_id);
$$;
create function public.delete_team(p_team_id uuid) returns jsonb
language sql security invoker set search_path = '' as $$
  select private.delete_team(p_team_id);
$$;
revoke all on function public.remove_team_member(uuid, uuid), public.delete_team(uuid) from public, anon;
grant execute on function public.remove_team_member(uuid, uuid), public.delete_team(uuid) to authenticated;
