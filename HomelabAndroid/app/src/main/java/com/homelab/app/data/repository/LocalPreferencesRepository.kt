package com.homelab.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}

@Singleton
class LocalPreferencesRepository @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    private val THEME_KEY = stringPreferencesKey("theme_mode")
    private val LANG_KEY = stringPreferencesKey("language_mode")
    private val HIDDEN_SERVICES_KEY = stringPreferencesKey("hidden_services")
    private val PIN_KEY = stringPreferencesKey("app_pin")
    private val BIOMETRIC_KEY = booleanPreferencesKey("biometric_enabled")
    private val ONBOARDING_COMPLETED_KEY = booleanPreferencesKey("onboarding_completed")

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

    val hiddenServices: Flow<Set<String>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val raw = preferences[HIDDEN_SERVICES_KEY] ?: ""
            if (raw.isBlank()) emptySet() else raw.split(",").toSet()
        }

    suspend fun toggleServiceVisibility(serviceKey: String) {
        dataStore.edit { preferences ->
            val raw = preferences[HIDDEN_SERVICES_KEY] ?: ""
            val current = if (raw.isBlank()) mutableSetOf() else raw.split(",").toMutableSet()
            if (current.contains(serviceKey)) {
                current.remove(serviceKey)
            } else {
                current.add(serviceKey)
            }
            preferences[HIDDEN_SERVICES_KEY] = current.joinToString(",")
        }
    }

    // PIN & Biometric

    val appPin: Flow<String?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences[PIN_KEY] }

    val biometricEnabled: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences[BIOMETRIC_KEY] ?: false }

    val hasCompletedOnboarding: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences -> preferences[ONBOARDING_COMPLETED_KEY] ?: false }

    suspend fun savePin(pin: String) {
        dataStore.edit { preferences ->
            preferences[PIN_KEY] = pin
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[BIOMETRIC_KEY] = enabled
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[ONBOARDING_COMPLETED_KEY] = completed
        }
    }

    suspend fun clearSecurity() {
        dataStore.edit { preferences ->
            preferences.remove(PIN_KEY)
            preferences.remove(BIOMETRIC_KEY)
        }
    }
}
