import { createAdminDashboardHandler } from "../_shared/admin_dashboard_handler.ts";

Deno.serve(createAdminDashboardHandler({
  url: Deno.env.get("SUPABASE_URL") ?? "",
  publicKey: Deno.env.get("SUPABASE_ANON_KEY") ?? "",
}));
