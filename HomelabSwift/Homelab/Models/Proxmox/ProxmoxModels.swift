import Foundation

// MARK: - Generic API Response Wrapper
// Proxmox wraps all JSON responses in { "data": ... }

struct ProxmoxAPIResponse<T: Decodable>: Decodable {
    let data: T
}

// MARK: - Authentication

struct ProxmoxAuthTicket: Decodable {
    let ticket: String
    let CSRFPreventionToken: String
    let username: String
}

struct ProxmoxTfaChallenge: Decodable {
    let totp: Bool?
    let recovery: String?
    let u2f: EmptyPayload?
    let webauthn: EmptyPayload?

    var supportsTotp: Bool { totp == true }
    var supportsRecovery: Bool {
        guard let recovery else { return false }
        return recovery.lowercased() != "unavailable"
    }
    var requiresWebAuthnOnly: Bool {
        !supportsTotp && !supportsRecovery && (u2f != nil || webauthn != nil)
    }
}

struct EmptyPayload: Decodable {}

// MARK: - Version

struct ProxmoxVersion: Decodable {
    let version: String?
    let release: String?
    let repoid: String?
}

// MARK: - Node

struct ProxmoxNode: Decodable, Identifiable, Hashable {
    var id: String { node }
    let node: String
    let status: String?
    let cpu: Double?
    let maxcpu: Int?
    let mem: Int64?
    let maxmem: Int64?
    let disk: Int64?
    let maxdisk: Int64?
    let uptime: Int?
    let type: String?
    let level: String?
    let ssl_fingerprint: String?

    var isOnline: Bool {
        status?.lowercased() == "online"
    }

    var cpuPercent: Double {
        guard let cpu else { return 0 }
        return cpu * 100
    }

    var memPercent: Double {
        guard let mem, let maxmem, maxmem > 0 else { return 0 }
        return Double(mem) / Double(maxmem) * 100
    }

    var diskPercent: Double {
        guard let disk, let maxdisk, maxdisk > 0 else { return 0 }
        return Double(disk) / Double(maxdisk) * 100
    }

    var formattedUptime: String {
        guard let uptime, uptime > 0 else { return "-" }
        let days = uptime / 86400
        let hours = (uptime % 86400) / 3600
        let minutes = (uptime % 3600) / 60
        if days > 0 {
            return "\(days)d \(hours)h \(minutes)m"
        } else if hours > 0 {
            return "\(hours)h \(minutes)m"
        } else {
            return "\(minutes)m"
        }
    }
}

struct ProxmoxNodeStatus: Decodable {
    let uptime: Int?
    let cpu: Double?
    let wait: Double?
    let cpuinfo: ProxmoxCPUInfo?
    let memory: ProxmoxMemoryInfo?
    let swap: ProxmoxMemoryInfo?
    let rootfs: ProxmoxDiskInfo?
    let loadavg: [String]?
    let kversion: String?
    let pveversion: String?
    let idle: Double?
    let ksm: ProxmoxKSMInfo?
}

struct ProxmoxCPUInfo: Decodable {
    let model: String?
    let cores: Int?
    let cpus: Int?
    let sockets: Int?
    let mhz: String?
    let hvm: String?
    let flags: String?
    let user_hz: Int?
}

struct ProxmoxMemoryInfo: Decodable {
    let total: Int64?
    let used: Int64?
    let free: Int64?

    var usedPercent: Double {
        guard let total, let used, total > 0 else { return 0 }
        return Double(used) / Double(total) * 100
    }
}

struct ProxmoxDiskInfo: Decodable {
    let total: Int64?
    let used: Int64?
    let free: Int64?
    let avail: Int64?

    var usedPercent: Double {
        guard let total, let used, total > 0 else { return 0 }
        return Double(used) / Double(total) * 100
    }
}

struct ProxmoxKSMInfo: Decodable {
    let shared: Int64?
}

// MARK: - Virtual Machine (QEMU)

struct ProxmoxVM: Decodable, Identifiable, Hashable {
    var id: String { "\(vmid)" }
    let vmid: Int
    let name: String?
    let status: String?
    let cpu: Double?
    let cpus: Int?
    let mem: Int64?
    let maxmem: Int64?
    let disk: Int64?
    let maxdisk: Int64?
    let diskread: Int64?
    let diskwrite: Int64?
    let netin: Int64?
    let netout: Int64?
    let uptime: Int?
    let template: Int?
    let qmpstatus: String?
    let tags: String?
    let lock: String?
    let pid: Int?

    var isRunning: Bool { status?.lowercased() == "running" }
    var isStopped: Bool { status?.lowercased() == "stopped" }
    var isPaused: Bool { status?.lowercased() == "paused" }
    var isTemplate: Bool { template == 1 }

    var displayName: String {
        name ?? "VM \(vmid)"
    }

    var cpuPercent: Double {
        guard let cpu else { return 0 }
        return cpu * 100
    }

    var memPercent: Double {
        guard let mem, let maxmem, maxmem > 0 else { return 0 }
        return Double(mem) / Double(maxmem) * 100
    }

    var formattedUptime: String {
        guard let uptime, uptime > 0 else { return "-" }
        let days = uptime / 86400
        let hours = (uptime % 86400) / 3600
        let minutes = (uptime % 3600) / 60
        if days > 0 { return "\(days)d \(hours)h" }
        if hours > 0 { return "\(hours)h \(minutes)m" }
        return "\(minutes)m"
    }

    var tagList: [String] {
        guard let tags, !tags.isEmpty else { return [] }
        return tags.components(separatedBy: ";").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
    }
}

// MARK: - LXC Container

struct ProxmoxLXC: Decodable, Identifiable, Hashable {
    var id: String { "\(vmid)" }
    let vmid: Int
    let name: String?
    let status: String?
    let cpu: Double?
    let cpus: Int?
    let mem: Int64?
    let maxmem: Int64?
    let disk: Int64?
    let maxdisk: Int64?
    let diskread: Int64?
    let diskwrite: Int64?
    let netin: Int64?
    let netout: Int64?
    let uptime: Int?
    let template: Int?
    let tags: String?
    let lock: String?
    let pid: Int?
    let type: String?

