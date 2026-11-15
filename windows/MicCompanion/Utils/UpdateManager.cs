using System.Diagnostics;
using System.IO;
using System.Net.Http;
using System.Text.Json;

namespace MicCompanion.Utils;

public class UpdateInfo
{
    public bool IsUpdateAvailable { get; set; }
    public string CurrentVersion { get; set; } = "";
    public string LatestVersion { get; set; } = "";
    public string ReleaseName { get; set; } = "";
    public string ReleaseNotes { get; set; } = "";
    public string ReleaseUrl { get; set; } = "";
    public string DownloadUrl { get; set; } = "";
    public string AssetName { get; set; } = "";
}

public static class UpdateManager
{
    public const string CurrentVersion = "v1.1";
    private const string RepoOwner = "aadarsh765";
    private const string RepoName = "EZmic";
    private const string ApiUrl = $"https://api.github.com/repos/{RepoOwner}/{RepoName}/releases/latest";

    private static readonly HttpClient _httpClient = new()
    {
        Timeout = TimeSpan.FromSeconds(15)
    };

    static UpdateManager()
    {
        _httpClient.DefaultRequestHeaders.Add("User-Agent", "EZmic-Desktop-Updater");
        _httpClient.DefaultRequestHeaders.Add("Accept", "application/vnd.github.v3+json");
    }

    public static async Task<UpdateInfo> CheckForUpdatesAsync()
    {
        var info = new UpdateInfo
        {
            CurrentVersion = CurrentVersion
        };

        try
        {
            using var response = await _httpClient.GetAsync(ApiUrl);
            if (!response.IsSuccessStatusCode)
            {
                return info;
            }

            string json = await response.Content.ReadAsStringAsync();
            using var doc = JsonDocument.Parse(json);
            var root = doc.RootElement;

            string tagName = root.GetProperty("tag_name").GetString() ?? "";
            info.LatestVersion = tagName;
            info.ReleaseName = root.TryGetProperty("name", out var n) ? n.GetString() ?? tagName : tagName;
            info.ReleaseNotes = root.TryGetProperty("body", out var b) ? b.GetString() ?? "" : "";
            info.ReleaseUrl = root.TryGetProperty("html_url", out var h) ? h.GetString() ?? $"https://github.com/{RepoOwner}/{RepoName}/releases" : "";

            // Find Windows .exe asset
            if (root.TryGetProperty("assets", out var assets) && assets.ValueKind == JsonValueKind.Array)
            {
                foreach (var asset in assets.EnumerateArray())
                {
                    string name = asset.GetProperty("name").GetString() ?? "";
                    if (name.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
                    {
                        info.DownloadUrl = asset.GetProperty("browser_download_url").GetString() ?? "";
                        info.AssetName = name;
                        break;
                    }
                }
            }

            // Fallback download URL to release page if no direct exe asset found
            if (string.IsNullOrEmpty(info.DownloadUrl))
            {
                info.DownloadUrl = info.ReleaseUrl;
            }

            info.IsUpdateAvailable = IsNewerVersion(tagName, CurrentVersion);
        }
        catch
        {
            // Network failure or offline mode: silently fail without crashing
            info.IsUpdateAvailable = false;
        }

        return info;
    }

    public static bool IsNewerVersion(string latestTag, string currentTag)
    {
        if (string.IsNullOrWhiteSpace(latestTag)) return false;

        string cleanLatest = latestTag.TrimStart('v', 'V');
        string cleanCurrent = currentTag.TrimStart('v', 'V');

        if (Version.TryParse(cleanLatest, out var vLatest) && Version.TryParse(cleanCurrent, out var vCurrent))
        {
            return vLatest > vCurrent;
        }

        return string.Compare(cleanLatest, cleanCurrent, StringComparison.OrdinalIgnoreCase) > 0;
    }

    public static async Task<string> DownloadUpdateAsync(string downloadUrl, Action<int>? progressCallback, CancellationToken token)
    {
        string tempPath = Path.Combine(Path.GetTempPath(), "EZmic-update.exe");

        using var response = await _httpClient.GetAsync(downloadUrl, HttpCompletionOption.ResponseHeadersRead, token);
        response.EnsureSuccessStatusCode();

        long? totalBytes = response.Content.Headers.ContentLength;
        using var stream = await response.Content.ReadAsStreamAsync(token);
        using var fileStream = new FileStream(tempPath, FileMode.Create, FileAccess.Write, FileShare.None);

        byte[] buffer = new byte[81920];
        long totalRead = 0;
        int read;

        while ((read = await stream.ReadAsync(buffer, token)) > 0)
        {
            await fileStream.WriteAsync(buffer.AsMemory(0, read), token);
            totalRead += read;

            if (totalBytes.HasValue && totalBytes.Value > 0)
            {
                int percent = (int)(totalRead * 100 / totalBytes.Value);
                progressCallback?.Invoke(percent);
            }
        }

        return tempPath;
    }

    public static void LaunchUpdateAndExit(string updateExePath)
    {
        try
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = updateExePath,
                UseShellExecute = true
            });
            Environment.Exit(0);
        }
        catch { }
    }
}
