package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.ProxmoxApi
import com.homelab.app.data.remote.dto.proxmox.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProxmoxRepository @Inject constructor(
    private val api: ProxmoxApi
) {
    suspend fun refreshTicket(
        url: String,
        username: String,
        currentTicket: String,
        allowSelfSigned: Boolean = false
    ): ProxmoxAuthTicket {
        val cleanUrl = url.trimEnd('/')
        val body = mapOf(
            "username" to username.trim(),
            "password" to currentTicket
        )
        val response = api.authenticate(
            url = "$cleanUrl/api2/json/access/ticket",
            allowSelfSigned = allowSelfSigned.toString(),
            credentials = body
        )
        return response.data
    }

    suspend fun authenticate(
        url: String,
        username: String,
        password: String,
        otp: String? = null,
        realm: String = "pam",
        allowSelfSigned: Boolean = false
    ): ProxmoxAuthTicket {
        val cleanUrl = url.trimEnd('/')
        val fullUsername = if (username.contains("@")) username else "$username@$realm"
        val body = mutableMapOf(
            "username" to fullUsername,
            "password" to password,
            "new-format" to "1"
        )
        if (!otp.isNullOrBlank()) body["otp"] = otp.trim()

        return try {
            val response = api.authenticate(
                url = "$cleanUrl/api2/json/access/ticket",
                allowSelfSigned = allowSelfSigned.toString(),
                credentials = body
            )
            val ticket = response.data
            val challenge = decodeTfaChallenge(ticket.ticket)
            if (challenge == null) {
                ticket
            } else {
                val secondFactor = otp?.trim().orEmpty()
                if (secondFactor.isBlank()) {
                    if (challenge.requiresWebAuthnOnly) {
                        throw Exception("This Proxmox account requires a WebAuthn or U2F challenge. Use an API token, a recovery key, or a TOTP-enabled account for app access.")
                    }
                    throw Exception("2FA code required. Please enter your TOTP or recovery key.")
                }

                api.authenticate(
                    url = "$cleanUrl/api2/json/access/ticket",
                    allowSelfSigned = allowSelfSigned.toString(),
                    credentials = mapOf(
                        "username" to fullUsername,
                        "password" to "${tfaResponsePrefix(secondFactor, challenge)}:$secondFactor",
                        "tfa-challenge" to ticket.ticket,
                        "new-format" to "1"
                    )
                ).data
            }
        } catch (e: Exception) {
            if (e is retrofit2.HttpException && e.code() == 401) {
                if (!otp.isNullOrBlank()) {
                    throw Exception("The second-factor code was rejected. Verify your TOTP or recovery key and try again.")
                }
                throw Exception("Authentication failed. Check your credentials and realm.")
            }
            throw e
        }
    }

    private data class ParsedTfaChallenge(
        val supportsTotp: Boolean,
        val supportsRecovery: Boolean,
        val requiresWebAuthnOnly: Boolean
    )

    private fun decodeTfaChallenge(ticket: String): ParsedTfaChallenge? {
        val parts = ticket.split(':')
        if (parts.size < 2) return null
        val rawChallenge = parts[1]
        if (!rawChallenge.startsWith("!tfa!")) return null
        val decoded = java.net.URLDecoder.decode(rawChallenge.removePrefix("!tfa!"), Charsets.UTF_8.name())
        val challenge = Json.parseToJsonElement(decoded).jsonObject
        val supportsTotp = challenge["totp"]?.jsonPrimitive?.contentOrNull == "true"
        val recovery = challenge["recovery"]?.jsonPrimitive?.contentOrNull
        val supportsRecovery = !recovery.isNullOrBlank() && recovery.lowercase() != "unavailable"
        val hasWebauthn = challenge["webauthn"] != null
        val hasU2f = challenge["u2f"] != null
        return ParsedTfaChallenge(
            supportsTotp = supportsTotp,
            supportsRecovery = supportsRecovery,
            requiresWebAuthnOnly = !supportsTotp && !supportsRecovery && (hasWebauthn || hasU2f)
        )
    }

    private fun tfaResponsePrefix(secondFactor: String, challenge: ParsedTfaChallenge): String {
        if (challenge.supportsTotp && !challenge.supportsRecovery) {
            return "totp"
        }
        if (challenge.supportsRecovery && !challenge.supportsTotp) {
            return "recovery"
        }
        val trimmed = secondFactor.trim()
        return if (trimmed.all(Char::isDigit) && trimmed.length in 6..8) "totp" else "recovery"
    }

    suspend fun authenticateWithApiToken(
        url: String,
        apiToken: String,
        allowSelfSigned: Boolean = false
    ) {
        val cleanUrl = url.trimEnd('/')
        val normalizedToken = apiToken.trim()
        require(normalizedToken.isNotBlank()) { "Proxmox API token is required" }
        api.validateApiToken(
            url = "$cleanUrl/api2/json/version",
            allowSelfSigned = allowSelfSigned.toString(),
            authorization = "PVEAPIToken=$normalizedToken"
        )
    }

    suspend fun getVersion(instanceId: String): ProxmoxVersion {
        return api.getVersion(instanceId = instanceId).data
    }

    suspend fun getNodes(instanceId: String): List<ProxmoxNode> {
        return api.getNodes(instanceId = instanceId).data
    }

    suspend fun getVMs(instanceId: String, node: String): List<ProxmoxVM> {
        return api.getVMs(instanceId = instanceId, node = node).data.filter { it.template != 1 }
    }

    suspend fun getLXCs(instanceId: String, node: String): List<ProxmoxLXC> {
        return api.getLXCs(instanceId = instanceId, node = node).data.filter { it.template != 1 }
    }

    suspend fun getStorage(instanceId: String, node: String): List<ProxmoxStorage> {
        return api.getStorage(instanceId = instanceId, node = node).data
    }

    suspend fun getTasks(instanceId: String, node: String, limit: Int = 20): List<ProxmoxTask> {
        return api.getTasks(instanceId = instanceId, node = node, limit = limit).data
    }

    suspend fun getTaskLog(instanceId: String, node: String, upid: String, limit: Int = 100): List<ProxmoxTaskLogEntry> {
        return api.getTaskLog(instanceId = instanceId, node = node, upid = upid, limit = limit).data
    }

    suspend fun getTaskStatus(instanceId: String, node: String, upid: String): ProxmoxTask {
        return api.getTaskStatus(instanceId = instanceId, node = node, upid = upid).data
    }

    suspend fun getPools(instanceId: String): List<ProxmoxPool> {
        return api.getPools(instanceId = instanceId).data
    }

    suspend fun getPoolMembers(instanceId: String, poolId: String): ProxmoxPoolDetail {
        return api.getPoolMembers(instanceId = instanceId, poolid = poolId).data
    }

    // MARK: - Snapshots

    suspend fun createSnapshot(instanceId: String, node: String, vmid: Int, isQemu: Boolean, snapname: String, description: String = ""): String {
        val body = mutableMapOf("snapname" to snapname)
        if (description.isNotBlank()) body["description"] = description
        return if (isQemu) {
            api.createVMSnapshot(instanceId = instanceId, node = node, vmid = vmid, body = body).data
        } else {
            api.createLXCSnapshot(instanceId = instanceId, node = node, vmid = vmid, body = body).data
        }
    }

    suspend fun deleteSnapshot(instanceId: String, node: String, vmid: Int, isQemu: Boolean, snapname: String) {
        if (isQemu) {
            api.deleteVMSnapshot(instanceId = instanceId, node = node, vmid = vmid, snapname = snapname)
        } else {
            api.deleteLXCSnapshot(instanceId = instanceId, node = node, vmid = vmid, snapname = snapname)
        }
    }

    suspend fun rollbackSnapshot(instanceId: String, node: String, vmid: Int, isQemu: Boolean, snapname: String) {
        if (isQemu) {
            api.rollbackVMSnapshot(instanceId = instanceId, node = node, vmid = vmid, snapname = snapname)
        } else {
            api.rollbackLXCSnapshot(instanceId = instanceId, node = node, vmid = vmid, snapname = snapname)
        }
    }

    suspend fun getClusterResources(instanceId: String): List<ProxmoxClusterResource> {
        return api.getClusterResources(instanceId = instanceId).data
    }

    suspend fun getBackupJobs(instanceId: String): List<ProxmoxBackupJob> {
        return api.getBackupJobs(instanceId = instanceId).data
    }

    suspend fun triggerBackupJob(instanceId: String, jobId: String): String {
        return api.triggerBackupJob(instanceId = instanceId, id = jobId).data
    }

    suspend fun getClusterFirewallRules(instanceId: String): List<ProxmoxFirewallRule> {
        return api.getClusterFirewallRules(instanceId = instanceId).data
    }

    suspend fun getClusterFirewallOptions(instanceId: String): ProxmoxFirewallOptions {
        return api.getClusterFirewallOptions(instanceId = instanceId).data
    }

    suspend fun updateClusterFirewallOptions(instanceId: String, enable: Boolean) {
        api.updateClusterFirewallOptions(instanceId = instanceId, body = mapOf("enable" to if (enable) "1" else "0"))
    }

    // MARK: - Storage Content Actions

    suspend fun deleteStorageContent(instanceId: String, node: String, storage: String, volume: String) {
        api.deleteStorageContent(instanceId = instanceId, node = node, storage = storage, volume = volume)
    }

    // MARK: - APT Updates

    suspend fun getAptUpdates(instanceId: String, node: String): List<ProxmoxAptPackage> {
        return api.getAptUpdates(instanceId = instanceId, node = node).data
    }

    // MARK: - HA Resources

    suspend fun getHAResources(instanceId: String): List<ProxmoxHAResource> {
        return api.getHAResources(instanceId = instanceId).data
    }

    suspend fun getHAGroups(instanceId: String): List<ProxmoxHAGroup> {
        return api.getHAGroups(instanceId = instanceId).data
    }

    // MARK: - Ceph

    suspend fun getCephStatus(instanceId: String, node: String): ProxmoxCephStatus {
        return api.getCephStatus(instanceId = instanceId, node = node).data
    }

    // MARK: - Replication

    suspend fun getReplicationJobs(instanceId: String): List<ProxmoxReplicationJob> {
        return api.getReplicationJobs(instanceId = instanceId).data
    }

    suspend fun triggerReplicationJob(instanceId: String, id: String): String {
        return api.triggerReplicationJob(instanceId = instanceId, id = id).data
    }

    // MARK: - Clone & Migrate

    suspend fun cloneVM(instanceId: String, node: String, vmid: Int, body: Map<String, String>): String {
        return api.cloneVM(instanceId = instanceId, node = node, vmid = vmid, body = body).data
    }

    suspend fun migrateVM(instanceId: String, node: String, vmid: Int, body: Map<String, String>): String {
        return api.migrateVM(instanceId = instanceId, node = node, vmid = vmid, body = body).data
    }

    suspend fun cloneLXC(instanceId: String, node: String, vmid: Int, body: Map<String, String>): String {
        return api.cloneLXC(instanceId = instanceId, node = node, vmid = vmid, body = body).data
    }

    suspend fun migrateLXC(instanceId: String, node: String, vmid: Int, body: Map<String, String>): String {
        return api.migrateLXC(instanceId = instanceId, node = node, vmid = vmid, body = body).data
    }

    // MARK: - Details

    suspend fun getNodeStatus(instanceId: String, node: String): ProxmoxNodeStatus {
        return api.getNodeStatus(instanceId = instanceId, node = node).data
    }

    suspend fun getVMStatus(instanceId: String, node: String, vmid: Int): ProxmoxVM {
        return api.getVMStatus(instanceId = instanceId, node = node, vmid = vmid).data
    }

    suspend fun getLXCStatus(instanceId: String, node: String, vmid: Int): ProxmoxLXC {
        return api.getLXCStatus(instanceId = instanceId, node = node, vmid = vmid).data
    }

    suspend fun getVMSnapshots(instanceId: String, node: String, vmid: Int): List<ProxmoxSnapshot> {
        return api.getVMSnapshots(instanceId = instanceId, node = node, vmid = vmid).data
    }

    suspend fun getLXCSnapshots(instanceId: String, node: String, vmid: Int): List<ProxmoxSnapshot> {
        return api.getLXCSnapshots(instanceId = instanceId, node = node, vmid = vmid).data
    }

    suspend fun getStorageContent(instanceId: String, node: String, storage: String): List<ProxmoxStorageContent> {
        return api.getStorageContent(instanceId = instanceId, node = node, storage = storage).data
    }

    suspend fun getNodeTasks(instanceId: String, node: String, limit: Int = 20): List<ProxmoxTask> {
        return api.getNodeTasks(instanceId = instanceId, node = node, limit = limit).data
    }

    // MARK: - VNC Console

    suspend fun getVncTicket(instanceId: String, node: String, vmid: Int, isQemu: Boolean): ProxmoxVncProxyResponse {
        return if (isQemu) {
            api.getVMVncTicket(instanceId = instanceId, node = node, vmid = vmid).data
        } else {
            api.getLXCvncTicket(instanceId = instanceId, node = node, vmid = vmid).data
        }
    }

    // MARK: - Guest Config

    suspend fun getGuestConfig(instanceId: String, node: String, vmid: Int, isQemu: Boolean): Map<String, String> {
        return if (isQemu) {
            api.getVMConfig(instanceId = instanceId, node = node, vmid = vmid).data
        } else {
            api.getLXCConfig(instanceId = instanceId, node = node, vmid = vmid).data
        }
    }

    suspend fun updateGuestConfig(
        instanceId: String,
        node: String,
        vmid: Int,
        isQemu: Boolean,
        config: Map<String, String>
    ) {
        if (isQemu) {
            api.updateVMConfig(instanceId = instanceId, node = node, vmid = vmid, config = config)
        } else {
            api.updateLXCConfig(instanceId = instanceId, node = node, vmid = vmid, config = config)
        }
    }

    // MARK: - Actions

    suspend fun startVM(instanceId: String, node: String, vmid: Int) {
        api.startVM(instanceId = instanceId, node = node, vmid = vmid)
    }

    suspend fun stopVM(instanceId: String, node: String, vmid: Int) {
        api.stopVM(instanceId = instanceId, node = node, vmid = vmid)
    }

    suspend fun shutdownVM(instanceId: String, node: String, vmid: Int) {
        api.shutdownVM(instanceId = instanceId, node = node, vmid = vmid)
    }

    suspend fun rebootVM(instanceId: String, node: String, vmid: Int) {
        api.rebootVM(instanceId = instanceId, node = node, vmid = vmid)
    }

    suspend fun startLXC(instanceId: String, node: String, vmid: Int) {
        api.startLXC(instanceId = instanceId, node = node, vmid = vmid)
    }

    suspend fun stopLXC(instanceId: String, node: String, vmid: Int) {
        api.stopLXC(instanceId = instanceId, node = node, vmid = vmid)
    }

    suspend fun shutdownLXC(instanceId: String, node: String, vmid: Int) {
        api.shutdownLXC(instanceId = instanceId, node = node, vmid = vmid)
    }

    suspend fun rebootLXC(instanceId: String, node: String, vmid: Int) {
        api.rebootLXC(instanceId = instanceId, node = node, vmid = vmid)
    }

    // MARK: - ISO & Journal & Create Guest

    suspend fun getIsoList(
        instanceId: String,
        node: String,
        storage: String,
        content: String = "iso"
    ): List<ProxmoxStorageIso> {
        return api.getStorageIsoList(
            instanceId = instanceId,
            node = node,
            storage = storage,
            content = content
        ).data
    }

    suspend fun getJournal(instanceId: String, node: String, since: Long? = null, limit: Int = 100): List<ProxmoxJournalLine> {
        return api.getNodeJournal(instanceId = instanceId, node = node, since = since, limit = limit).data
    }

    suspend fun getNextVmid(instanceId: String): String {
        return api.getNextVmid(instanceId = instanceId).data
    }

    suspend fun createVM(instanceId: String, node: String, body: Map<String, String>): String {
        return api.createVM(instanceId = instanceId, node = node, body = body).data
    }

    suspend fun createLXC(instanceId: String, node: String, body: Map<String, String>): String {
        return api.createLXC(instanceId = instanceId, node = node, body = body).data
    }

    // MARK: - Network Interfaces

    suspend fun getNodeNetwork(instanceId: String, node: String): List<ProxmoxNetworkInterface> {
        return api.getNodeNetwork(instanceId = instanceId, node = node).data
    }

    // MARK: - Update Guest Description/Notes

    suspend fun updateGuestDescription(
        instanceId: String,
        node: String,
        vmid: Int,
        isQemu: Boolean,
        description: String
    ) {
        val body = mapOf("description" to description)
        if (isQemu) {
            api.updateVMDescription(instanceId = instanceId, node = node, vmid = vmid, body = body)
        } else {
            api.updateLXCDescription(instanceId = instanceId, node = node, vmid = vmid, body = body)
        }
    }

    // MARK: - Helpers

    companion object {
        private fun formatBytes(bytes: Double): String {
            return when {
                bytes >= 1_099_511_627_776 -> String.format("%.1f TB", bytes / 1_099_511_627_776)
                bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes / 1_073_741_824)
                bytes >= 1_048_576 -> String.format("%.1f MB", bytes / 1_048_576)
                bytes >= 1024 -> String.format("%.1f KB", bytes / 1024)
                else -> "${bytes.toLong()} B"
            }
        }
    }
}
