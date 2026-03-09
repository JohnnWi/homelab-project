import SwiftUI

// Maps to components/ServiceDashboardLayout.tsx
// Generic wrapper providing: loading state, error state, pull-to-refresh, offline banner.

struct ServiceDashboardLayout<T, Content: View>: View {
    let serviceType: ServiceType
    let state: LoadableState<T>
    let onRefresh: () async -> Void
    @ViewBuilder let content: () -> Content

    @Environment(ServicesStore.self) private var servicesStore
    @State private var refreshID = UUID()

    var isUnreachable: Bool {
        servicesStore.isReachable(serviceType) == false
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
                OfflineBanner(serviceName: serviceType.displayName) {
                    Task { await servicesStore.checkReachability(for: serviceType) }
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
}

// MARK: - Two-column grid helper

let twoColumnGrid = [GridItem(.flexible()), GridItem(.flexible())]

// MARK: - ServiceErrorView
// Consolidated here because ServiceErrorView.swift is not in the Xcode project.

struct ServiceErrorView: View {
    let error: APIError
    let retryAction: () async -> Void
    
    @Environment(Localizer.self) private var localizer
    @State private var isRetrying = false
    
    var body: some View {
        VStack(spacing: 20) {
            Image(systemName: iconName)
                .font(.system(size: 60))
                .foregroundStyle(AppTheme.warning)
                .symbolEffect(.bounce, value: isRetrying)
            
            VStack(spacing: 8) {
                Text(title)
                    .font(.title3.bold())
                    .foregroundStyle(.primary)
                
                Text(error.localizedDescription)
                    .font(.body)
                    .foregroundStyle(AppTheme.textSecondary)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal, 24)
            
            Button {
                Task {
                    isRetrying = true
                    await retryAction()
                    isRetrying = false
                }
            } label: {
                HStack(spacing: 8) {
                    if isRetrying {
                        ProgressView().tint(.white)
                    } else {
                        Image(systemName: "arrow.clockwise")
                    }
                    Text(localizer.t.retry)
                        .fontWeight(.semibold)
                }
                .frame(minWidth: 140)
                .padding(.vertical, 12)
                .background(AppTheme.primary, in: Capsule())
                .foregroundStyle(.white)
            }
            .disabled(isRetrying)
            .padding(.top, 10)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(24)
    }
    
    private var iconName: String {
        switch error {
        case .notConfigured: return "gearshape.fill"
        case .invalidURL: return "link.badge.plus"
        case .unauthorized: return "lock.slash.fill"
        case .bothURLsFailed: return "network.slash"
        default: return "exclamationmark.triangle.fill"
        }
    }
    
    private var title: String {
        switch error {
        case .notConfigured: return localizer.t.launcherNotConfigured
        case .unauthorized: return localizer.t.loginErrorCredentials
        case .bothURLsFailed: return localizer.t.loginErrorFailed
        default: return localizer.t.error
        }
    }
}
