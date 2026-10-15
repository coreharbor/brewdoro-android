#!/usr/bin/env bash
# Disposable hosted emulator only. Preserve test failures while still collecting visual evidence.
set -u
test "${GITHUB_ACTIONS:-}" = true || exit 2
PACKAGE=io.github.coreharbor.brewdoro
adb -s emulator-5554 install -r --no-streaming app/build/outputs/apk/debug/app-debug.apk || exit 2
adb -s emulator-5554 shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS || exit 2
adb -s emulator-5554 shell appops set "$PACKAGE" SCHEDULE_EXACT_ALARM allow || exit 2
./gradlew --no-daemon --max-workers=2 :app:connectedDebugAndroidTest
test_status=$?
mkdir -p screenshots
adb -s emulator-5554 logcat -d -v raw -s BrewdoroAutoCycle:I "*:S" > screenshots/auto-cycle-evidence.txt
# UTP removes the target after the suite; run-as would only yield unknown-package text.
# adb may still exit zero for shell errors, so validate actual evidence content.
grep -q "^PASS: real60s focus.*receiver successor persisted.*elapsedMs=" screenshots/auto-cycle-evidence.txt
evidence_status=$?
python3 tools/emulator_smoke.py
capture_status=$?
printf "Instrumentation status: %s; screenshot status: %s\n" "$test_status" "$capture_status"
if [ "$test_status" -ne 0 ]; then exit "$test_status"; fi
if [ "$evidence_status" -ne 0 ]; then exit "$evidence_status"; fi
exit "$capture_status"
