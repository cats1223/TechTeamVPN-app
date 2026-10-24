import Foundation
import Network

@MainActor
final class AppModel: ObservableObject {
    @Published private(set) var state = ProfileStore.load()
    /// Profile id to TCP connect time in ms; -1 means unreachable.
    @Published private(set) var pings: [String: Int] = [:]
    @Published private(set) var busy = false
    @Published var message: String?
    @Published private(set) var internetCheck: InternetCheck = .idle

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
        let input = text.trimmingCharacters(in: .whitespacesAndNewlines)
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

    /// Replaces subscription servers with a fresh copy, keeping the selection when the server still exists.
    func refreshSubscription(silent: Bool = false) async {
        guard let url = URL(string: state.subscriptionURL) else { return }
        busy = true
        defer { busy = false }
        do {
            let result = try await Subscription.fetch(url)
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
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 10
        config.requestCachePolicy = .reloadIgnoringLocalCacheData
        let session = URLSession(configuration: config)
        let start = Date()
        do {
            let (_, response) = try await session.data(from: URL(string: "https://www.gstatic.com/generate_204")!)
            let code = (response as? HTTPURLResponse)?.statusCode ?? 0
            internetCheck = (200..<400).contains(code)
                ? .ok(ms: Int(Date().timeIntervalSince(start) * 1000))
                : .failed("HTTP \(code)")
        } catch let error as URLError {
            switch error.code {
            case .cannotFindHost, .dnsLookupFailed: internetCheck = .failed("не работает DNS через VPN")
            case .timedOut: internetCheck = .failed("нет ответа (таймаут) — трафик не проходит через сервер")
            default: internetCheck = .failed(error.localizedDescription)
            }
        } catch {
            internetCheck = .failed(error.localizedDescription)
        }
    }

    func resetInternetCheck() {
        internetCheck = .idle
    }

    private func update(_ change: (inout ProfilesState) -> Void) {
        change(&state)
        ProfileStore.save(state)
    }

    private nonisolated static func tcpPing(host: String, port: Int) async -> Int {
        guard let nwPort = NWEndpoint.Port(rawValue: UInt16(port)) else { return -1 }
        let connection = NWConnection(host: NWEndpoint.Host(host), port: nwPort, using: .tcp)
        let start = Date()
        return await withCheckedContinuation { continuation in
            let once = OnceFlag()
            let finish: (Int) -> Void = { value in
                guard once.set() else { return }
                connection.cancel()
                continuation.resume(returning: value)
            }
            connection.stateUpdateHandler = { state in
                switch state {
                case .ready: finish(Int(Date().timeIntervalSince(start) * 1000))
                case .failed, .cancelled: finish(-1)
                default: break
                }
            }
            connection.start(queue: .global())
            DispatchQueue.global().asyncAfter(deadline: .now() + 3) { finish(-1) }
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
