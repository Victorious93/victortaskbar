package com.victorious.taskbar.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Pinned apps persisted as an ordered, comma-separated list of package names. */
class PinnedAppsStore(private val context: Context) {
    private val key = stringPreferencesKey("pinned_packages")

    private fun decode(raw: String?) = raw?.split(',')?.filter { it.isNotBlank() } ?: emptyList()

    val pinned: Flow<List<String>> = context.dataStore.data.map { decode(it[key]) }

    suspend fun pin(pkg: String) = context.dataStore.edit { prefs ->
        val cur = decode(prefs[key])
        if (pkg !in cur) prefs[key] = (cur + pkg).joinToString(",")
    }

    suspend fun unpin(pkg: String) = context.dataStore.edit { prefs ->
        prefs[key] = (decode(prefs[key]) - pkg).joinToString(",")
    }

    suspend fun toggle(pkg: String) = context.dataStore.edit { prefs ->
        val cur = decode(prefs[key])
        prefs[key] = (if (pkg in cur) cur - pkg else cur + pkg).joinToString(",")
    }

    /** Moves [pkg] by [delta] positions (negative = left). No-op if not pinned. */
    suspend fun move(pkg: String, delta: Int) = context.dataStore.edit { prefs ->
        prefs[key] = TaskbarLogic.move(decode(prefs[key]), pkg, delta).joinToString(",")
    }
}
