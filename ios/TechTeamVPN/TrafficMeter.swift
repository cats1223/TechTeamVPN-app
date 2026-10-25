import Darwin
import Foundation

/// Live speed and session traffic of the VPN, read from the tunnel interface's byte counters.
/// The app sees the utun interface like any other, so this needs nothing from the extension.
@MainActor
final class TrafficMeter: ObservableObject {
    /// Bytes per second.
    @Published private(set) var down: Double = 0
    @Published private(set) var up: Double = 0
    /// Bytes since the meter started.
    @Published private(set) var sessionDown: UInt64 = 0
    @Published private(set) var sessionUp: UInt64 = 0

    private var timer: Timer?
    private var last: (rx: UInt32, tx: UInt32)?

    func start() {
        stop()
        sessionDown = 0
        sessionUp = 0
        last = Self.counters()
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.tick() }
        }
    }

    func stop() {
        timer?.invalidate()
        timer = nil
        down = 0
        up = 0
    }

    private func tick() {
        guard let now = Self.counters() else { return }
        if let last {
            // The counters are 32-bit and wrap around at 4 GB; unsigned subtraction handles that.
            let rx = UInt64(now.rx &- last.rx), tx = UInt64(now.tx &- last.tx)
            down = Double(rx)
            up = Double(tx)
            sessionDown += rx
            sessionUp += tx
        }
        last = now
    }

    /// Received and sent bytes of the utun interface that carries the tunnel's address.
    private static func counters() -> (rx: UInt32, tx: UInt32)? {
        var list: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&list) == 0, let first = list else { return nil }
        defer { freeifaddrs(list) }
        let all = Array(sequence(first: first) { $0.pointee.ifa_next })
        let tunnel = inet_addr(tunnelAddress)
        guard let name = all.first(where: { ptr in
            guard let addr = ptr.pointee.ifa_addr, addr.pointee.sa_family == UInt8(AF_INET) else { return false }
            return addr.withMemoryRebound(to: sockaddr_in.self, capacity: 1) { $0.pointee.sin_addr.s_addr == tunnel }
        }).map({ String(cString: $0.pointee.ifa_name) }) else { return nil }
        for ptr in all {
            guard let addr = ptr.pointee.ifa_addr, addr.pointee.sa_family == UInt8(AF_LINK),
                  String(cString: ptr.pointee.ifa_name) == name,
                  let data = ptr.pointee.ifa_data?.assumingMemoryBound(to: if_data.self) else { continue }
            // ibytes come out of the tunnel to apps (download), obytes go from apps into it (upload).
            return (data.pointee.ifi_ibytes, data.pointee.ifi_obytes)
        }
        return nil
    }

    /// The address PacketTunnelProvider gives the utun interface.
    static let tunnelAddress = "172.19.0.1"
}

/// "1.2 МБ/с" for a byte rate.
func formatRate(_ bytesPerSecond: Double) -> String {
    String(localized: "\(formatBytes(Int64(bytesPerSecond)))/с")
}
