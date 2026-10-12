type Config = { url: string; publicKey: string; serviceRoleKey: string };
type Fetcher = typeof fetch;

const responseHeaders = {
  "Content-Type": "application/json",
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Cache-Control": "no-store",
};
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const isoDate = /^\d{4}-\d{2}-\d{2}$/;
const reply = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: responseHeaders });

type CheckInResponses = {
  wellbeing?: unknown;
  workload?: unknown;
  energy?: unknown;
  focus?: unknown;
  afterHoursMinutes?: unknown;
};

function numericValues(rows: CheckInResponses[], field: keyof CheckInResponses, min: number, max: number): number[] {
  return rows
    .map((row) => row[field])
    .filter((value): value is number =>
      typeof value === "number" && Number.isFinite(value) && value >= min && value <= max
    );
}

function average(rows: CheckInResponses[], field: keyof CheckInResponses, min: number, max: number): number | null {
  const values = numericValues(rows, field, min, max);
  return values.length === 0 ? null : values.reduce((sum, value) => sum + value, 0) / values.length;
}

function anonymousAverage(rows: CheckInResponses[], field: keyof CheckInResponses, min: number, max: number): number | null {
  const values = numericValues(rows, field, min, max);
  return values.length < 5 ? null : values.reduce((sum, value) => sum + value, 0) / values.length;
}

