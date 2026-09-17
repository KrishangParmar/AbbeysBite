// ===========================================================================
// friend-progress — privacy-enforced progress sharing
//
// Returns coarse stats for the caller's ACCEPTED friends, but ONLY the stats
// each friend has explicitly opted into sharing. Enforced server-side with
// the service role so the client can never bypass privacy preferences.
//
// Response: [{ profile, active_days?, meals_logged?, plant_variety?, recipes_published? }]
// ===========================================================================

import { createClient } from "npm:@supabase/supabase-js@2";

Deno.serve(async (req) => {
  const authHeader = req.headers.get("Authorization") ?? "";
  const asUser = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    { global: { headers: { Authorization: authHeader } } },
  );
  const { data: userData, error: userError } = await asUser.auth.getUser();
  if (userError || !userData?.user) {
    return json({ error: "unauthorized" }, 401);
  }
  const me = userData.user.id;

  const service = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  // Accepted friendships involving the caller.
  const { data: friendships } = await service
    .from("friendships")
    .select("requester_id, addressee_id")
    .eq("status", "accepted")
    .or(`requester_id.eq.${me},addressee_id.eq.${me}`);

  const friendIds = (friendships ?? []).map((f) =>
    f.requester_id === me ? f.addressee_id : f.requester_id
  );
  if (friendIds.length === 0) return json([]);

  const weekStart = new Date();
  const day = weekStart.getUTCDay();
  const diffToMonday = (day + 6) % 7;
  weekStart.setUTCDate(weekStart.getUTCDate() - diffToMonday);
  weekStart.setUTCHours(0, 0, 0, 0);
  const weekStartDate = weekStart.toISOString().slice(0, 10);

  const results: unknown[] = [];
  for (const friendId of friendIds) {
    const [{ data: prefs }, { data: profile }] = await Promise.all([
      service.from("progress_sharing_preferences").select("*").eq("user_id", friendId).maybeSingle(),
      service.from("profiles").select("id, username, display_name, avatar_url, bio").eq("id", friendId).maybeSingle(),
    ]);
    if (!profile) continue;
    const entry: Record<string, unknown> = { profile };

    // Nothing shared unless explicitly opted in — hard privacy default.
    if (prefs?.share_active_days || prefs?.share_meals_logged) {
      const { data: meals } = await service
        .from("meal_entries")
        .select("entry_date")
        .eq("user_id", friendId)
        .gte("entry_date", weekStartDate);
      if (prefs?.share_active_days) {
        entry.active_days = new Set((meals ?? []).map((m) => m.entry_date)).size;
      }
      if (prefs?.share_meals_logged) {
        entry.meals_logged = (meals ?? []).length;
      }
    }
    if (prefs?.share_plant_variety) {
      // Coarse approximation server-side: count distinct analysis components.
      const { data: meals } = await service
        .from("meal_entries")
        .select("analysis")
        .eq("user_id", friendId)
        .gte("entry_date", weekStartDate);
      const names = new Set<string>();
      for (const m of meals ?? []) {
        const components = (m.analysis as { components?: { name?: string }[] })?.components ?? [];
        for (const c of components) if (c?.name) names.add(c.name.toLowerCase());
      }
      entry.plant_variety = names.size;
    }
    if (prefs?.share_recipes_published) {
      const { count } = await service
        .from("recipes")
        .select("*", { count: "exact", head: true })
        .eq("author_id", friendId)
        .eq("status", "published");
      entry.recipes_published = count ?? 0;
    }
    results.push(entry);
  }

  return json(results);
});

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
