// ===========================================================================
// revenuecat-webhook — keeps revenuecat_customers.is_premium in sync
//
// Configure in the RevenueCat dashboard (Integrations → Webhooks) pointing to
// this function's URL, and set an Authorization header secret:
//   supabase secrets set RC_WEBHOOK_SECRET=<random string>
// RevenueCat app_user_id must be the Supabase auth user id (the app calls
// Purchases.logIn(userId) after sign-in).
// ===========================================================================

import { createClient } from "npm:@supabase/supabase-js@2";

const WEBHOOK_SECRET = Deno.env.get("RC_WEBHOOK_SECRET") ?? "";
// Must match the RevenueCat dashboard AND the app (AppConfig.ENTITLEMENT_PREMIUM).
const ENTITLEMENT_ID = Deno.env.get("RC_ENTITLEMENT_ID") ?? "abbeysbite_premium";

Deno.serve(async (req) => {
  if (req.method !== "POST") {
    return new Response("method_not_allowed", { status: 405 });
  }
  if (!WEBHOOK_SECRET || req.headers.get("Authorization") !== `Bearer ${WEBHOOK_SECRET}`) {
    return new Response("unauthorized", { status: 401 });
  }

  const payload = await req.json().catch(() => null);
  const event = payload?.event;
  if (!event) return new Response("invalid_body", { status: 400 });

  const appUserId: string | undefined = event.app_user_id;
  if (!appUserId || appUserId.startsWith("$RCAnonymousID")) {
    return new Response("ignored_anonymous", { status: 200 });
  }

  const activeEntitlements: string[] = event.entitlement_ids ?? [];
  const type: string = event.type ?? "";
  const grantingTypes = new Set([
    "INITIAL_PURCHASE", "RENEWAL", "UNCANCELLATION", "PRODUCT_CHANGE",
    "NON_RENEWING_PURCHASE", "SUBSCRIPTION_EXTENDED", "TRANSFER",
  ]);
  const revokingTypes = new Set(["EXPIRATION", "CANCELLATION_WITH_REFUND", "REFUND"]);

  let isPremium: boolean | null = null;
  if (grantingTypes.has(type) && activeEntitlements.includes(ENTITLEMENT_ID)) isPremium = true;
  if (revokingTypes.has(type)) isPremium = false;
  if (isPremium === null) return new Response("ignored_event", { status: 200 });

  const service = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
  );

  // TRANSFER moves the entitlement between accounts — revoke the sources,
  // otherwise both accounts stay premium in the mirror forever.
  if (type === "TRANSFER") {
    const sources: string[] = (event.transferred_from ?? []).filter(
      (id: string) => id && !id.startsWith("$RCAnonymousID"),
    );
    for (const source of sources) {
      const { error } = await service.from("revenuecat_customers").upsert({
        user_id: source,
        rc_app_user_id: source,
        is_premium: false,
      });
      if (error) console.error("transfer source revoke failed", source, error);
    }
  }

  const { error } = await service.from("revenuecat_customers").upsert({
    user_id: appUserId,
    rc_app_user_id: appUserId,
    is_premium: isPremium,
  });
  if (error) {
    console.error("webhook upsert failed", error);
    return new Response("db_error", { status: 500 });
  }
  return new Response("ok", { status: 200 });
});
