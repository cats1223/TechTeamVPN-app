import SwiftUI

/// Shows the tail of the tunnel's Xray log so connection problems can be copied and sent.
struct LogView: View {
    @Environment(\.dismiss) private var dismiss
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
                    Button { load() } label: { Image(systemName: "arrow.clockwise") }
                    Button { UIPasteboard.general.string = text } label: { Image(systemName: "doc.on.doc") }
                        .disabled(text.isEmpty)
                    ShareLink(item: text) { Image(systemName: "square.and.arrow.up") }
                        .disabled(text.isEmpty)
                }
            }
            .onAppear(perform: load)
        }
    }

    private func load() {
        guard let url = AppGroup.logURL, let data = try? Data(contentsOf: url) else {
            text = ""
            return
        }
        // Info level is chatty; the last part is what matters.
        let lines = String(decoding: data, as: UTF8.self).split(separator: "\n", omittingEmptySubsequences: false)
        let tail = lines.count > 400 ? [lines[0], "…"] + lines.suffix(400) : Array(lines)
        text = tail.joined(separator: "\n")
    }
}
