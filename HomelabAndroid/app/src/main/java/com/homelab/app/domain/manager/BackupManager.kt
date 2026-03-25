package com.homelab.app.domain.manager

import com.homelab.app.BuildConfig
import com.homelab.app.data.repository.ServiceInstancesRepository
import com.homelab.app.domain.model.BackupEnvelope
import com.homelab.app.domain.model.BackupServiceTypeMapper
import com.homelab.app.domain.model.toBackupEntry
import com.homelab.app.domain.model.toServiceInstance
import com.homelab.app.util.BackupCrypto
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupManager @Inject constructor(
    private val repository: ServiceInstancesRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    data class PreviewInfo(
        val totalFound: Int,
        val unknownCount: Int,
        val envelope: BackupEnvelope
    )

    suspend fun exportBackup(password: String): ByteArray {
        val instances = repository.getAllInstances()
        val preferredIdsByType = repository.preferredInstanceIdByType.first()

        val entries = instances.map { instance ->
            val isPref = preferredIdsByType[instance.type] == instance.id
            instance.toBackupEntry(isPreferred = isPref ?: false)
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val exportedAt = dateFormat.format(Date())

        val envelope = BackupEnvelope(
            version = BackupEnvelope.CURRENT_VERSION,
            exportedAt = exportedAt,
            appVersion = BuildConfig.VERSION_NAME,
            services = entries
        )

        val jsonString = json.encodeToString(envelope)
        val data = jsonString.toByteArray(Charsets.UTF_8)

        return BackupCrypto.encrypt(data, password)
    }

    fun decryptAndPreview(data: ByteArray, password: String): PreviewInfo {
        val decryptedData = BackupCrypto.decrypt(data, password)
        val jsonString = String(decryptedData, Charsets.UTF_8)
        val envelope = json.decodeFromString<BackupEnvelope>(jsonString)

        val knownCount = envelope.services.count { BackupServiceTypeMapper.serviceType(it.type) != null }
        val unknownCount = envelope.services.size - knownCount

        return PreviewInfo(
            totalFound = envelope.services.size,
            unknownCount = unknownCount,
            envelope = envelope
        )
    }

    suspend fun applyBackup(envelope: BackupEnvelope) {
        // Delete all existing instances
        val existing = repository.getAllInstances()
        for (instance in existing) {
            repository.deleteInstance(instance.id)
        }

        // Add new ones
        for (entry in envelope.services) {
            val validInstance = entry.toServiceInstance()
            if (validInstance != null) {
                repository.saveInstance(validInstance)
                if (entry.isPreferred) {
                    repository.setPreferredInstance(validInstance.type, validInstance.id)
                }
            }
        }
    }
}
