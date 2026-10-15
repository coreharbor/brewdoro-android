# Landscape regression metadata — 0.1.2 (versionCode 3)

Authored instrumentation tests: **12** (previous 6 retained, 6 added).
This is source metadata, not an execution report. Compile, lint, instrumentation and
actual-APK screenshots are pending hosted CI; no local Android SDK/JDK was installed.

## Deterministic Compose coverage (BrewdoroUiTest: 8 tests)

The five original tests still exercise portrait Linux control order/cup geometry,
start/pause/reset/presets, running settings locks/permissions/reset confirmation,
duration validation/save, and 280 × 360dp at font scale 2 scrolling/settings reachability.

Three additional tests:

| Test | Inset-free app viewport | Font scale | Required assertions |
| --- | --- | --- | --- |
| landscapeShowsWholeCupReadoutAndAllControlsWithoutScrolling | 760 × 320dp, including 48dp header | 1 | Zero main scroll range; **unclipped** full bounds of cup, mode/session/time, primary/reset/all presets inside main; gear inside app; cup 136 × 86dp left of readout; 48dp control targets. Exercises focus idle/running/paused/finished and short/long break, then live start/pause/reset/preset callbacks. |
| veryShortLandscapeFallsBackToScrollWithSettingsStillReachable | 760 × 220dp | 1 | Nonzero scroll range; each item can be fully brought into main viewport; 48dp controls; preset/start/reset callbacks; fixed gear and settings Done remain reachable. |
| landscapeLargeFontFallsBackToScrollWithoutShrinkingTouchTargets | 760 × 300dp | 2 | Same fallback/control/settings assertions at large font scale. |

Explicit logical viewports are fitted inside the real test root via LocalDensity,
not oversized requiredSize clipping. System insets are consumed by the fixture to
make dimensions deterministic. This does not replace native inset/locale screenshot
checks on the actual downloadable APK.

## Actual MainActivity coverage

- MainActivitySmokeTest (1 original test): real repository callbacks and main/settings
  separation during a session, pause/reset and unlocked controls afterward.
- MainActivityRotationTest (3 new tests): real ActivityScenario orientation changes
  portrait → landscape → scenario.recreate() → portrait. Each replacement is checked
  with assertNotSame, so manifest configChanges cannot silently bypass recreation.
  - Running: status, generation, elapsed/wall deadline anchors, boot/mode/duration,
    cycle count and settings retained; pause still works.
  - Paused: exact snapshot/remainder retained; resume still works.
  - Settings: open route and three unsaved duration drafts retained; drafts do not
    implicitly save, explicit Save works, Back returns to main and that route survives
    another recreate.

No sleeps, displayed-second equality or assumptions about elapsed rotation time.
Fixtures cancel active timers, restore pre-test durations and return to portrait.
Domain/platform production sources and all existing JVM tests are unchanged.
