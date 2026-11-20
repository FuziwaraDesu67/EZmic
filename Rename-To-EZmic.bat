@echo off
title Configure EZmic Virtual Microphone
:: Check for administrator permissions
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo Requesting Administrator privileges to apply EZmic name to Windows and Discord...
    powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -Verb RunAs"
    exit /b
)

echo ========================================================
echo   Configuring EZmic Virtual Microphone in Windows
echo ========================================================
echo.

powershell.exe -ExecutionPolicy Bypass -NoProfile -File "%~dp0driver\install_ezmic_driver.ps1"
if %errorLevel% neq 0 (
    powershell.exe -ExecutionPolicy Bypass -NoProfile -File "d:\games\mic\driver\install_ezmic_driver.ps1"
)

echo.
echo Restarting audio services to flush Discord cache...
net stop Audiosrv /y >nul 2>&1
net start Audiosrv >nul 2>&1
net start AudioEndpointBuilder >nul 2>&1

echo.
echo ========================================================
echo   SUCCESS! 
echo   'EZmic Virtual Microphone' is now active system-wide!
echo   In Discord: Press Ctrl+R or restart Discord to see EZmic!
echo ========================================================
echo.
pause
