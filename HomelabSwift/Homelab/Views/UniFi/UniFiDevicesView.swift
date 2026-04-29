import SwiftUI
import UIKit

enum UniFiDeviceKind: String {
    case gateway
    case switchDevice
    case accessPoint
    case camera
    case storage
    case bridge
    case access
    case phone
    case other
}

enum UniFiDeviceClassifier {
    static func kind(for device: UniFiDevice) -> UniFiDeviceKind {
        let text = "\(device.type ?? "") \(device.model ?? "")".lowercased()
        if text.contains("camera") || text.contains("g4") || text.contains("g5") || text.contains("uvc") {
            return .camera
        }
        if text.contains("nvr") || text.contains("cloudkey") || text.contains("unvr") || text.contains("storage") {
            return .storage
        }
        if text.contains("door") || text.contains("access") || text.contains("hub") || text.contains("reader") {
            return .access
        }
        if text.contains("talk") || text.contains("phone") {
            return .phone
        }
        if text.contains("bridge") || text.contains("air") || text.contains("mesh") {
            return .bridge
        }
        if text.contains("switch") || text.contains("usw") {
            return .switchDevice
        }
        if text.contains("gateway") || text.contains("udm") || text.contains("uxg") || text.contains("usg") {
            return .gateway
        }
        if text.contains("ap") || text.contains("uap") || text.contains("wifi") {
            return .accessPoint
        }
        return .other
    }

    static func isAP(_ device: UniFiDevice) -> Bool { kind(for: device) == .accessPoint }
    static func isSwitch(_ device: UniFiDevice) -> Bool { kind(for: device) == .switchDevice }
    static func isGateway(_ device: UniFiDevice) -> Bool { kind(for: device) == .gateway }

    static func symbol(for device: UniFiDevice) -> String {
        switch kind(for: device) {
        case .gateway: return "globe"
        case .switchDevice: return "switch.2"
        case .accessPoint: return "wifi.router.fill"
        case .camera: return "video.fill"
        case .storage: return "externaldrive.fill"
        case .bridge: return "point.3.connected.trianglepath.dotted"
        case .access: return "door.left.hand.open"
        case .phone: return "phone.fill"
        case .other: return "antenna.radiowaves.left.and.right"
        }
    }

    static func accent(for device: UniFiDevice) -> Color {
        switch kind(for: device) {
        case .gateway: return ServiceType.unifiNetwork.colors.primary
        case .switchDevice: return Color(hex: "#3B82F6")
        case .accessPoint: return Color(hex: "#2563EB")
        case .camera: return Color(hex: "#8B5CF6")
        case .storage: return Color(hex: "#14B8A6")
        case .bridge: return Color(hex: "#F97316")
        case .access: return Color(hex: "#10B981")
        case .phone: return Color(hex: "#EC4899")
        case .other: return ServiceType.unifiNetwork.colors.primary
        }
    }
}

enum UniFiDeviceArtCatalog {
    private static let aliases: [String: String] = [
        "u7-wall": "u7-pro-wall",
        "usw-24-poe-95w": "usw-24-poe",
        "usw-lite-16-poe-45w": "usw-lite-16-poe",
        "usw-16-poe-42w": "usw-16-poe",
        "unifi-express": "ux",
        "uap-ac-m": "uap-ac-mesh",
        "udm-se": "dream-machine-special-edition",
        "ucg-max": "cloud-gateway-max",
        "ucg-ultra": "cloud-gateway-ultra",
        "ucg-fiber": "cloud-gateway-fiber",
        "ux7": "dream-router-7"
    ]

    static func image(for device: UniFiDevice) -> UIImage? {
        for name in assetNames(for: device) {
            if let image = UIImage(named: name) {
                return image
            }
        }
        return nil
    }

    private static func assetNames(for device: UniFiDevice) -> [String] {
        var names: [String] = []
        var seen = Set<String>()

        for slug in candidateSlugs(for: device) {
            for assetName in preferredAssetNames(for: slug) {
                if seen.insert(assetName).inserted {
                    names.append(assetName)
                }
            }
        }

        return names
    }

