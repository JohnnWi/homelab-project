import Foundation

struct DockmonHost: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let name: String
    let address: String?
    let status: String
    let isOnline: Bool

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: DockmonJSONKey.self)
        id = c.decodeLossyString(keys: ["id", "host_id", "hostId", "uuid"])
            ?? c.decodeLossyString(keys: ["name", "hostname"])
            ?? UUID().uuidString
        name = c.decodeLossyString(keys: ["name", "hostname", "label", "display_name"])
            ?? id
        address = c.decodeLossyString(keys: ["address", "url", "ip", "host", "endpoint"])
        status = c.decodeLossyString(keys: ["status", "state"]) ?? "unknown"
        let statusLower = status.lowercased()
        isOnline = c.decodeLossyBool(keys: ["online", "is_online", "active", "reachable"])
            ?? ["online", "active", "running", "healthy", "ok"].contains(statusLower)
    }

    init(id: String, name: String, address: String? = nil, status: String = "unknown", isOnline: Bool = false) {
        self.id = id
        self.name = name
        self.address = address
        self.status = status
        self.isOnline = isOnline
    }
}

struct DockmonContainer: Codable, Identifiable, Hashable, Sendable {
    let id: String
    let hostId: String?
    let name: String
    let image: String
    let state: String
    let status: String
    let autoRestart: Bool
    let updateAvailable: Bool
    let latestImage: String?
    let createdAt: String?
    let ports: [String]

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: DockmonJSONKey.self)
        id = c.decodeLossyString(keys: ["id", "ID", "container_id", "containerId", "Id"])
            ?? UUID().uuidString
        hostId = c.decodeLossyString(keys: ["host_id", "hostId", "host"])
        name = (c.decodeLossyString(keys: ["name", "Names", "container_name", "containerName"]) ?? id)
            .replacingOccurrences(of: "^/", with: "", options: .regularExpression)
        image = c.decodeLossyString(keys: ["image", "Image", "current_image", "currentImage"]) ?? "-"
        state = c.decodeLossyString(keys: ["state", "State"]) ?? "unknown"
        status = c.decodeLossyString(keys: ["status", "Status"]) ?? state
        autoRestart = c.decodeLossyBool(keys: ["auto_restart", "autoRestart", "restart_enabled", "restartEnabled", "watchdog"]) ?? false
        updateAvailable = c.decodeLossyBool(keys: ["update_available", "updateAvailable", "has_update", "hasUpdate", "outdated"]) ?? false
        latestImage = c.decodeLossyString(keys: ["latest_image", "latestImage", "available_image", "availableImage", "target_image", "targetImage"])
        createdAt = c.decodeLossyString(keys: ["created", "Created", "created_at", "createdAt"])
        ports = c.decodeStringArray(keys: ["ports", "Ports", "published_ports", "publishedPorts"])
    }

    var isRunning: Bool {
        let values = [state, status].map { $0.lowercased() }
        return values.contains { $0.contains("running") || $0 == "up" }
    }
}

struct DockmonHostsResponse: Decodable, Sendable {
    let hosts: [DockmonHost]

    init(from decoder: Decoder) throws {
        if let hosts = try? [DockmonHost](from: decoder) {
            self.hosts = hosts
            return
        }

        let c = try decoder.container(keyedBy: DockmonJSONKey.self)
        hosts = c.decodeArray(keys: ["hosts", "data", "items", "results"])
    }
}

struct DockmonContainersResponse: Decodable, Sendable {
    let containers: [DockmonContainer]

    init(from decoder: Decoder) throws {
        if let containers = try? [DockmonContainer](from: decoder) {
            self.containers = containers
            return
        }

        let c = try decoder.container(keyedBy: DockmonJSONKey.self)
        containers = c.decodeArray(keys: ["containers", "data", "items", "results"])
    }
}

struct DockmonDashboardData: Sendable {
    let hosts: [DockmonHost]
    let containersByHost: [String: [DockmonContainer]]

