package com.example.omnicontrol.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val DARK_THEME = booleanPreferencesKey("dark_theme")
    private val LARGE_BUTTONS = booleanPreferencesKey("large_buttons")
    private val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")

    val darkTheme: Flow<Boolean> = context.dataStore.data.map { it[DARK_THEME] ?: true }
    val largeButtons: Flow<Boolean> = context.dataStore.data.map { it[LARGE_BUTTONS] ?: false }
    val hapticsEnabled: Flow<Boolean> = context.dataStore.data.map { it[HAPTICS_ENABLED] ?: true }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[DARK_THEME] = enabled }
    }

    suspend fun setLargeButtons(enabled: Boolean) {
        context.dataStore.edit { it[LARGE_BUTTONS] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[HAPTICS_ENABLED] = enabled }
    }
}
