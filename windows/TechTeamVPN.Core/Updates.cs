using System.Text.Json;

namespace TechTeamVPN.Core;

/// <summary>Checks GitHub for a newer release of the app.</summary>
public static class Updates
{
    public const string LatestUrl = "https://api.github.com/repos/cats1223/TechTeamVPN-app/releases/latest";
    public const string AllUrl = "https://api.github.com/repos/cats1223/TechTeamVPN-app/releases?per_page=100";

    /// <summary><see cref="DownloadUrl"/> is the Windows zip when the release has one, otherwise the release page.</summary>
    public sealed record Release(string Version, string PageUrl, string DownloadUrl);

    public static Release? Parse(string json)
    {
        using var doc = JsonDocument.Parse(json);
        return Parse(doc.RootElement);
    }

    /// <summary>The newest published release in a list, by version: GitHub may sort tags as text.</summary>
    public static Release? ParseNewest(string json)
    {
        using var doc = JsonDocument.Parse(json);
        Release? best = null;
        foreach (var o in doc.RootElement.EnumerateArray())
        {
            if (o.TryGetProperty("draft", out var d) && d.ValueKind == JsonValueKind.True) continue;
            if (Parse(o) is { } r && (best is null || IsNewer(r.Version, best.Version))) best = r;
        }
        return best;
    }

    private static Release? Parse(JsonElement o)
    {
        if (!o.TryGetProperty("tag_name", out var tag) || !o.TryGetProperty("html_url", out var page)) return null;
        var zip = o.TryGetProperty("assets", out var assets)
            ? assets.EnumerateArray()
                .Where(a => a.GetProperty("name").GetString()?.EndsWith("-windows.zip") == true)
                .Select(a => a.GetProperty("browser_download_url").GetString())
                .FirstOrDefault()
            : null;
        var pageUrl = page.GetString() ?? "";
        return new Release(tag.GetString()!.TrimStart('v'), pageUrl, zip ?? pageUrl);
    }

    /// <summary>
    /// Compares dotted versions number by number; a beta (1.2.0-beta.1) is older than its release,
    /// and betas of one version compare by their number.
    /// </summary>
    public static bool IsNewer(string latest, string current)
    {
        var a = Numbers(latest);
        var b = Numbers(current);
        for (var i = 0; i < Math.Max(a.Length, b.Length); i++)
        {
            var x = i < a.Length ? a[i] : 0;
            var y = i < b.Length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return (IsBeta(latest), IsBeta(current)) switch
        {
            (false, true) => true,
            (true, true) => BetaNumber(latest) > BetaNumber(current),
            _ => false,
        };
    }

    public static bool IsBeta(string version) => version.Contains('-');

    private static int[] Numbers(string version) =>
        version.TrimStart('v').Split('-')[0].Split('.').Select(s => int.TryParse(s, out var n) ? n : 0).ToArray();

    private static int BetaNumber(string version)
    {
        var dash = version.IndexOf('-');
        if (dash < 0) return 0;
        var digits = new string(version[(dash + 1)..].Reverse().TakeWhile(char.IsDigit).Reverse().ToArray());
        return int.TryParse(digits, out var n) ? n : 0;
    }
}
