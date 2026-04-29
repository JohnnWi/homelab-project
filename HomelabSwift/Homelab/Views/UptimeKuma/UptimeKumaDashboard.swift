import SwiftUI

struct UptimeKumaDashboard: View {
    let instanceId: UUID

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedInstanceId: UUID
    @State private var state: LoadableState<Void> = .idle
    @State private var dashboard: UptimeKumaDashboardData?

    private let kumaColor = ServiceType.uptimeKuma.colors.primary

    init(instanceId: UUID) {
        self.instanceId = instanceId
        _selectedInstanceId = State(initialValue: instanceId)
    }

    var body: some View {
        ServiceDashboardLayout(
            serviceType: .uptimeKuma,
            instanceId: selectedInstanceId,
            state: state,
            onRefresh: { await fetchDashboard(showLoading: false) }
        ) {
            instancePicker
            overviewCard
            statusCard
            monitorsSection
        }
        .navigationTitle(ServiceType.uptimeKuma.displayName)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    Task { await fetchDashboard(showLoading: false) }
                } label: {
                    Image(systemName: "arrow.clockwise")
                }
                .disabled(state.isLoading)
                .accessibilityLabel(localizer.t.refresh)
            }
        }
        .task(id: selectedInstanceId) {
            dashboard = nil
            await fetchDashboard(showLoading: true)
        }
    }

    private var instancePicker: some View {
        let instances = servicesStore.instances(for: .uptimeKuma)
        return Group {
            if instances.count > 1 {
                VStack(alignment: .leading, spacing: 12) {
                    Text(localizer.t.dashboardInstances)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.textMuted)
                        .textCase(.uppercase)

                    ForEach(instances) { instance in
                        Button {
                            HapticManager.light()
                            selectedInstanceId = instance.id
                            servicesStore.setPreferredInstance(id: instance.id, for: .uptimeKuma)
                        } label: {
                            HStack(spacing: 10) {
                                Circle()
                                    .fill(instance.id == selectedInstanceId ? kumaColor : AppTheme.textMuted.opacity(0.4))
                                    .frame(width: 10, height: 10)

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(instance.displayLabel)
                                        .font(.subheadline.weight(.semibold))
                                    Text(instance.url)
                                        .font(.caption)
                                        .foregroundStyle(AppTheme.textMuted)
                                        .lineLimit(1)
                                }
                                Spacer()
                            }
                            .padding(14)
                            .glassCard(tint: instance.id == selectedInstanceId ? kumaColor.opacity(0.1) : nil)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private var overviewCard: some View {
        let data = dashboard
        let percent = data?.healthyPercent ?? 0

        return VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .center, spacing: 12) {
                ServiceIconView(type: .uptimeKuma, size: 52)
                    .padding(6)
                    .background(
                        kumaColor.opacity(colorScheme == .dark ? 0.18 : 0.12),
                        in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                    )

                VStack(alignment: .leading, spacing: 4) {
                    Text(localizer.t.uptimeKumaMonitors)
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.textSecondary)
                    Text("\(data?.up ?? 0) / \(data?.total ?? 0)")
                        .font(.system(size: 32, weight: .bold))
                        .contentTransition(.numericText())
                    Text(percent.formatted(.percent.precision(.fractionLength(0))))
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                }

                Spacer()

                ZStack {
                    Circle()
                        .stroke(kumaColor.opacity(0.14), lineWidth: 8)
                    Circle()
                        .trim(from: 0, to: min(max(percent, 0), 1))
                        .stroke(kumaColor, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                    Text(percent.formatted(.percent.precision(.fractionLength(0))))
                        .font(.caption.bold())
                        .foregroundStyle(kumaColor)
                }
                .frame(width: 62, height: 62)
            }

            HStack(spacing: 10) {
                metricCell(title: localizer.t.uptimeKumaUp, value: "\(data?.up ?? 0)", tint: kumaColor)
                metricCell(title: localizer.t.uptimeKumaDown, value: "\(data?.down ?? 0)", tint: AppTheme.danger)
                metricCell(title: localizer.t.uptimeKumaAvgLatency, value: latencyText(data?.averageResponseTimeMs), tint: AppTheme.info)
            }
        }
        .padding(18)
        .glassCard(tint: kumaColor.opacity(colorScheme == .dark ? 0.1 : 0.055))
    }

    private var statusCard: some View {
        let data = dashboard
        let total = max(data?.total ?? 0, 1)
        let rows: [(String, Int, Color)] = [
            (localizer.t.uptimeKumaUp, data?.up ?? 0, kumaColor),
            (localizer.t.uptimeKumaDown, data?.down ?? 0, AppTheme.danger),
            (localizer.t.uptimeKumaPending, data?.pending ?? 0, AppTheme.warning),
            (localizer.t.uptimeKumaMaintenance, data?.maintenance ?? 0, AppTheme.info),
            (localizer.t.uptimeKumaUnknown, data?.unknown ?? 0, AppTheme.textMuted)
        ]

        return VStack(alignment: .leading, spacing: 14) {
            HStack {
                Label(localizer.t.uptimeKumaMonitors, systemImage: "waveform.path.ecg")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                    .textCase(.uppercase)
                Spacer()
                if let expiring = data?.expiringCertificates, expiring > 0 {
                    Label("\(expiring)", systemImage: "lock.trianglebadge.exclamationmark")
                        .font(.caption.bold())
                        .foregroundStyle(AppTheme.warning)
                        .accessibilityLabel(localizer.t.uptimeKumaCertExpiring)
                }
            }

            ForEach(rows, id: \.0) { row in
                statusRow(title: row.0, count: row.1, total: total, tint: row.2)
            }
        }
        .padding(16)
        .glassCard()
    }

    private var monitorsSection: some View {
        let monitors = dashboard?.monitors ?? []
        return VStack(alignment: .leading, spacing: 12) {
            HStack {
                Label(localizer.t.uptimeKumaMonitors, systemImage: "checkmark.seal.fill")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
                    .textCase(.uppercase)
                Spacer()
                Text("\(monitors.count)")
                    .font(.caption.bold())
                    .foregroundStyle(kumaColor)
            }

            if monitors.isEmpty {
                emptyState(title: localizer.t.uptimeKumaNoMonitors, icon: "heart.slash")
                    .glassCard()
            } else {
                ForEach(monitors) { monitor in
                    monitorCard(monitor)
                }
            }
        }
    }

    private func metricCell(title: String, value: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(value)
                .font(.title3.bold())
                .foregroundStyle(tint)
                .contentTransition(.numericText())
                .lineLimit(1)
                .minimumScaleFactor(0.72)
            Text(title)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
                .lineLimit(2)
                .minimumScaleFactor(0.72)
        }
        .frame(maxWidth: .infinity, minHeight: 72, alignment: .topLeading)
        .padding(.vertical, 6)
    }

    private func statusRow(title: String, count: Int, total: Int, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 7) {
            HStack {
                Text(title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(AppTheme.textSecondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
                Spacer()
                Text("\(count)")
                    .font(.subheadline.bold())
                    .foregroundStyle(tint)
            }

            GeometryReader { proxy in
                Capsule()
                    .fill(tint.opacity(colorScheme == .dark ? 0.16 : 0.12))
                    .overlay(alignment: .leading) {
                        Capsule()
                            .fill(tint)
                            .frame(width: proxy.size.width * CGFloat(Double(count) / Double(total)))
                    }
            }
            .frame(height: 8)
        }
    }

    private func monitorCard(_ monitor: UptimeKumaMonitor) -> some View {
        let tint = color(for: monitor.state)
        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: icon(for: monitor.state))
                    .font(.headline)
                    .foregroundStyle(tint)
                    .frame(width: 34, height: 34)
                    .background(tint.opacity(0.13), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

                VStack(alignment: .leading, spacing: 4) {
                    Text(monitor.name)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(2)
                        .minimumScaleFactor(0.78)
                    if let target = monitor.target {
                        Text(target)
                            .font(.caption)
                            .foregroundStyle(AppTheme.textMuted)
                            .lineLimit(1)
                    }
                }

                Spacer(minLength: 8)

                statusPill(text: label(for: monitor.state), tint: tint)
            }

            HStack(spacing: 10) {
                compactInfo(
                    title: localizer.t.uptimeKumaResponseTime,
                    value: latencyText(monitor.responseTimeMs),
                    tint: AppTheme.info
                )
                compactInfo(
                    title: localizer.t.uptimeKumaCertDays,
                    value: certText(monitor.certDaysRemaining),
                    tint: certColor(monitor.certDaysRemaining)
                )
            }
        }
        .padding(15)
        .glassCard(tint: tint.opacity(colorScheme == .dark ? 0.06 : 0.035))
    }

    private func statusPill(text: String, tint: Color) -> some View {
        Text(text)
            .font(.caption2.bold())
            .foregroundStyle(tint)
            .lineLimit(1)
            .minimumScaleFactor(0.72)
            .padding(.horizontal, 9)
            .padding(.vertical, 6)
            .background(tint.opacity(colorScheme == .dark ? 0.18 : 0.12), in: Capsule())
    }

    private func compactInfo(title: String, value: String, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(value)
                .font(.caption.bold())
                .foregroundStyle(tint)
                .lineLimit(1)
                .minimumScaleFactor(0.72)
            Text(title)
                .font(.caption2)
                .foregroundStyle(AppTheme.textMuted)
                .lineLimit(1)
                .minimumScaleFactor(0.72)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func emptyState(title: String, icon: String) -> some View {
        VStack(spacing: 10) {
            Image(systemName: icon)
                .font(.title2)
                .foregroundStyle(AppTheme.textMuted)
            Text(title)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(AppTheme.textSecondary)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
        .padding(24)
    }

    private func color(for state: UptimeKumaMonitorState) -> Color {
        switch state {
        case .up: return kumaColor
        case .down: return AppTheme.danger
        case .pending: return AppTheme.warning
        case .maintenance: return AppTheme.info
        case .unknown: return AppTheme.textMuted
        }
    }

    private func icon(for state: UptimeKumaMonitorState) -> String {
        switch state {
        case .up: return "checkmark.circle.fill"
        case .down: return "xmark.octagon.fill"
        case .pending: return "clock.fill"
        case .maintenance: return "wrench.and.screwdriver.fill"
        case .unknown: return "questionmark.circle.fill"
        }
    }

    private func label(for state: UptimeKumaMonitorState) -> String {
        switch state {
        case .up: return localizer.t.uptimeKumaUp
        case .down: return localizer.t.uptimeKumaDown
        case .pending: return localizer.t.uptimeKumaPending
        case .maintenance: return localizer.t.uptimeKumaMaintenance
        case .unknown: return localizer.t.uptimeKumaUnknown
        }
    }

    private func certColor(_ days: Int?) -> Color {
        guard let days else { return AppTheme.textMuted }
        if days <= 7 { return AppTheme.danger }
        if days <= 14 { return AppTheme.warning }
        return kumaColor
    }

    private func certText(_ days: Int?) -> String {
        guard let days else { return localizer.t.notAvailable }
        return "\(days)d"
    }

    private func latencyText(_ value: Double?) -> String {
        guard let value else { return localizer.t.notAvailable }
        return "\(Int(value.rounded())) ms"
    }

    private func fetchDashboard(showLoading: Bool) async {
        guard let client = await servicesStore.uptimeKumaClient(instanceId: selectedInstanceId) else {
            state = .error(.notConfigured)
            return
        }

        if showLoading { state = .loading }
        do {
            dashboard = try await client.getDashboard()
            servicesStore.markInstanceReachable(selectedInstanceId)
            state = .loaded(())
        } catch {
            state = .error(APIError.custom(error.localizedDescription))
        }
    }
}
