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
    public static string CurrentVersion
    {
        get
        {
            var v = System.Reflection.Assembly.GetExecutingAssembly().GetName().Version;
            return v != null ? $"v{v.Major}.{v.Minor}.{v.Build}" : "v1.1.6";
        }
    }

    private const string RepoOwner = "FuziwaraDesu67";
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

            // Find Windows .exe asset specifically
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

            // Security (S-10): NEVER fall back to downloading the Release HTML page as an executable!
            // In-app update is only enabled if a verified .exe asset exists on GitHub Releases.
            bool isNewer = IsNewerVersion(tagName, CurrentVersion);
            info.IsUpdateAvailable = isNewer && !string.IsNullOrEmpty(info.DownloadUrl);
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

        string cleanLatest = latestTag.TrimStart('v', 'V').Trim();
        string cleanCurrent = currentTag.TrimStart('v', 'V').Trim();

        if (string.Equals(cleanLatest, cleanCurrent, StringComparison.OrdinalIgnoreCase))
            return false;

        var partsL = cleanLatest.Split('.').Select(p => int.TryParse(p, out var n) ? n : 0).ToList();
        var partsC = cleanCurrent.Split('.').Select(p => int.TryParse(p, out var n) ? n : 0).ToList();
        int maxLen = Math.Max(partsL.Count, partsC.Count);

        for (int i = 0; i < maxLen; i++)
        {
            int numL = i < partsL.Count ? partsL[i] : 0;
            int numC = i < partsC.Count ? partsC[i] : 0;
            if (numL > numC) return true;
            if (numL < numC) return false;
        }

        return false;
    }

    public static async Task<string> DownloadUpdateAsync(string downloadUrl, Action<int>? progressCallback, CancellationToken token)
    {
        if (string.IsNullOrWhiteSpace(downloadUrl) || !downloadUrl.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
        {
            throw new InvalidOperationException("Invalid or non-executable update URL.");
        }

        // Security (S-12): Use an isolated, randomized private directory rather than a static predictable path
        string updateDir = Path.Combine(Path.GetTempPath(), $"EZmic_Update_{Guid.NewGuid():N}");
        Directory.CreateDirectory(updateDir);
        string tempPath = Path.Combine(updateDir, "EZmic-update.exe");

        using var response = await _httpClient.GetAsync(downloadUrl, HttpCompletionOption.ResponseHeadersRead, token);
        response.EnsureSuccessStatusCode();

        long? totalBytes = response.Content.Headers.ContentLength;
        using var stream = await response.Content.ReadAsStreamAsync(token);
        using (var fileStream = new FileStream(tempPath, FileMode.Create, FileAccess.Write, FileShare.None))
        {
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
        }

        // Security (S-04 / S-10): Verify downloaded file has valid Windows PE magic header ('MZ')
        var fileInfo = new FileInfo(tempPath);
        if (fileInfo.Length < 1024)
        {
            File.Delete(tempPath);
            throw new InvalidDataException("Downloaded update file is too small to be a valid Windows binary.");
        }

        using (var verifyStream = new FileStream(tempPath, FileMode.Open, FileAccess.Read))
        {
            byte[] magic = new byte[2];
            int magicRead = verifyStream.Read(magic, 0, 2);
            if (magicRead < 2 || magic[0] != 0x4D || magic[1] != 0x5A) // 'M' 'Z'
            {
                verifyStream.Dispose();
                File.Delete(tempPath);
                throw new InvalidDataException("Security verification failed: Downloaded file is not a valid Windows executable.");
            }
        }

        return tempPath;
    }

    public static void LaunchUpdateAndExit(string updateExePath)
    {
        try
        {
            string currentExe = Environment.ProcessPath ?? "";
            int currentPid = Process.GetCurrentProcess().Id;
            if (!string.IsNullOrEmpty(currentExe) && File.Exists(currentExe))
            {
                string targetDir = Path.GetDirectoryName(updateExePath) ?? Path.GetTempPath();
                string scriptPath = Path.Combine(targetDir, "ezmic_replace_and_restart.bat");
                string script = $@"@echo off
set ""SRC={updateExePath}""
set ""DST={currentExe}""
set ""PID={currentPid}""

:: 1. Wait for current process PID to completely terminate
set /a p_wait=0
:wait_proc
timeout /t 1 /nobreak >nul
tasklist /fi ""pid eq %PID%"" 2>nul | findstr /i ""%PID%"" >nul
if not errorlevel 1 (
    set /a p_wait+=1
    if %p_wait% lss 8 goto wait_proc
)

:: 2. Retry file replacement up to 10 times to overcome Windows single-file lock
set /a c_wait=0
:retry_copy
copy /y ""%SRC%"" ""%DST%"" >nul 2>&1
if not errorlevel 1 goto copy_done
set /a c_wait+=1
if %c_wait% lss 10 (
    timeout /t 1 /nobreak >nul
    goto retry_copy
)

:: 3. Fallback: if DST is protected or cannot be written, launch the newly downloaded binary directly
start """" ""%SRC%""
exit /b

:copy_done
start """" ""%DST%""
timeout /t 1 /nobreak >nul
del ""%SRC%"" >nul 2>&1
(goto) 2>nul & del ""%~f0""
";
                File.WriteAllText(scriptPath, script);
                Process.Start(new ProcessStartInfo
                {
                    FileName = scriptPath,
                    CreateNoWindow = true,
                    UseShellExecute = false
                });
                Environment.Exit(0);
                return;
            }

            Process.Start(new ProcessStartInfo
            {
                FileName = updateExePath,
                UseShellExecute = true
            });
            Environment.Exit(0);
        }
        catch
        {
            Process.Start(new ProcessStartInfo { FileName = updateExePath, UseShellExecute = true });
            Environment.Exit(0);
        }
    }
}
