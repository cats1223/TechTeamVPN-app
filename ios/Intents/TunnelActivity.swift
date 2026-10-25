#if canImport(ActivityKit)
import ActivityKit
import Foundation

/// The Live Activity shown while the VPN is up: Dynamic Island and the lock screen. The app
/// starts and ends it; the widget extension draws it. The timer counts by itself, so the
/// activity needs no updates while it runs.
@available(iOS 16.1, *)
struct TunnelActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var since: Date
    }

    var serverName: String
}

#if !WIDGET
/// Starts the Live Activity when the VPN connects and ends it when it goes down.
@MainActor
enum TunnelActivity {
    /// On by default; "Dynamic Island" in settings.
    static let enabledKey = "liveActivity"
    static var enabled: Bool { UserDefaults.standard.object(forKey: enabledKey) as? Bool ?? true }

    static func update(connected: Bool, since: Date?, serverName: String?) {
        guard #available(iOS 16.2, *) else { return }
        let running = Activity<TunnelActivityAttributes>.activities
        guard connected, enabled, let since, ActivityAuthorizationInfo().areActivitiesEnabled else {
            for activity in running { Task { await activity.end(nil, dismissalPolicy: .immediate) } }
            return
        }
        let name = serverName ?? "TechTeamVPN"
        // One activity per connection: a new server or a new session replaces the old one.
        if let current = running.first, current.attributes.serverName == name, current.content.state.since == since {
            return
        }
        for activity in running { Task { await activity.end(nil, dismissalPolicy: .immediate) } }
        let content = ActivityContent(state: TunnelActivityAttributes.ContentState(since: since), staleDate: nil)
        _ = try? Activity.request(attributes: TunnelActivityAttributes(serverName: name), content: content)
    }
}
#endif
#endif