    private static func candidateSlugs(for device: UniFiDevice) -> [String] {
        let rawCandidates = [
            device.model,
            device.type,
            device.version,
            device.displayName
        ]
        var slugs: [String] = []
        var seen = Set<String>()
        for raw in rawCandidates.compactMap({ $0?.trimmingCharacters(in: .whitespacesAndNewlines) }).filter({ !$0.isEmpty }) {
            let slug = slugify(raw)
            guard !slug.isEmpty else { continue }
            if seen.insert(slug).inserted {
                slugs.append(slug)
            }
            if let alias = aliases[slug], seen.insert(alias).inserted {
                slugs.append(alias)
            }
        }
        return slugs
    }

    private static func preferredAssetNames(for slug: String) -> [String] {
        var names = ["unifi-device-\(slug)"]
        for translated in translatedWebSlugs(for: slug) {
            names.insert("ubiquiti-device-\(translated)", at: 0)
        }
        if slug.hasPrefix("dream-machine-") || slug.hasPrefix("cloud-gateway-") {
            names.insert("ubiquiti-device-\(slug)", at: 0)
        }
        return names
    }

    private static func translatedWebSlugs(for slug: String) -> [String] {
        if let alias = aliases[slug] {
            return [alias]
        }

        var candidates: [String] = []

        if slug.hasPrefix("uap-") {
            candidates.append("access-point-\(normalizedTokenString(String(slug.dropFirst(4))))")
        } else if slug.hasPrefix("u6-") || slug.hasPrefix("u7-") || slug.hasPrefix("e7") {
            candidates.append("access-point-\(normalizedTokenString(slug))")
        } else if slug.hasPrefix("usw-") {
            candidates.append("switch-\(normalizedTokenString(String(slug.dropFirst(4))))")
        } else if slug.hasPrefix("udm-") {
            candidates.append("dream-machine-\(normalizedTokenString(String(slug.dropFirst(4))))")
        } else if slug.hasPrefix("ucg-") {
            candidates.append("cloud-gateway-\(normalizedTokenString(String(slug.dropFirst(4))))")
        } else if slug == "ux" {
            candidates.append("unifi-express-7")
            candidates.append("unifi-travel-router")
        }

        switch slug {
        case "uap-ac-m":
            candidates.append("access-point-ac-mesh")
        case "uap-ac-m-pro":
            candidates.append("access-point-ac-mesh")
        case "u7-wall":
            candidates.append("access-point-u7-pro-wall")
        case "u7-pro-xg-wall":
            candidates.append("access-point-u7-pro-xg-wall")
        case "ux7":
            candidates.append("dream-router-7")
        default:
            break
        }

        var deduped: [String] = []
        var seen = Set<String>()
        for candidate in candidates where seen.insert(candidate).inserted {
            deduped.append(candidate)
        }
        return deduped
    }

    private static func normalizedTokenString(_ slug: String) -> String {
        slug
            .split(separator: "-")
            .map { token -> String in
                switch token {
                case "iw": return "in-wall"
                case "lr": return "long-range"
                case "se": return "special-edition"
                default: return String(token)
                }
            }
            .joined(separator: "-")
    }

    private static func slugify(_ value: String) -> String {
        value
            .lowercased()
            .replacingOccurrences(of: "[^a-z0-9]+", with: "-", options: .regularExpression)
            .trimmingCharacters(in: CharacterSet(charactersIn: "-"))
    }
}

struct UniFiDeviceGlyph: View {
    let device: UniFiDevice
    var size: CGFloat = 22
    var compact: Bool = false
    var boxSize: CGFloat? = nil
    var showsTileBackground: Bool = true
    var showsUpdateBadge: Bool = true

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let tint = UniFiDeviceClassifier.accent(for: device)
        let frameSize = boxSize ?? (compact ? 34 : 56)
        let productArt = UniFiDeviceArtCatalog.image(for: device)
        let hasProductArt = productArt != nil

        ZStack(alignment: .topTrailing) {
            if showsTileBackground {
                RoundedRectangle(cornerRadius: compact ? 12 : 18, style: .continuous)
                    .fill(
                        hasProductArt
                            ? Color(.secondarySystemGroupedBackground)
                            : tint.opacity(colorScheme == .dark ? 0.16 : 0.09)
                    )
            }

            if let image = productArt {
                Image(uiImage: image)
                    .resizable()
                    .interpolation(.high)
                    .scaledToFit()
                    .padding(showsTileBackground ? (compact ? 5 : 8) : 0)
                    .opacity(device.isOnline ? 1 : 0.72)
            } else {
                Image(systemName: UniFiDeviceClassifier.symbol(for: device))
                    .font(.system(size: size, weight: .semibold))
                    .foregroundStyle(device.isOnline ? tint : AppTheme.warning)
            }

            if showsUpdateBadge, device.updateAvailable == true {
                Image(systemName: "arrow.up.circle.fill")
                    .font(.system(size: compact ? 10 : 12, weight: .bold))
                    .foregroundStyle(AppTheme.info)
                    .background(Color(.systemBackground), in: Circle())
                    .offset(x: compact ? 4 : 6, y: compact ? -4 : -6)
            }
        }
        .frame(width: frameSize, height: frameSize)
    }
}

