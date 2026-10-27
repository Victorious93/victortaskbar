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
    val p = state.palette
    val bg = Color(p.background)
    val base = if (bg.luminance() > 0.5f) lightColorScheme() else darkColorScheme()
    val scheme = base.copy(
        primary = Color(p.primary), secondary = Color(p.secondary),
        background = bg, surface = Color(p.surface),
        onBackground = Color(p.onBackground), onSurface = Color(p.onBackground),
        error = Color(p.danger), outline = Color(p.muted),
    )
    CompositionLocalProvider(LocalFleetPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
