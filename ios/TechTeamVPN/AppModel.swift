import Foundation
import UIKit
import Network

@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var state = ProfileStore.load()
    /// Profile id to TCP connect time in ms; -1 means unreachable.
    @Published private(set) var pings: [String: Int] = [:]
    @Published private(set) var busy = false
    @Published var message: String?
    @Published private(set) var internetCheck: InternetCheck = .idle
    /// Shows "add the subscription from the clipboard?"; set by offerClipboard.
    @Published var clipboardOffer = false
    /// A newer release to offer; nil hides the alert.
    @Published var update: Updates.Release?

    /// Where the phone really is, looked up while the VPN is off.
    @Published private(set) var homeLocation: GeoIP.Location?
    /// Where sites see the phone now: the home location, or the server's while connected.
    @Published private(set) var visibleLocation: GeoIP.Location?
    /// Server address to its location, for the map pin and the server's flag.
    @Published private(set) var serverLocations: [String: GeoIP.Location] = [:]

    @Published private(set) var diagnostics: [DiagnosticStep] = []
    @Published private(set) var diagnosticsRunning = false

    enum InternetCheck: Equatable {
        case idle, running
        case ok(ms: Int)
        case failed(String)
    }

    let vpn = VPNManager()

    init() {
        if !state.subscriptionURL.isEmpty {
            Task { await refreshSubscription(silent: true) }
        }
        Task { await checkForUpdate(manual: false) }
    }

    /// The automatic check runs at most every 12 hours and stays quiet about a version the user
    /// already put off; the button in settings always asks GitHub and reports the result.
    func checkForUpdate(manual: Bool) async {
        let defaults = UserDefaults.standard
        let now = Date().timeIntervalSince1970
        if !manual, now - defaults.double(forKey: "updateCheckedAt") < 12 * 3600 { return }
        let release: Updates.Release
        do {
            release = try await Updates.fetchLatest()
        } catch {
            if manual { message = "Не удалось проверить обновления: \(error.localizedDescription)" }
            return
        }
        defaults.set(now, forKey: "updateCheckedAt")
        if !Updates.isNewer(release.version, than: AppSettings.shortVersion) {
            if manual { message = "У вас последняя версия" }
        } else if manual || defaults.string(forKey: "updateDismissed") != release.version {
            update = release
        }
    }

    /// "Later": the automatic check does not offer this version again.
    func dismissUpdate() {
        if let update { UserDefaults.standard.set(update.version, forKey: "updateDismissed") }
        update = nil
    }

    /// Looks up the address sites see. With the VPN off that is the phone's real location; while
    /// connected the request goes through the tunnel and shows the server's.
    func refreshLocation(connected: Bool) async {
        let location = await GeoIP.lookup()
        // The status may have changed while the request was out; a stale answer would put the pin in the wrong place.
        guard connected == (vpn.status == .connected) else { return }
        visibleLocation = location
        if !connected, let location { homeLocation = location }
    }

    /// Location of the selected server, looked up once per address.
    func locateSelectedServer() async {
        guard let address = state.selected?.address, serverLocations[address] == nil else { return }
        let ip = Self.isIPAddress(address) ? address : await Self.resolve(address)
        guard let ip, let location = await GeoIP.lookup(ip) else { return }
        serverLocations[address] = location
    }

    var selectedServerLocation: GeoIP.Location? {
        state.selected.flatMap { serverLocations[$0.address] }
    }

    /// Restarts a running tunnel so a changed split tunneling setting takes effect.
    func applyTunnelSettings() async {
        if let profile = state.selected { await vpn.reconnect(profile) }
    }

    func toggle() async {
        if vpn.isActive {
            vpn.disconnect()
        } else if let profile = state.selected {
            await vpn.connect(profile)
        } else {
            message = "Сначала добавьте сервер"
        }
    }

    func select(_ id: String) async {
        update { $0.selectedId = id }
        if let profile = state.selected { await vpn.reconnect(profile) }
    }

    func remove(_ id: String) {
        update { s in
            s.profiles.removeAll { $0.id == id }
            if s.selectedId == id { s.selectedId = nil }
        }
    }

    /// Accepts a vless:// key, several keys, or a subscription URL.
    func addInput(_ text: String) async {
        if DeepLink.wantsClipboard(text) {
            // Opened by the bot's "copy the link, then open" page: offer what was copied.
            await offerClipboard(force: true)
            return
        }
        let input = DeepLink.unwrap(text)
        let lower = input.lowercased()
        if lower.hasPrefix("https://") || lower.hasPrefix("http://") {
            update { $0.subscriptionURL = input }
            await refreshSubscription(silent: false)
            return
        }
        let parsed = Subscription.parseLinks(input)
        guard !parsed.isEmpty else {
            message = "Не удалось распознать ключ"
            return
        }
        update { s in
            s.profiles += parsed.map { StoredProfile(profile: $0, fromSubscription: false) }
            if s.selectedId == nil { s.selectedId = parsed.first?.id }
        }
        message = "Добавлено серверов: \(parsed.count)"
    }

    /// Offers to add what is in the clipboard: always after the bot's link (`force`), otherwise
    /// only while there are no servers yet and only once per clipboard content. Nothing is read
    /// until the user agrees, so iOS shows its paste prompt only then.
    func offerClipboard(force: Bool) async {
        let board = UIPasteboard.general
        guard board.hasStrings || board.hasURLs else {
            if force { message = "В буфере обмена нет ссылки. Скопируйте подписку в боте и откройте ссылку ещё раз." }
            return
        }
        if !force {
            let key = "clipboardOfferedChange"
            guard state.profiles.isEmpty, UserDefaults.standard.integer(forKey: key) != board.changeCount else { return }
            // Checks for a link without reading the text, so iOS shows no paste prompt here.
            let found = await withCheckedContinuation { (done: CheckedContinuation<Bool, Never>) in
                board.detectPatterns(for: [\UIPasteboard.DetectedValues.probableWebURL]) { result in
                    done.resume(returning: (try? result.get())?.isEmpty == false)
                }
            }
            guard board.hasURLs || found else { return }
            UserDefaults.standard.set(board.changeCount, forKey: key)
        }
        clipboardOffer = true
    }

    func addFromClipboard() async {
        guard let pasted = UIPasteboard.general.string ?? UIPasteboard.general.url?.absoluteString, !pasted.isEmpty else {
            message = "В буфере обмена нет ссылки"
            return
        }
        await addInput(pasted)
    }

    /// Replaces subscription servers with a fresh copy, keeping the selection when the server still exists.
    func refreshSubscription(silent: Bool = false) async {
        guard let url = URL(string: state.subscriptionURL) else { return }
        busy = true
        defer { busy = false }
        do {
            let result = try await Subscription.fetch(url, headers: DeviceInfo.headers)
            update { s in
                let oldIds = Dictionary(
                    s.profiles.filter(\.fromSubscription).map { ($0.profile.link, $0.id) },
                    uniquingKeysWith: { first, _ in first }
                )
                let fresh = result.profiles.map { p -> StoredProfile in
                    var p = p
                    if let id = oldIds[p.link] { p.id = id }
                    return StoredProfile(profile: p, fromSubscription: true)
                }
                s.profiles = fresh + s.profiles.filter { !$0.fromSubscription }
                if !s.profiles.contains(where: { $0.id == s.selectedId }) { s.selectedId = s.profiles.first?.id }
                s.subscriptionInfo = result.info
            }
            if !silent { message = "Подписка обновлена" }
        } catch {
            if !silent { message = "Ошибка подписки: \(error.localizedDescription)" }
        }
    }

    func removeSubscription() {
        update { s in
            s.profiles.removeAll(where: \.fromSubscription)
            s.subscriptionURL = ""
            s.subscriptionInfo = nil
            if !s.profiles.contains(where: { $0.id == s.selectedId }) { s.selectedId = nil }
        }
    }

    func pingAll() async {
        let profiles = state.profiles.map(\.profile)
        var results: [String: Int] = [:]
        await withTaskGroup(of: (String, Int).self) { group in
            for p in profiles {
                group.addTask { (p.id, await Self.tcpPing(host: p.address, port: p.port)) }
            }
            for await (id, ms) in group { results[id] = ms }
        }
        pings = results
    }

    /// Requests a tiny page through the tunnel: tells "VPN is up" apart from "traffic actually flows".
    func checkInternet() async {
        internetCheck = .running
        switch await Self.probeInternet() {
        case .success(let ms): internetCheck = .ok(ms: ms)
        case .failure(let reason): internetCheck = .failed(reason.message)
        }
    }

    /// Runs the checklist on the Servers tab step by step; nothing leaves the app.
    func runDiagnostics() async {
        guard !diagnosticsRunning else { return }
        guard let profile = state.selected else {
            message = "Сначала добавьте сервер"
            return
        }
        diagnosticsRunning = true
        defer { diagnosticsRunning = false }
        diagnostics = DiagnosticStep.Kind.allCases.map { DiagnosticStep(kind: $0) }
        set(.key, .ok(profile.summary))

        // 1. The server answers on its port, checked outside the tunnel.
        set(.server, .running)
        switch await Self.tcpProbe(host: profile.address, port: profile.port) {
        case .success(let ms): set(.server, .ok("\(profile.address):\(profile.port) · \(ms) мс"))
        case .failure(let e): set(.server, .failed("\(profile.address):\(profile.port) — \(e.message)"))
        }

        // 2. The tunnel is up.
        set(.vpn, .running)
        guard vpn.status == .connected else {
            set(.vpn, .failed("VPN не подключён — нажмите кнопку на главной"))
            skipRemaining()
            return
        }
        set(.vpn, .ok(profile.name))

        // 3. Names resolve through the tunnel.
        set(.dns, .running)
        if let ip = await Self.resolve("www.gstatic.com") {
            set(.dns, .ok("www.gstatic.com → \(ip)"))
        } else {
            set(.dns, .failed("адреса сайтов не определяются через VPN"))
            skipRemaining()
            return
        }

        // 4. A real HTTPS request goes through.
        set(.internet, .running)
        switch await Self.probeInternet() {
        case .success(let ms): set(.internet, .ok("ответ за \(ms) мс"))
        case .failure(let reason):
            set(.internet, .failed(reason.message))
            skipRemaining()
            return
        }

        // 5. Sites see the server's address, so traffic really exits there.
        set(.exitIP, .running)
        guard let exitIP = await Self.fetchText("https://api.ipify.org") else {
            set(.exitIP, .failed("не удалось узнать внешний IP"))
            return
        }
        var serverIP: String? = profile.address
        if !Self.isIPAddress(profile.address) { serverIP = await Self.resolve(profile.address) }
        set(.exitIP, exitIP == serverIP
            ? .ok("внешний IP \(exitIP) — ваш сервер")
            : .warning("внешний IP \(exitIP), сервер \(serverIP ?? profile.address)"))
    }

    private func set(_ kind: DiagnosticStep.Kind, _ state: DiagnosticStep.State) {
        if let i = diagnostics.firstIndex(where: { $0.kind == kind }) { diagnostics[i].state = state }
    }

    private func skipRemaining() {
        for i in diagnostics.indices where diagnostics[i].state == .pending { diagnostics[i].state = .skipped }
    }

    func resetInternetCheck() {
        internetCheck = .idle
    }

    private func update(_ change: (inout ProfilesState) -> Void) {
        change(&state)
        ProfileStore.save(state)
    }

    private struct ProbeError: Error {
        let message: String
    }

    private nonisolated static func session() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 10
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        return URLSession(configuration: config)
    }

    private nonisolated static func probeInternet() async -> Result<Int, ProbeError> {
        let start = Date()
        do {
            let (_, response) = try await session().data(from: URL(string: "https://www.gstatic.com/generate_204")!)
            let code = (response as? HTTPURLResponse)?.statusCode ?? 0
            guard (200..<400).contains(code) else { return .failure(ProbeError(message: "HTTP \(code)")) }
            return .success(Int(Date().timeIntervalSince(start) * 1000))
        } catch let error as URLError {
            switch error.code {
            case .cannotFindHost, .dnsLookupFailed: return .failure(ProbeError(message: "не работает DNS через VPN"))
            case .timedOut: return .failure(ProbeError(message: "нет ответа — трафик не проходит через сервер"))
            default: return .failure(ProbeError(message: error.localizedDescription))
            }
        } catch {
            return .failure(ProbeError(message: error.localizedDescription))
        }
    }

    private nonisolated static func fetchText(_ url: String) async -> String? {
        guard let url = URL(string: url), let result = try? await session().data(from: url) else { return nil }
        return String(decoding: result.0, as: UTF8.self).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private nonisolated static func isIPAddress(_ s: String) -> Bool {
        IPv4Address(s) != nil || IPv6Address(s) != nil
    }

    /// First IPv4 address of [host], or nil after 5 s.
    private nonisolated static func resolve(_ host: String) async -> String? {
        await withCheckedContinuation { continuation in
            let once = OnceFlag()
            let finish: (String?) -> Void = { value in
                if once.set() { continuation.resume(returning: value) }
            }
            DispatchQueue.global().async {
                var hints = addrinfo()
                hints.ai_family = AF_INET
                hints.ai_socktype = SOCK_STREAM
                var result: UnsafeMutablePointer<addrinfo>?
                guard getaddrinfo(host, nil, &hints, &result) == 0, let addr = result?.pointee.ai_addr else {
                    finish(nil)
                    return
                }
                defer { freeaddrinfo(result) }
                var buf = [CChar](repeating: 0, count: Int(INET_ADDRSTRLEN))
                addr.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { sin in
                    var a = sin.pointee.sin_addr
                    _ = inet_ntop(AF_INET, &a, &buf, socklen_t(buf.count))
                }
                finish(String(cString: buf))
            }
            DispatchQueue.global().asyncAfter(deadline: .now() + 5) { finish(nil) }
        }
    }

    /// TCP connect time to the server in ms, or -1. See `tcpProbe` for the reason of a failure.
    private nonisolated static func tcpPing(host: String, port: Int) async -> Int {
        if case .success(let ms) = await tcpProbe(host: host, port: port) { return ms }
        return -1
    }

    /// Connects straight to the server over Wi-Fi or cellular. Through the tunnel the local TUN stack
    /// would accept every connection instantly, so the result would mean nothing.
    private nonisolated static func tcpProbe(host: String, port: Int) async -> Result<Int, ProbeError> {
        guard let nwPort = NWEndpoint.Port(rawValue: UInt16(port)) else { return .failure(ProbeError(message: "неверный порт")) }
        let parameters = NWParameters.tcp
        if let physical = await physicalInterface() { parameters.requiredInterface = physical }
        let connection = NWConnection(host: NWEndpoint.Host(host), port: nwPort, using: parameters)
        let start = Date()
        return await withCheckedContinuation { continuation in
            let once = OnceFlag()
            let finish: (Result<Int, ProbeError>) -> Void = { value in
                guard once.set() else { return }
                connection.cancel()
                continuation.resume(returning: value)
            }
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready: finish(.success(Int(Date().timeIntervalSince(start) * 1000)))
                case .failed(let error): finish(.failure(ProbeError(message: error.localizedDescription)))
                case .waiting(let error):
                    // Waiting means no route yet; give it the full timeout but remember why.
                    DispatchQueue.global().asyncAfter(deadline: .now() + 5) {
                        finish(.failure(ProbeError(message: error.localizedDescription)))
                    }
                default: break
                }
            }
            connection.start(queue: .global())
            DispatchQueue.global().asyncAfter(deadline: .now() + 6) { finish(.failure(ProbeError(message: "таймаут 6 с"))) }
        }
    }

    /// The Wi-Fi or cellular interface currently in use, skipping the VPN's utun.
    private nonisolated static func physicalInterface() async -> NWInterface? {
        await withCheckedContinuation { continuation in
            let monitor = NWPathMonitor()
            let once = OnceFlag()
            monitor.pathUpdateHandler = { path in
                guard once.set() else { return }
                monitor.cancel()
                let preferred: [NWInterface.InterfaceType] = [.wifi, .cellular, .wiredEthernet]
                let found = preferred.lazy.compactMap { type in path.availableInterfaces.first { $0.type == type } }.first
                continuation.resume(returning: found)
            }
            monitor.start(queue: .global())
        }
    }
}