    var allContainers: [DockmonContainer] {
        if hosts.isEmpty {
            return containersByHost["__all__"] ?? []
        }
        return hosts.flatMap { containersByHost[$0.id] ?? [] }
    }

    var runningContainers: Int {
        allContainers.filter(\.isRunning).count
    }

    var totalContainers: Int {
        allContainers.count
    }

    var updateCount: Int {
        allContainers.filter(\.updateAvailable).count
    }

    var autoRestartCount: Int {
        allContainers.filter(\.autoRestart).count
    }
}

struct DockmonSummary: Sendable {
    let runningContainers: Int
    let totalContainers: Int
    let updateCount: Int
}

struct DockmonActionResponse: Decodable, Sendable {
    let success: Bool
    let message: String?

    init(success: Bool = true, message: String? = nil) {
        self.success = success
        self.message = message
    }

    init(from decoder: Decoder) throws {
        if let c = try? decoder.container(keyedBy: DockmonJSONKey.self) {
            success = c.decodeLossyBool(keys: ["success", "ok", "updated", "restarted"]) ?? true
            message = c.decodeLossyString(keys: ["message", "detail", "status"])
        } else {
            success = true
            message = nil
        }
    }
}

struct DockmonJSONKey: CodingKey {
    let stringValue: String
    let intValue: Int?

    init?(stringValue: String) {
        self.stringValue = stringValue
        self.intValue = nil
    }

    init?(intValue: Int) {
        self.stringValue = "\(intValue)"
        self.intValue = intValue
    }
}

private extension KeyedDecodingContainer where Key == DockmonJSONKey {
    func key(_ value: String) -> DockmonJSONKey {
        DockmonJSONKey(stringValue: value)!
    }

    func decodeLossyString(keys: [String]) -> String? {
        for candidate in keys {
            let key = key(candidate)
            if let value = try? decodeIfPresent(String.self, forKey: key) {
                return value
            }
            if let value = try? decodeIfPresent(Int.self, forKey: key) {
                return String(value)
            }
            if let value = try? decodeIfPresent(Double.self, forKey: key) {
                return String(value)
            }
            if let value = try? decodeIfPresent(Bool.self, forKey: key) {
                return value ? "true" : "false"
            }
            if let values = try? decodeIfPresent([String].self, forKey: key), let first = values.first {
                return first
            }
        }
        return nil
    }

    func decodeLossyBool(keys: [String]) -> Bool? {
        for candidate in keys {
            let key = key(candidate)
            if let value = try? decodeIfPresent(Bool.self, forKey: key) {
                return value
            }
            if let value = try? decodeIfPresent(Int.self, forKey: key) {
                return value != 0
            }
            if let value = try? decodeIfPresent(String.self, forKey: key) {
                switch value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
                case "1", "true", "yes", "on", "enabled", "available":
                    return true
                case "0", "false", "no", "off", "disabled", "none":
                    return false
                default:
                    continue
                }
            }
        }
        return nil
    }

    func decodeStringArray(keys: [String]) -> [String] {
        for candidate in keys {
            let key = key(candidate)
            if let values = try? decodeIfPresent([String].self, forKey: key) {
                return values
            }
            if let value = try? decodeIfPresent(String.self, forKey: key), !value.isEmpty {
                return [value]
            }
            if let values = try? decodeIfPresent([[String: String]].self, forKey: key) {
                return values.compactMap { dict in
                    if let ip = dict["IP"], let privatePort = dict["PrivatePort"] {
                        return "\(ip):\(privatePort)"
                    }
                    return dict["PublicPort"] ?? dict["PrivatePort"] ?? dict["port"]
                }
            }
        }
        return []
    }

    func decodeArray<T: Decodable>(keys: [String]) -> [T] {
        for candidate in keys {
            if let values = try? decodeIfPresent([T].self, forKey: key(candidate)) {
                return values
            }
        }
        return []
    }
}
