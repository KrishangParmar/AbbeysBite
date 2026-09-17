# SIGNING_HANDOFF

**Copy the `signing/` directory off this machine before it is retired, and
include it when moving to a new laptop.** It is gitignored and exists ONLY at:

```
<repo>/signing/abbeysbite-upload.keystore
<repo>/signing/signing.properties      (store/key passwords, plain text)
```

In the new-laptop handoff package these two files are in `private-handoff/`;
drop them back into `signing/` at the repo root.

`signing.properties` structure (values redacted):

```properties
storeFile=signing/abbeysbite-upload.keystore
storePassword=<REDACTED>
keyAlias=abbeysbite-upload
keyPassword=<REDACTED>
```

- Type: RSA 2048, validity 30 years, single key
- Alias: `abbeysbite-upload`
- Store password == key password (random hex, in `signing.properties`)
- Certificate SHA-256:
  `C7:DA:6A:2D:6F:C4:89:ED:5E:82:48:60:A2:3D:27:E6:E5:68:BF:55:C7:D2:4A:80:3D:0A:34:17:5B:A6:90:62`
- Certificate SHA-1:
  `B5:45:65:72:57:E3:4A:65:45:46:E0:22:F2:E1:1F:5B:65:23:02:F9`
- Valid: 2026-09-11 → 2056-09-03. Created on the development laptop, JDK 17 keytool.
- Verified 2026-09-13: the signed release AAB/APK carry this exact SHA-256.

Usage: `composeApp/build.gradle.kts` auto-loads `signing/signing.properties`
when present and signs ALL release variants (Play + Galaxy). Without the file,
release builds are unsigned (CI-safe).

Intent: use this as the **Play App Signing upload key** (Google manages the
app signing key) and as the Galaxy Store signing key. If Play enrollment
generates a different app signing key, that's expected — this remains the
upload key.

If this keystore is ever lost BEFORE first Play upload: generate a new one and
update nothing else. After first upload: use Play's upload-key reset flow.
