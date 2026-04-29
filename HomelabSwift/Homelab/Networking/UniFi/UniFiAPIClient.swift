import Foundation

struct UniFiDashboardData: Sendable {
    let mode: UniFiAuthMode
    let hosts: [UniFiHost]
    let sites: [UniFiSite]
    let devices: [UniFiDevice]
    let clients: [UniFiClient]
    let ispMetrics: [UniFiISPMetricSeries]
    let networks: [UniFiNetwork]

    var primarySite: UniFiSite? { sites.first }
    var totalClients: Int {
        let fromSites = sites.reduce(0) { $0 + $1.totalClients }
        return max(fromSites, clients.count)
    }
    var wirelessClients: Int {
        let fromSites = sites.reduce(0) { $0 + ($1.counts.wifiClient ?? 0) }
        return max(fromSites, clients.filter { $0.type?.uppercased() == "WIRELESS" }.count)
    }
    var wiredClients: Int {
        let fromSites = sites.reduce(0) { $0 + ($1.counts.wiredClient ?? 0) }
        return max(fromSites, clients.filter { $0.type?.uppercased() == "WIRED" }.count)
    }
    var offlineDevices: Int {
        let siteOffline = sites.reduce(0) { $0 + $1.offlineDevices }
        let deviceOffline = devices.filter { !$0.isOnline }.count
        return max(siteOffline, deviceOffline)
    }
    var totalDevices: Int {
        let siteTotal = sites.reduce(0) { $0 + ($1.counts.totalDevice ?? 0) }
        return max(siteTotal, devices.count)
    }
    var onlineDevices: Int { max(totalDevices - offlineDevices, devices.filter(\.isOnline).count) }
    var latestWAN: UniFiISPMetricPoint? {
        ispMetrics.flatMap(\.periods).sorted { $0.date < $1.date }.last
    }
    var pendingUpdates: Int { devices.filter { $0.updateAvailable == true }.count }
    var criticalAlerts: Int { sites.reduce(0) { $0 + ($1.counts.criticalNotification ?? 0) } }
    var unauthorizedGuests: Int { clients.filter(\.isGuestUnauthorized).count }

    func scoped(to siteId: String?) -> UniFiDashboardData {
        guard let siteId else { return self }
        let filteredSites = sites.filter { $0.siteId == siteId }
        let filteredHostIds = Set(filteredSites.compactMap(\.hostId))
        return UniFiDashboardData(
            mode: mode,
            hosts: hosts.filter { filteredHostIds.isEmpty || filteredHostIds.contains($0.id) || filteredHostIds.contains($0.hardwareId ?? "") },
            sites: filteredSites,
            devices: devices.filter { $0.siteId == siteId },
            clients: clients.filter { $0.siteId == siteId },
            ispMetrics: ispMetrics.filter { metric in
                if metric.siteId == siteId {
                    return true
                }
                guard let hostId = metric.hostId, !filteredHostIds.isEmpty else {
                    return false
                }
                return filteredHostIds.contains(hostId)
            },
            networks: networks.filter { $0.siteId == siteId }
        )
    }
}

struct UniFiHost: Identifiable, Decodable, Sendable, Hashable {
    let id: String
    let hardwareId: String?
    let type: String?
    let ipAddress: String?
    let owner: Bool?
    let isBlocked: Bool?
    let reportedState: UniFiReportedState?

    var displayName: String {
        reportedState?.name ?? reportedState?.hostname ?? type?.capitalized ?? "UniFi Host"
    }

    var state: String {
        reportedState?.state ?? "unknown"
    }

    var isConnected: Bool {
        ["connected", "online", "ready"].contains(state.lowercased())
    }
}

struct UniFiReportedState: Decodable, Sendable, Hashable {
    let name: String?
    let hostname: String?
    let state: String?
    let version: String?
    let firmwareVersion: String?

    enum CodingKeys: String, CodingKey {
        case name
        case hostname
        case state
        case version
        case firmwareVersion = "firmware_version"
    }
}

struct UniFiSite: Identifiable, Decodable, Sendable, Hashable {
    let siteId: String
    let hostId: String?
    let meta: UniFiSiteMeta?
    let statistics: UniFiSiteStatistics?
    let permission: String?
    let isOwner: Bool?

    var id: String { siteId }
    var displayName: String {
        meta?.descriptionText ?? meta?.name ?? "Site"
    }
    var counts: UniFiSiteCounts { statistics?.counts ?? UniFiSiteCounts() }
    var totalClients: Int { (counts.wifiClient ?? 0) + (counts.wiredClient ?? 0) + (counts.guestClient ?? 0) }
    var offlineDevices: Int {
        counts.offlineDevice ?? ((counts.offlineGatewayDevice ?? 0) + (counts.offlineWifiDevice ?? 0) + (counts.offlineWiredDevice ?? 0))
    }
    var ispName: String? { statistics?.ispInfo?.name ?? statistics?.ispInfo?.organization }
    var gatewayName: String? { statistics?.gateway?.shortname ?? meta?.gatewayMac }

    enum CodingKeys: String, CodingKey {
        case siteId
        case id
        case hostId
        case meta
        case name
        case statistics
        case permission
        case isOwner
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let resolvedId = try container.decodeIfPresent(String.self, forKey: .siteId)
            ?? container.decodeIfPresent(String.self, forKey: .id)
            ?? UUID().uuidString
        let localName = try container.decodeIfPresent(String.self, forKey: .name)
        siteId = resolvedId
        hostId = try container.decodeIfPresent(String.self, forKey: .hostId)
        meta = try container.decodeIfPresent(UniFiSiteMeta.self, forKey: .meta)
            ?? UniFiSiteMeta(name: localName, descriptionText: localName, timezone: nil, gatewayMac: nil)
        statistics = try container.decodeIfPresent(UniFiSiteStatistics.self, forKey: .statistics)
        permission = try container.decodeIfPresent(String.self, forKey: .permission)
        isOwner = try container.decodeIfPresent(Bool.self, forKey: .isOwner)
    }
}

