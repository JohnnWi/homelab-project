import SwiftUI

/// A reusable component that displays an error message and a retry button
/// when a LoadableState reaches the `.error` or `.offline` state.
public struct ServiceErrorView: View {
    public let error: APIError
    public let retryAction: () async -> Void
    
    public init(error: APIError, retryAction: @escaping () async -> Void) {
        self.error = error
        self.retryAction = retryAction
    }
    
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
                        ProgressView()
                            .tint(.white)
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
    
    // MARK: - Computed Properties
    
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
