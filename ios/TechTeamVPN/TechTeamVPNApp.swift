import SwiftUI

@main
struct TechTeamVPNApp: App {
    @StateObject private var model = AppModel()
    @AppStorage(AppSettings.themeKey) private var theme = AppSettings.Theme.dark.rawValue
    @Environment(\.scenePhase) private var scenePhase
    @State private var showSplash = true
    @State private var showOnboarding = false

    var body: some Scene {
        WindowGroup {
            ZStack {
                RootView()
                if showSplash {
                    SplashView {
                        showSplash = false
                        // First launch with no servers yet: a short introduction.
                        showOnboarding = !UserDefaults.standard.bool(forKey: "onboardingSeen") && model.state.profiles.isEmpty
                    }
                    .zIndex(1)
                }
            }
                .environmentObject(model)
                .environmentObject(model.vpn)
                .fullScreenCover(isPresented: $showOnboarding) {
                    OnboardingView {
                        UserDefaults.standard.set(true, forKey: "onboardingSeen")
                        showOnboarding = false
                    }
                }
                .preferredColorScheme(AppSettings.Theme(rawValue: theme) == .light ? .light : .dark)
                // Opening a server link (vless, trojan) imports the key.
                .onOpenURL { url in
                    Task { await model.addInput(url.absoluteString) }
                }
                // First launch with a subscription link already copied: offer it.
                .onChange(of: scenePhase) { phase in
                    if phase == .active { Task { await model.offerClipboard(force: false) } }
                }
        }
    }
}

struct RootView: View {
    @EnvironmentObject private var model: AppModel
    /// The accent the interface was last drawn in; changing it rebuilds the home screen.
    @State private var accent = Accent.hex

    var body: some View {
        // Servers and settings open from the panel on the home screen.
        HomeView()
            .id(accent)
            .tint(Brand.cyan)
            .onReceive(NotificationCenter.default.publisher(for: Accent.applyNotification)) { _ in
                if accent != Accent.hex { accent = Accent.hex }
            }
        .alert("Добавить подписку?", isPresented: $model.clipboardOffer) {
            Button("Добавить") { Task { await model.addFromClipboard() } }
            Button("Отмена", role: .cancel) {}
        } message: {
            Text("В буфере обмена есть ссылка. Добавить её как подписку?")
        }
        .sheet(isPresented: Binding(get: { model.whatsNew != nil }, set: { if !$0 { model.dismissWhatsNew() } })) {
            WhatsNewView(notes: model.whatsNew ?? "") { model.dismissWhatsNew() }
                .presentationDetents([.medium, .large])
        }
        .alert(
            "Доступно обновление",
            isPresented: Binding(get: { model.update != nil }, set: { if !$0 { model.update = nil } }),
            presenting: model.update
        ) { release in
            Button("Скачать") {
                model.dismissUpdate()
                UIApplication.shared.open(release.pageURL)
            }
            Button("Позже", role: .cancel) { model.dismissUpdate() }
        } message: { release in
            Text("Вышла версия \(release.version), у вас \(AppSettings.shortVersion). Новый IPA — на странице релиза.")
        }
    }
}

/// Shows `model.message`. A view covered by a sheet cannot present an alert, so the home screen
/// turns its copy off while a sheet is open and each sheet carries its own.
struct MessageAlert: ViewModifier {
    @EnvironmentObject private var model: AppModel
    var active: Bool

    func body(content: Content) -> some View {
        content.alert(
            model.message ?? "",
            isPresented: Binding(get: { active && model.message != nil }, set: { if !$0 { model.message = nil } })
        ) {
            Button("OK", role: .cancel) {}
        }
    }
}

extension View {
    func messageAlert(active: Bool = true) -> some View { modifier(MessageAlert(active: active)) }
}

/// Brand palette; every color has a dark and a light variant for the theme setting. Backgrounds
/// are neutral black, gray and white; only the accents carry color.
enum Brand {
    static let background = adaptive(dark: (0, 0, 0), light: (0.949, 0.949, 0.969))
    static let surface = adaptive(dark: (0.110, 0.110, 0.118), light: (1, 1, 1))
    static let surfaceHigh = adaptive(dark: (0.173, 0.173, 0.180), light: (0.898, 0.898, 0.918))
    /// The accent: the brand cyan, or the color picked in settings (`Accent`).
    static var cyan: Color { Accent.color ?? defaultCyan }
    static let defaultCyan = adaptive(dark: (0, 0.82, 1), light: (0, 0.58, 0.78))
    static let violet = adaptive(dark: (0.482, 0.361, 1), light: (0.416, 0.294, 1))
    static let green = adaptive(dark: (0.169, 0.851, 0.545), light: (0.071, 0.659, 0.400))
    static let amber = adaptive(dark: (1, 0.71, 0.278), light: (0.851, 0.541, 0))
    static let red = adaptive(dark: (1, 0.361, 0.478), light: (0.878, 0.267, 0.373))
    static let muted = adaptive(dark: (0.557, 0.557, 0.576), light: (0.424, 0.424, 0.439))
    static var gradient: LinearGradient { LinearGradient(colors: [cyan, violet], startPoint: .topLeading, endPoint: .bottomTrailing) }

    private static func adaptive(dark: (Double, Double, Double), light: (Double, Double, Double)) -> Color {
        Color(UIColor { traits in
            let c = traits.userInterfaceStyle == .light ? light : dark
            return UIColor(red: c.0, green: c.1, blue: c.2, alpha: 1)
        })
    }
}

/// The accent color picked in settings, stored as "RRGGBB"; empty for the brand cyan.
enum Accent {
    static let key = "accent"
    /// Posted when settings close, so the whole interface is redrawn in the new color.
    static let applyNotification = Notification.Name("TechTeamVPN.applyAccent")

    static let presets: [(hex: String, name: LocalizedStringKey)] = [
        ("", "Голубой"), ("7B5CFF", "Фиолетовый"), ("2BD98B", "Зелёный"),
        ("FF9F0A", "Оранжевый"), ("FF4F9A", "Розовый"), ("FF453A", "Красный"),
    ]

    static var hex: String { UserDefaults.standard.string(forKey: key) ?? "" }
    static var color: Color? { Color(hex: hex) }

    static func hex(of color: Color) -> String {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(color).getRed(&r, green: &g, blue: &b, alpha: &a)
        return String(format: "%02X%02X%02X", Int(round(r * 255)), Int(round(g * 255)), Int(round(b * 255)))
    }
}

extension Color {
    /// "RRGGBB"; nil for anything else.
    init?(hex: String) {
        guard hex.count == 6, let v = UInt32(hex, radix: 16) else { return nil }
        self.init(red: Double(v >> 16 & 0xFF) / 255, green: Double(v >> 8 & 0xFF) / 255, blue: Double(v & 0xFF) / 255)
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
    let units = Bundle.main.preferredLocalizations.first == "ru"
        ? ["Б", "КБ", "МБ", "ГБ", "ТБ"] : ["B", "KB", "MB", "GB", "TB"]
    var value = Double(bytes)
    var i = 0
    while value >= 1024, i < units.count - 1 {
        value /= 1024
        i += 1
    }
    return i == 0 ? "\(bytes) \(units[0])" : String(format: "%.1f %@", value, units[i])
}
