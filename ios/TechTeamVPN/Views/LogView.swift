import SwiftUI

/// Shows the tail of the tunnel's Xray log so connection problems can be copied and sent.
struct LogView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var vpn: VPNManager
    @State private var text = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                Text(text.isEmpty ? "Журнал пуст. Подключитесь и попробуйте открыть сайт." : text)
                    .font(.system(.caption2, design: .monospaced))
                    .foregroundStyle(text.isEmpty ? Brand.muted : .white)
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
            }
            .background(Brand.background.ignoresSafeArea())
            .navigationTitle("Журнал")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Закрыть") { dismiss() } }
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button { Task { await load() } } label: { Image(systemName: "arrow.clockwise") }
                    Button { UIPasteboard.general.string = text } label: { Image(systemName: "doc.on.doc") }
                        .disabled(text.isEmpty)
                    ShareLink(item: text) { Image(systemName: "square.and.arrow.up") }
                        .disabled(text.isEmpty)
                }
            }
            .task { await load() }
        }
    }

    private func load() async {
        // The running tunnel answers directly; the shared file only exists when App Groups is signed in.
        var raw = await vpn.fetchTunnelLog()
        if raw?.isEmpty ?? true, let url = AppGroup.logURL, let data = try? Data(contentsOf: url) {
            raw = String(decoding: data, as: UTF8.self)
        }
        guard let raw, !raw.isEmpty else {
            text = vpn.isActive ? "" : "Журнал доступен, пока VPN включён."
            return
        }
        // Info level is chatty; the last part is what matters.
        let lines = raw.split(separator: "\n", omittingEmptySubsequences: false)
        let tail = lines.count > 400 ? [lines[0], "…"] + lines.suffix(400) : Array(lines)
        text = tail.joined(separator: "\n")
    }
}
