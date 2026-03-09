import Foundation
import SwiftUI

/// Represents the loading state of any service data fetching operation.
/// Replaces disjointed `isLoading`, `error`, and `data` properties in ViewModels.
public enum LoadableState<T> {
    /// Initial state before any network request has been made.
    case idle
    
    /// A network request is currently in progress.
    case loading
    
    /// The network request completed successfully and data is available.
    case loaded(T)
    
    /// The network request failed with an APIError constraint.
    case error(APIError)
    
    /// The device is currently not connected to the internet.
    case offline
    
    // MARK: - Convenience Helpers
    
    /// Safely extracts the loaded data if the state is `.loaded`.
    public var value: T? {
        if case .loaded(let data) = self {
            return data
        }
        return nil
    }
    
    /// Returns true if currently loading.
    public var isLoading: Bool {
        if case .loading = self {
            return true
        }
        return false
    }
    
    /// Returns the active APIError if the state is `.error`.
    public var error: APIError? {
        if case .error(let err) = self {
            return err
        }
        return nil
    }
}

// MARK: - Equatable Support
// This is necessary for SwiftUI to efficiently re-render views observing state changes.
extension LoadableState: Equatable where T: Equatable {
    public static func == (lhs: LoadableState<T>, rhs: LoadableState<T>) -> Bool {
        switch (lhs, rhs) {
        case (.idle, .idle), (.loading, .loading), (.offline, .offline):
            return true
        case (.loaded(let lhsData), .loaded(let rhsData)):
            return lhsData == rhsData
        case (.error(let lhsErr), .error(let rhsErr)):
            // Fallback string matching for LocalizedError equality
            return lhsErr.localizedDescription == rhsErr.localizedDescription
        default:
            return false
        }
    }
}
