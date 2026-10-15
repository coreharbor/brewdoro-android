# Linux-style preview 0.1.1 verification

- Verified source: `5252dd46ec9cb4ebf95375beb7e289e77d61a7a2`. [Successful complete CI](https://github.com/coreharbor/brewdoro-android/actions/runs/38692441387).
- 74 JVM tests and 6 Android instrumentation/UI tests: zero failures/errors/skips.
- `lintDebug`: 0 errors, 36 non-fatal warnings (including unused legacy strings/newer dependency suggestions). `assembleDebug`: successful.
- Official Android build-tools `apksigner verify --verbose --print-certs`: APK v2 signature verified, one RSA-2048 Android Debug signer. Certificate SHA256: `1b92f9753814298ed521ed23283d75f2288fac2a3fa2e175a431122e46f31950`.
- `aapt dump badging`: package io.github.coreharbor.brewdoro, versionCode2/versionName0.1.1, min26/target35.
- APK size 10,215,238 bytes, SHA256 `a4fe41a53165ba8a94753375cf9b633b6accc9a3ec575be65e85c3e2f8811ea4`. Download ZIP integrity and checksum verified.
- Android16/API36 Pixel5-profile x86_64 emulator: all six UI tests passed. After instrumentation, the exact downloadable APK was installed separately and screenshots captured for actual EN/RU MainActivity and RU settings. Screenshots were visually inspected against the Linux reference; no mock renders used.
- Pixel inspection confirms the exact background #24283b, header #1f2335, text #c0caf5 and coffee #e0af68 appear in the actual screenshot. Main has only mode/session/time/cup/start/reset/horizontal presets; settings and Android permission information are behind the gear.
- Normal handset layout, compact/large-font scroll, settings/main separation, running-session controls and duration validation exercised in instrumentation tests. Physical-phone/OEM background notification behavior is still not claimed.

**Upgrade warning:** initial 0.1.0 certificate SHA256 is `7fbbfc1972d93c39e8598d4eb74e52fadbdd25462f82e0b1849809977f87f3f2`; preview0.1.1 differs. Android will not accept an in-place update over that first test install. Uninstall the first development preview before installing this one; its local preferences/session are lost. This limitation is explicitly tracked until stable signing is configured.

CI infrastructure: first emulator attempts failed before app execution because the Android16 image requires ~7.2GB initial userdata space (even with a smaller configured partition). Reclaimed unused .NET SDK only on the disposable GitHub-hosted runner; no server files/toolchains were removed. Final full run passed.

---

# Initial Android preview verification

## Verified build

- Source commit: `4aa5b4bf9d0a930f90f8ca7ba74d2e2d058f4fb1`.
- [Successful Android CI](https://github.com/coreharbor/brewdoro-android/actions/runs/38641869895).
- JDK 17, AGP 8.9.2, Gradle 8.11.1, Kotlin 2.1.20, SDK 35.
- `testDebugUnitTest`: **70 tests, zero failures/errors/skips** (downloaded JUnit XML).
- `lintDebug`: **zero errors**, 12 non-fatal warnings.
- `assembleDebug`: successful; development APK downloaded from the same CI run.
- Local download checks: ZIP CRC integrity, manifest/classes/license present, APK signing block present. This is not an independent cryptographic signing-certificate verification.
- APK size: 10,209,833 bytes. SHA-256: `54bb40d01262c1c2a6f5e41be415f72b6de26ab53ef7aec88f52b5c320920c49`.

## Review and fixes

Independent read-only source review found no timer-integrity blocker. Parent fixed the break cup direction (pure coffeeFillFraction plus seven regression tests), reduced repeated system/notification IPC, and fixed the API26 theme lint error.

## Known warnings and limits

Lint warnings concern target SDK 35 rather than the newest platform, newer dependency versions, percent-plural suggestion, backup configuration recommendation, one unused resource and optional KTX style improvements. Pinned older compatible dependencies are intentional for this first build, not a claim of latest versions or store readiness. CI also warns about the pinned actions Node20 metadata being forced to Node24; the run succeeds.

Android16 runtime, background delivery, sound, display/insets, reboot, battery saver and OEM restrictions are **not verified on a real phone**. Exact access revocation can cancel alarms until app reconciliation on return; inexact fallback may be delayed; force-stop prevents alarm delivery. Commit-before-notify can lose a notification across a precisely timed crash but prevents duplicate cycle counts.

No production signing key or Play listing exists. The APK is debug-signed for owner testing; independent CI debug keys are not a stable release-upgrade channel. See DEVICE_TESTING.md and SIGNING.md.
