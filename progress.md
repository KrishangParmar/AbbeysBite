# Abbey's Bite — Mission Progress

Last updated: 2026-09-13 (final engineering pass before laptop migration)

## Final engineering pass (2026-09-13)

Full audit → fix → build → test → verify → package. A 6-dimension read-only
audit surfaced 50 findings; the genuine ones were fixed and verified.

**P0/P1 security & backend (all fixed + live-verified against production):**
- RevenueCat webhook checked the wrong entitlement (`premium` vs
  `abbeysbite_premium`) → paying users never mirrored as premium server-side.
  Now uses `RC_ENTITLEMENT_ID`. Verified: granting event → mirror `is_premium=true`.
- Webhook was deployed under the default JWT gate → every RevenueCat call 401'd
  before the secret check. Pinned `verify_jwt = false` + redeployed
  `--no-verify-jwt`. Verified: bad auth 401, valid secret 200.
- Blocked user could delete the `blocked` friendship row and un-block
  themselves (0004 guarded only INSERT/UPDATE) → added a BEFORE DELETE guard
  (migration 0006). Verified live: blocked party rejected, blocker allowed.
- `send-notification` recipe_liked had no verification + trusted client text +
  no rate limit; target_user_id was interpolated into a PostgREST filter with
  no UUID check → added UUID validation, server-side recipe/like verification
  (DB title used), and a per-sender hourly rate limit (`notification_log`).
  Verified live: injection 400, fake like 403, no-relationship 403.
- ai-gateway only capped analyze/chat and never validated `task` → every task
  now capped (free + premium) and the allow-list enforced.
- delete-account storage cleanup was single-page → now paginates.
- Recipe moderation was reversible by the author; auto-usernames leaked the
  email local-part; a blanket default-privilege grant removed fail-closed
  safety → all fixed in migration 0006. Migration 0006 pushed to production.

**P1 on-device AI (fixed):**
- streamGenerate leaked the native session on cancellation (generation
  continued after screen exit / barge-in, next turn failed) and its awaitClose
  could cancel the *next* request → rewrote teardown to cancel-before-close and
  removed the racy awaitClose.
- generationMutex could deadlock forever if native generation failed →
  guaranteed teardown in `finally`.
- prepareModel had no lock; ImproveViewModel warm-up raced the provider →
  added prepareMutex (no double multi-GB instances).
- renderPrompt dropped the whole system prompt when the history window began
  with an assistant turn → drop leading assistant turns.
- maxOutputTokens was ignored → per-request output cap applied.
- ModelManager: oversize partial (permanent 416 loop), corrupt file kept and
  reloaded on next scan, space gate skipped when any partial existed,
  cancellation surfaced as failure → all fixed. New tests added.

**P1/P2 Chat + Live voice (fixed):**
- Permission-denied dead end (retry never re-requested) → retry re-runs start.
- Mic went cold seconds into every reply (barge-in dead) → `keepMicHot()`
  keeps the recognizer running through Thinking/Speaking; ERROR_RECOGNIZER_BUSY
  /ERROR_CLIENT handled; iOS audio-engine start failure now surfaces.
- Assistant reply committed twice on screen exit → commit only in-flight text.
- AI stream error mid-reply left TTS speaking over an Error phase → failTurn()
  silences the speaker.
- Muting mid-utterance still triggered a turn → muted guards on partial/final.
- Chat dictation lost synchronous start errors, had no stop control, and
  leaked the shared mic into the voice screen → onSubscription start, Stop
  button, DisposableEffect releases the mic on exit.
- Android TTS failed-enqueue left the speaker loop hung → resume on non-SUCCESS.

**Release safety:**
- RevenueCat key selection extracted to a pure, unit-tested function (9 tests):
  release never gets the Test Store key; flavors never cross-activate.
- Added R8 keep rules for MediaPipe/tasks-genai/LiteRT native methods + AdMob.
  Verified the minified signed release APK launches with no stripping crash AND
  starts the native LiteRT-LM session (topk 48, temp 0.35) on-device.
- Account switch now wipes all in-memory per-user caches (UserScopedState) +
  user-scoped weekly-insight keys.

**Docs:** rewrote/added HANDOFF_NEW_LAPTOP, BUILD_STATUS, TEST_STATUS,
MODEL_HANDOFF, REVENUECAT_HANDOFF; refreshed SIGNING/INFRASTRUCTURE handoffs and
corrected stale README/ARCHITECTURE/DATABASE/checklist claims.

**Verification:** 73 unit tests pass; play+galaxy debug/release build & sign;
iOS both arches compile; extensive live checks against production Supabase and
on the emulator (see TEST_STATUS.md).

---

## Earlier state (2026-09-11 session start)

## Audit baseline (2026-09-11)

- Repo state identical to 2026-09-05 session end. 110 Kotlin files. No git repo
  (Apple git blocked by unaccepted Xcode license on this machine).
- `testDebugUnitTest` + `assembleDebug`: **GREEN** (55 tests, 0 failures).
- Genuinely working (verified live on emulator 09-05): auth (demo+Supabase
  impls), onboarding, meal analysis flow (Mock AI), correction, chat, journal
  (rhythm/snapshot/insight), community demo feed/detail/editor, friends demo,
  paywall structure w/ honest unavailable state, account deletion flow, share
  card, YouTube embed.
