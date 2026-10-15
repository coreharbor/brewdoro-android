# Device acceptance checklist

CI compilation, lint and unit tests do **not** prove delivery on a physical phone. This checklist remains unverified until actual observations are recorded with phone model and Android version.

## Install

Download the debug artifact from a successful Android CI run on GitHub (private repository access required), extract it and install `app-debug.apk`. Allow installation from that specific trusted source when Android asks. This is a development APK, not a store release. Existing installs may require uninstall if a later CI debug key differs; uninstall clears local timer data.

## Tests

- Default focus is 25 min, breaks 5/15; set all to 1 minute for shorter tests.
- Verify cup empties during focus and fills during break; pause freezes progress, resume preserves remaining time.
- With cycle+autostart enabled (new defaults), first Start initiates focus → short break → focus automatically; fourth focus leads to long break, then focus/count reset. No next-stage Start tap.
- Disable autostart in gear settings: next mode is selected but idle until Start. Disable cycle: completion stays on the same mode. Preferences survive reopen/rotation.
- With all durations1minute and notification/exact-alarm access granted, verify at least two automatic transitions, including while backgrounded/screen locked; ensure one completion notification/sound per stage. Delayed delivery starts one fresh next stage, not several fictional catch-up cycles.
- Pause/reset stops the automatic chain. Verify reset timer versus reset full cycle and no stale completion after reset.
- Lock screen during focus. With notification permission and exact-alarm access granted, verify completion notification and system sound at expiry.
- Deny notification permission: timer must still complete safely; status must explain blocked notifications. Grant later and test a new session.
- Deny exact-alarm access: verify the inexact fallback warning; notification can be delayed in Doze. Grant access, resume the app and test again. Revoke access while active, return to the app and verify safe rescheduling.
- Pause or reset just before expiry; ensure no stale notification or double completion.
- Rotate, background, swipe away, reopen; state must restore from persistent deadlines. Android force-stop is a different case and prevents alarm delivery until launch.
- Reboot during a timer; verify restored remaining time or one completion, not duplicates.
- Change wall clock within a boot: timer must remain based on elapsed time.
- In ordinary landscape (also small 640x360dp at normal font), cup, timer, start/pause, reset and all three duration presets must be fully visible without scrolling. Gear stays accessible; portrait appearance stays unchanged.
- Rotate a running session portrait → landscape → portrait: remaining time continues from its deadline, not a restarted session. Repeat while paused: time stays frozen and Resume remains available.
- Open settings, enter an unsaved duration, hide the keyboard and rotate: settings and the draft survive. With large text/very short windows, scroll to all main/settings controls; no horizontal clipping or inaccessible buttons.
- TalkBack button labels and Russian/English system language in both orientations.
- Battery saver and OEM restrictions: document any manufacturer-specific delays, do not claim universal delivery.

## Record observations

Commit/device/build: _not yet tested_. Phone/Android: _not yet tested_. Each unchecked result is unknown, not a pass.
