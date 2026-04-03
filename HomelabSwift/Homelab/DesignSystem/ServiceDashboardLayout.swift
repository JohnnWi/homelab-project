import SwiftUI
import UIKit

// Maps to components/ServiceDashboardLayout.tsx
// Generic wrapper providing: loading state, error state, pull-to-refresh, offline banner.

struct ServiceDashboardLayout<T, Content: View>: View {
    let serviceType: ServiceType
    let instanceId: UUID
    let state: LoadableState<T>
    let onRefresh: () async -> Void
    let showTailscaleQuickAccess: Bool
    @ViewBuilder let content: () -> Content

    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @State private var refreshID = UUID()

    var isUnreachable: Bool {
        servicesStore.reachability(for: instanceId) == false
    }

    private var suppressTailscaleQuickAccess: Bool {
        !servicesStore.instances(for: .pangolin).isEmpty && servicesStore.isTailscaleConnected
    }

    private var stateChangeToken: String {
        switch state {
        case .idle:
            return "idle"
        case .loading:
            return "loading"
        case .loaded:
            return "loaded"
        case .error(let apiError):
            return "error:\(apiError.localizedDescription)"
        case .offline:
            return "offline"
        }
    }

    init(
        serviceType: ServiceType,
        instanceId: UUID,
        state: LoadableState<T>,
        showTailscaleQuickAccess: Bool = false,
        onRefresh: @escaping () async -> Void,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.serviceType = serviceType
        self.instanceId = instanceId
        self.state = state
        self.onRefresh = onRefresh
        self.showTailscaleQuickAccess = showTailscaleQuickAccess
        self.content = content
    }

    var body: some View {
        ZStack(alignment: .top) {
            switch state {
            case .idle, .loading:
                loadingView
            case .error(let apiError):
                ServiceErrorView(error: apiError, retryAction: onRefresh)
            case .offline:
                ServiceErrorView(error: .networkError(NSError(domain: "Network", code: -1009)), retryAction: onRefresh)
            case .loaded:
                ScrollView {
                    LazyVStack(spacing: AppTheme.gridSpacing) {
                        content()
                    }
                    .padding(AppTheme.padding)
                }
                .refreshable {
                    await onRefresh()
                }
            }

            if isUnreachable && !state.isLoading {
                VStack(spacing: 8) {
                    OfflineBanner(serviceName: serviceType.displayName) {
                        Task { await servicesStore.checkReachability(for: instanceId) }
                    }
                    if showTailscaleQuickAccess && !suppressTailscaleQuickAccess {
                        tailscaleQuickAccess
                    }
                }
            }
        }
        .onChange(of: stateChangeToken) { _, _ in
            AppLogger.shared.stateTransition(service: serviceType.displayName, state: state)
        }
    }

    // MARK: - Loading

    @ViewBuilder
    private var loadingView: some View {
        ScrollView {
            LazyVStack(spacing: AppTheme.gridSpacing) {
                LazyVGrid(
                    columns: [GridItem(.flexible()), GridItem(.flexible())],
                    spacing: AppTheme.gridSpacing
                ) {
                    ForEach(0..<4) { _ in SkeletonStatCard() }
                }
                ForEach(0..<3) { _ in SkeletonRow() }
            }
            .padding(AppTheme.padding)
        }
    }

    // old errorView removed (now using ServiceErrorView directly)

    private var tailscaleQuickAccess: some View {
        Button {
            HapticManager.medium()
            if let url = URL(string: "tailscale://app") {
                UIApplication.shared.open(url, options: [:]) { success in
                    if !success, let appStoreUrl = URL(string: "https://apps.apple.com/app/tailscale/id1475387142") {
                        UIApplication.shared.open(appStoreUrl)
                    }
                }
            }
        } label: {
            HStack(spacing: 12) {
                Image(systemName: servicesStore.isTailscaleConnected ? "shield.checkered" : "network.badge.shield.half.filled")
                    .font(.subheadline.bold())
                    .foregroundStyle(servicesStore.isTailscaleConnected ? AppTheme.running : AppTheme.info)

                VStack(alignment: .leading, spacing: 2) {
                    Text(servicesStore.isTailscaleConnected ? localizer.t.tailscaleConnected : localizer.t.tailscaleOpen)
                        .font(.subheadline.bold())
                        .foregroundStyle(.primary)
                    Text(servicesStore.isTailscaleConnected ? localizer.t.tailscaleSecure : localizer.t.tailscaleOpenDesc)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                        .lineLimit(2)
                }

                Spacer()

                HStack(spacing: 4) {
                    Text(localizer.t.tailscaleBadge)
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
            .padding(AppTheme.innerPadding)
            .modifier(GlassEffectModifier(
                cornerRadius: AppTheme.smallRadius,
                tint: servicesStore.isTailscaleConnected ? AppTheme.running.opacity(0.16) : AppTheme.info.opacity(0.12),
                interactive: false
            ))
            .padding(.horizontal, AppTheme.padding)
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Two-column grid helper

let twoColumnGrid = [GridItem(.flexible()), GridItem(.flexible())]
