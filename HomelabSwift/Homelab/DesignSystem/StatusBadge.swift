import SwiftUI

// Maps to components/StatusBadge.tsx

struct StatusBadge: View {
    let status: String
    var compact: Bool = false

    @Environment(Localizer.self) private var localizer

    private var color: Color {
        AppTheme.statusColor(for: status)
    }

    private var localizedStatus: String {
        switch status.lowercased() {
        case "running": return localizer.t.portainerRunning
        case "exited", "stopped", "dead": return localizer.t.portainerStopped
        case "paused": return localizer.t.actionPause
        case "healthy": return localizer.t.portainerHealthy
        case "unhealthy": return localizer.t.portainerUnhealthy
        case "up": return localizer.t.beszelUp
        case "down": return localizer.t.beszelDown
        default: return status.capitalized
        }
    }

    var body: some View {
        HStack(spacing: compact ? 4 : 6) {
            Circle()
                .fill(color)
                .frame(width: compact ? 6 : 8, height: compact ? 6 : 8)

            if !compact {
                Text(localizedStatus)
                    .font(.caption.bold())
                    .foregroundStyle(color)
            }
        }
        .padding(.horizontal, compact ? 6 : 10)
        .padding(.vertical, compact ? 3 : 4)
        .modifier(GlassEffectModifier(cornerRadius: AppTheme.pillRadius, tint: color.opacity(0.2), interactive: false))
    }
}

// MARK: - Beszel system status

struct SystemStatusBadge: View {
    let isOnline: Bool

    var body: some View {
        StatusBadge(status: isOnline ? "up" : "down")
    }
}
