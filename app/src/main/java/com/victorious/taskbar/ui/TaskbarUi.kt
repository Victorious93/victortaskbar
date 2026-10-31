package com.victorious.taskbar.ui

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.ReadOnlyComposable
import com.victorious.taskbar.data.AppEntry
import com.victorious.taskbar.data.BarPosition
import com.victorious.taskbar.data.BatteryInfo
import com.victorious.taskbar.data.IconCache
import com.victorious.taskbar.data.TaskbarLogic
import com.victorious.taskbar.data.TaskbarPrefs
import com.victorious.taskbar.theme.LocalFleetPalette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Colors follow the live fleet theme (VictorLaunch) or the built-in neon fallback.
val NeonCyan: Color @Composable @ReadOnlyComposable get() = Color(LocalFleetPalette.current.primary)
val NeonMagenta: Color @Composable @ReadOnlyComposable get() = Color(LocalFleetPalette.current.secondary)
val BarBg: Color @Composable @ReadOnlyComposable get() = Color(LocalFleetPalette.current.tile)
val PanelBg: Color @Composable @ReadOnlyComposable get() = Color(LocalFleetPalette.current.surface)
val OnBar: Color @Composable @ReadOnlyComposable get() = Color(LocalFleetPalette.current.onBackground)
val Muted: Color @Composable @ReadOnlyComposable get() = Color(LocalFleetPalette.current.muted)
val Danger: Color @Composable @ReadOnlyComposable get() = Color(LocalFleetPalette.current.danger)
val Warn: Color @Composable @ReadOnlyComposable get() = Color(LocalFleetPalette.current.stale)

/** Which popup sits above (or below, for a top bar) the taskbar. */
sealed interface Popup {
    data object None : Popup
    data object Start : Popup
    data object Quick : Popup
    data class AppMenu(val app: AppEntry) : Popup
}

@Composable
fun AppIcon(app: AppEntry, size: Int = 36) {
    val bmp = remember(app.packageName) { IconCache.get(app) }
    Image(bmp, contentDescription = app.label, modifier = Modifier.size(size.dp))
}

// ---------------------------------------------------------------- bar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskbarBar(
    prefs: TaskbarPrefs,
    pinned: List<AppEntry>,
    recent: List<AppEntry>,
    popup: Popup,
    battery: BatteryInfo,
    onStart: () -> Unit,
    onHome: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    onTray: () -> Unit,
    onCollapse: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val size = prefs.size
    val shape = if (prefs.position == BarPosition.BOTTOM) RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
    else RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)
    Row(
        Modifier.fillMaxWidth().height(size.heightDp.dp)
            .clip(shape).background(BarBg.copy(alpha = prefs.opacity))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val btn = (size.iconDp + 4).dp
        Box(
            Modifier.size(btn).clip(CircleShape)
                .background(Brush.linearGradient(listOf(NeonCyan, NeonMagenta)))
                .clickable { onStart() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (popup == Popup.Start) Icons.Filled.Close else Icons.Filled.Menu,
                contentDescription = "Start menu", tint = Color.Black, modifier = Modifier.size((size.iconDp * 0.6f).dp),
            )
        }
        BarIconButton(Icons.Filled.Home, "Home", size.iconDp, onHome)
        Spacer(Modifier.width(4.dp))
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pinned.forEach { app ->
                BarApp(app, size.iconDp, highlighted = (popup as? Popup.AppMenu)?.app?.packageName == app.packageName,
                    onClick = { onLaunch(app) },
                    onLongClick = { if (prefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress); onAppMenu(app) })
            }
            val extra = recent.filter { r -> pinned.none { it.packageName == r.packageName } }
            if (pinned.isNotEmpty() && extra.isNotEmpty()) {
                Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height((size.iconDp - 6).dp).background(Muted.copy(alpha = 0.6f)))
            }
            extra.forEach { app ->
                BarApp(app, size.iconDp - 4, highlighted = false, dim = true,
                    onClick = { onLaunch(app) },
                    onLongClick = { if (prefs.haptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress); onAppMenu(app) })
            }
            if (pinned.isEmpty() && extra.isEmpty()) {
                Text("Long-press an app in the start menu to pin it", color = Muted, fontSize = 12.sp, maxLines = 1)
            }
        }
        Tray(prefs, battery, active = popup == Popup.Quick, onClick = onTray)
        BarIconButton(
            if (prefs.position == BarPosition.BOTTOM) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            "Hide taskbar", size.iconDp - 6, onCollapse,
        )
    }
}