struct UniFiSiteMeta: Decodable, Sendable, Hashable {
    let name: String?
    let descriptionText: String?
    let timezone: String?
    let gatewayMac: String?

    enum CodingKeys: String, CodingKey {
        case name
        case descriptionText = "desc"
        case timezone
        case gatewayMac
    }

    init(name: String?, descriptionText: String?, timezone: String?, gatewayMac: String?) {
        self.name = name
        self.descriptionText = descriptionText
        self.timezone = timezone
        self.gatewayMac = gatewayMac
    }
}

struct UniFiSiteStatistics: Decodable, Sendable, Hashable {
    let counts: UniFiSiteCounts?
    let gateway: UniFiGatewayInfo?
    let ispInfo: UniFiISPInfo?
    let internetIssues: [String]?
    let percentages: UniFiSitePercentages?
}

struct UniFiSitePercentages: Decodable, Sendable, Hashable {
    let wanUptime: Double?
}

struct UniFiSiteCounts: Decodable, Sendable, Hashable {
    let criticalNotification: Int?
    let gatewayDevice: Int?
    let guestClient: Int?
    let offlineDevice: Int?
    let offlineGatewayDevice: Int?
    let offlineWifiDevice: Int?
    let offlineWiredDevice: Int?
    let pendingUpdateDevice: Int?
    let totalDevice: Int?
    let wifiClient: Int?
    let wiredClient: Int?
    let wifiDevice: Int?
    let wiredDevice: Int?

    init(
        criticalNotification: Int? = nil,
        gatewayDevice: Int? = nil,
        guestClient: Int? = nil,
        offlineDevice: Int? = nil,
        offlineGatewayDevice: Int? = nil,
        offlineWifiDevice: Int? = nil,
        offlineWiredDevice: Int? = nil,
        pendingUpdateDevice: Int? = nil,
        totalDevice: Int? = nil,
        wifiClient: Int? = nil,
        wiredClient: Int? = nil,
        wifiDevice: Int? = nil,
        wiredDevice: Int? = nil
    ) {
        self.criticalNotification = criticalNotification
        self.gatewayDevice = gatewayDevice
        self.guestClient = guestClient
        self.offlineDevice = offlineDevice
        self.offlineGatewayDevice = offlineGatewayDevice
        self.offlineWifiDevice = offlineWifiDevice
        self.offlineWiredDevice = offlineWiredDevice
        self.pendingUpdateDevice = pendingUpdateDevice
        self.totalDevice = totalDevice
        self.wifiClient = wifiClient
        self.wiredClient = wiredClient
        self.wifiDevice = wifiDevice
        self.wiredDevice = wiredDevice
    }
}

struct UniFiGatewayInfo: Decodable, Sendable, Hashable {
    let hardwareId: String?
    let shortname: String?
    let inspectionState: String?
    let ipsMode: String?
}

struct UniFiISPInfo: Decodable, Sendable, Hashable {
    let name: String?
    let organization: String?
}

struct UniFiDevice: Identifiable, Decodable, Sendable, Hashable {
    let id: String
    let name: String?
    let model: String?
    let type: String?
    let macAddress: String?
    let ipAddress: String?
    let state: String?
    let version: String?
    let firmwareVersion: String?
    let siteId: String?
    let updateAvailable: Bool?
    let uplinkDeviceName: String?
    let serialNumber: String?
    let clientCount: Int?
    let guestClientCount: Int?
    let totalRxBytes: Int64?
    let totalTxBytes: Int64?
    let rxRateBytesPerSecond: Double?
    let txRateBytesPerSecond: Double?
    let cpuUsagePercent: Double?
    let memoryUsagePercent: Double?
    let temperatureCelsius: Double?
    let ports: [UniFiDevicePort]
    let uplink: UniFiDeviceUplink?
    let radios: [UniFiDeviceRadio]

    var displayName: String {
        name?.nilIfEmpty ?? model?.nilIfEmpty ?? macAddress?.nilIfEmpty ?? "UniFi Device"
    }

    var kindLabel: String {
        (type ?? model ?? "device").uppercased()
    }

    var isOnline: Bool {
        guard let state = state?.lowercased() else { return true }
        return ["online", "connected", "ready", "active"].contains(state)
    }

    var activePortCount: Int {
        ports.filter { $0.up == true }.count
    }

    var totalPortCount: Int {
        ports.count
    }

    var poePowerWatts: Double? {
        let total = ports.compactMap(\.poePowerWatts).reduce(0, +)
        return total > 0 ? total : nil
    }

    var liveRxRateBytesPerSecond: Double? {
        let direct = rxRateBytesPerSecond ?? uplink?.rxRateBytesPerSecond
        if let direct, direct > 0 { return direct }
        let summed = ports.compactMap(\.rxRateBytesPerSecond).reduce(0, +)
        return summed > 0 ? summed : nil
    }

    var liveTxRateBytesPerSecond: Double? {
        let direct = txRateBytesPerSecond ?? uplink?.txRateBytesPerSecond
        if let direct, direct > 0 { return direct }
        let summed = ports.compactMap(\.txRateBytesPerSecond).reduce(0, +)
        return summed > 0 ? summed : nil
    }

    var liveTrafficBytesPerSecond: Double? {
        let total = (liveRxRateBytesPerSecond ?? 0) + (liveTxRateBytesPerSecond ?? 0)
        return total > 0 ? total : nil
    }

    var hasHealthStats: Bool {
        cpuUsagePercent != nil || memoryUsagePercent != nil || temperatureCelsius != nil
    }

