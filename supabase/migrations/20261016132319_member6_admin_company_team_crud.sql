-- Reconstructed from the live database on 2026-10-21.
-- The admin company and team CRUD functions were deployed to the live project
-- under this version but the file was never committed. The live database was the
-- source of truth for this file, so a fresh local database now matches production.
-- Each function authorises itself with private.is_active_admin(); the public
-- wrappers only forward to the private implementation.


CREATE OR REPLACE FUNCTION "private"."is_active_admin"() RETURNS boolean
    LANGUAGE "sql" STABLE SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
  select exists (
    select 1 from public."user" u
    where u.id = (select auth.uid()) and u.role = 'admin'
      and u.status = 'active' and u.onboarding_done
  );
$$;
revoke all on function private.is_active_admin() from public, anon;
grant execute on function private.is_active_admin() to authenticated;

CREATE OR REPLACE FUNCTION "private"."admin_list_companies"() RETURNS "jsonb"
    LANGUAGE "plpgsql" STABLE SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
begin
  if not private.is_active_admin() then raise sqlstate 'PT403' using message = 'An active administrator account is required.'; end if;
  return coalesce((select jsonb_agg(jsonb_build_object(
    'id', c.id, 'name', c.name, 'email_domain', c.email_domain, 'status', c.status
  ) order by c.name) from public.company c), '[]'::jsonb);
end;
$$;
revoke all on function private.admin_list_companies() from public, anon;
grant execute on function private.admin_list_companies() to authenticated;

CREATE OR REPLACE FUNCTION "private"."admin_upsert_company"("p_company_id" "uuid", "p_name" "text", "p_email_domain" "text", "p_status" "text") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
declare v_company public.company%rowtype;
begin
  if not private.is_active_admin() then raise sqlstate 'PT403' using message = 'An active administrator account is required.'; end if;
  if p_name is null or length(trim(p_name)) not between 1 and 150
    or p_status is null or p_status not in ('active', 'pending') then
    raise sqlstate 'PT400' using message = 'Provide a company name and valid status.';
  end if;
  if p_company_id is null then
    insert into public.company(name, email_domain, status)
    values (trim(p_name), nullif(lower(trim(p_email_domain)), ''), p_status) returning * into v_company;
  else
    update public.company set name = trim(p_name), email_domain = nullif(lower(trim(p_email_domain)), ''), status = p_status
      where id = p_company_id returning * into v_company;
    if not found then raise sqlstate 'PT404' using message = 'Company not found.'; end if;
  end if;
  return jsonb_build_object('id', v_company.id, 'name', v_company.name, 'email_domain', v_company.email_domain, 'status', v_company.status);
end;
$$;
revoke all on function private.admin_upsert_company(uuid, text, text, text) from public, anon;
grant execute on function private.admin_upsert_company(uuid, text, text, text) to authenticated;

CREATE OR REPLACE FUNCTION "private"."admin_delete_company"("p_id" "uuid") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
begin
  if not private.is_active_admin() then raise sqlstate 'PT403' using message = 'An active administrator account is required.'; end if;
  if exists (select 1 from public.team where company_id = p_id)
    or exists (select 1 from public."user" where company_id = p_id)
    or exists (select 1 from public.join_request where company_id = p_id) then
    raise sqlstate 'PT409' using message = 'Move or remove this company’s users, teams, and requests first.';
  end if;
  delete from public.company where id = p_id;
  if not found then raise sqlstate 'PT404' using message = 'Company not found.'; end if;
  return jsonb_build_object('deleted', true);
end;
$$;
revoke all on function private.admin_delete_company(uuid) from public, anon;
grant execute on function private.admin_delete_company(uuid) to authenticated;

CREATE OR REPLACE FUNCTION "private"."admin_list_teams"() RETURNS "jsonb"
    LANGUAGE "plpgsql" STABLE SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
begin
  if not private.is_active_admin() then raise sqlstate 'PT403' using message = 'An active administrator account is required.'; end if;
  return coalesce((select jsonb_agg(jsonb_build_object(
    'id', t.id, 'company_id', t.company_id, 'name', t.name, 'manager_id', t.manager_id
  ) order by t.name) from public.team t), '[]'::jsonb);
end;
$$;
revoke all on function private.admin_list_teams() from public, anon;
grant execute on function private.admin_list_teams() to authenticated;

