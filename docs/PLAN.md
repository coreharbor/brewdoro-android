# Implementation plan

## 1. Repository

- [x] Create private `coreharbor/brewdoro-android`.
- [x] Preserve MIT license and link to Linux project.
- [x] Document scope, architecture and verification gates before coding.

## 2. Android MVP

- [x] Gradle wrapper, pinned build toolchain and app manifest.
- [x] Pure Kotlin timer, cycle, injectable clocks and tests.
- [x] Atomic local persistence and restoration.
- [x] Compose cup animation, timer controls, mode selection and settings.
- [x] Russian/English resources and basic accessibility.
- [x] Alarm scheduling, notification permission/exact-alarm fallback, boot restore.
- [x] GitHub Actions tests/lint/debug APK artifact.

## 3. Verification

- [x] JVM tests pass: deadline progress, pause/resume, reset, modes, cycles, one-shot completion, restoration, stale generations, delayed callbacks and wall-clock changes.
- [x] `lintDebug` passes; no unresolved compilation errors.
- [x] `assembleDebug` passes and APK artifact is downloadable for the exact pushed commit.
- [x] Source/security review completed; no secrets or network permission.
- [x] Document actual verification commands and known limitations.

## 4. Real-device gate (must not be claimed from unit tests)

- [ ] Install APK on actual Android phone; verify cup/layout and Russian locale.
- [ ] Foreground/background/locked-screen completion and sound.
- [ ] Notification permission denied/granted and exact alarm access denied/granted/revoked.
- [ ] Pause/reset near expiry; rotate/close/reopen; reboot during a session.
- [ ] Large text, battery saver and manufacturer background restrictions.

## Later, not MVP

Store listing, signed production releases and signing-key backup, screenshots, Chinese translation, widget/statistics only if requested. No accounts, server or synchronization by default.