    var isRunning: Bool { status?.lowercased() == "running" }
    var isStopped: Bool { status?.lowercased() == "stopped" }
    var isTemplate: Bool { template == 1 }

    var displayName: String {
        name ?? "CT \(vmid)"
    }

    var cpuPercent: Double {
        guard let cpu else { return 0 }
        return cpu * 100
    }

    var memPercent: Double {
        guard let mem, let maxmem, maxmem > 0 else { return 0 }
        return Double(mem) / Double(maxmem) * 100
    }

    var formattedUptime: String {
        guard let uptime, uptime > 0 else { return "-" }
        let days = uptime / 86400
        let hours = (uptime % 86400) / 3600
        let minutes = (uptime % 3600) / 60
        if days > 0 { return "\(days)d \(hours)h" }
        if hours > 0 { return "\(hours)h \(minutes)m" }
        return "\(minutes)m"
    }

    var tagList: [String] {
        guard let tags, !tags.isEmpty else { return [] }
        return tags.components(separatedBy: ";").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
    }
}

// MARK: - Storage

struct ProxmoxStorage: Decodable, Identifiable, Hashable {
    var id: String { storage }
    let storage: String
    let type: String?
    let used: Int64?
    let total: Int64?
    let avail: Int64?
    let active: Int?
    let content: String?
    let enabled: Int?
    let shared: Int?
    let used_fraction: Double?

    var isActive: Bool { active == 1 }
    var isEnabled: Bool { enabled == 1 }

    var usedPercent: Double {
        if let used_fraction { return used_fraction * 100 }
        guard let total, let used, total > 0 else { return 0 }
        return Double(used) / Double(total) * 100
    }

    var contentTypes: [String] {
        guard let content, !content.isEmpty else { return [] }
        return content.components(separatedBy: ",").map { $0.trimmingCharacters(in: .whitespaces) }
    }
}

// MARK: - Snapshot

struct ProxmoxSnapshot: Decodable, Identifiable, Hashable {
    var id: String { name }
    let name: String
    let description: String?
    let snaptime: Int?
    let vmstate: Int?
    let parent: String?

    var isCurrent: Bool { name == "current" }
    var hasVMState: Bool { vmstate == 1 }

    var formattedDate: String {
        guard let snaptime else { return "-" }
        let date = Date(timeIntervalSince1970: TimeInterval(snaptime))
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}

// MARK: - Task

struct ProxmoxTask: Decodable, Identifiable, Hashable {
    var id: String { upid }
    let upid: String
    let type: String?
    let status: String?
    let starttime: Int?
    let endtime: Int?
    let user: String?
    let node: String?
    let pstart: Int?
    let exitstatus: String?

    init(
        upid: String,
        type: String? = nil,
        status: String? = nil,
        starttime: Int? = nil,
        endtime: Int? = nil,
        user: String? = nil,
        node: String? = nil,
        pstart: Int? = nil,
        exitstatus: String? = nil
    ) {
        self.upid = upid
        self.type = type
        self.status = status
        self.starttime = starttime
        self.endtime = endtime
        self.user = user
        self.node = node
        self.pstart = pstart
        self.exitstatus = exitstatus
    }

    var isRunning: Bool {
        let normalizedStatus = status?.lowercased()
        if normalizedStatus == "running" {
            return true
        }
        if endtime != nil || exitstatus != nil {
            return false
        }
        return false
    }

    var isOk: Bool {
        exitstatus?.lowercased() == "ok" || status?.lowercased() == "ok"
    }

    var formattedStart: String {
        guard let starttime else { return "-" }
        let date = Date(timeIntervalSince1970: TimeInterval(starttime))
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }

    var duration: String {
        guard let starttime else { return "-" }
        let end = endtime ?? Int(Date().timeIntervalSince1970)
        let seconds = end - starttime
        if seconds < 60 { return "\(seconds)s" }
        if seconds < 3600 { return "\(seconds / 60)m \(seconds % 60)s" }
        return "\(seconds / 3600)h \(seconds % 3600 / 60)m"
    }
}

struct ProxmoxTaskReference: Hashable, Sendable {
    let upid: String
    let node: String?

    init(upid: String) {
        self.upid = upid
        self.node = Self.extractNodeName(from: upid)
    }

    private static func extractNodeName(from upid: String) -> String? {
        let parts = upid.split(separator: ":")
        guard parts.count > 1, parts.first == "UPID" else { return nil }
        let value = String(parts[1]).trimmingCharacters(in: .whitespacesAndNewlines)
        return value.isEmpty ? nil : value
    }
}

struct ProxmoxTaskLogEntry: Decodable, Identifiable, Hashable {
    var id: Int { n }
    let n: Int
    let t: String?
}

// MARK: - Cluster Resource (union type)

struct ProxmoxClusterResource: Decodable, Identifiable, Hashable {
    var id: String { "\(type ?? "")_\(resourceId)" }
    let type: String? // "node", "qemu", "lxc", "storage", "sdn"
    let status: String?
    let node: String?
    let vmid: Int?
    let name: String?
    let cpu: Double?
    let maxcpu: Int?
    let mem: Int64?
    let maxmem: Int64?
    let disk: Int64?
    let maxdisk: Int64?
    let uptime: Int?
    let template: Int?
    let pool: String?
    let hastate: String?
    let tags: String?
    let storage: String?
    let content: String?
    let plugintype: String?

    private enum CodingKeys: String, CodingKey {
        case type, status, node, vmid, name, cpu, maxcpu, mem, maxmem
        case disk, maxdisk, uptime, template, pool, hastate, tags
        case storage, content, plugintype
        case id = "id"
    }