struct UniFiDevicesView: View {
    let instanceId: UUID
    let initialDevices: [UniFiDevice]
    let sites: [UniFiSite]

    @Environment(Localizer.self) private var localizer

    @State private var devices: [UniFiDevice]
    @State private var searchText = ""
    @State private var filter: DeviceFilter = .all
    @State private var selectedDevice: UniFiDevice?
    @State private var selectedSiteId: String?

    private let color = ServiceType.unifiNetwork.colors.primary

    init(instanceId: UUID, initialDevices: [UniFiDevice], sites: [UniFiSite] = [], selectedSiteId: String? = nil) {
        self.instanceId = instanceId
        self.initialDevices = initialDevices
        self.sites = sites
        _devices = State(initialValue: initialDevices)
        _selectedSiteId = State(initialValue: selectedSiteId)
    }

    enum DeviceFilter: String, CaseIterable, Identifiable {
        case all, online, offline, ap, switch_, gateway
        var id: String { rawValue }
    }

    private var siteNameById: [String: String] {
        Dictionary(uniqueKeysWithValues: sites.map { ($0.siteId, $0.displayName) })
    }

    private var availableSites: [UniFiSite] {
        sites.filter { site in
            devices.contains { $0.siteId == site.siteId }
        }
    }

    private var filteredDevices: [UniFiDevice] {
        var result = devices
        if let selectedSiteId {
            result = result.filter { $0.siteId == selectedSiteId }
        }
        switch filter {
        case .all: break
        case .online: result = result.filter(\.isOnline)
        case .offline: result = result.filter { !$0.isOnline }
        case .ap: result = result.filter { UniFiDeviceClassifier.isAP($0) }
        case .switch_: result = result.filter { UniFiDeviceClassifier.isSwitch($0) }
        case .gateway: result = result.filter { UniFiDeviceClassifier.isGateway($0) }
        }
        guard !searchText.isEmpty else { return result }
        return result.filter {
            $0.displayName.localizedCaseInsensitiveContains(searchText) ||
            $0.ipAddress?.localizedCaseInsensitiveContains(searchText) == true ||
            $0.model?.localizedCaseInsensitiveContains(searchText) == true ||
            $0.macAddress?.localizedCaseInsensitiveContains(searchText) == true ||
            siteNameById[$0.siteId ?? ""]?.localizedCaseInsensitiveContains(searchText) == true
        }
    }

    var body: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                if availableSites.count > 1 {
                    siteFilterBar
                        .padding(.top, 4)
                }
                filterBar

