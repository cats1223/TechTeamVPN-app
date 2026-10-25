import SwiftUI

/// The easter egg: green "Matrix" characters raining over the globe. Ten quick taps on the
/// globe turn it on and off.
struct MatrixRain: View {
    private static let glyphs = Array("ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄ0123456789TECHVPN")
    private static let cell: CGFloat = 16

    /// Each column falls at its own speed from its own start, so the rain looks random but is
    /// the same every frame without any stored state.
    var body: some View {
        TimelineView(.animation) { context in
            Canvas { gc, size in
                let t = context.date.timeIntervalSinceReferenceDate
                let columns = Int(size.width / Self.cell) + 1
                let rows = Int(size.height / Self.cell) + 1
                for c in 0..<columns {
                    var rng = SplitMix(seed: UInt64(c) &* 0x9E37_79B9_7F4A_7C15)
                    let speed = 6 + Double(rng.next() % 10)
                    let offset = Double(rng.next() % 1000)
                    let length = 8 + Int(rng.next() % 16)
                    let head = Int((t * speed + offset).truncatingRemainder(dividingBy: Double(rows + length)))
                    for k in 0..<length {
                        let row = head - k
                        guard row >= 0, row < rows else { continue }
                        // Characters flicker: each cell changes glyph a few times a second.
                        let g = Self.glyphs[Int((UInt64(row * 31 + c * 17) &+ UInt64(t * 8)) % UInt64(Self.glyphs.count))]
                        let alpha = k == 0 ? 1 : 0.85 * (1 - Double(k) / Double(length))
                        let color: Color = k == 0 ? .white : Color(red: 0.1, green: 1, blue: 0.35)
                        gc.draw(
                            Text(String(g)).font(.system(size: 14, weight: .medium, design: .monospaced)).foregroundColor(color.opacity(alpha)),
                            at: CGPoint(x: CGFloat(c) * Self.cell + Self.cell / 2, y: CGFloat(row) * Self.cell)
                        )
                    }
                }
            }
        }
        .background(Color.black.opacity(0.35))
        .allowsHitTesting(false)
    }
}

/// A tiny deterministic random generator, so every column keeps its speed between frames.
private struct SplitMix {
    var state: UInt64
    init(seed: UInt64) { state = seed }
    mutating func next() -> UInt64 {
        state &+= 0x9E37_79B9_7F4A_7C15
        var z = state
        z = (z ^ (z >> 30)) &* 0xBF58_476D_1CE4_E5B9
        z = (z ^ (z >> 27)) &* 0x94D0_49BB_1331_11EB
        return z ^ (z >> 31)
    }
}
