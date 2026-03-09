import SwiftUI

// Maps to app/beszel/index.tsx — system list with overview + metrics

struct BeszelDashboard: View {
    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer

    @State private var systems: [BeszelSystem] = []
    @State private var state: LoadableState<Void> = .idle

    private let beszelColor = Color(hex: "#0EA5E9")
    private let memoryColor = Color(hex: "#8B5CF6")

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .beszel,
            state: state,
            onRefresh: fetchSystems
        ) {
            overviewCard

            refreshHint

            if systems.isEmpty && !state.isLoading {
                emptyState
            } else {
                ForEach(systems) { system in
                    NavigationLink(value: system.id) {
                        SystemCard(
                            system: system,
                            beszelColor: beszelColor,
                            memoryColor: memoryColor,
                            t: localizer.t
                        )
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .navigationTitle(localizer.t.serviceBeszel)
        .navigationDestination(for: String.self) { systemId in
            BeszelSystemDetail(systemId: systemId)
        }
        .task { await fetchSystems() }
    }

    // MARK: - Overview Card

    private var overviewCard: some View {
        let onlineCount = systems.filter(\.isOnline).count

        return HStack(spacing: 14) {
            Image(systemName: "server.rack")
                .font(.title3)
                .foregroundStyle(beszelColor)
                .frame(width: 48, height: 48)
                .background(beszelColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 14, style: .continuous))

            VStack(alignment: .leading, spacing: 2) {
                Text(localizer.t.beszelSystems)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.textSecondary)
                Text("\(systems.count)")
                    .font(.system(size: 28, weight: .bold))
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 4) {
                HStack(spacing: 5) {
                    Circle().fill(AppTheme.running).frame(width: 7, height: 7)
                    Text("\(onlineCount) \(localizer.t.beszelUp)")
                        .font(.caption2.bold())
                        .foregroundStyle(AppTheme.running)
                }
                HStack(spacing: 5) {
                    Circle().fill(AppTheme.stopped).frame(width: 7, height: 7)
                    Text("\(systems.count - onlineCount) \(localizer.t.beszelDown)")
                        .font(.caption2.bold())
                        .foregroundStyle(AppTheme.stopped)
                }
            }
        }
        .padding(18)
        .glassCard()
    }

    // MARK: - Refresh Hint

    private var refreshHint: some View {
        HStack(spacing: 6) {
            Image(systemName: "arrow.clockwise")
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
            Text(localizer.t.beszelRefreshRate)
                .font(.caption)
                .foregroundStyle(AppTheme.textMuted)
        }
        .padding(.horizontal, 4)
    }

    // MARK: - Empty State

    private var emptyState: some View {
        VStack(spacing: 12) {
            Image(systemName: "server.rack")
                .font(.system(size: 48))
                .foregroundStyle(AppTheme.textMuted)
            Text(localizer.t.beszelNoSystems)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 60)
    }

    // MARK: - Fetch

    private func fetchSystems() async {
        state = .loading
        do {
            let response = try await servicesStore.beszelClient.getSystems()
            systems = response.items
            state = .loaded(())
        } catch let apiError as APIError {
            state = .error(apiError)
        } catch {
            state = .error(.custom(error.localizedDescription))
        }
    }
}

// MARK: - System Card

private struct SystemCard: View {
    let system: BeszelSystem
    let beszelColor: Color
    let memoryColor: Color
    let t: Translations

    private var info: BeszelSystemInfo { system.info ?? .empty }
    private var isUp: Bool { system.isOnline }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Header
            HStack {
                HStack(spacing: 8) {
                    Circle()
                        .fill(isUp ? AppTheme.running : AppTheme.stopped)
                        .frame(width: 10, height: 10)
                    Text(system.name)
                        .font(.body.bold())
                        .foregroundStyle(.primary)
                }

                Spacer()

                HStack(spacing: 6) {
                    statusBadge
                    Image(systemName: "chevron.right")
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                }
            }
            .padding(.bottom, 14)