/// Lets the ping continuation resume exactly once across racing callbacks.
private final class OnceFlag: @unchecked Sendable {
    private let lock = NSLock()
    private var done = false

    func set() -> Bool {
        lock.lock()
        defer { lock.unlock() }
        if done { return false }
        done = true
        return true
    }
}

struct DiagnosticStep: Identifiable, Equatable {
    enum Kind: CaseIterable {
        case key, server, vpn, dns, internet, exitIP

        var title: String {
            switch self {
            case .key: return "Параметры ключа"
            case .server: return "Сервер доступен"
            case .vpn: return "VPN подключён"
            case .dns: return "DNS работает через VPN"
            case .internet: return "Интернет через VPN"
            case .exitIP: return "Трафик идёт через сервер"
            }
        }
    }

    enum State: Equatable {
        case pending, running, skipped
        case ok(String)
        case warning(String)
        case failed(String)
    }

    let kind: Kind
    var state: State = .pending
    var id: Kind { kind }
}

/// What panels with per-device keys (HWID binding) expect from a client, as Happ and v2RayTun send it.
enum DeviceInfo {
    static var headers: [String: String] {
        let os = ProcessInfo.processInfo.operatingSystemVersion
        return [
            "x-hwid": hwid,
            "x-device-os": "iOS",
            "x-ver-os": "\(os.majorVersion).\(os.minorVersion).\(os.patchVersion)",
            "x-device-model": model,
        ]
    }

    /// Stable per install, so the panel keeps treating this phone as the same device.
    static var hwid: String {
        let key = "deviceHWID"
        if let saved = UserDefaults.standard.string(forKey: key) { return saved }
        let fresh = UUID().uuidString.lowercased()
        UserDefaults.standard.set(fresh, forKey: key)
        return fresh
    }

    /// Hardware identifier such as "iPhone16,2".
    private static var model: String {
        var info = utsname()
        uname(&info)
        return withUnsafeBytes(of: &info.machine) { String(decoding: $0.prefix { $0 != 0 }, as: UTF8.self) }
    }
}
