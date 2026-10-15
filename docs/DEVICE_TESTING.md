# Device acceptance checklist

CI compilation, lint and unit tests do **not** prove delivery on a physical phone. This checklist remains unverified until actual observations are recorded with phone model and Android version.

## Install

Download the debug artifact from a successful Android CI run on GitHub (private repository access required), extract it and install `app-debug.apk`. Allow installation from that specific trusted source when Android asks. This is a development APK, not a store release. Existing installs may require uninstall if a later CI debug key differs; uninstall clears local timer data.

## Comfort and fluid motion (0.1.4 acceptance)

- Select default, silent and a local system notification sound, cancel the picker, reopen/recreate and verify the chosen setting persists. Completion should use that profile; no app sound outside the notification path. System notification/channel overrides and Do Not Disturb remain authoritative. If a chosen tone disappears, verify safe fallback and no crash.
- Select vibration off/short/double; inspect the selected channel and test actual vibration on a physical phone (the emulator cannot prove tactile output). Deny notifications and verify the timer still advances without an alert or crash.
- Enable keep-screen-on: only a visible running focus session holds the screen awake. Pause, reset, switch to a break, background or destroy the Activity and verify it clears. Reopen a running focus and verify it resumes only while visible. Default is off; no wake-lock or lock-screen wake behavior.
- New comfort preferences remain editable during an active timer and do not implicitly save unfinished duration/cycle drafts. Old duration/cycle locks stay intact.
- Coffee surface subtly flows within the unchanged original cup while a session is running and visible. Empty/full edges stay bounded; focus drains and breaks fill as before. Pause/idle/settings/background stop the animation. Android animation scale0 disables fluid motion. Inspect portrait/landscape and confirm no main-screen clutter.
- Hosted capture uses the exact downloadable APK after instrumentation: enable system motion for the demo only, configure a one-minute focus through UI, capture real frames/video, compare normalized surface profiles (not just timer draining), and require paused cup pixels to remain identical. No physical audio/vibration/Doze claim from this capture.

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
