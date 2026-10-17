-- Members may edit their own display name from Settings > Account & profile.
-- Email and password changes stay with Supabase Auth: the email sync trigger
-- keeps public."user" aligned after the confirmation link is opened, and a
-- password never leaves the Auth service. A SECURITY DEFINER RPC owns the name
-- write so a member cannot reach role, status or team through the same call.
create function private.update_own_profile(p_name text) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare
  v_name text;
begin
  if auth.uid() is null then raise sqlstate 'PT401' using message = 'Sign in first.'; end if;
  v_name := left(trim(coalesce(p_name, '')), 150);
  if length(v_name) < 2 then
    raise sqlstate 'PT400' using message = 'Enter your full name.';
  end if;
  update public."user" set name = v_name where id = (select auth.uid());
  if not found then
    raise sqlstate 'PT409' using message = 'Your profile is not ready yet.';
  end if;
  return jsonb_build_object('name', v_name);
end;
$$;
revoke all on function private.update_own_profile(text) from public, anon;
grant execute on function private.update_own_profile(text) to authenticated;

create function public.update_own_profile(p_name text) returns jsonb
language sql security invoker set search_path = '' as $$
  select private.update_own_profile(p_name);
$$;
revoke all on function public.update_own_profile(text) from public, anon;
grant execute on function public.update_own_profile(text) to authenticated;
