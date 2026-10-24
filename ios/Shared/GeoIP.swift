import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Where an IP address is, from ipwho.is: the phone's own address before the VPN, the server's after.
enum GeoIP {
    struct Location: Equatable {
        var ip: String
        var country: String
        var countryCode: String
        var city: String
        var latitude: Double
        var longitude: Double

        var flag: String { GeoIP.flag(countryCode) }
        var place: String {
            var parts: [String] = []
            for part in [city, country] where !part.isEmpty && !parts.contains(part) { parts.append(part) }
            return parts.joined(separator: ", ")
        }
    }

    /// `ip` nil asks about the address the request comes from.
    static func url(_ ip: String?) -> URL {
        URL(string: "https://ipwho.is/" + (ip ?? ""))!
    }

    static func parse(_ data: Data) -> Location? {
        guard let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              o["success"] as? Bool == true,
              let lat = (o["latitude"] as? NSNumber)?.doubleValue,
              let lon = (o["longitude"] as? NSNumber)?.doubleValue else { return nil }
        return Location(
            ip: o["ip"] as? String ?? "",
            country: o["country"] as? String ?? "",
            countryCode: o["country_code"] as? String ?? "",
            city: o["city"] as? String ?? "",
            latitude: lat,
            longitude: lon
        )
    }

    /// "NL" -> 🇳🇱; anything else -> 🌐.
    static func flag(_ countryCode: String) -> String {
        let cc = countryCode.uppercased()
        guard cc.count == 2, cc.unicodeScalars.allSatisfy({ $0.value >= 65 && $0.value <= 90 }) else { return "🌐" }
        return String(String.UnicodeScalarView(cc.unicodeScalars.compactMap { Unicode.Scalar(0x1F1E6 + $0.value - 65) }))
    }

    /// A fresh session each time, so a lookup right after the VPN goes up does not reuse a connection made before it.
    static func lookup(_ ip: String? = nil) async -> Location? {
        let session = URLSession(configuration: .ephemeral)
        defer { session.finishTasksAndInvalidate() }
        guard let (data, _) = try? await session.data(for: URLRequest(url: url(ip), timeoutInterval: 8)) else { return nil }
        return parse(data)
    }
}
