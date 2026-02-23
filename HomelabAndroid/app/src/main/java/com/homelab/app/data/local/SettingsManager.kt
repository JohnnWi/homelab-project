package com.homelab.app.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.homelab.app.domain.model.ServiceConnection
import com.homelab.app.util.ServiceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsManager @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    private object Keys {
        val INTERNAL_SSID = stringPreferencesKey("internal_ssid")
        val USE_BIOMETRICS = booleanPreferencesKey("use_biometrics")
        
        fun connectionKey(type: ServiceType) = stringPreferencesKey("connection_${type.name.lowercase()}")
    }

    val internalSsid: Flow<String?> = dataStore.data.map { it[Keys.INTERNAL_SSID] }
    val useBiometrics: Flow<Boolean> = dataStore.data.map { it[Keys.USE_BIOMETRICS] ?: false }

    val allConnections: Flow<Map<ServiceType, ServiceConnection?>> = dataStore.data.map { prefs ->
        ServiceType.entries.filter { it != ServiceType.UNKNOWN }.associateWith { type ->
            prefs[Keys.connectionKey(type)]?.let { jsonString ->
                try {
                    json.decodeFromString<ServiceConnection>(jsonString)
                } catch (e: Exception) {
                    android.util.Log.e("SettingsManager", "Error decoding connection for $type: ${e.message}")
                    null
                }
            }
        }
    }

    fun getConnection(type: ServiceType): Flow<ServiceConnection?> = dataStore.data.map { prefs ->
        prefs[Keys.connectionKey(type)]?.let { jsonString ->
            try {
                json.decodeFromString<ServiceConnection>(jsonString)
            } catch (e: Exception) {
                android.util.Log.e("SettingsManager", "Error decoding connection for $type: ${e.message}")
                null
            }
        }
    }

    suspend fun saveConnection(connection: ServiceConnection) {
        val type = connection.type
        val jsonString = json.encodeToString(connection)
        dataStore.edit { prefs ->
            prefs[Keys.connectionKey(type)] = jsonString
        }
    }

    suspend fun deleteConnection(type: ServiceType) {
        dataStore.edit { prefs ->
            prefs.remove(Keys.connectionKey(type))
        }
    }

    suspend fun setInternalSsid(ssid: String) {
        dataStore.edit { it[Keys.INTERNAL_SSID] = ssid }
    }

    suspend fun setUseBiometrics(use: Boolean) {
        dataStore.edit { it[Keys.USE_BIOMETRICS] = use }
    }
}
