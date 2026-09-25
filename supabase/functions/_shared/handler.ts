type Operation = "submit" | "review";
type Config = { url: string; publicKey: string };
type Fetcher = typeof fetch;

const headers = {
  "Content-Type": "application/json",
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Cache-Control": "no-store",
};
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const reply = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers });

// The caller's token is forwarded to Postgres. No service-role key is needed.
export function createHandler(operation: Operation, config: Config, send: Fetcher = fetch) {
  return async (request: Request): Promise<Response> => {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers });
    if (request.method !== "POST") return reply(405, { error: "Use POST." });
    const authorization = request.headers.get("Authorization") ?? "";
    if (!/^Bearer \S+$/i.test(authorization)) return reply(401, { error: "Sign in first." });
    if (!config.url || !config.publicKey) return reply(500, { error: "Server configuration is missing." });

    try {
      // Validate with Auth even though the platform also checks the JWT.
      const authHeaders = { apikey: config.publicKey, Authorization: authorization };
      const identity = await send(`${config.url}/auth/v1/user`, {
        headers: authHeaders,
        signal: AbortSignal.timeout(10_000),
      });
      if (!identity.ok) {
        return reply(identity.status >= 500 ? 503 : 401, { error: "Unable to verify your session." });
      }
      const user = await identity.json();
      if (typeof user.id !== "string" || !uuid.test(user.id)) {
        return reply(401, { error: "Invalid session." });
      }
      // Read with a bound, including when Content-Length is missing.
      const reader = request.body?.getReader();
      let size = 0;
      const chunks: Uint8Array[] = [];
      if (reader) {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          size += value.length;
          if (size > 4096) {
            await reader.cancel();
            return reply(413, { error: "Request is too large." });
          }
          chunks.push(value);
        }
      }
      const bytes = new Uint8Array(size);
      let offset = 0;
      for (const chunk of chunks) {
        bytes.set(chunk, offset);
        offset += chunk.length;
      }
      let body;
      try {
        body = JSON.parse(new TextDecoder().decode(bytes));
      } catch {
        return reply(400, { error: "Send valid JSON." });
      }
      if (!body || typeof body !== "object" || Array.isArray(body)) {
        return reply(400, { error: "Send a JSON object." });
      }

      let rpc: string;
      let args: Record<string, unknown>;
      if (operation === "submit") {
        if (
          Object.keys(body).some((key) => !["company_id", "team_id"].includes(key)) ||
          typeof body.company_id !== "string" || !uuid.test(body.company_id) ||
          typeof body.team_id !== "string" || !uuid.test(body.team_id)
        ) {
          return reply(400, { error: "Provide only valid company_id and team_id UUIDs." });
        }
        rpc = "submit_join_request";
        args = { p_company_id: body.company_id, p_team_id: body.team_id };
      } else {
        if (
          Object.keys(body).some((key) => !["request_id", "approve"].includes(key)) ||
          typeof body.request_id !== "string" || !uuid.test(body.request_id) ||
          typeof body.approve !== "boolean"
        ) {
          return reply(400, { error: "Provide a valid request_id UUID and boolean approve." });
        }
        rpc = "review_join_request";
        args = { p_request_id: body.request_id, p_approve: body.approve };
      }
      const result = await send(`${config.url}/rest/v1/rpc/${rpc}`, {
        method: "POST",
        headers: { ...authHeaders, "Content-Type": "application/json" },
        body: JSON.stringify(args),
        signal: AbortSignal.timeout(10_000),
      });
      const data = await result.json();
      if (!result.ok) {
        // Only intentionally raised database errors are safe to show to callers.
        const code = String(data.code ?? "");
        if (["PT400", "PT401", "PT403", "PT409"].includes(code)) {
          return reply(Number(code.slice(2)), { error: data.message });
        }
        if (result.status === 401 || result.status === 403) {
          return reply(result.status, { error: "Access denied." });
        }
        return reply(500, { error: "The request could not be saved. Please retry." });
      }
      return reply(200, data);
    } catch {
      // Never log tokens, passwords, or raw database errors.
      return reply(503, { error: "The service is temporarily unavailable. Please retry." });
    }
  };
}
