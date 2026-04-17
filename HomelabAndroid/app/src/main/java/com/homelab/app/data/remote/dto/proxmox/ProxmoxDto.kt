package com.homelab.app.data.remote.dto.proxmox

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// MARK: - Auth

@Serializable
data class ProxmoxAuthTicket(
    val ticket: String,
    @SerialName("CSRFPreventionToken") val csrfPreventionToken: String,
    val username: String
)

// MARK: - Node

@Serializable
data class ProxmoxNode(
    val node: String,
    val status: String? = null,
    val cpu: Double? = null,
    val maxcpu: Int? = null,
    val mem: Long? = null,
    val maxmem: Long? = null,
    val disk: Long? = null,
    val maxdisk: Long? = null,
    val uptime: Int? = null,
    val type: String? = null,
    val level: String? = null,
    val ssl_fingerprint: String? = null
) {
    val isOnline: Boolean get() = status?.lowercase() == "online"
    val cpuPercent: Double get() = (cpu ?: 0.0) * 100
    val memPercent: Double get() = if ((maxmem ?: 0) > 0) (mem?.toDouble() ?: 0.0) / (maxmem?.toDouble() ?: 1.0) * 100 else 0.0
    val diskPercent: Double get() = if ((maxdisk ?: 0) > 0) (disk?.toDouble() ?: 0.0) / (maxdisk?.toDouble() ?: 1.0) * 100 else 0.0
    val formattedUptime: String
        get() {
            val u = uptime ?: return "-"
            if (u <= 0) return "-"
            val days = u / 86400
            val hours = (u % 86400) / 3600
            val minutes = (u % 3600) / 60
            return if (days > 0) "${days}d ${hours}h" else if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
}

// MARK: - VM (QEMU)

@Serializable
data class ProxmoxVM(
    val vmid: Int,
    val name: String? = null,
    val status: String? = null,
    val cpu: Double? = null,
    val cpus: Int? = null,
    val mem: Long? = null,
    val maxmem: Long? = null,
    val disk: Long? = null,
    val maxdisk: Long? = null,
    val diskread: Long? = null,
    val diskwrite: Long? = null,
    val netin: Long? = null,
    val netout: Long? = null,
    val uptime: Int? = null,
    val template: Int? = null,
    val qmpstatus: String? = null,
    val tags: String? = null,
    val lock: String? = null,
    val pid: Int? = null
) {
    val isRunning: Boolean get() = status?.lowercase() == "running"
    val isStopped: Boolean get() = status?.lowercase() == "stopped"
    val isPaused: Boolean get() = status?.lowercase() == "paused"
    val isTemplate: Boolean get() = template == 1
    val displayName: String get() = name ?: "VM $vmid"
    val cpuPercent: Double get() = (cpu ?: 0.0) * 100
    val memPercent: Double get() = if ((maxmem ?: 0) > 0) (mem?.toDouble() ?: 0.0) / (maxmem?.toDouble() ?: 1.0) * 100 else 0.0
    val formattedUptime: String
        get() {
            val u = uptime ?: return "-"
            if (u <= 0) return "-"
            val days = u / 86400
            val hours = (u % 86400) / 3600
            val minutes = (u % 3600) / 60
            return if (days > 0) "${days}d ${hours}h" else if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
    val tagList: List<String>
        get() = tags?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}

// MARK: - LXC Container

@Serializable
data class ProxmoxLXC(
    val vmid: Int,
    val name: String? = null,
    val status: String? = null,
    val cpu: Double? = null,
    val cpus: Int? = null,
    val mem: Long? = null,
    val maxmem: Long? = null,
    val disk: Long? = null,
    val maxdisk: Long? = null,
    val diskread: Long? = null,
    val diskwrite: Long? = null,
    val netin: Long? = null,
    val netout: Long? = null,
    val uptime: Int? = null,
    val template: Int? = null,
    val tags: String? = null,
    val lock: String? = null,
    val pid: Int? = null,
    val type: String? = null
) {
    val isRunning: Boolean get() = status?.lowercase() == "running"
    val isStopped: Boolean get() = status?.lowercase() == "stopped"
    val isTemplate: Boolean get() = template == 1
    val displayName: String get() = name ?: "CT $vmid"
    val cpuPercent: Double get() = (cpu ?: 0.0) * 100
    val memPercent: Double get() = if ((maxmem ?: 0) > 0) (mem?.toDouble() ?: 0.0) / (maxmem?.toDouble() ?: 1.0) * 100 else 0.0
    val formattedUptime: String
        get() {
            val u = uptime ?: return "-"
            if (u <= 0) return "-"
            val days = u / 86400
            val hours = (u % 86400) / 3600
            val minutes = (u % 3600) / 60
            return if (days > 0) "${days}d ${hours}h" else if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
    val tagList: List<String>
        get() = tags?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}

// MARK: - Storage

@Serializable
data class ProxmoxStorage(
    val storage: String,
    val type: String? = null,
    val used: Long? = null,
    val total: Long? = null,
    val avail: Long? = null,
    val active: Int? = null,
    val content: String? = null,
    val enabled: Int? = null,
    val shared: Int? = null,
    val used_fraction: Double? = null
) {
    val isActive: Boolean get() = active == 1
    val isEnabled: Boolean get() = enabled == 1
    val usedPercent: Double
        get() {
            if (used_fraction != null) return used_fraction * 100
            return if ((total ?: 0) > 0) (used?.toDouble() ?: 0.0) / (total?.toDouble() ?: 1.0) * 100 else 0.0
        }
    val contentTypes: List<String>
        get() = content?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}

// MARK: - Task

@Serializable
data class ProxmoxTask(
    val upid: String,
    val type: String? = null,
    val status: String? = null,
    val starttime: Long? = null,
    val endtime: Long? = null,
    val user: String? = null,
    val node: String? = null,
    val pstart: Int? = null,
    val exitstatus: String? = null
) {
    val isRunning: Boolean get() = status?.lowercase() == "running" || endtime == null
    val isOk: Boolean get() = exitstatus?.lowercase() == "ok" || status?.lowercase() == "ok"
    val formattedStart: String
        get() {
            val s = starttime ?: return "-"
            return java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(s * 1000))
        }
    val duration: String
        get() {
            val s = starttime ?: return "-"
            val end = endtime ?: (System.currentTimeMillis() / 1000)
            val seconds = end - s
            return if (seconds < 60) "${seconds}s"
            else if (seconds < 3600) "${seconds / 60}m ${seconds % 60}s"
            else "${seconds / 3600}h ${seconds % 3600 / 60}m"
        }
}

// MARK: - Pool

@Serializable
data class ProxmoxPool(
    val poolid: String,
    val comment: String? = null
)

@Serializable
data class ProxmoxPoolMember(
    val type: String? = null,
    val vmid: Int? = null,
    val name: String? = null,
    val status: String? = null,
    val node: String? = null,
    val storage: String? = null
)

@Serializable
data class ProxmoxPoolDetail(
    val members: List<ProxmoxPoolMember>? = null,
    val comment: String? = null
)

// MARK: - Cluster Resource

@Serializable
data class ProxmoxClusterResource(
    val type: String? = null,
    val status: String? = null,
    val node: String? = null,
    val vmid: Int? = null,
    val name: String? = null,
    val cpu: Double? = null,
    val maxcpu: Int? = null,
    val mem: Long? = null,
    val maxmem: Long? = null,
    val disk: Long? = null,
    val maxdisk: Long? = null,
    val uptime: Int? = null,
    val template: Int? = null,
    val pool: String? = null,
    val hastate: String? = null,
    val tags: String? = null,
    val storage: String? = null,
    val content: String? = null,
    val plugintype: String? = null
) {
    val isQemu: Boolean get() = type == "qemu"
    val isLXC: Boolean get() = type == "lxc"
    val isNode: Boolean get() = type == "node"
    val isStorage: Boolean get() = type == "storage"
    val isRunning: Boolean get() = status?.lowercase() == "running" || status?.lowercase() == "online"
    val isTemplate: Boolean get() = template == 1
    val cpuPercent: Double get() = (cpu ?: 0.0) * 100
    val memPercent: Double get() = if ((maxmem ?: 0) > 0) (mem?.toDouble() ?: 0.0) / (maxmem?.toDouble() ?: 1.0) * 100 else 0.0
}

// MARK: - Version

@Serializable
data class ProxmoxVersion(
    val version: String? = null,
    val release: String? = null,
    val repoid: String? = null
)

// MARK: - Backup Job

@Serializable
data class ProxmoxBackupJob(
    val id: String? = null,
    val enabled: Int? = null,
    val schedule: String? = null,
    val storage: String? = null,
    val mode: String? = null,
    val compress: String? = null,
    val vmid: String? = null,
    val all: Int? = null,
    val mailnotification: String? = null,
    val mailto: String? = null,
    val pool: String? = null,
    val exclude: String? = null,
    val node: String? = null,
    val starttime: String? = null
) {
    val isEnabled: Boolean get() = enabled == 1 || enabled == null
    val backupAll: Boolean get() = all == 1
    val vmidList: List<String> get() = vmid?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
}

// MARK: - Task Log Entry

@Serializable
data class ProxmoxTaskLogEntry(
    val n: Int,
    val t: String? = null
)

// MARK: - VNC Proxy

@Serializable
data class ProxmoxVncProxyResponse(
    val ticket: String,
    val port: Int? = null,
    val user: String? = null,
    val cert: String? = null
)

// MARK: - VM/LXC Config

@Serializable
data class ProxmoxGuestConfig(
    val name: String? = null,
    val cores: Int? = null,
    val memory: Int? = null,
    val net0: String? = null,
    val scsi0: String? = null,
    val ostype: String? = null,
    val rootfs: String? = null
) {
    val displayCores: Int get() = cores ?: 1
    val displayMemory: Int get() = memory ?: 512
}

// MARK: - Generic API Response

@Serializable
data class ProxmoxApiResponse<T>(
    val data: T
)

// MARK: - Node Status Detail

@Serializable
data class ProxmoxNodeStatus(
    val uptime: Int? = null,
    val cpu: Double? = null,
    val wait: Double? = null,
    val mem: Long? = null,
    val maxmem: Long? = null,
    val swap: Long? = null,
    val maxswap: Long? = null,
    val rootfs: ProxmoxDiskInfo? = null,
    val kversion: String? = null,
    val pveversion: String? = null,
    val cpus: Int? = null
) {
    val cpuPercent: Double get() = (cpu ?: 0.0) * 100
    val memPercent: Double get() = if ((maxmem ?: 0) > 0) (mem?.toDouble() ?: 0.0) / (maxmem?.toDouble() ?: 1.0) * 100 else 0.0
    val swapPercent: Double get() = if ((maxswap ?: 0) > 0) (swap?.toDouble() ?: 0.0) / (maxswap?.toDouble() ?: 1.0) * 100 else 0.0
    val rootfsPercent: Double get() {
        val r = rootfs ?: return 0.0
        return if ((r.total ?: 0) > 0) (r.used?.toDouble() ?: 0.0) / (r.total?.toDouble() ?: 1.0) * 100 else 0.0
    }
    val formattedUptime: String
        get() {
            val u = uptime ?: return "-"
            if (u <= 0) return "-"
            val days = u / 86400
            val hours = (u % 86400) / 3600
            val minutes = (u % 3600) / 60
            return if (days > 0) "${days}d ${hours}h" else if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
        }
}

@Serializable
data class ProxmoxDiskInfo(
    val used: Long? = null,
    val total: Long? = null,
    val free: Long? = null,
    val avail: Long? = null
)

@Serializable
data class ProxmoxSnapshot(
    val name: String,
    val description: String? = null,
    val snaptime: Long? = null,
    val vmstate: Int? = null
) {
    val formattedTime: String
        get() = snaptime?.let { java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it * 1000)) } ?: "-"
}