    enum CodingKeys: String, CodingKey {
        case id
        case name
        case model
        case type
        case macAddress
        case ipAddress
        case state
        case version
        case firmwareVersion
        case siteId
        case updateAvailable
        case uplinkDeviceName
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let root = try? decoder.container(keyedBy: UniFiDynamicCodingKey.self)
        let uidb = (try? decoder.container(keyedBy: UniFiDynamicCodingKey.self))
            .flatMap { try? $0.nestedContainer(keyedBy: UniFiDynamicCodingKey.self, forKey: UniFiDynamicCodingKey("uidb")) }
        let uplink = (uidb?.nested("uplink") ?? root?.nested("uplink")).flatMap { try? UniFiDeviceUplink(from: $0) }
        let ports = uidb?.array(UniFiDevicePort.self, "port_table")
            ?? root?.array(UniFiDevicePort.self, "port_table")
            ?? root?.array(UniFiDevicePort.self, "ports")
            ?? []
        let radios = uidb?.array(UniFiDeviceRadio.self, "radio_table")
            ?? root?.array(UniFiDeviceRadio.self, "radio_table")
            ?? root?.array(UniFiDeviceRadio.self, "radios")
            ?? []

        let fallbackId = try container.decodeIfPresent(String.self, forKey: .macAddress)
            ?? uidb?.string("mac")
            ?? root?.string("mac")
            ?? root?.string("mac_address")
            ?? UUID().uuidString
        id = try container.decodeIfPresent(String.self, forKey: .id) ?? uidb?.string("_id") ?? root?.string("_id") ?? fallbackId
        name = try container.decodeIfPresent(String.self, forKey: .name) ?? uidb?.string("name") ?? root?.string("name")
        model = try container.decodeIfPresent(String.self, forKey: .model) ?? uidb?.string("model") ?? root?.string("model") ?? uidb?.string("model_in_eol") ?? root?.string("model_in_eol")
        type = try container.decodeIfPresent(String.self, forKey: .type) ?? uidb?.string("type") ?? root?.string("type")
        macAddress = try container.decodeIfPresent(String.self, forKey: .macAddress) ?? uidb?.string("mac") ?? root?.string("mac") ?? root?.string("mac_address")
        ipAddress = try container.decodeIfPresent(String.self, forKey: .ipAddress) ?? uidb?.string("ip") ?? root?.string("ip") ?? uidb?.string("ipAddress") ?? root?.string("ipAddress") ?? root?.string("ip_address")
        state = try container.decodeIfPresent(String.self, forKey: .state) ?? uidb?.string("state") ?? root?.string("state")
        version = try container.decodeIfPresent(String.self, forKey: .version) ?? uidb?.string("version") ?? root?.string("version")
        firmwareVersion = try container.decodeIfPresent(String.self, forKey: .firmwareVersion) ?? uidb?.string("firmwareVersion") ?? root?.string("firmwareVersion") ?? uidb?.string("firmware_version") ?? root?.string("firmware_version")
        siteId = try container.decodeIfPresent(String.self, forKey: .siteId) ?? uidb?.string("site_id") ?? root?.string("site_id")
        updateAvailable = try container.decodeIfPresent(Bool.self, forKey: .updateAvailable) ?? uidb?.bool("upgradeable") ?? root?.bool("upgradeable")
        uplinkDeviceName = try container.decodeIfPresent(String.self, forKey: .uplinkDeviceName)
            ?? uidb?.string("uplink_device_name")
            ?? root?.string("uplink_device_name")
            ?? uplink?.deviceName
        serialNumber = uidb?.string("serial") ?? root?.string("serial")
        clientCount = uidb?.int("num_sta") ?? root?.int("num_sta") ?? root?.int("clientCount")
        guestClientCount = uidb?.int("guest-num_sta") ?? root?.int("guest-num_sta")
        totalRxBytes = uidb?.int64("rx_bytes") ?? root?.int64("rx_bytes")
        totalTxBytes = uidb?.int64("tx_bytes") ?? root?.int64("tx_bytes")
        rxRateBytesPerSecond = uidb?.double("rx_bytes-r") ?? root?.double("rx_bytes-r") ?? uidb?.double("rx_rate_bps") ?? root?.double("rx_rate_bps")
        txRateBytesPerSecond = uidb?.double("tx_bytes-r") ?? root?.double("tx_bytes-r") ?? uidb?.double("tx_rate_bps") ?? root?.double("tx_rate_bps")
        cpuUsagePercent = uidb?.double("cpu") ?? root?.double("cpu") ?? root?.double("cpuUsagePercent")
        memoryUsagePercent = uidb?.double("mem") ?? root?.double("mem") ?? root?.double("memoryUsagePercent")
        temperatureCelsius = uidb?.double("temperature") ?? root?.double("temperature") ?? uidb?.double("temp") ?? root?.double("temp")
        self.ports = ports
        self.uplink = uplink
        self.radios = radios
    }

    private init(
        id: String,
        name: String?,
        model: String?,
        type: String?,
        macAddress: String?,
        ipAddress: String?,
        state: String?,
        version: String?,
        firmwareVersion: String?,
        siteId: String?,
        updateAvailable: Bool?,
        uplinkDeviceName: String?,
        serialNumber: String?,
        clientCount: Int?,
        guestClientCount: Int?,
        totalRxBytes: Int64?,
        totalTxBytes: Int64?,
        rxRateBytesPerSecond: Double?,
        txRateBytesPerSecond: Double?,
        cpuUsagePercent: Double?,
        memoryUsagePercent: Double?,
        temperatureCelsius: Double?,
        ports: [UniFiDevicePort],
        uplink: UniFiDeviceUplink?,
        radios: [UniFiDeviceRadio]
    ) {
        self.id = id
        self.name = name
        self.model = model
        self.type = type
        self.macAddress = macAddress
        self.ipAddress = ipAddress
        self.state = state
        self.version = version
        self.firmwareVersion = firmwareVersion
        self.siteId = siteId
        self.updateAvailable = updateAvailable
        self.uplinkDeviceName = uplinkDeviceName
        self.serialNumber = serialNumber
        self.clientCount = clientCount
        self.guestClientCount = guestClientCount
        self.totalRxBytes = totalRxBytes
        self.totalTxBytes = totalTxBytes
        self.rxRateBytesPerSecond = rxRateBytesPerSecond
        self.txRateBytesPerSecond = txRateBytesPerSecond
        self.cpuUsagePercent = cpuUsagePercent
        self.memoryUsagePercent = memoryUsagePercent
        self.temperatureCelsius = temperatureCelsius
        self.ports = ports
        self.uplink = uplink
        self.radios = radios
    }

