import LibXray
import NetworkExtension
import os

final class PacketTunnelProvider: NEPacketTunnelProvider {
    private let log = Logger(subsystem: Bundle.main.bundleIdentifier ?? "tunnel", category: "tunnel")
    /// Shared with the app when the App Group is really writable, otherwise private to the
    /// extension; the app can always fetch it through `handleAppMessage`.
    private let logURL = PacketTunnelProvider.writableLogURL()
    /// Our own lines, kept in memory so the app gets something even if no file can be written.
    private var notes: [String] = []

    private static func writableLogURL() -> URL {
        let candidates = [AppGroup.logURL, FileManager.default.temporaryDirectory.appendingPathComponent("xray.log")]
        for case let url? in candidates where FileManager.default.createFile(atPath: url.path, contents: Data()) {
            return url
        }
        return FileManager.default.temporaryDirectory.appendingPathComponent("xray.log")
    }

    override func startTunnel(options: [String: NSObject]?) async throws {
        // The app passes the link explicitly; on-demand restarts fall back to the stored selection.
        let link = options?["link"] as? String
        guard let profile = link.flatMap(VlessProfile.parse) ?? ProfileStore.load().selected else {
            throw TunnelError.noServer
        }

        appendLog("TechTeamVPN: \(profile.summary)")
        appendLog("TechTeamVPN: log \(logURL.path), App Group \(AppGroup.logURL == nil ? "нет в профиле подписи" : "есть")")
        do {
            try await setTunnelNetworkSettings(networkSettings(for: profile))
            guard let fd = tunnelFileDescriptor else { throw TunnelError.noTunDescriptor }
            appendLog("TechTeamVPN: utun fd \(fd), starting Xray")
            let config = try XrayConfig.build(profile, tunFd: fd, logPath: logURL.path)
            try XrayCore.run(config)
            appendLog("TechTeamVPN: Xray started")
            log.info("Xray started for \(profile.name, privacy: .public)")
        } catch {
            appendLog("TechTeamVPN: start failed: \(error.localizedDescription)")
            throw error
        }
    }

    /// The app sends "log" and gets the last 64 KB of the log back.
    override func handleAppMessage(_ messageData: Data) async -> Data? {
        guard String(decoding: messageData, as: UTF8.self) == "log" else { return nil }
        var text = notes.joined(separator: "\n") + "\n— Xray —\n"
        do {
            let handle = try FileHandle(forReadingFrom: logURL)
            defer { try? handle.close() }
            let size = handle.seekToEndOfFile()
            handle.seek(toFileOffset: size > 65_536 ? size - 65_536 : 0)
            text += String(decoding: handle.readDataToEndOfFile(), as: UTF8.self)
        } catch {
            text += "файл журнала не читается: \(error.localizedDescription)"
        }
        return Data(text.utf8)
    }

    private func appendLog(_ line: String) {
        notes.append(line)
        guard let handle = try? FileHandle(forWritingTo: logURL) else { return }
        handle.seekToEndOfFile()
        handle.write(Data((line + "\n").utf8))
        try? handle.close()
    }

    override func stopTunnel(with reason: NEProviderStopReason) async {
        log.info("stopping tunnel, reason \(reason.rawValue)")
        XrayCore.stop()
    }

    private func networkSettings(for profile: VlessProfile) -> NEPacketTunnelNetworkSettings {
        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: profile.address)
        settings.mtu = NSNumber(value: XrayConfig.tunMTU)

        let ipv4 = NEIPv4Settings(addresses: ["172.19.0.1"], subnetMasks: ["255.255.255.252"])
        ipv4.includedRoutes = [NEIPv4Route.default()]
        settings.ipv4Settings = ipv4

        let ipv6 = NEIPv6Settings(addresses: ["fdfe:dcba:9876::1"], networkPrefixLengths: [126])
        ipv6.includedRoutes = [NEIPv6Route.default()]
        settings.ipv6Settings = ipv6

        let dns = NEDNSSettings(servers: XrayConfig.dnsServers)
        dns.matchDomains = [""]
        settings.dnsSettings = dns
        return settings
    }

    /// NetworkExtension does not expose the utun descriptor, so find the open socket whose
    /// control name is utunN (SYSPROTO_CONTROL = 2, UTUN_OPT_IFNAME = 2).
    private var tunnelFileDescriptor: Int32? {
        var buf = [CChar](repeating: 0, count: Int(IFNAMSIZ))
        let prefix = Array("utun".utf8CString.dropLast())
        for fd: Int32 in 0...1024 {
            var len = socklen_t(buf.count)
            if getsockopt(fd, 2, 2, &buf, &len) == 0, buf.starts(with: prefix) {
                return fd
            }
        }
        return nil
    }
}

enum TunnelError: LocalizedError {
    case noServer
    case noTunDescriptor
    case xray(String)

    var errorDescription: String? {
        switch self {
        case .noServer: return "Сервер не выбран"
        case .noTunDescriptor: return "Не удалось получить дескриптор туннеля"
        case .xray(let message): return message
        }
    }
}

/// Thin wrapper over libXray's JSON `invoke` API (apiVersion 3).
enum XrayCore {
    static func run(_ config: String) throws {
        try call("runXray", payload: ["xrayJson": config])
    }

    static func stop() {
        _ = try? call("stopXray", payload: nil)
    }

    @discardableResult
    private static func call(_ method: String, payload: [String: Any]?) throws -> [String: Any]? {
        var request: [String: Any] = ["apiVersion": 3, "method": method]
        if let payload { request["payload"] = payload }
        let data = try JSONSerialization.data(withJSONObject: request)
        let response = LibXrayInvoke(String(decoding: data, as: UTF8.self))
        guard let json = try JSONSerialization.jsonObject(with: Data(response.utf8)) as? [String: Any] else {
            throw TunnelError.xray("Xray: invalid response")
        }
        guard json["success"] as? Bool == true else {
            throw TunnelError.xray((json["error"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? "Xray: \(method) failed")
        }
        return json["data"] as? [String: Any]
    }
}
