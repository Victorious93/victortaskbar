package com.victorious.taskbar.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.victorious.taskbar.data.*
import com.victorious.taskbar.theme.FleetThemeClient
import com.victorious.taskbar.theme.TaskbarTheme
import com.victorious.taskbar.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Foreground service that draws the taskbar as a system overlay. */
class TaskbarService : LifecycleService(), SavedStateRegistryOwner {

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var wm: WindowManager
    private lateinit var controls: DeviceControls
    private var root: FrameLayout? = null

    // Shared between the Compose tree and the window callbacks (outside touch / back key).
    private val popup = MutableStateFlow<Popup>(Popup.None)
    private val collapsed = MutableStateFlow(false)
    private var position = BarPosition.BOTTOM

    override fun onCreate() {
        savedStateController.performRestore(null)
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        controls = DeviceControls(this).also { it.start() }
        FleetThemeClient.start(this)
        running.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP || !Permissions.canDrawOverlays(this)) {
            stopSelf(); return START_NOT_STICKY
        }
        startInForeground()
        if (intent?.action == ACTION_TOGGLE_COLLAPSE) collapsed.value = !collapsed.value
        if (root == null) addOverlay()
        return START_STICKY
    }

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Taskbar", NotificationManager.IMPORTANCE_MIN))
        }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val toggle = PendingIntent.getService(this, 1,
            Intent(this, TaskbarService::class.java).setAction(ACTION_TOGGLE_COLLAPSE), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 2,
            Intent(this, TaskbarService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("VictorTaskbar is running")
            .setContentText("Tap for settings")
            .setContentIntent(open)
            .addAction(0, "Show / hide", toggle)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, 1, n, type)
    }

    private fun params(focusable: Boolean, collapsed: Boolean) = WindowManager.LayoutParams(
        if (collapsed) WindowManager.LayoutParams.WRAP_CONTENT else WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        (if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = (if (position == BarPosition.TOP) Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL
    }

    private fun relayout() {
        val r = root ?: return
        try { wm.updateViewLayout(r, params(focusable = popup.value == Popup.Start, collapsed = collapsed.value)) } catch (_: Exception) {}
    }

    private fun closePopup() { popup.value = Popup.None }

    @SuppressLint("ClickableViewAccessibility")
    private fun addOverlay() {
        val repo = AppRepository(this)
        val store = PinnedAppsStore(this)
        val settings = TaskbarSettings(this)

        // Wrapper so we get Back and taps outside the overlay window to close popups.
        val container = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && popup.value != Popup.None) {
                    if (event.action == KeyEvent.ACTION_UP) closePopup()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        container.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE && popup.value != Popup.None) closePopup()
            false
        }
        val v = ComposeView(this)
        container.addView(v)
        container.setViewTreeLifecycleOwner(this)
        container.setViewTreeSavedStateRegistryOwner(this)

        v.setContent { TaskbarTheme {
            val prefs by settings.prefs.collectAsState(initial = TaskbarPrefs())
            val current by popup.collectAsState()
            val isCollapsed by collapsed.collectAsState()
            val torchOn by controls.torchOn.collectAsState()
            var allApps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
            var loadingApps by remember { mutableStateOf(false) }
            var appsVersion by remember { mutableIntStateOf(0) }
            var recent by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
            var battery by remember { mutableStateOf(controls.battery()) }
            val pinnedPkgs by store.pinned.collectAsState(initial = emptyList())
            val pinned = remember(pinnedPkgs, appsVersion) { pinnedPkgs.mapNotNull { repo.entryFor(it) } }

            LaunchedEffect(prefs.recentCount) {
                while (true) {
                    recent = withContext(Dispatchers.IO) { repo.recentApps(prefs.recentCount) }
                    battery = controls.battery()
                    delay(10_000)
                }
            }
            LaunchedEffect(prefs.position) { position = prefs.position; relayout() }
            LaunchedEffect(current, isCollapsed) {
                if (current == Popup.Start && (allApps.isEmpty() || loadingApps)) {
                    loadingApps = true
                    allApps = withContext(Dispatchers.IO) { repo.launchableApps() }
                    loadingApps = false
                }
                if (current == Popup.Quick) battery = controls.battery()
                relayout()
            }
            LaunchedEffect(appsVersion) {
                if (appsVersion > 0) {
                    IconCache.clear()
                    allApps = withContext(Dispatchers.IO) { repo.launchableApps() }
                }
            }

            fun toast(msg: String) = Toast.makeText(this@TaskbarService, msg, Toast.LENGTH_SHORT).show()
            fun launch(app: AppEntry, mode: LaunchMode = prefs.defaultLaunch) {
                closePopup()
                if (!repo.launch(app.packageName, mode)) toast("Couldn't open ${app.label}")
            }
            fun openSettings() {
                closePopup()
                repo.start(Intent(this@TaskbarService, MainActivity::class.java))
            }
            fun withClose(f: () -> Unit): () -> Unit = { closePopup(); f() }

            val top = prefs.position == BarPosition.TOP
            if (isCollapsed) {
                CollapsedHandle(prefs.position) { collapsed.value = false }
                return@TaskbarTheme
            }
            val popupContent: @Composable () -> Unit = {
                Box(contentAlignment = if (current is Popup.Quick) Alignment.BottomEnd else Alignment.BottomStart) {
                    when (val p = current) {
                        Popup.Start -> StartMenu(
                            apps = allApps, pinned = pinned,
                            recent = recent.filter { r -> pinnedPkgs.none { it == r.packageName } },
                            grid = prefs.menuGrid, loading = loadingApps,
                            onLaunch = { launch(it) },
                            onAppMenu = { popup.value = Popup.AppMenu(it) },
                            onToggleGrid = { lifecycleScope.launch { settings.update { it.copy(menuGrid = !it.menuGrid) } } },
                            onOpenSettings = ::openSettings,
                            onSystemSettings = withClose { repo.start(Intent(Settings.ACTION_SETTINGS)) },
                            onRefresh = { appsVersion++ },
                        )
                        Popup.Quick -> QuickPanel(battery, torchOn, controls.hasTorch, QuickActions(
                            onInternet = withClose { repo.openPanel(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_WIFI_SETTINGS) },
                            onBluetooth = withClose { repo.start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
                            onTorch = { if (!controls.toggleTorch()) toast("Torch unavailable") },
                            onVolumePanel = withClose { repo.openPanel(Settings.Panel.ACTION_VOLUME, Settings.ACTION_SOUND_SETTINGS) },
                            onVolume = { controls.volume(if (it > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER) },
                            onMute = { controls.toggleMute() },
                            onMedia = { controls.mediaKey(it) },
                            onDisplay = withClose { repo.start(Intent(Settings.ACTION_DISPLAY_SETTINGS)) },
                            onBattery = withClose { repo.start(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) },
                            onSettings = withClose { repo.start(Intent(Settings.ACTION_SETTINGS)) },
                            onTaskbarSettings = ::openSettings,
                            onStopTaskbar = { stopSelf() },
                        ))
                        is Popup.AppMenu -> {
                            val pkg = p.app.packageName
                            val idx = pinnedPkgs.indexOf(pkg)
                            AppActionsMenu(
                                app = p.app, isPinned = idx >= 0,
                                canMoveLeft = idx > 0, canMoveRight = idx >= 0 && idx < pinnedPkgs.lastIndex,
                                onOpen = { launch(p.app, LaunchMode.NORMAL) },
                                onSplit = { launch(p.app, LaunchMode.SPLIT) },
                                onWindow = { launch(p.app, LaunchMode.WINDOW) },
                                onTogglePin = { lifecycleScope.launch { store.toggle(pkg) }; closePopup() },
                                onMove = { d -> lifecycleScope.launch { store.move(pkg, d) } },
                                onInfo = withClose { repo.openAppInfo(pkg) },
                                onUninstall = withClose {
                                    if (!repo.uninstall(pkg)) toast("Couldn't start uninstall")
                                    lifecycleScope.launch { delay(4000); appsVersion++ }
                                },
                            )
                        }
                        Popup.None -> {}
                    }
                }
            }
            Column {
                if (!top) PopupHost(current != Popup.None, top = false, popupContent)
                TaskbarBar(
                    prefs = prefs, pinned = pinned, recent = recent, popup = current, battery = battery,
                    onStart = { popup.value = if (current == Popup.Start) Popup.None else Popup.Start },
                    onHome = withClose { repo.goHome() },
                    onLaunch = { launch(it) },
                    onAppMenu = { popup.value = Popup.AppMenu(it) },
                    onTray = { popup.value = if (current == Popup.Quick) Popup.None else Popup.Quick },
                    onCollapse = { closePopup(); collapsed.value = true },
                )
                if (top) PopupHost(current != Popup.None, top = true, popupContent)
            }
        } }
        wm.addView(container, params(focusable = false, collapsed = false))
        root = container
    }

    override fun onDestroy() {
        running.value = false
        controls.stop()
        root?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        root = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "taskbar"
        const val ACTION_STOP = "com.victorious.taskbar.STOP"
        const val ACTION_TOGGLE_COLLAPSE = "com.victorious.taskbar.TOGGLE_COLLAPSE"
        /** True while the service is alive; observed by the settings screen. */
        val running = MutableStateFlow(false)
        fun start(ctx: Context) =
            androidx.core.content.ContextCompat.startForegroundService(ctx, Intent(ctx, TaskbarService::class.java))
        fun stop(ctx: Context) = ctx.stopService(Intent(ctx, TaskbarService::class.java))
    }
}
