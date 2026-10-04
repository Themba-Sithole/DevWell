import { createTeamAggregateHandler } from "../_shared/team_aggregate_handler.ts";

Deno.serve(createTeamAggregateHandler({
  url: Deno.env.get("SUPABASE_URL") ?? "",
  publicKey: Deno.env.get("SUPABASE_ANON_KEY") ?? "",
  serviceRoleKey: Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
}));
