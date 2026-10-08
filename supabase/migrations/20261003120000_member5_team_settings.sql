-- Member 5: team settings (FR10). Only the SECURITY INVOKER wrapper is exposed through the Data API.
alter table public.team
  add column timezone text not null default 'Africa/Johannesburg',
  add column check_in_cadence text not null default 'Daily'
    check (check_in_cadence in ('Daily', 'Weekly')),
  add column low_wellbeing_threshold numeric(2,1) not null default 2.0
    check (low_wellbeing_threshold between 1.0 and 4.0);

create function private.update_team_settings(p_team_id uuid, p_name text, p_timezone text,
  p_check_in_cadence text, p_low_wellbeing_threshold numeric) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_name text := trim(p_name);
  v_team public.team%rowtype;
begin
  if auth.uid() is null then raise sqlstate 'PT401' using message = 'Sign in first.'; end if;
  if v_name is null or v_name = '' then
    raise sqlstate 'PT400' using message = 'Team name cannot be empty.';
  end if;
  if p_check_in_cadence is null or p_check_in_cadence not in ('Daily', 'Weekly') then
    raise sqlstate 'PT400' using message = 'Choose a check-in cadence: Daily or Weekly.';
  end if;
  if p_low_wellbeing_threshold is null or p_low_wellbeing_threshold not between 1.0 and 4.0 then
    raise sqlstate 'PT400' using message = 'Low-wellbeing threshold must be between 1.0 and 4.0.';
  end if;
  if p_timezone is null or not exists (select 1 from pg_catalog.pg_timezone_names where name = p_timezone) then
    raise sqlstate 'PT400' using message = 'Choose a valid time zone.';
  end if;
  -- Same lock order as review_join_request: caller profile first, then the team.
  perform 1 from public."user" where id = auth.uid() and status = 'active'
    and onboarding_done and role = 'manager' for share;
  if not found then raise sqlstate 'PT403' using message = 'An active manager account is required.'; end if;
  select t.* into v_team from public.team t
    join public.company c on c.id = t.company_id and c.status = 'active'
    join public."user" u on u.id = auth.uid() and u.company_id = t.company_id
    where t.id = p_team_id and t.manager_id = auth.uid()
    for update of t;
  if not found then raise sqlstate 'PT403' using message = 'You cannot change this team''s settings.'; end if;
  -- Older teams may have names over 60 characters; only a new name must meet the app limit.
  if v_name <> trim(v_team.name) and length(v_name) > 60 then
    raise sqlstate 'PT400' using message = 'Team name must be 60 characters or fewer.';
  end if;
  begin
    update public.team set name = v_name, timezone = p_timezone,
      check_in_cadence = p_check_in_cadence, low_wellbeing_threshold = p_low_wellbeing_threshold
      where id = v_team.id returning * into v_team;
  exception when unique_violation then
    raise sqlstate 'PT409' using message = 'Another team in your company already uses this name.';
  end;
  return jsonb_build_object('id', v_team.id, 'company_id', v_team.company_id, 'name', v_team.name,
    'manager_id', v_team.manager_id, 'timezone', v_team.timezone,
    'check_in_cadence', v_team.check_in_cadence, 'low_wellbeing_threshold', v_team.low_wellbeing_threshold);
end;
$$;
revoke all on function private.update_team_settings(uuid, text, text, text, numeric) from public, anon;
grant execute on function private.update_team_settings(uuid, text, text, text, numeric) to authenticated;

-- Public RPC wrapper does not elevate permissions; the private helper authorizes auth.uid().
create function public.update_team_settings(p_team_id uuid, p_name text, p_timezone text,
  p_check_in_cadence text, p_low_wellbeing_threshold numeric) returns jsonb
language sql security invoker set search_path = '' as $$
  select private.update_team_settings(p_team_id, p_name, p_timezone, p_check_in_cadence, p_low_wellbeing_threshold);
$$;
revoke all on function public.update_team_settings(uuid, text, text, text, numeric) from public, anon;
grant execute on function public.update_team_settings(uuid, text, text, text, numeric) to authenticated;
