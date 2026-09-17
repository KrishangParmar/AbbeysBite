# Abbey's Bite

> **Eat what you love. Add what helps.**

Abbey's Bite is a cross-platform (iOS + Android) nutrition app built with
Kotlin Multiplatform and Compose Multiplatform. Instead of counting calories
or scoring meals, it helps people make the food they already love more
satisfying with practical additions around **protein, fibre and healthy fats**.

Built for **RevenueCat Shipaton 2026** — Influencer Award: Nutrition & Healthy
Eating (Abbey's Kitchen).

## Product philosophy

- Never shames food. No "bad", "cheat", "unhealthy" labels, no red warning
  scores, no calorie budgets, no weight-loss countdowns.
- A meal analysis answers one question: *what's already on the plate, and
  what's one easy thing that could make it more satisfying?*
- Nutrition numbers are estimates and always labeled as such.

## Feature overview

| Tab | What it does |
| --- | --- |
| **Improve** | Photograph (or describe) a meal → AI analysis of protein/fibre/healthy fat → 2–4 realistic additions that respect your pantry, preferences, allergies and cooking setup. Contextual chat ("I only have eggs and cheese") and turn-based voice mode. |
| **Community** | "Hidden Gems" recipe feed (For You / Trending / Quick / Budget / Vegetarian / New), recipe detail with embedded YouTube player, publishing, likes, saves, keyword + AI natural-language search. |
| **Journal** | Visual food journal (not a calorie diary): daily meals, gentle weekly rhythm strip, nourishment snapshot, AI weekly reflection (max 3 observations + 2 ideas). |
| **You** | Profile, friends & requests, opt-in progress sharing, pantry, preferences, notifications, privacy, billing, help, account deletion. |

Premium (RevenueCat entitlement `abbeysbite_premium`): unlimited analyses & chat, voice
mode, pantry-aware AI, deeper weekly insights. Free tier limits are centralized
in `AppConfig.FreeTier`.

## Architecture (short version)

- **Single shared module** `composeApp` (commonMain / androidMain / iosMain) —
  UI, state, models, repositories, use cases, auth, entitlements and analytics
  are all shared. Platform code is limited to camera/photo picker, speech,
  TTS, haptics, share sheet, notifications, ads and the AI runtime bridge.
- **AI runs ON-DEVICE.** The production path is `LocalGemmaProvider` →
  `LocalAiEngine` → MediaPipe LLM Inference running
  **Gemma 3n E2B Instruct int4 (LiteRT-LM)** entirely on the phone. Meal
  photos and conversations never leave the device; there is no Gemini API key
  anywhere. `ModelManager` handles acquisition/integrity/recovery
  ("Preparing your private AI…"). The old cloud gateway remains compiled only
  as an explicitly-flagged debug fallback and is never selected silently.
  See [PERFORMANCE.md](PERFORMANCE.md) for tuning and measurements.
- **Two AI experiences** after analysis: **Chat** (streamed, with dictation)
  and **Live** — a real-time voice conversation (streaming generation →
  progressive sentence TTS) with true barge-in; both share context.
- **Store flavors**: `play` (Google Play / goog_ key) and `galaxy` (Samsung
  Galaxy Store / galx_ key via the native RevenueCat Galaxy configuration,
  TEST billing mode in debug). Debug play builds use the RevenueCat Test Store.
- **Demo mode**: with no Supabase credentials configured the app runs fully
  offline with local persistence and seeded demo content — every flow works.
- See [ARCHITECTURE.md](ARCHITECTURE.md), [DATABASE.md](DATABASE.md),
  [RELEASE_HANDOFF.md](RELEASE_HANDOFF.md), [INFRASTRUCTURE_HANDOFF.md](INFRASTRUCTURE_HANDOFF.md).

## Repository layout

```
composeApp/            Shared KMP module (UI + logic, all platforms)
  src/commonMain/      Shared code (features, data, ai, billing, design system)
  src/androidMain/     Android platform services + entry points
  src/iosMain/         iOS platform services + entry point
  src/commonTest/      Unit tests (73 tests)
iosApp/                Xcode wrapper project (SwiftUI host + push bridge)
supabase/
  migrations/          Versioned SQL (schema, RLS, storage, usage log)
  functions/           Edge Functions (ai-gateway, delete-account,
                       friend-progress, revenuecat-webhook, send-notification)
store-assets/          Play Store icon etc.
```

## Local setup

### Prerequisites

- JDK 17+
- Android SDK (compileSdk 37 platform + build-tools; `sdkmanager` works fine)
- Xcode 16+ (for iOS) with the license accepted: `sudo xcodebuild -license accept`
- No Gradle install needed — the wrapper downloads Gradle 9.7.1

### Configuration

Copy values from [.env.example](.env.example) into `local.properties` at the
repo root (this file is gitignored):

```properties
sdk.dir=/path/to/android-sdk
SUPABASE_URL=https://YOUR-PROJECT-REF.supabase.co
SUPABASE_ANON_KEY=eyJ...
REVENUECAT_ANDROID_PUBLIC_SDK_KEY=goog_...
REVENUECAT_IOS_PUBLIC_SDK_KEY=appl_...
ONESIGNAL_APP_ID=...
```

The Samsung Galaxy Store flavor also reads `REVENUECAT_GALAXY_PUBLIC_SDK_KEY`
(`galx_...`) and debug/play builds pick up `REVENUECAT_TEST_STORE_API_KEY`
(`test_...`). See [.env.example](.env.example) for the full key list.

Gradle generates `AppSecrets.kt` from these at build time (for **both**
platforms — the iOS framework is compiled by Gradle too). Leave any value
empty and the app degrades gracefully: no Supabase → offline demo mode; no
RevenueCat key → free tier with purchases marked unavailable; no OneSignal id
→ local notification permission only.

### Android

The Android app ships two store flavors (`play`, `galaxy`), so Gradle tasks are
flavor-specific:

```bash
./gradlew :composeApp:assemblePlayDebug        # build the Play debug APK
./gradlew :composeApp:testPlayDebugUnitTest    # run unit tests (play flavor)
adb install composeApp/build/outputs/apk/play/debug/composeApp-play-debug.apk
```

### iOS

1. Accept the Xcode license if you haven't: `sudo xcodebuild -license accept`
2. `cd iosApp && cp iosApp/Config/Secrets.example.xcconfig iosApp/Config/Secrets.xcconfig`
   (optional — used for bundle-level overrides; the shared framework reads
   `local.properties`)
3. Open `iosApp/iosApp.xcodeproj` in Xcode, select your team, run.
   The "Compile Kotlin Framework" build phase invokes
   `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` automatically.
4. Push (optional): add the OneSignal SPM package
   (`https://github.com/OneSignal/OneSignal-iOS-SDK`) to the iosApp target.
   `PushBridge.swift` picks it up automatically via `#if canImport`.

## Supabase setup

1. Create a project at [supabase.com](https://supabase.com).
2. Apply migrations `0001`–`0006` in order (SQL editor, or `supabase db push`
   with the CLI): `0001_initial_schema.sql`, `0002_storage.sql`,
   `0003_ai_usage_log.sql`, `0004_friendship_rules_and_analytics.sql`,
   `0005_role_grants.sql`, `0006_security_hardening.sql`. **Do not skip
   `0005`** — without its role grants every client write fails with Postgres
   error `42501`. `0006` adds security hardening (recipe moderation guard,
   notification log, neutral usernames).
3. Deploy the Edge Functions:
   ```bash
   supabase functions deploy ai-gateway
   supabase functions deploy delete-account
   supabase functions deploy friend-progress
   supabase functions deploy revenuecat-webhook --no-verify-jwt
   supabase functions deploy send-notification
   ```
   `revenuecat-webhook` **must** deploy with `--no-verify-jwt`: it
   authenticates callers with its own `RC_WEBHOOK_SECRET` (bearer token), so a
   Supabase JWT check would 401 every RevenueCat delivery.
4. Set server-side secrets (never in the app):
   ```bash
   supabase secrets set RC_WEBHOOK_SECRET=<long random string>
   supabase secrets set RC_ENTITLEMENT_ID=abbeysbite_premium
   ```
   The production AI runs **on-device** and needs no server model key.
   `GEMMA_API_KEY` is only required if you deliberately enable the dormant
   debug cloud fallback (see AI provider setup) — it is intentionally unset in
   production.
5. Enable email auth (Authentication → Providers → Email).

## RevenueCat setup

1. Create a project with iOS + Android apps (bundle id / application id:
   `com.abbeysbite.app`).
2. Products: `abbeysbite_premium_monthly`, `abbeysbite_premium_annual`
   (create matching subscriptions in App Store Connect / Play Console, attach
   a free trial to the intro offer).
3. Entitlement: `abbeysbite_premium`, attached to both products.
4. Offering: `default` containing both packages.
5. Put the **public** SDK keys in `local.properties` (see above).
6. Webhook: point RevenueCat → Integrations → Webhooks at the deployed
   `revenuecat-webhook` function URL with header
   `Authorization: Bearer <RC_WEBHOOK_SECRET>`. This keeps server-side
   entitlement state in sync for AI rate limiting.

## OneSignal setup

1. Create a OneSignal app (iOS + Android).
2. Put the app id in `local.properties` (`ONESIGNAL_APP_ID`).
3. Android works out of the box. iOS additionally needs the SPM package (see
   iOS setup) and an APNs key uploaded to OneSignal.
4. Notification categories are user-controlled in-app (You → Notifications);
   permission is requested contextually, never on first launch.

## AI provider setup

The app talks to `AiProvider` only. The **production** implementation is
`LocalGemmaProvider` → `LocalAiEngine` → MediaPipe LLM Inference
(`tasks-genai` / LiteRT-LM) running **Gemma 3n E2B Instruct int4** entirely
on-device (`AI_BACKEND=local`, the default). No cloud key is required and no
meal photo or conversation leaves the phone. Swapping providers = one new class
+ one Koin binding; prompts live in `ai/AiPrompts.kt`, response parsing in
`ai/AiResponseParser.kt`.

`GemmaGatewayProvider` (the `ai-gateway` Edge Function → Google AI API) still
compiles but is a **dormant, explicitly-flagged debug fallback**: it activates
only in a debug build with `AI_BACKEND=gateway` **and**
`CLOUD_AI_FALLBACK_ENABLED=true`, and its `GEMMA_API_KEY` server secret is
intentionally left unset in production. It is never selected silently.

Without credentials, `MockAiProvider` produces sensible deterministic analyses
so the full product is usable offline (and in demos/tests).

## Testing

```bash
./gradlew :composeApp:testPlayDebugUnitTest
```

73 unit tests (8 classes) cover: AI schema parsing & lenient status mapping,
meal-analysis status mapping, `ModelManager` acquisition/integrity/recovery
(9 tests), RevenueCat SDK-key selection per flavor (9 tests),
free-tier/premium entitlement gating, friendship state transitions, privacy
authorization (sharing requires accepted friendship AND opt-in), recipe
validation, YouTube URL validation, journal aggregation
(rhythm/snapshot/streak/plant variety) and weekly statistics.

Demo fixtures (`data/demo/DemoData.kt`) seed profiles, recipes and social
activity in demo mode only — production builds with credentials never see
fake content.

## Store builds

See [STORE_RELEASE_CHECKLIST.md](STORE_RELEASE_CHECKLIST.md) for the complete
iOS + Android submission checklist (identifiers, permissions copy, privacy
manifest, subscriptions, data safety, screenshots, review notes).

## Known limitations

- **On-device Gemma** is the primary production AI on Android (see
  ARCHITECTURE.md → AI layer). The iOS engine seat is in place; its native
  bridge lands with the Apple production phase. The cloud gateway remains only
  as a flagged debug fallback.
- **Live voice mode** streams generation into sentence-chunked progressive TTS
  with barge-in (`VoiceViewModel`); the turn-based Chat path (STT → text → AI →
  TTS) is still available and keeps the same reliability principles.
- Recipe feed personalization ("For You") is a simple shuffle/recency blend;
  no recommendation engine yet (intentional — see product rules).
- iOS speech recognition requires network on some devices (SFSpeechRecognizer
  limitation).
- Rebranding: change `core/config/Brand.kt`, the Android
  `strings.xml`/launcher icon, and the iOS display name/asset catalog — no
  other code changes needed.