    func withSiteId(_ siteId: String) -> UniFiDevice {
        UniFiDevice(
            id: id,
            name: name,
            model: model,
            type: type,
            macAddress: macAddress,
            ipAddress: ipAddress,
            state: state,
            version: version,
            firmwareVersion: firmwareVersion,
            siteId: siteId,
            updateAvailable: updateAvailable,
            uplinkDeviceName: uplinkDeviceName,
            serialNumber: serialNumber,
            clientCount: clientCount,
            guestClientCount: guestClientCount,
            totalRxBytes: totalRxBytes,
            totalTxBytes: totalTxBytes,
            rxRateBytesPerSecond: rxRateBytesPerSecond,
            txRateBytesPerSecond: txRateBytesPerSecond,
            cpuUsagePercent: cpuUsagePercent,
            memoryUsagePercent: memoryUsagePercent,
            temperatureCelsius: temperatureCelsius,
            ports: ports,
            uplink: uplink,
            radios: radios
        )
    }
}

struct UniFiDeviceUplink: Decodable, Sendable, Hashable {
    let deviceName: String?
    let remotePort: Int?
    let speedMbps: Int?
    let fullDuplex: Bool?
    let rxRateBytesPerSecond: Double?
    let txRateBytesPerSecond: Double?

    fileprivate init(from container: KeyedDecodingContainer<UniFiDynamicCodingKey>) throws {
        deviceName = container.string("uplink_device_name")
            ?? container.string("device_name")
            ?? container.string("name")
            ?? container.string("uplink_remote_device_name")
        remotePort = container.int("uplink_remote_port") ?? container.int("remote_port")
        speedMbps = container.int("speed")
        fullDuplex = container.bool("full_duplex")
        rxRateBytesPerSecond = container.double("rx_bytes-r") ?? container.double("rx_rate_bps")
        txRateBytesPerSecond = container.double("tx_bytes-r") ?? container.double("tx_rate_bps")
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: UniFiDynamicCodingKey.self)
        try self.init(from: container)
    }
}

struct UniFiDevicePort: Identifiable, Decodable, Sendable, Hashable {
    let idx: Int?
    let name: String?
    let up: Bool?
    let speedMbps: Int?
    let media: String?
    let poeMode: String?
    let poeEnabled: Bool?
    let poePowerWatts: Double?
    let rxRateBytesPerSecond: Double?
    let txRateBytesPerSecond: Double?
    let isUplink: Bool?

    var id: String { idx.map { "port-\($0)" } ?? name ?? "port-unknown-\(media ?? "na")-\(speedMbps ?? 0)" }
    var displayName: String { name?.nilIfEmpty ?? idx.map { "Port \($0)" } ?? "Port" }
    var liveTrafficBytesPerSecond: Double? {
        let total = (rxRateBytesPerSecond ?? 0) + (txRateBytesPerSecond ?? 0)
        return total > 0 ? total : nil
    }
    var hasTraffic: Bool { liveTrafficBytesPerSecond != nil }

    enum CodingKeys: String, CodingKey {
        case idx = "port_idx"
        case name
        case up
        case speedMbps = "speed"
        case media
        case poeMode = "poe_mode"
        case poeEnabled = "poe_enable"
        case poePowerWatts = "poe_power"
        case rxRateBytesPerSecond = "rx_bytes-r"
        case txRateBytesPerSecond = "tx_bytes-r"
        case isUplink = "is_uplink"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        idx = try container.decodeIfPresent(Int.self, forKey: .idx)
        name = try container.decodeIfPresent(String.self, forKey: .name)
        up = try container.decodeIfPresent(Bool.self, forKey: .up)
        speedMbps = try container.decodeIfPresent(Int.self, forKey: .speedMbps)
        media = try container.decodeIfPresent(String.self, forKey: .media)
        poeMode = try container.decodeIfPresent(String.self, forKey: .poeMode)
        poeEnabled = try container.decodeIfPresent(Bool.self, forKey: .poeEnabled)
        poePowerWatts = (try? container.decodeIfPresent(Double.self, forKey: .poePowerWatts))
            ?? (try? container.decodeIfPresent(Int.self, forKey: .poePowerWatts)).map(Double.init)
        rxRateBytesPerSecond = (try? container.decodeIfPresent(Double.self, forKey: .rxRateBytesPerSecond))
            ?? (try? container.decodeIfPresent(Int.self, forKey: .rxRateBytesPerSecond)).map(Double.init)
        txRateBytesPerSecond = (try? container.decodeIfPresent(Double.self, forKey: .txRateBytesPerSecond))
            ?? (try? container.decodeIfPresent(Int.self, forKey: .txRateBytesPerSecond)).map(Double.init)
        isUplink = try container.decodeIfPresent(Bool.self, forKey: .isUplink)
    }
}

struct UniFiDeviceRadio: Decodable, Sendable, Hashable {
    let radio: String?
    let channel: Int?
    let channelWidth: Int?
    let txPowerMode: String?
    let clientCount: Int?
    let satisfaction: Double?

