import Foundation
import Observation

@Observable
@MainActor
final class SettingsStore {

    // MARK: - Persisted State

    var language: Language {
        didSet {
            UserDefaults.standard.set(language.rawValue, forKey: Keys.language)
        }
    }

    var theme: ThemeMode {
        didSet {
            UserDefaults.standard.set(theme.rawValue, forKey: Keys.theme)
        }
    }

    var hiddenServices: Set<String> {
        didSet {
            UserDefaults.standard.set(Array(hiddenServices), forKey: Keys.hiddenServices)
        }
    }

    // MARK: - Keys

    private enum Keys {
        static let language = "homelab_language"
        static let theme = "homelab_theme"
        static let hiddenServices = "homelab_hidden_services"
    }

    // MARK: - Init

    init() {
        let savedLang = UserDefaults.standard.string(forKey: Keys.language) ?? "en"
        self.language = Language(rawValue: savedLang) ?? .en

        let savedTheme = UserDefaults.standard.string(forKey: Keys.theme)
        self.theme = savedTheme.flatMap(ThemeMode.init) ?? .system

        let savedHidden = UserDefaults.standard.stringArray(forKey: Keys.hiddenServices) ?? []
        self.hiddenServices = Set(savedHidden)
    }

    // MARK: - Service Visibility

    func isServiceHidden(_ type: ServiceType) -> Bool {
        hiddenServices.contains(type.rawValue)
    }

    func toggleServiceVisibility(_ type: ServiceType) {
        if hiddenServices.contains(type.rawValue) {
            hiddenServices.remove(type.rawValue)
        } else {
            hiddenServices.insert(type.rawValue)
        }
    }
}

// MARK: - Language

enum Language: String, CaseIterable, Codable {
    case it, en, fr, es, de

    var displayName: String {
        switch self {
        case .it: return "Italiano"
        case .en: return "English"
        case .fr: return "Français"
        case .es: return "Español"
        case .de: return "Deutsch"
        }
    }

    var flagEmoji: String {
        switch self {
        case .it: return "🇮🇹"
        case .en: return "🇬🇧"
        case .fr: return "🇫🇷"
        case .es: return "🇪🇸"
        case .de: return "🇩🇪"
        }
    }
}

// MARK: - ThemeMode

enum ThemeMode: String, CaseIterable, Codable {
    case light, dark, system
}