@Serializable
data class ProxmoxStorageContent(
    val volid: String,
    val content: String? = null,
    val format: String? = null,
    val size: Long? = null,
    val vmid: Int? = null,
    val notes: String? = null,
    val ctime: Long? = null,
    val protected: Int? = null
) {
    val isProtected: Boolean get() = protected == 1
    val formattedSize: String
        get() {
            val s = size?.toDouble() ?: return "-"
            return when {
                s >= 1_099_511_627_776 -> String.format("%.1f TB", s / 1_099_511_627_776)
                s >= 1_073_741_824 -> String.format("%.1f GB", s / 1_073_741_824)
                s >= 1_048_576 -> String.format("%.1f MB", s / 1_048_576)
                s >= 1024 -> String.format("%.1f KB", s / 1024)
                else -> "${s.toLong()} B"
            }
        }
}

// MARK: - Firewall Rule

@Serializable
data class ProxmoxFirewallRule(
    val type: String? = null, // "in", "out", "group"
    val action: String? = null, // "ACCEPT", "DROP", "REJECT"
    val enable: Int? = null,
    val comment: String? = null,
    val source: String? = null,
    val dest: String? = null,
    val proto: String? = null,
    val dport: String? = null,
    val pos: Int? = null,
    val iface: String? = null
) {
    val isEnabled: Boolean get() = enable != 0
}

