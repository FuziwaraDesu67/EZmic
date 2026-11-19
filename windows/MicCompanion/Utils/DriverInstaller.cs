using System.Diagnostics;

namespace MicCompanion.Utils;

/// <summary>
/// Handles virtual audio cable driver guidance.
/// Directly directs users to the official vendor portal to ensure installation
/// of the latest certified, patched driver (resolving S-05 and mitigating CVE-2026-124019).
/// </summary>
public static class DriverInstaller
{
    public const string OfficialCableWebPage = "https://vb-audio.com/Cable/";

    public static void OpenOfficialDownloadPage(Action<string> log)
    {
        try
        {
            log("Opening official VB-Audio CABLE portal (https://vb-audio.com/Cable/)...");
            log("Please install the official vendor driver, then return here to select your endpoint.");
            Process.Start(new ProcessStartInfo
            {
                FileName = OfficialCableWebPage,
                UseShellExecute = true
            });
        }
        catch (Exception ex)
        {
            log($"Could not open browser: {ex.Message}. Please visit: {OfficialCableWebPage}");
        }
    }
}
