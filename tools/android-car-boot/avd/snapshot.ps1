#requires -Version 5.1
<#
.SYNOPSIS
    Stop the official AVD and export a distributable snapshot zip.

.DESCRIPTION
    Stops the emulator that belongs to -AvdName (adb emu kill, then process and adb
    release are confirmed; only processes whose command line names this AVD are
    touched), copies the AVD directory and the .ini into a staging directory,
    removes volatile files (*.lock, hardware-qemu.ini, *.log, snapshots), adds
    manifest.json (SDK/system image versions, artifact sha256, known modifications)
    and RESTORE.md, then packs everything into a zip with the system tar.exe
    (PowerShell 5.1 Compress-Archive cannot handle >2 GB).

    The zip layout matches ~/.android/avd so a restore is just an extraction:
      <name>.ini, <name>.avd/..., manifest.json, RESTORE.md

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\snapshot.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\snapshot.ps1 -DryRun
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\snapshot.ps1 -ZipPath <path>\psa_api28.zip -Force
#>
[CmdletBinding()]
param(
    [string]$AvdName = "psa_api28",
    [string]$ZipPath = "",
    [string]$SdkRoot = "D:\Tools\Android\sdk",
    [switch]$NoStop,
    [switch]$KeepStage,
    [switch]$Force,
    [switch]$DryRun,
    [int]$TimeoutSec = 120,
    [long]$ChecksumMaxMB = 64
)

$ErrorActionPreference = "Stop"

$SdkRoot = $SdkRoot.TrimEnd('\', '/')
$AvdRoot = Join-Path $env:USERPROFILE ".android\avd"
$AvdDir = Join-Path $AvdRoot ($AvdName + ".avd")
$IniPath = Join-Path $AvdRoot ($AvdName + ".ini")
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$DistRoot = Join-Path $RepoRoot "tools\android-car-boot\dist"
$AdbExe = Join-Path $SdkRoot "platform-tools\adb.exe"
$EmulatorExe = Join-Path $SdkRoot "emulator\emulator.exe"
$Stamp = Get-Date -Format "yyyyMMdd-HHmmss"
if (-not $ZipPath) { $ZipPath = Join-Path $DistRoot ($AvdName + "-" + $Stamp + ".zip") }
$ZipPath = [System.IO.Path]::GetFullPath($ZipPath)
$StageDir = Join-Path $DistRoot ($AvdName + "-" + $Stamp + ".stage")

# volatile files that must not be shipped
$ExcludeNames = @("*.lock", "hardware-qemu.ini", "*.log")
$ExcludeDirs = @("snapshots")

function Write-Step([string]$Text) { Write-Host ("[snapshot] " + $Text) }
function Write-Warn([string]$Text) { Write-Host ("[snapshot] WARN: " + $Text) -ForegroundColor Yellow }
function Fail([string]$Text) {
    Write-Host ("[snapshot] ERROR: " + $Text) -ForegroundColor Red
    exit 1
}

function Get-AvdProcess {
    Get-CimInstance Win32_Process -Filter "Name='emulator.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and ($_.CommandLine -match ('-avd\s+"?' + [regex]::Escape($AvdName) + '"?(\s|$)')) }
}

function Get-AdbText {
    param([string[]]$Arguments)
    if (-not (Test-Path $AdbExe)) { return "" }
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $out = & $AdbExe @Arguments 2>$null
        return (($out | ForEach-Object { "$_" }) -join "`n").Trim()
    } finally {
        $ErrorActionPreference = $prev
    }
}

