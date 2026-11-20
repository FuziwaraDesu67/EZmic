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
            return v != null ? $"v{v.Major}.{v.Minor}.{v.Build}" : "v1.1.8";
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

    public static bool IsInstalledVersion()
    {
        string? currentExe = Environment.ProcessPath;
        if (string.IsNullOrEmpty(currentExe)) return false;
        string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        string progFiles = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
        string progFilesX86 = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86);
        return currentExe.StartsWith(Path.Combine(localAppData, "Programs"), StringComparison.OrdinalIgnoreCase)
            || currentExe.StartsWith(progFiles, StringComparison.OrdinalIgnoreCase)
            || currentExe.StartsWith(progFilesX86, StringComparison.OrdinalIgnoreCase);
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

            // Security (EZMIC-VULN-UPDATER-ASSET-COLLISION): Disambiguate Setup installer vs standalone binary
            if (root.TryGetProperty("assets", out var assets) && assets.ValueKind == JsonValueKind.Array)
            {
                bool isInstalled = IsInstalledVersion();

                // 1. If currently installed via Inno Setup, prioritize the "-Setup.exe" installer asset
                if (isInstalled)
                {
                    foreach (var asset in assets.EnumerateArray())
                    {
                        string name = asset.GetProperty("name").GetString() ?? "";
                        if (name.Contains("Setup", StringComparison.OrdinalIgnoreCase) && name.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
                        {
                            info.DownloadUrl = asset.GetProperty("browser_download_url").GetString() ?? "";
                            info.AssetName = name;
                            break;
                        }
                    }
                }

                // 2. If standalone portable mode, prioritize "EZmic.exe" (or non-Setup .exe)
                if (string.IsNullOrEmpty(info.DownloadUrl))
                {
                    foreach (var asset in assets.EnumerateArray())
                    {
                        string name = asset.GetProperty("name").GetString() ?? "";
                        if (name.Equals("EZmic.exe", StringComparison.OrdinalIgnoreCase))
                        {
                            info.DownloadUrl = asset.GetProperty("browser_download_url").GetString() ?? "";
                            info.AssetName = name;
                            break;
                        }
                    }
                }

                // 3. Fallback to any .exe asset if exact match wasn't found
                if (string.IsNullOrEmpty(info.DownloadUrl))
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

        // Security (EZMIC-VULN-UPDATER-UNSIGNED-EXEC): Validate download origin is strictly GitHub Releases or GitHub CDN
        if (!Uri.TryCreate(downloadUrl, UriKind.Absolute, out var uri) ||
            !uri.Scheme.Equals(Uri.UriSchemeHttps, StringComparison.OrdinalIgnoreCase) ||
            !(uri.Host.Equals("github.com", StringComparison.OrdinalIgnoreCase) ||
              uri.Host.EndsWith(".githubusercontent.com", StringComparison.OrdinalIgnoreCase)))
        {
            throw new InvalidOperationException("Untrusted download source host.");
        }

        // Security (S-12): Use an isolated, randomized private directory rather than a static predictable path
        string updateDir = Path.Combine(Path.GetTempPath(), $"EZmic_Update_{Guid.NewGuid():N}");
        Directory.CreateDirectory(updateDir);

        string fileName = Path.GetFileName(uri.LocalPath);
        if (string.IsNullOrWhiteSpace(fileName) || !fileName.EndsWith(".exe", StringComparison.OrdinalIgnoreCase))
        {
            fileName = "EZmic-update.exe";
        }
        string tempPath = Path.Combine(updateDir, fileName);

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

        // Security: Verify downloaded file has valid Windows PE magic header ('MZ') and valid PE signature
        var fileInfo = new FileInfo(tempPath);
        if (fileInfo.Length < 1024 * 1024)
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

            // Verify PE header offset at 0x3C
            verifyStream.Seek(0x3C, SeekOrigin.Begin);
            byte[] peOffsetBytes = new byte[4];
            verifyStream.Read(peOffsetBytes, 0, 4);
            int peOffset = BitConverter.ToInt32(peOffsetBytes, 0);
            if (peOffset > 0 && peOffset < fileInfo.Length - 4)
            {
                verifyStream.Seek(peOffset, SeekOrigin.Begin);
                byte[] peSignature = new byte[4];
                verifyStream.Read(peSignature, 0, 4);
                // 'P' 'E' 0x00 0x00
                if (peSignature[0] != 0x50 || peSignature[1] != 0x45 || peSignature[2] != 0 || peSignature[3] != 0)
                {
                    verifyStream.Dispose();
                    File.Delete(tempPath);
                    throw new InvalidDataException("Security verification failed: Corrupted PE structure.");
                }
            }
        }

        return tempPath;
    }

    public static void LaunchUpdateAndExit(string updateExePath)
    {
        try
        {
            string currentExe = Environment.ProcessPath ?? "";
            bool isSetupInstaller = Path.GetFileName(updateExePath).Contains("Setup", StringComparison.OrdinalIgnoreCase);

            // Security (EZMIC-VULN-UPDATER-ASSET-COLLISION):
            // If the downloaded update is the Inno Setup installer wizard, launch the installer directly
            // with /CLOSEAPPLICATIONS rather than running the batch replacement script!
            if (isSetupInstaller)
            {
                Process.Start(new ProcessStartInfo
                {
                    FileName = updateExePath,
                    Arguments = "/CLOSEAPPLICATIONS",
                    UseShellExecute = true
                });
                Environment.Exit(0);
                return;
            }

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
