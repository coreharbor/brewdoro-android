# Privacy

Brewdoro for Android is designed to work entirely offline. It does not use an account, analytics, advertising, remote servers, or the Android Internet permission. Timer state and preferences are stored locally in the app sandbox.

The initial app disables automatic Android backup so settings are not silently copied to cloud backup. Clearing app data or uninstalling removes its local state. Notifications are optional and controlled by Android permissions.

This document describes the intended MVP; check the manifest and source and update this document if implementation changes.