function Stop-Avd {
    $instances = @(Get-AvdProcess)
    if ($instances.Count -eq 0) {
        Write-Step ("AVD " + $AvdName + " is not running")
        return
    }
    $cmdline = $instances[0].CommandLine
    $oldPort = 5554
    if ($cmdline -match '-port\s+(\d+)') { $oldPort = [int]$Matches[1] }
    $oldSerial = "emulator-" + $oldPort
    Write-Step ("stopping " + $oldSerial + " (pid " + (($instances | ForEach-Object { $_.ProcessId }) -join ",") + ")")
    Get-AdbText @("-s", $oldSerial, "emu", "kill") | Out-Null
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    while ((Get-Date) -lt $deadline -and @(Get-AvdProcess).Count -gt 0) { Start-Sleep -Milliseconds 500 }
    if (@(Get-AvdProcess).Count -gt 0) {
        Write-Warn "adb emu kill did not stop it; terminating the matching emulator process only"
        Get-AvdProcess | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        Start-Sleep -Seconds 2
    }
    if (@(Get-AvdProcess).Count -gt 0) { Fail "failed to stop the emulator" }
    # wait for adb to release the port so the AVD directory is stable
    $deadline = (Get-Date).AddSeconds(30)
    while ((Get-Date) -lt $deadline) {
        $state = Get-AdbText @("-s", $oldSerial, "get-state")
        if ($state -ne "device") { break }
        Start-Sleep -Milliseconds 500
    }
    Start-Sleep -Seconds 1
    Write-Step "stopped"
}

function Get-EmulatorVersion {
    $props = Join-Path $SdkRoot "emulator\source.properties"
    if (-not (Test-Path $props)) { return "(not installed)" }
    $line = Select-String -LiteralPath $props -Pattern '^Pkg\.Revision\s*=' | Select-Object -First 1
    if ($null -eq $line) { return "(unknown)" }
    return ($line.Line -replace '^Pkg\.Revision\s*=\s*', '').Trim()
}

function Get-SystemImageRevision {
    $pkgXml = Join-Path $SdkRoot "system-images\android-28\default\x86_64\package.xml"
    if (-not (Test-Path $pkgXml)) { return "(not installed)" }
    try {
        [xml]$doc = Get-Content -LiteralPath $pkgXml -Raw
        $rev = $doc.SelectSingleNode("/*[local-name()='repository']/*[local-name()='localPackage']/*[local-name()='revision']")
        if ($null -eq $rev) { return "(unknown)" }
        $major = $rev.SelectSingleNode("*[local-name()='major']")
        $minor = $rev.SelectSingleNode("*[local-name()='minor']")
        if ($null -eq $major) { return "(unknown)" }
        $text = $major.InnerText
        if ($null -ne $minor) { $text = $text + "." + $minor.InnerText }
        return $text
    } catch {
        return "(unparsed)"
    }
}

function Get-Sha256([string]$Path) {
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLower()
}

function Get-ArtifactRecord([string]$Path) {
    if ($Path -and (Test-Path -LiteralPath $Path)) {
        $f = Get-Item -LiteralPath $Path
        return [pscustomobject]@{
            path = $Path
            exists = $true
            size = $f.Length
            sha256 = Get-Sha256 $Path
        }
    }
    return [pscustomobject]@{ path = $Path; exists = $false; size = 0; sha256 = "" }
}

function Get-FreeSpaceGB([string]$Path) {
    try {
        $qualifier = (Split-Path -Qualifier $Path).TrimEnd(':')
        $drive = Get-PSDrive -Name $qualifier -ErrorAction Stop
        return [math]::Round($drive.Free / 1GB, 2)
    } catch {
        return -1
    }
}

function Get-DirSizeBytes([string]$Path) {
    $sum = (Get-ChildItem -LiteralPath $Path -Recurse -Force -File -ErrorAction SilentlyContinue |
        Measure-Object Length -Sum).Sum
    if ($null -eq $sum) { return 0 }
    return [long]$sum
}

# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------

Write-Step ("AVD name : " + $AvdName)
Write-Step ("AVD dir  : " + $AvdDir)
Write-Step ("zip path : " + $ZipPath)