@Composable
private fun BarIconButton(icon: ImageVector, desc: String, iconDp: Int, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = 4.dp).size((iconDp + 4).dp).clip(RoundedCornerShape(10.dp)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = OnBar, modifier = Modifier.size((iconDp * 0.7f).dp)) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BarApp(app: AppEntry, iconDp: Int, highlighted: Boolean, dim: Boolean = false, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (highlighted) NeonCyan.copy(alpha = 0.18f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { alpha = if (dim) 0.8f else 1f }) { AppIcon(app, iconDp) }
    }
}

@Composable
fun Tray(prefs: TaskbarPrefs, battery: BatteryInfo, active: Boolean, onClick: () -> Unit) {
    val time = rememberClock(prefs.use24h, prefs.showSeconds, "time")
    val date = rememberClock(prefs.use24h, false, "date")
    Row(
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (active) NeonCyan.copy(alpha = 0.18f) else Color.Transparent)
            .clickable { onClick() }.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BatteryGlyph(battery, prefs.showBatteryPercent)
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(time, color = OnBar, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (prefs.showDate && prefs.size.heightDp >= 56) Text(date, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
fun BatteryGlyph(b: BatteryInfo, showPercent: Boolean) {
    val color = when {
        b.charging -> NeonCyan
        b.percent in 0..15 -> Danger
        b.percent in 16..30 -> Warn
        else -> OnBar
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 22.dp, height = 11.dp).border(1.5.dp, color, RoundedCornerShape(3.dp)).padding(2.dp)) {
            val frac = (b.percent.coerceIn(0, 100) / 100f).coerceAtLeast(0.05f)
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(color, RoundedCornerShape(1.dp)))
        }
        Box(Modifier.size(width = 2.dp, height = 5.dp).background(color))
        if (showPercent) {
            Spacer(Modifier.width(4.dp))
            Text(if (b.percent < 0) "--" else "${b.percent}%" + if (b.charging) "⚡" else "", color = color, fontSize = 12.sp)
        }
    }
}

/** Ticks once per second when seconds are shown, otherwise on the minute boundary. */
@Composable
fun rememberClock(use24h: Boolean, seconds: Boolean, kind: String): String {
    val pattern = when (kind) {
        "date" -> "EEE d MMM"
        "long" -> "EEEE, d MMMM yyyy"
        else -> buildString {
            append(if (use24h) "HH:mm" else "h:mm")
            if (seconds) append(":ss")
            if (!use24h) append(" a")
        }
    }
    val fmt = remember(pattern) { SimpleDateFormat(pattern, Locale.getDefault()) }
    var text by remember(pattern) { mutableStateOf(fmt.format(Date())) }
    LaunchedEffect(pattern) {
        while (true) {
            text = fmt.format(Date())
            val now = System.currentTimeMillis()
            delay(if (seconds) 1000 - now % 1000 else 60_000 - now % 60_000)
        }
    }
    return text
}

/** Thin pill shown when the bar is hidden; tap to bring the taskbar back. */
@Composable
fun CollapsedHandle(position: BarPosition, onExpand: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            .size(width = 64.dp, height = 22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(Brush.horizontalGradient(listOf(NeonCyan.copy(alpha = 0.85f), NeonMagenta.copy(alpha = 0.85f))))
            .clickable { onExpand() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (position == BarPosition.BOTTOM) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
            "Show taskbar", tint = Color.Black, modifier = Modifier.size(18.dp),
        )
    }
}

// ---------------------------------------------------------------- popups

@Composable
fun PopupHost(visible: Boolean, top: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { if (top) -it / 4 else it / 4 },
        exit = fadeOut() + slideOutVertically { if (top) -it / 4 else it / 4 },
    ) { content() }
}

