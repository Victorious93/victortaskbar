package com.victorious.taskbar.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val LocalFleetPalette = staticCompositionLocalOf { BuiltInTheme.NEON }

/** Applies the live fleet palette (or the built-in neon fallback) to Material3 + [LocalFleetPalette]. */
@Composable
fun TaskbarTheme(content: @Composable () -> Unit) {
    val state by FleetThemeClient.state.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val settings = androidx.compose.runtime.remember { com.victorious.taskbar.data.TaskbarSettings(ctx) }
    val prefs by settings.prefs.collectAsState(initial = com.victorious.taskbar.data.TaskbarPrefs())
    val p = if (prefs.highContrast) state.palette.highContrast() else state.palette
    val bg = Color(p.background)
    val base = if (bg.luminance() > 0.5f) lightColorScheme() else darkColorScheme()
    val scheme = base.copy(
        primary = Color(p.primary), secondary = Color(p.secondary),
        background = bg, surface = Color(p.surface),
        onBackground = Color(p.onBackground), onSurface = Color(p.onBackground),
        error = Color(p.danger), outline = Color(p.muted),
    )
    val density = androidx.compose.ui.platform.LocalDensity.current
    CompositionLocalProvider(
        LocalFleetPalette provides p,
        androidx.compose.ui.platform.LocalDensity provides
            androidx.compose.ui.unit.Density(density.density, density.fontScale * prefs.textScale),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Opaque black/white surfaces and brighter text; accents kept. */
fun FleetPalette.highContrast(): FleetPalette {
    val light = Color(background).luminance() > 0.5f
    return if (light) copy(background = 0xFFFFFFFF.toInt(), surface = 0xFFFFFFFF.toInt(), tile = 0xFFF0F0F0.toInt(),
        onBackground = 0xFF000000.toInt(), muted = 0xFF303030.toInt())
    else copy(background = 0xFF000000.toInt(), surface = 0xFF000000.toInt(), tile = 0xFF000000.toInt(),
        onBackground = 0xFFFFFFFF.toInt(), muted = 0xFFE6E6E6.toInt())
}
