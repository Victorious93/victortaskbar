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
- [x] Freeform window launch (`ActivityOptions.setLaunchBounds`; only applied when freeform is enabled)
- [x] Split-screen launch (`FLAG_ACTIVITY_LAUNCH_ADJACENT`)
- [x] Taskbar position (top/bottom), size, opacity, hide-to-handle

## Phase 3 — Tray
- [x] Quick panel: Internet/Bluetooth panels, torch, volume, media keys, display/battery shortcuts (brightness slider not done: needs WRITE_SETTINGS)
- [ ] Notification icons via NotificationListenerService (own consent screen)

## Phase 4 — Power features
- [ ] Optional Shizuku integration for privileged actions
- [ ] Accessibility service only if strictly needed, with its own consent flow

## Phase 5 — Release
- [ ] Release signing via CI secrets (never committed)
- [ ] App icon/branding, store listing

## Fleet theme
- [x] Read-only FleetThemeClient (provider query, observer, change broadcast, package+cert pin, neon fallback)
- [ ] Get VictorTaskbar added to VictorLaunch FleetPins.KNOWN and verify on a device
- [ ] Release-cert pin once release signing exists
