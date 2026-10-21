import { createHandler } from "./handler.ts";

const id = "11111111-1111-4111-8111-111111111111";
const config = { url: "https://example.supabase.co", publicKey: "public-test-key" };
function equal(actual: unknown, expected: unknown) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`Expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
}
const req = (body: unknown, token = "Bearer test-user-token") =>
  new Request("https://example.test", {
    method: "POST",
    headers: { Authorization: token },
    body: JSON.stringify(body),
  });
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
const identityOnly: typeof fetch = () => Promise.resolve(json({ id }));

Deno.test("rejects missing token before making a request", async () => {
  const handler = createHandler("submit", config, () => {
    throw new Error("Should not call backend");
  });
  equal((await handler(req({}, ""))).status, 401);
});
Deno.test("rejects unsupported methods", async () => {
  equal((await createHandler("submit", config)(new Request("https://example.test"))).status, 405);
});
Deno.test("rejects an invalid Auth session", async () => {
  const handler = createHandler("submit", config, () => Promise.resolve(json({}, 401)));
  equal((await handler(req({ company_id: id, team_id: id }))).status, 401);
});
Deno.test("rejects invalid IDs and attempted privilege injection", async () => {
  const handler = createHandler("submit", config, identityOnly);
  for (
    const body of [
      { company_id: "bad", team_id: id },
      { company_id: id, team_id: id, role: "admin" },
      null,
      [],
    ]
  ) {
    equal((await handler(req(body))).status, 400);
  }
});
Deno.test("requires a real boolean for review", async () => {
  equal(
    (await createHandler("review", config, identityOnly)(req({ request_id: id, approve: "true" }))).status,
    400,
  );
});
Deno.test("limits incoming body size", async () => {
  equal((await createHandler("submit", config, identityOnly)(req({ data: "x".repeat(5000) }))).status, 413);
});
Deno.test("forwards caller identity and only expected submit arguments", async () => {
  const calls: string[] = [];
  const handler = createHandler("submit", config, (url, init) => {
    calls.push(String(url));
    equal(new Headers(init?.headers).get("Authorization"), "Bearer test-user-token");
    if (calls.length === 1) return Promise.resolve(json({ id }));
    equal(JSON.parse(String(init?.body)), { p_company_id: id, p_team_id: id });
    return Promise.resolve(json({ request_id: id, status: "pending" }));
  });
  equal(await (await handler(req({ company_id: id, team_id: id }))).json(), {
    request_id: id,
    status: "pending",
  });
  equal(calls[1], `${config.url}/rest/v1/rpc/submit_join_request`);
});
Deno.test("review calls correct RPC and forwards rejection", async () => {
  const handler = createHandler("review", config, (url, init) => {
    if (String(url).endsWith("/user")) return Promise.resolve(json({ id }));
    equal(String(url), `${config.url}/rest/v1/rpc/review_join_request`);
    equal(JSON.parse(String(init?.body)), { p_request_id: id, p_approve: false });
    return Promise.resolve(json({ status: "rejected" }));
  });
  equal((await handler(req({ request_id: id, approve: false }))).status, 200);
});
Deno.test("preserves safe conflict response and hides internal errors", async () => {
  for (const code of ["PT409", "XX000"]) {
    const handler = createHandler("submit", config, (url) =>
      Promise.resolve(
        String(url).endsWith("/user") ? json({ id }) : json({ code, message: "details" }, 500),
      ));
    const result = await handler(req({ company_id: id, team_id: id }));
    equal(result.status, code === "PT409" ? 409 : 500);
    if (code === "XX000") equal((await result.text()).includes("details"), false);
  }
});
Deno.test("returns retryable failure when backend is unreachable", async () => {
  const handler = createHandler("submit", config, () => {
    throw new Error("Offline");
  });
  equal((await handler(req({ company_id: id, team_id: id }))).status, 503);
});
Deno.test("create-company requires a company name and a first team name", async () => {
  const handler = createHandler("create-company", config, identityOnly);
  for (
    const body of [
      { company_name: "Acme" },
      { team_name: "Platform" },
      { company_name: "   ", team_name: "Platform" },
      { company_name: "Acme", team_name: "" },
      { company_name: "Acme", team_name: "Platform", role: "admin" },
      { company_name: "x".repeat(151), team_name: "Platform" },
    ]
  ) {
    equal((await handler(req(body))).status, 400);
  }
});
Deno.test("create-company calls the manager company RPC with only expected arguments", async () => {
  const calls: string[] = [];
  const handler = createHandler("create-company", config, (url, init) => {
    calls.push(String(url));
    if (calls.length === 1) return Promise.resolve(json({ id }));
    equal(JSON.parse(String(init?.body)), { p_company_name: "Acme", p_team_name: "Platform" });
    return Promise.resolve(json({ company_id: id, team_id: id, status: "active" }));
  });
  const result = await handler(req({ company_name: "Acme", team_name: "Platform" }));
  equal(result.status, 200);
  equal(calls[1], `${config.url}/rest/v1/rpc/create_manager_company`);
});
Deno.test("create-team rejects extra fields and calls the team RPC", async () => {
  const rejecting = createHandler("create-team", config, identityOnly);
  equal((await rejecting(req({ team_name: "Platform", company_id: id }))).status, 400);
  equal((await rejecting(req({ team_name: "Platform", role: "admin" }))).status, 400);
  equal((await rejecting(req({ team_name: "" }))).status, 400);
  const handler = createHandler("create-team", config, (url, init) => {
    if (String(url).endsWith("/user")) return Promise.resolve(json({ id }));
    equal(String(url), `${config.url}/rest/v1/rpc/create_team`);
    equal(JSON.parse(String(init?.body)), { p_team_name: "Platform" });
    return Promise.resolve(json({ team_id: id }));
  });
  equal((await handler(req({ team_name: "Platform" }))).status, 200);
});
