import Combine
import NetworkExtension

/// Owns the NETunnelProviderManager that points at the PacketTunnel extension.
@MainActor
final class VPNManager: ObservableObject {
    @Published private(set) var status: NEVPNStatus = .invalid
    @Published private(set) var connectedDate: Date?
    @Published var lastError: String?

    private var manager: NETunnelProviderManager?
    private var observer: NSObjectProtocol?

    init() {
        Task { await load() }
    }

    var isActive: Bool { [.connected, .connecting, .reasserting].contains(status) }

    func load() async {
        let managers = (try? await NETunnelProviderManager.loadAllFromPreferences()) ?? []
        let manager = managers.first ?? NETunnelProviderManager()
        self.manager = manager
        observe(manager)
        refresh()
    }

    func connect(_ profile: VlessProfile, diagnose: Bool = false) async {
        lastError = nil
        do {
            let manager = try await preparedManager(serverAddress: profile.address)
            var options: [String: NSObject] = ["link": profile.link as NSString]
            if diagnose { options["diagnose"] = "1" as NSString }
            // The detailed Xray log is only kept in developer mode.
            if UserDefaults.standard.bool(forKey: AppSettings.devModeKey) { options["verbose"] = "1" as NSString }
            try manager.connection.startVPNTunnel(options: options)
        } catch {
            lastError = error.localizedDescription
        }
    }

    /// Asks the running tunnel for its log; works without App Groups in the signing profile.
    func fetchTunnelLog() async -> String? {
        guard let session = manager?.connection as? NETunnelProviderSession, isActive else { return nil }
        return await withCheckedContinuation { continuation in
            do {
                try session.sendProviderMessage(Data("log".utf8)) { data in
                    continuation.resume(returning: data.map { String(decoding: $0, as: UTF8.self) })
                }
            } catch {
                continuation.resume(returning: nil)
            }
        }
    }

    /// Runs the tunnel for 20 s in diagnostic mode and returns the log it hands back as its
    /// disconnect error, or nil when the tunnel died without reporting.
    func recordLog(_ profile: VlessProfile) async -> String? {
        if isActive {
            disconnect()
            for _ in 0..<50 where status != .disconnected { try? await Task.sleep(nanoseconds: 100_000_000) }
        }
        await connect(profile, diagnose: true)
        var wasActive = false
        for _ in 0..<600 {
            if isActive { wasActive = true } else if wasActive || lastError != nil { break }
            try? await Task.sleep(nanoseconds: 100_000_000)
        }
        guard let connection = manager?.connection else { return lastError }
        return await withCheckedContinuation { continuation in
            connection.fetchLastDisconnectError { error in
                continuation.resume(returning: error.map { ($0 as NSError).localizedDescription })
            }
        }
    }

    func disconnect() {
        manager?.connection.stopVPNTunnel()
    }

    /// Restarts the tunnel when it is running, so a newly selected server takes effect.
    func reconnect(_ profile: VlessProfile) async {
        guard isActive else { return }
        disconnect()
        for _ in 0..<50 where status != .disconnected {
            try? await Task.sleep(nanoseconds: 100_000_000)
        }
        await connect(profile)
    }

    private func preparedManager(serverAddress: String) async throws -> NETunnelProviderManager {
        let manager = manager ?? NETunnelProviderManager()
        let proto = (manager.protocolConfiguration as? NETunnelProviderProtocol) ?? NETunnelProviderProtocol()
        proto.providerBundleIdentifier = AppGroup.tunnelBundleId
        proto.serverAddress = serverAddress
        proto.disconnectOnSleep = false
        // Saved with the configuration, so the tunnel also sees it when iOS restarts it on its own.
        proto.providerConfiguration = ["russiaDirect": AppSettings.russiaDirect]
        manager.protocolConfiguration = proto
        manager.localizedDescription = "TechTeamVPN"
        manager.isEnabled = true
        // The first save shows the system "Add VPN configuration" prompt.
        try await manager.saveToPreferences()
        try await manager.loadFromPreferences()
        self.manager = manager
        observe(manager)
        return manager
    }

    private func observe(_ manager: NETunnelProviderManager) {
        if let observer { NotificationCenter.default.removeObserver(observer) }
        observer = NotificationCenter.default.addObserver(
            forName: .NEVPNStatusDidChange, object: manager.connection, queue: .main
        ) { [weak self] _ in
            Task { @MainActor in self?.refresh() }
        }
    }

    private func refresh() {
        guard let connection = manager?.connection else { return }
        status = connection.status
        connectedDate = connection.status == .connected ? connection.connectedDate : nil
        if connection.status == .disconnected {
            connection.fetchLastDisconnectError { [weak self] error in
                guard let error else { return }
                Task { @MainActor in self?.lastError = error.localizedDescription }
            }
        }
    }
}