    enum CodingKeys: String, CodingKey {
        case radio
        case channel
        case channelWidth = "channel_width"
        case txPowerMode = "tx_power_mode"
        case clientCount = "num_sta"
        case satisfaction
    }
}

struct UniFiClient: Identifiable, Decodable, Sendable, Hashable {
    let id: String
    let name: String?
    let macAddress: String?
    let ipAddress: String?
    let type: String?
    let networkName: String?
    let access: UniFiClientAccess?
    let rxBytes: Int64?
    let txBytes: Int64?
    let siteId: String?
    let accessPointName: String?
    let signalStrength: Int?
    let wifiExperience: Double?
    let rxRateBytesPerSecond: Double?
    let txRateBytesPerSecond: Double?

    var displayName: String {
        name?.nilIfEmpty ?? ipAddress?.nilIfEmpty ?? macAddress?.nilIfEmpty ?? "Client"
    }

    var isGuestUnauthorized: Bool {
        access?.type?.uppercased() == "GUEST" && access?.authorized == false
    }

    var liveTrafficBytesPerSecond: Double? {
        let total = (rxRateBytesPerSecond ?? 0) + (txRateBytesPerSecond ?? 0)
        return total > 0 ? total : nil
    }

    enum CodingKeys: String, CodingKey {
        case id
        case clientId
        case name
        case macAddress
        case ipAddress
        case type
        case networkName
        case access
        case rxBytes
        case txBytes
        case siteId
        case accessPointName = "ap_name"
        case signalStrength = "signal"
        case wifiExperience = "experience"
        case rxRateBytesPerSecond = "rx_bytes-r"
        case txRateBytesPerSecond = "tx_bytes-r"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let dynamic = try? decoder.container(keyedBy: UniFiDynamicCodingKey.self)
        macAddress = try container.decodeIfPresent(String.self, forKey: .macAddress)
            ?? dynamic?.string("mac")
            ?? dynamic?.string("mac_address")
        ipAddress = try container.decodeIfPresent(String.self, forKey: .ipAddress)
            ?? dynamic?.string("ip")
            ?? dynamic?.string("ip_address")
        id = try container.decodeIfPresent(String.self, forKey: .id)
            ?? container.decodeIfPresent(String.self, forKey: .clientId)
            ?? dynamic?.string("_id")
            ?? dynamic?.string("client_id")
            ?? macAddress
            ?? UUID().uuidString
        name = try container.decodeIfPresent(String.self, forKey: .name)
            ?? dynamic?.string("hostname")
            ?? dynamic?.string("host_name")
        type = try container.decodeIfPresent(String.self, forKey: .type)
            ?? dynamic?.string("type")
        networkName = try container.decodeIfPresent(String.self, forKey: .networkName)
            ?? dynamic?.string("network_name")
            ?? dynamic?.string("essid")
            ?? dynamic?.string("ssid")
        access = try container.decodeIfPresent(UniFiClientAccess.self, forKey: .access)
        rxBytes = (try? container.decodeIfPresent(Int64.self, forKey: .rxBytes))
            ?? dynamic?.int64("rx_bytes")
        txBytes = (try? container.decodeIfPresent(Int64.self, forKey: .txBytes))
            ?? dynamic?.int64("tx_bytes")
        siteId = try container.decodeIfPresent(String.self, forKey: .siteId)
            ?? dynamic?.string("site_id")
        accessPointName = try container.decodeIfPresent(String.self, forKey: .accessPointName)
            ?? dynamic?.string("apName")
            ?? dynamic?.string("ap_name")
            ?? dynamic?.string("uplink_device_name")
        signalStrength = (try? container.decodeIfPresent(Int.self, forKey: .signalStrength))
            ?? dynamic?.int("rssi")
            ?? dynamic?.int("signal")
        wifiExperience = (try? container.decodeIfPresent(Double.self, forKey: .wifiExperience))
            ?? dynamic?.double("wifiExperience")
            ?? dynamic?.double("satisfaction")
            ?? dynamic?.double("experience")
        rxRateBytesPerSecond = (try? container.decodeIfPresent(Double.self, forKey: .rxRateBytesPerSecond))
            ?? dynamic?.double("rx_bytes-r")
            ?? dynamic?.double("rx_rate_bps")
        txRateBytesPerSecond = (try? container.decodeIfPresent(Double.self, forKey: .txRateBytesPerSecond))
            ?? dynamic?.double("tx_bytes-r")
            ?? dynamic?.double("tx_rate_bps")
    }

    private init(
        id: String,
        name: String?,
        macAddress: String?,
        ipAddress: String?,
        type: String?,
        networkName: String?,
        access: UniFiClientAccess?,
        rxBytes: Int64?,
        txBytes: Int64?,
        siteId: String?,
        accessPointName: String?,
        signalStrength: Int?,
        wifiExperience: Double?,
        rxRateBytesPerSecond: Double?,
        txRateBytesPerSecond: Double?
    ) {
        self.id = id
        self.name = name
        self.macAddress = macAddress
        self.ipAddress = ipAddress
        self.type = type
        self.networkName = networkName
        self.access = access
        self.rxBytes = rxBytes
        self.txBytes = txBytes
        self.siteId = siteId
        self.accessPointName = accessPointName
        self.signalStrength = signalStrength
        self.wifiExperience = wifiExperience
        self.rxRateBytesPerSecond = rxRateBytesPerSecond
        self.txRateBytesPerSecond = txRateBytesPerSecond
    }

    func withSiteId(_ siteId: String) -> UniFiClient {
        UniFiClient(
            id: id,
            name: name,
            macAddress: macAddress,
            ipAddress: ipAddress,
            type: type,
            networkName: networkName,
            access: access,
            rxBytes: rxBytes,
            txBytes: txBytes,
            siteId: siteId,
            accessPointName: accessPointName,
            signalStrength: signalStrength,
            wifiExperience: wifiExperience,
            rxRateBytesPerSecond: rxRateBytesPerSecond,
            txRateBytesPerSecond: txRateBytesPerSecond
        )
    }

