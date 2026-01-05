package com.example.aialarmclock.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

class UserPreferences(private val context: Context) {

    private val apiKeyKey = stringPreferencesKey("claude_api_key")
    private val defaultThemeKey = stringPreferencesKey("default_theme")

    val apiKey: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[apiKeyKey]
    }

    val defaultTheme: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[defaultThemeKey] ?: DEFAULT_THEME
    }

    suspend fun saveApiKey(apiKey: String) {
        context.dataStore.edit { preferences ->
            preferences[apiKeyKey] = apiKey
        }
    }

    suspend fun saveDefaultTheme(theme: String) {
        context.dataStore.edit { preferences ->
            preferences[defaultThemeKey] = theme
        }
    }

    suspend fun clearApiKey() {
        context.dataStore.edit { preferences ->
            preferences.remove(apiKeyKey)
        }
    }

    companion object {
        const val DEFAULT_THEME = "morning reflection and gratitude"
    }
}
