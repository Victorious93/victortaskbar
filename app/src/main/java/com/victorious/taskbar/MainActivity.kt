package com.victorious.taskbar

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.victorious.taskbar.service.TaskbarService
import com.victorious.taskbar.ui.NeonCyan
import com.victorious.taskbar.ui.NeonMagenta
import com.victorious.taskbar.ui.Muted
import com.victorious.taskbar.ui.OnBar
import com.victorious.taskbar.theme.FleetThemeClient
import com.victorious.taskbar.theme.TaskbarTheme
import com.victorious.taskbar.theme.ThemeSource

class MainActivity : ComponentActivity() {
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FleetThemeClient.start(this)
        setContent { TaskbarTheme { Home() } }
    }

    @Composable
    private fun Home() {
        var overlayOk by remember { mutableStateOf(Permissions.canDrawOverlays(this)) }
        var usageOk by remember { mutableStateOf(Permissions.hasUsageAccess(this)) }
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
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("VictorTaskbar", color = NeonCyan, fontSize = 28.sp)
            val theme by FleetThemeClient.state.collectAsState()
            Text(
                if (theme.source == ThemeSource.FLEET) "Theme: fleet (${theme.mode ?: "?"}) from VictorLaunch"
                else "Theme: built-in neon — ${theme.fallbackReason ?: "fleet theme unavailable"}",
                color = Muted,
            )
            ConsentCard(
                title = "1. Display over other apps (required)",
                body = "VictorTaskbar draws a taskbar at the bottom of your screen on top of other apps. " +
                    "Android requires you to allow \"Display over other apps\" for that. The taskbar only shows " +
                    "app icons, the clock and battery; it does not read what is on screen or what you type.",
                granted = overlayOk,
                button = "Open overlay settings",
            ) { startActivity(Permissions.overlaySettingsIntent(this@MainActivity)) }
            ConsentCard(
                title = "2. Usage access (optional, for recent apps)",
                body = "To show your recently used apps, VictorTaskbar asks Android which apps you opened in the " +
                    "last 24 hours. This stays on your device and is never uploaded. Skip it and the taskbar " +
                    "shows pinned apps only.",
                granted = usageOk,
                button = "Open usage access settings",
            ) { startActivity(Permissions.usageSettingsIntent()) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = overlayOk, onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    TaskbarService.start(this@MainActivity)
                }) { Text("Start taskbar") }
                OutlinedButton(onClick = { TaskbarService.stop(this@MainActivity) }) { Text("Stop") }
            }
            Text(
                "Tip: open the start menu (◎) to search apps. Tap Pin/Unpin or long-press an app to change pinned apps.",
                color = Muted,
            )
        }
    }

    @Composable
    private fun ConsentCard(title: String, body: String, granted: Boolean, button: String, onClick: () -> Unit) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = OnBar, fontSize = 18.sp)
                Text(body, color = Muted)
                if (granted) Text("✓ Granted", color = NeonCyan)
                else Button(onClick = onClick) { Text(button) }
            }
        }
    }
}
