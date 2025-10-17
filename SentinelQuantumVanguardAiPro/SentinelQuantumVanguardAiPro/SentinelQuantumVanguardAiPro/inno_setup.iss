
[Setup]
AppName=Sentinelle Quantum Vanguard AI Pro
AppVersion=1.0
DefaultDirName={pf}\SentinelleQuantum
DefaultGroupName=Sentinelle
OutputDir=.
OutputBaseFilename=sentinelle_setup
SetupIconFile=Sentinelle.ico
Compression=lzma
SolidCompression=yes

[Files]
Source: "sentinelle_pc_flask.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "Sentinel Quantum Vanguard AI Pro.png"; DestDir: "{app}"

[Icons]
Name: "{group}\Sentinelle AI Pro"; Filename: "{app}\sentinelle_pc_flask.exe"
Name: "{userdesktop}\Sentinelle AI Pro"; Filename: "{app}\sentinelle_pc_flask.exe"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Créer un raccourci sur le Bureau"; GroupDescription: "Options supplémentaires :"
