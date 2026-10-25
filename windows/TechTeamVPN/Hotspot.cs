using System;
using System.Linq;
using System.Net.NetworkInformation;
using System.Threading.Tasks;
using TechTeamVPN.Core;
using Windows.Networking.Connectivity;
using Windows.Networking.NetworkOperators;

namespace TechTeamVPN;

/// <summary>
/// Windows Mobile Hotspot shared from the TUN adapter: devices on that Wi-Fi (a PS5, a TV) go
/// through the VPN entirely, UDP included. Xray supports this (forwarding moves to the TUN).
/// </summary>
public static class Hotspot
{
    public sealed record State(bool On, string Ssid, string Password, string? Error);

    /// <summary>The connection profile of the Wintun adapter that Xray created, if it is up.</summary>
    private static ConnectionProfile? TunProfile()
    {
        var adapter = NetworkInterface.GetAllNetworkInterfaces()
            .FirstOrDefault(n => n.Name == XrayConfig.TunName || n.Description.Contains(XrayConfig.TunName));
        if (adapter is null || !Guid.TryParse(adapter.Id, out var id)) return null;
        return NetworkInformation.GetConnectionProfiles().FirstOrDefault(p => p.NetworkAdapter?.NetworkAdapterId == id);
    }

    public static async Task<State> StartAsync()
    {
        try
        {
            var profile = TunProfile();
            if (profile is null) return new State(false, "", "", "Сначала подключитесь в режиме «Весь трафик»");
            var capability = NetworkOperatorTetheringManager.GetTetheringCapabilityFromConnectionProfile(profile);
            if (capability != TetheringCapability.Enabled)
                return new State(false, "", "", $"Windows не даёт раздавать это подключение ({capability})");
            var manager = NetworkOperatorTetheringManager.CreateFromConnectionProfile(profile);
            var config = manager.GetCurrentAccessPointConfiguration();
            if (manager.TetheringOperationalState != TetheringOperationalState.On)
            {
                var result = await manager.StartTetheringAsync();
                if (result.Status != TetheringOperationStatus.Success)
                    return new State(false, "", "", $"Не удалось включить раздачу: {result.Status} {result.AdditionalErrorMessage}".Trim());
            }
            return new State(true, config.Ssid, config.Passphrase, null);
        }
        catch (Exception e)
        {
            return new State(false, "", "", e.Message);
        }
    }

    public static async Task StopAsync()
    {
        try
        {
            if (TunProfile() is { } profile)
            {
                var manager = NetworkOperatorTetheringManager.CreateFromConnectionProfile(profile);
                if (manager.TetheringOperationalState == TetheringOperationalState.On) await manager.StopTetheringAsync();
            }
        }
        catch (Exception) { }
    }
}
