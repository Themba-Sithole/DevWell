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
const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === "object" && value !== null && !Array.isArray(value);

export function createAdminDashboardHandler(config: Config, send: Fetcher = fetch) {
  return async (request: Request): Promise<Response> => {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers });
    if (request.method !== "POST") return reply(405, { error: "Use POST." });
    const authorization = request.headers.get("Authorization") ?? "";
    if (!/^Bearer\s+\S+$/i.test(authorization)) return reply(401, { error: "Sign in first." });
    if (!config.url || !config.publicKey) return reply(500, { error: "Server configuration is missing." });

    try {
      const authHeaders = { apikey: config.publicKey, Authorization: authorization };
      const identity = await send(`${config.url}/auth/v1/user`, {
        headers: authHeaders,
        signal: AbortSignal.timeout(10_000),
      });
      if (!identity.ok) {
        return reply(identity.status >= 500 ? 503 : 401, { error: "Unable to verify your session." });
      }
      const user = await identity.json();
      if (typeof user.id !== "string" || !uuid.test(user.id)) return reply(401, { error: "Invalid session." });

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
      let body: unknown;
      try {
        body = JSON.parse(new TextDecoder().decode(bytes));
      } catch {
        return reply(400, { error: "Send valid JSON." });
      }
      if (!isRecord(body)) {
        return reply(400, { error: "Send a JSON object." });
      }

      let rpc: string;
      let args: Record<string, unknown>;
      if (body.action === "list") {
        if (Object.keys(body).some((key) => key !== "action")) {
          return reply(400, { error: "The list action accepts no other fields." });
        }
        rpc = "admin_dashboard";
        args = {};
      } else if (body.action === "set_status") {
        if (
          Object.keys(body).some((key) => !["action", "user_id", "status"].includes(key)) ||
          typeof body.user_id !== "string" || !uuid.test(body.user_id) ||
          (body.status !== "active" && body.status !== "suspended")
        ) {
          return reply(400, { error: "Provide a valid user_id and active or suspended status." });
        }
        rpc = "admin_set_user_status";
        args = { p_user_id: body.user_id, p_status: body.status };
      } else {
        return reply(400, { error: "Choose a valid admin action." });
      }

      const result = await send(`${config.url}/rest/v1/rpc/${rpc}`, {
        method: "POST",
        headers: { ...authHeaders, "Content-Type": "application/json" },
        body: JSON.stringify(args),
        signal: AbortSignal.timeout(10_000),
      });
      const data: unknown = await result.json();
      if (!result.ok) {
        const error = data as { code?: unknown; message?: unknown };
        const code = String(error.code ?? "");
        if (["PT400", "PT401", "PT403", "PT409"].includes(code)) {
          return reply(Number(code.slice(2)), {
            error: typeof error.message === "string" ? error.message : "Access denied.",
          });
        }
        if (result.status === 401 || result.status === 403) return reply(result.status, { error: "Access denied." });
        return reply(500, { error: "The admin request could not be completed. Please retry." });
      }
      return reply(200, data);
    } catch {
      return reply(503, { error: "The service is temporarily unavailable. Please retry." });
    }
  };
}