CREATE OR REPLACE FUNCTION "private"."admin_upsert_team"("p_team_id" "uuid", "p_company_id" "uuid", "p_name" "text", "p_manager_id" "uuid") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
declare v_team public.team%rowtype;
begin
  if not private.is_active_admin() then raise sqlstate 'PT403' using message = 'An active administrator account is required.'; end if;
  if p_name is null or length(trim(p_name)) not between 1 and 150
    or not exists (select 1 from public.company where id = p_company_id) then
    raise sqlstate 'PT400' using message = 'Choose a valid company and team name.';
  end if;
  if p_manager_id is not null and not exists (
    select 1 from public."user" u where u.id = p_manager_id and u.role = 'manager'
      and u.status = 'active' and u.company_id = p_company_id
  ) then raise sqlstate 'PT400' using message = 'The manager must be active and belong to this company.'; end if;
  if p_team_id is null then
    insert into public.team(company_id, name, manager_id) values (p_company_id, trim(p_name), p_manager_id) returning * into v_team;
  else
    update public.team set company_id = p_company_id, name = trim(p_name), manager_id = p_manager_id
      where id = p_team_id returning * into v_team;
    if not found then raise sqlstate 'PT404' using message = 'Team not found.'; end if;
  end if;
  return jsonb_build_object('id', v_team.id, 'company_id', v_team.company_id, 'name', v_team.name, 'manager_id', v_team.manager_id);
end;
$$;
revoke all on function private.admin_upsert_team(uuid, uuid, text, uuid) from public, anon;
grant execute on function private.admin_upsert_team(uuid, uuid, text, uuid) to authenticated;

CREATE OR REPLACE FUNCTION "private"."admin_delete_team"("p_id" "uuid") RETURNS "jsonb"
    LANGUAGE "plpgsql" SECURITY DEFINER
    SET "search_path" TO ''
    AS $$
begin
  if not private.is_active_admin() then raise sqlstate 'PT403' using message = 'An active administrator account is required.'; end if;
  if exists (select 1 from public."user" where team_id = p_id)
    or exists (select 1 from public.join_request where team_id = p_id) then
    raise sqlstate 'PT409' using message = 'Move or remove this team’s users and requests first.';
  end if;
  delete from public.team where id = p_id;
  if not found then raise sqlstate 'PT404' using message = 'Team not found.'; end if;
  return jsonb_build_object('deleted', true);
end;
$$;
revoke all on function private.admin_delete_team(uuid) from public, anon;
grant execute on function private.admin_delete_team(uuid) to authenticated;

CREATE OR REPLACE FUNCTION "public"."admin_list_companies"() RETURNS "jsonb"
    LANGUAGE "sql"
    SET "search_path" TO ''
    AS $$ select private.admin_list_companies(); $$;

CREATE OR REPLACE FUNCTION "public"."admin_upsert_company"("p_company_id" "uuid", "p_name" "text", "p_email_domain" "text", "p_status" "text") RETURNS "jsonb"
    LANGUAGE "sql"
    SET "search_path" TO ''
    AS $$ select private.admin_upsert_company(p_company_id,p_name,p_email_domain,p_status); $$;

CREATE OR REPLACE FUNCTION "public"."admin_delete_company"("p_id" "uuid") RETURNS "jsonb"
    LANGUAGE "sql"
    SET "search_path" TO ''
    AS $$ select private.admin_delete_company(p_id); $$;

CREATE OR REPLACE FUNCTION "public"."admin_list_teams"() RETURNS "jsonb"
    LANGUAGE "sql"
    SET "search_path" TO ''
    AS $$ select private.admin_list_teams(); $$;

CREATE OR REPLACE FUNCTION "public"."admin_upsert_team"("p_team_id" "uuid", "p_company_id" "uuid", "p_name" "text", "p_manager_id" "uuid") RETURNS "jsonb"
    LANGUAGE "sql"
    SET "search_path" TO ''
    AS $$ select private.admin_upsert_team(p_team_id,p_company_id,p_name,p_manager_id); $$;

CREATE OR REPLACE FUNCTION "public"."admin_delete_team"("p_id" "uuid") RETURNS "jsonb"
    LANGUAGE "sql"
    SET "search_path" TO ''
    AS $$ select private.admin_delete_team(p_id); $$;

revoke all on function public.admin_list_companies(), public.admin_upsert_company(uuid, text, text, text), public.admin_delete_company(uuid), public.admin_list_teams(), public.admin_upsert_team(uuid, uuid, text, uuid), public.admin_delete_team(uuid) from public, anon;
grant execute on function public.admin_list_companies(), public.admin_upsert_company(uuid, text, text, text), public.admin_delete_company(uuid), public.admin_list_teams(), public.admin_upsert_team(uuid, uuid, text, uuid), public.admin_delete_team(uuid) to authenticated;
grant execute on function public.admin_list_companies(), public.admin_upsert_company(uuid, text, text, text), public.admin_delete_company(uuid), public.admin_list_teams(), public.admin_upsert_team(uuid, uuid, text, uuid), public.admin_delete_team(uuid) to service_role;

CREATE POLICY "admins_can_read_profiles" ON "public"."user" FOR SELECT TO "authenticated" USING (( SELECT "private"."is_active_admin"() AS "is_active_admin"));

CREATE POLICY "admins_can_read_all_companies" ON "public"."company" FOR SELECT TO "authenticated" USING (( SELECT "private"."is_active_admin"() AS "is_active_admin"));

CREATE POLICY "admins_can_read_all_teams" ON "public"."team" FOR SELECT TO "authenticated" USING (( SELECT "private"."is_active_admin"() AS "is_active_admin"));
