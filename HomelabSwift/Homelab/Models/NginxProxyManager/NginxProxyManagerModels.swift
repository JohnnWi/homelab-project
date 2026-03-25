import Foundation

struct NpmTokenResponse: Codable {
    let token: String?
    let expires: String?
    let result: NpmTokenInner?

    var resolvedToken: String {
        result?.token ?? token ?? ""
    }
}

struct NpmTokenInner: Codable {
    let token: String?
    let expires: String?
}

struct NpmHostReport: Codable {
    let proxy: Int
    let redirection: Int
    let stream: Int
    let dead: Int

    var total: Int { proxy + redirection + stream + dead }

    init(proxy: Int = 0, redirection: Int = 0, stream: Int = 0, dead: Int = 0) {
        self.proxy = proxy
        self.redirection = redirection
        self.stream = stream
        self.dead = dead
    }
}

struct NpmProxyHost: Codable, Identifiable {
    let id: Int
    let createdOn: String?
    let modifiedOn: String?
    let domainNames: [String]
    let forwardHost: String
    let forwardPort: Int
    let forwardScheme: String
    let certificateId: Int
    let sslForced: Int
    let cachingEnabled: Int
    let blockExploits: Int
    let allowWebsocketUpgrade: Int
    let http2Support: Int
    let hstsEnabled: Int
    let enabled: Int
    let meta: NpmProxyHostMeta?
    let accessListId: Int

    var isEnabled: Bool { enabled == 1 }
    var hasSSL: Bool { certificateId > 0 }
    var isOnline: Bool { meta?.nginxOnline == true }
    var forwardTarget: String { "\(forwardScheme)://\(forwardHost):\(forwardPort)" }
    var primaryDomain: String { domainNames.first ?? "" }

    enum CodingKeys: String, CodingKey {
        case id
        case createdOn = "created_on"
        case modifiedOn = "modified_on"
        case domainNames = "domain_names"
        case forwardHost = "forward_host"
        case forwardPort = "forward_port"
        case forwardScheme = "forward_scheme"
        case certificateId = "certificate_id"
        case sslForced = "ssl_forced"
        case cachingEnabled = "caching_enabled"
        case blockExploits = "block_exploits"
        case allowWebsocketUpgrade = "allow_websocket_upgrade"
        case http2Support = "http2_support"
        case hstsEnabled = "hsts_enabled"
        case enabled
        case meta
        case accessListId = "access_list_id"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(Int.self, forKey: .id)
        createdOn = try container.decodeIfPresent(String.self, forKey: .createdOn)
        modifiedOn = try container.decodeIfPresent(String.self, forKey: .modifiedOn)
        domainNames = (try? container.decode([String].self, forKey: .domainNames)) ?? []
        forwardHost = (try? container.decode(String.self, forKey: .forwardHost)) ?? ""
        forwardPort = (try? container.decode(Int.self, forKey: .forwardPort)) ?? 80
        forwardScheme = (try? container.decode(String.self, forKey: .forwardScheme)) ?? "http"
        certificateId = (try? container.decode(Int.self, forKey: .certificateId)) ?? 0
        sslForced = (try? container.decode(Int.self, forKey: .sslForced)) ?? 0
        cachingEnabled = (try? container.decode(Int.self, forKey: .cachingEnabled)) ?? 0
        blockExploits = (try? container.decode(Int.self, forKey: .blockExploits)) ?? 0
        allowWebsocketUpgrade = (try? container.decode(Int.self, forKey: .allowWebsocketUpgrade)) ?? 0
        http2Support = (try? container.decode(Int.self, forKey: .http2Support)) ?? 0
        hstsEnabled = (try? container.decode(Int.self, forKey: .hstsEnabled)) ?? 0
        enabled = (try? container.decode(Int.self, forKey: .enabled)) ?? 1
        meta = try? container.decodeIfPresent(NpmProxyHostMeta.self, forKey: .meta)
        accessListId = (try? container.decode(Int.self, forKey: .accessListId)) ?? 0
    }
}

struct NpmProxyHostMeta: Codable {
    let letsencryptAgree: Bool
    let dnsChallenge: Bool
    let nginxOnline: Bool
    let nginxErr: String?

