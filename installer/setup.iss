; EZmic Inno Setup Script
; Generates standard Windows Setup Wizard + Native Windows Uninstaller in Settings / Control Panel

#define MyAppName "EZmic"
#define MyAppVersion "1.1.8"
#define MyAppPublisher "FuziwaraDesu67"
#define MyAppURL "https://fuziwaradesu67.github.io/EZmic/"
#define MyAppExeName "EZmic.exe"

[Setup]
; NOTE: The value of AppId uniquely identifies this application.
AppId={{9F82B34E-A79B-4D88-9258-29AC543B6E70}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} v{#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
AppUpdatesURL={#MyAppURL}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
OutputDir=..\release
OutputBaseFilename=EZmic-v1.1.8-Setup
SetupIconFile=..\windows\MicCompanion\Assets\app.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=admin
PrivilegesRequiredOverridesAllowed=commandline
CloseApplications=force
RestartApplications=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked
Name: "driver"; Description: "Install EZmic Virtual Microphone (Required for Discord, OBS, Zoom, Games)"

[Files]
Source: "..\dist\pc\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion restartreplace uninsrestartdelete
Source: "..\driver\*"; DestDir: "{tmp}\driver"; Flags: ignoreversion recursesubdirs deleteafterinstall; Tasks: driver

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "powershell.exe"; Parameters: "-ExecutionPolicy Bypass -NoProfile -WindowStyle Hidden -File ""{tmp}\driver\install_ezmic_driver.ps1"" -DriverDir ""{tmp}\driver"""; StatusMsg: "Configuring EZmic Virtual Microphone..."; Flags: runhidden waituntilterminated; Tasks: driver
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{localappdata}\MicToPC"

[Code]
procedure KillRunningApp();
var
  ErrorCode: Integer;
begin
  Exec(ExpandConstant('{sys}\taskkill.exe'), '/f /im EZmic.exe', '', SW_HIDE, ewWaitUntilTerminated, ErrorCode);
  Sleep(500);
end;

// Terminate any running instances of EZmic before starting installation or upgrade
function InitializeSetup(): Boolean;
begin
  KillRunningApp();
  Result := True;
end;

// Terminate any running instances of EZmic before uninstallation
function InitializeUninstall(): Boolean;
begin
  KillRunningApp();
  Result := True;
end;

procedure CurStepChanged(CurStep: TSetupStep);
begin
  if CurStep = ssInstall then
  begin
    KillRunningApp();
    // Clean up legacy per-user v1.1.7 install if present
    DelTree(ExpandConstant('{localappdata}\Programs\EZmic'), True, True, True);
    DeleteFile(ExpandConstant('{userappdata}\Microsoft\Windows\Start Menu\Programs\EZmic.lnk'));
    DeleteFile(ExpandConstant('{userdesktop}\EZmic.lnk'));
  end;
end;

// Clean up all background registry keys and AppData folders when uninstalled
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
begin
  if CurUninstallStep = usPostUninstall then
  begin
    // 1. Remove Run at startup registry value
    RegDeleteValue(HKEY_CURRENT_USER, 'Software\Microsoft\Windows\CurrentVersion\Run', '{#MyAppName}');
    
    // 2. Remove AppData cache folder
    DelTree(ExpandConstant('{localappdata}\MicToPC'), True, True, True);
  end;
end;
