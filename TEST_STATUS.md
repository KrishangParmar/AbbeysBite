# Test Status

Last run: 2026-09-13.

## Automated unit tests — 73 pass, 0 failures

`./gradlew :composeApp:testPlayDebugUnitTest` (also green on the Galaxy
flavor). 8 test classes:

| Area | What it locks in |
| --- | --- |
| AI response parsing | lenient JSON extraction, status mapping, list clamping |
| Meal-analysis mapping | present / could-add / uncertain (never shame) |
| ModelManager (9) | install / side-load / wrong-size / corrupt / space gate / delete / hash verify / **corrupt file is deleted so it isn't reloaded** |
| RevenueCat key selection (9) | release never gets the Test Store key; flavors never cross-activate; galaxy keeps galx_; debug play/appstore prefer test_ |
| Friendship rules | request/accept/decline/block/unblock state machine (mirrors the DB triggers) |
| Privacy authorization | sharing requires accepted friendship AND opt-in |
| Journal aggregation | rhythm, snapshot, streak, plant variety, weekly stats |
| Free-tier / entitlement gating | free caps vs premium unlimited |
| Recipe / YouTube validation | input validation, URL parsing |

## Live verification against PRODUCTION Supabase (this pass, curl + real JWTs)

Using test accounts tester1 / tester2 (Supabase auth), all confirmed live:

| Check | Result |
| --- | --- |
| meal_entries insert + read (owner) | ✅ 201 / row returned |
| meal_entries cross-user read (RLS) | ✅ `[]` — user B cannot see user A's entry |
| meal_entries cross-user delete (RLS) | ✅ no-op — user B cannot delete user A's row |
| RevenueCat webhook, no/invalid auth | ✅ 401 |
| RevenueCat webhook, valid secret + `abbeysbite_premium` | ✅ 200, mirror row `is_premium=true` |
| Blocked user deleting the block row (0006 guard) | ✅ rejected: "Only the user who blocked can remove the block." |
| Blocker deleting the block row | ✅ 204 |
| send-notification: filter-injection target | ✅ 400 `invalid_target` |
| send-notification: recipe_liked without/with fake recipe | ✅ 400 / 403 |
| send-notification: friend_request with no relationship | ✅ 403 |
| send-notification: weekly_reflection for another user | ✅ 403 |

## Live verification on Android emulator (this pass)

| Check | Result |
| --- | --- |
| Fresh debug install → sign in → Improve | ✅ |
| Account switch (tester1 ↔ tester2) | ✅ each sees only their own profile; no contamination |
| On-device model detected (side-loaded, correct size) | ✅ capture UI enabled |
| Native LiteRT-LM session (debug) | ✅ started with `topk 48, temperature 0.35` |
| **Minified release APK** launches, no R8 stripping crash | ✅ |
| **Native LiteRT-LM session in the minified release build** | ✅ started with `topk 48, temperature 0.35` |

## Not tested here (be honest about it)

- **Real-device AI latency** — no physical Android phone was attached; the
  emulator is not representative of on-device inference speed (see
  `PERFORMANCE.md`). No latency numbers are claimed.
- **Live voice (STT/TTS/barge-in) end-to-end** — the emulator has no reliable
  speech recognizer; the lifecycle fixes were verified by code + compile, not
  by spoken input. Needs a physical device.
- **Galaxy real purchase** — needs a physical Samsung device signed into a
  Galaxy Store test account (`REVENUECAT_HANDOFF.md`).
- **OneSignal delivery** — code path complete; actual push delivery is blocked
  on FCM credentials in the OneSignal dashboard (`INFRASTRUCTURE_HANDOFF.md`).
- **iOS runtime** — compiles; link/run blocked by the Xcode license on the old
  machine. iOS on-device AI bridge is a documented seat for the Apple phase.
