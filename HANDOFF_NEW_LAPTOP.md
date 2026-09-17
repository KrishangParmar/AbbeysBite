# Abbey's Bite — New-Laptop Handoff

This is the single starting point when you set the project up on a different
machine. It links to the specialized handoff docs and lists exactly what must
be transferred manually (secrets and the keystore never live in Git).

Last verified: 2026-09-13 (final engineering pass).

---

## 0. TL;DR — order of operations on the new laptop

1. Unzip the handoff package (or `git clone` once a repo exists — see §6).
2. Install the toolchain (§1).
3. Recreate the two secret files from the private-handoff area (§2).
4. Drop the keystore back into `signing/` (§2, `SIGNING_HANDOFF.md`).
5. Provision the Gemma model onto a device/emulator (`MODEL_HANDOFF.md`).
6. Build: `./gradlew :composeApp:assemblePlayDebug` (§4 / `BUILD_STATUS.md`).
7. Finish the external dashboard tasks that are still open
   (`INFRASTRUCTURE_HANDOFF.md`).

---

## 1. Toolchain

| Piece | Version / note |
| --- | --- |
| JDK | 17+ (this machine used a Temurin 17 under `~/dev/jdk-17…`) |
| Android SDK | compileSdk **37** platform + build-tools; `sdkmanager` is fine |
| Gradle | none to install — the wrapper pulls 9.7.1 |
| AGP | 9.4.0 (compat flags already set in `gradle.properties`) |
| Kotlin | 2.4.10 |
| Xcode | 16+ **with the license accepted**: `sudo xcodebuild -license accept` |
| Supabase CLI | 2.117.0 (only needed to redeploy migrations/functions) |

`sdk.dir` goes in `local.properties` (see below). Nothing else is
machine-specific in the source tree.

---

## 2. Files that are NOT in Git and MUST be transferred manually

These live in the package's `private-handoff/` folder. Copy them into place:

| File | Put it at | Contains |
| --- | --- | --- |
| `local.properties` | repo root | `sdk.dir` + the public client keys |
| `.local-secrets.env` | repo root | all credentials (Supabase, RevenueCat, OneSignal, HF token, webhook secret) |
| `signing.properties` | `signing/` | keystore path + passwords |
| `abbeysbite-upload.keystore` | `signing/` | **the upload key — exists nowhere else** |

- `.env.example` documents every key name; fill real values from the
  transferred `.local-secrets.env`.
- **The keystore is irreplaceable.** If it is lost you cannot ship an update
  to an existing Play listing. See `SIGNING_HANDOFF.md`. Keep a second copy
  off the laptop (encrypted).
- The Hugging Face token in `.local-secrets.env` is a **development-time
  model-download credential only** — it is never baked into the app
  (`build.gradle.kts` bakes only the client-safe key list into `AppSecrets`).

---

## 3. Secrets model (what is baked where)

- **Client-safe** values (Supabase URL + anon key, RevenueCat public SDK keys,
  OneSignal app id, entitlement/offering ids, legal URLs, model filename) are
  read at build time and baked into a generated `AppSecrets.kt`. These are safe
  to ship — anon/public keys are designed to be in the client.
- **Server-only** secrets (HF token, OneSignal REST key, RevenueCat webhook
  secret, service-role key, DB password) are **never** baked into the app.
  They live in `.local-secrets.env` (build host) and as Supabase function
  secrets (server). Confirmed by the `clientSafeKeys` allow-list in
  `composeApp/build.gradle.kts`.

---

## 4. Build commands (flavor-aware)

```bash
# Debug APK (Play flavor) + install
./gradlew :composeApp:assemblePlayDebug
adb install -r composeApp/build/outputs/apk/play/debug/composeApp-play-debug.apk

# Unit tests
./gradlew :composeApp:testPlayDebugUnitTest :composeApp:testGalaxyDebugUnitTest

# Signed release for Google Play (App Bundle)
./gradlew :composeApp:bundlePlayRelease
#   -> composeApp/build/outputs/bundle/playRelease/composeApp-play-release.aab

# Signed release APK for Samsung Galaxy Store
./gradlew :composeApp:assembleGalaxyRelease
#   -> composeApp/build/outputs/apk/galaxy/release/composeApp-galaxy-release.apk

# iOS (after accepting the Xcode license)
./gradlew :composeApp:compileKotlinIosSimulatorArm64   # compiles today
#   open iosApp/iosApp.xcodeproj in Xcode to link/run
```

Full matrix and last-known results: `BUILD_STATUS.md`, `TEST_STATUS.md`.

---

## 5. The other handoff docs

| Doc | Covers |
| --- | --- |
| `SIGNING_HANDOFF.md` | keystore, alias, fingerprints, signing config, where to store it |
| `MODEL_HANDOFF.md` | Gemma model file, SHA-256, side-load + hosted-download provisioning |
| `REVENUECAT_HANDOFF.md` | entitlement/offering, identity binding, Play + Galaxy, webhook |
| `INFRASTRUCTURE_HANDOFF.md` | every external dashboard action still required |
| `RELEASE_HANDOFF.md` | artifact locations, DB push command, release steps |
| `BUILD_STATUS.md` / `TEST_STATUS.md` | exact build & test results from this pass |
| `PERFORMANCE.md` | honest on-device findings (no fabricated latency numbers) |

---

## 6. Git safety

This working tree is **not a git repo** on the old machine (the Apple git
shim was blocked by an unaccepted Xcode license). On the new laptop, after
`sudo xcodebuild -license accept`:

```bash
cd <repo root>
git init && git add -A && git commit -m "Abbey's Bite — import from handoff"
```

`.gitignore` already excludes `.local-secrets.env`, `local.properties`,
`signing/`, and build outputs — verify with `git status` that none of those
are staged before your first push. Never commit the keystore or secrets.

---

## 7. Is it safe to continue from another laptop?

Yes. All source, tests, migrations, functions, and docs are in the package.
The production Supabase backend is already deployed and unchanged by the move.
The only things that must arrive out-of-band are the four files in §2 — and
they are in `private-handoff/`. After that, `assemblePlayDebug` builds and the
73-test suite runs with no additional setup.