    enum CodingKeys: String, CodingKey {
        case letsencryptAgree = "letsencrypt_agree"
        case dnsChallenge = "dns_challenge"
        case nginxOnline = "nginx_online"
        case nginxErr = "nginx_err"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        letsencryptAgree = (try? container.decode(Bool.self, forKey: .letsencryptAgree)) ?? false
        dnsChallenge = (try? container.decode(Bool.self, forKey: .dnsChallenge)) ?? false
        nginxOnline = (try? container.decode(Bool.self, forKey: .nginxOnline)) ?? false
        nginxErr = try? container.decodeIfPresent(String.self, forKey: .nginxErr)
    }
}

struct NpmHealthResponse: Codable {
    let status: String
    let version: NpmVersion?

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        status = try container.decodeIfPresent(String.self, forKey: .status) ?? ""
        // NPM returns version as object, NPMplus returns it as string
        if let obj = try? container.decode(NpmVersion.self, forKey: .version) {
            version = obj
        } else if let str = try? container.decode(String.self, forKey: .version) {
            version = NpmVersion.fromString(str)
        } else {
            version = nil
        }
    }

    private enum CodingKeys: String, CodingKey {
        case status, version
    }
}

struct NpmVersion: Codable {
    let major: Int
    let minor: Int
    let revision: Int

    var display: String { "\(major).\(minor).\(revision)" }

    static func fromString(_ s: String) -> NpmVersion {
        let parts = s.split(separator: ".").compactMap { Int($0) }
        return NpmVersion(
            major: parts.count > 0 ? parts[0] : 0,
            minor: parts.count > 1 ? parts[1] : 0,
            revision: parts.count > 2 ? parts[2] : 0
        )
    }
}

// MARK: - Redirection Hosts

struct NpmRedirectionHost: Codable, Identifiable {
    let id: Int
    let createdOn: String?
    let modifiedOn: String?
    let domainNames: [String]
    let forwardHttpCode: Int
    let forwardScheme: String
    let forwardDomainName: String
    let preservePath: Int
    let certificateId: Int
    let sslForced: Int
    let hstsEnabled: Int
    let hstsSubdomains: Int
    let http2Support: Int
    let blockExploits: Int
    let enabled: Int
    let meta: NpmProxyHostMeta?
    let accessListId: Int

    var isEnabled: Bool { enabled == 1 }
    var hasSSL: Bool { certificateId > 0 }
    var primaryDomain: String { domainNames.first ?? "" }

    enum CodingKeys: String, CodingKey {
        case id
        case createdOn = "created_on"
        case modifiedOn = "modified_on"
        case domainNames = "domain_names"
        case forwardHttpCode = "forward_http_code"
        case forwardScheme = "forward_scheme"
        case forwardDomainName = "forward_domain_name"
        case preservePath = "preserve_path"
        case certificateId = "certificate_id"
        case sslForced = "ssl_forced"
        case hstsEnabled = "hsts_enabled"
        case hstsSubdomains = "hsts_subdomains"
        case http2Support = "http2_support"
        case blockExploits = "block_exploits"
        case enabled
        case meta
        case accessListId = "access_list_id"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(Int.self, forKey: .id)
        createdOn = try container.decodeIfPresent(String.self, forKey: .createdOn)
        modifiedOn = try container.decodeIfPresent(String.self, forKey: .modifiedOn)
        domainNames = (try? container.decode([String].self, forKey: .domainNames)) ?? []
        forwardHttpCode = (try? container.decode(Int.self, forKey: .forwardHttpCode)) ?? 302
        forwardScheme = (try? container.decode(String.self, forKey: .forwardScheme)) ?? "http"
        forwardDomainName = (try? container.decode(String.self, forKey: .forwardDomainName)) ?? ""
        preservePath = (try? container.decode(Int.self, forKey: .preservePath)) ?? 0
        certificateId = (try? container.decode(Int.self, forKey: .certificateId)) ?? 0
        sslForced = (try? container.decode(Int.self, forKey: .sslForced)) ?? 0
        hstsEnabled = (try? container.decode(Int.self, forKey: .hstsEnabled)) ?? 0
        hstsSubdomains = (try? container.decode(Int.self, forKey: .hstsSubdomains)) ?? 0
        http2Support = (try? container.decode(Int.self, forKey: .http2Support)) ?? 0
        blockExploits = (try? container.decode(Int.self, forKey: .blockExploits)) ?? 0
        enabled = (try? container.decode(Int.self, forKey: .enabled)) ?? 1
        meta = try? container.decodeIfPresent(NpmProxyHostMeta.self, forKey: .meta)
        accessListId = (try? container.decode(Int.self, forKey: .accessListId)) ?? 0
    }
}

// MARK: - Streams

