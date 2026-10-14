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