                if filteredDevices.isEmpty {
                    emptyState
                } else {
                    ForEach(filteredDevices) { device in
                        Button {
                            HapticManager.light()
                            selectedDevice = device
                        } label: {
                            deviceRow(device)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(AppTheme.padding)
        }
        .navigationTitle(localizer.t.unifiDevices)
        .navigationBarTitleDisplayMode(.large)
        .searchable(text: $searchText, prompt: localizer.t.unifiSearchDevices)
        .sheet(item: $selectedDevice) { device in
            UniFiDeviceDetailSheet(device: device, siteName: siteNameById[device.siteId ?? ""])
        }
    }

    private var siteFilterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                filterPill(
                    title: localizer.t.unifiAllSites,
                    selected: selectedSiteId == nil
                ) {
                    selectedSiteId = nil
                }
                ForEach(availableSites) { site in
                    filterPill(
                        title: site.displayName,
                        selected: selectedSiteId == site.siteId
                    ) {
                        selectedSiteId = site.siteId
                    }
                }
            }
        }
    }

    private var filterBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(DeviceFilter.allCases) { item in
                    filterPill(title: label(for: item), selected: filter == item) {
                        filter = item
                    }
                }
            }
        }
    }

    private func filterPill(title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button {
            HapticManager.light()
            action()
        } label: {
            Text(title)
                .font(.caption.bold())
                .foregroundStyle(selected ? .white : color)
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(selected ? color : color.opacity(0.12), in: Capsule())
        }
        .buttonStyle(.plain)
        .animation(.spring(response: 0.3, dampingFraction: 0.8), value: selected)
    }

    private func label(for filter: DeviceFilter) -> String {
        switch filter {
        case .all: return localizer.t.unifiAll
        case .online: return localizer.t.unifiOnlineDevices
        case .offline: return localizer.t.unifiOfflineDevices
        case .ap: return localizer.t.unifiAPs
        case .switch_: return localizer.t.unifiSwitches
        case .gateway: return localizer.t.unifiGateways
        }
    }

    private func deviceRow(_ device: UniFiDevice) -> some View {
        HStack(spacing: 14) {
            UniFiDeviceGlyph(device: device)

            VStack(alignment: .leading, spacing: 6) {
                HStack(spacing: 6) {
                    Text(device.displayName)
                        .font(.subheadline.bold())
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                    if let siteName = siteNameById[device.siteId ?? ""], availableSites.count > 1 {
                        Text(siteName)
                            .font(.caption2.bold())
                            .foregroundStyle(color)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(color.opacity(0.12), in: Capsule())
                    }
                }

                Text([device.model?.nilIfEmpty, device.ipAddress?.nilIfEmpty]
                    .compactMap { $0 }
                    .joined(separator: " • "))
                    .font(.caption)
                    .foregroundStyle(AppTheme.textSecondary)
                    .lineLimit(1)

                if let metrics = deviceMetricsLine(device) {
                    Text(metrics)
                        .font(.caption2.weight(.medium))
                        .foregroundStyle(AppTheme.textMuted)
                        .lineLimit(1)
                }
            }

            Spacer(minLength: 10)

            VStack(alignment: .trailing, spacing: 7) {
                Circle()
                    .fill(device.isOnline ? AppTheme.running : AppTheme.warning)
                    .frame(width: 9, height: 9)
                if let rate = device.liveTrafficBytesPerSecond {
                    Text(rateString(rate))
                        .font(.caption2.bold())
                        .foregroundStyle(UniFiDeviceClassifier.accent(for: device))
                } else if let kind = device.type?.nilIfEmpty ?? device.model?.nilIfEmpty {
                    Text(String(kind.prefix(4)).uppercased())
                        .font(.caption2.bold())
                        .foregroundStyle(AppTheme.textMuted)
                }
            }
        }
        .padding(14)
        .glassCard(tint: device.isOnline ? nil : AppTheme.warning.opacity(0.04))
        .contentShape(Rectangle())
    }

    private func deviceMetricsLine(_ device: UniFiDevice) -> String? {
        var parts: [String] = []
        if let clients = device.clientCount, clients > 0 {
            parts.append("\(clients) \(localizer.t.unifiClients.lowercased())")
        }
        if device.totalPortCount > 0 {
            parts.append("\(device.activePortCount)/\(device.totalPortCount) \(localizer.t.unifiPorts.lowercased())")
        }
        if let poe = device.poePowerWatts {
            parts.append("PoE \(String(format: "%.1fW", poe))")
        }
        return parts.isEmpty ? nil : parts.joined(separator: " • ")
    }

    private var emptyState: some View {
        VStack(spacing: 14) {
            Image(systemName: "antenna.radiowaves.left.and.right.slash")
                .font(.system(size: 44))
                .foregroundStyle(AppTheme.textMuted)
            Text(localizer.t.noData)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 60)
    }

    private func rateString(_ value: Double) -> String {
        "\(Formatters.formatBytes(value))/s"
    }
}

struct UniFiDeviceDetailSheet: View {
    let device: UniFiDevice
    let siteName: String?

