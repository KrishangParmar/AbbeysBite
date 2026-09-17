# Abbey’s Bite — project status

Last updated: 2026-09-17. This is the release handoff source of truth.

## WORKING

- KMP/Compose shared app with Android `play` and `galaxy` flavors; iOS source
  structure preserved.
- Application ID remains `com.abbeysbite.app`.
- Supabase repositories, migrations `0001`–`0006`, RLS, storage, and Edge
  Functions are present in the project; prior live checks are documented in
  `TEST_STATUS.md`.
- Local Gemma path, ModelManager, streaming/cancellation, meal analysis, Chat,
  Live, Community, Journal, friends, legal screens, RevenueCat integration,
  OneSignal adapter, and Community-only ad policy are present.
- Debug AdMob inventory is now forced to Google’s test unit; release AdMob
  serving is disabled unless real app/unit IDs are supplied.
- Community feed no longer displays a filled heart as if the current user
  liked every recipe with a non-zero popularity count.

## TESTED

- `:composeApp:testPlayDebugUnitTest`: 73 tests, 0 failures.
- `:composeApp:testGalaxyDebugUnitTest`: 73 tests, 0 failures.
- Source compile reached Android Kotlin compilation successfully for both debug
  flavors; `assemblePlayDebug` and `bundlePlayRelease` completed successfully.
- No Samsung phone is attached yet. Camera, real-device Gemma latency/RAM,
  STT/TTS/barge-in, push delivery, and physical billing remain unverified.

## EXTERNAL SETUP REMAINING

- Play Console app, Play App Signing, subscriptions/base plans/trial, RevenueCat
  Play credentials/products, internal testing, and sandbox purchase.
- First-party hosting for the 3.4 GB model and `MODEL_DOWNLOAD_URL`.
- OneSignal Firebase/FCM credentials and real push-delivery test.
- AdMob app/native unit IDs and release rebuild; ads remain safely disabled.
- Hosted Privacy Policy, Terms, Community Guidelines, and Support URLs.
- Galaxy Seller Portal and Galaxy billing setup; Apple/App Store setup later.
- Store listing, Data Safety, content rating, target audience, Health Apps,
  Ads declaration, App Access, countries, screenshots, and review.

## BLOCKERS

- P0: none found in the source audit.
- P1: physical Samsung validation is required before productionization; the
  current candidate uses side-loaded Gemma because no hosted model URL exists.
- P2: release dashboards and legal hosting are external setup items above.

## CURRENT RELEASE ARTIFACTS

- Physical APK: `artifacts/AbbeysBite-physical-test.apk` — SHA-256
  `A4FA5692C0D19DDE9B2DACF19135FBC0A909D0FBA6B1D7892DAD30648787487B`.
- Candidate Play AAB: `artifacts/AbbeysBite-PlayStore-candidate.aab` — SHA-256
  `66B981010B4F80B2215C9305C29BB979344492279976B09CD8D2D9E0FE2D8C98`.

## NEXT ACTION

Install `AbbeysBite-physical-test.apk` on the Samsung and complete
`PHYSICAL_DEVICE_TEST_CHECKLIST.md`. Stop here until the user confirms:
“Physical phone testing passed.”
