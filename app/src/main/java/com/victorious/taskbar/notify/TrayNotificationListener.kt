package com.victorious.taskbar.notify

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class TrayNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
    val clearable: Boolean,
    val contentIntent: PendingIntent?,
)

/**
 * Mirrors active notifications for the taskbar tray. Only runs after the user grants
 * notification access in Android settings; nothing is stored or sent anywhere.
 */
class TrayNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() { instance = this; publish() }
    override fun onListenerDisconnected() { instance = null; _items.value = emptyList() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = publish()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()

    private fun publish() {
        val list = try { activeNotifications } catch (e: Exception) { null } ?: return
        _items.value = list.asSequence()
            .filter { it.packageName != packageName }
            .filter { it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .map { sbn ->
                val ex = sbn.notification.extras
                TrayNotification(
                    key = sbn.key,
                    packageName = sbn.packageName,
                    title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                    text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty(),
                    postedAt = sbn.postTime,
                    clearable = sbn.isClearable,
                    contentIntent = sbn.notification.contentIntent,
                )
            }
            .filter { it.title.isNotBlank() || it.text.isNotBlank() }
            .sortedByDescending { it.postedAt }
            .toList()
    }

    companion object {
        private val _items = MutableStateFlow<List<TrayNotification>>(emptyList())
        val items: StateFlow<List<TrayNotification>> = _items
        @Volatile private var instance: TrayNotificationListener? = null

        fun hasAccess(ctx: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

        /** Ask Android to rebind us (e.g. after the taskbar starts) when access is granted but we're disconnected. */
        fun ensureBound(ctx: Context) {
            if (instance == null && hasAccess(ctx)) {
                try { NotificationListenerService.requestRebind(ComponentName(ctx, TrayNotificationListener::class.java)) } catch (_: Exception) {}
            }
        }

        fun dismiss(key: String) { try { instance?.cancelNotification(key) } catch (_: Exception) {} }

        fun dismissAll() { try { instance?.cancelAllNotifications() } catch (_: Exception) {} }

        /**
         * Fires the notification's tap action. On Android 14+ the sender must opt in to starting
         * activities from the background; the taskbar may do so because it holds "display over other apps".
         */
        fun open(n: TrayNotification): Boolean {
            val pi = n.contentIntent ?: return false
            return try {
                if (Build.VERSION.SDK_INT >= 34) {
                    val opts = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    pi.send(null, 0, null, null, null, null, opts.toBundle())
                } else pi.send()
                true
            } catch (e: Exception) { false }
        }
    }
}
