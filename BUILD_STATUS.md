# Build Status

Snapshot from the final engineering pass. Every result below was produced on
this machine on the date shown — nothing here is aspirational.

Last run: 2026-09-13. Toolchain: JDK 17, Gradle 9.7.1 (wrapper), AGP 9.4.0,
Kotlin 2.4.10, compileSdk 37 / targetSdk 36 / minSdk 26.

## Results

| Target | Task | Result |
| --- | --- | --- |
| Unit tests (Play) | `:composeApp:testPlayDebugUnitTest` | ✅ 73 tests, 0 failures |
| Unit tests (Galaxy) | `:composeApp:testGalaxyDebugUnitTest` | ✅ pass |
| Android debug APK | `:composeApp:assemblePlayDebug` | ✅ built + installed on emulator |
| Android release AAB | `:composeApp:bundlePlayRelease` | ✅ signed, 88 MB |
| Android release APK (Play) | `:composeApp:assemblePlayRelease` | ✅ signed, 158 MB (universal) |
| Android release APK (Galaxy) | `:composeApp:assembleGalaxyRelease` | ✅ signed, 158 MB |
| iOS (simulator arm64) | `:composeApp:compileKotlinIosSimulatorArm64` | ✅ compiles |
| iOS (device arm64) | `:composeApp:compileKotlinIosArm64` | ✅ compiles |
| iOS link / .app | Xcode `embedAndSignAppleFrameworkForXcode` | ⛔ blocked only by unaccepted Xcode license on the old machine |

## Artifact locations

```
composeApp/build/outputs/bundle/playRelease/composeApp-play-release.aab      (88 MB, signed)
composeApp/build/outputs/apk/play/release/composeApp-play-release.apk        (158 MB, signed)
composeApp/build/outputs/apk/galaxy/release/composeApp-galaxy-release.apk    (158 MB, signed)
composeApp/build/outputs/apk/play/debug/composeApp-play-debug.apk            (debug)
composeApp/build/outputs/mapping/playRelease/mapping.txt                     (R8 mapping — upload to Play Console)
composeApp/build/outputs/mapping/galaxyRelease/mapping.txt                   (R8 mapping)
```

## Why the APK is ~158 MB but the AAB is ~88 MB

The universal APK contains every native ABI (the MediaPipe / LiteRT-LM runtime
ships native `.so` libraries for the on-device model). The App Bundle splits
per-ABI at install time, so what a user downloads from Play is far smaller.
The 3.4 GB Gemma model is **not** bundled — it is provisioned separately
(see `MODEL_HANDOFF.md`).

## R8 / minification

- Release build: `isMinifyEnabled = true`, `isShrinkResources = true`,
  `proguard-android-optimize.txt` + `composeApp/proguard-rules.pro`.
- Keep rules cover kotlinx-serialization, RevenueCat, OneSignal, Ktor/OkHttp,
  and (added this pass) **MediaPipe / tasks-genai / LiteRT native methods**
  and Google Mobile Ads — a stripped reflective/JNI class would only crash in
  release, so this was verified directly (see below), not assumed.
- **Verified:** the minified, signed release APK was installed on the emulator,
  launched with no `ClassNotFound` / `NoClassDefFound` / `UnsatisfiedLink`,
  and drove an on-device analysis to the point where the native LiteRT-LM
  session started with our exact config (`topk 48, temperature 0.35`).

## Installing on a physical Android phone

The release (or debug) APK is a normal universal APK and installs directly:

```bash
adb install -r composeApp/build/outputs/apk/play/release/composeApp-play-release.apk
```

The app then shows "Your private AI" until the Gemma model is provisioned onto
the device (`MODEL_HANDOFF.md`). AI features unlock once the model is present.