struct NpmStream: Codable, Identifiable {
    let id: Int
    let createdOn: String?
    let modifiedOn: String?
    let incomingPort: Int
    let forwardingHost: String
    let forwardingPort: Int
    let tcpForwarding: Int
    let udpForwarding: Int
    let enabled: Int
    let meta: NpmProxyHostMeta?

    var isEnabled: Bool { enabled == 1 }
    var isOnline: Bool { meta?.nginxOnline == true }

    enum CodingKeys: String, CodingKey {
        case id
        case createdOn = "created_on"
        case modifiedOn = "modified_on"
        case incomingPort = "incoming_port"
        case forwardingHost = "forwarding_host"
        case forwardingPort = "forwarding_port"
        case tcpForwarding = "tcp_forwarding"
        case udpForwarding = "udp_forwarding"
        case enabled
        case meta
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(Int.self, forKey: .id)
        createdOn = try container.decodeIfPresent(String.self, forKey: .createdOn)
        modifiedOn = try container.decodeIfPresent(String.self, forKey: .modifiedOn)
        incomingPort = (try? container.decode(Int.self, forKey: .incomingPort)) ?? 0
        forwardingHost = (try? container.decode(String.self, forKey: .forwardingHost)) ?? ""
        forwardingPort = (try? container.decode(Int.self, forKey: .forwardingPort)) ?? 0
        tcpForwarding = (try? container.decode(Int.self, forKey: .tcpForwarding)) ?? 1
        udpForwarding = (try? container.decode(Int.self, forKey: .udpForwarding)) ?? 0
        enabled = (try? container.decode(Int.self, forKey: .enabled)) ?? 1
        meta = try? container.decodeIfPresent(NpmProxyHostMeta.self, forKey: .meta)
    }
}

// MARK: - Dead Hosts (404 pages)

struct NpmDeadHost: Codable, Identifiable {
    let id: Int
    let createdOn: String?
    let modifiedOn: String?
    let domainNames: [String]
    let certificateId: Int
    let sslForced: Int
    let hstsEnabled: Int
    let hstsSubdomains: Int
    let http2Support: Int
    let enabled: Int
    let meta: NpmProxyHostMeta?

    var isEnabled: Bool { enabled == 1 }
    var hasSSL: Bool { certificateId > 0 }
    var primaryDomain: String { domainNames.first ?? "" }

    enum CodingKeys: String, CodingKey {
        case id
        case createdOn = "created_on"
        case modifiedOn = "modified_on"
        case domainNames = "domain_names"
        case certificateId = "certificate_id"
        case sslForced = "ssl_forced"
        case hstsEnabled = "hsts_enabled"
        case hstsSubdomains = "hsts_subdomains"
        case http2Support = "http2_support"
        case enabled
        case meta
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(Int.self, forKey: .id)
        createdOn = try container.decodeIfPresent(String.self, forKey: .createdOn)
        modifiedOn = try container.decodeIfPresent(String.self, forKey: .modifiedOn)
        domainNames = (try? container.decode([String].self, forKey: .domainNames)) ?? []
        certificateId = (try? container.decode(Int.self, forKey: .certificateId)) ?? 0
        sslForced = (try? container.decode(Int.self, forKey: .sslForced)) ?? 0
        hstsEnabled = (try? container.decode(Int.self, forKey: .hstsEnabled)) ?? 0
        hstsSubdomains = (try? container.decode(Int.self, forKey: .hstsSubdomains)) ?? 0
        http2Support = (try? container.decode(Int.self, forKey: .http2Support)) ?? 0
        enabled = (try? container.decode(Int.self, forKey: .enabled)) ?? 1
        meta = try? container.decodeIfPresent(NpmProxyHostMeta.self, forKey: .meta)
    }
}

// MARK: - Certificates

struct NpmCertificate: Codable, Identifiable {
    let id: Int
    let createdOn: String?
    let modifiedOn: String?
    let provider: String
    let niceName: String
    let domainNames: [String]
    let expiresOn: String?

    var isLetsEncrypt: Bool { provider == "letsencrypt" }
    var primaryDomain: String { domainNames.first ?? "" }

