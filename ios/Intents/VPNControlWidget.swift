import AppIntents
import SwiftUI
import WidgetKit

/// The Control Center button. It is built into both the app and the widget extension: the
/// system resolves a control through the app as well, and with the control only in the
/// extension iOS showed an empty, dead button.
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
