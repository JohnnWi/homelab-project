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
}
