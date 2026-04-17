import Foundation
import Observation
import Darwin

private final class ServiceClientManager {
    private var portainerClients: [UUID: PortainerAPIClient] = [:]
    private var piholeClients: [UUID: PiHoleAPIClient] = [:]
    private var adguardClients: [UUID: AdGuardHomeAPIClient] = [:]
    private var technitiumClients: [UUID: TechnitiumAPIClient] = [:]
    private var beszelClients: [UUID: BeszelAPIClient] = [:]
    private var healthchecksClients: [UUID: HealthchecksAPIClient] = [:]
    private var linuxUpdateClients: [UUID: LinuxUpdateAPIClient] = [:]
    private var dockhandClients: [UUID: DockhandAPIClient] = [:]
    private var craftyClients: [UUID: CraftyAPIClient] = [:]
    private var giteaClients: [UUID: GiteaAPIClient] = [:]
    private var npmClients: [UUID: NginxProxyManagerAPIClient] = [:]
    private var pangolinClients: [UUID: PangolinAPIClient] = [:]
    private var patchmonClients: [UUID: PatchmonAPIClient] = [:]
    private var jellystatClients: [UUID: JellystatAPIClient] = [:]
    private var plexClients: [UUID: PlexAPIClient] = [:]
    private var qbittorrentClients: [UUID: QbittorrentAPIClient] = [:]
    private var radarrClients: [UUID: RadarrAPIClient] = [:]
    private var sonarrClients: [UUID: SonarrAPIClient] = [:]
    private var lidarrClients: [UUID: LidarrAPIClient] = [:]
    private var genericClients: [UUID: GenericAPIClient] = [:]
    private var wakapiClients: [UUID: WakapiAPIClient] = [:]
    private var proxmoxClients: [UUID: ProxmoxAPIClient] = [:]

    func portainerClient(id: UUID) -> PortainerAPIClient {
        if let client = portainerClients[id] {
            return client
        }
        let client = PortainerAPIClient(instanceId: id)
        portainerClients[id] = client
        return client
    }

    func piholeClient(id: UUID) -> PiHoleAPIClient {
        if let client = piholeClients[id] {
            return client
        }
        let client = PiHoleAPIClient(instanceId: id)
        piholeClients[id] = client
        return client
    }

    func adguardClient(id: UUID) -> AdGuardHomeAPIClient {
        if let client = adguardClients[id] {
            return client
        }
        let client = AdGuardHomeAPIClient(instanceId: id)
        adguardClients[id] = client
        return client
    }

    func technitiumClient(id: UUID) -> TechnitiumAPIClient {
        if let client = technitiumClients[id] {
            return client
        }
        let client = TechnitiumAPIClient(instanceId: id)
        technitiumClients[id] = client
        return client
    }

    func beszelClient(id: UUID) -> BeszelAPIClient {
        if let client = beszelClients[id] {
            return client
        }
        let client = BeszelAPIClient(instanceId: id)
        beszelClients[id] = client
        return client
    }

    func healthchecksClient(id: UUID) -> HealthchecksAPIClient {
        if let client = healthchecksClients[id] {
            return client
        }
        let client = HealthchecksAPIClient(instanceId: id)
        healthchecksClients[id] = client
        return client
    }

    func linuxUpdateClient(id: UUID) -> LinuxUpdateAPIClient {
        if let client = linuxUpdateClients[id] {
            return client
        }
        let client = LinuxUpdateAPIClient(instanceId: id)
        linuxUpdateClients[id] = client
        return client
    }

    func dockhandClient(id: UUID) -> DockhandAPIClient {
        if let client = dockhandClients[id] {
            return client
        }
        let client = DockhandAPIClient(instanceId: id)
        dockhandClients[id] = client
        return client
    }

    func craftyClient(id: UUID) -> CraftyAPIClient {
        if let client = craftyClients[id] {
            return client
        }
        let client = CraftyAPIClient(instanceId: id)
        craftyClients[id] = client
        return client
    }

    func giteaClient(id: UUID) -> GiteaAPIClient {
        if let client = giteaClients[id] {
            return client
        }
        let client = GiteaAPIClient(instanceId: id)
        giteaClients[id] = client
        return client
    }

    func npmClient(id: UUID) -> NginxProxyManagerAPIClient {
        if let client = npmClients[id] {
            return client
        }
        let client = NginxProxyManagerAPIClient(instanceId: id)
        npmClients[id] = client
        return client
    }

    func pangolinClient(id: UUID) -> PangolinAPIClient {
        if let client = pangolinClients[id] {
            return client
        }
        let client = PangolinAPIClient(instanceId: id)
        pangolinClients[id] = client
        return client
    }

    func patchmonClient(id: UUID) -> PatchmonAPIClient {
        if let client = patchmonClients[id] {
            return client
        }
        let client = PatchmonAPIClient(instanceId: id)
        patchmonClients[id] = client
        return client
    }

    func jellystatClient(id: UUID) -> JellystatAPIClient {
        if let client = jellystatClients[id] {
            return client
        }
        let client = JellystatAPIClient(instanceId: id)
        jellystatClients[id] = client
        return client
    }

