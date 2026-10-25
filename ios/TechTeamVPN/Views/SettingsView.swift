import CryptoKit
import SwiftUI

enum AppSettings {
    static let themeKey = "theme"
    static let devModeKey = "devMode"
    /// Russian sites and IP ranges go past the VPN; on by default.
    static let russiaDirectKey = "russiaDirect"
    /// Offer betas in the update check too, not only full releases.
    static let betaUpdatesKey = "betaUpdates"

    static var russiaDirect: Bool {
        UserDefaults.standard.object(forKey: russiaDirectKey) as? Bool ?? true
    }

    enum Theme: String, CaseIterable, Identifiable {
        case dark, light
        var id: String { rawValue }
        var title: String { self == .dark ? "Чёрная" : "Белая" }
    }

    /// SHA-256 of the developer password, so the password itself is not in the source.
    static let devPasswordHash = "ebf975deece2e0b3b0f84f343e7b235ec1bcc459910dfb6ebace4ec35a56df6d"

    static func checkDevPassword(_ password: String) -> Bool {
        SHA256.hash(data: Data(password.utf8)).map { String(format: "%02x", $0) }.joined() == devPasswordHash
    }

    /// The release version, with the beta suffix when there is one; the update check compares it.
    static var shortVersion: String {
        let info = Bundle.main.infoDictionary
        let display = info?["TTDisplayVersion"] as? String ?? ""
        return display.isEmpty ? info?["CFBundleShortVersionString"] as? String ?? "?" : display
    }

    static var version: String {
        "\(shortVersion) (\(Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?"))"
    }
}

struct SettingsView: View {
    @EnvironmentObject private var model: AppModel
    @AppStorage(AppSettings.themeKey) private var theme = AppSettings.Theme.dark.rawValue
    @AppStorage(AppSettings.devModeKey) private var devMode = false
    @AppStorage(AppSettings.russiaDirectKey) private var russiaDirect = true
    @AppStorage(AppSettings.betaUpdatesKey) private var betaUpdates = false
    @State private var versionTaps = 0
    @State private var lastTap = Date.distantPast
    @State private var askPassword = false
    @State private var password = ""
    @State private var showLog = false
    @State private var showDiagnostics = false

    var body: some View {
        NavigationStack {
            List {
                Section("Оформление") {
                    Picker("Тема", selection: $theme) {
                        ForEach(AppSettings.Theme.allCases) { Text($0.title).tag($0.rawValue) }
                    }
                    .pickerStyle(.segmented)
                    .listRowBackground(Brand.surface)
                }

                Section {
                    Toggle("Российские сайты напрямую", isOn: $russiaDirect)
                        .tint(Brand.cyan)
                        .onChange(of: russiaDirect) { _ in
                            Task { await model.applyTunnelSettings() }
                        }
                        .listRowBackground(Brand.surface)
                } header: {
                    Text("Раздельное туннелирование")
                } footer: {
                    Text("Сайты в зонах .ru, .su, .рф и российские адреса открываются без VPN, с вашего обычного адреса. "
                        + "Российские приложения ходят на свои серверы тоже напрямую. Выбрать отдельные приложения "
                        + "на iPhone нельзя: Apple разрешает это только через MDM.")
                }

                Section("О приложении") {
                    HStack {
                        Text("Версия")
                        Spacer()
                        Text(AppSettings.version).foregroundStyle(Brand.muted)
                    }
                    .contentShape(Rectangle())
                    .onTapGesture(perform: tapVersion)
                    .listRowBackground(Brand.surface)
                    Button("Проверить обновления") { Task { await model.checkForUpdate(manual: true) } }
                        .listRowBackground(Brand.surface)
                    Toggle("Получать бета-версии", isOn: $betaUpdates)
                        .tint(Brand.cyan)
                        .listRowBackground(Brand.surface)
                }

                if devMode { developerSection }
            }
            .scrollContentBackground(.hidden)
            .background(Brand.background.ignoresSafeArea())
            .navigationTitle("Настройки")
            .alert("Режим разработчика", isPresented: $askPassword) {
                SecureField("Пароль", text: $password)
                Button("Отмена", role: .cancel) { password = "" }
                Button("Включить") { unlock() }
            } message: {
                Text("Введите пароль разработчика")
            }
            .sheet(isPresented: $showLog) { LogView() }
            .sheet(isPresented: $showDiagnostics) { DiagnosticsView() }
        }
    }

    private var developerSection: some View {
        Section {
            Button { showLog = true } label: { Label("Журнал подключения", systemImage: "doc.text.magnifyingglass") }
            Button { showDiagnostics = true } label: { Label("Проверка подключения", systemImage: "checklist") }
            copyRow("ID устройства", DeviceInfo.hwid)
            if let profile = model.state.selected {
                copyRow("Параметры ключа", profile.summary)
            }
            Button(role: .destructive) { devMode = false } label: {
                Label("Выключить режим разработчика", systemImage: "hammer")
            }
        } header: {
            Text("Разработчик")
        } footer: {
            Text("В режиме разработчика VPN ведёт подробный журнал Xray. Переподключитесь, чтобы он начал записываться.")
        }
        .listRowBackground(Brand.surface)
    }

    private func copyRow(_ title: String, _ value: String) -> some View {
        Button {
            UIPasteboard.general.string = value
            model.message = "\(title): скопировано"
        } label: {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).foregroundStyle(.primary)
                Text(value).font(.caption.monospaced()).foregroundStyle(Brand.muted).lineLimit(3)
            }
        }
    }

    /// Five taps on the version within a couple of seconds ask for the developer password.
    private func tapVersion() {
        let now = Date()
        versionTaps = now.timeIntervalSince(lastTap) < 1.5 ? versionTaps + 1 : 1
        lastTap = now
        guard versionTaps >= 5 else { return }
        versionTaps = 0
        if devMode {
            model.message = "Режим разработчика уже включён"
        } else {
            askPassword = true
        }
    }

    private func unlock() {
        if AppSettings.checkDevPassword(password) {
            devMode = true
            model.message = "Режим разработчика включён"
        } else {
            model.message = "Неверный пароль"
        }
        password = ""
    }
}
