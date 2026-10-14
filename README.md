# Brewdoro for Android

An offline Pomodoro timer with an animated coffee cup. Android companion to [Brewdoro for Linux](https://github.com/coreharbor/brewdoro), implemented independently in Kotlin and Jetpack Compose.

**Status:** initial Android implementation in progress; no verified APK yet.

## Scope

- Focus, short break and long break; a long break after four focus sessions.
- Animated coffee cup, pause/resume, reset and explicit next-session start.
- Configurable durations, persisted session and local settings.
- Android completion notifications, with transparent permission/fallback behavior.
- Russian and English interface; no account, server, analytics or network permission.

Android 8.0 (API 26) or newer. First development version: 0.1.0.

## Project documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Implementation plan and acceptance gates](docs/PLAN.md)
- [Privacy](PRIVACY.md)

MIT licensed. Existing Brewdoro design and behavior are credited to Brewdoro contributors. Android and Linux releases are independent.
