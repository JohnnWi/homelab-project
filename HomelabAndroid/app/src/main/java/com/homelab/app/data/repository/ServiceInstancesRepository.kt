package com.homelab.app.data.repository

import com.homelab.app.data.local.SettingsManager
import com.homelab.app.data.local.dao.ServiceInstanceDao
import com.homelab.app.data.local.entity.ServiceInstanceEntity
import com.homelab.app.domain.model.PiHoleAuthMode
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ServiceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceInstancesRepository @Inject constructor(
    private val dao: ServiceInstanceDao,
    private val settingsManager: SettingsManager
) {
    val allInstances: Flow<List<ServiceInstance>> = dao.observeAll().map { entities ->
        entities.map { it.toDomain() }
    }

    val instancesByType: Flow<Map<ServiceType, List<ServiceInstance>>> = allInstances.map { instances ->
        ServiceType.entries
            .filter { it != ServiceType.UNKNOWN }
            .associateWith { type -> instances.filter { it.type == type } }
    }

    val preferredInstanceIdByType: Flow<Map<ServiceType, String?>> = settingsManager.preferredInstanceIds

    val preferredInstancesByType: Flow<Map<ServiceType, ServiceInstance?>> = combine(
        instancesByType,
        preferredInstanceIdByType
    ) { grouped, preferredIds ->
        ServiceType.entries
            .filter { it != ServiceType.UNKNOWN }
            .associateWith { type ->
                val instances = grouped[type].orEmpty()
                val preferredId = preferredIds[type]
                instances.firstOrNull { it.id == preferredId } ?: instances.firstOrNull()
            }
    }

    suspend fun initialize() {
        migrateLegacyDataIfNeeded()
        normalizeStoredInstancesIfNeeded()
        repairAllPreferredInstances()
    }

    suspend fun getInstance(id: String): ServiceInstance? = dao.getById(id)?.toDomain()

    suspend fun getAllInstances(): List<ServiceInstance> {
        return dao.getAll().map { it.toDomain() }
    }

    suspend fun getInstances(type: ServiceType): List<ServiceInstance> {
        return dao.getByType(type.name).map { it.toDomain() }
    }

    suspend fun getPreferredInstance(type: ServiceType): ServiceInstance? {
        val instances = getInstances(type)
        val preferredId = settingsManager.preferredInstanceId(type).first()
        val preferred = instances.firstOrNull { it.id == preferredId } ?: instances.firstOrNull()
        if (preferred?.id != preferredId) {
            settingsManager.setPreferredInstanceId(type, preferred?.id)
        }
        return preferred
    }

    suspend fun saveInstance(instance: ServiceInstance) {
        val normalized = normalizeInstance(instance)
        dao.upsert(normalized.toEntity())
        val currentPreferred = settingsManager.preferredInstanceId(normalized.type).first()
        if (currentPreferred.isNullOrBlank()) {
            settingsManager.setPreferredInstanceId(normalized.type, normalized.id)
        }
    }

    suspend fun deleteInstance(id: String) {
        val instance = getInstance(id) ?: return
        dao.deleteById(id)
        repairPreferredInstance(instance.type)
    }

    suspend fun setPreferredInstance(type: ServiceType, instanceId: String?) {
        val validId = instanceId?.takeIf { candidate ->
            dao.getById(candidate)?.type == type.name
        }
        settingsManager.setPreferredInstanceId(type, validId)
        if (validId == null) {
            repairPreferredInstance(type)
        }
    }

    suspend fun migrateLegacyDataIfNeeded() {
        if (settingsManager.serviceInstancesMigrated.first()) {
            return
        }

        ServiceType.entries
            .filter { it != ServiceType.UNKNOWN }
            .forEach { type ->
                val existing = dao.getByType(type.name)
                val legacy = settingsManager.getLegacyConnection(type)

                if (legacy != null && existing.isEmpty()) {
                    val migrated = normalizeInstance(legacy.migratedInstance(UUID.randomUUID().toString()))
                    dao.upsert(migrated.toEntity())
                    settingsManager.setPreferredInstanceId(type, migrated.id)
                } else if (existing.isNotEmpty()) {
                    val currentPreferred = settingsManager.preferredInstanceId(type).first()
                    if (currentPreferred.isNullOrBlank()) {
                        settingsManager.setPreferredInstanceId(type, existing.first().id)
                    }
                }

                settingsManager.removeLegacyConnection(type)
            }

        settingsManager.setServiceInstancesMigrated(true)
    }

    private suspend fun normalizeStoredInstancesIfNeeded() {
        val entities = dao.getAll()
        if (entities.isEmpty()) return

        val normalized = entities.map { entity ->
            val normalizedUrl = normalizeUrl(entity.url)
            val normalizedFallback = normalizeOptionalUrl(entity.fallbackUrl)
            if (normalizedUrl == entity.url && normalizedFallback == entity.fallbackUrl) {
                entity
            } else {
                entity.copy(url = normalizedUrl, fallbackUrl = normalizedFallback)
            }
        }

        if (normalized != entities) {
            dao.upsertAll(normalized)
        }
    }

    suspend fun repairAllPreferredInstances() {
        ServiceType.entries
            .filter { it != ServiceType.UNKNOWN }
            .forEach { repairPreferredInstance(it) }
    }

    suspend fun repairPreferredInstance(type: ServiceType) {
        val instances = getInstances(type)
        val currentPreferred = settingsManager.preferredInstanceId(type).first()
        val repaired = instances.firstOrNull { it.id == currentPreferred } ?: instances.firstOrNull()
        settingsManager.setPreferredInstanceId(type, repaired?.id)
    }
}

private fun ServiceInstanceEntity.toDomain(): ServiceInstance {
    return ServiceInstance(
        id = id,
        type = ServiceType.valueOf(type),
        label = label,
        url = url,
        token = token,
        username = username,
        apiKey = apiKey,
        piholePassword = piholePassword,
        piholeAuthMode = piholeAuthMode?.let(PiHoleAuthMode::valueOf),
        fallbackUrl = fallbackUrl,
        allowSelfSigned = allowSelfSigned,
        password = password
    )
}

private fun ServiceInstance.toEntity(): ServiceInstanceEntity {
    return ServiceInstanceEntity(
        id = id,
        type = type.name,
        label = label.ifBlank { type.displayName },
        url = url,
        token = token,
        username = username,
        apiKey = apiKey,
        piholePassword = piholePassword,
        piholeAuthMode = piholeAuthMode?.name,
        fallbackUrl = fallbackUrl,
        allowSelfSigned = allowSelfSigned,
        password = password
    )
}

private fun normalizeUrl(raw: String): String {
    var clean = raw.trim()
    clean = clean.trimEnd { it == ')' || it == ']' || it == '}' || it == ',' || it == ';' }
    if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
        clean = "https://$clean"
    }
    return clean.replace(Regex("/+$"), "")
}

private fun normalizeOptionalUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val normalized = normalizeUrl(raw)
    return normalized.ifBlank { null }
}

private fun normalizeInstance(instance: ServiceInstance): ServiceInstance {
    val normalizedUrl = normalizeUrl(instance.url)
    val normalizedFallback = normalizeOptionalUrl(instance.fallbackUrl)
    if (normalizedUrl == instance.url && normalizedFallback == instance.fallbackUrl) {
        return instance
    }
    return instance.copy(url = normalizedUrl, fallbackUrl = normalizedFallback)
}
