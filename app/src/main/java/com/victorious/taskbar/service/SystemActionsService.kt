package com.victorious.taskbar.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Optional, user-enabled accessibility service whose ONLY job is to press system buttons
 * (Back, Recents, notification shade, lock, screenshot, power menu) for the taskbar.
 * It is configured to receive no window content (canRetrieveWindowContent=false) and ignores events.
 */
class SystemActionsService : AccessibilityService() {
    override fun onServiceConnected() { instance = this; _connected.value = true }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { instance = null; _connected.value = false; super.onDestroy() }

    enum class Action(val label: String, val minSdk: Int, val code: Int) {
        BACK("Back", 16, GLOBAL_ACTION_BACK),
        RECENTS("Recent apps", 16, GLOBAL_ACTION_RECENTS),
        NOTIFICATIONS("Notification shade", 16, GLOBAL_ACTION_NOTIFICATIONS),
        QUICK_SETTINGS("Quick settings", 17, GLOBAL_ACTION_QUICK_SETTINGS),
        POWER("Power menu", 21, GLOBAL_ACTION_POWER_DIALOG),
        LOCK("Lock screen", 28, 8 /* GLOBAL_ACTION_LOCK_SCREEN */),
        SCREENSHOT("Screenshot", 28, 9 /* GLOBAL_ACTION_TAKE_SCREENSHOT */),
    }

    companion object {
        @Volatile private var instance: SystemActionsService? = null
        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected

        fun supported(a: Action) = Build.VERSION.SDK_INT >= a.minSdk

        /** Returns false when the service is off or Android refused the action. */
        fun perform(a: Action): Boolean {
            val s = instance ?: return false
            if (!supported(a)) return false
            return try { s.performGlobalAction(a.code) } catch (e: Exception) { false }
        }

        fun isEnabled(ctx: Context): Boolean {
            val flat = ComponentName(ctx, SystemActionsService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { it.equals(flat, ignoreCase = true) }
        }
    }
}
