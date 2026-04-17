import SwiftUI

// MARK: - Dockhand Mini Pill

struct DockhandMiniPill: View {
    let text: String
    let tint: Color

    var body: some View {
        Text(text)
            .font(.caption2.weight(.semibold))
            .lineLimit(1)
            .foregroundStyle(tint)
            .padding(.horizontal, 9)
            .padding(.vertical, 4)
            .background(tint.opacity(0.14), in: Capsule())
    }
}

// MARK: - Dockhand Section Title

struct DockhandSectionTitle: View {
    let title: String
    let trailing: String?
    var uppercased: Bool = true

    var body: some View {
        HStack {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
                .textCase(uppercased ? .uppercase : nil)
            Spacer()
            if let trailing {
                Text(trailing)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textMuted)
            }
        }
    }
}

// MARK: - Dockhand Placeholder

struct DockhandPlaceholder: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.subheadline)
            .foregroundStyle(AppTheme.textMuted)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(14)
            .glassCard(cornerRadius: AppTheme.smallRadius)
    }
}

// MARK: - Dockhand Resource Chip

struct DockhandResourceChip: View {
    let title: String
    let value: Int

    var body: some View {
        HStack(spacing: 6) {
            Text("\(value)")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.primary)
            Text(title)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
                .lineLimit(1)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(AppTheme.surface.opacity(0.85), in: Capsule())
    }
}

// MARK: - Dockhand Resource Stat Card

struct DockhandResourceStatCard: View {
    let title: String
    let value: Int
    let tint: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title)
                .font(.caption.weight(.bold))
                .foregroundStyle(AppTheme.textMuted)
                .lineLimit(1)

            Text("\(value)")
                .font(.title3.weight(.bold))
                .foregroundStyle(tint)
        }
        .frame(maxWidth: .infinity, minHeight: 72, alignment: .leading)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .glassCard(cornerRadius: AppTheme.smallRadius, tint: tint.opacity(0.08))
        .overlay {
            RoundedRectangle(cornerRadius: AppTheme.smallRadius, style: .continuous)
                .stroke(tint.opacity(0.28), lineWidth: 1)
        }
    }
}

// MARK: - Dockhand Container Card

struct DockhandContainerCard: View {
    let container: DockhandContainerInfo
    let action: () -> Void

    var body: some View {
        let statusTint = container.isIssue ? AppTheme.danger : (container.isRunning ? AppTheme.running : AppTheme.warning)
        let healthTint = {
            let value = (container.health ?? "").lowercased()
            if value.contains("unhealthy") || value.contains("fail") { return AppTheme.danger }
            if value.contains("starting") { return AppTheme.warning }
            if value.contains("healthy") { return AppTheme.running }
            return AppTheme.info
        }()
        let borderColor = container.isIssue ? statusTint.opacity(0.34) : .white.opacity(0.05)

        Button(action: action) {
            VStack(alignment: .leading, spacing: 9) {
                HStack(spacing: 8) {
                    Text(container.name)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.textMuted)
                }

                Text(container.image)
                    .font(.caption)
                    .foregroundStyle(AppTheme.textMuted)
                    .lineLimit(1)

                HStack(spacing: 8) {
                    DockhandMiniPill(text: container.state, tint: statusTint)
                    if let health = container.health, !health.isEmpty {
                        DockhandMiniPill(text: health, tint: healthTint)
                    }
                    Spacer()
                    Text(container.portsSummary)
                        .font(.caption2)
                        .foregroundStyle(AppTheme.textMuted)
                        .lineLimit(1)
                }
            }
            .padding(12)
            .glassCard(cornerRadius: AppTheme.smallRadius, tint: nil)
            .overlay {
                RoundedRectangle(cornerRadius: AppTheme.smallRadius, style: .continuous)
                    .stroke(borderColor, lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Dockhand Stack Card

struct DockhandStackCard: View {
    let stack: DockhandStackInfo
    let action: () -> Void

    var body: some View {
        let running = stack.status.lowercased().contains("running") || stack.status.lowercased().contains("up")
        let tint = running ? AppTheme.running : AppTheme.warning
        let borderColor = running ? Color.white.opacity(0.05) : tint.opacity(0.3)

        Button(action: action) {
            HStack(spacing: 10) {
                Circle()
                    .fill(tint.opacity(0.14))
                    .frame(width: 28, height: 28)
                    .overlay {
                        Image(systemName: "square.3.layers.3d")
                            .font(.caption.weight(.medium))
                            .foregroundStyle(tint)
                    }

                VStack(alignment: .leading, spacing: 2) {
                    Text(stack.name)
                        .font(.subheadline.weight(.semibold))
                        .lineLimit(1)
                    Text((stack.source?.isEmpty == false ? stack.source : stack.status) ?? stack.status)
                        .font(.caption)
                        .foregroundStyle(AppTheme.textMuted)
                        .lineLimit(1)
                }

                Spacer()

                DockhandMiniPill(text: stack.status, tint: tint)
                Text("\(stack.services)")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textSecondary)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(AppTheme.textMuted)
            }
            .padding(12)
            .glassCard(cornerRadius: AppTheme.smallRadius, tint: nil)
            .overlay {
                RoundedRectangle(cornerRadius: AppTheme.smallRadius, style: .continuous)
                    .stroke(borderColor, lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Dockhand Detail Row

struct DockhandDetailRow: View {
    let label: String
    let value: String

    var body: some View {
        HStack(alignment: .top) {
            Text(label)
                .font(.caption)
                .foregroundStyle(AppTheme.textMuted)
            Spacer()
            Text(value)
                .font(.caption)
                .foregroundStyle(AppTheme.textSecondary)
                .multilineTextAlignment(.trailing)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

// MARK: - Dockhand Detail Action Button

struct DockhandDetailActionButton: View {
    let title: String
    let icon: String
    let tint: Color
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label(title, systemImage: icon)
                .font(.caption.weight(.semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.72)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 8)
                .padding(.vertical, 8)
                .foregroundStyle(enabled ? tint : AppTheme.textMuted)
                .background(
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .fill(enabled ? tint.opacity(0.12) : AppTheme.surface.opacity(0.7))
                )
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.65)
    }
}

// MARK: - Dockhand Stack Action Button

struct DockhandStackActionButton: View {
    let title: String
    let icon: String
    let tint: Color
    let enabled: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Label(title, systemImage: icon)
                .font(.caption.weight(.semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.72)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, 10)
                .padding(.vertical, 10)
                .foregroundStyle(enabled ? tint : AppTheme.textMuted)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(enabled ? tint.opacity(0.12) : AppTheme.surface.opacity(0.7))
                )
        }
        .buttonStyle(.plain)
        .disabled(!enabled)
        .opacity(enabled ? 1 : 0.65)
    }
}
