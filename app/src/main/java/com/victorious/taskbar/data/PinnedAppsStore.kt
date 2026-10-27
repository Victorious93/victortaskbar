package com.victorious.taskbar.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "taskbar_prefs")

/** Pinned apps persisted as an ordered, comma-separated list of package names. */
class PinnedAppsStore(private val context: Context) {
    private val key = stringPreferencesKey("pinned_packages")

    val pinned: Flow<List<String>> = context.dataStore.data.map { prefs ->
        prefs[key]?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun pin(pkg: String) = context.dataStore.edit { prefs ->
        val cur = prefs[key]?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
        if (pkg !in cur) prefs[key] = (cur + pkg).joinToString(",")
    }

    suspend fun unpin(pkg: String) = context.dataStore.edit { prefs ->
        val cur = prefs[key]?.split(',')?.filter { it.isNotBlank() } ?: emptyList()
        prefs[key] = (cur - pkg).joinToString(",")
    }
}