if (-not (Test-Path $AvdDir)) { Fail ("AVD directory not found: " + $AvdDir + " (run setup.ps1 first)") }
if (-not (Test-Path $IniPath)) { Fail ("AVD ini not found: " + $IniPath) }
if (-not (Test-Path $DistRoot)) { New-Item -ItemType Directory -Path $DistRoot -Force | Out-Null }
if ((Test-Path $ZipPath) -and -not $Force) { Fail ("zip already exists (use -Force): " + $ZipPath) }

# artifacts referenced by the manifest
$ArtifactJar = Join-Path $RepoRoot "tools\android-car-boot\api\build\android.car.jar"
$ArtifactService = Join-Path $RepoRoot "tools\android-car-boot\service\build\outputs\apk\debug\service-debug.apk"
$ArtifactApp = Join-Path $RepoRoot "app\build\outputs\apk\debug\app-debug.apk"
$ArtifactProbe = Join-Path $RepoRoot "verify\bootinject\out\probe-app.apk"

$avdSize = Get-DirSizeBytes $AvdDir
$iniSize = (Get-Item -LiteralPath $IniPath).Length
$freeGB = Get-FreeSpaceGB $DistRoot

Write-Step ("AVD size : " + [math]::Round($avdSize / 1GB, 2) + " GB (plus ini)")
Write-Step ("free space on target drive: " + $freeGB + " GB")

$running = @(Get-AvdProcess)
if ($running.Count -gt 0) {
    Write-Step ("AVD is running (pid " + (($running | ForEach-Object { $_.ProcessId }) -join ",") + "); it will be stopped")
} else {
    Write-Step "AVD is not running"
}

if ($DryRun) {
    Write-Step "[dry-run] would stop the emulator (if running)"
    Write-Step ("[dry-run] would copy to " + $StageDir)
    Write-Step ("[dry-run] would exclude: " + ($ExcludeNames -join ", ") + "; dirs: " + ($ExcludeDirs -join ", "))
    Write-Step ("[dry-run] would zip to " + $ZipPath)
    Write-Step ("[dry-run] free space needed ~" + [math]::Round(($avdSize * 2) / 1GB, 2) + " GB for staging + zip")
    foreach ($rec in @(
            (Get-ArtifactRecord $ArtifactJar),
            (Get-ArtifactRecord $ArtifactService),
            (Get-ArtifactRecord $ArtifactApp),
            (Get-ArtifactRecord $ArtifactProbe))) {
        if ($rec.exists) {
            Write-Step ("[dry-run] artifact: " + $rec.path + "  " + $rec.size + " B  sha256=" + $rec.sha256)
        } else {
            Write-Step ("[dry-run] artifact missing: " + $rec.path)
        }
    }
    exit 0
}

if (-not $NoStop) {
    Stop-Avd
} else {
    Write-Warn "-NoStop: snapshot may be inconsistent while the emulator is running"
}

# the AVD dir must be stable: snapshot only a stopped AVD
if (@(Get-AvdProcess).Count -gt 0) {
    Fail "AVD is still running; stop it first (run.ps1 -Stop)"
}

$needed = [long]($avdSize * 2 + 200MB)
if ($freeGB -ge 0 -and ($freeGB * 1GB) -lt $needed) {
    Fail ("not enough free space on " + (Split-Path -Qualifier $ZipPath) + ": need ~" +
        [math]::Round($needed / 1GB, 2) + " GB, have " + $freeGB + " GB")
}

# 1. stage a clean copy ------------------------------------------------------
if (Test-Path $StageDir) { Remove-Item -LiteralPath $StageDir -Recurse -Force }
New-Item -ItemType Directory -Path $StageDir -Force | Out-Null
$stageAvd = Join-Path $StageDir ($AvdName + ".avd")
Write-Step ("copying " + $AvdDir + " -> " + $stageAvd)
Copy-Item -LiteralPath $AvdDir -Destination $stageAvd -Recurse -Force
Copy-Item -LiteralPath $IniPath -Destination (Join-Path $StageDir ($AvdName + ".ini")) -Force

