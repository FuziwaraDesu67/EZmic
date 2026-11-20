# install_ezmic_driver.ps1
# Silently installs the virtual audio driver and renames endpoints to EZmic Virtual Microphone

param(
    [string]$DriverDir = $PSScriptRoot
)

$setupExe = Join-Path $DriverDir "VBCABLE_Setup_x64.exe"
if (Test-Path $setupExe) {
    # 1. Run silent driver setup (-i = install, -h = hide/silent)
    Write-Output "Installing virtual audio driver..."
    $proc = Start-Process -FilePath $setupExe -ArgumentList "-i", "-h" -Wait -PassThru -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
}

# 2. Rename CoreAudio endpoints using Win32 API (KEY_SET_VALUE | KEY_QUERY_VALUE)
Write-Output "Configuring EZmic device names in Windows..."

$win32Code = @"
using System;
using System.Runtime.InteropServices;

public class CoreAudioReg
{
    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern int RegOpenKeyEx(IntPtr hKey, string lpSubKey, int ulOptions, int samDesired, out IntPtr phkResult);

    [DllImport("advapi32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern int RegSetValueEx(IntPtr hKey, string lpValueName, int Reserved, int dwType, string lpData, int cbData);

    [DllImport("advapi32.dll", SetLastError = true)]
    public static extern int RegCloseKey(IntPtr hKey);

    public static readonly IntPtr HKEY_LOCAL_MACHINE = new IntPtr(unchecked((int)0x80000002));
    public const int KEY_SET_VALUE = 0x0002;
    public const int KEY_QUERY_VALUE = 0x0001;
    public const int REG_SZ = 1;

    public static int SetValue(string subKey, string valueName, string valueData)
    {
        IntPtr hKey;
        int ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE, subKey, 0, KEY_SET_VALUE | KEY_QUERY_VALUE, out hKey);
        if (ret != 0) return ret;

        int byteLen = (valueData.Length + 1) * 2;
        ret = RegSetValueEx(hKey, valueName, 0, REG_SZ, valueData, byteLen);
        RegCloseKey(hKey);
        return ret;
    }
}
"@

if (-not ([System.Management.Automation.PSTypeName]'CoreAudioReg').Type) {
    Add-Type -TypeDefinition $win32Code
}

# Rename Capture (Recording / Microphone -> EZmic Virtual Microphone)
$captureKey = "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Capture"
if (Test-Path $captureKey) {
    Get-ChildItem $captureKey -ErrorAction SilentlyContinue | ForEach-Object {
        $propsPath = Join-Path $_.PSPath "Properties"
        $p = Get-ItemProperty $propsPath -ErrorAction SilentlyContinue
        if ($p) {
            $desc = $p.'{b3f8fa53-0004-438e-9003-51a46e139bfc},6'
            $name = $p.'{a45c254e-df1c-4efd-8020-67d146a850e0},2'
            if (($desc -like "*VB-Audio*" -or $name -like "*CABLE Output*" -or $desc -like "*EZmic*") -and $name -notlike "*16ch*") {
                $subKey = "SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Capture\$($_.PSChildName)\Properties"
                [CoreAudioReg]::SetValue($subKey, "{a45c254e-df1c-4efd-8020-67d146a850e0},2", "EZmic Virtual Microphone")
                [CoreAudioReg]::SetValue($subKey, "{b3f8fa53-0004-438e-9003-51a46e139bfc},6", "EZmic Virtual Microphone")
                Write-Output "Renamed Capture endpoint to: EZmic Virtual Microphone"
            }
        }
    }
}

# Rename Render (Playback / Audio Cable -> EZmic Audio Cable)
$renderKey = "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Render"
if (Test-Path $renderKey) {
    Get-ChildItem $renderKey -ErrorAction SilentlyContinue | ForEach-Object {
        $propsPath = Join-Path $_.PSPath "Properties"
        $p = Get-ItemProperty $propsPath -ErrorAction SilentlyContinue
        if ($p) {
            $desc = $p.'{b3f8fa53-0004-438e-9003-51a46e139bfc},6'
            $name = $p.'{a45c254e-df1c-4efd-8020-67d146a850e0},2'
            if (($desc -like "*VB-Audio*" -or $name -like "*CABLE Input*" -or $desc -like "*EZmic*") -and $name -notlike "*16ch*") {
                $subKey = "SOFTWARE\Microsoft\Windows\CurrentVersion\MMDevices\Audio\Render\$($_.PSChildName)\Properties"
                [CoreAudioReg]::SetValue($subKey, "{a45c254e-df1c-4efd-8020-67d146a850e0},2", "EZmic Audio Cable")
                [CoreAudioReg]::SetValue($subKey, "{b3f8fa53-0004-438e-9003-51a46e139bfc},6", "EZmic Audio Cable")
                Write-Output "Renamed Render endpoint to: EZmic Audio Cable"
            }
        }
    }
}

# 3. Refresh Windows Audio Service so Discord and Windows reload the new names immediately
Write-Output "Refreshing Windows Audio subsystem..."
Restart-Service AudioEndpointBuilder -Force -ErrorAction SilentlyContinue
Restart-Service AudioSrv -Force -ErrorAction SilentlyContinue

Write-Output "EZmic virtual microphone configuration complete."
exit 0