    private var _apiId: String?

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        _apiId = try container.decodeIfPresent(String.self, forKey: .id)
        type = try container.decodeIfPresent(String.self, forKey: .type)
        status = try container.decodeIfPresent(String.self, forKey: .status)
        node = try container.decodeIfPresent(String.self, forKey: .node)
        vmid = try container.decodeIfPresent(Int.self, forKey: .vmid)
        name = try container.decodeIfPresent(String.self, forKey: .name)
        cpu = try container.decodeIfPresent(Double.self, forKey: .cpu)
        maxcpu = try container.decodeIfPresent(Int.self, forKey: .maxcpu)
        mem = try container.decodeIfPresent(Int64.self, forKey: .mem)
        maxmem = try container.decodeIfPresent(Int64.self, forKey: .maxmem)
        disk = try container.decodeIfPresent(Int64.self, forKey: .disk)
        maxdisk = try container.decodeIfPresent(Int64.self, forKey: .maxdisk)
        uptime = try container.decodeIfPresent(Int.self, forKey: .uptime)
        template = try container.decodeIfPresent(Int.self, forKey: .template)
        pool = try container.decodeIfPresent(String.self, forKey: .pool)
        hastate = try container.decodeIfPresent(String.self, forKey: .hastate)
        tags = try container.decodeIfPresent(String.self, forKey: .tags)
        storage = try container.decodeIfPresent(String.self, forKey: .storage)
        content = try container.decodeIfPresent(String.self, forKey: .content)
        plugintype = try container.decodeIfPresent(String.self, forKey: .plugintype)
    }

    var resourceId: String {
        _apiId ?? "\(node ?? "unknown")/\(vmid.map { String($0) } ?? name ?? storage ?? "?")"
    }

    var isQemu: Bool { type == "qemu" }
    var isLXC: Bool { type == "lxc" }
    var isNode: Bool { type == "node" }
    var isStorage: Bool { type == "storage" }
    var isRunning: Bool { status?.lowercased() == "running" || status?.lowercased() == "online" }
    var isTemplate: Bool { template == 1 }
}

// MARK: - VM/LXC Config (for detail views)

private struct ProxmoxDynamicCodingKey: CodingKey {
    let stringValue: String
    let intValue: Int?

    init?(stringValue: String) {
        self.stringValue = stringValue
        intValue = nil
    }

    init?(intValue: Int) {
        stringValue = "\(intValue)"
        self.intValue = intValue
    }
}

struct ProxmoxGuestConfig: Decodable {
    let name: String?
    let hostname: String?
    let description: String?
    let memory: Int?
    let cores: Int?
    let sockets: Int?
    let cpu: String?
    let ostype: String?
    let boot: String?
    let onboot: Int?
    let agent: String?
    let balloon: Int?
    let numa: Int?
    let hotplug: String?
    let scsihw: String?
    let bios: String?
    let machine: String?
    let tags: String?
    let protection: Int?
    let startup: String?
    let extraConfig: [String: String]

    private enum CodingKeys: String, CodingKey, CaseIterable {
        case name
        case hostname
        case description
        case memory
        case cores
        case sockets
        case cpu
        case ostype
        case boot
        case onboot
        case agent
        case balloon
        case numa
        case hotplug
        case scsihw
        case bios
        case machine
        case tags
        case protection
        case startup
    }

    private static let knownKeys = Set(CodingKeys.allCases.map(\.rawValue))

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        name = try container.decodeIfPresent(String.self, forKey: .name)
        hostname = try container.decodeIfPresent(String.self, forKey: .hostname)
        description = try container.decodeIfPresent(String.self, forKey: .description)
        memory = try container.decodeIfPresent(Int.self, forKey: .memory)
        cores = try container.decodeIfPresent(Int.self, forKey: .cores)
        sockets = try container.decodeIfPresent(Int.self, forKey: .sockets)
        cpu = try container.decodeIfPresent(String.self, forKey: .cpu)
        ostype = try container.decodeIfPresent(String.self, forKey: .ostype)
        boot = try container.decodeIfPresent(String.self, forKey: .boot)
        onboot = try container.decodeIfPresent(Int.self, forKey: .onboot)
        agent = try container.decodeIfPresent(String.self, forKey: .agent)
        balloon = try container.decodeIfPresent(Int.self, forKey: .balloon)
        numa = try container.decodeIfPresent(Int.self, forKey: .numa)
        hotplug = try container.decodeIfPresent(String.self, forKey: .hotplug)
        scsihw = try container.decodeIfPresent(String.self, forKey: .scsihw)
        bios = try container.decodeIfPresent(String.self, forKey: .bios)
        machine = try container.decodeIfPresent(String.self, forKey: .machine)
        tags = try container.decodeIfPresent(String.self, forKey: .tags)
        protection = try container.decodeIfPresent(Int.self, forKey: .protection)
        startup = try container.decodeIfPresent(String.self, forKey: .startup)