foreach ($dir in $ExcludeDirs) {
    $victim = Join-Path $stageAvd $dir
    if (Test-Path $victim) { Remove-Item -LiteralPath $victim -Recurse -Force }
}
foreach ($name in $ExcludeNames) {
    Get-ChildItem -LiteralPath $stageAvd -Recurse -Force -File -Filter $name -ErrorAction SilentlyContinue |
        ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
}

# 2. manifest + RESTORE.md ---------------------------------------------------
$files = @()
Get-ChildItem -LiteralPath $StageDir -Recurse -Force -File | ForEach-Object {
    $rel = $_.FullName.Substring($StageDir.Length + 1) -replace '\\', '/'
    $rec = [ordered]@{ path = $rel; size = $_.Length }
    if ($_.Length -le ($ChecksumMaxMB * 1MB)) { $rec.sha256 = Get-Sha256 $_.FullName }
    $files += $rec
}

$avdConfig = [ordered]@{}
$configPath = Join-Path $stageAvd "config.ini"
if (Test-Path $configPath) {
    $wantedKeys = @(
        "hw.lcd.width", "hw.lcd.height", "hw.lcd.density", "hw.ramSize",
        "disk.dataPartition.size", "hw.gpu.enabled", "hw.gpu.mode",
        "hw.audioInput", "hw.audioOutput", "hw.camera.back", "hw.keyboard",
        "showDeviceFrame", "hw.cpu.ncore", "image.sysdir.1", "tag.id", "abi.type"
    )
    foreach ($line in [System.IO.File]::ReadAllLines($configPath)) {
        $idx = $line.IndexOf('=')
        if ($idx -gt 0) {
            $key = $line.Substring(0, $idx).Trim()
            if ($wantedKeys -contains $key) { $avdConfig[$key] = $line.Substring($idx + 1).Trim() }
        }
    }
}

$jarRec = Get-ArtifactRecord $ArtifactJar
$serviceRec = Get-ArtifactRecord $ArtifactService
$appRec = Get-ArtifactRecord $ArtifactApp
$probeRec = Get-ArtifactRecord $ArtifactProbe

$manifest = [ordered]@{
    schema = 1
    kind = "psa-avd-snapshot"
    created = (Get-Date).ToString("yyyy-MM-ddTHH:mm:sszzz")
    avdName = $AvdName
    emulatorVersion = Get-EmulatorVersion
    systemImage = [ordered]@{
        package = "system-images;android-28;default;x86_64"
        revision = Get-SystemImageRevision
        sdk = 28
    }
    host = [ordered]@{
        sdkRoot = $SdkRoot
        avdRoot = $AvdRoot
        psVersion = $PSVersionTable.PSVersion.ToString()
    }
    zipPath = $ZipPath
    contents = [ordered]@{
        avdIni = ($AvdName + ".ini")
        avdDir = ($AvdName + ".avd")
        manifest = "manifest.json"
        restore = "RESTORE.md"
    }
    exclusions = [ordered]@{
        files = $ExcludeNames
        dirs = $ExcludeDirs
    }
    avdConfig = $avdConfig
    artifacts = [ordered]@{
        apiJar = $jarRec
        serviceApk = $serviceRec
        appApk = $appRec
        probeApk = $probeRec
    }
    knownChanges = @(
        "/system/framework/android.test.base.jar replaced by android.car.jar (minimal bootclasspath mechanism)",
        "/system/framework/x86_64/boot-android.test.base.{art,art.rel,oat,vdex} removed",
        "com.android.car service installed at /system/app/CarService/CarService.apk",
        "APP APK (com.bandwa.openadb) may be installed under /data/app",
        "zygote rc / BOOTCLASSPATH / ramdisk are untouched (stock)"
    )
    restore = "extract into %USERPROFILE%\.android\avd (<name>.ini + <name>.avd) and start with run.ps1"
}