    func plexClient(id: UUID) -> PlexAPIClient {
        if let client = plexClients[id] {
            return client
        }
        let client = PlexAPIClient(instanceId: id)
        plexClients[id] = client
        return client
    }

    func qbittorrentClient(id: UUID) -> QbittorrentAPIClient {
        if let client = qbittorrentClients[id] {
            return client
        }
        let client = QbittorrentAPIClient(instanceId: id)
        qbittorrentClients[id] = client
        return client
    }

    func radarrClient(id: UUID) -> RadarrAPIClient {
        if let client = radarrClients[id] {
            return client
        }
        let client = RadarrAPIClient(instanceId: id)
        radarrClients[id] = client
        return client
    }

    func sonarrClient(id: UUID) -> SonarrAPIClient {
        if let client = sonarrClients[id] {
            return client
        }
        let client = SonarrAPIClient(instanceId: id)
        sonarrClients[id] = client
        return client
    }
    
    func lidarrClient(id: UUID) -> LidarrAPIClient {
        if let client = lidarrClients[id] {
            return client
        }
        let client = LidarrAPIClient(instanceId: id)
        lidarrClients[id] = client
        return client
    }
    func wakapiClient(id: UUID) -> WakapiAPIClient {
        if let client = wakapiClients[id] {
            return client
        }
        let client = WakapiAPIClient(instanceId: id)
        wakapiClients[id] = client
        return client
    }

    func proxmoxClient(id: UUID) -> ProxmoxAPIClient {
        if let client = proxmoxClients[id] {
            return client
        }
        let client = ProxmoxAPIClient(instanceId: id)
        proxmoxClients[id] = client
        return client
    }

    func genericClient(id: UUID, type: ServiceType) -> GenericAPIClient {
        if let client = genericClients[id] {
            return client
        }
        let client = GenericAPIClient(serviceType: type, instanceId: id)
        genericClients[id] = client
        return client
    }

    func removeClient(id: UUID, type: ServiceType) {
        switch type {
        case .portainer:
            portainerClients.removeValue(forKey: id)
        case .pihole:
            piholeClients.removeValue(forKey: id)
        case .adguardHome:
            adguardClients.removeValue(forKey: id)
        case .technitium:
            technitiumClients.removeValue(forKey: id)
        case .beszel:
            beszelClients.removeValue(forKey: id)
        case .healthchecks:
            healthchecksClients.removeValue(forKey: id)
        case .linuxUpdate:
            linuxUpdateClients.removeValue(forKey: id)
        case .dockhand:
            dockhandClients.removeValue(forKey: id)
        case .craftyController:
            craftyClients.removeValue(forKey: id)
        case .gitea:
            giteaClients.removeValue(forKey: id)
        case .nginxProxyManager:
            npmClients.removeValue(forKey: id)
        case .pangolin:
            pangolinClients.removeValue(forKey: id)
        case .patchmon:
            patchmonClients.removeValue(forKey: id)
        case .jellystat:
            jellystatClients.removeValue(forKey: id)
        case .plex:
            plexClients.removeValue(forKey: id)
        case .qbittorrent:
            qbittorrentClients.removeValue(forKey: id)
        case .radarr:
            radarrClients.removeValue(forKey: id)
        case .sonarr:
            sonarrClients.removeValue(forKey: id)
        case .lidarr:
            lidarrClients.removeValue(forKey: id)
        case .wakapi:
            wakapiClients.removeValue(forKey: id)
        case .proxmox:
            proxmoxClients.removeValue(forKey: id)
        case .jellyseerr, .prowlarr, .bazarr, .gluetun, .flaresolverr:
            genericClients.removeValue(forKey: id)
        }
    }

    /// Removes all clients whose instance IDs are no longer present in the store.
    /// Call this after deleting instances or rebuilding the service list.
    func purgeUnknownClients(knownInstanceIds: Set<UUID>) {
        portainerClients = portainerClients.filter { knownInstanceIds.contains($0.key) }
        piholeClients = piholeClients.filter { knownInstanceIds.contains($0.key) }
        adguardClients = adguardClients.filter { knownInstanceIds.contains($0.key) }
        technitiumClients = technitiumClients.filter { knownInstanceIds.contains($0.key) }
        beszelClients = beszelClients.filter { knownInstanceIds.contains($0.key) }
        healthchecksClients = healthchecksClients.filter { knownInstanceIds.contains($0.key) }
        linuxUpdateClients = linuxUpdateClients.filter { knownInstanceIds.contains($0.key) }
        dockhandClients = dockhandClients.filter { knownInstanceIds.contains($0.key) }
        craftyClients = craftyClients.filter { knownInstanceIds.contains($0.key) }
        giteaClients = giteaClients.filter { knownInstanceIds.contains($0.key) }
        npmClients = npmClients.filter { knownInstanceIds.contains($0.key) }
        pangolinClients = pangolinClients.filter { knownInstanceIds.contains($0.key) }
        patchmonClients = patchmonClients.filter { knownInstanceIds.contains($0.key) }
        jellystatClients = jellystatClients.filter { knownInstanceIds.contains($0.key) }
        plexClients = plexClients.filter { knownInstanceIds.contains($0.key) }
        qbittorrentClients = qbittorrentClients.filter { knownInstanceIds.contains($0.key) }
        radarrClients = radarrClients.filter { knownInstanceIds.contains($0.key) }
        sonarrClients = sonarrClients.filter { knownInstanceIds.contains($0.key) }
        lidarrClients = lidarrClients.filter { knownInstanceIds.contains($0.key) }
        wakapiClients = wakapiClients.filter { knownInstanceIds.contains($0.key) }
        proxmoxClients = proxmoxClients.filter { knownInstanceIds.contains($0.key) }
        genericClients = genericClients.filter { knownInstanceIds.contains($0.key) }
    }
}

