package com.victorious.taskbar.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.victorious.taskbar.Permissions
import com.victorious.taskbar.data.TaskbarSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Starts the taskbar after boot / app update, only if the user turned "Start on boot" on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (TaskbarSettings(context).current().startOnBoot && Permissions.canDrawOverlays(context)) {
                    TaskbarService.start(context)
                }
            } catch (_: Exception) {
            } finally { pending.finish() }
        }
    }
}
