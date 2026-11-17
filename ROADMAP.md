# Roadmap

## Phase 1 — MVP (this scaffold)
- [x] Foreground service drawing a bottom overlay taskbar
- [x] Consent screens for overlay and usage access
- [x] Start menu with app search
- [x] Pinned apps (DataStore), pin/unpin from start menu
- [x] Recent apps via UsageStatsManager
- [x] Tray: clock + battery
- [x] CI building a debug APK

## Phase 2 — Windowing
- [ ] Freeform window launch (`ActivityOptions.setLaunchBounds`) — stub in `stubs/FutureFeatures.kt`
- [ ] Split-screen launch (`FLAG_ACTIVITY_LAUNCH_ADJACENT`)
- [ ] Taskbar position/size/auto-hide settings

## Phase 3 — Tray
- [ ] Quick toggles (Wi-Fi, Bluetooth, brightness, volume)
- [ ] Notification icons via NotificationListenerService (own consent screen)

## Phase 4 — Power features
- [ ] Optional Shizuku integration for privileged actions
- [ ] Accessibility service only if strictly needed, with its own consent flow

## Optional root module (Magisk / KernelSU / APatch)
- [x] `module/` with priv-app install, minimal privapp-permissions XML (`PACKAGE_USAGE_STATS`), standard Magisk template installer
- [x] `scripts/build-module.sh`, `scripts/check-module-zip.sh`, CI job uploading the zip
- [x] Install-mode line on the main screen; usage access recognised when granted as a priv-app
- [ ] Flash-test on a real device with Magisk, KernelSU and APatch
- [ ] Add `REAL_GET_TASKS` only together with code that uses it (task list / windowing)

## Phase 5 — Release
- [ ] Release signing via CI secrets (never committed)
- [ ] App icon/branding, store listing

## Fleet theme
- [x] Read-only FleetThemeClient (provider query, observer, change broadcast, package+cert pin, neon fallback)
- [ ] Get VictorTaskbar added to VictorLaunch FleetPins.KNOWN and verify on a device
- [ ] Release-cert pin once release signing exists