@Observable
@MainActor
final class ServicesStore {

    private(set) var instancesById: [UUID: ServiceInstance] = [:]
    private(set) var preferredInstanceIdByType: [ServiceType: UUID] = [:]
    private(set) var isReady: Bool = false
    private(set) var reachabilityByInstanceId: [UUID: Bool?] = [:]
    private(set) var pingingByInstanceId: [UUID: Bool] = [:]
    private(set) var isTailscaleConnected: Bool = false

    private var lastReachabilityCheck: Date?
    private var healthCheckTask: Task<Void, Never>?
    private let clientManager = ServiceClientManager()

    var connectedCount: Int { instancesById.count }
    var allInstances: [ServiceInstance] {
        instancesById.values.sorted { lhs, rhs in
            if lhs.type != rhs.type {
                return lhs.type.rawValue < rhs.type.rawValue
            }
            if lhs.displayLabel != rhs.displayLabel {
                return lhs.displayLabel.localizedCaseInsensitiveCompare(rhs.displayLabel) == .orderedAscending
            }
            return lhs.id.uuidString < rhs.id.uuidString
        }
    }

    init() {
        NotificationCenter.default.addObserver(
            forName: .serviceUnauthorized,
            object: nil,
            queue: .main
        ) { [weak self] notification in
            guard let instanceId = notification.userInfo?["instanceId"] as? UUID else { return }
            Task { @MainActor in
                await self?.handleUnauthorized(instanceId: instanceId)
            }
        }
    }

    func initialize() async {
        let state = KeychainService.loadServiceState()
        var didNormalize = false
        let normalizedInstances = state.instances.map { instance in
            let normalized = normalizedInstance(instance)
            if normalized != instance { didNormalize = true }
            return normalized
        }
        instancesById = Dictionary(uniqueKeysWithValues: normalizedInstances.map { ($0.id, $0) })
        preferredInstanceIdByType = state.preferredInstanceIdByType

        repairAllPreferredInstances()

        if didNormalize {
            persistState()
        }

        for instance in allInstances {
            await configureClient(for: instance, refreshPiHoleAuth: true)
            reachabilityByInstanceId[instance.id] = nil
        }

        isReady = true

        Task {
            await checkAllReachability()
            await checkTailscale()
        }

        startPeriodicHealthChecks()
    }

    func instances(for type: ServiceType) -> [ServiceInstance] {
        allInstances.filter { $0.type == type }
    }

    func instance(id: UUID) -> ServiceInstance? {
        instancesById[id]
    }

    func hasInstances(for type: ServiceType) -> Bool {
        !instances(for: type).isEmpty
    }

    func isConnected(_ type: ServiceType) -> Bool {
        hasInstances(for: type)
    }

    func preferredInstance(for type: ServiceType) -> ServiceInstance? {
        if let preferredId = preferredInstanceIdByType[type],
           let preferred = instancesById[preferredId],
           preferred.type == type {
            return preferred
        }

        let fallback = instances(for: type).first
        if preferredInstanceIdByType[type] != fallback?.id {
            preferredInstanceIdByType[type] = fallback?.id
            persistState()
        }
        return fallback
    }

    func preferredReachability(for type: ServiceType) -> Bool? {
        guard let instance = preferredInstance(for: type) else { return nil }
        return reachability(for: instance.id)
    }

    func isReachable(_ type: ServiceType) -> Bool? {
        preferredReachability(for: type)
    }

    func preferredPinging(for type: ServiceType) -> Bool {
        guard let instance = preferredInstance(for: type) else { return false }
        return isPinging(instanceId: instance.id)
    }

    func isPinging(_ type: ServiceType) -> Bool {
        preferredPinging(for: type)
    }

    func connection(for type: ServiceType) -> ServiceInstance? {
        preferredInstance(for: type)
    }

    func reachability(for instanceId: UUID) -> Bool? {
        guard instancesById[instanceId] != nil else { return nil }
        return reachabilityByInstanceId[instanceId] ?? nil
    }

    func isPinging(instanceId: UUID) -> Bool {
        pingingByInstanceId[instanceId] ?? false
    }

    func markInstanceReachable(_ instanceId: UUID) {
        guard instancesById[instanceId] != nil else { return }
        pingingByInstanceId[instanceId] = false
        reachabilityByInstanceId[instanceId] = true
    }

