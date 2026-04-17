package com.homelab.app.data.remote.api

import com.homelab.app.data.remote.dto.proxmox.*
import retrofit2.http.*

interface ProxmoxApi {

    // MARK: - Authentication

    @POST
    suspend fun authenticate(
        @Url url: String,
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Bypass") bypass: String = "true",
        @Header("X-Homelab-Allow-Self-Signed") allowSelfSigned: String = "false",
        @Body credentials: Map<String, String>
    ): ProxmoxApiResponse<ProxmoxAuthTicket>

    @GET
    suspend fun validateApiToken(
        @Url url: String,
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Bypass") bypass: String = "true",
        @Header("X-Homelab-Allow-Self-Signed") allowSelfSigned: String = "false",
        @Header("Authorization") authorization: String
    ): ProxmoxApiResponse<ProxmoxVersion>

    // MARK: - Version

    @GET("api2/json/version")
    suspend fun getVersion(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): ProxmoxApiResponse<ProxmoxVersion>

    // MARK: - Nodes

    @GET("api2/json/nodes")
    suspend fun getNodes(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): ProxmoxApiResponse<List<ProxmoxNode>>

    // MARK: - VMs (QEMU)

    @GET("api2/json/nodes/{node}/qemu")
    suspend fun getVMs(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String
    ): ProxmoxApiResponse<List<ProxmoxVM>>

    // MARK: - LXC Containers

    @GET("api2/json/nodes/{node}/lxc")
    suspend fun getLXCs(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String
    ): ProxmoxApiResponse<List<ProxmoxLXC>>

    // MARK: - Storage

    @GET("api2/json/nodes/{node}/storage")
    suspend fun getStorage(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String
    ): ProxmoxApiResponse<List<ProxmoxStorage>>

    // MARK: - Tasks

    @GET("api2/json/nodes/{node}/tasks")
    suspend fun getTasks(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Query("limit") limit: Int = 20
    ): ProxmoxApiResponse<List<ProxmoxTask>>

    @GET("api2/json/nodes/{node}/tasks/{upid}/log")
    suspend fun getTaskLog(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("upid", encoded = true) upid: String,
        @Query("limit") limit: Int = 100
    ): ProxmoxApiResponse<List<ProxmoxTaskLogEntry>>

    @GET("api2/json/nodes/{node}/tasks/{upid}/status")
    suspend fun getTaskStatus(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("upid", encoded = true) upid: String
    ): ProxmoxApiResponse<ProxmoxTask>

    // MARK: - Pools

    @GET("api2/json/pools")
    suspend fun getPools(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): ProxmoxApiResponse<List<ProxmoxPool>>

    @GET("api2/json/pools/{poolid}")
    suspend fun getPoolMembers(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("poolid", encoded = true) poolid: String
    ): ProxmoxApiResponse<ProxmoxPoolDetail>

    // MARK: - Cluster Resources

    @GET("api2/json/cluster/resources")
    suspend fun getClusterResources(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): ProxmoxApiResponse<List<ProxmoxClusterResource>>

    // MARK: - Backup Jobs

    @GET("api2/json/cluster/backup")
    suspend fun getBackupJobs(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): ProxmoxApiResponse<List<ProxmoxBackupJob>>

    @POST("api2/json/cluster/backup/{id}")
    suspend fun triggerBackupJob(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id", encoded = true) id: String
    ): ProxmoxApiResponse<String>

    @GET("api2/json/cluster/firewall/rules")
    suspend fun getClusterFirewallRules(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): ProxmoxApiResponse<List<ProxmoxFirewallRule>>

    // MARK: - Actions (start/stop/shutdown/reboot)

    @POST("api2/json/nodes/{node}/qemu/{vmid}/status/start")
    suspend fun startVM(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int
    ): ProxmoxApiResponse<String>

    @POST("api2/json/nodes/{node}/qemu/{vmid}/status/stop")
    suspend fun stopVM(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int
    ): ProxmoxApiResponse<String>

    @POST("api2/json/nodes/{node}/qemu/{vmid}/status/shutdown")
    suspend fun shutdownVM(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int
    ): ProxmoxApiResponse<String>

    @POST("api2/json/nodes/{node}/qemu/{vmid}/status/reboot")
    suspend fun rebootVM(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int
    ): ProxmoxApiResponse<String>

    @POST("api2/json/nodes/{node}/lxc/{vmid}/status/start")
    suspend fun startLXC(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int
    ): ProxmoxApiResponse<String>

    @POST("api2/json/nodes/{node}/lxc/{vmid}/status/stop")
    suspend fun stopLXC(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int
    ): ProxmoxApiResponse<String>

