# VictorTaskbar — Manual

## Install
1. Get the APK: download the `victortaskbar-debug-apk` artifact from the latest GitHub Actions run,
   or build it with `./gradlew assembleDebug`.
2. Install it: `adb install -r app-debug.apk`, or copy it to the phone and open it
   (allow "Install unknown apps" for your file manager if asked).

## First run
1. Open **VictorTaskbar**.
2. **Display over other apps** — read the explanation, tap **Open overlay settings**, enable
   VictorTaskbar, then go back.
3. **Usage access** (optional) — tap **Open usage access settings**, enable VictorTaskbar, go back.
   Without it the taskbar shows pinned apps only.
4. Tap **Start taskbar**. Allow notifications if asked (Android 13+). The bar appears at the bottom.

## Use
- **◎ button** — opens/closes the start menu. Type to search apps; tap an app to launch it.
- **Pin/Unpin** — in the start menu, tap Pin/Unpin (or long-press an app). Pinned apps appear left
  on the bar, then a divider, then recent apps.
- **Tray** — battery percentage (⚡ when charging) and the clock, on the right.
- **Settings** — "VictorTaskbar settings" at the bottom of the start menu reopens the app.
- **Stop** — open the app and tap **Stop**, or revoke the overlay permission.

## Troubleshooting
- Bar doesn't appear: check the overlay permission is on, then tap Start again.
- No recent apps: grant usage access; some apps only show after you use them.
- Aggressive battery savers (some OEMs) may kill the service — exempt VictorTaskbar from battery optimisation.
