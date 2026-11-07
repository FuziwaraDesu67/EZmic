using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Net.Http;

namespace MicCompanion.Utils;

public static class DriverInstaller
{
    private const string CableDownloadUrl = "https://download.vb-audio.com/Download_CABLE/VBCABLE_Driver_Pack43.zip";
    private const string CableWebPage = "https://vb-audio.com/Cable/";

    public static async Task<bool> DownloadAndInstallCableAsync(Action<string> log)
    {
        string tempDir = Path.Combine(Path.GetTempPath(), "MicCompanion_Driver");
        string zipPath = Path.Combine(tempDir, "VBCABLE.zip");
        string extractDir = Path.Combine(tempDir, "Extracted");

        try
        {
            if (Directory.Exists(tempDir))
            {
                Directory.Delete(tempDir, true);
            }
            Directory.CreateDirectory(tempDir);
            Directory.CreateDirectory(extractDir);

            log("Downloading VB-Audio Virtual Cable driver...");
            using (var client = new HttpClient())
            {
                client.Timeout = TimeSpan.FromSeconds(30);
                var data = await client.GetByteArrayAsync(CableDownloadUrl);
                await File.WriteAllBytesAsync(zipPath, data);
            }

            log("Extracting driver setup files...");
            ZipFile.ExtractToDirectory(zipPath, extractDir, true);

            string setupExe = Environment.Is64BitOperatingSystem
                ? Path.Combine(extractDir, "VBCABLE_Setup_x64.exe")
                : Path.Combine(extractDir, "VBCABLE_Setup.exe");

            if (!File.Exists(setupExe))
            {
                // Fallback search
                setupExe = Directory.GetFiles(extractDir, "*Setup*.exe", SearchOption.AllDirectories)
                    .OrderByDescending(f => f.Contains("x64", StringComparison.OrdinalIgnoreCase))
                    .FirstOrDefault() ?? "";
            }

            if (!File.Exists(setupExe))
            {
                log("Setup executable not found in archive. Opening official download page...");
                OpenWebPage(CableWebPage);
                return false;
            }

            log("Launching driver installer (Please click Yes on the Windows prompt)...");
            var startInfo = new ProcessStartInfo
            {
                FileName = setupExe,
                UseShellExecute = true,
                Verb = "runas" // Prompt Windows UAC administrator elevation
            };

            using var process = Process.Start(startInfo);
            if (process != null)
            {
                await process.WaitForExitAsync();
                log("Driver installer finished. Refreshing audio endpoints...");
                return true;
            }
        }
        catch (Exception ex)
        {
            log($"Driver download error: {ex.Message}. Opening official download page...");
            OpenWebPage(CableWebPage);
        }

        return false;
    }

    public static void OpenWebPage(string url)
    {
        try
        {
            Process.Start(new ProcessStartInfo
            {
                FileName = url,
                UseShellExecute = true
            });
        }
        catch { }
    }
}
