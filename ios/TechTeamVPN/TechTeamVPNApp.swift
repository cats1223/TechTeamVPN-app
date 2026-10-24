import SwiftUI

@main
struct TechTeamVPNApp: App {
    @StateObject private var model = AppModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(model)
                .environmentObject(model.vpn)
                .preferredColorScheme(.dark)
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

enum Brand {
    static let background = Color(red: 0.043, green: 0.063, blue: 0.125)
    static let surface = Color(red: 0.082, green: 0.106, blue: 0.180)
    static let surfaceHigh = Color(red: 0.118, green: 0.149, blue: 0.251)
    static let cyan = Color(red: 0, green: 0.82, blue: 1)
    static let violet = Color(red: 0.482, green: 0.361, blue: 1)
    static let green = Color(red: 0.169, green: 0.851, blue: 0.545)
    static let amber = Color(red: 1, green: 0.71, blue: 0.278)
    static let red = Color(red: 1, green: 0.361, blue: 0.478)
    static let muted = Color(red: 0.541, green: 0.576, blue: 0.698)
    static let gradient = LinearGradient(colors: [cyan, violet], startPoint: .topLeading, endPoint: .bottomTrailing)
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
