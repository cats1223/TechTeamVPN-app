import AVFoundation
import UIKit

/// Taps and sounds: a click on the button, a success buzz and chime when the VPN connects, a
/// softer one when it disconnects, an error buzz when it fails. The sounds follow the silent switch.
@MainActor
enum Feedback {
    /// "Звуки" in settings; on by default.
    static let soundKey = "sounds"
    static var soundOn: Bool { UserDefaults.standard.object(forKey: soundKey) as? Bool ?? true }

    private static var player: AVAudioPlayer?

    static func tap() {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }

    static func connected() {
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        play("connect")
    }

    static func disconnected() {
        UIImpactFeedbackGenerator(style: .soft).impactOccurred()
        play("disconnect")
    }

    static func failed() {
        UINotificationFeedbackGenerator().notificationOccurred(.error)
    }

    private static func play(_ name: String) {
        guard soundOn, let url = Bundle.main.url(forResource: name, withExtension: "wav") else { return }
        // Ambient: muted by the silent switch and mixed with whatever is playing.
        try? AVAudioSession.sharedInstance().setCategory(.ambient, options: .mixWithOthers)
        player = try? AVAudioPlayer(contentsOf: url)
        player?.volume = 0.7
        player?.play()
    }
}
