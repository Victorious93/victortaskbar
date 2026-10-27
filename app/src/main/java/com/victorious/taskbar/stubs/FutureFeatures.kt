package com.victorious.taskbar.stubs

/**
 * Placeholders for upcoming phases (see ROADMAP.md). Nothing here is wired up yet.
 */
object FreeformWindows {
    // TODO(phase 2): launch apps in freeform via ActivityOptions.setLaunchBounds (needs
    //  "enable freeform windows" developer option or Shizuku-granted permissions).
    // TODO(phase 2): split-screen launch with FLAG_ACTIVITY_LAUNCH_ADJACENT.
    fun isSupported(): Boolean = false
}

object QuickToggles {
    // TODO(phase 3): Wi-Fi / Bluetooth / brightness / volume toggles in the tray.
    fun available(): List<String> = emptyList()
}

object NotificationTray {
    // TODO(phase 3): NotificationListenerService behind its own consent screen.
    fun enabled(): Boolean = false
}

object ShizukuBridge {
    // TODO(phase 4): optional Shizuku integration for privileged actions (task list, freeform).
    fun isAvailable(): Boolean = false
}