- Merely existing / gaps at audit:
  - AI production path = cloud `ai-gateway` (Gemini API endpoint) — to be
    REPLACED by on-device Gemma as primary.
  - No local model runtime, no ModelManager.
  - RevenueCat identity never linked to Supabase auth; entitlement id was
    `premium` (actual project: `abbeysbite_premium`).
  - OneSignal adapter existed but `initialize()` never called; no external id
    mapping; no server sender.
  - Analytics = println in debug, no-op in release.
  - Legal URLs = example.com placeholders.
  - Auth signup assumed immediate session (email-confirmation flow broken).
  - Friendship transitions enforced client-side only (RLS allowed arbitrary
    participant updates).
  - No ads, no play/galaxy flavors, Supabase project never linked/deployed.

## Work log

- [x] Audit: build+tests green; grep sweep for TODO/mock/Gemini/example.com
- [x] `.local-secrets.env` + `.env.example` (names) + .gitignore hardening
- [x] Gemma model downloaded + hash recorded (3,655,827,456 B, sha256 2ed7bc3a…)
- [x] Supabase CLI 2.117.0 installed
- [x] Gradle: secrets loader (.local-secrets.env → local.properties → env,
      client-safe keys only baked); play/galaxy flavors; deps: tasks-genai
      0.10.35 + tasks-core, native purchases + galaxy + admob @10.19.1,
      play-services-ads 25.4.0, OneSignal 5.10.0
- [x] Supabase PRODUCTION deployed: 4 migrations pushed (pooler),
      5 Edge Functions live, 3 function secrets set (token can't `link` —
      documented workaround with --project-ref/--db-url)
- [x] Auth: email-confirmation state machine + check-your-email UI + resend +
      abbeysbite://auth-callback deep link (Android); mailer_autoconfirm=true
      set for dev via management API (decision documented)
- [x] AppSessionCoordinator: Supabase UUID → RevenueCat logIn/logOut +
      OneSignal external id + analytics id; wired at startup on both platforms
- [x] LocalAiEngine + AndroidGemmaEngine (MediaPipe LLM Inference; vision
      ≤768px; streaming; cancellation) + IosGemmaEngine seat
- [x] ModelManager: side-load detection, download w/ resume+progress, size
      gate, sha-256 verify, delete/re-download, space checks (8 unit tests)
- [x] LocalGemmaProvider primary (AI_BACKEND=local); gateway compiled only as
      debug+flagged fallback; DebugSwitchingAiProvider (debug-only mock toggle)
- [x] "Preparing your private AI" surface in Improve (download/progress/errors)
- [x] Chat: token streaming UI + mic dictation
- [x] Live: streamed generation → sentence-chunked TTS, hot-mic barge-in,
      mute, shared context with Chat, partial-reply commit on interrupt
- [x] RevenueCat: entitlement `abbeysbite_premium` (config-driven), Test Store
      key in play/iOS debug, native GalaxyConfiguration (TEST/PROD modes),
      store-aware key selection, awaitLogIn/awaitLogOut identity
- [x] Friendship server-side enforcement (migration 0004 triggers + symmetric
      unique pair) — client tests already cover the mirror logic
- [x] OneSignal: init at startup, external id via coordinator, category tags,
      send-notification Edge Function (relationship-validated), deep-link
      routing (manifest hosts + DeepLinkBus + tab requests)
- [x] Ads: AdsManager policy (premium=zero ads; Community-only placement;
      release disabled until real AdMob ids), native ad card via RevenueCat
      forNativeAdWithTracking (loaded/impression/opened/revenue tracked)
- [x] Analytics: SupabaseAnalytics → analytics_events (insert-only RLS)
- [x] Legal: example.com eliminated; canonical in-app pages (privacy/terms/
      guidelines) + hosted HTML exports in legal/ + configurable URLs
- [x] Keystore generated (signing/, gitignored) + release signing wired
- [x] PERFORMANCE.md (honest emulator findings; real-device table pending)
- [x] EMULATOR VERIFIED: real Supabase signup (trigger-created profile),
      model side-load detection, native LiteRT-LM session running our exact
      config (temp 0.35/topK 48), 3.0 GB RSS, sustained generation —
      emulator too slow for interactive latency (documented); analysis flow
      UI states all correct
- [x] Release artifacts: SIGNED Play AAB (78 MB) + Galaxy release APK (158 MB) + galaxy debug
- [x] Emulator LIVE: Test Store offerings (Annual $79.98/Monthly $9.99 from `default`), purchase completed, receipt POSTed under Supabase UUID, "Premium active", account-switch isolation verified (tester2 = free)
- [x] Docs updated (README/ARCHITECTURE AI sections, handoffs, PERFORMANCE); evidence screenshots in store-assets/screenshots

## Environment constraints (this machine)

- Xcode license unaccepted → `git`, `python3`, `xcrun` blocked → iOS framework
  LINK blocked (Kotlin iOS COMPILE works). One-time fix: `sudo xcodebuild -license accept`.
- Network ~1 MB/s. Model download (~3 GB) runs in background.
- Physical devices: none attached at session start (emulator available).
