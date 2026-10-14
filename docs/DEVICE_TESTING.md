# Device acceptance checklist

CI compilation, lint and unit tests do **not** prove delivery on a physical phone. This checklist remains unverified until actual observations are recorded with phone model and Android version.

## Install

Download the debug artifact from a successful Android CI run on GitHub (private repository access required), extract it and install `app-debug.apk`. Allow installation from that specific trusted source when Android asks. This is a development APK, not a store release. Existing installs may require uninstall if a later CI debug key differs; uninstall clears local timer data.

## Tests

- Default focus is 25 min, breaks 5/15; set all to 1 minute for shorter tests.
- Verify cup empties during focus and fills during break; pause freezes progress, resume preserves remaining time.
- Check reset timer versus reset full cycle; manually start next phase, long break after four focus completions.
- Lock screen during focus. With notification permission and exact-alarm access granted, verify completion notification and system sound at expiry.
- Deny notification permission: timer must still complete safely; status must explain blocked notifications. Grant later and test a new session.
- Deny exact-alarm access: verify the inexact fallback warning; notification can be delayed in Doze. Grant access, resume the app and test again. Revoke access while active, return to the app and verify safe rescheduling.
- Pause or reset just before expiry; ensure no stale notification or double completion.
- Rotate, background, swipe away, reopen; state must restore from persistent deadlines. Android force-stop is a different case and prevents alarm delivery until launch.
- Reboot during a timer; verify restored remaining time or one completion, not duplicates.
- Change wall clock within a boot: timer must remain based on elapsed time.
- Large text, small screen, portrait/landscape, TalkBack button labels, Russian/English system language.
- Battery saver and OEM restrictions: document any manufacturer-specific delays, do not claim universal delivery.

## Record observations

Commit/device/build: _not yet tested_. Phone/Android: _not yet tested_. Each unchecked result is unknown, not a pass.
