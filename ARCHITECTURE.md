# Architecture

## Overview

Abbey's Bite is a Kotlin Multiplatform app with a **single shared module**
(`composeApp`) targeting Android and iOS (arm64 + simulator). Compose
Multiplatform renders 100% of the UI on both platforms; a thin SwiftUI shell
hosts the Compose view controller on iOS.

```
┌────────────────────────────────────────────────────────────┐
│                      commonMain                            │
│                                                            │
│  features/   improve · community · journal · profile ·     │
│              onboarding · auth · paywall · shell           │
│  navigation/ type-safe routes + NavHost                    │
│  core/       designsystem (theme/components) · config      │
│              (Brand, AppConfig, AppSecrets*) · network ·   │
│              util (AppResult/AppError)                     │
│  data/       model · repository (interface + Supabase +    │
│              Local/Demo impls) · demo fixtures             │
│  domain/     JournalAggregator · FriendshipRules ·         │
│              Validators (pure, unit-tested)                │
│  ai/         AiProvider · prompts · parser ·               │
│              GemmaGatewayProvider · MockAiProvider         │
│  billing/    BillingManager · RevenueCat impl ·            │
│              FreeTierLimiter                               │
│  analytics/  Analytics abstraction + events               │
│  platform/   INTERFACES ONLY (MediaPicker, Speech, TTS,    │
│              Haptics, Share, Push, UrlOpener, YouTube)     │
│  di/         Koin modules                                  │
├──────────────────────────┬─────────────────────────────────┤
│       androidMain        │            iosMain              │
│  MainActivity/App        │  MainViewController (Compose)   │
│  ActivityResult camera/  │  UIImagePickerController        │
│  gallery + permissions   │  SFSpeechRecognizer             │
│  SpeechRecognizer        │  AVSpeechSynthesizer            │
│  TextToSpeech, Vibrator  │  UIKit haptics/share            │
│  OneSignal Android SDK   │  Swift PushBridge → OneSignal   │
│  WebView YouTube embed   │  WKWebView YouTube embed        │
└──────────────────────────┴─────────────────────────────────┘
```

\* `AppSecrets.kt` is generated at build time from `local.properties`/env — no
credentials in source control.

## Key decisions

### Single module, layered packages
A multi-module KMP build adds real complexity (framework export, dependency
wiring) for little benefit at this size. Discipline lives in package layering:
`features → domain/data/ai/billing → core/platform`, enforced by convention
and reviewed imports.

### Repository pattern with dual implementations
Every repository is an interface with two implementations:

- `Supabase*Repository` — production, talking to Postgres/Auth/Storage/
  Functions through supabase-kt.
- `Local*/Demo*Repository` — settings-backed offline implementation used when
  credentials are absent. This is what makes the app fully explorable on a
  fresh checkout and keeps demos/tests hermetic.

Selection happens once, in `di/AppModules.kt`, keyed on
`isSupabaseConfigured`. Nothing else in the app knows which world it's in.

### AI layer — on-device Gemma (2026-09-11 revision)

**Primary production path (AI_BACKEND=local, the default):**

```
camera/text → LocalGemmaProvider → LocalAiEngine (expect/actual)
                                     └ Android: MediaPipe LLM Inference
                                       (tasks-genai / LiteRT-LM) running
                                       gemma-3n-E2B-it-int4.litertlm
                                     └ iOS: engine seat, native bridge lands
                                       with the Apple production phase
```

- `ModelManager` owns acquisition (first-party `MODEL_DOWNLOAD_URL` download
  with resume/progress, or dev side-load via adb), size + SHA-256 integrity,
  corruption recovery and deletion. The HF token is a development-time
  acquisition tool only and never ships.
- Streaming: `AiProvider.streamChat` (token flow) powers Chat and Live;
  cancellation propagates to the native session (barge-in).
- Local failures surface as local failures — there is NO silent cloud
  fallback. `GemmaGatewayProvider` (below) still compiles but activates only
  with `AI_BACKEND=gateway` + `CLOUD_AI_FALLBACK_ENABLED=true` + debug build.

`AiProvider` is the only AI surface the app sees:

```kotlin
interface AiProvider {
    suspend fun analyzeMeal(imageBytes, description, preferences, pantry,
                            correction, previousAnalysis): AppResult<MealAnalysis>
    suspend fun chat(context: MealChatContext, userMessage): AppResult<String>
    suspend fun parseRecipeQuery(query): AppResult<RecipeSearchIntent>
    suspend fun weeklyInsight(entries, preferences): AppResult<WeeklyInsight>
}
```

