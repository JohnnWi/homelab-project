import Foundation

public enum PiholeDomainListType: String, Codable, Sendable {
    case allow
    case deny
}

public struct PiholeDomain: Codable, Identifiable, Sendable {
    public let id: Int
    public let domain: String
    public let kind: String // exact or regex
    public let list: String? // Optional in v6 API based on the endpoint, but usually present

    public var type: PiholeDomainListType? {
        PiholeDomainListType(rawValue: list ?? "")
    }

    public init(id: Int, domain: String, kind: String, list: String?) {
        self.id = id
        self.domain = domain
        self.kind = kind
        self.list = list
    }

    enum CodingKeys: String, CodingKey {
        case id
        case domain
        case kind
        case list
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let domain = try container.decode(String.self, forKey: .domain)
        let kind = try container.decodeIfPresent(String.self, forKey: .kind) ?? "exact"
        let list = try container.decodeIfPresent(String.self, forKey: .list)

        if let decodedId = try container.decodeIfPresent(Int.self, forKey: .id) {
            self.id = decodedId
        } else if let stringId = try container.decodeIfPresent(String.self, forKey: .id),
                  let parsedId = Int(stringId) {
            self.id = parsedId
        } else {
            self.id = Self.syntheticID(domain: domain, kind: kind, list: list)
        }

        self.domain = domain
        self.kind = kind
        self.list = list
    }

    private static func syntheticID(domain: String, kind: String, list: String?) -> Int {
        var hash = 5381
        let raw = "\(list ?? "unknown")|\(kind)|\(domain)"
        for scalar in raw.unicodeScalars {
            hash = ((hash << 5) &+ hash) &+ Int(scalar.value)
        }
        return abs(hash)
    }
}

struct PiholeDomainListResponse: Codable, Sendable {
    let domains: [PiholeDomain]

    enum CodingKeys: String, CodingKey {
        case domains
        case allow
        case deny
        case whitelist
        case blacklist
        case regex_whitelist
        case regex_blacklist
    }

    private struct LegacyDomainEntry: Decodable {
        let domain: String?
    }

    init(domains: [PiholeDomain]) {
        self.domains = domains
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)

        if let v6Domains = try container.decodeIfPresent([PiholeDomain].self, forKey: .domains) {
            domains = v6Domains
            return
        }

        var normalized: [PiholeDomain] = []
        var nextID = 1

        func append(_ values: [String], type: PiholeDomainListType, kind: String) {
            for value in values where !value.isEmpty {
                normalized.append(
                    PiholeDomain(
                        id: nextID,
                        domain: value,
                        kind: kind,
                        list: type.rawValue
                    )
                )
                nextID += 1
            }
        }

        append(Self.decodeLegacyList(for: .allow, from: container), type: .allow, kind: "exact")
        append(Self.decodeLegacyList(for: .deny, from: container), type: .deny, kind: "exact")
        append(Self.decodeLegacyList(for: .whitelist, from: container), type: .allow, kind: "exact")
        append(Self.decodeLegacyList(for: .blacklist, from: container), type: .deny, kind: "exact")
        append(Self.decodeLegacyList(for: .regex_whitelist, from: container), type: .allow, kind: "regex")
        append(Self.decodeLegacyList(for: .regex_blacklist, from: container), type: .deny, kind: "regex")

        domains = normalized
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(domains, forKey: .domains)
    }

    private static func decodeLegacyList(
        for key: CodingKeys,
        from container: KeyedDecodingContainer<CodingKeys>
    ) -> [String] {
        if let plain = try? container.decode([String].self, forKey: key) {
            return plain
        }

        if let objects = try? container.decode([LegacyDomainEntry].self, forKey: key) {
            return objects.compactMap(\.domain)
        }

        return []
    }
}
