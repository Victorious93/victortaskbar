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

## Phase 5 — Release
- [ ] Release signing via CI secrets (never committed)
- [ ] App icon/branding, store listing
