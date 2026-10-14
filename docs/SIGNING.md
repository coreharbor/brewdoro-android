# Production signing and distribution

The initial CI produces a **debug-signed development APK** only. There is no Play Store listing or production signing key yet. Debug keys generated on different CI machines may differ and are not suitable for a stable upgrade channel.

Before a production release:

1. Generate a dedicated Android release signing key on a trusted owner-controlled workstation. Do not reuse an SSH key or other app signing key.
2. Store its keystore, alias and passwords in secure owner-controlled storage; make and test an encrypted off-server backup. Losing the key may prevent updates to already installed apps.
3. Choose direct APK distribution versus Play App Signing; confirm current store target-SDK and exact-alarm policy requirements if publishing there.
4. Add a manual signed-release workflow using repository/environment secrets and restricted permissions; never commit the keystore/passwords or echo secrets in logs.
5. Verify the APK signing certificate, versionCode progression and install-over-existing upgrade before publishing a GitHub Release.

Production signing is intentionally not silently bootstrapped with a key whose only copy lives on this server.
