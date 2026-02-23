package com.homelab.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode {
    SYSTEM, LIGHT, DARK;

    companion object {
        fun fromString(value: String?): ThemeMode {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: SYSTEM
        }
    }
}

enum class LanguageMode(val code: String, val flag: String) {
    ITALIAN("it", "🇮🇹"),
    ENGLISH("en", "🇬🇧"),
    FRENCH("fr", "🇫🇷"),
    SPANISH("es", "🇪🇸"),
    GERMAN("de", "🇩🇪");

    companion object {
        fun fromCode(code: String?): LanguageMode {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: ITALIAN
        }
    }
}

@Singleton
class LocalPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    private val THEME_KEY = stringPreferencesKey("theme_mode")
    private val LANG_KEY = stringPreferencesKey("language_mode")

    val themeMode: Flow<ThemeMode> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            ThemeMode.fromString(preferences[THEME_KEY])
        }

    val languageMode: Flow<LanguageMode> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            LanguageMode.fromCode(preferences[LANG_KEY])
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_KEY] = mode.name
        }
    }

    suspend fun setLanguageMode(mode: LanguageMode) {
        dataStore.edit { preferences ->
            preferences[LANG_KEY] = mode.code
        }
    }
}