    @POST("api2/json/nodes/{node}/lxc/{vmid}/status/shutdown")
    suspend fun shutdownLXC(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int
    ): ProxmoxApiResponse<String>

    @POST("api2/json/nodes/{node}/lxc/{vmid}/status/reboot")
    suspend fun rebootLXC(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int
    ): ProxmoxApiResponse<String>

    // MARK: - Console (noVNC ticket)

    @POST("api2/json/nodes/{node}/qemu/{vmid}/vncproxy")
    suspend fun getVMVncTicket(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int,
        @Body body: Map<String, String> = mapOf("websocket" to "1")
    ): ProxmoxApiResponse<ProxmoxVncProxyResponse>

    @POST("api2/json/nodes/{node}/lxc/{vmid}/vncproxy")
    suspend fun getLXCvncTicket(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int,
        @Body body: Map<String, String> = mapOf("websocket" to "1")
    ): ProxmoxApiResponse<ProxmoxVncProxyResponse>

    // MARK: - Config Updates (VM)

    @PUT("api2/json/nodes/{node}/qemu/{vmid}/config")
    suspend fun updateVMConfig(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int,
        @Body config: Map<String, String>
    ): ProxmoxApiResponse<String>

    // MARK: - Config Updates (LXC)

    @PUT("api2/json/nodes/{node}/lxc/{vmid}/config")
    suspend fun updateLXCConfig(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int,
        @Body config: Map<String, String>
    ): ProxmoxApiResponse<String>

    // MARK: - Get Config (VM/LXC)

    @GET("api2/json/nodes/{node}/qemu/{vmid}/config")
    suspend fun getVMConfig(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int
    ): ProxmoxApiResponse<Map<String, String>>

    @GET("api2/json/nodes/{node}/lxc/{vmid}/config")
    suspend fun getLXCConfig(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int
    ): ProxmoxApiResponse<Map<String, String>>

    // MARK: - Details

    @GET("api2/json/nodes/{node}/status")
    suspend fun getNodeStatus(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String
    ): ProxmoxApiResponse<ProxmoxNodeStatus>

    @GET("api2/json/nodes/{node}/qemu/{vmid}/status/current")
    suspend fun getVMStatus(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int
    ): ProxmoxApiResponse<ProxmoxVM>

    @GET("api2/json/nodes/{node}/lxc/{vmid}/status/current")
    suspend fun getLXCStatus(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int
    ): ProxmoxApiResponse<ProxmoxLXC>

    @GET("api2/json/nodes/{node}/qemu/{vmid}/snapshot")
    suspend fun getVMSnapshots(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int
    ): ProxmoxApiResponse<List<ProxmoxSnapshot>>

    @POST("api2/json/nodes/{node}/qemu/{vmid}/snapshot")
    suspend fun createVMSnapshot(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int,
        @Body body: Map<String, String>
    ): ProxmoxApiResponse<String>

    @HTTP(method = "DELETE", path = "api2/json/nodes/{node}/qemu/{vmid}/snapshot/{snapname}", hasBody = true)
    suspend fun deleteVMSnapshot(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int,
        @Path("snapname", encoded = true) snapname: String
    ): ProxmoxApiResponse<String>

    @POST("api2/json/nodes/{node}/qemu/{vmid}/snapshot/{snapname}/rollback")
    suspend fun rollbackVMSnapshot(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int,
        @Path("snapname", encoded = true) snapname: String
    ): ProxmoxApiResponse<String>

    @GET("api2/json/nodes/{node}/lxc/{vmid}/snapshot")
    suspend fun getLXCSnapshots(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int
    ): ProxmoxApiResponse<List<ProxmoxSnapshot>>

    @POST("api2/json/nodes/{node}/lxc/{vmid}/snapshot")
    suspend fun createLXCSnapshot(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int,
        @Body body: Map<String, String>
    ): ProxmoxApiResponse<String>

    @HTTP(method = "DELETE", path = "api2/json/nodes/{node}/lxc/{vmid}/snapshot/{snapname}", hasBody = true)
    suspend fun deleteLXCSnapshot(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int,
        @Path("snapname", encoded = true) snapname: String
    ): ProxmoxApiResponse<String>

    @POST("api2/json/nodes/{node}/lxc/{vmid}/snapshot/{snapname}/rollback")
    suspend fun rollbackLXCSnapshot(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int,
        @Path("snapname", encoded = true) snapname: String
    ): ProxmoxApiResponse<String>

    @GET("api2/json/nodes/{node}/storage/{storage}/content")
    suspend fun getStorageContent(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("storage") storage: String
    ): ProxmoxApiResponse<List<ProxmoxStorageContent>>

