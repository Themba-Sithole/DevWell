-- Keep check-in data in the reproducible schema and revoke access immediately
-- when an account is suspended, even while its existing Auth session is valid.
create table if not exists public.check_in (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  responses jsonb not null,
  created_at timestamptz not null default now()
);

create index if not exists check_in_user_id_created_at_idx
  on public.check_in(user_id, created_at desc);

alter table public.check_in enable row level security;
revoke all on public.check_in from anon, authenticated;
grant all on public.check_in to service_role;
grant select, insert, update on public.check_in to authenticated;

drop policy if exists check_in_insert_own on public.check_in;
create policy check_in_insert_own on public.check_in
  for insert to authenticated
  with check (
    user_id = (select auth.uid())
    and private.is_active_account()
  );

drop policy if exists check_in_select_own on public.check_in;
create policy check_in_select_own on public.check_in
  for select to authenticated
  using (
    user_id = (select auth.uid())
    and private.is_active_account()
  );

drop policy if exists check_in_update_own on public.check_in;
create policy check_in_update_own on public.check_in
  for update to authenticated
  using (
    user_id = (select auth.uid())
    and private.is_active_account()
  )
  with check (
    user_id = (select auth.uid())
    and private.is_active_account()
  );
