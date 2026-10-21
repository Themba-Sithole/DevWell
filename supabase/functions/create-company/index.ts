import { createHandler } from "../_shared/handler.ts";

Deno.serve(createHandler("create-company", {
  url: Deno.env.get("SUPABASE_URL") ?? "",
  publicKey: Deno.env.get("SUPABASE_ANON_KEY") ?? "",
}));
