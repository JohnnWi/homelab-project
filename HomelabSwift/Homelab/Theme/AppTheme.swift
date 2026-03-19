import SwiftUI

// MARK: - Semantic colors (map exactly from constants/themes.ts)
// All colors use light/dark adaptive Color sets

enum AppTheme {
    // Accent
    static let accent = Color("AccentColor")
    static var primary: Color { accent }

    // Semantic status colors
    static var running: Color { Color(hex: "#3FB950") }      // dark: #3FB950 / light: #2DA44E
    static var stopped: Color { Color(hex: "#F85149") }      // dark: #F85149 / light: #CF222E
    static var paused: Color { Color(hex: "#D29922") }       // dark: #D29922 / light: #BF8700
    static var created: Color { Color(hex: "#58A6FF") }      // dark: #58A6FF / light: #0969DA
    static var info: Color { Color(hex: "#58A6FF") }
    static var danger: Color { Color(hex: "#F85149") }
    static var warning: Color { Color(hex: "#D29922") }

    // Background & surface (used for content areas not covered by glass)
    static var background: Color { Color(.systemGroupedBackground) }
    static var surface: Color { Color(.secondarySystemGroupedBackground) }

    // Text
    static var textSecondary: Color { Color(.secondaryLabel) }
    static var textMuted: Color { Color(.tertiaryLabel) }

    // Corner radii
    static let cardRadius: CGFloat = 20
    static let smallRadius: CGFloat = 12
    static let pillRadius: CGFloat = 100

    // Spacing
    static let padding: CGFloat = 16
    static let innerPadding: CGFloat = 12
    static let gridSpacing: CGFloat = 12

    // MARK: - Container status color

    static func statusColor(for state: String) -> Color {
        switch state.lowercased() {
        case "up":                         return running
        case "down":                       return stopped
        case "grace":                      return warning
        case "running":                    return running
        case "exited", "dead":             return stopped
        case "paused":                     return paused
        case "created", "restarting", "new":      return created
        default:                           return .gray
        }
    }

    // MARK: - System status for Beszel

    static func systemStatusColor(online: Bool) -> Color {
        online ? running : stopped
    }

    // MARK: - Premium Gradients

    static var meshColors: [Color] {
        [
            Color(hex: "#0A84FF"), // Blue
            Color(hex: "#5E5CE6"), // Indigo
            Color(hex: "#00C7BE"), // Teal
            Color(hex: "#FF375F")  // Pink/Red
        ]
    }

    @ViewBuilder
    static func premiumGradient() -> some View {
        PremiumGradientView()
    }

    // MARK: - Dedicated Button Styles
    
    struct LiquidGlass: ButtonStyle {
        @Environment(\.colorScheme) var colorScheme
        var color: Color? = nil // Optional override
        var size: CGFloat = 72
        
        func makeBody(configuration: Configuration) -> some View {
            let isDark = colorScheme == .dark
            let glassColor = color ?? (isDark ? .white : AppTheme.accent)
            
            configuration.label
                .frame(width: size, height: size)
                .background(
                    ZStack {
                        // Main Glass Material
                        Circle()
                            .fill(.ultraThinMaterial)
                            .environment(\.colorScheme, colorScheme) // Ensure material follows scheme
                        
                        // Inner glow/highlight for "Liquid" feel
                        Circle()
                            .stroke(
                                LinearGradient(
                                    colors: [
                                        glassColor.opacity(isDark ? 0.5 : 0.3),
                                        glassColor.opacity(0.1),
                                        glassColor.opacity(isDark ? 0.3 : 0.1)
                                    ],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                ),
                                lineWidth: 1.5
                            )
                        
                        // Surface sheen
                        Circle()
                            .fill(
                                LinearGradient(
                                    colors: [
                                        .white.opacity(isDark ? 0.1 : 0.2),
                                        .clear,
                                        .black.opacity(isDark ? 0.05 : 0.02)
                                    ],
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .blendMode(.overlay)
                        
                        if configuration.isPressed {
                            Circle()
                                .fill(glassColor.opacity(isDark ? 0.2 : 0.15))
                                .blur(radius: 2)
                        }
                    }
                )
                // Shadow for depth
                .shadow(color: .black.opacity(isDark ? 0.3 : 0.1), radius: 10, y: 5)
                .scaleEffect(configuration.isPressed ? 0.96 : 1.0)
                .animation(.spring(response: 0.28, dampingFraction: 0.8), value: configuration.isPressed)
        }
    }
}

// MARK: - Premium Gradient View

struct PremiumGradientView: View {
    @Environment(\.colorScheme) var colorScheme
    
    var body: some View {
        let isDark = colorScheme == .dark
        
        ZStack {
            if isDark {
                // Dark Mode: Deep obsidian to slate
                LinearGradient(
                    colors: [
                        Color.black,
                        Color(hex: "#0F172A"),
                        Color(hex: "#1E293B")
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
            } else {
                // Light Mode: Soft, airy glass-inspired gradient
                LinearGradient(
                    colors: [
                        Color(hex: "#F8FAFC"), // Slate 50
                        Color(hex: "#F0F9FF"), // Sky 50
                        Color(hex: "#EEF2FF")  // Indigo 50
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                
                // Add soft colored blobs for a "Liquid" Apple-style feel
                ZStack {
                    Circle()
                        .fill(Color(hex: "#38BDF8").opacity(0.12)) // Sky 400
                        .frame(width: 400)
                        .offset(x: 100, y: -150)
                        .blur(radius: 80)
                    
                    Circle()
                        .fill(Color(hex: "#818CF8").opacity(0.1)) // Indigo 400
                        .frame(width: 500)
                        .offset(x: -150, y: 150)
                        .blur(radius: 100)
                }
            }
        }
        .ignoresSafeArea()
    }
}

extension ButtonStyle where Self == AppTheme.LiquidGlass {
    static var liquidGlass: AppTheme.LiquidGlass { AppTheme.LiquidGlass() }
    
    static func liquidGlass(color: Color? = nil, size: CGFloat = 72) -> AppTheme.LiquidGlass {
        AppTheme.LiquidGlass(color: color, size: size)
    }
}

// MARK: - Adaptive status color (light/dark aware)

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3:
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6:
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8:
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }

    static func adaptiveStatusColor(for state: String) -> Color {
        AppTheme.statusColor(for: state)
    }
}
