import Foundation

struct KomodoResourceSummary: Hashable, Sendable {
    let total: Int
    let running: Int
    let stopped: Int
    let healthy: Int
    let unhealthy: Int
    let unknown: Int

    static let empty = KomodoResourceSummary(total: 0, running: 0, stopped: 0, healthy: 0, unhealthy: 0, unknown: 0)
}

struct KomodoContainerSummary: Hashable, Sendable {
    let total: Int
    let running: Int
    let stopped: Int
    let unhealthy: Int
    let exited: Int
    let paused: Int
    let restarting: Int
    let unknown: Int

    static let empty = KomodoContainerSummary(total: 0, running: 0, stopped: 0, unhealthy: 0, exited: 0, paused: 0, restarting: 0, unknown: 0)
}

struct KomodoDashboardData: Sendable {
    let version: String?
    let servers: KomodoResourceSummary
    let deployments: KomodoResourceSummary
    let stacks: KomodoResourceSummary
    let containers: KomodoContainerSummary
    let generatedAt: Date

    var hasAnyData: Bool {
        servers.total > 0 || deployments.total > 0 || stacks.total > 0 || containers.total > 0
    }
}

struct KomodoStackItem: Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let status: String
    let server: String?
    let project: String?
    let updateAvailable: Bool
}

struct KomodoStackService: Identifiable, Hashable, Sendable {
    var id: String { name }
    let name: String
    let image: String?
    let containerName: String?
    let status: String
    let updateAvailable: Bool
}

struct KomodoStackDetail: Sendable {
    let stack: KomodoStackItem
    let services: [KomodoStackService]
}

enum KomodoStackAction: Sendable {
    case deploy
    case start
    case stop
    case restart
}

struct KomodoSummary: Sendable {
    let runningContainers: Int
    let totalContainers: Int
    let deployments: Int
    let servers: Int
}
