import SwiftUI
import Darwin

// Maps to app/(tabs)/(home)/index.tsx — the launcher screen

struct HomeView: View {
    @Environment(ServicesStore.self) private var servicesStore
    @Environment(SettingsStore.self) private var settingsStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.scenePhase) private var scenePhase

    @State private var showLogin: ServiceType? = nil

    private let columns = [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)]

    private var hasServices: Bool {
        ServiceType.allCases.contains { !settingsStore.isServiceHidden($0) && servicesStore.isConnected($0) }
    }
    
    // Computed property to check if ANY active, un-hidden service is currently unreachable
    private var hasUnreachableService: Bool {
        ServiceType.allCases.contains { type in
            !settingsStore.isServiceHidden(type) &&
            servicesStore.isConnected(type) &&
            servicesStore.reachability[type] == false
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVStack(spacing: 0) {
                    headerSection
                    if hasServices {
                        if hasUnreachableService || servicesStore.isTailscaleConnected {
                            tailscaleSection
                        }
                        serviceGrid
                    }
                    DashboardSummary()
                    footerSection
                }
                .padding(.horizontal, 16)
            }
            .background(AppTheme.background)
            .navigationBarHidden(true)
            .sheet(item: $showLogin) { type in
                ServiceLoginView(serviceType: type)
            }
            .navigationDestination(for: ServiceType.self) { type in
                serviceDestination(for: type)
            }
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase == .active {
                    // Re-check reachability when app returns to foreground
                    Task { await servicesStore.checkAllReachability() }
                }
            }
        }
    }

    // MARK: - Header

    private var headerSection: some View {
        HStack {
            Text(localizer.t.launcherTitle)
                .font(.largeTitle)
                .fontWeight(.heavy)
                .foregroundStyle(.primary)

            Spacer()

            // Connected badge
            HStack(spacing: 5) {
                Image(systemName: "bolt.fill")
                    .font(.caption2)
                Text("\(servicesStore.connectedCount)/4")
                    .font(.subheadline.bold())
            }
            .foregroundStyle(AppTheme.accent)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .glassCard(cornerRadius: 20, tint: AppTheme.accent.opacity(0.15))
        }
        .padding(.top, 8)
        .padding(.bottom, 16)
    }

    // MARK: - Tailscale Section
    
    private var tailscaleSection: some View {
        Button {
            HapticManager.medium()
            if let url = URL(string: "tailscale://app") {
                UIApplication.shared.open(url, options: [:]) { success in
                    if !success {
                        if let appStoreUrl = URL(string: "https://apps.apple.com/app/tailscale/id1475387142") {
                            UIApplication.shared.open(appStoreUrl)
                        }
                    }
                }
            }
        } label: {
            HStack(spacing: 16) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(servicesStore.isTailscaleConnected ? AppTheme.running : Color.black)
                        .frame(width: 44, height: 44)
                    
                    Image(systemName: servicesStore.isTailscaleConnected ? "shield.checkered" : "network.badge.shield.half.filled")
                        .font(.title3)
                        .foregroundStyle(.white)
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text(servicesStore.isTailscaleConnected ? localizer.t.tailscaleConnected : localizer.t.tailscaleOpen)
                        .font(.subheadline.bold())
                        .foregroundStyle(.primary)

                    Text(servicesStore.isTailscaleConnected ? localizer.t.tailscaleSecure : localizer.t.tailscaleOpenDesc)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                }

                Spacer(minLength: 0)

                // Status indicator
                if servicesStore.isTailscaleConnected {
                    Image(systemName: "checkmark.seal.fill")
                        .foregroundStyle(AppTheme.running)
                        .font(.title3)
                } else {
                    HStack(spacing: 4) {
                        Text("VPN")
                            .font(.caption2.bold())
                            .foregroundStyle(AppTheme.textMuted)
                        Image(systemName: "chevron.right")
                            .font(.caption2)
                            .foregroundStyle(AppTheme.textMuted)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color.gray.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
            }
            .padding(12)
            .frame(maxWidth: .infinity)
            .glassCard(tint: servicesStore.isTailscaleConnected ? AppTheme.running.opacity(0.05) : nil)
        }
        .buttonStyle(.plain)
        .padding(.bottom, 16)
    }


    // MARK: - Service Grid

    private var serviceGrid: some View {
        GlassGroup(spacing: 14) {
            LazyVGrid(columns: columns, spacing: 14) {
                ForEach(ServiceType.allCases.filter { !settingsStore.isServiceHidden($0) }) { type in
                    let connected = servicesStore.isConnected(type)
                    if connected {
                        NavigationLink(value: type) {
                            ServiceCardContent(
                                type: type,
                                isConnected: true,
                                reachable: servicesStore.isReachable(type),
                                isPinging: servicesStore.isPinging(type),
                                t: localizer.t
                            ) {
                                Task { await servicesStore.checkReachability(for: type) }
                            }
                        }
                        .buttonStyle(.plain)
                    } else {
                        Button {
                            HapticManager.medium()
                            showLogin = type
                        } label: {
                            ServiceCardContent(
                                type: type,
                                isConnected: false,
                                reachable: nil,
                                isPinging: false,
                                t: localizer.t
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    // MARK: - Footer

    private var footerSection: some View {
        Text("\(localizer.t.launcherServices) • \(servicesStore.connectedCount) \(localizer.t.launcherConnected.lowercased())")
            .font(.caption)
            .foregroundStyle(AppTheme.textMuted)
            .frame(maxWidth: .infinity)
            .padding(.top, 28)
            .padding(.bottom, 40)
    }

    @ViewBuilder
    private func serviceDestination(for type: ServiceType) -> some View {
        switch type {
        case .portainer: PortainerDashboard()
        case .pihole:    PiHoleDashboard()
        case .beszel:    BeszelDashboard()
        case .gitea:     GiteaDashboard()
        }
    }
}

// MARK: - Service Card

private struct ServiceCardContent: View {
    let type: ServiceType
    let isConnected: Bool
    let reachable: Bool?
    let isPinging: Bool
    let t: Translations
    var onRefresh: (() -> Void)? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Icon row with refresh button
            HStack {
                Image(systemName: type.symbolName)
                    .font(.title2)
                    .foregroundStyle(type.colors.primary)
                    .frame(width: 56, height: 56)
                    .background(type.colors.bg, in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                Spacer()

                // Refresh button when unreachable
                if isConnected && reachable == false, let onRefresh {
                    Button {
                        onRefresh()
                    } label: {
                        Image(systemName: "arrow.clockwise")
                            .font(.subheadline.bold())
                            .foregroundStyle(type.colors.primary)
                            .frame(width: 36, height: 36)
                            .background(type.colors.primary.opacity(0.1), in: Circle())
                            .rotationEffect(.degrees(isPinging ? 360 : 0))
                            .animation(isPinging ? .linear(duration: 1).repeatForever(autoreverses: false) : .default, value: isPinging)
                    }
                    .buttonStyle(.plain)
                } else if isConnected && reachable == nil {
                    // Checking indicator
                    ProgressView()
                        .controlSize(.small)
                        .tint(type.colors.primary)
                }
            }
            .padding(.bottom, 10)

            // Name
            VStack(alignment: .leading, spacing: 3) {
                Text(serviceName)
                    .font(.body.bold())
                    .foregroundStyle(.primary)
            }

            Spacer(minLength: 8)

            // Status badge
            statusBadge
        }
        .frame(maxWidth: .infinity, minHeight: 140, alignment: .leading)
        .padding(14)
        .contentShape(Rectangle())
        .glassCard()
    }

    private var serviceName: String {
        switch type {
        case .portainer: return t.servicePortainer
        case .pihole:    return t.servicePihole
        case .beszel:    return t.serviceBeszel
        case .gitea:     return t.serviceGitea
        }
    }

    private var serviceDesc: String {
        switch type {
        case .portainer: return t.servicePortainerDesc
        case .pihole:    return t.servicePiholeDesc
        case .beszel:    return t.serviceBeszelDesc
        case .gitea:     return t.serviceGiteaDesc
        }
    }

    @ViewBuilder
    private var statusBadge: some View {
        if !isConnected {
            HStack(spacing: 5) {
                Text(t.launcherTapToConnect)
                    .font(.caption2.bold())
                    .foregroundStyle(AppTheme.textMuted)
                Image(systemName: "chevron.right")
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(.gray.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        } else if reachable == false {
            HStack(spacing: 5) {
                Circle().fill(AppTheme.warning).frame(width: 6, height: 6)
                Text(t.statusUnreachable)
                    .font(.caption2.bold())
                    .foregroundStyle(AppTheme.warning)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(AppTheme.warning.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        } else if reachable == nil {
            Text(t.statusVerifying)
                .font(.caption2.bold())
                .foregroundStyle(AppTheme.textMuted)
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(.gray.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        } else {
            HStack(spacing: 5) {
                Circle().fill(AppTheme.running).frame(width: 6, height: 6)
                Text(t.launcherConnected)
                    .font(.caption2.bold())
                    .foregroundStyle(AppTheme.running)
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(AppTheme.running.opacity(0.1), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
    }
}
