import Foundation

/// A VLESS server parsed from a share link:
/// vless://<uuid>@<host>:<port>?type=tcp&security=reality&pbk=...&sid=...&sni=...&fp=chrome&flow=xtls-rprx-vision#Name
struct VlessProfile: Codable, Identifiable, Hashable {
    var id = UUID().uuidString
    var name: String
    var address: String
    var port: Int
    var uuid: String
    var encryption = "none"
    var flow = ""
    var network = "tcp"
    var security = "none"
    var sni = ""
    var fingerprint = ""
    var publicKey = ""
    var shortId = ""
    var spiderX = ""
    var mldsa65Verify = ""
    var alpn: [String] = []
    var pinnedPeerCertSha256 = ""
    var verifyPeerCertByName = ""
    var echConfigList = ""
    var host = ""
    var path = ""
    var serviceName = ""
    var authority = ""
    var mode = ""
    var headerType = ""
    var extra = ""
    /// The original link, kept so the profile can be shared or re-parsed.
    var link = ""

    /// Returns nil for anything that is not a well-formed vless:// link.
    static func parse(_ raw: String) -> VlessProfile? {
        let text = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard text.lowercased().hasPrefix("vless://") else { return nil }

        // Names often hold raw spaces and emoji, so cut the fragment off before URL parsing.
        var body = text
        var fragment: String?
        if let hash = text.firstIndex(of: "#") {
            body = String(text[..<hash])
            fragment = String(text[text.index(after: hash)...])
        }
        body = body.replacingOccurrences(of: " ", with: "%20")
        guard let comps = URLComponents(string: body),
              let uuid = comps.percentEncodedUser.map(decode), !uuid.isEmpty,
              var host = comps.host, !host.isEmpty,
              let port = comps.port, (1...65535).contains(port)
        else { return nil }
        host = host.trimmingCharacters(in: CharacterSet(charactersIn: "[]"))

        var q: [String: String] = [:]
        for pair in (comps.percentEncodedQuery ?? "").split(separator: "&") {
            let parts = pair.split(separator: "=", maxSplits: 1, omittingEmptySubsequences: false)
            q[decode(String(parts[0]))] = parts.count > 1 ? decode(String(parts[1])) : ""
        }

        var network = (q["type"] ?? "").lowercased()
        if network.isEmpty || network == "raw" { network = "tcp" }
        if network == "splithttp" { network = "xhttp" }

        let decodedName = fragment.map { $0.removingPercentEncoding ?? $0 }?
            .trimmingCharacters(in: .whitespaces) ?? ""

        var p = VlessProfile(
            name: decodedName.isEmpty ? "\(host):\(port)" : decodedName,
            address: host, port: port, uuid: uuid
        )
        p.encryption = q["encryption"].flatMap { $0.isEmpty ? nil : $0 } ?? "none"
        p.flow = q["flow"] ?? ""
        p.network = network
        p.security = q["security"].flatMap { $0.isEmpty ? nil : $0.lowercased() } ?? "none"
        p.sni = q["sni"] ?? q["peer"] ?? ""
        p.fingerprint = q["fp"] ?? ""
        p.publicKey = q["pbk"] ?? ""
        p.shortId = q["sid"] ?? ""
        p.spiderX = q["spx"] ?? ""
        p.mldsa65Verify = q["pqv"] ?? ""
        p.alpn = (q["alpn"] ?? "").split(separator: ",")
            .map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
        p.pinnedPeerCertSha256 = q["pcs"] ?? ""
        p.verifyPeerCertByName = q["vcn"] ?? ""
        p.echConfigList = q["ech"] ?? ""
        p.host = q["host"] ?? ""
        p.path = q["path"] ?? ""
        p.serviceName = q["serviceName"] ?? ""
        p.authority = q["authority"] ?? ""
        p.mode = q["mode"] ?? ""
        p.headerType = q["headerType"] ?? ""
        p.extra = q["extra"] ?? ""
        p.link = text
        return p
    }

    // Unlike form decoding, '+' stays literal: base64 values such as pbk contain it.
    private static func decode(_ s: String) -> String {
        s.removingPercentEncoding ?? s
    }
}
