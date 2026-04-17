import SwiftUI

// MARK: - Flexible Pill Row

struct FlexiblePillRow: View {
    let items: [String]
    let tint: Color

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(items, id: \.self) { item in
                    Text(item)
                        .font(.caption2.weight(.semibold))
                        .foregroundStyle(tint)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(
                            Capsule()
                                .fill(tint.opacity(0.12))
                        )
                }
            }
        }
    }
}

// MARK: - Placeholder Card

struct PlaceholderCard: View {
    let title: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "tray")
                .foregroundStyle(AppTheme.textMuted)
            Text(title)
                .font(.subheadline)
                .foregroundStyle(AppTheme.textSecondary)
            Spacer()
        }
        .padding(AppTheme.innerPadding)
        .glassCard()
    }
}

// MARK: - Section Header

struct PangolinSectionHeader: View {
    let title: String
    let detail: String?
    var actionLabel: String? = nil
    var action: (() -> Void)? = nil

    var body: some View {
        HStack {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(AppTheme.textMuted)
                .textCase(.uppercase)
            Spacer()
            if let detail {
                Text(detail)
                    .font(.caption2)
                    .foregroundStyle(AppTheme.textSecondary)
            }
            if let actionLabel, action != nil {
                Button(action: action!) {
                    Text(actionLabel)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(AppTheme.accent)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

// MARK: - Info Pill

struct InfoPill: View {
    let label: String
    let value: String
    var tint: Color = AppTheme.textMuted

    var body: some View {
        HStack(spacing: 6) {
            Text(label)
                .font(.caption2.weight(.semibold))
                .foregroundStyle(tint.opacity(0.75))
            Text(value)
                .font(.caption2)
                .foregroundStyle(tint)
                .lineLimit(1)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(tint.opacity(0.12), in: Capsule())
    }
}

// MARK: - Hero Badge

struct HeroBadge: View {
    let text: String
    var tint: Color = AppTheme.textMuted

    var body: some View {
        Text(text)
            .font(.caption.weight(.semibold))
            .foregroundStyle(tint)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(tint.opacity(0.12), in: Capsule())
    }
}

// MARK: - Glass Stat Card

struct GlassStatCard: View {
    let title: String
    let value: String
    let icon: String
    let iconColor: Color
    var subtitle: String? = nil

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Image(systemName: icon)
                .font(.body)
                .foregroundStyle(iconColor)
                .frame(width: 34, height: 34)
                .background(iconColor.opacity(0.12), in: RoundedRectangle(cornerRadius: 10, style: .continuous))

            Text(value)
                .font(.title3.bold())
                .foregroundStyle(.primary)

            if let subtitle {
                Text(subtitle)
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(AppTheme.textSecondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(minHeight: 104, alignment: .topLeading)
        .padding(12)
        .glassCard()
    }
}
