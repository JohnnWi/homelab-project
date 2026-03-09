import Foundation

enum PiHoleAuthMode: String, Codable, Equatable {
    case session
    case legacy
}

struct ServiceConnection: Codable, Identifiable, Equatable {
    var id: String { type.rawValue }
    let type: ServiceType
    var url: String
    var token: String
    var username: String?
    var apiKey: String?
    var piholePassword: String?
    var piholeAuthMode: PiHoleAuthMode?
    var fallbackUrl: String?

    init(
        type: ServiceType,
        url: String,
        token: String = "",
        username: String? = nil,
        apiKey: String? = nil,
        piholePassword: String? = nil,
        piholeAuthMode: PiHoleAuthMode? = nil,
        fallbackUrl: String? = nil
    ) {
        self.type = type
        self.url = url.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "/+$", with: "", options: .regularExpression)
        self.token = token
        self.username = username
        self.apiKey = apiKey
        self.piholePassword = piholePassword
        self.piholeAuthMode = piholeAuthMode
        self.fallbackUrl = fallbackUrl?.isEmpty == true ? nil : fallbackUrl
    }

    var piHoleStoredSecret: String? {
        if let piholePassword, !piholePassword.isEmpty {
            return piholePassword
        }
        if type == .pihole, let apiKey, !apiKey.isEmpty {
            return apiKey
        }
        return nil
    }

    func updatingToken(_ token: String, piholeAuthMode: PiHoleAuthMode? = nil) -> ServiceConnection {
        let migratedPiHolePassword = type == .pihole ? piHoleStoredSecret : piholePassword
        return ServiceConnection(
            type: type,
            url: url,
            token: token,
            username: username,
            apiKey: apiKey,
            piholePassword: migratedPiHolePassword,
            piholeAuthMode: piholeAuthMode ?? self.piholeAuthMode,
            fallbackUrl: fallbackUrl
        )
    }
}
