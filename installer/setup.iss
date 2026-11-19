; EZmic Inno Setup Script
; Generates standard Windows Setup Wizard + Native Windows Uninstaller in Settings / Control Panel

#define MyAppName "EZmic"
#define MyAppVersion "1.1.6"
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
OutputBaseFilename=EZmic-v1.1.6-Setup
SetupIconFile=..\windows\MicCompanion\Assets\app.ico
UninstallDisplayIcon={app}\{#MyAppExeName}
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
Source: "..\dist\pc\{#MyAppExeName}"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{autoprograms}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{localappdata}\MicToPC"

[Code]
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
