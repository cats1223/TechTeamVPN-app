import AppIntents
import SwiftUI
import WidgetKit

@main
struct TechTeamWidgets: WidgetBundle {
    var body: some Widget {
        StatusWidget()
        if #available(iOS 18.0, *) {
            VPNControlWidget()
        }
    }
}

// MARK: - Home screen widget

struct StatusEntry: TimelineEntry {
    let date: Date
    let connected: Bool
}

struct StatusProvider: TimelineProvider {
    func placeholder(in context: Context) -> StatusEntry { StatusEntry(date: .now, connected: true) }

    func getSnapshot(in context: Context, completion: @escaping (StatusEntry) -> Void) {
        completion(StatusEntry(date: .now, connected: TunnelProbe.isConnected()))
    }

    /// The app reloads the widget whenever the VPN changes; the timeline is a safety net.
    func getTimeline(in context: Context, completion: @escaping (Timeline<StatusEntry>) -> Void) {
        let entry = StatusEntry(date: .now, connected: TunnelProbe.isConnected())
        completion(Timeline(entries: [entry], policy: .after(.now.addingTimeInterval(15 * 60))))
    }
}

/// A switch with the status under it, on a dark gradient.
struct StatusWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "TechTeamVPNStatus", provider: StatusProvider()) { entry in
            StatusWidgetView(entry: entry)
        }
        .configurationDisplayName("TechTeamVPN")
        .description("Включайте и выключайте VPN прямо с экрана «Домой».")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct StatusWidgetView: View {
    let entry: StatusEntry
    @Environment(\.widgetFamily) private var family

    private var accent: Color { entry.connected ? Color(red: 0.17, green: 0.85, blue: 0.55) : Color(white: 0.6) }
    private var status: LocalizedStringKey { entry.connected ? "Подключено" : "Не подключено" }

    var body: some View {
        Group {
            if family == .systemMedium { medium } else { small }
        }
        .containerBackground(for: .widget) {
            LinearGradient(
                colors: entry.connected
                    ? [Color(red: 0.03, green: 0.18, blue: 0.12), .black]
                    : [Color(white: 0.12), .black],
                startPoint: .topLeading, endPoint: .bottomTrailing
            )
        }
    }

    private var small: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                shield(size: 34)
                Spacer()
                slider
            }
            Spacer()
            Text("TechTeamVPN").font(.caption).foregroundStyle(.secondary)
            statusLine.font(.headline)
        }
    }

    private var medium: some View {
        HStack(spacing: 14) {
            shield(size: 56)
            VStack(alignment: .leading, spacing: 4) {
                Text("TechTeamVPN").font(.subheadline).foregroundStyle(.secondary)
                statusLine.font(.title3.weight(.semibold))
                Text(entry.connected ? "Нажмите, чтобы отключить" : "Нажмите, чтобы подключить")
                    .font(.caption).foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
            slider
        }
    }

    /// A drawn switch: the system Toggle in a small widget loses its knob and looks like a grey
    /// pill. Tapping it runs the same intent as the toggle would.
    private var slider: some View {
        Button(intent: SetVPNIntent(value: !entry.connected)) {
            ZStack(alignment: entry.connected ? .trailing : .leading) {
                Capsule()
                    .fill(entry.connected ? accent : Color.white.opacity(0.22))
                    .overlay(Capsule().strokeBorder(.white.opacity(0.15), lineWidth: 1))
                Circle()
                    .fill(.white)
                    .shadow(color: .black.opacity(0.35), radius: 2, y: 1)
                    .padding(3)
            }
            .frame(width: 56, height: 32)
            .invalidatableContent()
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(status))
    }

    private var statusLine: some View {
        HStack(spacing: 6) {
            Circle().fill(accent).frame(width: 8, height: 8)
            Text(status)
        }
        .widgetAccentable()
    }

    private func shield(size: CGFloat) -> some View {
        Image(systemName: entry.connected ? "checkmark.shield.fill" : "shield")
            .font(.system(size: size * 0.55, weight: .semibold))
            .foregroundStyle(accent)
            .frame(width: size, height: size)
            .background(accent.opacity(0.18), in: Circle())
            .widgetAccentable()
    }
}

// MARK: - Control Center button

@available(iOS 18.0, *)
struct VPNControlWidget: ControlWidget {
    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: "TechTeamVPNControl", provider: Provider()) { isOn in
            // The label gives the control its own icon, shown in Control Center's edit mode and
            // before the value is known; the value label swaps it for the current state.
            ControlWidgetToggle(isOn: isOn, action: SetVPNIntent()) {
                Label("TechTeamVPN", systemImage: "checkmark.shield.fill")
            } valueLabel: { on in
                Label(on ? "Подключено" : "Не подключено", systemImage: on ? "checkmark.shield.fill" : "shield.slash")
            }
            .tint(.green)
        }
        .displayName("TechTeamVPN")
        .description("Включить или выключить VPN.")
    }

    struct Provider: ControlValueProvider {
        var previewValue: Bool { false }

        func currentValue() async throws -> Bool { TunnelProbe.isConnected() }
    }
}
