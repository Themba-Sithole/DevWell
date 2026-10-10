-- Run in the Supabase SQL Editor after noblessensei@gmail.com has registered and confirmed email.
-- This provisions the existing profile as the manager for the Software Engineering team.
begin;

do $$
declare
  v_company_id uuid;
  v_team_id uuid;
  v_manager_id uuid;
  v_match_count integer;
  v_manager_email constant text := 'noblessensei@gmail.com';
begin
  select count(*) into v_match_count
  from public."user" u
  join auth.users a on a.id = u.id
  where lower(u.email) = lower(v_manager_email)
    and lower(a.email) = lower(v_manager_email)
    and a.email_confirmed_at is not null;

  if v_match_count = 0 then
    raise exception 'No confirmed DevWell profile found for the configured manager email. Register and confirm the account first, then run this script again.';
  elsif v_match_count > 1 then
    raise exception 'Multiple confirmed profiles found for the configured manager email; refusing to choose a manager account.';
  end if;

  select id into v_manager_id
  from public."user" u
  join auth.users a on a.id = u.id
  where lower(u.email) = lower(v_manager_email)
    and lower(a.email) = lower(v_manager_email)
    and a.email_confirmed_at is not null;

  select count(*) into v_match_count
  from public.company
  where name = 'DevWell';

  if v_match_count > 1 then
    raise exception 'Multiple companies named DevWell exist; refusing to choose one.';
  elsif v_match_count = 0 then
    insert into public.company (name, status)
    values ('DevWell', 'active')
    returning id into v_company_id;
  else
    select id into v_company_id
    from public.company
    where name = 'DevWell';

    update public.company
    set status = 'active'
    where id = v_company_id;
  end if;

  select count(*) into v_match_count
  from public.team
  where company_id = v_company_id and name = 'Software Engineering';

  if v_match_count > 1 then
    raise exception 'Multiple Software Engineering teams exist for DevWell; refusing to choose one.';
  elsif v_match_count = 0 then
    insert into public.team (company_id, name)
    values (v_company_id, 'Software Engineering')
    returning id into v_team_id;
  else
    select id into v_team_id
    from public.team
    where company_id = v_company_id and name = 'Software Engineering';
  end if;

  update public."user"
  set role = 'manager',
      status = 'active',
      onboarding_done = true,
      company_id = v_company_id
  where id = v_manager_id;

  update public.team
  set manager_id = v_manager_id
  where id = v_team_id and company_id = v_company_id;
end;
$$;

commit;