@Composable
private fun PanelSurface(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val screenW = LocalConfiguration.current.screenWidthDp
    Column(
        modifier.padding(8.dp).widthIn(max = minOf(440, screenW - 16).dp)
            .clip(RoundedCornerShape(18.dp)).background(PanelBg.copy(alpha = 0.98f))
            .border(1.dp, Brush.linearGradient(listOf(NeonCyan.copy(alpha = 0.5f), NeonMagenta.copy(alpha = 0.5f))), RoundedCornerShape(18.dp))
            .padding(14.dp),
        content = content,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StartMenu(
    apps: List<AppEntry>,
    pinned: List<AppEntry>,
    recent: List<AppEntry>,
    grid: Boolean,
    loading: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    onToggleGrid: () -> Unit,
    onOpenSettings: () -> Unit,
    onSystemSettings: () -> Unit,
    onRefresh: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query, apps) { TaskbarLogic.rank(apps, query, { it.label }, { it.packageName }) }
    val focus = remember { FocusRequester() }
    val haptic = LocalHapticFeedback.current
    val screenH = LocalConfiguration.current.screenHeightDp
    PanelSurface(Modifier.heightIn(max = (screenH * 0.72f).dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(rememberClock(true, false, "long"), color = Muted, fontSize = 12.sp)
                Text("${apps.size} apps", color = OnBar, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Reload app list", tint = Muted) }
            IconButton(onClick = onToggleGrid) {
                Icon(if (grid) Icons.Filled.List else Icons.Filled.Apps, if (grid) "List view" else "Grid view", tint = Muted)
            }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = NeonCyan) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Clear", tint = Muted) } },
            placeholder = { Text("Search apps — Enter opens the top hit", color = Muted) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { results.firstOrNull()?.let(onLaunch) }),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = OnBar, unfocusedTextColor = OnBar, cursorColor = NeonCyan,
                focusedBorderColor = NeonCyan, unfocusedBorderColor = Muted.copy(alpha = 0.5f),
            ),
        )
        Spacer(Modifier.height(10.dp))

        val longPress: (AppEntry) -> Unit = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onAppMenu(it) }
        val showSections = query.isBlank()
        Box(Modifier.weight(1f, fill = false)) {
            when {
                loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonCyan)
                }
                results.isEmpty() -> Text("No app matches \"$query\"", color = Muted, modifier = Modifier.padding(16.dp))
                grid -> LazyVerticalGrid(GridCells.Adaptive(76.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (showSections && pinned.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Pinned") }
                        items(pinned, key = { "p:" + it.packageName }) { GridApp(it, onLaunch, longPress) }
                    }
                    if (showSections && recent.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Recent") }
                        items(recent, key = { "r:" + it.packageName }) { GridApp(it, onLaunch, longPress) }
                    }
                    if (showSections) item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("All apps") }
                    items(results, key = { "a:" + it.packageName }) { GridApp(it, onLaunch, longPress) }
                }
                else -> LazyColumn {
                    var last: Char? = null
                    if (showSections && pinned.isNotEmpty()) {
                        item { SectionLabel("Pinned") }
                        items(pinned, key = { "p:" + it.packageName }) { ListApp(it, onLaunch, longPress) }
                    }
                    results.forEach { app ->
                        val b = TaskbarLogic.bucket(app.label)
                        if (showSections && b != last) { last = b; item(key = "h:$b") { SectionLabel(b.toString()) } }
                        item(key = "a:" + app.packageName) { ListApp(app, onLaunch, longPress) }
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Muted.copy(alpha = 0.25f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = onOpenSettings, label = { Text("Taskbar settings", color = OnBar) },
                leadingIcon = { Icon(Icons.Filled.Settings, null, tint = NeonCyan) })
            AssistChip(onClick = onSystemSettings, label = { Text("Android settings", color = OnBar) },
                leadingIcon = { Icon(Icons.Filled.Build, null, tint = NeonMagenta) })
        }
    }
}

@Composable
private fun SectionLabel(text: String) =
    Text(text.uppercase(), color = NeonMagenta, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp))

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridApp(app: AppEntry, onLaunch: (AppEntry) -> Unit, onLong: (AppEntry) -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onLong(app) })
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app, 44)
        Spacer(Modifier.height(4.dp))
        Text(app.label, color = OnBar, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListApp(app: AppEntry, onLaunch: (AppEntry) -> Unit, onLong: (AppEntry) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = { onLaunch(app) }, onLongClick = { onLong(app) })
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app, 32)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, color = OnBar, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Long-press menu for an app (bar or start menu). */
@Composable
fun AppActionsMenu(
    app: AppEntry,
    isPinned: Boolean,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    onOpen: () -> Unit,
    onSplit: () -> Unit,
    onWindow: () -> Unit,
    onTogglePin: () -> Unit,
    onMove: (Int) -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    PanelSurface(Modifier.width(300.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 40)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(app.label, color = OnBar, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(8.dp))
        MenuRow(Icons.Filled.PlayArrow, "Open", onClick = onOpen)
        MenuRow(Icons.Filled.ViewAgenda, "Open in split screen", "If your device supports it", onSplit)
        MenuRow(Icons.Filled.OpenInNew, "Open in floating window", "Needs freeform windows enabled", onWindow)
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Muted.copy(alpha = 0.25f))
        MenuRow(if (isPinned) Icons.Filled.Star else Icons.Filled.StarBorder, if (isPinned) "Unpin from taskbar" else "Pin to taskbar", onClick = onTogglePin)
        if (isPinned) Row {
            TextButton(enabled = canMoveLeft, onClick = { onMove(-1) }) {
                Icon(Icons.Filled.KeyboardArrowLeft, null); Text("Move left")
            }
            TextButton(enabled = canMoveRight, onClick = { onMove(1) }) {
                Text("Move right"); Icon(Icons.Filled.KeyboardArrowRight, null)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = Muted.copy(alpha = 0.25f))
        MenuRow(Icons.Filled.Info, "App info", onClick = onInfo)
        MenuRow(Icons.Filled.Delete, "Uninstall", tint = Danger, onClick = onUninstall)
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, hint: String? = null, onClick: () -> Unit, tint: Color = NeonCyan) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onClick() }.padding(vertical = 9.dp, horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = OnBar, fontSize = 14.sp)
            if (hint != null) Text(hint, color = Muted, fontSize = 11.sp)
        }
    }
}

