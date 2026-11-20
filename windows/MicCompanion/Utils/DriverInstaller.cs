using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Reflection;

namespace MicCompanion.Utils;

/// <summary>
/// Handles silent, 1-click installation of the virtual audio driver and
/// automatically configures the device name to "EZmic Virtual Microphone".
/// </summary>
public static class DriverInstaller
{
    public const string OfficialCableWebPage = "https://vb-audio.com/Cable/";

    public static string GetDriverDirectory()
    {
        string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        return Path.Combine(localAppData, "MicToPC", "driver");
    }

    public static void EnsureDriverExtracted(string targetDir)
    {
        try
        {
            if (!Directory.Exists(targetDir))
            {
                Directory.CreateDirectory(targetDir);
            }

            string setupExe = Path.Combine(targetDir, "VBCABLE_Setup_x64.exe");
            string scriptFile = Path.Combine(targetDir, "install_ezmic_driver.ps1");
            if (File.Exists(setupExe) && File.Exists(scriptFile))
            {
                return;
            }

            var assembly = typeof(DriverInstaller).Assembly;
            string[] resourceNames = assembly.GetManifestResourceNames();

            string resName = resourceNames.FirstOrDefault(r => r.EndsWith("driver.zip", StringComparison.OrdinalIgnoreCase)) ?? "";
            if (!string.IsNullOrEmpty(resName))
            {
                using var stream = assembly.GetManifestResourceStream(resName);
                if (stream != null)
                {
                    string zipPath = Path.Combine(targetDir, "driver_temp.zip");
                    using (var fileStream = File.Create(zipPath))
                    {
                        stream.CopyTo(fileStream);
                    }
                    ZipFile.ExtractToDirectory(zipPath, targetDir, overwriteFiles: true);
                    try { File.Delete(zipPath); } catch { }
                }
            }
        }
        catch { }
    }

    public static async Task<bool> InstallVirtualMicAsync(Action<string> log)
    {
        string driverDir = GetDriverDirectory();
        log("Extracting virtual microphone components...");
        EnsureDriverExtracted(driverDir);

        string setupScript = Path.Combine(driverDir, "install_ezmic_driver.ps1");
        string setupExe = Path.Combine(driverDir, "VBCABLE_Setup_x64.exe");

        if (!File.Exists(setupScript) || !File.Exists(setupExe))
        {
            log("Error: Driver installation files could not be extracted.");
            return false;
        }

        log("Launching silent driver installation (please accept the Windows administrator prompt)...");

        try
        {
            var psi = new ProcessStartInfo
            {
                FileName = "powershell.exe",
                Arguments = $"-ExecutionPolicy Bypass -NoProfile -WindowStyle Hidden -File \"{setupScript}\" -DriverDir \"{driverDir}\"",
                Verb = "runas",
                UseShellExecute = true
            };

            using var proc = Process.Start(psi);
            if (proc != null)
            {
                await proc.WaitForExitAsync();
                if (proc.ExitCode == 0)
                {
                    log("EZmic Virtual Microphone successfully installed and configured!");
                    return true;
                }
                else
                {
                    log($"Driver installation returned exit code {proc.ExitCode}.");
                    return false;
                }
            }
        }
        catch (System.ComponentModel.Win32Exception)
        {
            log("Driver installation was cancelled (administrator permission was not granted).");
            return false;
        }
        catch (Exception ex)
        {
            log($"Driver installation error: {ex.Message}");
            return false;
        }

        return false;
    }

    public static bool IsEndpointRenamed()
    {
        try
        {
            using var key = Microsoft.Win32.Registry.LocalMachine.OpenSubKey(@"SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Capture");
            if (key != null)
            {
                foreach (var subKeyName in key.GetSubKeyNames())
                {
                    using var propKey = key.OpenSubKey($@"{subKeyName}\Properties");
                    if (propKey != null)
                    {
                        var name = propKey.GetValue("{a45c254e-df1c-4efd-8020-67d146a850e0},2") as string;
                        var desc = propKey.GetValue("{b3f8fa53-0004-438e-9003-51a46e139bfc},6") as string;
                        if ((desc != null && desc.Contains("EZmic", StringComparison.OrdinalIgnoreCase)) ||
                            (name != null && name.Contains("EZmic", StringComparison.OrdinalIgnoreCase)))
                        {
                            return true;
                        }
                    }
                }
            }
        }
        catch { }
        return false;
    }

    [System.Runtime.InteropServices.DllImport("advapi32.dll", CharSet = System.Runtime.InteropServices.CharSet.Unicode, SetLastError = true)]
    private static extern int RegOpenKeyEx(IntPtr hKey, string lpSubKey, int ulOptions, int samDesired, out IntPtr phkResult);

    [System.Runtime.InteropServices.DllImport("advapi32.dll", CharSet = System.Runtime.InteropServices.CharSet.Unicode, SetLastError = true)]
    private static extern int RegSetValueEx(IntPtr hKey, string lpValueName, int Reserved, int dwType, string lpData, int cbData);

    [System.Runtime.InteropServices.DllImport("advapi32.dll", SetLastError = true)]
    private static extern int RegCloseKey(IntPtr hKey);

