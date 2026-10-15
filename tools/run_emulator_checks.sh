#!/usr/bin/env bash
# Disposable hosted emulator only. Preserve test failures while still collecting visual evidence.
set -u
test "${GITHUB_ACTIONS:-}" = true || exit 2
./gradlew --no-daemon --max-workers=2 :app:connectedDebugAndroidTest
test_status=$?
python3 tools/emulator_smoke.py
capture_status=$?
printf "Instrumentation status: %s; screenshot status: %s\n" "$test_status" "$capture_status"
if [ "$test_status" -ne 0 ]; then exit "$test_status"; fi
exit "$capture_status"
