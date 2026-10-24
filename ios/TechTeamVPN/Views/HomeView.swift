import NetworkExtension
import SwiftUI

struct HomeView: View {
    @EnvironmentObject private var model: AppModel
    @EnvironmentObject private var vpn: VPNManager
    @State private var showAdd = false
    @State private var showLog = false

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Text("TechTeamVPN")
                    .font(.system(size: 26, weight: .bold))
                    .padding(.top, 24)
                Text("Быстрый и защищённый доступ")
                    .font(.subheadline)
                    .foregroundStyle(Brand.muted)

                PowerButton(status: vpn.status) {
                    Task { await model.toggle() }
                }
                .padding(.top, 40)

                StatusText(status: vpn.status, since: vpn.connectedDate, error: vpn.lastError)
                    .padding(.top, 28)

                diagnostics
                    .padding(.top, 16)

                Group {
                    if let selected = model.state.selected {
                        serverCard(selected)
                    } else {
                        Button {
                            showAdd = true
                        } label: {
                            Label("Добавить ключ", systemImage: "plus")
                                .font(.headline)
                                .frame(maxWidth: .infinity, minHeight: 54)
                                .background(Brand.cyan, in: RoundedRectangle(cornerRadius: 14))
                                .foregroundStyle(Brand.background)
                        }
                    }
                    if let info = model.state.subscriptionInfo {
                        SubscriptionCard(info: info)
                    }
                }
                .padding(.top, 12)
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 24)
        }
        .background(Brand.background.ignoresSafeArea())
        .sheet(isPresented: $showAdd) { AddView() }
        .sheet(isPresented: $showLog) { LogView() }
        .onChange(of: vpn.status) { status in
            if status == .connected {
                Task { await model.checkInternet() }
            } else {
                model.resetInternetCheck()
            }
        }
    }

    private var diagnostics: some View {
        VStack(spacing: 10) {
            if vpn.status == .connected {
                Button {
                    Task { await model.checkInternet() }
                } label: {
                    HStack(spacing: 8) {
                        switch model.internetCheck {
                        case .idle:
                            Image(systemName: "network")
                            Text("Проверить интернет")
                        case .running:
                            ProgressView().controlSize(.small)
                            Text("Проверяю интернет…")
                        case .ok(let ms):
                            Image(systemName: "checkmark.circle.fill").foregroundStyle(Brand.green)
                            Text("Интернет работает · \(ms) мс")
                        case .failed(let reason):
                            Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(Brand.amber)
                            Text("Интернета нет: \(reason)").multilineTextAlignment(.leading)
                        }
                    }
                    .font(.footnote)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Brand.surface, in: Capsule())
                }
                .buttonStyle(.plain)
                .disabled(model.internetCheck == .running)
            }
            Button("Журнал подключения") { showLog = true }
                .font(.footnote)
                .foregroundStyle(Brand.muted)
            Text("Сборка \(Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "?")")
                .font(.caption2)
                .foregroundStyle(Brand.muted.opacity(0.6))
        }
    }

    private func serverCard(_ profile: VlessProfile) -> some View {
        HStack(spacing: 14) {
            Image(systemName: "globe")
                .foregroundStyle(Brand.cyan)
                .frame(width: 40, height: 40)
                .background(Brand.surfaceHigh, in: Circle())
            VStack(alignment: .leading, spacing: 2) {
                Text("Сервер").font(.caption).foregroundStyle(Brand.muted)
                Text(profile.name).font(.body.weight(.semibold)).lineLimit(1)
            }
            Spacer()
        }
        .card()
        .padding(.top, 24)
    }
}

private struct PowerButton: View {
    let status: NEVPNStatus
    let action: () -> Void
    @State private var pulse = false

    private var connected: Bool { status == .connected }
    private var connecting: Bool { [.connecting, .reasserting, .disconnecting].contains(status) }
    private var ring: Color { connected ? Brand.green : connecting ? Brand.amber : Brand.surfaceHigh }

    var body: some View {
        Button(action: action) {
            ZStack {
                Circle()
                    .fill(RadialGradient(colors: [ring.opacity(0.25), .clear], center: .center, startRadius: 60, endRadius: 105))
                    .frame(width: 210, height: 210)
                Circle()
                    .fill(connected ? AnyShapeStyle(Brand.gradient) : AnyShapeStyle(Brand.surface))
                    .overlay(Circle().stroke(ring, lineWidth: 3))
                    .frame(width: 160, height: 160)
                Image(systemName: "power")
                    .font(.system(size: 60, weight: .semibold))
                    .foregroundStyle(connected ? .white : Brand.cyan)
            }
            .scaleEffect(connecting && pulse ? 1.08 : 1)
            .animation(connecting ? .easeInOut(duration: 0.7).repeatForever(autoreverses: true) : .default, value: pulse)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(connected ? "Отключить" : "Подключить")
        .onAppear { pulse = true }
    }
}

private struct StatusText: View {
    let status: NEVPNStatus
    let since: Date?
    let error: String?

    var body: some View {
        VStack(spacing: 6) {
            Text(title).font(.title3.weight(.semibold)).foregroundStyle(color)
            if status == .connected, let since {
                TimelineView(.periodic(from: since, by: 1)) { context in
                    Text(duration(context.date.timeIntervalSince(since))).monospacedDigit()
                }
            } else if status == .disconnected, let error {
                Text(error).font(.footnote).foregroundStyle(Brand.muted).multilineTextAlignment(.center)
            } else if status == .disconnected || status == .invalid {
                Text("Нажмите кнопку, чтобы подключиться").font(.footnote).foregroundStyle(Brand.muted)
            }
        }
    }

    private var title: String {
        switch status {
        case .connected: return "Подключено"
        case .connecting, .reasserting: return "Подключение…"
        case .disconnecting: return "Отключение…"
        default: return error == nil ? "Не подключено" : "Ошибка подключения"
        }
    }

    private var color: Color {
        switch status {
        case .connected: return Brand.green
        case .connecting, .reasserting, .disconnecting: return Brand.amber
        default: return error == nil ? Brand.muted : Brand.red
        }
    }

    private func duration(_ t: TimeInterval) -> String {
        let s = Int(max(0, t))
        return String(format: "%02d:%02d:%02d", s / 3600, s % 3600 / 60, s % 60)
    }
}

private struct SubscriptionCard: View {
    let info: SubscriptionInfo

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(info.title.isEmpty ? "Подписка" : info.title).font(.body.weight(.semibold))
            let used = info.upload + info.download
            if info.total > 0 {
                ProgressView(value: min(1, Double(used) / Double(info.total))).tint(Brand.cyan)
                Text("Трафик: \(formatBytes(used)) из \(formatBytes(info.total))")
                    .font(.footnote).foregroundStyle(Brand.muted)
            } else {
                Text("Использовано: \(formatBytes(used))").font(.footnote).foregroundStyle(Brand.muted)
            }
            if info.expire > 0 {
                let days = max(0, Int((Double(info.expire) - Date().timeIntervalSince1970) / 86_400))
                Text("Осталось дней: \(days)")
                    .font(.footnote)
                    .foregroundStyle(days <= 3 ? Brand.amber : Brand.muted)
            }
        }
        .card()
    }
}
