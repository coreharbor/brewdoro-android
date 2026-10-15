# Comfort settings and fluid coffee

Requested follow-up to0.1.3: selectable completion sound/vibration, optional keep-screen-on during work, and subtle coffee fluid motion. This is not a request for statistics, notification transport controls or stable signing.

## Contract

- Keep the main screen minimal and the fixed Linux palette/cup silhouette/adaptive layouts unchanged. All new choices live behind the gear.
- Persist comfort preferences independently from the timing snapshot. Editing them while running must not alter phase identity/deadlines or save unfinished timer drafts.
- Use system notification tones including silent/default and meaningful vibration choices. Android8+ channel settings are immutable: profiles need stable identities, not ineffective mutations. Do not delete/recreate a user-overridden channel to bypass system choices or DND. Notification permission still applies. No media playback or wake-lock workaround.
- Keep-screen-on is opt-in, scoped to visible running focus; never a background wake lock.
- Coffee movement is a low-amplitude surface wave inside the existing136x86 geometry. It does not change elapsed time, progress direction or cup/coffee colors. Freeze/settle on pause and suspend while not visible; honor disabled system animations.

## Evidence required before publishing

Hosted JVM/instrumentation compile, unit tests, lint, APK signature verification; real Activity lifecycle/window-flag and selected-channel configuration tests; persisted settings and picker cancellation; actual downloadable APK settings screenshot and short animation recording. Quantify frame surface change after removing mean vertical fill change; confirm paused frames match. Retain test/evidence artifacts even if capture fails. Real sound/vibration/Doze on the user phone remains a separate gate.

## Distribution

0.1.4 remains a private debug preview. Compare its certificate to0.1.3 and disclose uninstall/settings-loss if different. Do not claim stable signing or in-place upgrades. Use GitHub release assets, never rename/wrap a blocked APK attachment.