    func withAuthorized() -> UniFiClient {
        UniFiClient(
            id: id,
            name: name,
            macAddress: macAddress,
            ipAddress: ipAddress,
            type: type,
            networkName: networkName,
            access: UniFiClientAccess(type: access?.type, authorized: true),
            rxBytes: rxBytes,
            txBytes: txBytes,
            siteId: siteId,
            accessPointName: accessPointName,
            signalStrength: signalStrength,
            wifiExperience: wifiExperience,
            rxRateBytesPerSecond: rxRateBytesPerSecond,
            txRateBytesPerSecond: txRateBytesPerSecond
        )
    }
}

struct UniFiClientAccess: Decodable, Sendable, Hashable {
    let type: String?
    let authorized: Bool?
}

struct UniFiISPMetricSeries: Identifiable, Decodable, Sendable, Hashable {
    let metricType: String?
    let periods: [UniFiISPMetricPoint]
    let hostId: String?
    let siteId: String?

    var id: String { "\(hostId ?? "host"):\(siteId ?? "site"):\(metricType ?? "metric")" }
}

struct UniFiISPMetricPoint: Identifiable, Decodable, Sendable, Hashable {
    let metricTime: String
    let data: UniFiISPMetricData?

    var id: String { metricTime }
    var date: Date { UniFiDateParser.parse(metricTime) ?? Date.distantPast }
    var wan: UniFiWANMetric? { data?.wan }
}

struct UniFiISPMetricData: Decodable, Sendable, Hashable {
    let wan: UniFiWANMetric?
}

struct UniFiWANMetric: Decodable, Sendable, Hashable {
    let avgLatency: Double?
    let maxLatency: Double?
    let packetLoss: Double?
    let uptime: Double?
    let downtime: Double?
    let downloadKbps: Double?
    let uploadKbps: Double?
    let ispName: String?

    enum CodingKeys: String, CodingKey {
        case avgLatency
        case maxLatency
        case packetLoss
        case uptime
        case downtime
        case downloadKbps = "download_kbps"
        case uploadKbps = "upload_kbps"
        case ispName
    }
}

struct UniFiNetwork: Identifiable, Decodable, Sendable, Hashable {
    let id: String
    let name: String?
    let purpose: String?
    let vlanId: Int?
    let ipSubnet: String?
    let dhcpEnabled: Bool?
    let siteId: String?

    var displayName: String { name?.nilIfEmpty ?? id }
    var isGuestNetwork: Bool { purpose?.lowercased().contains("guest") == true }

    enum CodingKeys: String, CodingKey {
        case id = "_id"
        case networkId
        case name
        case purpose
        case vlanId = "vlan_id"
        case ipSubnet = "ip_subnet"
        case dhcpEnabled = "dhcpdEnabled"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decodeIfPresent(String.self, forKey: .id)
            ?? container.decodeIfPresent(String.self, forKey: .networkId)
            ?? UUID().uuidString
        name = try container.decodeIfPresent(String.self, forKey: .name)
        purpose = try container.decodeIfPresent(String.self, forKey: .purpose)
        vlanId = try container.decodeIfPresent(Int.self, forKey: .vlanId)
        ipSubnet = try container.decodeIfPresent(String.self, forKey: .ipSubnet)
        dhcpEnabled = try container.decodeIfPresent(Bool.self, forKey: .dhcpEnabled)
        siteId = nil
    }

    private init(
        id: String,
        name: String?,
        purpose: String?,
        vlanId: Int?,
        ipSubnet: String?,
        dhcpEnabled: Bool?,
        siteId: String?
    ) {
        self.id = id
        self.name = name
        self.purpose = purpose
        self.vlanId = vlanId
        self.ipSubnet = ipSubnet
        self.dhcpEnabled = dhcpEnabled
        self.siteId = siteId
    }

    func withSiteId(_ siteId: String) -> UniFiNetwork {
        UniFiNetwork(
            id: id,
            name: name,
            purpose: purpose,
            vlanId: vlanId,
            ipSubnet: ipSubnet,
            dhcpEnabled: dhcpEnabled,
            siteId: siteId
        )
    }
}