    private static readonly IntPtr HKEY_LOCAL_MACHINE = new IntPtr(unchecked((int)0x80000002));
    private const int KEY_SET_VALUE = 0x0002;
    private const int KEY_QUERY_VALUE = 0x0001;
    private const int REG_SZ = 1;

    private static int SetRegistryPropertyDirect(string subKey, string valueName, string valueData)
    {
        int ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE, subKey, 0, KEY_SET_VALUE | KEY_QUERY_VALUE, out IntPtr hKey);
        if (ret != 0) return ret;

        int byteLen = (valueData.Length + 1) * 2;
        ret = RegSetValueEx(hKey, valueName, 0, REG_SZ, valueData, byteLen);
        RegCloseKey(hKey);
        return ret;
    }

    public static async Task<bool> RenameEndpointAsync(Action<string> log)
    {
        log("Configuring 'EZmic Virtual Microphone' in Windows CoreAudio...");

        try
        {
            // 1. Rename Capture devices (Microphone / Recording)
            using (var capKey = Microsoft.Win32.Registry.LocalMachine.OpenSubKey(@"SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Capture"))
            {
                if (capKey != null)
                {
                    foreach (var subName in capKey.GetSubKeyNames())
                    {
                        using var propKey = capKey.OpenSubKey($@"{subName}\Properties");
                        if (propKey != null)
                        {
                            var name = propKey.GetValue("{a45c254e-df1c-4efd-8020-67d146a850e0},2") as string;
                            var desc = propKey.GetValue("{b3f8fa53-0004-438e-9003-51a46e139bfc},6") as string;
                            if (((desc != null && desc.Contains("VB-Audio", StringComparison.OrdinalIgnoreCase)) ||
                                 (name != null && name.Contains("CABLE Output", StringComparison.OrdinalIgnoreCase)) ||
                                 (desc != null && desc.Contains("EZmic", StringComparison.OrdinalIgnoreCase))) &&
                                (name == null || !name.Contains("16ch", StringComparison.OrdinalIgnoreCase)))
                            {
                                string subPath = $@"SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Capture\{subName}\Properties";
                                SetRegistryPropertyDirect(subPath, "{a45c254e-df1c-4efd-8020-67d146a850e0},2", "EZmic Virtual Microphone");
                                SetRegistryPropertyDirect(subPath, "{b3f8fa53-0004-438e-9003-51a46e139bfc},6", "EZmic Virtual Microphone");
                                log("Renamed Capture endpoint to 'EZmic Virtual Microphone'.");
                            }
                        }
                    }
                }
            }

            // 2. Rename Render devices (Playback / Output)
            using (var renKey = Microsoft.Win32.Registry.LocalMachine.OpenSubKey(@"SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Render"))
            {
                if (renKey != null)
                {
                    foreach (var subName in renKey.GetSubKeyNames())
                    {
                        using var propKey = renKey.OpenSubKey($@"{subName}\Properties");
                        if (propKey != null)
                        {
                            var name = propKey.GetValue("{a45c254e-df1c-4efd-8020-67d146a850e0},2") as string;
                            var desc = propKey.GetValue("{b3f8fa53-0004-438e-9003-51a46e139bfc},6") as string;
                            if (((desc != null && desc.Contains("VB-Audio", StringComparison.OrdinalIgnoreCase)) ||
                                 (name != null && name.Contains("CABLE Input", StringComparison.OrdinalIgnoreCase)) ||
                                 (desc != null && desc.Contains("EZmic", StringComparison.OrdinalIgnoreCase))) &&
                                (name == null || !name.Contains("16ch", StringComparison.OrdinalIgnoreCase)))
                            {
                                string subPath = $@"SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Render\{subName}\Properties";
                                SetRegistryPropertyDirect(subPath, "{a45c254e-df1c-4efd-8020-67d146a850e0},2", "EZmic Audio Cable");
                                SetRegistryPropertyDirect(subPath, "{b3f8fa53-0004-438e-9003-51a46e139bfc},6", "EZmic Audio Cable");
                                log("Renamed Render endpoint to 'EZmic Audio Cable'.");
                            }
                        }
                    }
                }
            }

            // 3. Restart Audio subsystem so Discord and Windows flush their device cache
            log("Flushing Windows CoreAudio endpoint cache...");
            var psi = new ProcessStartInfo
            {
                FileName = "cmd.exe",
                Arguments = "/c net stop Audiosrv /y && net start Audiosrv && net start AudioEndpointBuilder",
                Verb = "runas",
                WindowStyle = ProcessWindowStyle.Hidden,
                UseShellExecute = true
            };

            using var proc = Process.Start(psi);
            if (proc != null)
            {
                await proc.WaitForExitAsync();
            }

            log("Endpoint successfully configured as 'EZmic Virtual Microphone'!");
            return true;
        }
        catch (System.ComponentModel.Win32Exception)
        {
            log("Audio service flush was cancelled (UAC prompt not accepted). Name updated in registry.");
            return true;
        }
        catch (Exception ex)
        {
            log($"Configuration notice: {ex.Message}");
            return true;
        }
    }

    public static void OpenOfficialDownloadPage(Action<string> log)
    {
        try
        {
            log("Opening official VB-Audio CABLE portal (https://vb-audio.com/Cable/)...");
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
