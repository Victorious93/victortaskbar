# VictorTaskbar

A desktop-style taskbar for Android: a bar along the bottom of the screen, drawn over other apps,
with a start menu, pinned apps, recent apps, a clock and a battery indicator.

Status: MVP scaffold (v0.1.0). See [ROADMAP.md](ROADMAP.md) and [MANUAL.md](MANUAL.md).

## Tech
- Kotlin + Jetpack Compose, package `com.victorious.taskbar`
- minSdk 24, compileSdk/targetSdk 36
- Gradle Kotlin DSL with version catalog (`gradle/libs.versions.toml`), Gradle wrapper 8.14.3, AGP 8.13
- ABIs: `arm64-v8a`, `armeabi-v7a`; APK signing v2 + v3

## Build
Requires JDK 17+ and the Android SDK (platform 36).
```
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```
CI (`.github/workflows/android.yml`) builds the debug APK on every push/PR and uploads it as an artifact.

### Signing
Debug builds are signed with the committed `app/debug.keystore`
(store/key password `android`, alias `androiddebugkey`) so every build, local or CI, has the same
signature and installs over the previous one. **This is a debug key only.** No release keys are in this repo.

## Permissions and consent
| Permission | Why | How it's granted |
|---|---|---|
| `SYSTEM_ALERT_WINDOW` | Draw the taskbar over other apps | Only after the in-app consent screen explains it; the button opens Android's overlay settings |
| `PACKAGE_USAGE_STATS` | Show recently used apps (optional) | Separate consent screen; the button opens Usage access settings. Data stays on device |
| `FOREGROUND_SERVICE` (+ `SPECIAL_USE`) | Keep the taskbar alive | Automatic; shows an ongoing notification |
| `POST_NOTIFICATIONS` | Show that ongoing notification on Android 13+ | Runtime prompt on Start |
| `QUERY_ALL_PACKAGES` | List launchable apps in the start menu | Install-time |

No accessibility service is used.

## Victorious fleet

- Monorepo: [https://github.com/Victorious93/victorious-fleet](https://github.com/Victorious93/victorious-fleet) *(not yet a submodule)*
- Status: [fleet-manifest ROADMAP](https://github.com/Victorious93/fleet-manifest/blob/main/ROADMAP.md)
- Fleet site: [https://victorious-framework.pages.dev](https://victorious-framework.pages.dev)

## License
All rights reserved, Don Victor. See [LICENSE](LICENSE).
