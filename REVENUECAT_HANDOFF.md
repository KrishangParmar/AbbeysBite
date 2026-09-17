# RevenueCat Handoff

Everything about entitlements, identity, the two Android stores, and the
server-side webhook. Reflects the code as of 2026-09-13.

## Canonical configuration

| Field | Value |
| --- | --- |
| Entitlement id | `abbeysbite_premium` |
| Offering id | `default` |
| App User ID | the **Supabase Auth UUID** (set via `Purchases.logIn` after sign-in) |
| Android (Play) key | `goog_…` (`REVENUECAT_ANDROID_PUBLIC_SDK_KEY`) |
| Android (Galaxy) key | `galx_…` (`REVENUECAT_GALAXY_PUBLIC_SDK_KEY`) |
| iOS key | `appl_…` (`REVENUECAT_IOS_PUBLIC_SDK_KEY`, pending Apple phase) |
| Test Store key | `test_…` (`REVENUECAT_TEST_STORE_API_KEY`) — debug only |

Product ids are **not hardcoded** — the paywall resolves packages dynamically
from the `default` offering (monthly/annual detected from each product's
subscription period). Create the products/packages in the RevenueCat dashboard
and the app picks them up.

## Key selection (release-safe, unit-tested)

Selection is a pure function, `RevenueCatKeySelection.selectApiKey(...)`, with
9 tests locking these guarantees:

- **A release build of any flavor never receives the Test Store key.**
- galaxy builds always use `galx_` (with `GalaxyBillingMode.TEST` in debug —
  the Test Store key would bypass real Galaxy billing).
- debug play / appstore builds prefer `test_` so purchases are exercisable
  without store credentials.
- flavors never cross-activate another store's key (baked at flavor level via
  `BuildConfig.STORE`).

There is exactly **one** RevenueCat runtime configuration per build. On Play/
iOS the KMP `Purchases.configure` is used; on Galaxy the native
`GalaxyConfiguration` is used and the KMP wrapper is attached to the native
singleton (see `PlatformBilling.android.kt`). No duplicate singletons.

## Identity binding (no cross-account contamination)

`AppSessionCoordinator` observes auth state and, on sign-in, calls
`billingManager.loginUser(supabaseUuid)`; on sign-out it calls `logoutUser()`
and now **also wipes all in-memory per-user caches** (journal, pantry,
preferences, the Improve chat session) so switching accounts can't leak state.
Weekly-insight cache keys are user-scoped. Verified live on the emulator:
tester1 ↔ tester2 each see only their own data.

Verified previously (prior session, Test Store, emulator): purchase →
"Premium active" under the Supabase UUID → logout → second account is free.

## Server-side mirror + webhook

`revenuecat_customers.is_premium` is kept in sync by the `revenuecat-webhook`
Edge Function and used by `ai-gateway` for defense-in-depth rate limiting.

**Two fixes this pass — both were silently breaking premium server-side:**

1. The webhook checked the wrong entitlement id (`premium`), so no real
   purchase ever set `is_premium=true`. It now reads
   `RC_ENTITLEMENT_ID` (set to `abbeysbite_premium`) and matches correctly.
2. The webhook must be deployed with **`--no-verify-jwt`** — RevenueCat calls
   it with `Authorization: Bearer <RC_WEBHOOK_SECRET>` (not a Supabase JWT),
   which the function verifies itself. With the default JWT gate on, every
   RevenueCat call was rejected at the platform with 401. `supabase/config.toml`
   now pins `[functions.revenuecat-webhook] verify_jwt = false`.

Verified live: no/invalid auth → 401; valid secret + `abbeysbite_premium`
granting event → 200 and the mirror row flips to `is_premium=true`. TRANSFER
events now also revoke the source account (`event.transferred_from`).

### Dashboard steps still required (you)

1. Create the app(s) in RevenueCat for `com.abbeysbite.app` (Play + Galaxy;
   iOS with the Apple phase).
2. Create products and attach them to entitlement `abbeysbite_premium` and
   offering `default`.
3. Integrations → Webhooks → point at the deployed `revenuecat-webhook` URL
   with header `Authorization: Bearer <RC_WEBHOOK_SECRET>` (the value in
   `.local-secrets.env`). The Supabase function secret `RC_WEBHOOK_SECRET`
   must match.
4. Galaxy: create the RevenueCat Galaxy service account, upload the Samsung
   Seller private key, set the Service Account ID and required scopes, and
   paste the RevenueCat ISN/incoming-notification URL into the Samsung Seller
   Portal (details in `INFRASTRUCTURE_HANDOFF.md`).

## Galaxy specifics

- Real Galaxy purchases require a **physical Samsung device** signed into a
  Galaxy Store test account — they cannot be validated on the emulator.
- The Galaxy release APK is at
  `composeApp/build/outputs/apk/galaxy/release/composeApp-galaxy-release.apk`.
- Debug Galaxy builds use `GalaxyBillingMode.TEST` with the real `galx_` key.
