import { createAdminDashboardHandler } from "./admin_dashboard_handler.ts";

const config = { url: "https://example.supabase.co", publicKey: "public-key" };
const adminId = "11111111-1111-4111-8111-111111111111";
const targetId = "22222222-2222-4222-8222-222222222222";
const authRequest = (body: unknown, method = "POST") => new Request("https://function.test", {
  method,
  headers: { Authorization: "Bearer user-token", "Content-Type": "application/json" },
  body: method === "POST" ? JSON.stringify(body) : undefined,
});

Deno.test("rejects missing authentication before requests", async () => {
  let calls = 0;
  const handler = createAdminDashboardHandler(config, (() => {
    calls += 1;
    return Promise.resolve(new Response());
  }) as typeof fetch);
  const result = await handler(new Request("https://function.test", { method: "POST", body: "{}" }));
  if (result.status !== 401 || calls !== 0) throw new Error("Unauthenticated request was not rejected.");
});

Deno.test("rejects unsupported methods", async () => {
  const handler = createAdminDashboardHandler(config, fetch);
  if ((await handler(authRequest({}, "GET"))).status !== 405) throw new Error("GET was accepted.");
});

Deno.test("rejects invalid actions and forged fields", async () => {
  const handler = createAdminDashboardHandler(config, (async (input) => {
    if (String(input).includes("/auth/v1/user")) return Response.json({ id: adminId });
    throw new Error("Invalid input reached an RPC.");
  }) as typeof fetch);
  for (const body of [
    { action: "root" },
    { action: "list", role: "admin" },
    { action: "set_status", user_id: targetId, status: "rejected" },
    { action: "set_status", user_id: "not-a-uuid", status: "suspended" },
  ]) {
    if ((await handler(authRequest(body))).status !== 400) throw new Error(`Accepted input ${JSON.stringify(body)}`);
  }
});

Deno.test("forwards the caller identity to the dashboard RPC", async () => {
  const seen: RequestInit[] = [];
  const handler = createAdminDashboardHandler(config, (async (input, init) => {
    seen.push(init ?? {});
    if (String(input).includes("/auth/v1/user")) {
      return Response.json({ id: adminId });
    }
    return Response.json({ stats: { Users: 3 }, users: [] });
  }) as typeof fetch);
  const result = await handler(authRequest({ action: "list" }));
  const body = await result.json();
  if (result.status !== 200 || body.stats.Users !== 3) throw new Error("Dashboard response was not returned.");
  if (!String(seen[1].body).includes("{}")) throw new Error("Unexpected RPC payload.");
  if ((seen[1].headers as Record<string, string>).Authorization !== "Bearer user-token") {
    throw new Error("Caller token was not forwarded.");
  }
});

Deno.test("calls the constrained status RPC", async () => {
  const seen: Array<{ input: RequestInfo | URL; init?: RequestInit }> = [];
  const handler = createAdminDashboardHandler(config, (async (input, init) => {
    seen.push({ input, init });
    return String(input).includes("/auth/v1/user")
      ? Response.json({ id: adminId })
      : Response.json({ user_id: targetId, status: "suspended" });
  }) as typeof fetch);
  const result = await handler(authRequest({ action: "set_status", user_id: targetId, status: "suspended" }));
  if (result.status !== 200) throw new Error("Status update failed.");
  if (!String(seen[1].input).endsWith("/rpc/admin_set_user_status")) throw new Error("Unexpected RPC.");
  if (String(seen[1].init?.body) !== JSON.stringify({ p_user_id: targetId, p_status: "suspended" })) {
    throw new Error("Unexpected status RPC arguments.");
  }
});

Deno.test("returns database authorization failures without exposing internals", async () => {
  const handler = createAdminDashboardHandler(config, (async (input) =>
    String(input).includes("/auth/v1/user")
      ? Response.json({ id: adminId })
      : Response.json({ code: "PT403", message: "An active admin account is required." }, { status: 403 })
  ) as typeof fetch);
  const result = await handler(authRequest({ action: "list" }));
  const body = await result.json();
  if (result.status !== 403 || body.error !== "An active admin account is required.") {
    throw new Error("Admin authorization failure was not preserved.");
  }
});
