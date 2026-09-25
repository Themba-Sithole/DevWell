import { createHandler } from "../_shared/handler.ts";

Deno.serve(createHandler("review", {
  url: Deno.env.get("SUPABASE_URL") ?? "",
  publicKey: Deno.env.get("SUPABASE_ANON_KEY") ?? "",
}));
