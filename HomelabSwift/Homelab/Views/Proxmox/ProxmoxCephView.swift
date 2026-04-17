import SwiftUI

struct ProxmoxCephView: View {
    let instanceId: UUID
    let nodeName: String

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var cephStatus: ProxmoxCephStatus?
    @State private var cephPools: [ProxmoxCephPool] = []
    @State private var cephOSDs: [ProxmoxCephOSDNode] = []
    @State private var state: LoadableState<Void> = .idle

    private let proxmoxColor = ServiceType.proxmox.colors.primary

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .proxmox,
            instanceId: instanceId,
            state: state,
            onRefresh: fetchData
        ) {
            if let status = cephStatus {
                healthSection(status)
                clusterInfoSection(status)
                if !cephOSDs.isEmpty {
                    osdSection
                }

                if let pgmap = status.pgmap {
                    usageSection(pgmap)
                    performanceSection(pgmap)
                }

                if !cephPools.isEmpty {
                    poolsSection
                }
            }
        }
        .navigationTitle("\(localizer.t.proxmoxCeph) - \(nodeName)")
        .navigationBarTitleDisplayMode(.inline)
        .task { await fetchData() }
    }

    // MARK: - Health

    private func healthSection(_ status: ProxmoxCephStatus) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxClusterHealth)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            HStack(spacing: 16) {
                let health = status.health
                let healthColor: Color = {
                    if health?.isHealthy == true { return AppTheme.running }
                    if health?.isWarning == true { return .orange }
                    return AppTheme.stopped
                }()

                VStack(spacing: 8) {
                    Image(systemName: health?.isHealthy == true ? "checkmark.shield.fill" : (health?.isWarning == true ? "exclamationmark.shield.fill" : "xmark.shield.fill"))
                        .font(.largeTitle)
                        .foregroundStyle(healthColor)
                        .symbolEffect(.pulse, isActive: health?.isWarning == true)

                    Text(health?.status ?? localizer.t.proxmoxUnknown.uppercased())
                        .font(.caption.bold())
                        .foregroundStyle(healthColor)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 18)
                .glassCard(tint: healthColor.opacity(0.06))

                // Monitors & OSDs summary
                VStack(spacing: 10) {
                    if let monCount = status.monmap?.num_mons {
                        HStack(spacing: 6) {
                            Image(systemName: "eye.fill")
                                .font(.caption)
                                .foregroundStyle(proxmoxColor)
                            Text("\(monCount) \(localizer.t.proxmoxMonsLabel)")
                                .font(.caption.bold())
                            Spacer()
                        }
                    }
                    if let osdmap = status.osdmap {
                        HStack(spacing: 6) {
                            Image(systemName: "internaldrive.fill")
                                .font(.caption)
                                .foregroundStyle(proxmoxColor)
                            Text("\(osdmap.num_up_osds ?? 0)/\(osdmap.num_osds ?? 0) \(localizer.t.proxmoxOsdLabel)s up")
                                .font(.caption.bold())
                            Spacer()
                        }
                        if osdmap.full == true {
                            HStack(spacing: 4) {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .font(.caption2)
                                    .foregroundStyle(.red)
                                Text(localizer.t.proxmoxFull)
                                    .font(.caption2.bold())
                                    .foregroundStyle(.red)
                                Spacer()
                            }
                        }
                        if osdmap.nearfull == true {
                            HStack(spacing: 4) {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .font(.caption2)
                                    .foregroundStyle(.orange)
                                Text(localizer.t.proxmoxNearFull)
                                    .font(.caption2.bold())
                                    .foregroundStyle(.orange)
                                Spacer()
                            }
                        }
                    }
                    if let quorum = status.quorum_names, !quorum.isEmpty {
                        HStack(spacing: 6) {
                            Image(systemName: "person.3.fill")
                                .font(.caption)
                                .foregroundStyle(AppTheme.textMuted)
                            Text(quorum.joined(separator: ", "))
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textSecondary)
                                .lineLimit(1)
                            Spacer()
                        }
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(14)
                .glassCard()
            }

            // Health checks
            if let checks = status.health?.checks, !checks.isEmpty {
                VStack(spacing: 0) {
                    ForEach(Array(checks.keys.sorted()), id: \.self) { key in
                        if let check = checks[key] {
                            HStack(spacing: 8) {
                                Circle()
                                    .fill(check.severity == "HEALTH_WARN" ? Color.orange : .red)
                                    .frame(width: 6, height: 6)
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(key)
                                        .font(.caption.bold())
                                    if let msg = check.summary?.message {
                                        Text(msg)
                                            .font(.caption2)
                                            .foregroundStyle(AppTheme.textMuted)
                                    }
                                }
                                Spacer()
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                        }
                    }
                }
                .glassCard()
            }
        }
    }

    // MARK: - Cluster Info

    private func clusterInfoSection(_ status: ProxmoxCephStatus) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxClusterInfo)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                statChip(label: localizer.t.proxmoxMon, value: "\(status.monmap?.num_mons ?? 0)", icon: "eye.fill", color: proxmoxColor)
                statChip(label: localizer.t.proxmoxOsd, value: "\(status.osdmap?.num_up_osds ?? 0)/\(status.osdmap?.num_osds ?? 0)", icon: "internaldrive.fill", color: .blue)
                statChip(label: localizer.t.proxmoxPlacementGroup, value: "\(status.pgmap?.num_pgs ?? 0)", icon: "square.grid.3x3.fill", color: .purple)
            }
        }
    }

    // MARK: - Usage

    private var osdSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("\(localizer.t.proxmoxOsdLabel)s (\(cephOSDs.count))")
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            ForEach(cephOSDs.filter { $0.type == "osd" }.sorted { $0.osdId < $1.osdId }) { osd in
                HStack(spacing: 10) {
                    Image(systemName: "internaldrive.fill")
                        .font(.subheadline)
                        .foregroundStyle(osd.status == "up" ? proxmoxColor : AppTheme.textMuted)
                        .frame(width: 30, height: 30)
                        .background((osd.status == "up" ? proxmoxColor : AppTheme.textMuted).opacity(0.1), in: RoundedRectangle(cornerRadius: 8, style: .continuous))

                    VStack(alignment: .leading, spacing: 2) {
                        Text(osd.name ?? "osd.\(osd.osdId)")
                            .font(.subheadline.bold())
                        HStack(spacing: 6) {
                            Text((osd.status ?? localizer.t.proxmoxUnknown).uppercased())
                                .font(.caption2.bold())
                                .foregroundStyle(osd.status == "up" ? AppTheme.running : AppTheme.stopped)
                            if let weight = osd.crush_weight {
                                Text("\(localizer.t.proxmoxCrushWeight) \(String(format: "%.2f", weight))")
                                    .font(.caption2)
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                            if let reweight = osd.reweight {
                                Text("\(localizer.t.proxmoxReweight) \(String(format: "%.2f", reweight))")
                                    .font(.caption2)
                                    .foregroundStyle(AppTheme.textMuted)
                            }
                        }
                    }

                    Spacer()
                }
                .padding(12)
                .glassCard()
            }
        }
    }

    private func usageSection(_ pgmap: ProxmoxCephPGMap) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxStorageUsage)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            let total = pgmap.bytes_total ?? 1
            let used = pgmap.bytes_used ?? 0
            let avail = pgmap.bytes_avail ?? 0
            let usedPct = total > 0 ? Double(used) / Double(total) * 100 : 0

            VStack(spacing: 14) {
                ZStack {
                    Circle()
                        .stroke(proxmoxColor.opacity(0.15), lineWidth: 10)
                    Circle()
                        .trim(from: 0, to: min(usedPct / 100, 1))
                        .stroke(usedPct > 85 ? Color.red : proxmoxColor, style: StrokeStyle(lineWidth: 10, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                        .animation(.easeOut(duration: 0.8), value: usedPct)
                    VStack(spacing: 2) {
                        Text(String(format: "%.1f%%", usedPct))
                            .font(.title2.bold())
                        Text(localizer.t.detailUsed.capitalized)
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                }
                .frame(width: 110, height: 110)

                HStack(spacing: 20) {
                    usageStat(label: localizer.t.detailUsed.capitalized, value: Formatters.formatBytes(Double(used)), color: proxmoxColor)
                    usageStat(label: localizer.t.proxmoxAvailable, value: Formatters.formatBytes(Double(avail)), color: AppTheme.running)
                    usageStat(label: localizer.t.proxmoxTotal, value: Formatters.formatBytes(Double(total)), color: AppTheme.textSecondary)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(18)
            .glassCard()
        }
    }

    // MARK: - Performance

    private func performanceSection(_ pgmap: ProxmoxCephPGMap) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(localizer.t.proxmoxPerformance)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                perfCard(label: localizer.t.proxmoxRead, value: Formatters.formatBytes(Double(pgmap.read_bytes_sec ?? 0)) + "/s", icon: "arrow.down.circle.fill", color: .green)
                perfCard(label: localizer.t.proxmoxWrite, value: Formatters.formatBytes(Double(pgmap.write_bytes_sec ?? 0)) + "/s", icon: "arrow.up.circle.fill", color: .orange)
                perfCard(label: localizer.t.proxmoxReadIops, value: "\(pgmap.read_op_per_sec ?? 0)", icon: "arrow.down.right.circle", color: .cyan)
                perfCard(label: localizer.t.proxmoxWriteIops, value: "\(pgmap.write_op_per_sec ?? 0)", icon: "arrow.up.right.circle", color: .pink)
            }
        }
    }

    // MARK: - Pools

    private var poolsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("\(localizer.t.proxmoxPools) (\(cephPools.count))")
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)

            ForEach(cephPools) { pool in
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Image(systemName: "tray.2.fill")
                            .font(.subheadline)
                            .foregroundStyle(proxmoxColor)
                        Text(pool.pool_name ?? "\(localizer.t.proxmoxPool) \(pool.pool ?? 0)")
                            .font(.subheadline.bold())
                        Spacer()
                        if let type = pool.type {
                            Text(type)
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(proxmoxColor)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(proxmoxColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 4, style: .continuous))
                        }
                    }

                    HStack(spacing: 12) {
                        miniInfo(label: localizer.t.proxmoxSizeLabel, value: "\(pool.size ?? 0)")
                        miniInfo(label: "\(localizer.t.proxmoxPgLabel)s", value: "\(pool.pg_num ?? 0)")
                        if let bytesUsed = pool.bytes_used {
                            miniInfo(label: localizer.t.detailUsed.capitalized, value: Formatters.formatBytes(Double(bytesUsed)))
                        }
                        if let pctUsed = pool.percent_used {
                            miniInfo(label: localizer.t.proxmoxStorageUsage, value: String(format: "%.1f%%", pctUsed * 100))
                        }
                    }

                    if let crushRule = pool.crush_rule_name {
                        HStack(spacing: 4) {
                            Text("\(localizer.t.proxmoxCrushRule):")
                                .font(.caption2)
                                .foregroundStyle(AppTheme.textMuted)
                            Text(crushRule)
                                .font(.caption2.bold())
                                .foregroundStyle(AppTheme.textSecondary)
                        }
                    }
                }
                .padding(12)
                .glassCard()
            }
        }
    }

    // MARK: - Helper Views

    private func statChip(label: String, value: String, icon: String, color: Color) -> some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.caption)
                .foregroundStyle(color)
            Text(value)
                .font(.title3.bold())
            Text(label)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .glassCard()
    }

    private func usageStat(label: String, value: String, color: Color) -> some View {
        VStack(spacing: 3) {
            Text(value)
                .font(.caption.bold())
                .foregroundStyle(color)
            Text(label)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
        }
    }

    private func perfCard(label: String, value: String, icon: String, color: Color) -> some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.body)
                .foregroundStyle(color)
            VStack(alignment: .leading, spacing: 2) {
                Text(value)
                    .font(.subheadline.bold())
                Text(label)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
            }
            Spacer()
        }
        .padding(12)
        .glassCard()
    }

    private func miniInfo(label: String, value: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.caption.bold())
            Text(label)
                .font(.system(size: 9))
                .foregroundStyle(AppTheme.textMuted)
        }
    }

    // MARK: - Data

    private func fetchData() async {
        state = .loading
        do {
            guard let client = await servicesStore.proxmoxClient(instanceId: instanceId) else {
                state = .error(.notConfigured)
                return
            }
            cephStatus = try await client.getCephStatus(node: nodeName)
            cephPools = (try? await client.getCephPools(node: nodeName)) ?? []
            cephOSDs = (try? await client.getCephOSDs(node: nodeName).nodes) ?? []
            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }
}
