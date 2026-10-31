package com.victorious.taskbar

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.victorious.taskbar.data.BarPosition
import com.victorious.taskbar.data.BarSize
import com.victorious.taskbar.data.LaunchMode
import com.victorious.taskbar.data.TaskbarPrefs
import com.victorious.taskbar.data.TaskbarSettings
import com.victorious.taskbar.service.TaskbarService
import com.victorious.taskbar.theme.FleetThemeClient
import com.victorious.taskbar.theme.TaskbarTheme
import com.victorious.taskbar.theme.ThemeSource
import com.victorious.taskbar.ui.Muted
import com.victorious.taskbar.ui.NeonCyan
import com.victorious.taskbar.ui.NeonMagenta
import com.victorious.taskbar.ui.OnBar
import com.victorious.taskbar.ui.PanelBg
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private lateinit var settings: TaskbarSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = TaskbarSettings(this)
        FleetThemeClient.start(this)
        setContent { TaskbarTheme { Home() } }
    }

    private fun update(f: (TaskbarPrefs) -> TaskbarPrefs) { lifecycleScope.launch { settings.update(f) } }

    private fun startBar() {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        TaskbarService.start(this)
    }

    @Composable
    private fun Home() {
        var overlayOk by remember { mutableStateOf(Permissions.canDrawOverlays(this)) }
        var usageOk by remember { mutableStateOf(Permissions.hasUsageAccess(this)) }
        val running by TaskbarService.running.collectAsState()
        val prefs by settings.prefs.collectAsState(initial = TaskbarPrefs())
        val theme by FleetThemeClient.state.collectAsState()
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val obs = LifecycleEventObserver { _, e ->
                if (e == Lifecycle.Event.ON_RESUME) {
                    FleetThemeClient.refresh(this@MainActivity)
                    overlayOk = Permissions.canDrawOverlays(this@MainActivity)
                    usageOk = Permissions.hasUsageAccess(this@MainActivity)
                }
            }
            owner.lifecycle.addObserver(obs)
            onDispose { owner.lifecycle.removeObserver(obs) }
        }

        Column(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState()).safeDrawingPadding().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Hero / power
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(NeonCyan.copy(alpha = 0.25f), NeonMagenta.copy(alpha = 0.25f))))
                    .padding(18.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("VictorTaskbar", color = OnBar, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text(
                                if (running) "Running" else if (overlayOk) "Stopped" else "Setup needed",
                                color = if (running) NeonCyan else Muted,
                            )
                        }
                        Box(Modifier.size(14.dp).clip(CircleShape).background(if (running) NeonCyan else Muted.copy(alpha = 0.4f)))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (running) {
                            Button(onClick = { TaskbarService.stop(this@MainActivity) }) { Text("Stop taskbar") }
                            OutlinedButton(onClick = {
                                TaskbarService.stop(this@MainActivity); startBar()
                            }) { Text("Restart") }
                        } else {
                            Button(enabled = overlayOk, onClick = { startBar() }) { Text("Start taskbar") }
                        }
                    }
                }
            }

            if (!overlayOk || !usageOk) Card("Setup") {
                Step(
                    "Display over other apps", "Required. Lets the taskbar draw on top of apps. It never reads screen content or typing.",
                    overlayOk, "Allow",
                ) { startActivity(Permissions.overlaySettingsIntent(this@MainActivity)) }
                Step(
                    "Usage access", "Optional. Shows your recently used apps on the bar. Stays on this device.",
                    usageOk, "Allow",
                ) { startActivity(Permissions.usageSettingsIntent()) }
            }

            Card("Look") {
                Label("Position")
                Segmented(BarPosition.entries, prefs.position, { if (it == BarPosition.BOTTOM) "Bottom" else "Top" }) { v -> update { it.copy(position = v) } }
                Label("Size")
                Segmented(BarSize.entries, prefs.size, { it.label }) { v -> update { it.copy(size = v) } }
                Label("Bar opacity  ${(prefs.opacity * 100).toInt()}%")
                Slider(prefs.opacity, { v -> update { it.copy(opacity = v) } }, valueRange = 0.4f..1f)
                Toggle("Grid start menu", "Off = A–Z list with package names", prefs.menuGrid) { v -> update { it.copy(menuGrid = v) } }
            }

            Card("Clock & tray") {
                Toggle("24-hour clock", null, prefs.use24h) { v -> update { it.copy(use24h = v) } }
                Toggle("Show seconds", null, prefs.showSeconds) { v -> update { it.copy(showSeconds = v) } }
                Toggle("Show date", "Under the clock (Normal and Large sizes)", prefs.showDate) { v -> update { it.copy(showDate = v) } }
                Toggle("Battery percentage", null, prefs.showBatteryPercent) { v -> update { it.copy(showBatteryPercent = v) } }
            }

            Card("Behaviour") {
                Label("Recent apps on bar  ${prefs.recentCount}")
                Slider(prefs.recentCount.toFloat(), { v -> update { it.copy(recentCount = v.toInt()) } }, valueRange = 0f..12f, steps = 11)
                Label("Tapping an app opens it in")
                Segmented(LaunchMode.entries, prefs.defaultLaunch, { it.label }) { v -> update { it.copy(defaultLaunch = v) } }
                if (prefs.defaultLaunch == LaunchMode.WINDOW) Text(
                    "Floating windows only work when Android's freeform mode is on (Developer options → " +
                        "\"Enable freeform windows\", or a desktop mode). Otherwise apps open full screen.",
                    color = Muted, fontSize = 12.sp,
                )
                Toggle("Start on boot", "Launch the taskbar automatically after restart", prefs.startOnBoot) { v -> update { it.copy(startOnBoot = v) } }
                Toggle("Haptic feedback", "Vibrate on long-press", prefs.haptics) { v -> update { it.copy(haptics = v) } }
            }

            Card("How to use") {
                Tip("☰ Start", "Search apps (fuzzy: \"gm\" finds Google Maps). Enter opens the top result.")
                Tip("Long-press any app", "Pin/unpin, reorder, split screen, floating window, app info, uninstall.")
                Tip("Tap the clock", "Quick panel: Wi-Fi/Internet, Bluetooth, torch, volume, media controls.")
                Tip("⌄ Hide", "Collapses the bar to a small handle. The notification also has Show / hide.")
                Tip("Back / tap outside", "Closes any open menu.")
            }

            Text(
                if (theme.source == ThemeSource.FLEET) "Theme: fleet (${theme.mode ?: "?"}) from VictorLaunch"
                else "Theme: built-in neon — ${theme.fallbackReason ?: "fleet theme unavailable"}",
                color = Muted, fontSize = 12.sp,
            )
        }
    }

    @Composable
    private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(PanelBg).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, color = NeonMagenta, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }

    @Composable
    private fun Label(text: String) = Text(text, color = OnBar, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))

    @Composable
    private fun Toggle(title: String, hint: String?, value: Boolean, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth().clickable { onChange(!value) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = OnBar)
                if (hint != null) Text(hint, color = Muted, fontSize = 12.sp)
            }
            Switch(value, onChange)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, o ->
                SegmentedButton(
                    selected = o == selected, onClick = { onPick(o) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(label(o), fontSize = 12.sp, maxLines = 1) }
            }
        }
    }

    @Composable
    private fun Step(title: String, body: String, done: Boolean, button: String, onClick: () -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Icon(
                if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                tint = if (done) NeonCyan else Muted,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = OnBar, fontWeight = FontWeight.SemiBold)
                Text(body, color = Muted, fontSize = 12.sp)
            }
            if (!done) TextButton(onClick = onClick) { Text(button, color = NeonCyan) }
        }
    }

    @Composable
    private fun Tip(title: String, body: String) {
        Column(Modifier.padding(vertical = 3.dp)) {
            Text(title, color = NeonCyan, fontSize = 14.sp)
            Text(body, color = Muted, fontSize = 12.sp)
        }
    }
}