            if isUp {
                // Metrics: CPU, Memory, Disk
                VStack(spacing: 12) {
                    MetricBar(
                        icon: "cpu",
                        iconColor: beszelColor,
                        label: t.beszelCpu,
                        value: info.cpuValue,
                        barColor: beszelColor
                    )
                    MetricBar(
                        icon: "memorychip",
                        iconColor: memoryColor,
                        label: t.beszelMemory,
                        value: info.mpValue,
                        barColor: memoryColor
                    )
                    MetricBar(
                        icon: "internaldrive",
                        iconColor: AppTheme.warning,
                        label: t.beszelDisk,
                        value: info.dpValue,
                        barColor: AppTheme.warning
                    )
                }
                .padding(.bottom, 14)

                // Footer: uptime
                HStack(spacing: 16) {
                    HStack(spacing: 4) {
                        Image(systemName: "clock")
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                        Text(formatUptimeHours(info.uValue))
                            .font(.caption2.weight(.medium))
                            .foregroundStyle(AppTheme.textMuted)
                    }
                    Spacer()
                }
                .padding(.top, 12)
                .overlay(alignment: .top) {
                    Divider()
                }
                .padding(.bottom, 8)
            }

            // Host
            Text("\(system.host):\(system.port)")
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
        }
        .padding(16)
        .glassCard()
    }

    @ViewBuilder
    private var statusBadge: some View {
        HStack(spacing: 4) {
            Image(systemName: isUp ? "wifi" : "wifi.slash")
                .font(.caption2)
            Text(isUp ? t.beszelUp : t.beszelDown)
                .font(.caption2.bold())
        }
        .foregroundStyle(isUp ? AppTheme.running : AppTheme.stopped)
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .background(
            (isUp ? AppTheme.running : AppTheme.stopped).opacity(0.1),
            in: RoundedRectangle(cornerRadius: 12, style: .continuous)
        )
    }
}

// MARK: - Metric Bar

private struct MetricBar: View {
    let icon: String
    let iconColor: Color
    let label: String
    let value: Double
    let barColor: Color

    var body: some View {
        VStack(spacing: 6) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.caption)
                    .foregroundStyle(iconColor)
                Text(label)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textSecondary)
                Spacer()
                Text(Formatters.formatPercent(value))
                    .font(.caption.bold())
                    .foregroundStyle(.primary)
            }

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(.white.opacity(0.1))
                        .frame(height: 6)
                    RoundedRectangle(cornerRadius: 3)
                        .fill(barColor.gradient)
                        .frame(width: geo.size.width * CGFloat(min(value, 100)) / 100, height: 6)
                        .animation(.spring(response: 0.6, dampingFraction: 0.8), value: value)
                }
            }
            .frame(height: 6)
        }
    }
}

// MARK: - Info Chip

private struct InfoChip: View {
    let icon: String
    let iconColor: Color
    let text: String

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: 10))
                .foregroundStyle(iconColor)
            Text(text)
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(AppTheme.textSecondary)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(.gray.opacity(0.1), in: RoundedRectangle(cornerRadius: 8, style: .continuous))
    }
}

// MARK: - Formatters

private func formatBeszelSize(_ val: Double, compact: Bool = false) -> String {
    if val == 0 { return "0" }
    
    // Fallback per valori già in bytes (es. traffico o vecchie API)
    if val > 10000 {
        let formatted = Formatters.formatBytes(val)
        return compact ? formatted.replacingOccurrences(of: "B", with: "").replacingOccurrences(of: " ", with: "") : formatted
    }
    
    if val < 0.01 { return compact ? "<0.01" : "< 0.01 GB" }
    if val < 1 {
        let mb = String(format: "%.0f", val * 1024)
        return compact ? mb : "\(mb) MB"
    }
    let gb = String(format: "%.1f", val)
    return compact ? gb : "\(gb) GB"
}

private func formatNetRate(_ val: Double) -> String {
    if val == 0 { return "0 B/s" }
    return "\(Formatters.formatBytes(val))/s"
}

private func formatUptimeHours(_ seconds: Double) -> String {
    let days = Int(seconds / 86400)
    let hours = Int(seconds.truncatingRemainder(dividingBy: 86400) / 3600)
    if days > 0 { return "\(days)d \(hours)h" }
    let minutes = Int(seconds.truncatingRemainder(dividingBy: 3600) / 60)
    if hours > 0 { return "\(hours)h \(minutes)m" }
    return "\(minutes)m"
}