$json = $manifest | ConvertTo-Json -Depth 8
$utf8 = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText((Join-Path $StageDir "manifest.json"), $json + "`n", $utf8)

$restoreText = @'
# AVD snapshot restore notes

This zip is a snapshot of the official Google Android 9 (API 28) userdebug AVD,
provisioned with the project's minimal bootclasspath mechanism:

- `/system/framework/android.test.base.jar` was replaced by the project `android.car.jar`;
- `/system/framework/x86_64/boot-android.test.base.{art,art.rel,oat,vdex}` were removed;
- the `com.android.car` service lives at `/system/app/CarService/CarService.apk`;
- `zygote rc`, `BOOTCLASSPATH` and the ramdisk are untouched (stock).

## Restore steps

1. Install the same SDK components (see `manifest.json`: systemImage.revision and
   emulatorVersion).  `setup.ps1` checks/installs emulator, platform-tools and
   `system-images;android-28;default;x86_64` for you.
2. Stop any emulator that uses this AVD name.
3. Extract this zip into `%USERPROFILE%\.android\avd`, keeping the layout:

   ```
   %USERPROFILE%\.android\avd\<avdName>.ini
   %USERPROFILE%\.android\avd\<avdName>.avd\...
   ```

4. Start it:

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\run.ps1 -AvdName <avdName>
   ```

5. Verify the injection (read-only):

   ```powershell
   python tools\android-car-boot\avd\provision.py --verify --serial emulator-5554
   ```

6. Roll back to a stock system (restore stock jar/slots, remove the system service):
   `provision.py --rollback`.

## Notes

- The snapshot contains a preallocated `userdata-qemu.img` (~8 GB) and the system overlay.
- Before the first boot check `image.sysdir.1` in `config.ini`; on Windows it must be
  `system-images\android-28\default\x86_64\`.
- Never use the same snapshot from two emulator instances at once (`*.lock` is excluded
  and will be recreated on start).
- AEHD 2.2 or WHPX is required, otherwise the AVD is extremely slow or fails to boot.
'@
[System.IO.File]::WriteAllText((Join-Path $StageDir "RESTORE.md"), $restoreText, $utf8)

# 3. zip ---------------------------------------------------------------------
$tar = (Get-Command tar.exe -ErrorAction SilentlyContinue).Source
if (-not $tar) {
    $tar = Join-Path $env:SystemRoot "system32\tar.exe"
}
if (-not (Test-Path $tar)) { Fail "tar.exe not found; cannot create a >2 GB compatible zip" }

$zipDir = Split-Path -Parent $ZipPath
if ($zipDir -and -not (Test-Path $zipDir)) { New-Item -ItemType Directory -Path $zipDir -Force | Out-Null }
if ((Test-Path $ZipPath) -and $Force) { Remove-Item -LiteralPath $ZipPath -Force }

Write-Step ("zipping " + $StageDir + " -> " + $ZipPath)
& $tar -a -c -f $ZipPath -C $StageDir .
if ($LASTEXITCODE -ne 0) { Fail ("tar failed (exit " + $LASTEXITCODE + ")") }
if (-not (Test-Path $ZipPath)) { Fail "zip was not created" }

$zipItem = Get-Item -LiteralPath $ZipPath
Write-Step ("zip size : " + [math]::Round($zipItem.Length / 1GB, 2) + " GB (" + $zipItem.Length + " B)")
$zipSha = Get-Sha256 $ZipPath
Write-Step ("zip sha256: " + $zipSha)

if (-not $KeepStage) {
    Write-Step "removing staging directory (use -KeepStage to keep it)"
    Remove-Item -LiteralPath $StageDir -Recurse -Force
} else {
    Write-Step ("staging directory kept: " + $StageDir)
}

Write-Step "done"
exit 0
