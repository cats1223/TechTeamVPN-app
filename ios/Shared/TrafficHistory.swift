import Foundation

/// Traffic per day for the statistics screen: "2026-10-25" -> [down, up] bytes, kept for 60 days.
/// The tunnel adds to it while it runs and hands it to the app on request.
struct TrafficHistory: Codable, Equatable {
    var days: [String: [Int64]] = [:]

    static let keepDays = 60

    static func dayKey(_ date: Date, calendar: Calendar = .current) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year ?? 0, c.month ?? 0, c.day ?? 0)
    }

    mutating func add(down: Int64, up: Int64, at date: Date = Date(), calendar: Calendar = .current) {
        guard down > 0 || up > 0 else { return }
        let key = Self.dayKey(date, calendar: calendar)
        let old = days[key] ?? [0, 0]
        days[key] = [old[0] + down, old[1] + up]
        // Keys sort as dates, so anything below the cut-off is older than 60 days.
        if let cut = calendar.date(byAdding: .day, value: -Self.keepDays, to: date) {
            let oldest = Self.dayKey(cut, calendar: calendar)
            days = days.filter { $0.key > oldest }
        }
    }

    struct Day: Equatable {
        let day: Date
        let down: Int64
        let up: Int64
        var total: Int64 { down + up }
    }

    /// Down and up bytes of each of the last `count` days, oldest first, today last.
    func daily(_ count: Int, until date: Date = Date(), calendar: Calendar = .current) -> [Day] {
        (0..<count).reversed().compactMap { back in
            guard let day = calendar.date(byAdding: .day, value: -back, to: date) else { return nil }
            let v = days[Self.dayKey(day, calendar: calendar)] ?? [0, 0]
            return Day(day: day, down: v[0], up: v[1])
        }
    }

    /// Down and up bytes of the last `count` days, today included.
    func total(_ count: Int, until date: Date = Date(), calendar: Calendar = .current) -> (down: Int64, up: Int64) {
        daily(count, until: date, calendar: calendar).reduce((Int64(0), Int64(0))) { ($0.0 + $1.down, $0.1 + $1.up) }
    }

    init() {}

    init?(data: Data) {
        guard let decoded = try? JSONDecoder().decode(Self.self, from: data) else { return nil }
        self = decoded
    }

    var data: Data { (try? JSONEncoder().encode(self)) ?? Data() }
}