function validDate(value: unknown): value is string {
  if (typeof value !== "string" || !isoDate.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(date.valueOf()) && date.toISOString().slice(0, 10) === value;
}

export function createTeamAggregateHandler(config: Config, send: Fetcher = fetch) {
  return async (request: Request): Promise<Response> => {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: responseHeaders });
    if (request.method !== "POST") return reply(405, { error: "Use POST." });
    const authorization = request.headers.get("Authorization") ?? "";
    if (!/^Bearer \S+$/i.test(authorization)) return reply(401, { error: "Sign in first." });
    if (!config.url || !config.publicKey || !config.serviceRoleKey) {
      return reply(503, { error: "Team insights are temporarily unavailable." });
    }

    let body: Record<string, unknown>;
    try {
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
      const parsed: unknown = JSON.parse(new TextDecoder().decode(bytes));
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        return reply(400, { error: "Send a JSON object." });
      }
      body = parsed as Record<string, unknown>;
    } catch {
      return reply(400, { error: "Send valid JSON." });
    }

    if (
      Object.keys(body).some((key) => !["team_id", "start_date", "end_date"].includes(key)) ||
      typeof body.team_id !== "string" || !uuid.test(body.team_id) ||
      !validDate(body.start_date) || !validDate(body.end_date)
    ) {
      return reply(400, { error: "Provide a team_id UUID and valid start_date and end_date." });
    }
    const startDay = Date.parse(`${body.start_date}T00:00:00Z`);
    const endDay = Date.parse(`${body.end_date}T00:00:00Z`);
    const spanDays = (endDay - startDay) / 86_400_000;
    if (spanDays < 0 || spanDays > 93) return reply(400, { error: "Choose a date range of at most 94 days." });

    try {
      const userHeaders = { apikey: config.publicKey, Authorization: authorization };
      const identityResponse = await send(`${config.url}/auth/v1/user`, {
        headers: userHeaders,
        signal: AbortSignal.timeout(10_000),
      });
      if (!identityResponse.ok) return reply(identityResponse.status >= 500 ? 503 : 401, { error: "Unable to verify your session." });
      const identity = await identityResponse.json();
      if (!identity || typeof identity.id !== "string" || !uuid.test(identity.id)) {
        return reply(401, { error: "Invalid session." });
      }

      const profileUrl = new URL(`${config.url}/rest/v1/user`);
      profileUrl.searchParams.set("select", "id,role,status,company_id,team_id,onboarding_done");
      profileUrl.searchParams.set("id", `eq.${identity.id}`);
      const profileResponse = await send(profileUrl, { headers: userHeaders, signal: AbortSignal.timeout(10_000) });
      if (!profileResponse.ok) return reply(503, { error: "Unable to verify team access." });
      const profiles = await profileResponse.json();
      const profile = Array.isArray(profiles) ? profiles[0] : null;
      if (!profile || profile.status !== "active" || profile.onboarding_done !== true) {
        return reply(403, { error: "An active team membership is required." });
      }

      const teamUrl = new URL(`${config.url}/rest/v1/team`);
      teamUrl.searchParams.set("select", "id,company_id,manager_id");
      teamUrl.searchParams.set("id", `eq.${body.team_id}`);
      const teamResponse = await send(teamUrl, { headers: userHeaders, signal: AbortSignal.timeout(10_000) });
      if (!teamResponse.ok) return reply(503, { error: "Unable to verify team access." });
      const teams = await teamResponse.json();
      const team = Array.isArray(teams) ? teams[0] : null;
      if (!team || team.company_id !== profile.company_id) return reply(403, { error: "You cannot view this team." });
      const isEngineer = profile.role === "engineer" && profile.team_id === body.team_id;
      const isManager = profile.role === "manager" && team.manager_id === identity.id;
      if (!isEngineer && !isManager) return reply(403, { error: "You cannot view this team." });

      const adminHeaders = {
        apikey: config.serviceRoleKey,
        Authorization: `Bearer ${config.serviceRoleKey}`,
      };
      const membersUrl = new URL(`${config.url}/rest/v1/user`);
      membersUrl.searchParams.set("select", "id");
      membersUrl.searchParams.set("role", "eq.engineer");
      membersUrl.searchParams.set("status", "eq.active");
      membersUrl.searchParams.set("onboarding_done", "eq.true");
      membersUrl.searchParams.set("team_id", `eq.${body.team_id}`);
      membersUrl.searchParams.set("company_id", `eq.${team.company_id}`);
      const membersResponse = await send(membersUrl, { headers: adminHeaders, signal: AbortSignal.timeout(10_000) });
      if (!membersResponse.ok) return reply(503, { error: "Team insights are temporarily unavailable." });
      const members: unknown = await membersResponse.json();
      const memberIds = Array.isArray(members)
        ? members.map((member) => member?.id).filter((id): id is string => typeof id === "string" && uuid.test(id))
        : [];

      const responsesByMember = new Map<string, CheckInResponses[]>();
      const rangeStart = new Date(`${body.start_date}T00:00:00+02:00`).toISOString();
      const rangeEnd = new Date(Date.parse(`${body.end_date}T00:00:00+02:00`) + 86_400_000).toISOString();
      for (let index = 0; index < memberIds.length; index += 80) {
        const group = memberIds.slice(index, index + 80);
        for (let from = 0; ; from += 1000) {
          const checkInsUrl = new URL(`${config.url}/rest/v1/check_in`);
          checkInsUrl.searchParams.set("select", "user_id,responses");
          checkInsUrl.searchParams.set("user_id", `in.(${group.join(",")})`);
          checkInsUrl.searchParams.set("created_at", `gte.${rangeStart}`);
          checkInsUrl.searchParams.set("and", `(created_at.lt.${rangeEnd})`);
          const checkInsResponse = await send(checkInsUrl, {
            headers: { ...adminHeaders, Range: `${from}-${from + 999}`, "Range-Unit": "items" },
            signal: AbortSignal.timeout(10_000),
          });
          if (!checkInsResponse.ok) return reply(503, { error: "Team insights are temporarily unavailable." });
          const rows: unknown = await checkInsResponse.json();
          if (!Array.isArray(rows)) return reply(503, { error: "Team insights are temporarily unavailable." });
          rows.forEach((row) => {
            if (typeof row?.user_id === "string" && row?.responses && typeof row.responses === "object" && !Array.isArray(row.responses)) {
              const memberResponses = responsesByMember.get(row.user_id) ?? [];
              memberResponses.push(row.responses as CheckInResponses);
              responsesByMember.set(row.user_id, memberResponses);
            }
          });
          if (rows.length < 1000) break;
        }
      }

      const participantCount = responsesByMember.size;
      if (participantCount < 5) {
        return reply(200, { team_id: body.team_id, response_count: participantCount });
      }
      // Give each employee equal weight so repeated submissions cannot dominate the team average.
      const participantAverages = [...responsesByMember.values()].map((memberResponses) => ({
        wellbeing: average(memberResponses, "wellbeing", 1, 5),
        workload: average(memberResponses, "workload", 1, 5),
        energy: average(memberResponses, "energy", 1, 10),
        focus: average(memberResponses, "focus", 1, 10),
        afterHoursMinutes: average(memberResponses, "afterHoursMinutes", 0, 1440),
      }));
      return reply(200, {
        team_id: body.team_id,
        response_count: participantCount,
        average_wellbeing: anonymousAverage(participantAverages, "wellbeing", 1, 5),
        average_workload: anonymousAverage(participantAverages, "workload", 1, 5),
        average_energy: anonymousAverage(participantAverages, "energy", 1, 10),
        average_focus: anonymousAverage(participantAverages, "focus", 1, 10),
        average_after_hours_minutes: anonymousAverage(participantAverages, "afterHoursMinutes", 0, 1440),
      });
    } catch {
      // Never log or return tokens, service keys, row data, or raw backend errors.
      return reply(503, { error: "Team insights are temporarily unavailable." });
    }
  };
}