- **`LocalGemmaProvider`** (production): the on-device path above. Structured
  outputs are demanded via prompt and parsed defensively (`AiResponseParser`
  extracts the first balanced JSON object, decodes leniently, clamps list
  sizes).
- **`GemmaGatewayProvider`** (debug-only fallback): calls the `ai-gateway`
  Supabase Edge Function with the user's JWT. Compiled but dormant — it
  activates only in a debug build with `AI_BACKEND=gateway` +
  `CLOUD_AI_FALLBACK_ENABLED=true`, and its Google AI key is intentionally
  unset in production. Never selected silently.
- **`MockAiProvider`**: deterministic, preference/pantry-aware fake used
  without credentials. Also exercised by unit tests.
- On Android, on-device Gemma is the production path today. iOS holds an engine
  seat whose native bridge lands with the Apple production phase; the
  `AiProvider` interface keeps that a one class + one binding change.

The product philosophy ("never shame, always help add") is encoded **once**
in `AiPrompts.PHILOSOPHY` and shared by every task prompt.

### Voice mode
**Live mode** streams generation and speaks it as it arrives: tokens from
`AiProvider.streamChat` are sentence-chunked and handed to
`TextToSpeechService` progressively, with true barge-in (new speech cancels
in-flight generation and playback). `VoiceViewModel` orchestrates it as a
state machine (Listening → Thinking → Speaking → Listening…), and the same
turn-based reliability principles still apply — STT → text → AI → text → TTS,
with no experimental full-duplex audio in the critical path.

### Free tier & entitlements
- `BillingManager` exposes `StateFlow<PremiumState>`; the RevenueCat KMP SDK
  implementation refreshes on start/login. Without keys, an Unconfigured
  implementation reports free tier + "purchases unavailable".
- `FreeTierLimiter` is pure logic over an injected key-value store — trivially
  unit-tested; limits live in `AppConfig.FreeTier`.
- Defense in depth: the ai-gateway function *also* enforces daily caps
  server-side using entitlement state synced by the RevenueCat webhook.

### Error handling
All failures funnel through `AppError` (network/timeout/AI-unavailable/
invalid-response/unauthorized/upload/limit/…) with human copy at the type
level. `AppResult<T>` is the return currency of every repository and provider;
screens render friendly states + retry, never stack traces.

### Design system
`core/designsystem` holds the theme (light/dark Material 3 schemes + extended
`AppColors` for nutrient/status tints), system-font typography scale, shapes
(18–24dp radii), and the component kit (AppCard, PrimaryButton, StatusPill,
SkeletonBox, EmptyState, InitialsAvatar). Brand values come from
`core/config/Brand.kt` — the single rebranding point.

Non-negotiables encoded in the system: no red for ordinary food states
(statuses are present/could-add/uncertain with calm tints), generous
whitespace, skeleton loaders instead of spinners for content, progressive
status copy during long AI operations.

### Navigation
Type-safe navigation-compose routes (`Route` sealed interface, `@Serializable`
data objects/classes). The four tabs live inside `MainShell`; full-screen
flows (chat, voice, recipe detail/editor, paywall, settings) are pushed on the
root NavHost so they cover the bottom bar.

### Share cards
The "Two tiny additions" card is a Compose subtree recorded into a
`GraphicsLayer`, exported via `toImageBitmap()` + a per-platform PNG encoder,
and handed to the native share sheet. No server round-trip.

## Build & toolchain

| Piece | Version |
| --- | --- |
| Kotlin | 2.4.10 |
| Compose Multiplatform | 1.12.0 (material3 1.9.0, icons 1.7.3) |
| AGP / Gradle | 9.4.0 / 9.7.1 (compat flags `android.builtInKotlin=false`, `android.newDsl=false` for the single-module KMP app layout) |
| compileSdk / targetSdk / minSdk | 37 / 36 / 26 |
| supabase-kt | 3.8.0 (BOM) |
| Ktor | 3.5.2 |
| Koin | 4.2.2 |
| Coil | 3.6.2 |
| RevenueCat KMP | 3.7.0 |
| OneSignal Android | 5.10.0 |

The iOS framework (`ComposeApp`, static) is built by Gradle and embedded by an
Xcode build phase (`embedAndSignAppleFrameworkForXcode`).
