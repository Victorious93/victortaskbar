package com.victorious.taskbar.data

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/** Single DataStore instance for the whole app (two delegates on one file would crash). */
internal val Context.dataStore by preferencesDataStore(name = "taskbar_prefs")
