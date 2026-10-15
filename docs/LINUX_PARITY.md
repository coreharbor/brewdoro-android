# Linux visual parity correction

User feedback (2026-10-15): first Android preview has the wrong colors and is too cluttered. Match the Linux application, not a new Material dashboard. Linux project is read-only.

## Authoritative references

- `/home/theza/brewdoro/src/brewdoro/resources/style.css` — exact color tokens and typography.
- `/home/theza/brewdoro/src/brewdoro/window.py` — layout/order/spacing and labels.
- `/home/theza/brewdoro/src/brewdoro/widgets/coffee_cup.py` plus `coffee_geometry.py` — exact 136x86 cup paths.
- https://raw.githubusercontent.com/coreharbor/brewdoro/main/docs/images/brewdoro.png — overall minimal visual reference. Current CSS/source take precedence over potentially older screenshot colors.

## Main screen

Flat header with a small settings gear (and functional locale control if retained). No giant title, tagline, explanatory cards, permission banners, settings forms, reset explanations or vertically stacked mode cards.

Centered content max 296dp wide: small uppercase mode, muted session counter, 68sp light timer, 136x86dp cup, outlined/translucent 48dp primary action with 12dp corners, quiet reset text, then three horizontal duration presets. Match Linux order and gaps (4/12/18/26/8/28dp). Preserve Android minimum touch targets, screen insets, scrolling on small/landscape/large-font screens and accessible labels.

Use exact Linux palette: background #24283b, header #1f2335, surface #292e42, primary text #c0caf5, secondary #a9b1d6, muted/cup outline #737aa2, accent #3d59a1 / light #7aa2f7, liquid rgba(0.878,0.686,0.408,1). Disable dynamic colors. No brown theme, saucer, steam, foam or invented decorative shapes. Port the exact original cup curves and rounded liquid.

## Settings and behavior

Keep duration settings, permission state and Android-specific background limitations behind the gear, accessible even during a session; only duration editing is locked. Keep cycle-reset confirmation there or reproduce the contextual reset behavior without adding a second reset action to main. Keep the tested timer/repository/receivers untouched. No backend, extra analytics or permission changes.

## Verification and delivery

Increment Android versionCode/versionName. Existing previews use ephemeral debug keys; if the signer differs, say a reinstall may be required instead of promising in-place upgrade.

CI must run unit tests, lint, assembly, APK signature verification. Add a hosted Android emulator smoke check and real screenshot of the installed app (not an authored mock) for visual inspection and reference side-by-side. Prefer a Compose UI/instrumentation test checking main/settings separation. No local SDK/JDK installation on the Pi.

Publish a clearly labeled second development preview with APK and checksum. Direct local .apk attachments are blocked by OpenClaw; use the verified GitHub release link, and a screenshot attachment if supported. Do not zip/relabel the APK to bypass the attachment restriction. Physical Android16 behavior remains unverified until user testing.
