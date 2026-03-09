import Foundation
import os

/// A centralized structured logger for the Homelab application.
public struct AppLogger: Sendable {
    public static let shared = AppLogger()
    
    private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "com.homelab.app", category: "General")
    
    public func debug(_ message: String) {
        logger.debug("\(message, privacy: .public)")
    }
    
    public func info(_ message: String) {
        logger.info("\(message, privacy: .public)")
    }
    
    public func error(_ message: String) {
        logger.error("\(message, privacy: .public)")
    }
    
    public func error(_ error: Error) {
        logger.error("Error: \(error.localizedDescription, privacy: .public)")
    }

    /// Logs state transitions for ViewModels using LoadableState
    public func stateTransition<T>(service: String, state: LoadableState<T>) {
        let stateString: String
        switch state {
        case .idle: stateString = "Idle"
        case .loading: stateString = "Loading"
        case .loaded: stateString = "Loaded"
        case .error(let err): stateString = "Error (\(err.errorDescription ?? "unknown"))"
        case .offline: stateString = "Offline"
        }
        logger.debug("[\(service, privacy: .public)] State Transition -> \(stateString, privacy: .public)")
    }
}
