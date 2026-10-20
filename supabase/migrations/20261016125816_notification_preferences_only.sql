create table public.notification_preference (
  user_id uuid primary key,
  daily_enabled boolean not null default false,
  reminder_time text not null default '09:00'
    check (reminder_time ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
  quiet_start text check (quiet_start is null or quiet_start ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
  quiet_end text check (quiet_end is null or quiet_end ~ '^([01][0-9]|2[0-3]):[0-5][0-9]$'),
  check_in_reminders boolean not null default true,
  team_alerts boolean not null default true,
  tip_nudges boolean not null default false
);

alter table public.notification_preference enable row level security;
revoke all on public.notification_preference from anon, authenticated;
grant select, insert, update on public.notification_preference to authenticated;
grant all on public.notification_preference to service_role;

create policy notification_preferences_are_private on public.notification_preference
  for all to authenticated
  using (
    user_id = (select auth.uid())
    and exists (
      select 1 from public."user" u
      where u.id = (select auth.uid()) and u.status = 'active' and u.onboarding_done
    )
  )
  with check (
    user_id = (select auth.uid())
    and exists (
      select 1 from public."user" u
      where u.id = (select auth.uid()) and u.status = 'active' and u.onboarding_done
    )
  );

notify pgrst, 'reload schema';