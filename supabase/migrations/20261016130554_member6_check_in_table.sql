-- Reconstructed from the live database on 2026-10-21.
-- The check-in table and its owner-only policies were deployed to the live project
-- under this version but the file was never committed. The live database was the
-- source of truth for this file, so a fresh local database now matches production.

CREATE TABLE IF NOT EXISTS "public"."check_in" (
    "id" "uuid" DEFAULT "gen_random_uuid"() NOT NULL,
    "user_id" "uuid" NOT NULL,
    "responses" "jsonb" NOT NULL,
    "created_at" timestamp with time zone DEFAULT "now"() NOT NULL
);

ALTER TABLE ONLY "public"."check_in"
    ADD CONSTRAINT "check_in_pkey" PRIMARY KEY ("id");

ALTER TABLE ONLY "public"."check_in"
    ADD CONSTRAINT "check_in_user_id_fkey" FOREIGN KEY ("user_id") REFERENCES "auth"."users"("id") ON DELETE CASCADE;

CREATE INDEX "check_in_user_id_created_at_idx" ON "public"."check_in" USING "btree" ("user_id", "created_at" DESC);

ALTER TABLE "public"."check_in" ENABLE ROW LEVEL SECURITY;

CREATE POLICY "check_in_insert_own" ON "public"."check_in" FOR INSERT TO "authenticated" WITH CHECK (("auth"."uid"() = "user_id"));

CREATE POLICY "check_in_select_own" ON "public"."check_in" FOR SELECT TO "authenticated" USING (("auth"."uid"() = "user_id"));

CREATE POLICY "check_in_update_own" ON "public"."check_in" FOR UPDATE TO "authenticated" USING ((( SELECT "auth"."uid"() AS "uid") = "user_id")) WITH CHECK ((( SELECT "auth"."uid"() AS "uid") = "user_id"));

revoke all on table public.check_in from anon;
grant all on table public.check_in to authenticated;
grant all on table public.check_in to service_role;
