# Store Release Checklist

Working through this top-to-bottom yields a submittable build on both stores.
Items marked ✅ are already done in the repo; ☐ needs an action in a dashboard
or on your machine.

## Shared (both stores)

- ✅ Product name: **Abbey's Bite** (single rebrand point: `core/config/Brand.kt`)
- ✅ No credentials in source; build-time injection via `local.properties` / xcconfig
- ✅ Account deletion in-app (You → Delete account → typed confirmation → server-side wipe)
- ✅ UGC safety: report recipe, report user, block user, delete own content,
  moderation status field, community guidelines link
- ✅ Nutrition disclaimers ("estimates only", allergen safety) on analysis + onboarding + preferences
- ☐ Host real Privacy Policy / Terms / Community Guidelines and set the
  `PRIVACY_POLICY_URL` / `TERMS_URL` / `COMMUNITY_GUIDELINES_URL` / `SUPPORT_URL`
  build config values (legal content lives in `core/config/LegalContent.kt`)
- ☐ Supabase production project + migrations applied + Edge Functions deployed
  (see README → Supabase setup)
- ☐ RevenueCat project configured: products `abbeysbite_premium_monthly` /
  `abbeysbite_premium_annual`, entitlement `abbeysbite_premium`, offering
  `default`, free trial on intro offer, webhook → `revenuecat-webhook` function
- ☐ OneSignal app created; APNs key + FCM credentials uploaded

## iOS

| Item | Status |
| --- | --- |
| Bundle identifier `com.abbeysbite.app` | ✅ (`Config/Project.xcconfig`) |
| Version / build (`MARKETING_VERSION` 1.0.0 / 1) | ✅ |
| App icon (1024 universal, asset catalog) | ✅ generated |
| Launch screen (`UILaunchScreen` + LaunchBackground color, light/dark) | ✅ |
| Camera permission text (`NSCameraUsageDescription`) | ✅ |
| Photo library permission text (`NSPhotoLibraryUsageDescription`) | ✅ |
| Microphone permission text (`NSMicrophoneUsageDescription`) | ✅ |
| Speech recognition text (`NSSpeechRecognitionUsageDescription`) | ✅ |
| Notification permission (requested contextually via OneSignal) | ✅ code; ☐ APNs key in OneSignal |
| Privacy manifest (`PrivacyInfo.xcprivacy`) | ✅ |
| Encryption exemption (`ITSAppUsesNonExemptEncryption=false`) | ✅ |
| RevenueCat iOS key in config | ☐ |
| StoreKit products created in App Store Connect (monthly + annual + trial) | ☐ |
| Restore purchases button | ✅ (paywall) |
| Account deletion | ✅ |
| Privacy policy URL + Terms URL in App Store Connect | ☐ |
| App Privacy questionnaire (email, photos, user content, purchases — no tracking) | ☐ |
| Screenshots (6.7" + 5.5" or use 6.7"+6.1") | ☐ |
| Review notes: demo account credentials (create one via email sign-up) | ☐ |
| Accept Xcode license on build machine (`sudo xcodebuild -license accept`) | ☐ |
| Archive: Product → Archive with Release config, upload via Organizer | ☐ |
| Optional: add OneSignal SPM package for push | ☐ |

## Android

| Item | Status |
| --- | --- |
| Application ID `com.abbeysbite.app` | ✅ |
| versionCode 2 / versionName 1.1.0 | ✅ |
| Adaptive icon (foreground vector + monochrome + background color) | ✅ |
| Camera permission (`CAMERA`, feature optional) | ✅ |
| Photo picker (no storage permission needed — Photo Picker API) | ✅ |
| Microphone permission (`RECORD_AUDIO`) | ✅ |
| Notification permission (API 33+, contextual request) | ✅ |
| compileSdk 37 / targetSdk 36 / minSdk 26 | ✅ |
| R8 minify + resource shrink + keep rules (serialization/RevenueCat/OneSignal) | ✅ |
| Release signing config (keystore `signing/abbeysbite-upload.keystore` + wiring exist; release build reads `signing/signing.properties`, not `local.properties`) | ✅ |
| `./gradlew :composeApp:bundlePlayRelease` produces the Play AAB; `./gradlew :composeApp:assembleGalaxyRelease` produces the Galaxy APK | ☐ verify after signing |
| RevenueCat Android key in config | ☐ |
| Play Console: subscriptions matching RevenueCat products + base plans + trial | ☐ |
| Data Safety form (collected: email, photos, user content, purchase history; no sharing/tracking) | ☐ |
| Privacy policy URL in Play listing | ☐ |
| Account deletion URL/declaration (in-app deletion exists) | ☐ |
| Store listing: icon 512 (✅ `store-assets/play-icon-512.png`), feature graphic, screenshots | ☐ |
| Internal testing track rollout first | ☐ |
| Production release | ☐ |

## Pre-submission smoke test (both platforms)

1. Fresh install → create account → complete onboarding (and once with Skip)
2. Photograph a meal → analysis renders → correction flow → re-analysis
3. "Use what I have" chat with pantry constraints; voice mode round-trip
4. Log meal → appears in Journal → rhythm + snapshot update → weekly reflection
5. Browse feed sections → open recipe → play YouTube embed → external fallback
6. Publish a recipe (with cover photo) → edit → unpublish → delete
7. Like + save (double-tap like: no duplicate) → saved list
8. Friend search → request → accept (second account) → opt-in progress sharing
   visible; disable sharing → hidden; block → interactions fenced
9. Paywall: purchase sandbox monthly (trial) → premium unlocks voice/limits;
   restore purchases on reinstall
10. Notifications: enable a category → contextual permission prompt
11. Delete account → sign-in fails → data gone (verify in Supabase dashboard)
12. Airplane mode: every screen shows friendly error/empty states, no crashes
