import SwiftUI

struct PressableCardButtonStyle: ButtonStyle {
    var scale: CGFloat = 0.985
    var opacity: Double = 0.94
    var animation: Animation = .spring(response: 0.3, dampingFraction: 0.85)

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .opacity(configuration.isPressed ? opacity : 1)
            .animation(animation, value: configuration.isPressed)
    }
}

struct FlexibleTagRow: View {
    let tags: [String]

    var body: some View {
        FlexibleView(data: tags, spacing: 8, alignment: .leading) { tag in
            Text(tag)
                .font(.caption2.bold())
                .foregroundStyle(AppTheme.textSecondary)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .modifier(GlassEffectModifier(cornerRadius: AppTheme.pillRadius, tint: nil, interactive: false))
        }
    }
}

struct FlexibleView<Data: Collection, Content: View>: View where Data.Element: Hashable {
    let data: Data
    let spacing: CGFloat
    let alignment: HorizontalAlignment
    let content: (Data.Element) -> Content

    init(data: Data, spacing: CGFloat = 8, alignment: HorizontalAlignment = .leading, @ViewBuilder content: @escaping (Data.Element) -> Content) {
        self.data = data
        self.spacing = spacing
        self.alignment = alignment
        self.content = content
    }

    var body: some View {
        HealthchecksFlowLayout(spacing: spacing, alignment: alignment) {
            ForEach(Array(data), id: \.self) { item in
                content(item)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .frame(minHeight: 20)
    }
}

private struct HealthchecksFlowLayout: Layout {
    let spacing: CGFloat
    let alignment: HorizontalAlignment

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > maxWidth {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }

        return CGSize(width: maxWidth, height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            subview.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