    @Environment(Localizer.self) private var localizer
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    private let color = ServiceType.unifiNetwork.colors.primary

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    headerCard
                    if device.updateAvailable == true { updateBanner }
                    if hasQuickStats {
                        quickStatsCard
                    }
                    if !device.radios.isEmpty {
                        radiosCard
                    }
                    if device.hasHealthStats {
                        healthCard
                    }
                    if !activePorts.isEmpty {
                        portsCard
                    }
                    infoCard
                }
                .padding(AppTheme.padding)
            }
            .navigationTitle(localizer.t.unifiDeviceDetail)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    UniFiSheetCloseButton { dismiss() }
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(28)
    }

    private var hasQuickStats: Bool {
        device.liveRxRateBytesPerSecond != nil ||
        device.liveTxRateBytesPerSecond != nil ||
        device.clientCount != nil ||
        device.totalPortCount > 0 ||
        device.poePowerWatts != nil
    }

    private var activePorts: [UniFiDevicePort] {
        device.ports
            .filter { $0.hasTraffic || $0.up == true || $0.isUplink == true }
            .sorted { ($0.liveTrafficBytesPerSecond ?? 0) > ($1.liveTrafficBytesPerSecond ?? 0) }
    }

    private var headerCard: some View {
        VStack(spacing: 18) {
            UniFiDeviceGlyph(
                device: device,
                size: 30,
                compact: false,
                boxSize: 156,
                showsTileBackground: false,
                showsUpdateBadge: false
            )
            .frame(maxWidth: .infinity)

            VStack(spacing: 8) {
                Text(device.displayName)
                    .font(.system(size: 32, weight: .bold, design: .rounded))
                    .foregroundStyle(.primary)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)

                HStack(spacing: 8) {
                    detailChip(device.kindLabel, tint: UniFiDeviceClassifier.accent(for: device))
                    if let siteName {
                        detailChip(siteName, tint: color.opacity(0.85), isFilled: false)
                    }
                }

                statusPill(device.isOnline)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 8)
    }

    private var updateBanner: some View {
        HStack(spacing: 10) {
            Image(systemName: "arrow.up.circle.fill")
                .foregroundStyle(AppTheme.info)
            Text(String(format: localizer.t.unifiPendingUpdatesFormat, 1))
                .font(.subheadline.bold())
                .foregroundStyle(AppTheme.info)
            Spacer()
        }
        .padding(14)
        .glassCard(tint: AppTheme.info.opacity(0.08))
    }

    private var quickStatsCard: some View {
        let tiles: [(String, String, Color)] = [
            (localizer.t.unifiDownload, rateString(device.liveRxRateBytesPerSecond), AppTheme.running),
            (localizer.t.unifiUpload, rateString(device.liveTxRateBytesPerSecond), color),
            (localizer.t.unifiClients, device.clientCount.map(String.init) ?? localizer.t.notAvailable, Color(hex: "#A855F7")),
            (localizer.t.unifiPorts, device.totalPortCount > 0 ? "\(device.activePortCount)/\(device.totalPortCount)" : localizer.t.notAvailable, Color(hex: "#F97316"))
        ]

        return VStack(alignment: .leading, spacing: 14) {
            Text(localizer.t.unifiTrafficNow)
                .font(.headline.bold())

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                ForEach(Array(tiles.enumerated()), id: \.offset) { _, tile in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(tile.1)
                            .font(.headline.bold())
                            .foregroundStyle(tile.2)
                        Text(tile.0)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(tile.2.opacity(colorScheme == .dark ? 0.12 : 0.07), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
            }

            if let poe = device.poePowerWatts {
                HStack {
                    Text("PoE")
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(AppTheme.textSecondary)
                    Spacer()
                    Text(String(format: "%.1f W", poe))
                        .font(.subheadline.bold())
                        .foregroundStyle(Color(hex: "#F59E0B"))
                }
            }
        }
        .padding(16)
        .glassCard()
    }

    private var radiosCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text(localizer.t.unifiWifiRadios)
                .font(.headline.bold())

            ForEach(Array(device.radios.enumerated()), id: \.offset) { _, radio in
                HStack(alignment: .center, spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(color.opacity(colorScheme == .dark ? 0.16 : 0.08))
                        VStack(spacing: 2) {
                            Text(radio.radio ?? "Radio")
                                .font(.subheadline.bold())
                                .foregroundStyle(color)
                            if let channel = radio.channel {
                                Text("Ch \(channel)")
                                    .font(.caption2.bold())
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                        }
                    }
                    .frame(width: 72, height: 58)

                    VStack(alignment: .leading, spacing: 4) {
                        Text(radioSubtitle(radio))
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.primary)
                        Text(radioDetailLine(radio))
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                            .lineLimit(2)
                    }

                    Spacer()

                    if let satisfaction = radio.satisfaction {
                        VStack(alignment: .trailing, spacing: 3) {
                            Text("\(Int(satisfaction.rounded()))%")
                                .font(.subheadline.bold())
                                .foregroundStyle(satisfactionColor(satisfaction))
                            Text(localizer.t.unifiQuality)
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                        }
                    }
                }
                .padding(12)
                .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
        }
        .padding(16)
        .glassCard()
    }

    private var healthCard: some View {
        let cpuValue = device.cpuUsagePercent.map { Formatters.formatPercent($0) }
        let ramValue = device.memoryUsagePercent.map { Formatters.formatPercent($0) }
        let temperatureValue = device.temperatureCelsius.map { String(format: "%.0f°C", $0) }

        return VStack(alignment: .leading, spacing: 14) {
            Text(localizer.t.unifiDeviceHealth)
                .font(.headline.bold())

            if cpuValue != nil || ramValue != nil {
                HStack(spacing: 12) {
                    if let cpuValue {
                        healthTile(label: "CPU", value: cpuValue, tint: color)
                    }
                    if let ramValue {
                        healthTile(label: "RAM", value: ramValue, tint: AppTheme.info)
                    }
                }
            }

            if let temperatureValue {
                HStack(spacing: 12) {
                    Image(systemName: "thermometer.medium")
                        .font(.headline.bold())
                        .foregroundStyle(Color(hex: "#F97316"))
                        .frame(width: 26)

                    VStack(alignment: .leading, spacing: 3) {
                        Text(temperatureValue)
                            .font(.headline.bold())
                            .foregroundStyle(Color(hex: "#F97316"))
                        Text(localizer.t.unifiTemperature)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                    }

                    Spacer()
                }
                .padding(12)
                .background(Color(hex: "#F97316").opacity(colorScheme == .dark ? 0.12 : 0.07), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
        }
        .padding(16)
        .glassCard()
    }

    private func healthTile(label: String, value: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(value)
                .font(.headline.bold())
                .foregroundStyle(tint)
            Text(label)
                .font(.caption)
                .foregroundStyle(AppTheme.textMuted)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(tint.opacity(colorScheme == .dark ? 0.12 : 0.07), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private var portsCard: some View {
        let topPorts = Array(activePorts.prefix(6))
        let maxTraffic = topPorts.compactMap(\.liveTrafficBytesPerSecond).max() ?? 1
        return VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text(localizer.t.unifiPorts)
                    .font(.headline.bold())
                Spacer()
                if let uplink = activePorts.first(where: { $0.isUplink == true }) {
                    Text(uplink.displayName)
                        .font(.caption.bold())
                        .foregroundStyle(color)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(color.opacity(0.12), in: Capsule())
                }
            }

            VStack(spacing: 10) {
                ForEach(topPorts) { port in
                    VStack(alignment: .leading, spacing: 10) {
                        HStack(spacing: 10) {
                            portBadge(port)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(port.displayName)
                                    .font(.subheadline.weight(.semibold))
                                Text(portSubtitle(port))
                                    .font(.caption)
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                            Spacer()
                            if let total = port.liveTrafficBytesPerSecond {
                                Text(rateString(total))
                                    .font(.caption.bold())
                                    .foregroundStyle(color)
                            }
                        }

                        if let total = port.liveTrafficBytesPerSecond {
                            VStack(spacing: 8) {
                                GeometryReader { geometry in
                                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                                        .fill(color.opacity(0.08))
                                        .overlay(alignment: .leading) {
                                            RoundedRectangle(cornerRadius: 8, style: .continuous)
                                                .fill(
                                                    LinearGradient(
                                                        colors: [AppTheme.running, color],
                                                        startPoint: .leading,
                                                        endPoint: .trailing
                                                    )
                                                )
                                                .frame(width: max(24, geometry.size.width * CGFloat(total / maxTraffic)))
                                        }
                                }
                                .frame(height: 10)

                                HStack(spacing: 14) {
                                    if let rx = port.rxRateBytesPerSecond {
                                        portMetric(localizer.t.unifiDownload, value: rateString(rx), tint: AppTheme.running)
                                    }
                                    if let tx = port.txRateBytesPerSecond {
                                        portMetric(localizer.t.unifiUpload, value: rateString(tx), tint: color)
                                    }
                                }
                            }
                        }
                    }
                    .padding(12)
                    .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
            }
        }
        .padding(16)
        .glassCard()
    }

    private var infoCard: some View {
        let rows: [(String, String)] = [
            ("IP", device.ipAddress ?? "—"),
            ("MAC", device.macAddress ?? "—"),
            ("Model", device.model ?? "—"),
            ("Firmware", device.firmwareVersion ?? device.version ?? "—"),
            ("Serial", device.serialNumber ?? "—"),
            ("Status", device.state?.capitalized ?? "—"),
            ("Uplink", device.uplinkDeviceName ?? "—"),
            (localizer.t.unifiSites, siteName ?? device.siteId ?? "—")
        ].filter { $0.1 != "—" }

        return VStack(spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.offset) { idx, row in
                HStack {
                    Text(row.0)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                    Spacer()
                    Text(row.1)
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(.primary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 13)
                if idx < rows.count - 1 {
                    Divider().padding(.leading, 16)
                }
            }
        }
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
    }

    private func portSubtitle(_ port: UniFiDevicePort) -> String {
        var bits: [String] = []
        if let speed = port.speedMbps, speed > 0 {
            bits.append("\(speed) Mbps")
        }
        if port.up == true {
            bits.append(localizer.t.statusOnline)
        }
        if let poe = port.poePowerWatts, poe > 0 {
            bits.append(String(format: "PoE %.1fW", poe))
        }
        return bits.isEmpty ? "—" : bits.joined(separator: " • ")
    }

    private func rateString(_ value: Double?) -> String {
        guard let value, value > 0 else { return localizer.t.notAvailable }
        return "\(Formatters.formatBytes(value))/s"
    }

    private func statusPill(_ online: Bool) -> some View {
        Text(online ? localizer.t.statusOnline : localizer.t.unifiNeedsAttention)
            .font(.caption.bold())
            .foregroundStyle(online ? AppTheme.running : AppTheme.warning)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background((online ? AppTheme.running : AppTheme.warning).opacity(0.12), in: Capsule())
    }

    private func portBadge(_ port: UniFiDevicePort) -> some View {
        let tint: Color = port.isUplink == true ? color : (port.up == true ? AppTheme.running : AppTheme.textMuted)
        return ZStack {
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(tint.opacity(colorScheme == .dark ? 0.16 : 0.1))
            VStack(spacing: 2) {
                if let idx = port.idx {
                    Text("\(idx)")
                        .font(.caption.bold())
                        .foregroundStyle(tint)
                }
                if port.poePowerWatts != nil {
                    Image(systemName: "bolt.fill")
                        .font(.caption2.bold())
                        .foregroundStyle(tint)
                }
            }
        }
        .frame(width: 34, height: 34)
    }

    private func portMetric(_ label: String, value: String, tint: Color) -> some View {
        HStack(spacing: 5) {
            Circle()
                .fill(tint)
                .frame(width: 6, height: 6)
            Text("\(label) \(value)")
                .font(.caption2.bold())
                .foregroundStyle(tint)
        }
    }

    private func radioSubtitle(_ radio: UniFiDeviceRadio) -> String {
        var parts: [String] = []
        if let width = radio.channelWidth {
            parts.append("\(width) MHz")
        }
        if let clients = radio.clientCount {
            parts.append("\(clients) \(localizer.t.unifiClients.lowercased())")
        }
        return parts.isEmpty ? (radio.radio ?? "Radio") : parts.joined(separator: " • ")
    }

    private func radioDetailLine(_ radio: UniFiDeviceRadio) -> String {
        var parts: [String] = []
        if let channel = radio.channel {
            parts.append("Ch \(channel)")
        }
        if let mode = radio.txPowerMode?.nilIfEmpty {
            parts.append(mode.capitalized)
        }
        return parts.isEmpty ? "—" : parts.joined(separator: " • ")
    }

    private func satisfactionColor(_ value: Double) -> Color {
        switch value {
        case ..<70: return AppTheme.warning
        case ..<90: return Color(hex: "#F59E0B")
        default: return AppTheme.running
        }
    }

    private func detailChip(_ text: String, tint: Color, isFilled: Bool = true) -> some View {
        Text(text)
            .font(.caption.bold())
            .foregroundStyle(isFilled ? .white : tint)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(
                Capsule()
                    .fill(isFilled ? tint : tint.opacity(colorScheme == .dark ? 0.16 : 0.1))
            )
    }
}

struct UniFiSheetCloseButton: View {
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Image(systemName: "xmark")
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(.primary)
                .frame(width: 34, height: 34)
                .background(.ultraThinMaterial, in: Circle())
                .overlay(
                    Circle()
                        .strokeBorder(Color.primary.opacity(0.08), lineWidth: 0.5)
                )
        }
        .buttonStyle(.plain)
    }
}

private extension String {
    var nilIfEmpty: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}
