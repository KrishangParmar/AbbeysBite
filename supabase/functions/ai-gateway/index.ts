// ===========================================================================
// ai-gateway — secure model gateway for Abbey's Bite
//
// The mobile app NEVER holds a model API key. It calls this function with the
// user's Supabase JWT; we forward the request to a Gemma-family model via the
// Google AI (Generative Language) API using a server-side secret.
//
// Secrets (supabase secrets set):
//   GEMMA_API_KEY   — Google AI Studio API key
//   GEMMA_MODEL     — optional, defaults to "gemma-3-27b-it" (multimodal)
//
// Request body:
//   { task, system, user, image_base64?, history?: [{role, content}] }
// Response body:
//   { text } on success, { error } on failure.
// ===========================================================================

import { createClient } from "npm:@supabase/supabase-js@2";

const GEMMA_MODEL = Deno.env.get("GEMMA_MODEL") ?? "gemma-3-27b-it";
const GEMMA_API_KEY = Deno.env.get("GEMMA_API_KEY") ?? "";

// Daily caps per task. Every allowed task is capped — an unmetered task
// would be unmetered spend on the server-side model key.
const FREE_DAILY_CAPS: Record<string, number> = {
  analyze: 3,
  chat: 10,
  recipe_query: 20,
  weekly_insight: 4,
};
const PREMIUM_DAILY_CAPS: Record<string, number> = {
  analyze: 200,
  chat: 500,
  recipe_query: 200,
  weekly_insight: 20,
};

interface HistoryMessage {
  role: "user" | "assistant";
  content: string;
}

interface GatewayRequest {
  task: "analyze" | "chat" | "recipe_query" | "weekly_insight";
  system: string;
  user: string;
  image_base64?: string;
  history?: HistoryMessage[];
}

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return json({ error: "method_not_allowed" }, 405);
  }
  if (!GEMMA_API_KEY) {
    return json({ error: "gateway_not_configured" }, 503);
  }

  // ---- authenticate the caller with their own JWT ------------------------
  const authHeader = req.headers.get("Authorization") ?? "";
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    { global: { headers: { Authorization: authHeader } } },
  );
  const { data: userData, error: userError } = await supabase.auth.getUser();
  if (userError || !userData?.user) {
    return json({ error: "unauthorized" }, 401);
  }
  const userId = userData.user.id;

  let body: GatewayRequest;
  try {
    body = await req.json();
  } catch {
    return json({ error: "invalid_body" }, 400);
  }
  if (!body?.task || !body?.system) {
    return json({ error: "invalid_body" }, 400);
  }
  // The TypeScript union is not a runtime check — enforce the allow-list.
  if (!(body.task in FREE_DAILY_CAPS)) {
    return json({ error: "invalid_task" }, 400);
  }

  // ---- coarse server-side rate limiting (defense in depth) ---------------
  // Premium is verified against the revenuecat_customers row maintained by
  // the RevenueCat webhook; free users get daily caps enforced here too.
  const service = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );
  const { data: rcRow } = await service
    .from("revenuecat_customers")
    .select("is_premium")
    .eq("user_id", userId)
    .maybeSingle();
  const isPremium = rcRow?.is_premium === true;

  const since = new Date();
  since.setUTCHours(0, 0, 0, 0);
  const { count } = await service
    .from("ai_usage_log")
    .select("*", { count: "exact", head: true })
    .eq("user_id", userId)
    .eq("task", body.task)
    .gte("created_at", since.toISOString());
  const cap = (isPremium ? PREMIUM_DAILY_CAPS : FREE_DAILY_CAPS)[body.task];
  if ((count ?? 0) >= cap) {
    return json({ error: "rate_limited" }, 429);
  }

  // ---- build the Gemini-API request for the Gemma model ------------------
  const contents: unknown[] = [];
  for (const message of body.history ?? []) {
    contents.push({
      role: message.role === "assistant" ? "model" : "user",
      parts: [{ text: message.content }],
    });
  }
  const parts: unknown[] = [];
  if (body.image_base64) {
    parts.push({
      inline_data: { mime_type: "image/jpeg", data: body.image_base64 },
    });
  }
  // Gemma models don't take a separate system role — prepend it.
  parts.push({ text: `${body.system}\n\n${body.user}` });
  contents.push({ role: "user", parts });

  const modelUrl =
    `https://generativelanguage.googleapis.com/v1beta/models/${GEMMA_MODEL}:generateContent`;

  try {
    const response = await fetch(modelUrl, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": GEMMA_API_KEY,
      },
      body: JSON.stringify({
        contents,
        generationConfig: {
          temperature: body.task === "analyze" ? 0.4 : 0.7,
          maxOutputTokens: 2048,
        },
      }),
    });

    if (!response.ok) {
      const detail = await response.text();
      console.error("model_error", response.status, detail.slice(0, 500));
      return json({ error: "model_unavailable" }, 502);
    }

    const payload = await response.json();
    const text: string = payload?.candidates?.[0]?.content?.parts
      ?.map((p: { text?: string }) => p.text ?? "")
      .join("") ?? "";

    if (!text) {
      return json({ error: "empty_response" }, 502);
    }

    // Log usage for rate limiting (fire-and-forget; content is NOT logged).
    service.from("ai_usage_log").insert({ user_id: userId, task: body.task })
      .then(() => {}, () => {});

    return json({ text });
  } catch (e) {
    console.error("gateway_failure", e);
    return json({ error: "gateway_failure" }, 502);
  }
});

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