    @GET("api2/json/nodes/{node}/tasks")
    suspend fun getNodeTasks(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Query("limit") limit: Int = 20
    ): ProxmoxApiResponse<List<ProxmoxTask>>

    // MARK: - APT Updates

    @GET("api2/json/nodes/{node}/apt/update")
    suspend fun getAptUpdates(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String
    ): ProxmoxApiResponse<List<ProxmoxAptPackage>>

    // MARK: - HA Resources

    @GET("api2/json/cluster/ha/resources")
    suspend fun getHAResources(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): ProxmoxApiResponse<List<ProxmoxHAResource>>

    @GET("api2/json/cluster/ha/groups")
    suspend fun getHAGroups(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): ProxmoxApiResponse<List<ProxmoxHAGroup>>

    // MARK: - Ceph

    @GET("api2/json/nodes/{node}/ceph/status")
    suspend fun getCephStatus(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String
    ): ProxmoxApiResponse<ProxmoxCephStatus>

    // MARK: - Replication

    @GET("api2/json/cluster/replication")
    suspend fun getReplicationJobs(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): ProxmoxApiResponse<List<ProxmoxReplicationJob>>

    @POST("api2/json/cluster/replication/{id}/run")
    suspend fun triggerReplicationJob(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id", encoded = true) id: String
    ): ProxmoxApiResponse<String>

    // MARK: - Firewall Options

    @GET("api2/json/cluster/firewall/options")
    suspend fun getClusterFirewallOptions(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): ProxmoxApiResponse<ProxmoxFirewallOptions>

    @PUT("api2/json/cluster/firewall/options")
    suspend fun updateClusterFirewallOptions(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body body: Map<String, String>
    ): ProxmoxApiResponse<String>

    // MARK: - Storage Content Actions

    @HTTP(method = "DELETE", path = "api2/json/nodes/{node}/storage/{storage}/content/{volume}", hasBody = false)
    suspend fun deleteStorageContent(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("storage") storage: String,
        @Path("volume", encoded = true) volume: String
    ): ProxmoxApiResponse<String>

    // MARK: - Clone & Migrate (Actions)

    @POST("api2/json/nodes/{node}/qemu/{vmid}/clone")
    suspend fun cloneVM(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int,
        @Body body: Map<String, String>
    ): ProxmoxApiResponse<String>

    @POST("api2/json/nodes/{node}/qemu/{vmid}/migrate")
    suspend fun migrateVM(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int,
        @Body body: Map<String, String>
    ): ProxmoxApiResponse<String>

    @POST("api2/json/nodes/{node}/lxc/{vmid}/clone")
    suspend fun cloneLXC(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int,
        @Body body: Map<String, String>
    ): ProxmoxApiResponse<String>

    @POST("api2/json/nodes/{node}/lxc/{vmid}/migrate")
    suspend fun migrateLXC(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int,
        @Body body: Map<String, String>
    ): ProxmoxApiResponse<String>

    // MARK: - ISO Images

    @GET("api2/json/nodes/{node}/storage/{storage}/content")
    suspend fun getStorageIsoList(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("storage") storage: String,
        @Query("content") content: String = "iso"
    ): ProxmoxApiResponse<List<ProxmoxStorageIso>>

    // MARK: - Journal

    @GET("api2/json/nodes/{node}/journal")
    suspend fun getNodeJournal(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Query("since") since: Long? = null,
        @Query("limit") limit: Int = 100
    ): ProxmoxApiResponse<List<ProxmoxJournalLine>>

    // MARK: - Network Interfaces

    @GET("api2/json/nodes/{node}/network")
    suspend fun getNodeNetwork(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String
    ): ProxmoxApiResponse<List<ProxmoxNetworkInterface>>

    // MARK: - Update Guest Description/Notes

    @PUT("api2/json/nodes/{node}/qemu/{vmid}/config")
    suspend fun updateVMDescription(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int,
        @Body body: Map<String, String> // mapOf("description" to text)
    ): ProxmoxApiResponse<String>

    @PUT("api2/json/nodes/{node}/lxc/{vmid}/config")
    suspend fun updateLXCDescription(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Path("vmid") vmid: Int,
        @Body body: Map<String, String>
    ): ProxmoxApiResponse<String>

    // MARK: - Create Guest

    @POST("api2/json/nodes/{node}/qemu")
    suspend fun createVM(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Body body: Map<String, String>
    ): ProxmoxApiResponse<String>

    @POST("api2/json/nodes/{node}/lxc")
    suspend fun createLXC(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("node") node: String,
        @Body body: Map<String, String>
    ): ProxmoxApiResponse<String>

    @GET("api2/json/cluster/nextid")
    suspend fun getNextVmid(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): ProxmoxApiResponse<String>
}
