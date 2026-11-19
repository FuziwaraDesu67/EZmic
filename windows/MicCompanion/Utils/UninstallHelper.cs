using Microsoft.Win32;
using System.Diagnostics;
using System.IO;

namespace MicCompanion.Utils;

public static class UninstallHelper
{
    public static void CleanAllUserData()
    {
        // 1. Remove Run at startup registry key
        try
        {
            StartupHelper.SetRunAtStartup(false);
        }
        catch { }

        // 2. Remove ADB tunnels if any
        try
        {
            _ = AdbHelper.RemovePortTunnelAsync();
        }
        catch { }

        // 3. Remove Desktop shortcuts
        try
        {
            string userProfile = Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
            string[] shortcutPaths =
            {
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.Desktop), "EZmic.lnk"),
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.CommonDesktopDirectory), "EZmic.lnk"),
                Path.Combine(userProfile, "OneDrive", "Desktop", "EZmic.lnk"),
                Path.Combine(userProfile, "Desktop", "EZmic.lnk")
            };

            foreach (var path in shortcutPaths)
            {
                if (File.Exists(path))
                {
                    File.Delete(path);
                }
            }
        }
        catch { }

        // 4. Remove Start Menu shortcuts
        try
        {
            string startMenuPrograms = Environment.GetFolderPath(Environment.SpecialFolder.Programs);
            string shortcut = Path.Combine(startMenuPrograms, "EZmic.lnk");
            if (File.Exists(shortcut)) File.Delete(shortcut);

            string folder = Path.Combine(startMenuPrograms, "EZmic");
            if (Directory.Exists(folder)) Directory.Delete(folder, true);
        }
        catch { }

        // 5. Clean up AppData cache
        try
        {
            string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            string micToPc = Path.Combine(localAppData, "MicToPC");
            if (Directory.Exists(micToPc))
            {
                Directory.Delete(micToPc, true);
            }
        }
        catch { }
    }

    public static void UninstallAndSelfDelete()
    {
        CleanAllUserData();

        string? currentExe = Environment.ProcessPath;
        if (!string.IsNullOrEmpty(currentExe) && File.Exists(currentExe))
        {
            ProcessStartInfo psi = new()
            {
                FileName = "cmd.exe",
                Arguments = $"/c timeout /t 1 /nobreak > nul & del /f /q \"{currentExe}\"",
                CreateNoWindow = true,
                UseShellExecute = false,
                WindowStyle = ProcessWindowStyle.Hidden
            };
            Process.Start(psi);
        }

        Environment.Exit(0);
    }
}
