import Foundation

/// Builds the Xray-core JSON that libXray runs inside the packet tunnel.
enum XrayConfig {
    static let tunMTU = 1500
    /// What the system resolver is pointed at; Xray hijacks these queries.
    static let dnsServers = ["1.1.1.1", "8.8.8.8"]
    /// Upstreams Xray resolves through the proxy. DoH rides a TCP stream, so DNS does not depend on UDP via the server.
    static let upstreamDNS = ["https://1.1.1.1/dns-query", "https://8.8.8.8/dns-query"]

    private static let privateCIDRs = [
        "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8", "169.254.0.0/16",
        "100.64.0.0/10", "fc00::/7", "fe80::/10", "::1/128",
    ]

    /// - Parameter tunFd: the utun descriptor; Xray reads it from the `xray.tun.fd` env key.
    ///   Pass nil to build a config for validation only.
    /// - Parameter logPath: file for Xray's error log at info level, shown in the app's connection log.
    static func build(_ profile: VlessProfile, tunFd: Int32?, logPath: String? = nil) throws -> String {
        var root: [String: Any] = [
            "log": logPath.map { ["loglevel": "info", "access": "none", "error": $0] } ?? ["loglevel": "warning"],
            "dns": ["servers": upstreamDNS, "queryStrategy": "UseIPv4"],
            "inbounds": [[
                "tag": "tun-in",
                "protocol": "tun",
                "settings": ["name": "xray0", "mtu": tunMTU],
                "sniffing": ["enabled": true, "destOverride": ["http", "tls", "quic"], "routeOnly": true],
            ]],
            "outbounds": [
                outbound(profile),
                ["tag": "direct", "protocol": "freedom"],
                ["tag": "block", "protocol": "blackhole"],
                ["tag": "dns-out", "protocol": "dns"],
            ],
            "routing": [
                "domainStrategy": "AsIs",
                "rules": [
                    ["inboundTag": ["tun-in"], "port": "53", "outboundTag": "dns-out"],
                    ["ip": privateCIDRs, "outboundTag": "direct"],
                ],
            ],
        ]
        if let tunFd {
            root["env"] = ["xray.tun.fd": String(tunFd)]
        }
        let data = try JSONSerialization.data(withJSONObject: root, options: [.sortedKeys])
        return String(decoding: data, as: UTF8.self)
    }

    static func outbound(_ p: VlessProfile) -> [String: Any] {
        var settings: [String: Any] = [
            "address": p.address,
            "port": p.port,
            "id": p.uuid,
            "encryption": p.encryption,
        ]
        if !p.flow.isEmpty { settings["flow"] = p.flow }
        return [
            "tag": "proxy",
            "protocol": "vless",
            "settings": settings,
            "streamSettings": streamSettings(p),
        ]
    }

    private static func streamSettings(_ p: VlessProfile) -> [String: Any] {
        var s: [String: Any] = ["network": p.network, "security": p.security]
        let serverName = p.sni.isEmpty ? p.host : p.sni

        switch p.security {
        case "reality":
            var r: [String: Any] = [
                "serverName": serverName,
                "fingerprint": p.fingerprint.isEmpty ? "chrome" : p.fingerprint,
                "password": p.publicKey,
                "shortId": p.shortId,
            ]
            r.putIfNotEmpty("spiderX", p.spiderX)
            r.putIfNotEmpty("mldsa65Verify", p.mldsa65Verify)
            s["realitySettings"] = r
        case "tls":
            var t: [String: Any] = [:]
            t.putIfNotEmpty("serverName", serverName)
            t.putIfNotEmpty("fingerprint", p.fingerprint)
            if !p.alpn.isEmpty { t["alpn"] = p.alpn }
            t.putIfNotEmpty("pinnedPeerCertSha256", p.pinnedPeerCertSha256)
            t.putIfNotEmpty("verifyPeerCertByName", p.verifyPeerCertByName)
            t.putIfNotEmpty("echConfigList", p.echConfigList)
            s["tlsSettings"] = t
        default:
            break
        }

        let path = p.path.isEmpty ? "/" : p.path
        switch p.network {
        case "ws", "httpupgrade":
            var w: [String: Any] = ["path": path]
            w.putIfNotEmpty("host", p.host)
            s[p.network == "ws" ? "wsSettings" : "httpupgradeSettings"] = w
        case "grpc":
            var g: [String: Any] = ["serviceName": p.serviceName, "multiMode": p.mode == "multi"]
            g.putIfNotEmpty("authority", p.authority)
            s["grpcSettings"] = g
        case "xhttp":
            var x: [String: Any] = ["path": path]
            x.putIfNotEmpty("host", p.host)
            x.putIfNotEmpty("mode", p.mode)
            if let data = p.extra.data(using: .utf8),
               let extra = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                x.merge(extra) { _, new in new }
            }
            s["xhttpSettings"] = x
        case "tcp" where p.headerType == "http":
            var request: [String: Any] = ["path": [path]]
            if !p.host.isEmpty {
                request["headers"] = ["Host": p.host.split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }]
            }
            s["tcpSettings"] = ["header": ["type": "http", "request": request]]
        default:
            break
        }
        return s
    }
}

extension VlessProfile {
    /// Connection parameters without the uuid and keys, safe to show in the log.
    var summary: String {
        var parts = ["\(address):\(port)", "type=\(network)", "security=\(security)"]
        for (key, value) in [("sni", sni), ("fp", fingerprint), ("flow", flow), ("mode", mode),
                             ("path", path), ("host", host), ("sid", shortId.isEmpty ? "" : "set"),
                             ("pbk", publicKey.isEmpty ? "" : "set"), ("extra", extra.isEmpty ? "" : "set")]
        where !value.isEmpty {
            parts.append("\(key)=\(value)")
        }
        return parts.joined(separator: " ")
    }
}

private extension Dictionary where Key == String, Value == Any {
    mutating func putIfNotEmpty(_ key: String, _ value: String) {
        if !value.isEmpty { self[key] = value }
    }
}
