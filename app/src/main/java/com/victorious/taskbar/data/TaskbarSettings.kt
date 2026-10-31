package com.victorious.taskbar.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class BarPosition { BOTTOM, TOP }

enum class BarSize(val label: String, val heightDp: Int, val iconDp: Int) {
    COMPACT("Compact", 44, 28), NORMAL("Normal", 56, 36), LARGE("Large", 68, 44)
}

enum class LaunchMode(val label: String) {
    NORMAL("Full screen"), SPLIT("Split screen"), WINDOW("Floating window")
}

data class TaskbarPrefs(
    val position: BarPosition = BarPosition.BOTTOM,
    val size: BarSize = BarSize.NORMAL,
    val opacity: Float = 0.95f,
    val showDate: Boolean = true,
    val use24h: Boolean = true,
    val showSeconds: Boolean = false,
    val showBatteryPercent: Boolean = true,
    val recentCount: Int = 6,
    val menuGrid: Boolean = true,
    val defaultLaunch: LaunchMode = LaunchMode.NORMAL,
    val startOnBoot: Boolean = false,
    val haptics: Boolean = true,
)

class TaskbarSettings(private val context: Context) {
    private object K {
        val position = stringPreferencesKey("position")
        val size = stringPreferencesKey("size")
        val opacity = floatPreferencesKey("opacity")
        val showDate = booleanPreferencesKey("show_date")
        val use24h = booleanPreferencesKey("use_24h")
        val showSeconds = booleanPreferencesKey("show_seconds")
        val showBatteryPercent = booleanPreferencesKey("show_battery_pct")
        val recentCount = intPreferencesKey("recent_count")
        val menuGrid = booleanPreferencesKey("menu_grid")
        val defaultLaunch = stringPreferencesKey("default_launch")
        val startOnBoot = booleanPreferencesKey("start_on_boot")
        val haptics = booleanPreferencesKey("haptics")
    }

    private inline fun <reified E : Enum<E>> Preferences.enum(k: Preferences.Key<String>, def: E): E =
        this[k]?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: def

    val prefs: Flow<TaskbarPrefs> = context.dataStore.data.map { read(it) }

    private fun read(p: Preferences): TaskbarPrefs {
        val d = TaskbarPrefs()
        return TaskbarPrefs(
            position = p.enum(K.position, d.position),
            size = p.enum(K.size, d.size),
            opacity = (p[K.opacity] ?: d.opacity).coerceIn(0.4f, 1f),
            showDate = p[K.showDate] ?: d.showDate,
            use24h = p[K.use24h] ?: d.use24h,
            showSeconds = p[K.showSeconds] ?: d.showSeconds,
            showBatteryPercent = p[K.showBatteryPercent] ?: d.showBatteryPercent,
            recentCount = (p[K.recentCount] ?: d.recentCount).coerceIn(0, 12),
            menuGrid = p[K.menuGrid] ?: d.menuGrid,
            defaultLaunch = p.enum(K.defaultLaunch, d.defaultLaunch),
            startOnBoot = p[K.startOnBoot] ?: d.startOnBoot,
            haptics = p[K.haptics] ?: d.haptics,
        )
    }

    suspend fun current(): TaskbarPrefs = prefs.first()

    suspend fun update(f: (TaskbarPrefs) -> TaskbarPrefs) = context.dataStore.edit { p ->
        val n = f(read(p))
        p[K.position] = n.position.name
        p[K.size] = n.size.name
        p[K.opacity] = n.opacity
        p[K.showDate] = n.showDate
        p[K.use24h] = n.use24h
        p[K.showSeconds] = n.showSeconds
        p[K.showBatteryPercent] = n.showBatteryPercent
        p[K.recentCount] = n.recentCount
        p[K.menuGrid] = n.menuGrid
        p[K.defaultLaunch] = n.defaultLaunch.name
        p[K.startOnBoot] = n.startOnBoot
        p[K.haptics] = n.haptics
    }
}
