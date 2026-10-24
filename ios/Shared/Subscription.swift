import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Traffic and expiry the panel reports in the `subscription-userinfo` header.
struct SubscriptionInfo: Codable, Hashable {
    var title = ""
    var upload: Int64 = 0
    var download: Int64 = 0
    var total: Int64 = 0
    /// Unix seconds; 0 means no expiry.
    var expire: Int64 = 0
    var updatedAt: Int64 = 0
}

enum SubscriptionError: LocalizedError {
    case http(Int)
    case empty

    var errorDescription: String? {
        switch self {
        case .http(let code): return "HTTP \(code)"
        case .empty: return "В подписке нет серверов VLESS"
        }
    }
}

enum Subscription {
    static let userAgent = "TechTeamVPN/1.0"

    /// - Parameter headers: device headers (`x-hwid` and friends) for panels that bind keys to devices.
    static func fetch(_ url: URL, headers: [String: String] = [:]) async throws -> (profiles: [VlessProfile], info: SubscriptionInfo) {
        var request = URLRequest(url: url, timeoutInterval: 15)
        request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        for (name, value) in headers { request.setValue(value, forHTTPHeaderField: name) }
        let (data, response) = try await URLSession.shared.data(for: request)
        let http = response as? HTTPURLResponse
        if let code = http?.statusCode, !(200..<300).contains(code) {
            throw SubscriptionError.http(code)
        }
        let profiles = parseLinks(String(decoding: data, as: UTF8.self))
        if profiles.isEmpty { throw SubscriptionError.empty }
        var info = parseUserInfo(http?.value(forHTTPHeaderField: "subscription-userinfo"))
        info.title = decodeTitle(http?.value(forHTTPHeaderField: "profile-title"))
        info.updatedAt = Int64(Date().timeIntervalSince1970)
        return (profiles, info)
    }

    /// Accepts plain text or base64 with one share link per line.
    static func parseLinks(_ body: String) -> [VlessProfile] {
        let text = body.trimmingCharacters(in: .whitespacesAndNewlines)
        let plain = text.contains("://") ? text : (decodeBase64(text) ?? text)
        return plain.split(whereSeparator: \.isNewline).compactMap { VlessProfile.parse(String($0)) }
    }

    static func parseUserInfo(_ header: String?) -> SubscriptionInfo {
        var info = SubscriptionInfo()
        for part in (header ?? "").split(separator: ";") {
            let kv = part.split(separator: "=", maxSplits: 1)
            guard kv.count == 2 else { continue }
            let value = Int64(Double(kv[1].trimmingCharacters(in: .whitespaces)) ?? 0)
            switch kv[0].trimmingCharacters(in: .whitespaces).lowercased() {
            case "upload": info.upload = value
            case "download": info.download = value
            case "total": info.total = value
            case "expire": info.expire = value
            default: break
            }
        }
        return info
    }

    private static func decodeTitle(_ header: String?) -> String {
        guard let header, !header.isEmpty else { return "" }
        if header.hasPrefix("base64:") { return decodeBase64(String(header.dropFirst(7))) ?? "" }
        return header
    }

    private static func decodeBase64(_ s: String) -> String? {
        var clean = s.filter { !$0.isWhitespace }
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        clean += String(repeating: "=", count: (4 - clean.count % 4) % 4)
        guard let data = Data(base64Encoded: clean) else { return nil }
        return String(data: data, encoding: .utf8)
    }
}