// MARK: - Firewall Options

@Serializable
data class ProxmoxFirewallOptions(
    val enable: Int? = null
) {
    val isEnabled: Boolean get() = enable == 1
}

// MARK: - APT Updates

@Serializable
data class ProxmoxAptPackage(
    @kotlinx.serialization.SerialName("package") val `package`: String? = null,
    val title: String? = null,
    val version: String? = null,
    val old_version: String? = null,
    val arch: String? = null,
    val origin: String? = null
) {
    val displayName: String get() = title ?: `package` ?: "Unknown"
    val displayVersion: String get() = if (old_version != null && version != null) "$old_version \u2192 $version" else (version ?: "-")
}

// MARK: - HA Resources

@Serializable
data class ProxmoxHAResource(
    val sid: String? = null,
    val type: String? = null,
    val state: String? = null,
    val group: String? = null,
    val max_relocate: Int? = null,
    val max_restart: Int? = null,
    val comment: String? = null,
    val status: String? = null
) {
    val isVm: Boolean get() = sid?.startsWith("vm:") == true
    val vmid: Int? get() = sid?.removePrefix("vm:")?.toIntOrNull()
    val isCt: Boolean get() = sid?.startsWith("ct:") == true
    val ctid: Int? get() = sid?.removePrefix("ct:")?.toIntOrNull()
    val resourceId: String? get() = when {
        isVm -> vmid?.toString()
        isCt -> ctid?.toString()
        else -> sid
    }
}