    func saveInstance(
        _ instance: ServiceInstance,
        refreshPiHoleAuth: Bool = false,
        triggerReachabilityCheck: Bool = true
    ) async {
        let normalized = normalizedInstance(instance)
        let previous = instancesById[normalized.id]
        instancesById[normalized.id] = normalized

        if previous?.type != normalized.type {
            repairPreferredInstance(for: previous?.type)
        }
        if preferredInstanceIdByType[normalized.type] == nil || preferredInstanceIdByType[normalized.type] == normalized.id {
            preferredInstanceIdByType[normalized.type] = normalized.id
        }

        persistState()
        await configureClient(for: normalized, refreshPiHoleAuth: refreshPiHoleAuth)

        if triggerReachabilityCheck {
            reachabilityByInstanceId[normalized.id] = nil
            Task { await checkReachability(for: normalized.id) }
        }
    }

    func deleteInstance(id: UUID) {
        guard let removed = instancesById.removeValue(forKey: id) else { return }
        reachabilityByInstanceId.removeValue(forKey: id)
        pingingByInstanceId.removeValue(forKey: id)
        clientManager.removeClient(id: id, type: removed.type)
        repairPreferredInstance(for: removed.type)
        persistState()
    }

    func setPreferredInstance(id: UUID, for type: ServiceType) {
        guard let instance = instancesById[id], instance.type == type else {
            repairPreferredInstance(for: type)
            return
        }
        preferredInstanceIdByType[type] = id
        persistState()
    }

    func updateFallbackURL(instanceId: UUID, fallbackUrl: String) async {
        guard let current = instancesById[instanceId] else { return }
        let normalizedFallback = normalizeOptionalURL(fallbackUrl)
        let updated = normalizedInstance(
            current.updating(
                fallbackUrl: normalizedFallback,
                allowSelfSigned: current.allowSelfSigned
            )
        )
        instancesById[instanceId] = updated
        persistState()
        await configureClient(for: updated, refreshPiHoleAuth: false)
    }

