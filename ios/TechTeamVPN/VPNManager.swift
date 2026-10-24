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

    func connect(_ profile: VlessProfile) async {
        lastError = nil
        do {
            let manager = try await preparedManager(serverAddress: profile.address)
            try manager.connection.startVPNTunnel(options: ["link": profile.link as NSString])
        } catch {
            lastError = error.localizedDescription
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
