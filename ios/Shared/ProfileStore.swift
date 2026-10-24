import Foundation

enum AppGroup {
    /// Must match the App Group capability of both targets.
    static let id = "group.com.techteam.vpn"
    static let tunnelBundleId = "com.techteam.vpn.tunnel"

    static var defaults: UserDefaults { UserDefaults(suiteName: id) ?? .standard }
}

struct StoredProfile: Codable, Hashable, Identifiable {
    var profile: VlessProfile
    var fromSubscription: Bool
    var id: String { profile.id }
}

struct ProfilesState: Codable, Hashable {
    var profiles: [StoredProfile] = []
    var selectedId: String?
    var subscriptionURL = ""
    var subscriptionInfo: SubscriptionInfo?

    var selected: VlessProfile? {
        (profiles.first { $0.id == selectedId } ?? profiles.first)?.profile
    }
}

/// Persists servers in the shared App Group so the packet tunnel can read the selection on its own
/// (for example when iOS restarts it through on-demand rules).
enum ProfileStore {
    private static let key = "profilesState"

    static func load() -> ProfilesState {
        guard let data = AppGroup.defaults.data(forKey: key),
              let state = try? JSONDecoder().decode(ProfilesState.self, from: data)
        else { return ProfilesState() }
        return state
    }

    static func save(_ state: ProfilesState) {
        if let data = try? JSONEncoder().encode(state) {
            AppGroup.defaults.set(data, forKey: key)
        }
    }
}
