import Foundation
import os
import Observation

/// A centralized structured logger for the Homelab application.
public struct AppLogger: Sendable {
    public static let shared = AppLogger()
    
    private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "com.homelab.app", category: "General")
    
    public func debug(_ message: String, source: String = "App") {
        logger.debug("\(message, privacy: .public)")
        Task { @MainActor in
            LogStore.shared.add(message, level: .debug, source: source)
        }
    }
    
    public func info(_ message: String, source: String = "App") {
        logger.info("\(message, privacy: .public)")
        Task { @MainActor in
            LogStore.shared.add(message, level: .info, source: source)
        }
    }
    
    public func warn(_ message: String, source: String = "App") {
        logger.warning("\(message, privacy: .public)")
        Task { @MainActor in
            LogStore.shared.add(message, level: .warn, source: source)
        }
    }
    
    public func error(_ message: String, source: String = "App") {
        logger.error("\(message, privacy: .public)")
        Task { @MainActor in
            LogStore.shared.add(message, level: .warn, source: source)
        }
    }
    
    public func error(_ error: Error, source: String = "App") {
        let msg = "Error: \(error.localizedDescription)"
        logger.error("\(msg, privacy: .public)")
        Task { @MainActor in
            LogStore.shared.add(msg, level: .warn, source: source)
        }
    }

    public func network(_ message: String, source: String = "Network") {
        logger.info("[Network] \(message, privacy: .public)")
        Task { @MainActor in
            LogStore.shared.add(message, level: .network, source: source)
        }
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
        let msg = "[\(service)] State Transition -> \(stateString)"
        logger.debug("\(msg, privacy: .public)")
        Task { @MainActor in
            LogStore.shared.add(msg, level: .debug, source: service)
        }
    }
}

// MARK: - LogStore

@MainActor
@Observable
public final class LogStore {
    public static let shared = LogStore()
    
    public struct LogEntry: Identifiable {
        public let id: UUID
        public let timestamp: Date
        public let level: LogLevel
        public let source: String
        public let message: String

        public init(level: LogLevel, message: String, source: String = "App", timestamp: Date = Date(), id: UUID = UUID()) {
            self.id = id
            self.timestamp = timestamp
            self.level = level
            self.source = source
            self.message = message
        }
        
        private static let timeFormatter: DateFormatter = {
            let f = DateFormatter()
            f.dateFormat = "HH:mm:ss.SSS"
            return f
        }()

        public var formattedTime: String {
            Self.timeFormatter.string(from: timestamp)
        }
    }
    
    public enum LogLevel: String, CaseIterable {
        case debug = "DEBUG"
        case info = "INFO"
        case warn = "WARN"
        case network = "NET"
        
        public var icon: String {
            switch self {
            case .debug: return "ladybug.fill"
            case .info: return "info.circle.fill"
            case .warn: return "exclamationmark.circle.fill"
            case .network: return "network"
            }
        }
    }
    
    public private(set) var entries: [LogEntry] = []
    private let maxEntries = 500
    private var lastEmissionByKey: [String: Date] = [:]
    
    private init() {}
    
    public func add(_ message: String, level: LogLevel = .info, source: String = "App") {
        let now = Date()
        guard !shouldDrop(level: level, message: message, now: now) else { return }

        let entry = LogEntry(level: level, message: message, source: source, timestamp: now)
        entries.append(entry)
        
        if entries.count > maxEntries {
            entries.removeFirst()
        }
    }
    
    public func clear() {
        entries.removeAll()
    }
    
    public func export() -> String {
        entries.map { "[\($0.formattedTime)] [\($0.level.rawValue)] [\($0.source)] \($0.message)" }
            .joined(separator: "\n")
    }

    private func shouldDrop(level: LogLevel, message: String, now: Date) -> Bool {
        let minInterval: TimeInterval
        switch level {
        case .network:
            minInterval = 2.0
        case .debug:
            minInterval = 0.8
        case .info, .warn:
            return false
        }

        let key = "\(level.rawValue)|\(message)"
        if let last = lastEmissionByKey[key], now.timeIntervalSince(last) < minInterval {
            return true
        }

        lastEmissionByKey[key] = now

        if lastEmissionByKey.count > 1200 {
            let threshold = now.addingTimeInterval(-120)
            lastEmissionByKey = lastEmissionByKey.filter { $0.value >= threshold }
        }
        return false
    }
}
