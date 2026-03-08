import SwiftUI

// Maps to app/(tabs)/_layout.tsx
// iOS 26: TabView automatically gets Liquid Glass tab bar.
// The entire custom GlassTabBar.tsx (342 lines) is replaced by native TabView.

struct ContentView: View {
    @Environment(SettingsStore.self) private var settingsStore
    @Environment(ServicesStore.self) private var servicesStore
    @Environment(Localizer.self) private var localizer
    @Environment(\.scenePhase) private var scenePhase

    var body: some View {
        TabView {
            Tab(localizer.t.tabHome, systemImage: "house.fill") {
                HomeView()
            }
            
            Tab(localizer.t.tabBookmarks, systemImage: "bookmark.fill") {
                BookmarksView()
            }

            Tab(localizer.t.tabSettings, systemImage: "gearshape.fill") {
                SettingsView()
            }
        }
        .tabBarMinimizeBehavior(.onScrollDown)
        .preferredColorScheme(colorScheme)
        .onChange(of: scenePhase) { _, newPhase in
            switch newPhase {
            case .active:
                // App returned to foreground — immediately check and resume polling
                Task { await servicesStore.checkAllReachability() }
                servicesStore.startPeriodicHealthChecks()
            case .background:
                // App went to background — stop polling to save battery
                servicesStore.stopPeriodicHealthChecks()
            default:
                break
            }
        }
    }

    private var colorScheme: ColorScheme? {
        switch settingsStore.theme {
        case .light: return .light
        case .dark: return .dark
        case .system: return nil
        }
    }
}