        let dynamic = try decoder.container(keyedBy: ProxmoxDynamicCodingKey.self)
        var extras: [String: String] = [:]
        for key in dynamic.allKeys where !Self.knownKeys.contains(key.stringValue) {
            if let value = try? dynamic.decode(String.self, forKey: key) {
                extras[key.stringValue] = value
            } else if let value = try? dynamic.decode(Int.self, forKey: key) {
                extras[key.stringValue] = "\(value)"
            } else if let value = try? dynamic.decode(Double.self, forKey: key) {
                extras[key.stringValue] = String(value)
            } else if let value = try? dynamic.decode(Bool.self, forKey: key) {
                extras[key.stringValue] = value ? "1" : "0"
            }
        }
        extraConfig = extras
    }

    var tagList: [String] {
        guard let tags, !tags.isEmpty else { return [] }
        return tags
            .components(separatedBy: ";")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    var displayName: String? {
        hostname ?? name
    }

    var rawConfigEntries: [(key: String, value: String)] {
        extraConfig
            .map { (key: $0.key, value: $0.value) }
            .sorted { $0.key.localizedStandardCompare($1.key) == .orderedAscending }
    }

    var networkInterfaces: [ProxmoxGuestNetworkInterface] {
        extraConfig.keys
            .filter { $0.range(of: #"^net\d+$"#, options: .regularExpression) != nil }
            .sorted { $0.localizedStandardCompare($1) == .orderedAscending }
            .compactMap { key in
                guard let rawValue = extraConfig[key] else { return nil }
                let parsed = Self.parseOptionString(rawValue)
                let suffix = String(key.dropFirst(3))
                let ipConfigRaw = extraConfig["ipconfig\(suffix)"]
                let ipConfig = ipConfigRaw.map(Self.parseOptionString)
                let primaryParts = parsed.primary.split(separator: "=", maxSplits: 1).map(String.init)
                let model = primaryParts.first?.trimmingCharacters(in: .whitespacesAndNewlines)
                let macAddress = primaryParts.count > 1 ? primaryParts[1].trimmingCharacters(in: .whitespacesAndNewlines) : nil
                return ProxmoxGuestNetworkInterface(
                    key: key,
                    model: model,
                    macAddress: macAddress,
                    bridge: parsed.options["bridge"],
                    vlanTag: parsed.options["tag"] ?? parsed.options["trunks"],
                    rateLimit: parsed.options["rate"],
                    firewallEnabled: Self.boolFlag(parsed.options["firewall"]),
                    ipAddress: ipConfig?.options["ip"],
                    ipv6Address: ipConfig?.options["ip6"],
                    gateway: ipConfig?.options["gw"],
                    gateway6: ipConfig?.options["gw6"],
                    rawValue: rawValue,
                    rawIpConfig: ipConfigRaw
                )
            }
    }

    var diskDevices: [ProxmoxGuestDiskDevice] {
        extraConfig.keys
            .filter { key in
                key == "rootfs" ||
                key.range(of: #"^mp\d+$"#, options: .regularExpression) != nil ||
                key.range(of: #"^(ide|sata|scsi|virtio|efidisk|tpmstate|unused)\d+$"#, options: .regularExpression) != nil
            }
            .sorted { $0.localizedStandardCompare($1) == .orderedAscending }
            .compactMap { key in
                guard let rawValue = extraConfig[key] else { return nil }
                let parsed = Self.parseOptionString(rawValue)
                let storageParts = parsed.primary.split(separator: ":", maxSplits: 1).map(String.init)
                let storage = storageParts.count > 1 ? storageParts[0].trimmingCharacters(in: .whitespacesAndNewlines) : nil
                let volume = (storageParts.count > 1 ? storageParts[1] : parsed.primary).trimmingCharacters(in: .whitespacesAndNewlines)
                return ProxmoxGuestDiskDevice(
                    key: key,
                    storage: storage,
                    volume: volume.isEmpty ? nil : volume,
                    size: parsed.options["size"],
                    mountPoint: parsed.options["mp"] ?? (key == "rootfs" ? "/" : nil),
                    media: parsed.options["media"],
                    backupEnabled: Self.boolFlag(parsed.options["backup"]),
                    replicateEnabled: Self.boolFlag(parsed.options["replicate"]),
                    discardEnabled: Self.boolFlag(parsed.options["discard"]),
                    ssdEnabled: Self.boolFlag(parsed.options["ssd"]),
                    rawValue: rawValue
                )
            }
    }

    var guestAgentEnabled: Bool? {
        guard let agent, !agent.isEmpty else { return nil }
        if let enabled = Self.boolFlag(agent) {
            return enabled
        }
        let parsed = Self.parseOptionString(agent)
        return Self.boolFlag(parsed.options["enabled"]) ?? true
    }

    private static func parseOptionString(_ rawValue: String) -> (primary: String, options: [String: String]) {
        let segments = rawValue.split(separator: ",").map {
            $0.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        guard let primary = segments.first else {
            return ("", [:])
        }

        var options: [String: String] = [:]
        for segment in segments where !segment.isEmpty {
            if let separatorIndex = segment.firstIndex(of: "=") {
                let key = segment[..<separatorIndex].trimmingCharacters(in: .whitespacesAndNewlines)
                let value = segment[segment.index(after: separatorIndex)...].trimmingCharacters(in: .whitespacesAndNewlines)
                options[key] = value
            } else {
                options[segment] = "1"
            }
        }

        return (primary, options)
    }

    private static func boolFlag(_ value: String?) -> Bool? {
        guard let value else { return nil }
        switch value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "1", "on", "yes", "true", "enabled":
            return true
        case "0", "off", "no", "false", "disabled":
            return false
        default:
            return nil
        }
    }
}

struct ProxmoxGuestNetworkInterface: Identifiable, Hashable {
    var id: String { key }

    let key: String
    let model: String?
    let macAddress: String?
    let bridge: String?
    let vlanTag: String?
    let rateLimit: String?
    let firewallEnabled: Bool?
    let ipAddress: String?
    let ipv6Address: String?
    let gateway: String?
    let gateway6: String?
    let rawValue: String
    let rawIpConfig: String?

    var displayName: String {
        key.uppercased()
    }
}

struct ProxmoxGuestDiskDevice: Identifiable, Hashable {
    var id: String { key }

    let key: String
    let storage: String?
    let volume: String?
    let size: String?
    let mountPoint: String?
    let media: String?
    let backupEnabled: Bool?
    let replicateEnabled: Bool?
    let discardEnabled: Bool?
    let ssdEnabled: Bool?
    let rawValue: String

    var displayName: String {
        key.uppercased()
    }
}

struct ProxmoxGuestAgentInfo: Decodable, Hashable {
    let version: String?
    let supportedCommands: [ProxmoxGuestAgentCommand]

    enum CodingKeys: String, CodingKey {
        case version
        case supportedCommands = "supported_commands"
    }
}

struct ProxmoxGuestAgentCommand: Decodable, Hashable, Identifiable {
    var id: String { name }

    let name: String
    let enabled: Bool?
    let successResponse: Bool?

    enum CodingKeys: String, CodingKey {
        case name
        case enabled
        case successResponse = "success-response"
    }
}

struct ProxmoxGuestAgentOSInfo: Decodable, Hashable {
    let name: String?
    let prettyName: String?
    let version: String?
    let versionId: String?
    let kernelRelease: String?
    let kernelVersion: String?
    let machine: String?

    enum CodingKeys: String, CodingKey {
        case name
        case prettyName = "pretty-name"
        case version
        case versionId = "version-id"
        case kernelRelease = "kernel-release"
        case kernelVersion = "kernel-version"
        case machine
    }

    var displayName: String {
        prettyName ?? name ?? "-"
    }

    var displayVersion: String? {
        version ?? versionId
    }

    var displayKernel: String? {
        let parts = [kernelRelease, kernelVersion]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        return parts.isEmpty ? nil : parts.joined(separator: " • ")
    }
}

struct ProxmoxGuestAgentHostname: Decodable, Hashable {
    let hostName: String?

    enum CodingKeys: String, CodingKey {
        case hostName = "host-name"
    }
}

struct ProxmoxGuestAgentUser: Decodable, Hashable, Identifiable {
    var id: String { "\(domain ?? ""):\(user)" }

    let user: String
    let domain: String?
    let loginTime: Double?

    enum CodingKeys: String, CodingKey {
        case user
        case domain
        case loginTime = "login-time"
    }

    var displayName: String {
        let trimmedDomain = domain?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !trimmedDomain.isEmpty else { return user }
        return "\(trimmedDomain)\\\(user)"
    }

    var loginDate: Date? {
        guard let loginTime else { return nil }
        return Date(timeIntervalSince1970: loginTime)
    }
}

struct ProxmoxGuestAgentTimezone: Decodable, Hashable {
    let zone: String?
    let offset: Int

    var offsetLabel: String {
        let sign = offset >= 0 ? "+" : "-"
        let absolute = abs(offset)
        let hours = absolute / 3600
        let minutes = (absolute % 3600) / 60
        return String(format: "UTC%@%02d:%02d", sign, hours, minutes)
    }

    var displayName: String {
        let trimmedZone = zone?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !trimmedZone.isEmpty else { return offsetLabel }
        return "\(trimmedZone) (\(offsetLabel))"
    }
}

struct ProxmoxGuestAgentFilesystem: Decodable, Hashable, Identifiable {
    var id: String { "\(mountpoint)|\(name)" }

    let name: String
    let mountpoint: String
    let type: String
    let usedBytes: Int64?
    let totalBytes: Int64?
    let totalBytesPrivileged: Int64?
    let disk: [ProxmoxGuestAgentFilesystemDisk]

    enum CodingKeys: String, CodingKey {
        case name
        case mountpoint
        case type
        case usedBytes = "used-bytes"
        case totalBytes = "total-bytes"
        case totalBytesPrivileged = "total-bytes-privileged"
        case disk
    }

    var capacityBytes: Int64? {
        totalBytes ?? totalBytesPrivileged
    }

    var usagePercent: Double {
        guard let usedBytes, let capacityBytes, capacityBytes > 0 else { return 0 }
        return Double(usedBytes) / Double(capacityBytes)
    }

    var diskSummary: String? {
        let parts = disk.compactMap { $0.displayName }
        guard !parts.isEmpty else { return nil }
        return parts.joined(separator: ", ")
    }
}

struct ProxmoxGuestAgentFilesystemDisk: Decodable, Hashable, Identifiable {
    var id: String {
        [busType, dev, serial]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: "|")
    }

    let busType: String?
    let dev: String?
    let serial: String?

    enum CodingKeys: String, CodingKey {
        case busType = "bus-type"
        case dev
        case serial
    }

    var displayName: String? {
        let parts = [busType, dev ?? serial]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
        guard !parts.isEmpty else { return nil }
        return parts.joined(separator: " • ")
    }
}

struct ProxmoxGuestAgentNetworkInterface: Decodable, Hashable, Identifiable {
    var id: String { name }

    let name: String
    let hardwareAddress: String?
    let ipAddresses: [ProxmoxGuestAgentIPAddress]

    enum CodingKeys: String, CodingKey {
        case name
        case hardwareAddress = "hardware-address"
        case ipAddresses = "ip-addresses"
    }

    var visibleAddresses: [ProxmoxGuestAgentIPAddress] {
        let filtered = ipAddresses.filter { !$0.isLoopback }
        return filtered.isEmpty ? ipAddresses : filtered
    }
}

struct ProxmoxGuestAgentIPAddress: Decodable, Hashable, Identifiable {
    var id: String { "\(type)-\(address)-\(prefix ?? -1)" }

    let address: String
    let type: String
    let prefix: Int?

    enum CodingKeys: String, CodingKey {
        case address = "ip-address"
        case type = "ip-address-type"
        case prefix
    }

    var displayLabel: String {
        if let prefix {
            return "\(address)/\(prefix)"
        }
        return address
    }

    var isLoopback: Bool {
        if type.lowercased() == "ipv4" {
            return address.hasPrefix("127.")
        }
        return address == "::1"
    }
}

// MARK: - RRD Stats

struct ProxmoxRRDData: Decodable {
    let time: Double?
    let cpu: Double?
    let maxcpu: Double?
    let mem: Double?
    let maxmem: Double?
    let netin: Double?
    let netout: Double?
    let diskread: Double?
    let diskwrite: Double?

    var date: Date? {
        guard let time else { return nil }
        return Date(timeIntervalSince1970: time)
    }

    var cpuPercent: Double {
        max(0, min((cpu ?? 0) * 100, 100))
    }

    var memoryPercent: Double {
        guard let mem, let maxmem, maxmem > 0 else { return 0 }
        return max(0, min((mem / maxmem) * 100, 100))
    }

    var networkRate: Double {
        max((netin ?? 0) + (netout ?? 0), 0)
    }

    var diskRate: Double {
        max((diskread ?? 0) + (diskwrite ?? 0), 0)
    }

    var hasData: Bool {
        cpu != nil || mem != nil || netin != nil || netout != nil || diskread != nil || diskwrite != nil
    }
}

enum ProxmoxRRDTimeframe: String, CaseIterable, Identifiable, Sendable {
    case hour
    case day
    case week

    var id: String { rawValue }

    var apiValue: String { rawValue }
}

// MARK: - Storage Content

struct ProxmoxStorageContent: Decodable, Identifiable, Hashable {
    var id: String { volid }
    let volid: String
    let content: String?
    let format: String?
    let size: Int64?
    let ctime: Int?
    let vmid: Int?
    let notes: String?
    let verification: ProxmoxVerification?
    let protected: Int?

    var isProtected: Bool { `protected` == 1 }

    var contentType: String {
        content ?? "unknown"
    }

    var formattedSize: String {
        guard let size else { return "-" }
        return Formatters.formatBytes(Double(size))
    }

    var formattedDate: String {
        guard let ctime else { return "-" }
        let date = Date(timeIntervalSince1970: TimeInterval(ctime))
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }

    var backupMetadata: ProxmoxBackupArchiveMetadata? {
        ProxmoxBackupArchiveMetadata.parse(from: volid)
    }
}

struct ProxmoxVerification: Decodable, Hashable {
    let state: String?
}

struct ProxmoxBackupArchiveMetadata: Hashable, Sendable {
    let guestType: ProxmoxGuestType
    let vmid: Int
    let archiveName: String

    static func parse(from volumeId: String) -> ProxmoxBackupArchiveMetadata? {
        let archiveName = volumeId.components(separatedBy: "/").last ?? volumeId
        let pattern = #"vzdump-(qemu|lxc)-(\d+)-(.+)"#
        guard let regex = try? NSRegularExpression(pattern: pattern) else { return nil }
        let nsRange = NSRange(archiveName.startIndex..<archiveName.endIndex, in: archiveName)
        guard let match = regex.firstMatch(in: archiveName, range: nsRange),
              match.numberOfRanges == 4,
              let guestTypeRange = Range(match.range(at: 1), in: archiveName),
              let vmidRange = Range(match.range(at: 2), in: archiveName),
              let vmid = Int(archiveName[vmidRange]) else {
            return nil
        }

        let guestTypeRawValue = String(archiveName[guestTypeRange])
        guard let guestType = ProxmoxGuestType(rawValue: guestTypeRawValue) else { return nil }

        return ProxmoxBackupArchiveMetadata(
            guestType: guestType,
            vmid: vmid,
            archiveName: archiveName
        )
    }

    var guestTypeLabel: String {
        guestType == .qemu ? "QEMU" : "LXC"
    }
}

// MARK: - Firewall

struct ProxmoxFirewallRule: Decodable, Identifiable, Hashable {
    var id: String { "\(pos ?? 0)_\(type ?? "")_\(action ?? "")" }
    let pos: Int?
    let type: String?      // "in", "out", "group"
    let action: String?    // "ACCEPT", "DROP", "REJECT"
    let enable: Int?
    let comment: String?
    let source: String?
    let dest: String?
    let proto: String?
    let dport: String?
    let sport: String?
    let iface: String?
    let log: String?
    let macro: String?

    var isEnabled: Bool { enable == 1 || enable == nil }

    var displayAction: String {
        action?.uppercased() ?? "ACCEPT"
    }

    var displayDirection: String {
        switch type?.lowercased() {
        case "in": return "IN"
        case "out": return "OUT"
        case "group": return "GROUP"
        default: return type?.uppercased() ?? "?"
        }
    }
}

struct ProxmoxFirewallOptions: Decodable {
    let enable: Int?
    let policy_in: String?
    let policy_out: String?
    let log_ratelimit: String?

    var isEnabled: Bool { enable == 1 }
}

// MARK: - Backup Job

struct ProxmoxBackupJob: Decodable, Identifiable, Hashable {
    var id: String {
        let fallback = [storage, node, schedule, vmid, pool, mode]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: "|")
        return jobId ?? (fallback.isEmpty ? "backup-job" : fallback)
    }
    let jobId: String?
    let enabled: Int?
    let schedule: String?
    let storage: String?
    let mode: String?
    let compress: String?
    let vmid: String?       // comma separated
    let all: Int?
    let mailnotification: String?
    let mailto: String?
    let pool: String?
    let exclude: String?
    let node: String?
    let dow: String?         // deprecated, now schedule
    let starttime: String?   // deprecated, now schedule
    let notes_template: String?

    private enum CodingKeys: String, CodingKey {
        case enabled, schedule, storage, mode, compress, vmid, all
        case mailnotification, mailto, pool, exclude, node, dow, starttime
        case notes_template
        case jobId = "id"
    }

    var isEnabled: Bool { enabled == 1 || enabled == nil }
    var backupAll: Bool { all == 1 }

    var vmidList: [String] {
        guard let vmid, !vmid.isEmpty else { return [] }
        return vmid.components(separatedBy: ",").map { $0.trimmingCharacters(in: .whitespaces) }
    }
}

// MARK: - Network

struct ProxmoxNetwork: Decodable, Identifiable, Hashable {
    var id: String { iface }
    let iface: String
    let type: String?       // "bridge", "bond", "eth", "vlan", etc.
    let active: Int?
    let autostart: Int?
    let method: String?     // "static", "dhcp", "manual"
    let address: String?
    let netmask: String?
    let gateway: String?
    let cidr: String?
    let bridge_ports: String?
    let bridge_stp: String?
    let bridge_fd: String?
    let bond_mode: String?
    let slaves: String?
    let comments: String?
    let families: [String]?
    let method6: String?
    let address6: String?
    let netmask6: String?
    let gateway6: String?
    let cidr6: String?

    var isActive: Bool { active == 1 }
    var isAutostart: Bool { autostart == 1 }

    var typeIcon: String {
        switch type {
        case "bridge": return "network"
        case "bond": return "link"
        case "eth": return "cable.connector.horizontal"
        case "vlan": return "tag.fill"
        case "OVSBridge": return "square.stack.3d.up.fill"
        default: return "network"
        }
    }
}

// MARK: - Pool

struct ProxmoxPool: Decodable, Identifiable, Hashable {
    var id: String { poolid }
    let poolid: String
    let comment: String?
}

struct ProxmoxPoolDetail: Decodable {
    let members: [ProxmoxPoolMember]?
    let comment: String?
}

struct ProxmoxPoolMember: Decodable, Identifiable, Hashable {
    var id: String { "\(type ?? "")_\(vmid ?? 0)_\(storage ?? "")" }
    let type: String?    // "qemu", "lxc", "storage"
    let vmid: Int?
    let name: String?
    let status: String?
    let node: String?
    let storage: String?
}

// MARK: - HA Resource

struct ProxmoxHAResource: Decodable, Identifiable, Hashable {
    var id: String { sid }
    let sid: String          // e.g. "vm:100"
    let type: String?
    let state: String?       // "started", "stopped", "enabled", "disabled"
    let group: String?
    let max_relocate: Int?
    let max_restart: Int?
    let comment: String?
    let status: String?
    let request_state: String?

    var vmid: Int? {
        let parts = sid.components(separatedBy: ":")
        guard parts.count == 2, let id = Int(parts[1]) else { return nil }
        return id
    }

    var resourceType: String {
        let parts = sid.components(separatedBy: ":")
        return parts.first ?? "vm"
    }
}

// MARK: - HA Group

struct ProxmoxHAGroup: Decodable, Identifiable, Hashable {
    var id: String { group }
    let group: String
    let nodes: String?      // "node1:1,node2:2"
    let restricted: Int?
    let nofailback: Int?
    let comment: String?
    let type: String?

    var nodeList: [String] {
        guard let nodes, !nodes.isEmpty else { return [] }
        return nodes.components(separatedBy: ",").map { $0.components(separatedBy: ":").first ?? $0 }
    }
}

// MARK: - Replication

struct ProxmoxReplicationJob: Decodable, Identifiable, Hashable {
    var id: String {
        let fallback = [source, target, guest.map(String.init), schedule, type]
            .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
            .joined(separator: "|")
        return jobId ?? (fallback.isEmpty ? "replication-job" : fallback)
    }
    let jobId: String?
    let type: String?
    let source: String?
    let target: String?
    let guest: Int?
    let schedule: String?
    let rate: Double?
    let comment: String?
    let disable: Int?
    let remove_job: String?
    let error: String?
    let duration: Double?

    private enum CodingKeys: String, CodingKey {
        case type, source, target, guest, schedule, rate, comment
        case disable, remove_job, error, duration
        case jobId = "id"
    }

    var isEnabled: Bool { disable != 1 }
}

// MARK: - DNS

struct ProxmoxDNS: Decodable {
    let search: String?
    let dns1: String?
    let dns2: String?
    let dns3: String?
}

// MARK: - APT Package

struct ProxmoxAptPackage: Decodable, Identifiable, Hashable {
    var id: String { "\(package ?? "")-\(version ?? "")" }
    let package: String?
    let title: String?
    let description: String?
    let version: String?
    let old_version: String?
    let arch: String?
    let section: String?
    let priority: String?
    let origin: String?
    let change_log_url: String?

    private enum CodingKeys: String, CodingKey {
        case package = "Package"
        case title = "Title"
        case description = "Description"
        case version = "Version"
        case old_version = "OldVersion"
        case arch = "Arch"
        case section = "Section"
        case priority = "Priority"
        case origin = "Origin"
        case change_log_url = "ChangeLogUrl"
    }
}

// MARK: - Node Service

struct ProxmoxService: Decodable, Identifiable, Hashable {
    var id: String { service }
    let service: String
    let name: String?
    let desc: String?
    let state: String?

    var isRunning: Bool { state?.lowercased() == "running" }
}

// MARK: - Ceph

struct ProxmoxCephStatus: Decodable {
    let health: ProxmoxCephHealth?
    let pgmap: ProxmoxCephPGMap?
    let osdmap: ProxmoxCephOSDMap?
    let monmap: ProxmoxCephMonMap?
    let quorum_names: [String]?
}

struct ProxmoxCephHealth: Decodable {
    let status: String?   // "HEALTH_OK", "HEALTH_WARN", "HEALTH_ERR"
    let checks: [String: ProxmoxCephHealthCheck]?

    var isHealthy: Bool { status == "HEALTH_OK" }
    var isWarning: Bool { status == "HEALTH_WARN" }
}

struct ProxmoxCephHealthCheck: Decodable {
    let severity: String?
    let summary: ProxmoxCephCheckSummary?
}

struct ProxmoxCephCheckSummary: Decodable {
    let message: String?
    let count: Int?
}

struct ProxmoxCephPGMap: Decodable {
    let bytes_total: Int64?
    let bytes_used: Int64?
    let bytes_avail: Int64?
    let data_bytes: Int64?
    let num_pgs: Int?
    let read_bytes_sec: Int64?
    let write_bytes_sec: Int64?
    let read_op_per_sec: Int?
    let write_op_per_sec: Int?
}

struct ProxmoxCephOSDMap: Decodable {
    let num_osds: Int?
    let num_up_osds: Int?
    let num_in_osds: Int?
    let full: Bool?
    let nearfull: Bool?
}

struct ProxmoxCephMonMap: Decodable {
    let num_mons: Int?
}

struct ProxmoxCephOSDTree: Decodable {
    let nodes: [ProxmoxCephOSDNode]?
}

struct ProxmoxCephOSDNode: Decodable, Identifiable, Hashable {
    var id: Int { osdId }
    let osdId: Int
    let name: String?
    let type: String?     // "osd", "host", "root"
    let status: String?
    let crush_weight: Double?
    let reweight: Double?

    private enum CodingKeys: String, CodingKey {
        case name, type, status, crush_weight, reweight
        case osdId = "id"
    }
}

struct ProxmoxCephPool: Decodable, Identifiable, Hashable {
    var id: String { "\(pool_name ?? "")-\(pool ?? 0)" }
    let pool: Int?
    let pool_name: String?
    let size: Int?
    let min_size: Int?
    let pg_num: Int?
    let pg_num_min: Int?
    let bytes_used: Int64?
    let percent_used: Double?
    let crush_rule: Int?
    let crush_rule_name: String?
    let type: String?
}

// MARK: - Provisioning

struct ProxmoxFlexibleInt: Decodable, Hashable, Sendable {
    let value: Int

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let intValue = try? container.decode(Int.self) {
            value = intValue
            return
        }
        if let stringValue = try? container.decode(String.self),
           let intValue = Int(stringValue.trimmingCharacters(in: .whitespacesAndNewlines)) {
            value = intValue
            return
        }
        throw DecodingError.dataCorruptedError(in: container, debugDescription: "Expected integer or numeric string.")
    }
}

struct ProxmoxVMCreationRequest: Hashable, Sendable {
    let vmid: Int
    let name: String
    let node: String
    let diskStorage: String
    let diskSizeGiB: Int
    let memoryMB: Int
    let cores: Int
    let sockets: Int
    let bridge: String
    let isoVolumeId: String?
    let osType: String
    let bios: String
    let machine: String
    let pool: String?
    let tags: String?
    let description: String?
    let enableGuestAgent: Bool
    let startAtBoot: Bool
    let createAsTemplate: Bool

    func formParameters() -> [String: String] {
        var params: [String: String] = [
            "vmid": "\(vmid)",
            "cores": "\(max(1, cores))",
            "sockets": "\(max(1, sockets))",
            "memory": "\(max(256, memoryMB))",
            "ostype": osType,
            "bios": bios,
            "machine": machine,
            "scsihw": "virtio-scsi-single",
            "scsi0": "\(diskStorage):\(max(1, diskSizeGiB))",
            "net0": "virtio,bridge=\(bridge)",
            "boot": isoVolumeId == nil ? "order=scsi0;net0" : "order=scsi0;ide2;net0",
            "onboot": startAtBoot ? "1" : "0",
            "agent": enableGuestAgent ? "enabled=1" : "enabled=0"
        ]

        let trimmedName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmedName.isEmpty {
            params["name"] = trimmedName
        }

        if let isoVolumeId {
            params["ide2"] = "\(isoVolumeId),media=cdrom"
        }

        let trimmedPool = pool?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !trimmedPool.isEmpty {
            params["pool"] = trimmedPool
        }

        let trimmedTags = tags?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !trimmedTags.isEmpty {
            params["tags"] = trimmedTags
        }

        let trimmedDescription = description?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !trimmedDescription.isEmpty {
            params["description"] = trimmedDescription
        }

        if createAsTemplate {
            params["template"] = "1"
        }

        return params
    }
}

enum ProxmoxLXCAddressMode: String, CaseIterable, Identifiable, Hashable, Sendable {
    case dhcp
    case staticAddress
    case manual

    var id: String { rawValue }
}

struct ProxmoxLXCCreationRequest: Hashable, Sendable {
    let vmid: Int
    let hostname: String
    let node: String
    let ostemplate: String
    let rootfsStorage: String
    let rootfsSizeGiB: Int
    let memoryMB: Int
    let swapMB: Int
    let cores: Int
    let bridge: String
    let addressMode: ProxmoxLXCAddressMode
    let ipv4Address: String?
    let gateway: String?
    let password: String?
    let pool: String?
    let tags: String?
    let description: String?
    let unprivileged: Bool
    let startAtBoot: Bool

    func formParameters() -> [String: String] {
        var params: [String: String] = [
            "vmid": "\(vmid)",
            "hostname": hostname.trimmingCharacters(in: .whitespacesAndNewlines),
            "ostemplate": ostemplate,
            "rootfs": "\(rootfsStorage):\(max(1, rootfsSizeGiB))",
            "memory": "\(max(128, memoryMB))",
            "swap": "\(max(0, swapMB))",
            "cores": "\(max(1, cores))",
            "unprivileged": unprivileged ? "1" : "0",
            "onboot": startAtBoot ? "1" : "0"
        ]

        let trimmedBridge = bridge.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmedBridge.isEmpty {
            var networkParts = ["name=eth0", "bridge=\(trimmedBridge)"]
            switch addressMode {
            case .dhcp:
                networkParts.append("ip=dhcp")
            case .manual:
                networkParts.append("ip=manual")
            case .staticAddress:
                let trimmedAddress = ipv4Address?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                if !trimmedAddress.isEmpty {
                    networkParts.append("ip=\(trimmedAddress)")
                }
                let trimmedGateway = gateway?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                if !trimmedGateway.isEmpty {
                    networkParts.append("gw=\(trimmedGateway)")
                }
            }
            params["net0"] = networkParts.joined(separator: ",")
        }

        let trimmedPassword = password?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !trimmedPassword.isEmpty {
            params["password"] = trimmedPassword
        }

        let trimmedPool = pool?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !trimmedPool.isEmpty {
            params["pool"] = trimmedPool
        }

        let trimmedTags = tags?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !trimmedTags.isEmpty {
            params["tags"] = trimmedTags
        }

        let trimmedDescription = description?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !trimmedDescription.isEmpty {
            params["description"] = trimmedDescription
        }

        return params
    }
}

struct ProxmoxVMRestoreRequest: Hashable, Sendable {
    let vmid: Int
    let archiveVolumeId: String
    let storage: String
    let unique: Bool
    let force: Bool
    let pool: String?

    func formParameters() -> [String: String] {
        var params: [String: String] = [
            "vmid": "\(vmid)",
            "archive": archiveVolumeId,
            "storage": storage
        ]

        if unique {
            params["unique"] = "1"
        }

        if force {
            params["force"] = "1"
        }

        let trimmedPool = pool?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !trimmedPool.isEmpty {
            params["pool"] = trimmedPool
        }

        return params
    }
}

struct ProxmoxLXCRestoreRequest: Hashable, Sendable {
    let vmid: Int
    let archiveVolumeId: String
    let storage: String
    let unique: Bool
    let force: Bool
    let pool: String?

    func formParameters() -> [String: String] {
        var params: [String: String] = [
            "vmid": "\(vmid)",
            "ostemplate": archiveVolumeId,
            "storage": storage,
            "restore": "1"
        ]

        if unique {
            params["unique"] = "1"
        }

        if force {
            params["force"] = "1"
        }

        let trimmedPool = pool?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        if !trimmedPool.isEmpty {
            params["pool"] = trimmedPool
        }

        return params
    }
}
