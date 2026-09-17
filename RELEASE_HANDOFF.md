# RELEASE_HANDOFF

State as of 2026-09-11 (this session). Companion docs:
[INFRASTRUCTURE_HANDOFF.md](INFRASTRUCTURE_HANDOFF.md) (external actions),
[SIGNING_HANDOFF.md](SIGNING_HANDOFF.md) (keystore — copy off this machine),
[PERFORMANCE.md](PERFORMANCE.md), [progress.md](progress.md).

## Identifiers & versions

- Android applicationId: `com.abbeysbite.app` (both stores)
- iOS bundle id: `com.abbeysbite.app`
- versionCode 2 · versionName 1.1.0
- Store flavors: `play` | `galaxy` (dimension `store`; BuildConfig.STORE)

## Environment variable NAMES (values live in `.local-secrets.env`, gitignored)

Client-safe (baked into app): SUPABASE_URL, SUPABASE_ANON_KEY,
REVENUECAT_ANDROID_PUBLIC_SDK_KEY, REVENUECAT_GALAXY_PUBLIC_SDK_KEY,
REVENUECAT_IOS_PUBLIC_SDK_KEY, REVENUECAT_TEST_STORE_API_KEY,
REVENUECAT_ENTITLEMENT_ID, REVENUECAT_OFFERING_ID, ONESIGNAL_APP_ID,
AI_BACKEND, CLOUD_AI_FALLBACK_ENABLED, GEMMA_MODEL_FILE, MODEL_DOWNLOAD_URL,
ADMOB_ANDROID_APP_ID, ADMOB_NATIVE_COMMUNITY_AD_UNIT_ID, PRIVACY_POLICY_URL,
TERMS_URL, COMMUNITY_GUIDELINES_URL, SUPPORT_URL.

Tooling/server only (NEVER in the app): SUPABASE_PROJECT_REF,
SUPABASE_ACCESS_TOKEN, SUPABASE_DB_PASSWORD, HUGGINGFACE_TOKEN,
ONESIGNAL_APP_API_KEY, REVENUECAT_WEBHOOK_AUTH_SECRET.

## Supabase (project `xhzlvcyppmecaptjabhr`, ap-southeast-1) — DEPLOYED

- Migrations applied to production: 0001 initial schema+RLS, 0002 storage,
  0003 ai_usage_log, 0004 friendship triggers + analytics_events,
  0005 role grants (required — client writes 42501 without it),
  0006 security hardening (recipe moderation guard, notification_log,
  neutral usernames). ✔
- Edge Functions deployed: ai-gateway (disabled fallback — no GEMMA_API_KEY
  set, returns 503 by design), delete-account, friend-progress,
  revenuecat-webhook, send-notification. ✔
- Function secrets set: RC_WEBHOOK_SECRET, ONESIGNAL_APP_ID,
  ONESIGNAL_APP_API_KEY. ✔ (no Gemini key exists anywhere)
- Auth: `mailer_autoconfirm=true` was enabled via management API for
  development. DECIDE before launch: keep instant sign-up, or switch off to
  require email confirmation — the app supports BOTH (check-your-email UI +
  `abbeysbite://auth-callback` deep link).
- CLI note: the supplied access token cannot use `supabase link` (limited
  privilege) — use `--project-ref` per command and DB pushes via the pooler:
  `supabase db push --db-url "postgresql://postgres.xhzlvcyppmecaptjabhr:<DB_PASSWORD>@aws-0-ap-southeast-1.pooler.supabase.com:5432/postgres" --yes`

## Model — provisioning & runtime

- Runtime: MediaPipe tasks-genai 0.10.35 (LiteRT-LM), androidMain
  `AndroidGemmaEngine`; commonMain contracts `LocalAiEngine` + `ModelManager`;
  iOS engine seat present (bridge lands with Apple phase).
- Artifact: `gemma-3n-E2B-it-int4.litertlm`, 3,655,827,456 bytes,
  SHA-256 `2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6`,
  version tag `gemma-3n-E2B-it-int4-2026-06`.
- Dev copy on this laptop: `~/dev/models/gemma-3n-E2B-it-int4.litertlm`
  (downloaded with the HF token — token is NOT in the app).
- Device provisioning (dev):
  `adb push ~/dev/models/gemma-3n-E2B-it-int4.litertlm /sdcard/Android/data/com.abbeysbite.app/files/models/`
- Production delivery: host the artifact (first-party bucket/CDN), set
  `MODEL_DOWNLOAD_URL`, rebuild → in-app "Set up my AI" downloads with resume,
  progress, size verification, corruption recovery, delete/re-download.
  (Play Asset Delivery caps packs ≪ 3.4 GB, so first-party delivery is the
  correct mechanism for this model.)

## Build artifacts (this machine)

- Play AAB: `composeApp/build/outputs/bundle/playRelease/composeApp-play-release.aab` (signed)
- Galaxy APK: `composeApp/build/outputs/apk/galaxy/release/composeApp-galaxy-release.apk` (signed)
- Debug APKs per flavor under `composeApp/build/outputs/apk/{play,galaxy}/debug/`
- Rebuild: `JAVA_HOME=~/dev/jdk-17.0.20.1+1/Contents/Home ./gradlew :composeApp:bundlePlayRelease :composeApp:assembleGalaxyRelease`

## RevenueCat

- Entitlement `abbeysbite_premium`, offering `default`, packages resolved
  dynamically (monthly/yearly) — no raw product ids in UI logic.
- Identity: Supabase UUID ⇄ RevenueCat App User ID via AppSessionCoordinator
  (logIn on auth, logOut on sign-out; account switching safe).
- Play: goog_ key wired (play flavor). Test Store key used automatically in
  play/iOS DEBUG builds. Galaxy: galx_ key + native GalaxyConfiguration
  (galaxy flavor; TEST billing mode in debug, PRODUCTION in release).
- Webhook: deployed; RevenueCat dashboard entry still needed (see
  INFRASTRUCTURE_HANDOFF).

## iOS status

Compiles (Kotlin, all source sets) — link/simulator blocked ONLY by the
unaccepted Xcode license on this machine. Architecture complete: RC via KMP,
push bridge, STT/TTS/Live, LocalAiEngine seat, privacy manifest, deletion.
Apple production values pending (INFRASTRUCTURE_HANDOFF → Apple).

## Remaining external blockers

All enumerated with exact steps in INFRASTRUCTURE_HANDOFF.md: Play Console
app+subscriptions+service account, Galaxy Seller Portal service account +
ISN URL + items, RevenueCat webhook entry, OneSignal↔FCM credentials, AdMob
ids, legal-page hosting (files ready in `legal/`), model hosting URL, Apple
everything, Xcode license on this machine.
