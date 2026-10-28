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

## Fleet theme (VictorLaunch)

VictorTaskbar is a **read-only** client of VictorLaunch's fleet theme (spec: victorlaunch `docs/specs/FLEET_THEME.md`).

- On start it queries `content://com.victorious.launch.theme/current`, registers a `ContentObserver`, and listens for the explicit broadcast `com.victorious.launch.action.FLEET_THEME_CHANGED` (exported receiver; extras are ignored, it only triggers a re-query).
- Before querying it verifies that the authority resolves to package `com.victorious.launch` **and** that the signer SHA-256 matches the pinned VictorLaunch cert (not just a signature permission).
- `schemaVersion != 1`, a `SecurityException`, a failed pin, or bad data → built-in neon theme. The app never writes to the provider.
- `palette.*` applies live to both the app and the overlay taskbar.
- **Action for Don:** VictorLaunch must trust VictorTaskbar (add it to `FleetPins.KNOWN`) before the query will succeed; until then the app shows "built-in". VictorTaskbar debug cert SHA-256: `4F:58:A5:C5:75:93:5E:10:3B:1F:73:C0:7A:D2:E0:0A:FF:64:57:76:27:71:1B:77:96:79:37:D5:FC:3B:C2:92`.