    var isExpired: Bool {
        guard let expiresOn else { return false }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: expiresOn) {
            return date < Date()
        }
        formatter.formatOptions = [.withInternetDateTime]
        if let date = formatter.date(from: expiresOn) {
            return date < Date()
        }
        return false
    }

    enum CodingKeys: String, CodingKey {
        case id
        case createdOn = "created_on"
        case modifiedOn = "modified_on"
        case provider
        case niceName = "nice_name"
        case domainNames = "domain_names"
        case expiresOn = "expires_on"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(Int.self, forKey: .id)
        createdOn = try container.decodeIfPresent(String.self, forKey: .createdOn)
        modifiedOn = try container.decodeIfPresent(String.self, forKey: .modifiedOn)
        provider = (try? container.decode(String.self, forKey: .provider)) ?? ""
        niceName = (try? container.decode(String.self, forKey: .niceName)) ?? ""
        domainNames = (try? container.decode([String].self, forKey: .domainNames)) ?? []
        expiresOn = try container.decodeIfPresent(String.self, forKey: .expiresOn)
    }
}

// MARK: - Access Lists

struct NpmAccessList: Codable, Identifiable {
    let id: Int
    let createdOn: String?
    let modifiedOn: String?
    let name: String
    let items: [NpmAccessListItem]?
    let clients: [NpmAccessListClient]?

    enum CodingKeys: String, CodingKey {
        case id
        case createdOn = "created_on"
        case modifiedOn = "modified_on"
        case name
        case items
        case clients
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(Int.self, forKey: .id)
        createdOn = try container.decodeIfPresent(String.self, forKey: .createdOn)
        modifiedOn = try container.decodeIfPresent(String.self, forKey: .modifiedOn)
        name = (try? container.decode(String.self, forKey: .name)) ?? ""
        items = try? container.decodeIfPresent([NpmAccessListItem].self, forKey: .items)
        clients = try? container.decodeIfPresent([NpmAccessListClient].self, forKey: .clients)
    }
}

// MARK: - Users / Audit Logs / Settings

struct NpmUser: Codable, Identifiable {
    let id: Int
    let createdOn: String?
    let modifiedOn: String?
    let isDisabled: Bool?
    let email: String?
    let name: String?
    let nickname: String?
    let avatar: String?
    let roles: [String]?

    enum CodingKeys: String, CodingKey {
        case id
        case createdOn = "created_on"
        case modifiedOn = "modified_on"
        case isDisabled = "is_disabled"
        case email
        case name
        case nickname
        case avatar
        case roles
    }
}

struct NpmAuditLog: Codable, Identifiable {
    let id: Int
    let createdOn: String?
    let modifiedOn: String?
    let userId: Int?
    let objectType: String?
    let objectId: Int?
    let action: String?
    let meta: JSONValue?
    let user: NpmUser?

    enum CodingKeys: String, CodingKey {
        case id
        case createdOn = "created_on"
        case modifiedOn = "modified_on"
        case userId = "user_id"
        case objectType = "object_type"
        case objectId = "object_id"
        case action
        case meta
        case user
    }
}

struct NpmSetting: Codable, Identifiable {
    let id: String
    let name: String
    let description: String?
    let value: JSONValue?
    let meta: [String: JSONValue]?
}

// MARK: - Request Bodies

struct NpmUserRequest: Encodable {
    let email: String
    let name: String?
    let nickname: String?
    let password: String?
    let roles: [String]
    let isDisabled: Bool

    enum CodingKeys: String, CodingKey {
        case email
        case name
        case nickname
        case password
        case roles
        case isDisabled = "is_disabled"
    }
}

struct NpmProxyHostRequest: Encodable {
    let domainNames: [String]
    let forwardScheme: String
    let forwardHost: String
    let forwardPort: Int
    let certificateId: Int
    let accessListId: Int
    let sslForced: Int
    let cachingEnabled: Int
    let blockExploits: Int
    let allowWebsocketUpgrade: Int
    let http2Support: Int
    let hstsEnabled: Int
    let hstsSubdomains: Int
    let enabled: Int
    let advancedConfig: String
    let meta: NpmProxyHostMeta?

    enum CodingKeys: String, CodingKey {
        case domainNames = "domain_names"
        case forwardScheme = "forward_scheme"
        case forwardHost = "forward_host"
        case forwardPort = "forward_port"
        case certificateId = "certificate_id"
        case accessListId = "access_list_id"
        case sslForced = "ssl_forced"
        case cachingEnabled = "caching_enabled"
        case blockExploits = "block_exploits"
        case allowWebsocketUpgrade = "allow_websocket_upgrade"
        case http2Support = "http2_support"
        case hstsEnabled = "hsts_enabled"
        case hstsSubdomains = "hsts_subdomains"
        case enabled
        case advancedConfig = "advanced_config"
        case meta
    }
}

