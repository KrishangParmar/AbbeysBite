// ===========================================================================
// send-notification — server-side OneSignal sender
//
// The OneSignal App API key lives ONLY here (Supabase function secret
// ONESIGNAL_APP_API_KEY). Clients invoke this function with their own JWT for
// a small set of allow-listed, non-spammy notification types targeting a
// user's OneSignal External ID (= Supabase auth UUID).
//
// Types:
//   friend_request        { target_user_id }
//   friend_accepted       { target_user_id }
//   recipe_liked          { target_user_id, recipe_title }
//   weekly_reflection     { target_user_id }           (self or cron)
//
// Recipients only receive pushes for categories they enabled (OneSignal tags
// set by the app: friend_activity / recipe_activity / weekly_reflection).
// ===========================================================================

import { createClient } from "npm:@supabase/supabase-js@2";

const ONESIGNAL_APP_ID = Deno.env.get("ONESIGNAL_APP_ID") ?? "";
const ONESIGNAL_APP_API_KEY = Deno.env.get("ONESIGNAL_APP_API_KEY") ?? "";

interface SendRequest {
  type: "friend_request" | "friend_accepted" | "recipe_liked" | "weekly_reflection";
  target_user_id: string;
  /** recipe_liked only — the recipe is looked up server-side; its DB title is
   *  used in the push (client text is never trusted into notification copy). */
  recipe_id?: string;
  recipe_title?: string;
}

const UUID_RE =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Per-sender cap (rolling hour) — generous for real use, hostile to spam. */
const MAX_SENDS_PER_HOUR = 30;

const TYPE_CONFIG: Record<string, { tag: string; title: string; body: (r: SendRequest, sender: string) => string; deepLink: string }> = {
  friend_request: {
    tag: "friend_activity",
    title: "New friend request",
    body: (_r, sender) => `${sender} wants to be friends`,
    deepLink: "abbeysbite://friends/requests",
  },
  friend_accepted: {
    tag: "friend_activity",
    title: "Request accepted",
    body: (_r, sender) => `${sender} accepted your friend request`,
    deepLink: "abbeysbite://friends",
  },
  recipe_liked: {
    tag: "recipe_activity",
    title: "Your recipe got some love",
    body: (r, sender) => `${sender} liked “${r.recipe_title ?? "your recipe"}”`,
    deepLink: "abbeysbite://community/mine",
  },
  weekly_reflection: {
    tag: "weekly_reflection",
    title: "Your week in food",
    body: () => "Your weekly reflection is ready to read.",
    deepLink: "abbeysbite://journal",
  },
};

Deno.serve(async (req) => {
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);
  if (!ONESIGNAL_APP_ID || !ONESIGNAL_APP_API_KEY) {
    return json({ error: "onesignal_not_configured" }, 503);
  }

  const authHeader = req.headers.get("Authorization") ?? "";
  const asUser = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    { global: { headers: { Authorization: authHeader } } },
  );
  const { data: userData, error: userError } = await asUser.auth.getUser();
  if (userError || !userData?.user) return json({ error: "unauthorized" }, 401);
  const senderId = userData.user.id;

  let body: SendRequest;
  try {
    body = await req.json();
  } catch {
    return json({ error: "invalid_body" }, 400);
  }
  const config = TYPE_CONFIG[body?.type ?? ""];
  if (!config || !body.target_user_id) return json({ error: "invalid_type" }, 400);
  // target_user_id is interpolated into PostgREST filters below — it must be
  // a UUID, nothing else.
  if (!UUID_RE.test(body.target_user_id)) return json({ error: "invalid_target" }, 400);

  const service = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  // Rate limit: pushing is a privilege, not an API.
  const hourAgo = new Date(Date.now() - 60 * 60 * 1000).toISOString();
  const { count: recentSends } = await service
    .from("notification_log")
    .select("*", { count: "exact", head: true })
    .eq("sender_id", senderId)
    .gte("created_at", hourAgo);
  if ((recentSends ?? 0) >= MAX_SENDS_PER_HOUR) {
    return json({ error: "rate_limited" }, 429);
  }

  // Anti-abuse: every social notification requires a real relationship/event.
  if (body.type === "friend_request" || body.type === "friend_accepted") {
    const { data: rel } = await service
      .from("friendships")
      .select("status")
      .or(
        `and(requester_id.eq.${senderId},addressee_id.eq.${body.target_user_id}),` +
        `and(requester_id.eq.${body.target_user_id},addressee_id.eq.${senderId})`,
      )
      .limit(1)
      .maybeSingle();
    const required = body.type === "friend_request" ? "pending" : "accepted";
    if (rel?.status !== required) return json({ error: "no_matching_relationship" }, 403);
  }
  if (body.type === "recipe_liked") {
    // Verify the event server-side: the recipe exists and is published, the
    // target authored it, and the sender actually liked it. The DB title is
    // used for the push copy — client-sent text never reaches a notification.
    if (!body.recipe_id || !UUID_RE.test(body.recipe_id)) {
      return json({ error: "invalid_recipe" }, 400);
    }
    const { data: recipe } = await service
      .from("recipes")
      .select("author_id, title, status")
      .eq("id", body.recipe_id)
      .maybeSingle();
    if (!recipe || recipe.status !== "published" ||
        recipe.author_id !== body.target_user_id) {
      return json({ error: "no_matching_event" }, 403);
    }
    const { data: like } = await service
      .from("recipe_likes")
      .select("recipe_id")
      .eq("recipe_id", body.recipe_id)
      .eq("user_id", senderId)
      .maybeSingle();
    if (!like) return json({ error: "no_matching_event" }, 403);
    body.recipe_title = String(recipe.title ?? "").slice(0, 80);
  }
  if (body.type === "weekly_reflection" && body.target_user_id !== senderId) {
    return json({ error: "forbidden" }, 403);
  }

  const { data: senderProfile } = await service
    .from("profiles")
    .select("display_name, username")
    .eq("id", senderId)
    .maybeSingle();
  const senderName = senderProfile?.display_name || senderProfile?.username || "Someone";

  const response = await fetch("https://api.onesignal.com/notifications", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Key ${ONESIGNAL_APP_API_KEY}`,
    },
    body: JSON.stringify({
      app_id: ONESIGNAL_APP_ID,
      include_aliases: { external_id: [body.target_user_id] },
      target_channel: "push",
      headings: { en: config.title },
      contents: { en: config.body(body, senderName) },
      url: config.deepLink,
      // Category opt-in enforced via OneSignal tag filters set by the app.
      filters: [{ field: "tag", key: config.tag, relation: "=", value: "true" }],
    }),
  });

  if (!response.ok) {
    const detail = await response.text();
    console.error("onesignal_send_failed", response.status, detail.slice(0, 300));
    return json({ error: "send_failed" }, 502);
  }

  // Record the send for rate limiting (fire-and-forget).
  service.from("notification_log")
    .insert({ sender_id: senderId, type: body.type })
    .then(() => {}, () => {});

  return json({ ok: true });
});

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
