# Model Handoff — on-device Gemma

Abbey's Bite runs its AI **entirely on the device**. Meal photos and
conversations never leave the phone. This doc is everything needed to provision
the model on a new machine / device.

## The model

| Field | Value |
| --- | --- |
| Model | Gemma 3n E2B Instruct, int4, LiteRT-LM bundle |
| File name | `gemma-3n-E2B-it-int4.litertlm` |
| Size | 3,655,827,456 bytes (~3.4 GB) |
| SHA-256 | `2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6` |
| Source | Hugging Face `google/gemma-3n-E2B-it-litert-lm` |
| Runtime | MediaPipe LLM Inference (`com.google.mediapipe:tasks-genai`) |

Size and hash are pinned in code (`GemmaModelSpec` in
`composeApp/.../ai/local/ModelManager.kt`). The app refuses to load a file
whose size doesn't match, and `verifyIntegrity()` checks the full SHA-256 and
**deletes** a file that fails (so a corrupt model can't be silently reloaded).

## Where the file is on the old machine

```
~/dev/models/gemma-3n-E2B-it-int4.litertlm   (3.4 GB)
```

Because of its size it is delivered as a **separate zip**
(`ABBEYS_BITE_GEMMA_MODEL.zip`), not inside the main handoff package. Verify
after copying:

```bash
shasum -a 256 gemma-3n-E2B-it-int4.litertlm
# expect: 2ed7bc3a0026c93d5b8a4544b352d9d00cd66ff0bac3ef6a20ac3d2cba4010d6
```

## Re-downloading it from scratch (if you don't have the file)

The Hugging Face token in `.local-secrets.env` (`HUGGINGFACE_TOKEN`) is a
**development-time download credential only** — it is never shipped in the app.
Accept the Gemma license on the HF model page with the account that owns the
token, then download `gemma-3n-E2B-it-int4.litertlm` from
`google/gemma-3n-E2B-it-litert-lm`. Confirm the SHA-256 above before use.

## Provisioning onto a device or emulator (developer side-load)

The app auto-detects a correctly-sized file in its external files dir. Push it
into the **app-owned** directory (install and launch the app once first so the
system creates the dir with the right ownership — pushing into a
manually-created dir can leave it unreadable to the app):

```bash
# 1. install + launch once so Android creates the app data dir
adb install -r composeApp/build/outputs/apk/play/debug/composeApp-play-debug.apk
adb shell am start -n com.abbeysbite.app/.MainActivity
# 2. push the model
adb push gemma-3n-E2B-it-int4.litertlm \
  /sdcard/Android/data/com.abbeysbite.app/files/models/
```

Relaunch (or tap "Check again" on the Improve tab). The setup card disappears
and AI features unlock. iOS side-load path: Application Support / `models`
(the iOS inference bridge itself lands with the Apple phase — see
`ARCHITECTURE.md`).

## Production delivery (hosted download)

For end users the model is downloaded in-app with resume + progress + integrity
("Preparing your private AI…"). Set `MODEL_DOWNLOAD_URL` to a first-party
hosted copy of the exact file and rebuild; `ModelManager` handles resume,
the size gate, SHA-256 verification, oversize-partial cleanup, and a disk-space
check that accounts for a partial already on disk. Until that URL is set, only
the developer side-load path is available (the app says so honestly).

## Runtime facts (see PERFORMANCE.md)

- One process-wide `LlmInference`; a fresh session per request.
- Vision input downscaled to ≤768 px before the encoder.
- Per-request output cap is honored (≈ maxOutputTokens × 4 chars).
- Cancellation stops the native generation **before** the session is closed
  (prevents the leaked-generation bug where the next turn fails).
- Concurrent model loads are serialized (no double multi-GB instances).
- No real-device latency numbers are claimed — emulator inference is not
  representative. Fill in `PERFORMANCE.md` from real hardware.
