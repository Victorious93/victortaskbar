package com.victorious.taskbar.ui

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.ReadOnlyComposable
import com.victorious.taskbar.theme.LocalFleetPalette
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.victorious.taskbar.data.AppEntry
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Colors follow the live fleet theme (VictorLaunch) or the built-in neon fallback.
val NeonCyan: Color @Composable @ReadOnlyComposable get() = Color(LocalFleetPalette.current.primary)
val NeonMagenta: Color @Composable @ReadOnlyComposable get() = Color(LocalFleetPalette.current.secondary)
val BarBg: Color @Composable @ReadOnlyComposable get() = Color(LocalFleetPalette.current.tile)
val OnBar: Color @Composable @ReadOnlyComposable get() = Color(LocalFleetPalette.current.onBackground)
val Muted: Color @Composable @ReadOnlyComposable get() = Color(LocalFleetPalette.current.muted)

@Composable
fun AppIcon(app: AppEntry, size: Int = 36) {
    val bmp = remember(app.packageName) { app.icon.toBitmap(96, 96).asImageBitmap() }
    Image(bmp, contentDescription = app.label, modifier = Modifier.size(size.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskbarBar(
    pinned: List<AppEntry>,
    recent: List<AppEntry>,
    onStart: () -> Unit,
    onLaunch: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).background(BarBg).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(
                Brush.linearGradient(listOf(NeonCyan, NeonMagenta)), RoundedCornerShape(20.dp)
            ).clickable { onStart() },
            contentAlignment = Alignment.Center,
        ) { Text("◎", color = OnBar, fontSize = 20.sp) }
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            pinned.forEach { app -> Box(Modifier.clickable { onLaunch(app.packageName) }) { AppIcon(app) } }
            if (pinned.isNotEmpty() && recent.isNotEmpty()) {
                Box(Modifier.width(1.dp).height(32.dp).background(Muted))
            }
            recent.filter { r -> pinned.none { it.packageName == r.packageName } }.forEach { app ->
                Box(Modifier.clickable { onLaunch(app.packageName) }) { AppIcon(app, 32) }
            }
        }
        Tray()
    }
}

@Composable
fun Tray() {
    val ctx = LocalContext.current
    var time by remember { mutableStateOf("") }
    var battery by remember { mutableStateOf(batteryText(ctx)) }
    LaunchedEffect(Unit) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            time = fmt.format(Date())
            battery = batteryText(ctx)
            delay(15_000)
        }
    }
    // TODO(phase 3): quick toggles + notification icons go here.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(battery, color = NeonCyan, fontSize = 13.sp)
        Spacer(Modifier.width(10.dp))
        Text(time, color = OnBar, fontSize = 15.sp)
    }
}

private fun batteryText(ctx: Context): String {
    val i: Intent? = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    if (level < 0) return "--%"
    return "${level * 100 / scale}%" + if (charging) " ⚡" else ""
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StartMenu(
    apps: List<AppEntry>,
    pinned: Set<String>,
    onLaunch: (String) -> Unit,
    onTogglePin: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query, ignoreCase = true) }
    }
    Column(
        Modifier.width(320.dp).heightIn(max = 480.dp)
            .background(BarBg, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)).padding(12.dp)
    ) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Search apps") }, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = OnBar, unfocusedTextColor = OnBar,
                focusedBorderColor = NeonCyan, unfocusedBorderColor = NeonMagenta,
            ),
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f, fill = false)) {
            items(filtered, key = { it.packageName }) { app ->
                Row(
                    Modifier.fillMaxWidth().combinedClickable(
                        onClick = { onLaunch(app.packageName) },
                        onLongClick = { onTogglePin(app.packageName) },
                    ).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app, 28)
                    Spacer(Modifier.width(10.dp))
                    Text(app.label, color = OnBar, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onTogglePin(app.packageName) }) {
                        Text(if (app.packageName in pinned) "Unpin" else "Pin", color = NeonCyan)
                    }
                }
            }
        }
        TextButton(onClick = onOpenSettings) { Text("VictorTaskbar settings", color = NeonMagenta) }
    }
}
