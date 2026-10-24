import SwiftUI

@main
struct TechTeamVPNApp: App {
    @StateObject private var model = AppModel()
    @AppStorage(AppSettings.themeKey) private var theme = AppSettings.Theme.dark.rawValue

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .environmentObject(model.vpn)
                .preferredColorScheme(AppSettings.Theme(rawValue: theme) == .light ? .light : .dark)
                // Opening a vless:// link imports the key.
                .onOpenURL { url in
                    Task { await model.addInput(url.absoluteString) }
                }
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var model: AppModel

    var body: some View {
        TabView {
            HomeView()
                .tabItem { Label("Главная", systemImage: "shield.lefthalf.filled") }
            ServersView()
                .tabItem { Label("Серверы", systemImage: "server.rack") }
            SettingsView()
                .tabItem { Label("Настройки", systemImage: "gearshape") }
        }
        .tint(Brand.cyan)
        .alert(
            model.message ?? "",
            isPresented: Binding(get: { model.message != nil }, set: { if !$0 { model.message = nil } })
        ) {
            Button("OK", role: .cancel) {}
        }
    }
}

/// Brand palette; every color has a dark and a light variant for the theme setting.
enum Brand {
    static let background = adaptive(dark: (0.043, 0.063, 0.125), light: (0.957, 0.965, 0.984))
    static let surface = adaptive(dark: (0.082, 0.106, 0.180), light: (1, 1, 1))
    static let surfaceHigh = adaptive(dark: (0.118, 0.149, 0.251), light: (0.902, 0.918, 0.953))
    static let cyan = adaptive(dark: (0, 0.82, 1), light: (0, 0.58, 0.78))
    static let violet = adaptive(dark: (0.482, 0.361, 1), light: (0.416, 0.294, 1))
    static let green = adaptive(dark: (0.169, 0.851, 0.545), light: (0.071, 0.659, 0.400))
    static let amber = adaptive(dark: (1, 0.71, 0.278), light: (0.851, 0.541, 0))
    static let red = adaptive(dark: (1, 0.361, 0.478), light: (0.878, 0.267, 0.373))
    static let muted = adaptive(dark: (0.541, 0.576, 0.698), light: (0.420, 0.447, 0.502))
    static let gradient = LinearGradient(colors: [cyan, violet], startPoint: .topLeading, endPoint: .bottomTrailing)

    private static func adaptive(dark: (Double, Double, Double), light: (Double, Double, Double)) -> Color {
        Color(UIColor { traits in
            let c = traits.userInterfaceStyle == .light ? light : dark
            return UIColor(red: c.0, green: c.1, blue: c.2, alpha: 1)
        })
    }
}

struct CardStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Brand.surface, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

extension View {
    func card() -> some View { modifier(CardStyle()) }
}

func formatBytes(_ bytes: Int64) -> String {
    let units = ["Б", "КБ", "МБ", "ГБ", "ТБ"]
    var value = Double(bytes)
    var i = 0
    while value >= 1024, i < units.count - 1 {
        value /= 1024
        i += 1
    }
    return i == 0 ? "\(bytes) \(units[0])" : String(format: "%.1f %@", value, units[i])
}
