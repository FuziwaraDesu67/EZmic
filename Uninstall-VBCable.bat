@echo off
title Complete VB-CABLE Wipe & Clean Uninstaller
:: Check for administrator permissions
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo Requesting Administrator privileges to completely delete VB-CABLE...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

if exist "%~dp0tools\wipe_vbcable.ps1" (
    powershell.exe -ExecutionPolicy Bypass -NoProfile -File "%~dp0tools\wipe_vbcable.ps1"
) else if exist "d:\games\mic\tools\wipe_vbcable.ps1" (
    powershell.exe -ExecutionPolicy Bypass -NoProfile -File "d:\games\mic\tools\wipe_vbcable.ps1"
) else (
    powershell.exe -ExecutionPolicy Bypass -NoProfile -Command ^
        "Write-Host '[1/4] Running VB-CABLE uninstaller...' -ForegroundColor Cyan; " ^
        "Start-Process 'C:\Program Files\VB\CABLE\VBCABLE_Setup_x64.exe' -ArgumentList '-u', '-h' -Wait -ErrorAction SilentlyContinue; " ^
        "Write-Host '[2/4] Removing driver packages...' -ForegroundColor Cyan; " ^
        "pnputil /enum-drivers | Select-String -Pattern 'vbmmecable' -Context 3,0 | ForEach-Object { if ($_.Context.PreContext -join ' ' -match 'oem\d+\.inf') { pnputil /delete-driver $matches[0] /uninstall /force } }; " ^
        "Write-Host '[3/4] Deleting folders and registry...' -ForegroundColor Cyan; " ^
        "Remove-Item -Path 'C:\Program Files\VB' -Recurse -Force -ErrorAction SilentlyContinue; " ^
        "Remove-Item -Path 'HKLM:\SOFTWARE\VB-Audio' -Recurse -Force -ErrorAction SilentlyContinue; " ^
        "Write-Host '[4/4] Restarting Audio services...' -ForegroundColor Cyan; " ^
        "Restart-Service AudioSrv -Force -ErrorAction SilentlyContinue; " ^
        "Write-Host 'VB-CABLE has been completely removed!' -ForegroundColor Green;"
)

echo.
pause
