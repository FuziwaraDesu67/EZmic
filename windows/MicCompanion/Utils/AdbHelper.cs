using System.Diagnostics;
using System.IO;

namespace MicCompanion.Utils;

public static class AdbHelper
{
    private static string? _cachedAdbPath;

    private static void EnsureEmbeddedAdbExtracted(string targetDir)
    {
        try
        {
            if (!Directory.Exists(targetDir))
            {
                Directory.CreateDirectory(targetDir);
            }

            string adbExe = Path.Combine(targetDir, "adb.exe");
            if (File.Exists(adbExe)) return;

            var assembly = typeof(AdbHelper).Assembly;
            string[] resourceNames = assembly.GetManifestResourceNames();

            string[] files = { "adb.exe", "AdbWinApi.dll", "AdbWinUsbApi.dll" };
            foreach (var file in files)
            {
                string resName = resourceNames.FirstOrDefault(r => r.EndsWith(file, StringComparison.OrdinalIgnoreCase)) ?? "";
                if (!string.IsNullOrEmpty(resName))
                {
                    using var stream = assembly.GetManifestResourceStream(resName);
                    if (stream != null)
                    {
                        string outPath = Path.Combine(targetDir, file);
                        using var fileStream = File.Create(outPath);
                        stream.CopyTo(fileStream);
                    }
                }
            }
        }
        catch { }
    }

    public static string GetAdbPath()
    {
        if (_cachedAdbPath != null) return _cachedAdbPath;

        // 1. Check local AppData Android SDK
        string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        string defaultSdkAdb = Path.Combine(localAppData, "Android", "Sdk", "platform-tools", "adb.exe");
        if (File.Exists(defaultSdkAdb))
        {
            _cachedAdbPath = defaultSdkAdb;
            return _cachedAdbPath;
        }

        // 2. Check embedded / extracted ADB in %LOCALAPPDATA%\MicToPC\tools\adb\
        string embeddedAdbDir = Path.Combine(localAppData, "MicToPC", "tools", "adb");
        EnsureEmbeddedAdbExtracted(embeddedAdbDir);
        string embeddedAdb = Path.Combine(embeddedAdbDir, "adb.exe");
        if (File.Exists(embeddedAdb))
        {
            _cachedAdbPath = embeddedAdb;
            return _cachedAdbPath;
        }

        // 3. Check Program Files Android SDK
        string pfAdb = @"C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe";
        if (File.Exists(pfAdb))
        {
            _cachedAdbPath = pfAdb;
            return _cachedAdbPath;
        }

        // 4. Fallback to "adb" in PATH
        _cachedAdbPath = "adb";
        return _cachedAdbPath;
    }

    public static async Task<(bool Success, string Output)> RunAdbCommandAsync(string arguments)
    {
        string adbPath = GetAdbPath();

        try
        {
            using var process = new Process
            {
                StartInfo = new ProcessStartInfo
                {
                    FileName = adbPath,
                    Arguments = arguments,
                    RedirectStandardOutput = true,
                    RedirectStandardError = true,
                    UseShellExecute = false,
                    CreateNoWindow = true
                }
            };

            process.Start();
            string output = await process.StandardOutput.ReadToEndAsync();
            string error = await process.StandardError.ReadToEndAsync();
            await process.WaitForExitAsync();

            bool success = process.ExitCode == 0;
            return (success, string.IsNullOrWhiteSpace(output) ? error : output);
        }
        catch (Exception ex)
        {
            return (false, $"ADB error ({adbPath}): {ex.Message}");
        }
    }

    public static async Task<bool> IsAdbAvailableAsync()
    {
        var (success, _) = await RunAdbCommandAsync("version");
        return success;
    }

    public static async Task<List<string>> GetConnectedDevicesAsync()
    {
        var devices = new List<string>();
        var (success, output) = await RunAdbCommandAsync("devices");
        if (!success) return devices;

        var lines = output.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
        foreach (var line in lines)
        {
            if (line.StartsWith("List of devices") || string.IsNullOrWhiteSpace(line))
                continue;

            var parts = line.Split('\t', StringSplitOptions.RemoveEmptyEntries);
            if (parts.Length >= 2 && parts[1].Trim() == "device")
            {
                devices.Add(parts[0].Trim());
            }
        }

        return devices;
    }

    public static async Task<bool> SetupPortTunnelAsync(int port = 49153)
    {
        // 1. Run adb reverse (tunnels phone:port -> pc:port so phone can connect to 127.0.0.1:port)
        var (revSuccess, _) = await RunAdbCommandAsync($"reverse tcp:{port} tcp:{port}");

        // 2. Also run adb forward (pc:port -> phone:port)
        var (fwdSuccess, _) = await RunAdbCommandAsync($"forward tcp:{port} tcp:{port}");

        return revSuccess || fwdSuccess;
    }

    public static async Task RemovePortTunnelAsync(int port = 49153)
    {
        await RunAdbCommandAsync($"reverse --remove tcp:{port}");
        await RunAdbCommandAsync($"forward --remove tcp:{port}");
    }
}