@Serializable
data class ProxmoxHAGroup(
    val group: String? = null,
    val comment: String? = null,
    val nodes: String? = null, // "node1:1,node2:2"
    val restricted: Int? = null,
    val nofailback: Int? = null,
    val type: String? = null
) {
    val nodeList: List<String> get() = nodes?.split(",")?.map { it.split(":").firstOrNull() ?: it } ?: emptyList()
}

// MARK: - Ceph

@Serializable
data class ProxmoxCephStatus(
    val health: ProxmoxCephHealth? = null,
    val fsmap: ProxmoxCephFsMap? = null,
    val osdmap: ProxmoxCephOsdMap? = null,
    val pgmap: ProxmoxCephPgMap? = null,
    val monmap: ProxmoxCephMonMap? = null
)

@Serializable
data class ProxmoxCephHealth(
    val status: String? = null,
    val checks: Map<String, ProxmoxCephCheck>? = null
)

@Serializable
data class ProxmoxCephCheck(
    val severity: String? = null,
    val summary: ProxmoxCephSummary? = null
)

@Serializable
data class ProxmoxCephSummary(
    val message: String? = null
)

@Serializable
data class ProxmoxCephFsMap(
    val epoch: Int? = null,
    val by_rank: List<ProxmoxCephMdsRank>? = null
)

@Serializable
data class ProxmoxCephMdsRank(
    val name: String? = null,
    val rank: Int? = null,
    val state: String? = null
)

