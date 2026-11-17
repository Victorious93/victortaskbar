# VictorTaskbar root module (optional)

**You do not need this.** VictorTaskbar works as a normal app without root. This module is
for rooted devices only. It installs the same debug APK *systemlessly* as a privileged system app
(`/system/priv-app/VictorTaskbar`) with a minimal privileged-permission allowlist.

**Status: untested on a real device.** It is built and checked in CI (zip layout, `module.prop`,
shellcheck), but nobody has flashed it yet. Back up first.

Works with **Magisk** (v20.4+), **KernelSU** and **APatch**. Module id: `victortaskbar_priv`.

## What it does

| File in the zip | Purpose |
| --- | --- |
| `module.prop` | id, name, version, versionCode, author, description |
| `customize.sh` | Checks API level (24+) and ABI (`arm64`/`arm` only, matching the APK's `abiFilters`), reports the manager (`MAGISK_VER_CODE` / `KSU` / `APATCH`), checks the APK and XML are present, sets owner `0:0`, modes `0755`/`0644` and SELinux label `u:object_r:system_file:s0` |
| `system/priv-app/VictorTaskbar/VictorTaskbar.apk` | The debug APK, added at build time (never committed) |
| `system/etc/permissions/privapp-permissions-com.victorious.taskbar.xml` | Privileged-permission allowlist |
| `META-INF/com/google/android/update-binary`, `updater-script` | Standard Magisk module template installer (`#MAGISK`), for flashing from the Magisk app or recovery. KernelSU and APatch use their own installer and just run `customize.sh`. |

There is no `uninstall.sh`: removing the module removes the overlay at the next boot, so there is
nothing to clean up.

## Privileged permissions (and why)

Only one:

- **`android.permission.PACKAGE_USAGE_STATS`**: the taskbar's *recent apps* list reads
  `UsageStatsManager` (`AppRepository.recentApps`). As a normal app this needs the user to turn on
  *Usage access* by hand. As a priv-app with this allowlisted, Android grants it at install.

Deliberately **not** granted:

- `REAL_GET_TASKS`: nothing in the app calls the running-tasks APIs yet. It will be added together
  with the code that uses it (planned for windowing / task list work, see ROADMAP).
- `MANAGE_ACTIVITY_TASKS`: protection level is signature-only, so a priv-app cannot get it anyway.

Rule: every permission in the XML must be requested by the app, and every privileged permission
the app requests must be in the XML. On devices with `ro.control_privapp_permissions=enforce` a
missing entry can stop the device from booting. `scripts/check-module-zip.sh` enforces both in CI.

## Build

```
scripts/build-module.sh            # runs ./gradlew assembleDebug first
scripts/build-module.sh path/to.apk # or package an existing debug APK
scripts/check-module-zip.sh build/module/victortaskbar-root-module-0.1.0.zip
```

CI uploads the zip as the `victortaskbar-root-module` artifact (job `root-module` in
`.github/workflows/android.yml`). Only the committed debug key is used; there are no release keys.

## Install

**Back up first** (at least your data, ideally a full backup or the stock boot image).

- **Magisk**: Magisk app → Modules → *Install from storage* → pick the zip → Reboot.
- **KernelSU**: KernelSU app → Module → *Install* → pick the zip → Reboot.
- **APatch**: APatch app → APModule → *Install* → pick the zip → Reboot.

After reboot, open VictorTaskbar. The main screen shows
`Install: Privileged system app (root module)` and usage access shows as granted.

If you already had VictorTaskbar installed normally, it must be signed with the same debug key (all
builds from this repo are). Android then treats the installed copy as an update of the system copy.

## Uninstall

Remove the module in the same manager app and reboot. VictorTaskbar goes back to being a normal
app (or disappears if it was only installed through the module; install the APK again if you want it).

## Risks and recovery

- Any system-level module can **bootloop** a device, especially on heavily customised ROMs.
- If the device does not boot:
  - Magisk: boot to safe mode (most devices: hold Volume Down during boot) to disable all modules,
    or run `adb wait-for-device shell magisk --remove-modules`.
  - KernelSU / APatch: press Volume Down repeatedly during boot to enter safe mode (all modules
    disabled), then remove the module in the app.
  - Or delete `/data/adb/modules/victortaskbar_priv` from recovery.
