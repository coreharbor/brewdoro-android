# Comfort and fluid-coffee preview 0.1.4 verification

- Verified source: `a7d36f540769d0c98cf1775071e70b33e1467fab`. [Full successful CI](https://github.com/coreharbor/brewdoro-android/actions/runs/38748155393).
- 122/122 JVM and19/19 Android16/API36 instrumentation tests: zero failures/errors/skips. Lint: zero errors/fatal,43warnings,1information. Instrumentation compiled before emulator boot. Official apksigner verified v2 with one RSA2048 debug signer.
- APK10,258,090bytes; SHA256 `46fd8bacae447eb73c9b1279309e128bb0a4e771c4c55df91bebdb6e919455d4`; downloaded checksum and ZIP CRC verified. Manifest versionName0.1.4/versionCode5,min26/target35. VIBRATE added; no storage/network/wake-lock permission.
- Certificate SHA256 `0bcd5ad0e6bd8a905bc42f9b0bf854cf417cadfb69a98699560f0c7c8f684132` differs from0.1.3 (`60fa7b7f6730222d6033bd1bb0ee78f468de04a411365f0e8b6b87725163f66e`): uninstall previous preview, losing local preferences/session. Production signing remains separate.
- Independent persisted comfort model/store leaves timer V1/V2 snapshots and unsaved timer/cycle drafts intact. Live comfort changes remain available during active/paused sessions. Rotation persistence, actual selected-profile channels, default/silent/local URI configuration, off/short/double vibration and missing-tone fallback passed. Existing channels/overrides are not mutated/deleted. System/DND overrides remain authoritative.
- Actual Activity Window flag verified: visible running focus ON when opted in; pause/reset/break/background OFF; resume/recreation restores only when applicable. Existing real60s +60s foreground/background cycle test passed, including automatic screen-flag changes; elapsedMs120104. No background wake lock.
- Real Canvas captures with fixed midlevel/manual Compose clock changed356pixels; inactive and system-scale-zero captures static. Actual downloadable APK separately reinstalled after instrumentation: original EN/RU portrait/landscape, compact640x360dp and large-font layout checks passed. Comfort/vibration settings and system picker screenshots visually inspected.
- Cross-app picker cancellation tested outside Compose/Espresso on the exact APK: actual Google system picker appeared, Back cancelled it, comfort/timer persisted state unchanged, unfinished1minute focus draft retained. This is an additional hosted assertion, not counted as an instrumentation test. Earlier mixed-framework attempts exposed external accessibility/focus/IME synchronization failures; test moved to the external real-APK harness without dropping the substantive cancellation/persistence assertions. Duration focus is now cleared before picker launch to prevent keyboard leakage.
- Actual6s MP4 plus8real screenshot frames retained. Coffee surface profile comparison subtracts mean vertical height, so ordinary draining alone cannot satisfy the gate: residual3.92px; surface variation3-4px; paused cup frames pixel-identical. GIF is cropped/resized actual screencaps, not a synthetic rendering. Small wave remains within original136x86 outline and original palette.
- Final run produced no emulator-recovery record. Earlier known hosted-system ANR recovery was restricted to specifically observed SystemUI/PixelLauncher/Process system dialogs and recorded; app ANRs never dismissed. APK/UI tests do not prove physical sound/vibration or OEM/Doze delivery. These remain user-phone checks.

---

# Automatic-cycle preview 0.1.3 verification

- Verified source: `72a255e8c94223f68e6a971a9824bd8332f48aa5`. [Full passing CI](https://github.com/coreharbor/brewdoro-android/actions/runs/38720137951).
- 109/109 JVM and 15/15 Android instrumentation tests: zero failures, errors or skips. Lint: zero errors/fatal issues, 37 non-fatal warnings. App and instrumentation compile before emulator boot.
- Android16/API36 Pixel5-profile x86_64 emulator: actual60s focus automatically starts short break in foreground, then actual60s break automatically starts focus with Activity CREATED and UI refresh stopped. No manual next-stage tap, injected completion, fake receiver broadcast or reconcile in wait predicate. Both completion notifications observed; full60s successor and its committed snapshot verified; pause/reset still work on return. Retained log evidence: elapsedMs=120066. Physical phone/Doze/OEM behavior remains pending.
- Linux window.py _show_finished and cycle.py parity: fourth focus chooses long break, long break resets count, auto-start uses a fresh full duration at observed expiry. Delayed callbacks never fabricate catch-up phases. Android intentionally defaults cycle/autostart ON per user request; Linux ships them OFF. Gear can disable autostart (select next idle) or cycle (finish in place).
- V2 snapshot codec strictly validates booleans; V1 snapshots retain session fields with new defaults. Legacy FINISHED remains inert until explicit Start. Atomic persist → successor schedule → completion alert; duplicate alarms/ticks do not count or notify twice or withdraw the last alert. Phase-bound Pause protects a newly started successor from a stale UI click.
- APK: 10,233,794 bytes; SHA256 `15549c4fc0ec5f90e9cfa4d7bb9ce1cedc7e41b8839348b6c1e958368c49afa0`. ZIP CRC integrity and downloaded CI checksum match. Official apksigner verified v2, one RSA2048 debug signer.
- Certificate SHA256 `60fa7b7f6730222d6033bd1bb0ee78f468de04a411365f0e8b6b87725163f66e` differs from0.1.2 (`e6b03a08d25129fb4f109293a9f543475538966689622e8912b26e77d8eb2518`): uninstall older preview first, losing local session/settings. No stable production key or upgrade channel is claimed.
- Manifest: io.github.coreharbor.brewdoro, versionName0.1.3/versionCode4, min26/target35.
- Exact downloadable APK reinstalled after instrumentation; actual EN/RU portrait/landscape, compact640x360dp, font1.5 and RU settings captured. Inspected cycle/autostart defaults ON, original minimal portrait, compact landscape fit, large-font stacked full-width action labels. Existing rotation/recreation tests retain running/paused state and unsaved settings drafts including both switches.
- Earlier attempts caught an asynchronous test assertion before Activity rendered the committed successor, and UTP uninstalling the target before private evidence retrieval. Fixed the test to await successor semantics; retained evidence through logcat and content-gated it (adb shell exit status alone can falsely report success). Final complete run passed with the evidence assertion enabled.

---

# Landscape preview 0.1.2 verification

- Source: `e802c01965ba94df96bc75f3acdade2095a2873f`. [Full passing CI](https://github.com/coreharbor/brewdoro-android/actions/runs/38712263952).
- 74/74 JVM and 12/12 instrumentation tests passed; zero failures, errors or skips. Android16/API36 Pixel5-profile emulator.
- Lint: 0 errors/fatal issues, 37 non-fatal warnings. APK assembled; official Android build-tools apksigner verifies v2 signature, one RSA2048 debug signer.
- APK bytes: 10,215,242. SHA256: `7f706dcf3feaceb8a22ff7d3ee4ea3afb271b9eed3136703085da8688b6b37f9`. Local APK ZIP integrity and CI checksum match verified.
- Certificate SHA256: `e6b03a08d25129fb4f109293a9f543475538966689622e8912b26e77d8eb2518`. Different from0.1.1 (`1b92f9753814298ed521ed23283d75f2288fac2a3fa2e175a431122e46f31950`); uninstall the older test build first, losing local settings/session. Stable production signing remains an open separate task.
- Manifest: io.github.coreharbor.brewdoro, versionName0.1.2/versionCode3, min26/target35.
- Actual verified downloadable APK was reinstalled after instrumentation, not a separately rebuilt test app. EN/RU portrait1080x2340 and landscape2340x1080 screenshots captured. Additional RU compact landscape1280x720 at density320 (=640x360dp) and font1.5 initial/scrolled; settings in both orientations. Orientation dimensions and initial normal-font control visibility asserted. Screenshots visually inspected; normal and compact landscape fit cup/readout/primary/reset/presets without scrolling. Large-font buttons now use full-width stacked actions, preventing broken Russian Reset labels; scrolling brings the controls fully into view. Portrait appearance retained.
- Deterministic Compose tests cover zero scroll range and unclipped bounds at760x320dp, short760x220 and font2 overflow. Real MainActivity orientation/recreate tests retain running generation/deadlines, exact paused snapshot, settings route and unsaved duration drafts. Domain/persistence/alarms/notifications code unchanged.
- Earlier verification attempts exposed an injected Back-before-composition race (test synchronized to committed settings route), emulator SystemUI/PixelLauncher ANR dialogs (explicit limited Wait for those components only, never application ANRs), and a test-only unsupported DpRect.width API (fixed to edge difference). Final complete run passed; no emulator recovery file was produced in final artifact.
- Screenshot inspection additionally caught broken large-font reset wrapping and a mislabeled portrait capture after locale relaunch; fixed action layout and enforced post-launch rotation/dimension checks before final passing run.
- Physical-phone background/OEM behavior is not implied by emulator success. User confirmed0.1.1 Linux visual fidelity and reported landscape overflow; this preview is ready for their landscape retest.

---

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
