# INFRASTRUCTURE_HANDOFF — external actions required

Living document. Every dashboard/account action that cannot be completed from
this environment is recorded here with exact steps. Updated throughout the
mission; finalized at completion.

## Blocked on this machine (owner action, one-time)

1. **Accept the Xcode license** (blocks git, python3, xcrun, iOS linking):
   ```bash
   sudo xcodebuild -license accept
   ```
   After this: `git init && git add -A && git commit` the repo (no git history
   exists yet — snapshots are in the session scratchpad).

## External dashboard actions (status tracked during mission)

### Google Play
- [ ] Create app `com.abbeysbite.app` in Play Console; internal testing track.
- [ ] Create subscriptions `abbeysbite_premium_monthly` / `abbeysbite_premium_annual`
      (base plans + free-trial offer) and attach in RevenueCat → Products →
      entitlement `abbeysbite_premium`, offering `default`.
- [ ] Play service-account JSON → RevenueCat Play credentials (PENDING supplied).

### Samsung Galaxy Store
- [ ] Seller Portal → Assistance → API Service → Create Service Account →
      enable **Publishing & ITEM** + **GSS** → Create → **Download Key** →
      copy Service Account ID.
- [ ] RevenueCat → Galaxy app config → enter Service Account ID + upload key.
- [ ] Seller Portal → app → In App Purchase → **Instant Server Notification URL** →
      paste: `https://api.revenuecat.com/v1/incoming-webhooks/galaxy/app4c9d6c5356`
- [ ] Create Galaxy subscription items mirroring monthly/yearly; attach to the
      same `abbeysbite_premium` entitlement / `default` offering in RevenueCat.
- [ ] Real-device Galaxy TEST purchase (requires physical Galaxy + Samsung account).

### RevenueCat
- [ ] Webhooks → add Supabase webhook URL
      `https://xhzlvcyppmecaptjabhr.supabase.co/functions/v1/revenuecat-webhook`
      with header `Authorization: Bearer <REVENUECAT_WEBHOOK_AUTH_SECRET>`
      (value in `.local-secrets.env`).
      NOTE: the function is deployed with `--no-verify-jwt` and authenticates
      via that bearer secret itself; the server secret `RC_ENTITLEMENT_ID`
      is set to `abbeysbite_premium` so the mirror matches the real entitlement.
      Both were fixed on 2026-09-13 and re-verified live (see REVENUECAT_HANDOFF.md).

### OneSignal / Firebase
- [ ] Create Firebase project for `com.abbeysbite.app`, generate FCM service
      account JSON, upload in OneSignal → Settings → Platforms → Android.
      (Client + server code complete; delivery blocked only on this.)
- [ ] iOS APNs key upload — deferred with Apple setup.

### AdMob
- [ ] Create AdMob app for `com.abbeysbite.app`; create a **Native Advanced**
      ad unit for the Community feed; put ids into `.local-secrets.env`
      (`ADMOB_ANDROID_APP_ID`, `ADMOB_NATIVE_COMMUNITY_AD_UNIT_ID`) and rebuild
      release. Release builds ship with ads DISABLED until these are real.

### Legal hosting
- [ ] Host the pages in `legal/` (privacy, terms, community guidelines,
      support) at final URLs; set the four `*_URL` values in `.local-secrets.env`.

### Apple (intentionally deferred)
- [ ] Paid Apple Developer account; App Store Connect app `com.abbeysbite.app`;
      RevenueCat iOS app + `appl_` key; IAP products; APNs key; then fill
      `REVENUECAT_IOS_PUBLIC_SDK_KEY` and Secrets.xcconfig.

### Model hosting (production delivery)
- [ ] Host `gemma-3n-E2B-it-int4.litertlm` (see MODEL_PROVISIONING in
      RELEASE_HANDOFF.md) at a first-party CDN/bucket URL; set
      `MODEL_DOWNLOAD_URL` in `.local-secrets.env`; rebuild. Dev/test uses
      side-load provisioning (documented) — the HF token is never shipped.

## Supabase deployment state — DONE (updated 2026-09-13)

- [x] Migrations 0001–**0006** pushed to production. 0005 = role grants (a
      fresh PG17 project does NOT auto-grant table privileges to `authenticated`
      — symptom was 42501 on every client write despite correct RLS).
      **0006 = security hardening (2026-09-13):** blocked-user delete guard,
      recipe moderation guard, `notification_log` (rate limiting), neutral
      auto-usernames (stops leaking the email local-part), and revoking the
      blanket default-privilege grant so future tables start fail-closed.
- [x] Edge Functions deployed (all re-deployed 2026-09-13):
      ai-gateway (dormant, flagged debug fallback — every task now capped and
      the task allow-list enforced), delete-account (storage cleanup now
      paginates), friend-progress, revenuecat-webhook
      (**`--no-verify-jwt`; correct entitlement id**), send-notification
      (UUID validation, recipe_liked verified server-side, per-sender rate limit).
- [x] Function secrets set: RC_WEBHOOK_SECRET, RC_ENTITLEMENT_ID
      (`abbeysbite_premium`), ONESIGNAL_APP_ID, ONESIGNAL_APP_API_KEY.
      GEMMA_API_KEY intentionally NOT set — production AI is on-device, no Gemini.
- [x] Auth `mailer_autoconfirm=true` (dev). Before launch, decide whether to
      require email confirmation — both flows are implemented in-app.
- Note: `supabase link` fails with this access token (limited privilege).
  Use `--project-ref xhzlvcyppmecaptjabhr` for functions/secrets and
  `--db-url` via the ap-southeast-1 pooler for db push (see RELEASE_HANDOFF).
- Live-verified against production: signup (profile trigger), preferences save,
  RevenueCat Test Store purchase under the Supabase UUID, entitlement isolation
  across account switch (2026-09-11); meal_entries RLS isolation, webhook auth +
  entitlement match + mirror write, blocked-user delete guard, and every
  send-notification abuse guard (2026-09-13).