actor UniFiAPIClient {
    private let instanceId: UUID
    private var engine: BaseNetworkEngine
    private var mode: UniFiAuthMode = .siteManager
    private var baseURL = "https://api.ui.com"
    private var fallbackURL = ""
    private var apiKey = ""
    private var storedAllowSelfSigned = true

    init(instanceId: UUID) {
        self.instanceId = instanceId
        self.engine = BaseNetworkEngine(serviceType: .unifiNetwork, instanceId: instanceId)
    }

    func configure(
        url: String,
        apiKey: String,
        mode: UniFiAuthMode,
        fallbackUrl: String? = nil,
        allowSelfSigned: Bool? = nil
    ) {
        self.mode = mode
        self.baseURL = mode == .siteManager ? "https://api.ui.com" : Self.cleanURL(url)
        self.fallbackURL = Self.cleanURL(fallbackUrl ?? "")
        self.apiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        if let allowSelfSigned {
            storedAllowSelfSigned = allowSelfSigned
        }
        engine = BaseNetworkEngine(serviceType: .unifiNetwork, instanceId: instanceId, allowSelfSigned: storedAllowSelfSigned)
    }

    func ping() async -> Bool {
        guard !apiKey.isEmpty else { return false }
        if mode == .siteManager {
            return await engine.pingURL("\(baseURL)/v1/sites?pageSize=1", extraHeaders: authHeaders)
        }
        for path in Self.localNetworkPathCandidates("sites?pageSize=1") {
            if await engine.pingURL("\(baseURL)\(path)", extraHeaders: authHeaders) {
                return true
            }
            if !fallbackURL.isEmpty, await engine.pingURL("\(fallbackURL)\(path)", extraHeaders: authHeaders) {
                return true
            }
        }
        return false
    }

    func authenticate(url: String, apiKey: String, mode: UniFiAuthMode, fallbackUrl: String? = nil) async throws {
        configure(url: url, apiKey: apiKey, mode: mode, fallbackUrl: fallbackUrl, allowSelfSigned: storedAllowSelfSigned)
        _ = try await getSites(pageSize: 1)
    }

    func getDashboard() async throws -> UniFiDashboardData {
        switch mode {
        case .siteManager:
            let sites = try await getSites(pageSize: 100)
            async let hostsResult = getOptionalHosts()
            async let devicesResult = getOptionalDevices()
            async let metricsResult = getOptionalISPMetrics()
            return await UniFiDashboardData(
                mode: mode,
                hosts: hostsResult,
                sites: sites,
                devices: devicesResult,
                clients: [],
                ispMetrics: metricsResult,
                networks: []
            )
        case .localNetwork:
            let sites = try await getSites(pageSize: 100)
            var devices: [UniFiDevice] = []
            var clients: [UniFiClient] = []
            var networks: [UniFiNetwork] = []
            for site in sites {
                async let siteDevices = getLocalDevices(siteId: site.siteId)
                async let siteClients = getLocalClients(siteId: site.siteId)
                async let siteNetworks = getLocalNetworks(siteId: site.siteId)
                let fetchedDevices = ((try? await siteDevices) ?? []).map { $0.withSiteId(site.siteId) }
                devices.append(contentsOf: fetchedDevices)
                let fetched = ((try? await siteClients) ?? []).map { $0.withSiteId(site.siteId) }
                clients.append(contentsOf: fetched)
                let fetchedNetworks = ((try? await siteNetworks) ?? []).map { $0.withSiteId(site.siteId) }
                networks.append(contentsOf: fetchedNetworks)
            }
            return UniFiDashboardData(
                mode: mode,
                hosts: [],
                sites: sites,
                devices: devices,
                clients: clients,
                ispMetrics: [],
                networks: networks
            )
        }
    }

    func authorizeGuest(siteId: String, clientId: String, minutes: Int = 120) async throws {
        let body = try JSONEncoder().encode(UniFiClientAction(action: "AUTHORIZE_GUEST_ACCESS", timeLimitMinutes: minutes))
        let encodedSiteId = Self.encodePathComponent(siteId)
        let encodedClientId = Self.encodePathComponent(clientId)
        switch mode {
        case .siteManager:
            try await engine.requestVoid(
                baseURL: baseURL,
                fallbackURL: fallbackURL,
                path: "/v1/sites/\(encodedSiteId)/clients/\(encodedClientId)/actions",
                method: "POST",
                headers: authHeaders,
                body: body
            )
        case .localNetwork:
            try await localRequestVoid(path: "sites/\(encodedSiteId)/clients/\(encodedClientId)/actions", method: "POST", body: body)
        }
    }

    private var authHeaders: [String: String] {
        [
            "Accept": "application/json",
            "Content-Type": "application/json",
            "X-API-Key": apiKey
        ]
    }

    private func getHosts() async throws -> [UniFiHost] {
        let response: UniFiEnvelope<[UniFiHost]> = try await request(path: "/v1/hosts")
        return response.data ?? []
    }

    private func getOptionalHosts() async -> [UniFiHost] {
        (try? await getHosts()) ?? []
    }

    private func getSites(pageSize: Int) async throws -> [UniFiSite] {
        if mode == .localNetwork {
            let response: UniFiLocalEnvelope<[UniFiSite]> = try await localRequest(path: "sites?pageSize=\(pageSize)")
            return response.resolvedData
        }
        var sites: [UniFiSite] = []
        var nextToken: String?
        repeat {
            var components = URLComponents()
            components.path = mode == .siteManager ? "/v1/sites" : "/proxy/network/integration/v1/sites"
            components.queryItems = [URLQueryItem(name: "pageSize", value: "\(pageSize)")]
            if let nextToken {
                components.queryItems?.append(URLQueryItem(name: "nextToken", value: nextToken))
            }
            let response: UniFiEnvelope<[UniFiSite]> = try await request(path: components.string ?? components.path)
            sites.append(contentsOf: response.data ?? [])
            nextToken = response.nextToken
        } while nextToken?.isEmpty == false
        return sites
    }

    private func getDevices() async throws -> [UniFiDevice] {
        let response: UniFiEnvelope<[UniFiDevice]> = try await request(path: "/v1/devices")
        return response.data ?? []
    }

    private func getOptionalDevices() async -> [UniFiDevice] {
        (try? await getDevices()) ?? []
    }

    private func getISPMetrics() async throws -> [UniFiISPMetricSeries] {
        let response: UniFiEnvelope<[UniFiISPMetricSeries]> = try await request(path: "/ea/isp-metrics/5m?duration=24h")
        return response.data ?? []
    }

    private func getOptionalISPMetrics() async -> [UniFiISPMetricSeries] {
        (try? await getISPMetrics()) ?? []
    }

    private func getLocalDevices(siteId: String) async throws -> [UniFiDevice] {
        let response: UniFiLocalEnvelope<[UniFiDevice]> = try await localRequest(path: "sites/\(Self.encodePathComponent(siteId))/devices")
        return response.resolvedData
    }

    private func getLocalClients(siteId: String) async throws -> [UniFiClient] {
        let response: UniFiLocalEnvelope<[UniFiClient]> = try await localRequest(path: "sites/\(Self.encodePathComponent(siteId))/clients")
        return response.resolvedData
    }

    private func getLocalNetworks(siteId: String) async throws -> [UniFiNetwork] {
        let response: UniFiLocalEnvelope<[UniFiNetwork]> = try await localRequest(path: "sites/\(Self.encodePathComponent(siteId))/networks")
        return response.resolvedData
    }

    private func localRequest<T: Decodable>(path: String) async throws -> T {
        var lastError: Error?
        for candidate in Self.localNetworkPathCandidates(path) {
            do {
                return try await request(path: candidate)
            } catch {
                lastError = error
            }
        }
        throw lastError ?? APIError.notConfigured
    }

    private func localRequestVoid(path: String, method: String, body: Data?) async throws {
        var lastError: Error?
        for candidate in Self.localNetworkPathCandidates(path) {
            do {
                try await engine.requestVoid(
                    baseURL: baseURL,
                    fallbackURL: fallbackURL,
                    path: candidate,
                    method: method,
                    headers: authHeaders,
                    body: body
                )
                return
            } catch {
                lastError = error
            }
        }
        throw lastError ?? APIError.notConfigured
    }

    private func request<T: Decodable>(path: String) async throws -> T {
        try await engine.request(
            baseURL: baseURL,
            fallbackURL: fallbackURL,
            path: path,
            headers: authHeaders
        )
    }

    private static func cleanURL(_ value: String) -> String {
        value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    private static func localNetworkPathCandidates(_ path: String) -> [String] {
        let cleanPath = path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        return [
            "/proxy/network/integration/v1/\(cleanPath)",
            "/v1/\(cleanPath)"
        ]
    }

    private static func encodePathComponent(_ value: String) -> String {
        var allowed = CharacterSet.urlPathAllowed
        allowed.remove(charactersIn: "/?#[]@!$&'()*+,;=")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }
}

private struct UniFiEnvelope<T: Decodable>: Decodable {
    let data: T?
    let httpStatusCode: Int?
    let traceId: String?
    let nextToken: String?
}

private struct UniFiLocalEnvelope<T: Decodable>: Decodable {
    let data: T?
    let items: T?

    var resolvedData: T {
        if let data { return data }
        if let items { return items }
        if let empty = [] as? T { return empty }
        return data ?? items!
    }

    init(from decoder: Decoder) throws {
        if let direct = try? T(from: decoder) {
            data = direct
            items = nil
            return
        }
        let container = try decoder.container(keyedBy: CodingKeys.self)
        data = try container.decodeIfPresent(T.self, forKey: .data)
        items = try container.decodeIfPresent(T.self, forKey: .items)
    }

    enum CodingKeys: String, CodingKey {
        case data
        case items
    }
}

private struct UniFiClientAction: Encodable {
    let action: String
    let timeLimitMinutes: Int
}

private struct UniFiDynamicCodingKey: CodingKey {
    let stringValue: String
    let intValue: Int? = nil

    init(_ stringValue: String) {
        self.stringValue = stringValue
    }

    init?(stringValue: String) {
        self.stringValue = stringValue
    }

    init?(intValue: Int) {
        return nil
    }
}

private extension KeyedDecodingContainer where K == UniFiDynamicCodingKey {
    func string(_ key: String) -> String? {
        try? decodeIfPresent(String.self, forKey: UniFiDynamicCodingKey(key))
    }

    func bool(_ key: String) -> Bool? {
        try? decodeIfPresent(Bool.self, forKey: UniFiDynamicCodingKey(key))
    }

    func int(_ key: String) -> Int? {
        if let value = try? decodeIfPresent(Int.self, forKey: UniFiDynamicCodingKey(key)) {
            return value
        }
        if let value = try? decodeIfPresent(Double.self, forKey: UniFiDynamicCodingKey(key)) {
            return Int(value)
        }
        if let value = try? decodeIfPresent(String.self, forKey: UniFiDynamicCodingKey(key)) {
            return Int(value)
        }
        return nil
    }

    func int64(_ key: String) -> Int64? {
        if let value = try? decodeIfPresent(Int64.self, forKey: UniFiDynamicCodingKey(key)) {
            return value
        }
        if let value = try? decodeIfPresent(Int.self, forKey: UniFiDynamicCodingKey(key)) {
            return Int64(value)
        }
        if let value = try? decodeIfPresent(Double.self, forKey: UniFiDynamicCodingKey(key)) {
            return Int64(value)
        }
        if let value = try? decodeIfPresent(String.self, forKey: UniFiDynamicCodingKey(key)) {
            return Int64(value)
        }
        return nil
    }

    func double(_ key: String) -> Double? {
        if let value = try? decodeIfPresent(Double.self, forKey: UniFiDynamicCodingKey(key)) {
            return value
        }
        if let value = try? decodeIfPresent(Int.self, forKey: UniFiDynamicCodingKey(key)) {
            return Double(value)
        }
        if let value = try? decodeIfPresent(String.self, forKey: UniFiDynamicCodingKey(key)) {
            return Double(value)
        }
        return nil
    }

    func array<T: Decodable>(_ type: T.Type, _ key: String) -> [T]? {
        try? decodeIfPresent([T].self, forKey: UniFiDynamicCodingKey(key))
    }

    func nested(_ key: String) -> KeyedDecodingContainer<UniFiDynamicCodingKey>? {
        try? nestedContainer(keyedBy: UniFiDynamicCodingKey.self, forKey: UniFiDynamicCodingKey(key))
    }
}

private enum UniFiDateParser {
    nonisolated(unsafe) private static let fractionalFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    nonisolated(unsafe) private static let regularFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime]
        return f
    }()

    static func parse(_ value: String?) -> Date? {
        guard let value else { return nil }
        return fractionalFormatter.date(from: value) ?? regularFormatter.date(from: value)
    }
}

private extension String {
    var nilIfEmpty: String? {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }
}