// ---------------------------------------------------------------- quick panel

data class QuickActions(
    val onInternet: () -> Unit,
    val onBluetooth: () -> Unit,
    val onTorch: () -> Unit,
    val onVolumePanel: () -> Unit,
    val onVolume: (Int) -> Unit,
    val onMute: () -> Unit,
    val onMedia: (Int) -> Unit,
    val onDisplay: () -> Unit,
    val onBattery: () -> Unit,
    val onSettings: () -> Unit,
    val onTaskbarSettings: () -> Unit,
    val onStopTaskbar: () -> Unit,
)

@Composable
fun QuickPanel(battery: BatteryInfo, torchOn: Boolean, hasTorch: Boolean, a: QuickActions) {
    PanelSurface(Modifier.width(340.dp)) {
        Text(rememberClock(true, false, "long"), color = OnBar, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            BatteryGlyph(battery, true)
            Spacer(Modifier.width(8.dp))
            Text(
                buildString {
                    append(if (battery.charging) "Charging" else "On battery")
                    battery.tempC?.let { append(" · %.1f°C".format(it)) }
                },
                color = Muted, fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Internet", Icons.Filled.Language, false, Modifier.weight(1f), a.onInternet)
            Tile("Bluetooth", Icons.Filled.Share, false, Modifier.weight(1f), a.onBluetooth)
            Tile(if (torchOn) "Torch on" else "Torch", Icons.Filled.FlashOn, torchOn, Modifier.weight(1f), a.onTorch, enabled = hasTorch)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Display", Icons.Filled.Brightness6, false, Modifier.weight(1f), a.onDisplay)
            Tile("Battery", Icons.Filled.BatteryFull, false, Modifier.weight(1f), a.onBattery)
            Tile("Settings", Icons.Filled.Settings, false, Modifier.weight(1f), a.onSettings)
        }
        Spacer(Modifier.height(12.dp))
        SectionLabel("Sound")
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundBtn(Icons.Filled.VolumeOff, "Mute", onClick = a.onMute)
            RoundBtn(Icons.Filled.VolumeDown, "Volume down") { a.onVolume(-1) }
            RoundBtn(Icons.Filled.VolumeUp, "Volume up") { a.onVolume(1) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = a.onVolumePanel) { Text("Mixer", color = NeonCyan) }
        }
        SectionLabel("Media")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            RoundBtn(Icons.Filled.SkipPrevious, "Previous") { a.onMedia(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
            RoundBtn(Icons.Filled.PlayArrow, "Play / pause", true) { a.onMedia(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
            RoundBtn(Icons.Filled.SkipNext, "Next") { a.onMedia(KeyEvent.KEYCODE_MEDIA_NEXT) }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Muted.copy(alpha = 0.25f))
        Row {
            TextButton(onClick = a.onTaskbarSettings) { Text("Taskbar settings", color = NeonCyan) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = a.onStopTaskbar) { Text("Stop taskbar", color = Danger) }
        }
    }
}

@Composable
private fun Tile(label: String, icon: ImageVector, on: Boolean, modifier: Modifier, onClick: () -> Unit, enabled: Boolean = true) {
    Column(
        modifier.height(72.dp).clip(RoundedCornerShape(14.dp))
            .background(if (on) NeonCyan else Muted.copy(alpha = 0.15f))
            .clickable(enabled = enabled) { onClick() }.padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        val fg = if (on) Color.Black else if (enabled) OnBar else Muted.copy(alpha = 0.5f)
        Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        Text(label, color = fg, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun RoundBtn(icon: ImageVector, desc: String, big: Boolean = false, onClick: () -> Unit) {
    val s = if (big) 52.dp else 42.dp
    Box(
        Modifier.padding(4.dp).size(s).clip(CircleShape)
            .background(if (big) NeonCyan else Muted.copy(alpha = 0.15f)).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, desc, tint = if (big) Color.Black else OnBar) }
}
