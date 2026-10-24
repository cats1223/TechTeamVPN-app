import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Checks GitHub for a newer release of the app.
enum Updates {
    static let latestURL = URL(string: "https://api.github.com/repos/cats1223/TechTeamVPN-app/releases/latest")!

    struct Release: Equatable {
        var version: String
        var pageURL: URL
    }

    static func parse(_ data: Data) -> Release? {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let tag = json["tag_name"] as? String,
              let page = (json["html_url"] as? String).flatMap(URL.init(string:)) else { return nil }
        return Release(version: tag.hasPrefix("v") ? String(tag.dropFirst()) : tag, pageURL: page)
    }

    /// Compares dotted versions number by number: 1.10.0 is newer than 1.9.2. A beta
    /// (1.2.0-beta.1) is older than its release, so beta testers are offered 1.2.0.
    static func isNewer(_ latest: String, than current: String) -> Bool {
        let a = numbers(latest), b = numbers(current)
        for i in 0..<max(a.count, b.count) {
            let x = i < a.count ? a[i] : 0, y = i < b.count ? b[i] : 0
            if x != y { return x > y }
        }
        return isBeta(current) && !isBeta(latest)
    }

    static func isBeta(_ version: String) -> Bool { version.contains("-") }

    private static func numbers(_ version: String) -> [Int] {
        let core = (version.hasPrefix("v") ? version.dropFirst() : Substring(version)).split(separator: "-").first ?? ""
        return core.split(separator: ".").map { Int($0) ?? 0 }
    }

    static func fetchLatest() async throws -> Release {
        var request = URLRequest(url: latestURL, timeoutInterval: 10)
        request.setValue("application/vnd.github+json", forHTTPHeaderField: "Accept")
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard status == 200, let release = parse(data) else {
            throw URLError(.badServerResponse, userInfo: [NSLocalizedDescriptionKey: "GitHub: HTTP \(status)"])
        }
        return release
    }
}