@Serializable
data class ProxmoxCephOsdMap(
    val osds: List<ProxmoxCephOsd>? = null,
    val num_osds: Int? = null,
    val num_up_osds: Int? = null,
    val num_in_osds: Int? = null
)

@Serializable
data class ProxmoxCephOsd(
    val osd: Int,
    val up: Int,
    @SerialName("in") val `in`: Int,
    val state: String? = null,
    val weight: Double? = null
) {
    val isUp: Boolean get() = up == 1
    val isIn: Boolean get() = `in` == 1
    val displayWeight: String get() = String.format("%.2f", weight ?: 0.0)
}

@Serializable
data class ProxmoxCephPgMap(
    val pgs_by_state: List<ProxmoxCephPgState>? = null,
    val num_pgs: Int? = null,
    val data_bytes: Long? = null,
    val bytes_used: Long? = null,
    val bytes_avail: Long? = null,
    val bytes_total: Long? = null
) {
    val usagePercent: Double
        get() {
            val total = bytes_total ?: return 0.0
            if (total <= 0) return 0.0
            return (bytes_used?.toDouble() ?: 0.0) / total.toDouble() * 100
        }
}

@Serializable
data class ProxmoxCephPgState(
    val state_name: String,
    val count: Int
)

@Serializable
data class ProxmoxCephMonMap(
    val mons: List<ProxmoxCephMon>? = null
)

@Serializable
data class ProxmoxCephMon(
    val name: String,
    val rank: Int,
    val state: String? = null
)

// MARK: - Replication

@Serializable
data class ProxmoxReplicationJob(
    val id: String? = null,
    val type: String? = null,
    val source: String? = null,
    val target: String? = null,
    val guest: Int? = null,
    val schedule: String? = null,
    val disable: Int? = null,
    val state: String? = null,
    val fail_count: Int? = null,
    val duration: Double? = null
) {
    val isEnabled: Boolean get() = disable != 1
    val guestId: String? get() = guest?.toString() ?: id
    val formattedDuration: String
        get() {
            val d = duration ?: return "-"
            return if (d < 60) "${d.toInt()}s"
            else if (d < 3600) "${d.toInt() / 60}m ${d.toInt() % 60}s"
            else "${d.toInt() / 3600}h ${(d.toInt() % 3600) / 60}m"
        }
}

// MARK: - Storage ISO

@Serializable
data class ProxmoxStorageIso(
    val content: String? = null,
    val ctime: Long? = null,
    val format: String? = null,
    val size: Long? = null,
    val volid: String? = null
) {
    val name: String get() = volid?.substringAfterLast("/") ?: volid ?: "-"
    val formattedSize: String
        get() {
            val s = size?.toDouble() ?: return "-"
            return when {
                s >= 1_099_511_627_776 -> String.format("%.1f TB", s / 1_099_511_627_776)
                s >= 1_073_741_824 -> String.format("%.1f GB", s / 1_073_741_824)
                s >= 1_048_576 -> String.format("%.1f MB", s / 1_048_576)
                s >= 1024 -> String.format("%.1f KB", s / 1024)
                else -> "${s.toLong()} B"
            }
        }
}

// MARK: - Network Interface

@Serializable
data class ProxmoxNetworkInterface(
    val iface: String,
    val type: String? = null, // eth, bridge, bond, alias
    val address: String? = null,
    val cidr: String? = null,
    val gateway: String? = null,
    val address6: String? = null,
    val cidr6: String? = null,
    val gateway6: String? = null,
    val active: Int? = null,
    val autostart: Int? = null,
    val priority: Int? = null,
    val bridge_ports: String? = null,
    val bond_mode: String? = null,
    val comments: String? = null
) {
    val isActive: Boolean get() = active == 1
    val isAutostart: Boolean get() = autostart == 1
    val ipAddress: String get() = cidr ?: address ?: "-"
    val ipAddress6: String get() = cidr6 ?: address6 ?: ""
}

// MARK: - Journal

@Serializable
data class ProxmoxJournalLine(
    val t: Double? = null, // timestamp
    val n: Int? = null, // line number
    val text: String? = null
)
