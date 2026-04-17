import Foundation
import SwiftUI

// MARK: - Icon Type

enum BookmarkIconType: String, Codable, Equatable {
    case favicon
    case systemSymbol
    case selfhst
}

// MARK: - Category Color Palette

enum CategoryColor: String, CaseIterable {
    case blue = "#007AFF"
    case purple = "#AF52DE"
    case pink = "#FF2D55"
    case red = "#FF3B30"
    case orange = "#FF9500"
    case yellow = "#FFCC00"
    case green = "#34C759"
    case teal = "#5AC8FA"
    case indigo = "#5856D6"
    case gray = "#8E8E93"

    var color: Color {
        Color(hex: rawValue)
    }

    static var defaultColor: CategoryColor { .blue }
}


// MARK: - Category Model

struct BookmarkCategory: Identifiable, Codable, Hashable {
    var id: UUID = UUID()
    var name: String
    var icon: String? // SF Symbol name
    var color: String? // Hex color code
    var sortOrder: Int

    init(id: UUID = UUID(), name: String, icon: String? = nil, color: String? = nil, sortOrder: Int = 0) {
        self.id = id
        self.name = name
        self.icon = icon
        self.color = color
        self.sortOrder = sortOrder
    }

    var categoryColor: Color {
        if let hex = color {
            return Color(hex: hex)
        }
        return CategoryColor.defaultColor.color
    }
}

// MARK: - Bookmark Model

struct Bookmark: Identifiable, Codable, Hashable {
    var id: UUID = UUID()
    var categoryId: UUID
    var title: String
    var description: String?
    var url: String
    var iconType: BookmarkIconType
    var iconValue: String // URL or SF Symbol name
    var tags: [String]
    var sortOrder: Int

    init(id: UUID = UUID(), categoryId: UUID, title: String, description: String? = nil, url: String, iconType: BookmarkIconType = .favicon, iconValue: String = "", tags: [String] = [], sortOrder: Int = 0) {
        self.id = id
        self.categoryId = categoryId
        self.title = title
        self.description = description
        self.url = url
        self.iconType = iconType
        self.iconValue = iconValue
        self.tags = tags
        self.sortOrder = sortOrder
    }

    var domain: String? {
        guard let normalizedUrl, let host = normalizedUrl.host else { return nil }
        return host
    }

    var faviconUrl: URL? {
        faviconCandidates.first
    }

    var faviconCandidates: [URL] {
        guard iconType == .favicon, let normalizedUrl, let host = normalizedUrl.host else { return [] }

        var candidates: [URL] = []
        // Try direct favicon first — no third-party involved, maximum privacy.
        if let directFavicon = URL(string: "\(normalizedUrl.scheme ?? "https")://\(host)/favicon.ico") {
            candidates.append(directFavicon)
        }
        // Then Apple touch icon — also direct, no third-party.
        if let touchIcon = URL(string: "\(normalizedUrl.scheme ?? "https")://\(host)/apple-touch-icon.png") {
            candidates.append(touchIcon)
        }
        // Only fall back to third-party services if direct requests fail.
        if let encodedAbsolute = normalizedUrl.absoluteString.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
           let google = URL(string: "https://www.google.com/s2/favicons?sz=128&domain_url=\(encodedAbsolute)") {
            candidates.append(google)
        }
        if let duckduckgo = URL(string: "https://icons.duckduckgo.com/ip3/\(host).ico") {
            candidates.append(duckduckgo)
        }

        return candidates
    }

    var selfhstIconUrl: URL? {
        guard iconType == .selfhst else { return nil }
        let service = iconValue.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !service.isEmpty else { return nil }
        return URL(string: "https://raw.githubusercontent.com/selfhst/icons/main/png/\(service).png")
    }

    var customImageUrl: URL? {
        guard iconType == .systemSymbol else { return nil }
        return Self.normalizeRemoteImageUrl(iconValue)
    }

    private var normalizedUrl: URL? {
        let trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        if let parsed = URL(string: trimmed), parsed.host != nil {
            return parsed
        }
        if let parsed = URL(string: "https://\(trimmed)"), parsed.host != nil {
            return parsed
        }
        return nil
    }

    static func normalizeRemoteImageUrl(_ raw: String) -> URL? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }

        if let components = URLComponents(string: trimmed),
           let imgUrl = components.queryItems?.first(where: { $0.name == "imgurl" })?.value {
            let decodedImgUrl = imgUrl.removingPercentEncoding ?? imgUrl
            if let parsed = URL(string: decodedImgUrl), parsed.scheme?.hasPrefix("http") == true {
                return parsed
            }
        }

        let decoded = trimmed.removingPercentEncoding ?? trimmed
        if let parsed = URL(string: decoded), parsed.scheme?.hasPrefix("http") == true {
            return parsed
        }

        return nil
    }
}
