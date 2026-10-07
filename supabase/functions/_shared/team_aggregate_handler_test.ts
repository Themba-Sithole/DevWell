import { createTeamAggregateHandler } from "./team_aggregate_handler.ts";

const userId = "11111111-1111-4111-8111-111111111111";
const teamId = "22222222-2222-4222-8222-222222222222";
const companyId = "33333333-3333-4333-8333-333333333333";
const memberIds = Array.from({ length: 5 }, (_, index) =>
  `${String(index + 1).padStart(8, "0")}-4444-4444-8444-444444444444`
);
const config = {
  url: "https://example.supabase.co",
  publicKey: "publishable-test-key",
  serviceRoleKey: "server-only-test-key",
};

function equal(actual: unknown, expected: unknown) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`Expected ${JSON.stringify(expected)}, got ${JSON.stringify(actual)}`);
  }
}

const request = (body: unknown, authorization = "Bearer signed-in-user-token") =>
  new Request("https://example.test", {
    method: "POST",
    headers: { Authorization: authorization },
    body: JSON.stringify(body),
  });

function fakeFetch(checkInCount: number, callerRole = "engineer") {
  const calls: Array<{ url: URL; init?: RequestInit }> = [];
  const send: typeof fetch = async (input, init) => {
    const url = new URL(String(input));
    calls.push({ url, init });
    if (url.pathname.endsWith("/auth/v1/user")) return Response.json({ id: userId });
    if (url.pathname.endsWith("/rest/v1/user") && new Headers(init?.headers).get("Authorization") === "Bearer signed-in-user-token") {
      return Response.json([{
        id: userId,
        role: callerRole,
        status: "active",
        company_id: companyId,
        team_id: callerRole === "engineer" ? teamId : null,
        onboarding_done: true,
      }]);
    }
    if (url.pathname.endsWith("/rest/v1/team")) {
      return Response.json([{ id: teamId, company_id: companyId, manager_id: callerRole === "manager" ? userId : null }]);
    }
    if (url.pathname.endsWith("/rest/v1/user")) return Response.json(memberIds.slice(0, checkInCount).map((id) => ({ id })));
    if (url.pathname.endsWith("/rest/v1/check_in")) {
      return Response.json(memberIds.slice(0, checkInCount).map((userId, index) => ({
        user_id: userId,
        responses: { wellbeing: index + 1, workload: 3, energy: 6 + index, focus: 7, afterHoursMinutes: 30 },
      })));
    }
    throw new Error(`Unexpected request: ${url.pathname}`);
  };
  return { send, calls };
}

Deno.test("rejects unauthenticated callers before accessing Supabase", async () => {
  const result = await createTeamAggregateHandler(config, () => { throw new Error("unexpected fetch"); })(
    request({ team_id: teamId, start_date: "2026-07-04", end_date: "2026-10-04" }, ""),
  );
  equal(result.status, 401);
});

Deno.test("rejects malformed ranges and unexpected request fields", async () => {
  const handler = createTeamAggregateHandler(config, () => { throw new Error("unexpected fetch"); });
  equal((await handler(request({ team_id: teamId, start_date: "2026-01-01", end_date: "2026-05-01" }))).status, 400);
  equal((await handler(request({ team_id: teamId, start_date: "2026-07-04", end_date: "2026-10-04", include_names: true }))).status, 400);
});

Deno.test("returns only the count below the anonymity threshold", async () => {
  const fake = fakeFetch(4);
  const result = await createTeamAggregateHandler(config, fake.send)(
    request({ team_id: teamId, start_date: "2026-07-04", end_date: "2026-10-04" }),
  );
  equal(result.status, 200);
  equal(await result.json(), { team_id: teamId, response_count: 4 });
  equal(new Headers(fake.calls[3].init?.headers).get("Authorization"), `Bearer ${config.serviceRoleKey}`);
});

Deno.test("returns anonymous averages at five and no raw check-in fields", async () => {
  const fake = fakeFetch(5);
  const result = await createTeamAggregateHandler(config, fake.send)(
    request({ team_id: teamId, start_date: "2026-07-04", end_date: "2026-10-04" }),
  );
  const body = await result.json();
  equal(result.status, 200);
  equal(body, {
    team_id: teamId,
    response_count: 5,
    average_wellbeing: 3,
    average_workload: 3,
    average_energy: 8,
    average_focus: 7,
    average_after_hours_minutes: 30,
  });
  equal(Object.keys(body).sort(), [
    "average_after_hours_minutes", "average_energy", "average_focus", "average_wellbeing",
    "average_workload", "response_count", "team_id",
  ]);
});

Deno.test("rejects a team outside the authenticated engineer's membership", async () => {
  const fake = fakeFetch(5);
  const send: typeof fetch = async (input, init) => {
    const url = new URL(String(input));
    if (url.pathname.endsWith("/rest/v1/team")) return Response.json([{ id: teamId, company_id: companyId, manager_id: null }]);
    return fake.send(input, init);
  };
  const result = await createTeamAggregateHandler(config, send)(
    request({ team_id: "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", start_date: "2026-07-04", end_date: "2026-10-04" }),
  );
  equal(result.status, 403);
});