    func portainerClient(instanceId: UUID) async -> PortainerAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .portainer else { return nil }
        return clientManager.portainerClient(id: instance.id)
    }

    func piholeClient(instanceId: UUID) async -> PiHoleAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .pihole else { return nil }
        return clientManager.piholeClient(id: instance.id)
    }

    func adguardClient(instanceId: UUID) async -> AdGuardHomeAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .adguardHome else { return nil }
        return clientManager.adguardClient(id: instance.id)
    }

    func technitiumClient(instanceId: UUID) async -> TechnitiumAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .technitium else { return nil }
        return clientManager.technitiumClient(id: instance.id)
    }

    func beszelClient(instanceId: UUID) async -> BeszelAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .beszel else { return nil }
        return clientManager.beszelClient(id: instance.id)
    }

    func healthchecksClient(instanceId: UUID) async -> HealthchecksAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .healthchecks else { return nil }
        return clientManager.healthchecksClient(id: instance.id)
    }

    func linuxUpdateClient(instanceId: UUID) async -> LinuxUpdateAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .linuxUpdate else { return nil }
        return clientManager.linuxUpdateClient(id: instance.id)
    }

    func dockhandClient(instanceId: UUID) async -> DockhandAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .dockhand else { return nil }
        return clientManager.dockhandClient(id: instance.id)
    }

    func craftyClient(instanceId: UUID) async -> CraftyAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .craftyController else { return nil }
        return clientManager.craftyClient(id: instance.id)
    }

    func giteaClient(instanceId: UUID) async -> GiteaAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .gitea else { return nil }
        return clientManager.giteaClient(id: instance.id)
    }

    func npmClient(instanceId: UUID) async -> NginxProxyManagerAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .nginxProxyManager else { return nil }
        return clientManager.npmClient(id: instance.id)
    }

    func patchmonClient(instanceId: UUID) async -> PatchmonAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .patchmon else { return nil }
        return clientManager.patchmonClient(id: instance.id)
    }

    func pangolinClient(instanceId: UUID) async -> PangolinAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .pangolin else { return nil }
        return clientManager.pangolinClient(id: instance.id)
    }

    func jellystatClient(instanceId: UUID) async -> JellystatAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .jellystat else { return nil }
        return clientManager.jellystatClient(id: instance.id)
    }

    func plexClient(instanceId: UUID) async -> PlexAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .plex else { return nil }
        return clientManager.plexClient(id: instance.id)
    }

    func qbittorrentClient(instanceId: UUID) async -> QbittorrentAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .qbittorrent else { return nil }
        return clientManager.qbittorrentClient(id: instance.id)
    }

    func radarrClient(instanceId: UUID) async -> RadarrAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .radarr else { return nil }
        return clientManager.radarrClient(id: instance.id)
    }

    func sonarrClient(instanceId: UUID) async -> SonarrAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .sonarr else { return nil }
        return clientManager.sonarrClient(id: instance.id)
    }

    func lidarrClient(instanceId: UUID) async -> LidarrAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .lidarr else { return nil }
        return clientManager.lidarrClient(id: instance.id)
    }

    func wakapiClient(instanceId: UUID) async -> WakapiAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .wakapi else { return nil }
        return clientManager.wakapiClient(id: instance.id)
    }

    func proxmoxClient(instanceId: UUID) async -> ProxmoxAPIClient? {
        guard let instance = instancesById[instanceId], instance.type == .proxmox else { return nil }
        return clientManager.proxmoxClient(id: instance.id)
    }

    func genericMediaClient(instanceId: UUID) async -> GenericAPIClient? {
        guard let instance = instancesById[instanceId],
              [.jellyseerr, .prowlarr, .bazarr, .gluetun, .flaresolverr].contains(instance.type) else {
            return nil
        }
        return clientManager.genericClient(id: instance.id, type: instance.type)
    }

    func checkReachability(for instanceId: UUID) async {
        guard let instance = instancesById[instanceId], pingingByInstanceId[instanceId] != true else { return }

        pingingByInstanceId[instanceId] = true
        reachabilityByInstanceId[instanceId] = nil
        defer { pingingByInstanceId[instanceId] = false }

        let ok: Bool
        switch instance.type {
        case .portainer:
            ok = await clientManager.portainerClient(id: instanceId).ping()
        case .pihole:
            ok = await clientManager.piholeClient(id: instanceId).ping()
        case .adguardHome:
            ok = await clientManager.adguardClient(id: instanceId).ping()
        case .technitium:
            ok = await clientManager.technitiumClient(id: instanceId).ping()
        case .beszel:
            ok = await clientManager.beszelClient(id: instanceId).ping()
        case .healthchecks:
            ok = await clientManager.healthchecksClient(id: instanceId).ping()
        case .linuxUpdate:
            ok = await clientManager.linuxUpdateClient(id: instanceId).ping()
        case .dockhand:
            ok = await clientManager.dockhandClient(id: instanceId).ping()
        case .craftyController:
            ok = await clientManager.craftyClient(id: instanceId).ping()
        case .gitea:
            ok = await clientManager.giteaClient(id: instanceId).ping()
        case .nginxProxyManager:
            ok = await clientManager.npmClient(id: instanceId).ping()
        case .pangolin:
            ok = await clientManager.pangolinClient(id: instanceId).ping()
        case .patchmon:
            ok = await clientManager.patchmonClient(id: instanceId).ping()
        case .jellystat:
            ok = await clientManager.jellystatClient(id: instanceId).ping()
        case .plex:
            ok = await clientManager.plexClient(id: instanceId).ping()
        case .qbittorrent:
            ok = await clientManager.qbittorrentClient(id: instanceId).ping()
        case .radarr:
            ok = await clientManager.radarrClient(id: instanceId).ping()
        case .sonarr:
            ok = await clientManager.sonarrClient(id: instanceId).ping()
        case .lidarr:
            ok = await clientManager.lidarrClient(id: instanceId).ping()
        case .wakapi:
            ok = await clientManager.wakapiClient(id: instanceId).ping()
        case .proxmox:
            ok = await clientManager.proxmoxClient(id: instanceId).ping()
        case .jellyseerr, .prowlarr, .bazarr, .gluetun, .flaresolverr:
            ok = await clientManager.genericClient(id: instanceId, type: instance.type).ping()
        }

        reachabilityByInstanceId[instanceId] = ok
    }

    func checkAllReachability(force: Bool = false) async {
        if !force, let last = lastReachabilityCheck, Date().timeIntervalSince(last) < 5 {
            return
        }
        lastReachabilityCheck = Date()

        let ids = Array(instancesById.keys)
        guard !ids.isEmpty else { return }

        // Purge any orphaned clients that no longer correspond to known instances.
        clientManager.purgeUnknownClients(knownInstanceIds: Set(ids))

        await withTaskGroup(of: Void.self) { group in
            for id in ids {
                group.addTask { await self.checkReachability(for: id) }
            }
        }
        await checkTailscale()
    }

    func checkTailscale() async {
        var addrs: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&addrs) == 0, let first = addrs else {
            isTailscaleConnected = false
            return
        }
        defer { freeifaddrs(first) }

        func ipv4Value(_ ip: String) -> UInt32? {
            var addr = in_addr()
            guard inet_pton(AF_INET, ip, &addr) == 1 else { return nil }
            return UInt32(bigEndian: addr.s_addr)
        }

        let tailscaleStart = ipv4Value("100.64.0.0") ?? 0
        let tailscaleEnd = ipv4Value("100.127.255.255") ?? 0

        var cursor: UnsafeMutablePointer<ifaddrs>? = first
        var found = false
        while let addr = cursor {
            let name = String(cString: addr.pointee.ifa_name)
            let isTailscaleInterface = name.hasPrefix("utun") || name.localizedCaseInsensitiveContains("tailscale")
            if isTailscaleInterface, let sa = addr.pointee.ifa_addr {
                var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                getnameinfo(sa, socklen_t(sa.pointee.sa_len), &hostname, socklen_t(hostname.count), nil, 0, NI_NUMERICHOST)
                let slice = hostname.prefix { $0 != 0 }
                let bytes = slice.map { UInt8(bitPattern: $0) }
                let ip = String(decoding: bytes, as: UTF8.self)
                switch sa.pointee.sa_family {
                case UInt8(AF_INET):
                    if let value = ipv4Value(ip), value >= tailscaleStart, value <= tailscaleEnd {
                        found = true
                    }
                case UInt8(AF_INET6):
                    if ip.lowercased().hasPrefix("fd7a:115c:a1e0") {
                        found = true
                    }
                default:
                    break
                }
                if found { break }
            }
            cursor = addr.pointee.ifa_next
        }
        isTailscaleConnected = found
    }

    func startPeriodicHealthChecks() {
        guard healthCheckTask == nil else { return }
        healthCheckTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(180))
                guard !Task.isCancelled else { break }
                await self?.checkAllReachability()
            }
        }
    }

    func stopPeriodicHealthChecks() {
        healthCheckTask?.cancel()
        healthCheckTask = nil
    }

    private func persistState() {
        KeychainService.saveServiceState(
            ServiceStateV2(
                instances: allInstances,
                preferredInstanceIdByType: preferredInstanceIdByType
            )
        )
    }

    private func repairAllPreferredInstances() {
        for type in ServiceType.allCases {
            repairPreferredInstance(for: type)
        }
    }

    private func repairPreferredInstance(for type: ServiceType?) {
        guard let type else { return }
        let validPreferred = preferredInstanceIdByType[type].flatMap { id in
            instancesById[id].flatMap { $0.type == type ? $0.id : nil }
        }
        if let validPreferred {
            preferredInstanceIdByType[type] = validPreferred
            return
        }

        preferredInstanceIdByType[type] = instances(for: type).first?.id
    }

    private func handleUnauthorized(instanceId: UUID) async {
        guard let current = instancesById[instanceId] else { return }

        if current.type == .pihole,
           let password = current.piHoleStoredSecret,
           !password.isEmpty {
            do {
                let client = clientManager.piholeClient(id: instanceId)
                let refreshedSID = try await client.authenticate(url: current.url, password: password, fallbackUrl: current.fallbackUrl)
                let authMode: PiHoleAuthMode = refreshedSID == password ? .legacy : .session
                let refreshed = current.updatingToken(refreshedSID, piholeAuthMode: authMode)
                instancesById[instanceId] = refreshed
                persistState()
                await configureClient(for: refreshed, refreshPiHoleAuth: false)
                return
            } catch {
                // Fall through to marking unreachable.
            }
        }

        // Beszel: re-authenticate using stored email/password (PocketBase JWT refresh)
        if current.type == .beszel,
           let email = current.username, !email.isEmpty,
           let password = current.password, !password.isEmpty {
            do {
                let client = clientManager.beszelClient(id: instanceId)
                let newToken = try await client.authenticate(url: current.url, email: email, password: password)
                var refreshed = current
                refreshed.token = newToken
                instancesById[instanceId] = refreshed
                persistState()
                await configureClient(for: refreshed, refreshPiHoleAuth: false)
                return
            } catch {
                // Fall through to marking unreachable.
            }
        }

        // Nginx Proxy Manager: re-authenticate using stored email/password
        if current.type == .nginxProxyManager,
           let email = current.username, !email.isEmpty,
           let password = current.password, !password.isEmpty {
            do {
                let client = clientManager.npmClient(id: instanceId)
                let newToken = try await client.authenticate(url: current.url, email: email, password: password, fallbackUrl: current.fallbackUrl)
                var refreshed = current
                refreshed.token = newToken
                instancesById[instanceId] = refreshed
                persistState()
                await configureClient(for: refreshed, refreshPiHoleAuth: false)
                return
            } catch {
                // Fall through to marking unreachable.
            }
        }

        // Keep stored instances and mark them temporarily unreachable when auth expires.
        // This prevents destructive data loss for services that require re-login.
        // Proxmox: re-authenticate using stored username/password
        if current.type == .proxmox,
           current.proxmoxAuthMode != .apiToken,
           let username = current.username, !username.isEmpty,
           let password = current.password, !password.isEmpty {
            let client = clientManager.proxmoxClient(id: instanceId)
            if await client.refreshAuthenticatedSession() {
                return
            }
        }

        if instancesById[instanceId] != nil {
            reachabilityByInstanceId[instanceId] = false
            persistState()
        }
    }


    private func configureClient(for instance: ServiceInstance, refreshPiHoleAuth: Bool) async {
        switch instance.type {
        case .portainer:
            let client = clientManager.portainerClient(id: instance.id)
            if let apiKey = instance.apiKey, !apiKey.isEmpty {
                await client.configureWithApiKey(url: instance.url, apiKey: apiKey, fallbackUrl: instance.fallbackUrl, allowSelfSigned: instance.allowSelfSigned)
            } else {
                await client.configure(url: instance.url, jwt: instance.token, fallbackUrl: instance.fallbackUrl, allowSelfSigned: instance.allowSelfSigned)
            }

        case .pihole:
            let client = clientManager.piholeClient(id: instance.id)
            let configuredSID: String
            var authMode = instance.piholeAuthMode

            if refreshPiHoleAuth,
               let password = instance.piHoleStoredSecret,
               !password.isEmpty {
                do {
                    configuredSID = try await client.authenticate(url: instance.url, password: password, fallbackUrl: instance.fallbackUrl)
                    authMode = configuredSID == password ? .legacy : .session
                    let refreshed = instance.updatingToken(configuredSID, piholeAuthMode: authMode)
                    if refreshed != instance {
                        instancesById[instance.id] = refreshed
                        persistState()
                    }
                } catch {
                    configuredSID = instance.token
                }
            } else {
                configuredSID = instance.token
            }

            await client.configure(url: instance.url, sid: configuredSID, authMode: authMode, fallbackUrl: instance.fallbackUrl, password: instance.piHoleStoredSecret, allowSelfSigned: instance.allowSelfSigned)
            let instanceId = instance.id
            await client.setTokenRefreshCallback { [weak self] newSid, newMode in
                Task { @MainActor in
                    guard let self else { return }
                    if var existing = self.instancesById[instanceId] {
                        existing = existing.updatingToken(newSid, piholeAuthMode: newMode)
                        self.instancesById[instanceId] = existing
                        self.persistState()
                    }
                }
            }

        case .adguardHome:
            let client = clientManager.adguardClient(id: instance.id)
            await client.configure(url: instance.url, username: instance.username ?? "", password: instance.password ?? "", fallbackUrl: instance.fallbackUrl, allowSelfSigned: instance.allowSelfSigned)

        case .technitium:
            let client = clientManager.technitiumClient(id: instance.id)
            await client.configure(
                url: instance.url,
                token: instance.token,
                fallbackUrl: instance.fallbackUrl,
                username: instance.username,
                password: instance.password
            , allowSelfSigned: instance.allowSelfSigned)
            let instanceId = instance.id
            await client.setTokenRefreshCallback { [weak self] newToken in
                Task { @MainActor in
                    guard let self, var current = self.instancesById[instanceId] else { return }
                    current.token = newToken
                    self.instancesById[instanceId] = current
                    self.persistState()
                }
            }

        case .beszel:
            let client = clientManager.beszelClient(id: instance.id)
            await client.configure(url: instance.url, token: instance.token, fallbackUrl: instance.fallbackUrl, email: instance.username, password: instance.password, allowSelfSigned: instance.allowSelfSigned)
            let instanceId = instance.id
            await client.setTokenRefreshCallback { [weak self] newToken in
                Task { @MainActor in
                    guard let self, var current = self.instancesById[instanceId] else { return }
                    current.token = newToken
                    self.instancesById[instanceId] = current
                    self.persistState()
                }
            }

        case .healthchecks:
            let client = clientManager.healthchecksClient(id: instance.id)
            await client.configure(url: instance.url, apiKey: instance.apiKey ?? "", fallbackUrl: instance.fallbackUrl, allowSelfSigned: instance.allowSelfSigned)

        case .linuxUpdate:
            let client = clientManager.linuxUpdateClient(id: instance.id)
            await client.configure(url: instance.url, apiToken: instance.apiKey ?? "", fallbackUrl: instance.fallbackUrl, allowSelfSigned: instance.allowSelfSigned)

        case .dockhand:
            let client = clientManager.dockhandClient(id: instance.id)
            await client.configure(
                url: instance.url,
                sessionCookie: instance.token,
                fallbackUrl: instance.fallbackUrl,
                username: instance.username,
                password: instance.password
            , allowSelfSigned: instance.allowSelfSigned)

        case .craftyController:
            let client = clientManager.craftyClient(id: instance.id)
            await client.configure(
                url: instance.url,
                username: instance.username ?? "",
                password: instance.password ?? "",
                token: instance.token,
                fallbackUrl: instance.fallbackUrl
            , allowSelfSigned: instance.allowSelfSigned)

        case .gitea:
            let client = clientManager.giteaClient(id: instance.id)
            await client.configure(url: instance.url, token: instance.token, fallbackUrl: instance.fallbackUrl, allowSelfSigned: instance.allowSelfSigned)

        case .nginxProxyManager:
            let client = clientManager.npmClient(id: instance.id)
            await client.configure(
                url: instance.url,
                token: instance.token,
                fallbackUrl: instance.fallbackUrl,
                email: instance.username,
                password: instance.password
            , allowSelfSigned: instance.allowSelfSigned)
            let instanceId = instance.id
            await client.setTokenRefreshCallback { [weak self] newToken in
                Task { @MainActor in
                    guard let self, var current = self.instancesById[instanceId] else { return }
                    current.token = newToken
                    self.instancesById[instanceId] = current
                    self.persistState()
                }
            }

        case .pangolin:
            let client = clientManager.pangolinClient(id: instance.id)
            await client.configure(
                url: instance.url,
                apiKey: instance.apiKey ?? "",
                fallbackUrl: instance.fallbackUrl,
                orgId: instance.username
            , allowSelfSigned: instance.allowSelfSigned)

        case .patchmon:
            let client = clientManager.patchmonClient(id: instance.id)
            await client.configure(
                url: instance.url,
                tokenKey: instance.username ?? "",
                tokenSecret: instance.password ?? "",
                fallbackUrl: instance.fallbackUrl
            , allowSelfSigned: instance.allowSelfSigned)

        case .jellystat:
            let client = clientManager.jellystatClient(id: instance.id)
            await client.configure(
                url: instance.url,
                apiKey: instance.apiKey ?? "",
                fallbackUrl: instance.fallbackUrl
            , allowSelfSigned: instance.allowSelfSigned)

        case .wakapi:
            let client = clientManager.wakapiClient(id: instance.id)
            await client.configure(
                url: instance.url,
                apiKey: instance.apiKey ?? "",
                fallbackUrl: instance.fallbackUrl
            , allowSelfSigned: instance.allowSelfSigned)

        case .plex:
            let client = clientManager.plexClient(id: instance.id)
            await client.configure(
                url: instance.url,
                token: instance.apiKey ?? "",
                fallbackUrl: instance.fallbackUrl
            , allowSelfSigned: instance.allowSelfSigned)
        case .qbittorrent:
            let client = clientManager.qbittorrentClient(id: instance.id)
            await client.configure(
                url: instance.url,
                sid: instance.token,
                fallbackUrl: instance.fallbackUrl,
                username: instance.username,
                password: instance.password
            , allowSelfSigned: instance.allowSelfSigned)
            let instanceId = instance.id
            await client.setTokenRefreshCallback { [weak self] newSid in
                Task { @MainActor in
                    guard let self, var current = self.instancesById[instanceId] else { return }
                    current.token = newSid
                    self.instancesById[instanceId] = current
                    self.persistState()
                }
            }
        case .radarr:
            let client = clientManager.radarrClient(id: instance.id)
            await client.configure(url: instance.url, apiKey: instance.apiKey ?? "", fallbackUrl: instance.fallbackUrl, allowSelfSigned: instance.allowSelfSigned)
        case .sonarr:
            let client = clientManager.sonarrClient(id: instance.id)
            await client.configure(url: instance.url, apiKey: instance.apiKey ?? "", fallbackUrl: instance.fallbackUrl, allowSelfSigned: instance.allowSelfSigned)
        case .lidarr:
            let client = clientManager.lidarrClient(id: instance.id)
            await client.configure(url: instance.url, apiKey: instance.apiKey ?? "", fallbackUrl: instance.fallbackUrl, allowSelfSigned: instance.allowSelfSigned)
        case .jellyseerr, .prowlarr, .bazarr, .gluetun, .flaresolverr:
            let client = clientManager.genericClient(id: instance.id, type: instance.type)
            await client.configure(url: instance.url, fallbackUrl: instance.fallbackUrl, apiKey: instance.apiKey, allowSelfSigned: instance.allowSelfSigned)

        case .proxmox:
            let client = clientManager.proxmoxClient(id: instance.id)
            let authMode = instance.proxmoxAuthMode ?? ((instance.password?.isEmpty == false || instance.username?.isEmpty == false) ? .credentials : .apiToken)
            await client.configure(
                url: instance.url,
                fallbackUrl: instance.fallbackUrl,
                ticket: authMode == .credentials ? instance.token : nil,
                csrfToken: authMode == .credentials ? instance.apiKey : nil,
                apiTokenString: authMode == .apiToken ? instance.apiKey : nil,
                username: instance.username,
                password: instance.password,
                otp: instance.proxmoxOTP,
                realm: instance.proxmoxRealm,
                allowSelfSigned: instance.allowSelfSigned
            )
            let instanceId = instance.id
            await client.setTokenRefreshCallback { [weak self] newTicket, newCsrf in
                Task { @MainActor in
                    guard let self, var current = self.instancesById[instanceId] else { return }
                    current.token = newTicket
                    current = current.updating(apiKey: newCsrf)
                    self.instancesById[instanceId] = current
                    self.persistState()
                }
            }
        }
    }

    // MARK: - URL Normalization

    private func normalizeServiceURL(_ raw: String) -> String {
        var clean = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let trailing = CharacterSet(charactersIn: ")]},;")
        while let last = clean.unicodeScalars.last, trailing.contains(last) {
            clean = String(clean.dropLast())
        }
        if !clean.hasPrefix("http://") && !clean.hasPrefix("https://") {
            clean = "https://" + clean
        }
        return clean.replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
    }

    private func normalizeOptionalURL(_ raw: String?) -> String? {
        guard let raw else { return nil }
        let normalized = normalizeServiceURL(raw)
        return normalized.isEmpty ? nil : normalized
    }

    private func normalizedInstance(_ instance: ServiceInstance) -> ServiceInstance {
        let normalizedUrl = normalizeServiceURL(instance.url)
        let normalizedFallback = normalizeOptionalURL(instance.fallbackUrl)
        if normalizedUrl == instance.url && normalizedFallback == instance.fallbackUrl {
            return instance
        }
        return instance.updating(
            url: normalizedUrl,
            fallbackUrl: normalizedFallback,
            allowSelfSigned: instance.allowSelfSigned
        )
    }
}
