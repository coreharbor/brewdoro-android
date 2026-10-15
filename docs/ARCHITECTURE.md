# Android architecture

## Decisions

- Native Kotlin + Jetpack Compose; single Android app module, small pure-Kotlin domain layer. No backend, DI framework, database, Python runtime or shared cross-platform build.
- Application ID `io.github.coreharbor.brewdoro`, min SDK 26. Use a documented, pinned compatible Gradle/AGP/Kotlin/Compose combination, JDK 17, compile/target SDK 35 for the initial sideloadable APK. Reassess Play target requirements before store publication.
- Independent version 0.1.0. Keep Linux sources read-only; retain upstream MIT copyright and license.

## Timer and cycle

Implement a testable state machine (idle, running, paused, finished) with an injected clock. Never decrement a counter as the source of truth. Persist remaining duration/deadline, mode, settings, completed focus count and a session/generation identifier. Use monotonic elapsed realtime within a boot and a wall-clock fallback across reboot. Handle clock changes, delayed callbacks, pause at expiry and stale alarm generations explicitly.

Defaults are 25/5/15 minutes. Linux window.py _show_finished and cycle.py define transition behavior: cycle selects focus/short/long successor, and autostart launches it from the observed completion instant with its full duration. After the fourth focus choose long break; after long break reset focus count. Delayed delivery advances only one phase, never fabricates missed sessions. Android0.1.3 enables both cycle and autostart by default at the user request (Linux ships these switches off); gear settings can disable autostart (next stage selected idle) or cycle (same mode remains finished). Atomic successor persistence precedes scheduling/notification. Default initial state is still IDLE: first Start is always explicit. UI mode changes and duration changes must not mutate a running/paused session. Reset timer and reset full cycle must have unambiguous separate behavior. Count each completion only once, including restoration and alarm delivery races.

## Persistence and background

Use a small local SharedPreferences-backed repository with serialized access and atomic snapshot commits. Do not rely on an in-memory ViewModel, a UI coroutine or a foreground service for elapsed time. AlarmManager wakes a receiver for completion; cancel/reschedule on pause, reset, restart and duration/mode transitions.

Use exact alarms only where permitted. Request `SCHEDULE_EXACT_ALARM` special access through an explicit user action, not automatically; do not assume `USE_EXACT_ALARM` policy eligibility. Without access, use an inexact idle-aware alarm and clearly disclose that a background notification may be delayed. Handle access denial/revocation and SecurityException safely. Android 13+ notification permission is an independent user choice. Request only at a meaningful moment and show actionable permission state.

Receivers must validate current session/generation and deadline, complete idempotently, persist before notifying, and never launch an activity on completion. Notification channel uses system notification sound; no full-screen intent, wake lock or ongoing service needed. Reboot/package-replaced handling reschedules a persisted active timer or reconciles an expired one. Force-stop and OEM power management cannot be defeated; document this limitation.

Foreground UI ticks exist only to redraw visible remaining time; stop ticking while not started. Small settings screen behind the gear, exact Linux navy/lavender CSS palette and original 136x86 cup geometry (no invented coffee-colored Material dashboard), responsive/scrollable layout, accessible buttons/content descriptions, large-font support, drawable cup in Compose Canvas. Russian and English string resources, system-selected language.

## Build, tests and supply chain

CI on GitHub-hosted Ubuntu performs JVM unit tests, Android lint and APK assembly. Keep heavy Android tooling off the Raspberry Pi server (currently no JDK/SDK and limited free RAM). Pin action commits and dependency versions, use Gradle wrapper validation/checksum, minimal workflow permissions. Keep SDK paths, keystores and secrets out of Git.

Initial downloadable artifact is explicitly a **debug-signed development APK**, not a store-ready release. A release-signing workflow/documentation may be added, but production signing key custody and an off-server backup must be decided before publishing a production release. No fabricated screenshots, device verification or release claims.
