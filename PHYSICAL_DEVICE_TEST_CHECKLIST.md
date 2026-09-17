# Abbey’s Bite — Samsung physical-device test

This checklist is for the candidate APK only. It is not a Play Store release
claim. Physical-device behavior that has not been run on a Samsung is marked
below for confirmation.

## Before installing

- Samsung phone with Android 8.0+ and preferably 8 GB RAM.
- At least **4.2 GB free space** for model provisioning; the model file itself
  is **3,655,827,456 bytes (about 3.4 GB)**.
- Wi-Fi for the first model transfer. The candidate build has no hosted model
  URL, so model setup is developer side-load rather than an in-app download.
- The model archive: `ABBEYS_BITE_GEMMA_MODEL.zip`.

Verify the model after extracting it on Windows:

```powershell
Expand-Archive .\ABBEYS_BITE_GEMMA_MODEL.zip -DestinationPath .\gemma-model
(Get-FileHash .\gemma-model\gemma-3n-E2B-it-int4.litertlm -Algorithm SHA256).Hash
```

Expected SHA-256:

`2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6`

## Install with ADB (recommended)

1. Enable Developer options and USB debugging on the Samsung.
2. Connect the phone and accept the debugging prompt.
3. Confirm it appears in `adb devices`.
4. Install `AbbeysBite-physical-test.apk`:

   ```powershell
   adb install -r .\artifacts\AbbeysBite-physical-test.apk
   adb shell am start -n com.abbeysbite.app/.MainActivity
   ```

5. After the first launch, push the verified model:

   ```powershell
   adb shell mkdir -p /sdcard/Android/data/com.abbeysbite.app/files/models
   adb push .\gemma-model\gemma-3n-E2B-it-int4.litertlm /sdcard/Android/data/com.abbeysbite.app/files/models/
   ```

6. Return to Abbey’s Bite and tap **Check again** on the private-AI card, or
   relaunch the app. The first engine load is expected to take time; keep the
   phone connected to power and do not switch apps during the first analysis.

If the Android version blocks writing to `Android/data`, use the Samsung Files
app to copy the verified `.litertlm` file into:

`Internal storage/Android/data/com.abbeysbite.app/files/models/`

## Test pass

- [ ] Fresh launch and app name/icon are correct.
- [ ] Sign up with a new test account; complete onboarding.
- [ ] Log out, log back in, and confirm the session persists after relaunch.
- [ ] Review profile, pantry, food preferences, allergies/avoid list, and
      notification settings.
- [ ] Take a meal photo; verify camera permission, preview, loading state, and
      analysis result.
- [ ] Choose a photo from the gallery; analyze a real meal.
- [ ] Use “Something wrong?” to correct the meal and re-analyze.
- [ ] Confirm suggestions are practical additions and the estimate disclaimer
      is visible.
- [ ] Log the meal and confirm it appears in Journal.
- [ ] Open contextual Chat, send a follow-up, and test voice dictation.
- [ ] Open Live, test microphone permission, speech, TTS, mute, interruption,
      and ending the conversation.
- [ ] Browse Community sections, search, open a recipe, play/open YouTube,
      like, save, report, and publish a recipe with an image.
- [ ] Check Journal rhythm, nourishment snapshot, weekly reflection, and
      deletion of a journal entry.
- [ ] Check Friends request/accept/reject/privacy behavior with a second test
      account.
- [ ] Open Premium/paywall. In this candidate, purchases may be unavailable
      unless the supplied debug Test Store configuration is reachable; do not
      treat that as a production billing test.
- [ ] Test airplane mode on Improve, Community, Journal, and You; verify
      friendly errors and no crashes.
- [ ] Force-stop and relaunch after model setup; verify the model is detected.
- [ ] Rotate the phone and background/restore the app during Chat and Live.
- [ ] Observe heat, battery use, memory pressure, and first-token latency.

## Expected model performance

- Model storage: **3.66 GB**, plus headroom; the app’s download gate is 4.2 GB.
- Estimated memory: plan around **3.0–3.1 GB RSS** based on the prior emulator
  observation; actual Galaxy RAM use is not yet verified.
- Cold load: prior emulator observation was **about 2–3 minutes**; measure the
  Galaxy and record the real value in `PERFORMANCE.md`.
- Real-device analysis latency, thermals, voice behavior, and RAM safety are
  physical-device-only checks and must not be inferred from the emulator.

## If anything fails

Record the exact screen, account type (demo/Supabase), phone model, Android
version, network state, and whether the model was detected. Collect:

```powershell
adb logcat -c
adb logcat -v time > abbeys-bite-logcat.txt
```

Reproduce once, stop the command with `Ctrl+C`, and attach the log plus the
steps that caused the failure. Never include tokens, passwords, or private
account data in the log bundle.