struct NpmRedirectionHostRequest: Encodable {
    let domainNames: [String]
    let forwardHttpCode: Int
    let forwardScheme: String
    let forwardDomainName: String
    let preservePath: Int
    let certificateId: Int
    let accessListId: Int
    let sslForced: Int
    let hstsEnabled: Int
    let hstsSubdomains: Int
    let http2Support: Int
    let blockExploits: Int
    let enabled: Int

    enum CodingKeys: String, CodingKey {
        case domainNames = "domain_names"
        case forwardHttpCode = "forward_http_code"
        case forwardScheme = "forward_scheme"
        case forwardDomainName = "forward_domain_name"
        case preservePath = "preserve_path"
        case certificateId = "certificate_id"
        case accessListId = "access_list_id"
        case sslForced = "ssl_forced"
        case hstsEnabled = "hsts_enabled"
        case hstsSubdomains = "hsts_subdomains"
        case http2Support = "http2_support"
        case blockExploits = "block_exploits"
        case enabled
    }
}

struct NpmStreamRequest: Encodable {
    let incomingPort: Int
    let forwardingHost: String
    let forwardingPort: Int
    let tcpForwarding: Int
    let udpForwarding: Int
    let enabled: Int

    enum CodingKeys: String, CodingKey {
        case incomingPort = "incoming_port"
        case forwardingHost = "forwarding_host"
        case forwardingPort = "forwarding_port"
        case tcpForwarding = "tcp_forwarding"
        case udpForwarding = "udp_forwarding"
        case enabled
    }
}

struct NpmDeadHostRequest: Encodable {
    let domainNames: [String]
    let certificateId: Int
    let sslForced: Int
    let hstsEnabled: Int
    let hstsSubdomains: Int
    let http2Support: Int
    let enabled: Int

    enum CodingKeys: String, CodingKey {
        case domainNames = "domain_names"
        case certificateId = "certificate_id"
        case sslForced = "ssl_forced"
        case hstsEnabled = "hsts_enabled"
        case hstsSubdomains = "hsts_subdomains"
        case http2Support = "http2_support"
        case enabled
    }
}

struct NpmCertificateRequest: Encodable {
    let provider: String
    let niceName: String
    let domainNames: [String]
    let meta: NpmCertificateRequestMeta

    enum CodingKeys: String, CodingKey {
        case provider
        case niceName = "nice_name"
        case domainNames = "domain_names"
        case meta
    }
}

struct NpmCertificateRequestMeta: Encodable {
    let letsencryptAgree: Bool
    let letsencryptEmail: String
    let dnsChallenge: Bool

    enum CodingKeys: String, CodingKey {
        case letsencryptAgree = "letsencrypt_agree"
        case letsencryptEmail = "letsencrypt_email"
        case dnsChallenge = "dns_challenge"
    }
}

struct NpmAccessListItem: Codable {
    let username: String
    let password: String
}

struct NpmAccessListClient: Codable {
    let address: String
    let directive: String
}

struct NpmAccessListRequest: Encodable {
    let name: String
    let items: [NpmAccessListItem]
    let clients: [NpmAccessListClient]

    enum CodingKeys: String, CodingKey {
        case name
        case items
        case clients
    }
}

enum JSONValue: Codable, Equatable {
    case string(String)
    case number(Double)
    case bool(Bool)
    case object([String: JSONValue])
    case array([JSONValue])
    case null

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() {
            self = .null
        } else if let value = try? container.decode(Bool.self) {
            self = .bool(value)
        } else if let value = try? container.decode(Double.self) {
            self = .number(value)
        } else if let value = try? container.decode(String.self) {
            self = .string(value)
        } else if let value = try? container.decode([String: JSONValue].self) {
            self = .object(value)
        } else if let value = try? container.decode([JSONValue].self) {
            self = .array(value)
        } else {
            self = .null
        }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch self {
        case .string(let value):
            try container.encode(value)
        case .number(let value):
            try container.encode(value)
        case .bool(let value):
            try container.encode(value)
        case .object(let value):
            try container.encode(value)
        case .array(let value):
            try container.encode(value)
        case .null:
            try container.encodeNil()
        }
    }

    var displayString: String {
        switch self {
        case .string(let value):
            return value
        case .number(let value):
            if value.rounded(.towardZero) == value {
                return String(Int(value))
            }
            return String(value)
        case .bool(let value):
            return value ? "true" : "false"
        case .object, .array:
            if let data = try? JSONEncoder().encode(self),
               let text = String(data: data, encoding: .utf8) {
                return text
            }
            return ""
        case .null:
            return "null"
        }
    }
}
