package com.victorious.taskbar.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.victorious.taskbar.MainActivity
import com.victorious.taskbar.Permissions
import com.victorious.taskbar.data.AppEntry
import com.victorious.taskbar.data.AppRepository
import com.victorious.taskbar.data.PinnedAppsStore
import com.victorious.taskbar.ui.StartMenu
import com.victorious.taskbar.ui.TaskbarBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Foreground service that draws the bottom taskbar as a system overlay. */
class TaskbarService : LifecycleService(), SavedStateRegistryOwner {

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var wm: WindowManager
    private var view: ComposeView? = null

    override fun onCreate() {
        savedStateController.performRestore(null)
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP || !Permissions.canDrawOverlays(this)) {
            stopSelf(); return START_NOT_STICKY
        }
        startInForeground()
        if (view == null) addOverlay()
        return START_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Taskbar", NotificationManager.IMPORTANCE_MIN))
        }
        val pi = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("VictorTaskbar is running")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 1, n, type)
    }

    private fun overlayParams(focusable: Boolean) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply { gravity = Gravity.BOTTOM or Gravity.START }

    private fun addOverlay() {
        val repo = AppRepository(this)
        val store = PinnedAppsStore(this)
        val v = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@TaskbarService)
            setViewTreeSavedStateRegistryOwner(this@TaskbarService)
        }
        v.setContent {
            var menuOpen by remember { mutableStateOf(false) }
            var allApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
            var recent by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
            val pinnedPkgs by store.pinned.collectAsState(initial = emptyList())
            val pinned = remember(pinnedPkgs) { pinnedPkgs.mapNotNull { repo.entryFor(it) } }
            LaunchedEffect(Unit) {
                while (true) { recent = repo.recentApps(); delay(10_000) }
            }
            LaunchedEffect(menuOpen) {
                if (menuOpen && allApps.isEmpty()) allApps = repo.launchableApps()
                // Start menu needs focus for the search field; the bar alone must not steal it.
                wm.updateViewLayout(v, overlayParams(focusable = menuOpen))
            }
            val launch: (String) -> Unit = { menuOpen = false; repo.launch(it) }
            Column {
                if (menuOpen) StartMenu(
                    apps = allApps,
                    pinned = pinnedPkgs.toSet(),
                    onLaunch = launch,
                    onTogglePin = { pkg ->
                        lifecycleScope.launch { if (pkg in pinnedPkgs) store.unpin(pkg) else store.pin(pkg) }
                    },
                    onOpenSettings = {
                        menuOpen = false
                        startActivity(Intent(this@TaskbarService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                )
                TaskbarBar(pinned, recent, onStart = { menuOpen = !menuOpen }, onLaunch = launch)
            }
        }
        wm.addView(v, overlayParams(focusable = false))
        view = v
    }

    override fun onDestroy() {
        view?.let { wm.removeView(it) }
        view = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "taskbar"
        const val ACTION_STOP = "com.victorious.taskbar.STOP"
        fun start(ctx: Context) =
            androidx.core.content.ContextCompat.startForegroundService(ctx, Intent(ctx, TaskbarService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, TaskbarService::class.java))
    }
}
