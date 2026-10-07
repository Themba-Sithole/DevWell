const responseHeaders = {
  "Content-Type": "application/json",
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, apikey, content-type, x-client-info",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Cache-Control": "no-store",
};

const reply = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: responseHeaders });

const systemInstruction = `You are DevWell, a supportive workplace wellbeing assistant. Offer practical, low-risk guidance about focus, workload, communication, and general wellbeing. You are not a clinician: do not diagnose conditions, recommend medication, or present your response as professional medical advice. Keep replies concise and kind. If someone describes immediate danger or intent to harm themselves or another person, encourage them to contact local emergency services or a trusted person now; do not guess a hotline number. Do not ask for identifying details.`;

Deno.serve(async (request: Request): Promise<Response> => {
  if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: responseHeaders });
  if (request.method !== "POST") return reply(405, { error: "Use POST." });

  const authorization = request.headers.get("Authorization") ?? "";

  const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? "";
  const supabasePublicKey = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
  const geminiApiKey = Deno.env.get("GEMINI_API_KEY") ?? "";
  if (!supabaseUrl || !supabasePublicKey || !geminiApiKey) {
    return reply(503, { error: "The assistant is not configured yet." });
  }

  try {
    const reader = request.body?.getReader();
    const chunks: Uint8Array[] = [];
    let size = 0;
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
    if (!body || typeof body !== "object" || Array.isArray(body)) {
      return reply(400, { error: "Send a JSON object." });
    }
    const payload = body as Record<string, unknown>;
    if (Object.keys(payload).some((key) => !["question", "consent", "preview"].includes(key))) {
      return reply(400, { error: "Only a question and consent value are accepted." });
    }
    if (payload.consent !== true) return reply(400, { error: "Consent is required before sending a question." });
    if (typeof payload.question !== "string") return reply(400, { error: "Send a question." });
    const question = payload.question.trim();
    if (!question || question.length > 1000) {
      return reply(400, { error: "Questions must be between 1 and 1,000 characters." });
    }

    // Preview requests are allowed through the Supabase gateway without a user
    // session. Signed-in requests must still belong to an active profile.
    if (payload.preview !== true) {
      if (!/^Bearer \S+$/i.test(authorization)) return reply(401, { error: "Please sign in first." });
      const authHeaders = { apikey: supabasePublicKey, Authorization: authorization };
      const identityResponse = await fetch(`${supabaseUrl}/auth/v1/user`, {
        headers: authHeaders,
        signal: AbortSignal.timeout(10_000),
      });
      if (!identityResponse.ok) return reply(401, { error: "Please sign in again." });
      const identity: unknown = await identityResponse.json();
      if (!identity || typeof identity !== "object" || !("id" in identity) || typeof identity.id !== "string") {
        return reply(401, { error: "Unable to verify your session." });
      }
      const profileUrl = new URL(`${supabaseUrl}/rest/v1/user`);
      profileUrl.searchParams.set("select", "status");
      profileUrl.searchParams.set("id", `eq.${identity.id}`);
      profileUrl.searchParams.set("limit", "1");
      const profileResponse = await fetch(profileUrl, {
        headers: authHeaders,
        signal: AbortSignal.timeout(10_000),
      });
      if (!profileResponse.ok) return reply(503, { error: "Unable to verify your account status." });
      const profiles: unknown = await profileResponse.json();
      const profile = Array.isArray(profiles) ? profiles[0] as { status?: unknown } | undefined : undefined;
      if (profile?.status !== "active") return reply(403, { error: "Your account must be active to use the assistant." });
    }

    const geminiResponse = await fetch(
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent",
      {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": geminiApiKey },
        body: JSON.stringify({
          system_instruction: { parts: [{ text: systemInstruction }] },
          contents: [{ parts: [{ text: question }] }],
          generationConfig: { maxOutputTokens: 320, temperature: 0.4 },
        }),
        signal: AbortSignal.timeout(25_000),
      },
    );
    if (geminiResponse.status === 429) return reply(429, { error: "The free assistant is busy. Please try again shortly." });
    if (!geminiResponse.ok) return reply(502, { error: "The assistant could not answer right now. Please retry." });

    const generated: unknown = await geminiResponse.json();
    if (!generated || typeof generated !== "object" || !("candidates" in generated) || !Array.isArray(generated.candidates)) {
      return reply(502, { error: "The assistant returned an invalid response. Please retry." });
    }
    const candidate = generated.candidates[0] as { content?: { parts?: Array<{ text?: unknown }> } } | undefined;
    const answer = candidate?.content?.parts?.map((part) => typeof part.text === "string" ? part.text : "").join("").trim();
    if (!answer) return reply(502, { error: "The assistant returned an empty response. Please retry." });
    return reply(200, { answer });
  } catch {
    // Avoid logging tokens, keys, questions, or provider/database error bodies.
    return reply(503, { error: "The assistant is temporarily unavailable. Please retry." });
  }
});
