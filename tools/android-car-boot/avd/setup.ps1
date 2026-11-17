#requires -Version 5.1
<#
.SYNOPSIS
    Create / repair the official Google Android 9 (API 28) userdebug AVD.

.DESCRIPTION
    Installs the required SDK packages (emulator, platform-tools, API 28 default
    x86_64 system image), creates the AVD with the automotive_1024p_landscape
    hardware profile and writes the config.ini overrides required by
    tools/android-car-boot/avd (1920x720 @213dpi, 4 GB RAM, 8 GB data, swiftshader,
    audio/camera off).  Checks AEHD acceleration.

    Idempotent: installed packages and existing config values are kept unless -Force.
    The script never starts the emulator (use run.ps1) and never provisions the
    bootclasspath slot (use provision.py).

    The default AVD name is psa_api28.  A running AVD is never modified.

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\setup.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\setup.ps1 -VerifyOnly
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\setup.ps1 -Force -DryRun
#>
[CmdletBinding()]
param(
    [string]$AvdName = "psa_api28",
    [string]$SdkRoot = "D:\Tools\Android\sdk",
    [string]$JavaHome = "D:\Tools\Android\jdk-17\jdk-17.0.21+9",
    [switch]$Force,
    [switch]$VerifyOnly,
    [switch]$SkipSdkInstall,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$SdkRoot = $SdkRoot.TrimEnd('\', '/')
$SdkManager = Join-Path $SdkRoot "cmdline-tools\latest\bin\sdkmanager.bat"
$AvdManager = Join-Path $SdkRoot "cmdline-tools\latest\bin\avdmanager.bat"
$EmulatorExe = Join-Path $SdkRoot "emulator\emulator.exe"
$AvdRoot = Join-Path $env:USERPROFILE ".android\avd"
$AvdDir = Join-Path $AvdRoot ($AvdName + ".avd")
$IniPath = Join-Path $AvdRoot ($AvdName + ".ini")
$ConfigPath = Join-Path $AvdDir "config.ini"

$SystemImage = "system-images;android-28;default;x86_64"
$AvdDevice = "automotive_1024p_landscape"
$RequiredPackages = @("emulator", "platform-tools", $SystemImage)

# config.ini overrides required for the AVD fast path
$DesiredConfig = [ordered]@{
    "hw.lcd.width"              = "1920"
    "hw.lcd.height"             = "720"
    "hw.lcd.density"            = "213"
    "hw.ramSize"                = "4096M"
    "disk.dataPartition.size"   = "8G"
    "hw.gpu.enabled"            = "yes"
    "hw.gpu.mode"               = "swiftshader_indirect"
    "hw.audioInput"             = "no"
    "hw.audioOutput"            = "no"
    "hw.camera.back"            = "none"
    "hw.keyboard"               = "yes"
    "showDeviceFrame"           = "no"
}

function Write-Step([string]$Text) { Write-Host ("[setup] " + $Text) }
function Write-Warn([string]$Text) { Write-Host ("[setup] WARN: " + $Text) -ForegroundColor Yellow }
function Fail([string]$Text) {
    Write-Host ("[setup] ERROR: " + $Text) -ForegroundColor Red
    exit 1
}

function Invoke-Native {
    param([string]$Exe, [string[]]$Arguments, [int]$TimeoutSec = 600)
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $out = & $Exe @Arguments 2>&1 | ForEach-Object { "$_" }
        $code = $LASTEXITCODE
        return [pscustomobject]@{ Code = $code; Text = (($out -join "`n").Trim()) }
    } finally {
        $ErrorActionPreference = $prev
    }
}

function Get-AvdProcess {
    Get-CimInstance Win32_Process -Filter "Name='emulator.exe' OR Name='qemu-system-x86_64.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and ($_.CommandLine -match ('-avd\s+"?' + [regex]::Escape($AvdName) + '"?(\s|$)')) }
}

function Test-AvdRunning {
    return [bool](Get-AvdProcess)
}

function Get-InstalledPackages {
    $r = Invoke-Native $SdkManager @("--sdk_root=$SdkRoot", "--list_installed") 300
    if ($r.Code -ne 0) { Fail ("sdkmanager --list_installed failed (exit " + $r.Code + "): " + $r.Text) }
    return $r.Text
}

function Test-PackageInstalled([string]$ListText, [string]$Package) {
    return [bool]($ListText -match ('(?m)^\s*' + [regex]::Escape($Package) + '\s'))
}

function Get-SystemImageRevision {
    $pkgXml = Join-Path $SdkRoot ("system-images\android-28\default\x86_64\package.xml")
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

function Get-EmulatorVersion {
    $props = Join-Path $SdkRoot "emulator\source.properties"
    if (-not (Test-Path $props)) { return "(not installed)" }
    $line = Select-String -LiteralPath $props -Pattern '^Pkg\.Revision\s*=' | Select-Object -First 1
    if ($null -eq $line) { return "(unknown)" }
    return ($line.Line -replace '^Pkg\.Revision\s*=\s*', '').Trim()
}

function Test-Aehd {
    $check = Join-Path $SdkRoot "emulator\emulator-check.exe"
    if (Test-Path $check) {
        $r = Invoke-Native $check @("accel") 60
        return [pscustomobject]@{ Code = $r.Code; Text = $r.Text }
    }
    return [pscustomobject]@{ Code = -1; Text = "emulator-check.exe not found" }
}

function Read-ConfigLines {
    if (-not (Test-Path $ConfigPath)) { return @() }
    return @([System.IO.File]::ReadAllLines($ConfigPath))
}

function Write-ConfigLines([string[]]$Lines) {
    $text = ($Lines -join "`n")
    if (-not $text.EndsWith("`n")) { $text = $text + "`n" }
    $utf8 = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($ConfigPath, $text, $utf8)
}

function Set-DesiredConfig {
    $lines = New-Object System.Collections.Generic.List[string]
    if (Test-Path $ConfigPath) {
        foreach ($l in [System.IO.File]::ReadAllLines($ConfigPath)) { $lines.Add($l) }
    }
    $changed = New-Object System.Collections.Generic.List[string]
    foreach ($key in $DesiredConfig.Keys) {
        $value = $DesiredConfig[$key]
        $pattern = '^' + [regex]::Escape($key) + '='
        $idx = -1
        for ($i = 0; $i -lt $lines.Count; $i++) {
            if ($lines[$i] -match $pattern) { $idx = $i; break }
        }
        $wanted = $key + "=" + $value
        if ($idx -lt 0) {
            $lines.Add($wanted)
            $changed.Add($key + ": (missing) -> " + $value)
        } elseif ($lines[$idx].Trim() -ne $wanted) {
            $changed.Add($key + ": " + $lines[$idx].Trim() + " -> " + $value)
            $lines[$idx] = $wanted
        }
    }
    return [pscustomobject]@{ Lines = $lines.ToArray(); Changed = $changed.ToArray() }
}

function Get-ConfigMismatches {
    $result = New-Object System.Collections.Generic.List[string]
    if (-not (Test-Path $ConfigPath)) { return @("config.ini missing") }
    $lines = [System.IO.File]::ReadAllLines($ConfigPath)
    foreach ($key in $DesiredConfig.Keys) {
        $pattern = '^' + [regex]::Escape($key) + '='
        $matched = $null
        foreach ($l in $lines) { if ($l -match $pattern) { $matched = $l.Trim(); break } }
        $wanted = $key + "=" + $DesiredConfig[$key]
        if ($null -eq $matched) { $result.Add($key + " missing (want " + $wanted + ")") }
        elseif ($matched -ne $wanted) { $result.Add($matched + " (want " + $wanted + ")") }
    }
    return $result.ToArray()
}

function Get-FreeSpaceGB([string]$Path) {
    try {
        $qualifier = (Split-Path -Qualifier $Path).TrimEnd(':')
        $drive = Get-PSDrive -Name $qualifier -ErrorAction Stop
        return [math]::Round($drive.Free / 1GB, 1)
    } catch {
        return -1
    }
}

# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------

Write-Step ("AVD name   : " + $AvdName)
Write-Step ("SDK root   : " + $SdkRoot)
Write-Step ("system image: " + $SystemImage)
Write-Step ("AVD dir    : " + $AvdDir)

if (-not (Test-Path $SdkRoot)) { Fail ("SDK root not found: " + $SdkRoot) }
if (-not (Test-Path $AvdManager)) { Fail ("avdmanager not found: " + $AvdManager) }
if (-not (Test-Path $SdkManager)) { Fail ("sdkmanager not found: " + $SdkManager) }

# JDK17 is required by avdmanager/sdkmanager on this host
$javaExe = Join-Path $JavaHome "bin\java.exe"
if (-not (Test-Path $javaExe)) {
    Fail ("JDK 17 not found: " + $javaExe + " (use -JavaHome to override)")
}
$env:JAVA_HOME = $JavaHome
$env:PATH = (Join-Path $JavaHome "bin") + ";" + $env:PATH

$running = Test-AvdRunning
if ($running) {
    $pids = (Get-AvdProcess | ForEach-Object { $_.ProcessId }) -join ","
    Write-Step ("AVD is running (pid " + $pids + ")")
} else {
    Write-Step "AVD is not running"
}

if ($VerifyOnly) {
    $problems = New-Object System.Collections.Generic.List[string]

    $listText = Get-InstalledPackages
    foreach ($pkg in $RequiredPackages) {
        if (-not (Test-PackageInstalled $listText $pkg)) { $problems.Add("SDK package missing: " + $pkg) }
    }
    if (-not (Test-Path $EmulatorExe)) { $problems.Add("emulator.exe missing: " + $EmulatorExe) }
    if (-not (Test-Path $IniPath)) { $problems.Add("AVD ini missing: " + $IniPath) }
    if (-not (Test-Path $ConfigPath)) { $problems.Add("AVD config.ini missing: " + $ConfigPath) }
    foreach ($m in (Get-ConfigMismatches)) { $problems.Add("config.ini: " + $m) }

    Write-Step ("emulator version: " + (Get-EmulatorVersion))
    Write-Step ("system image revision: " + (Get-SystemImageRevision))
    $aehd = Test-Aehd
    Write-Step ("acceleration probe (exit " + $aehd.Code + "): " + (($aehd.Text -split "`n")[0]))

    if ($problems.Count -gt 0) {
        foreach ($p in $problems) { Write-Warn $p }
        Fail ("verification failed: " + $problems.Count + " problem(s)")
    }
    Write-Step "verification OK"
    exit 0
}

# disk space: the AVD itself is ~13 GB, snapshots need ~2x during copy
$freeGB = Get-FreeSpaceGB $AvdRoot
if ($freeGB -ge 0 -and $freeGB -lt 30) {
    Write-Warn ("only " + $freeGB + " GB free on the AVD drive; the AVD plus snapshot needs ~30 GB")
}

# 1. SDK packages -----------------------------------------------------------
$listText = Get-InstalledPackages
$missing = @()
foreach ($pkg in $RequiredPackages) {
    if (-not (Test-PackageInstalled $listText $pkg)) { $missing += $pkg }
}
if ($missing.Count -gt 0) {
    if ($SkipSdkInstall) {
        Fail ("SDK packages missing and -SkipSdkInstall set: " + ($missing -join ", "))
    }
    Write-Step ("missing SDK packages: " + ($missing -join ", "))
    if ($DryRun) {
        Write-Step ("[dry-run] would run: sdkmanager --sdk_root=" + $SdkRoot + " --install " + ($missing -join " "))
    } else {
        Write-Step "accepting SDK licenses"
        $answers = @()
        1..40 | ForEach-Object { $answers += "y" }
        $answers | & $SdkManager "--sdk_root=$SdkRoot" "--licenses" 2>&1 | ForEach-Object { "$_" } | Out-Null
        Write-Step ("installing: " + ($missing -join ", "))
        $answers | & $SdkManager "--sdk_root=$SdkRoot" "--install" @missing 2>&1 | ForEach-Object { "$_" } | Out-Null
        if ($LASTEXITCODE -ne 0) { Fail ("sdkmanager --install failed (exit " + $LASTEXITCODE + ")") }
        $listText = Get-InstalledPackages
        foreach ($pkg in $RequiredPackages) {
            if (-not (Test-PackageInstalled $listText $pkg)) { Fail ("SDK package still missing after install: " + $pkg) }
        }
        Write-Step "SDK packages installed"
    }
} else {
    Write-Step "required SDK packages are installed"
}

Write-Step ("emulator version: " + (Get-EmulatorVersion))
Write-Step ("system image revision: " + (Get-SystemImageRevision))

# 2. AVD creation -----------------------------------------------------------
$avdExists = (Test-Path $IniPath) -and (Test-Path $ConfigPath)
if ($Force -and $avdExists) {
    if ($running) { Fail "refusing to recreate a running AVD; stop it first (run.ps1 -Stop)" }
    Write-Step ("-Force: recreate AVD " + $AvdName)
    if ($DryRun) {
        Write-Step ("[dry-run] would remove " + $AvdDir + " and " + $IniPath)
        Write-Step ("[dry-run] would run: avdmanager create avd -n " + $AvdName + " -k " + $SystemImage + " -d " + $AvdDevice)
    } else {
        if (Test-Path $AvdDir) { Remove-Item -LiteralPath $AvdDir -Recurse -Force }
        if (Test-Path $IniPath) { Remove-Item -LiteralPath $IniPath -Force }
        $avdExists = $false
    }
}

if (-not $avdExists) {
    if ($DryRun) {
        Write-Step ("[dry-run] would run: avdmanager create avd -n " + $AvdName + " -k " + $SystemImage + " -d " + $AvdDevice + " --force")
    } else {
        if ($running) { Fail "refusing to create AVD while an emulator with the same name is running" }
        if (-not (Test-Path $AvdRoot)) { New-Item -ItemType Directory -Path $AvdRoot -Force | Out-Null }
        Write-Step ("creating AVD " + $AvdName + " (" + $AvdDevice + ")")
        # answer "no" to the custom hardware profile prompt
        $answers = @("no", "no", "no", "no", "no")
        $answers | & $AvdManager "create" "avd" "-n" $AvdName "-k" $SystemImage "-d" $AvdDevice "--force" 2>&1 | ForEach-Object { "$_" } | Out-Null
        if (-not (Test-Path $ConfigPath)) { Fail ("avdmanager did not create " + $ConfigPath) }
        Write-Step "AVD created"
    }
} else {
    Write-Step "AVD already exists"
}

# 3. config.ini overrides ---------------------------------------------------
if ($DryRun) {
    $planned = Set-DesiredConfig
    if ($planned.Changed.Count -eq 0) {
        Write-Step "[dry-run] config.ini already matches the required values"
    } else {
        Write-Step ("[dry-run] would update config.ini: " + ($planned.Changed -join "; "))
    }
} else {
    $apply = Set-DesiredConfig
    if ($apply.Changed.Count -eq 0) {
        Write-Step "config.ini matches the required values"
    } else {
        Write-ConfigLines $apply.Lines
        Write-Step ("config.ini updated: " + ($apply.Changed -join "; "))
    }
}

# 4. acceleration -----------------------------------------------------------
$aehd = Test-Aehd
if ($aehd.Code -eq 0) {
    Write-Step ("acceleration OK: " + (($aehd.Text -split "`n")[0]))
} else {
    Write-Warn ("acceleration probe failed (exit " + $aehd.Code + "): " + $aehd.Text)
    Write-Warn "install AEHD 2.2 (extras;google;Android_Emulator_Hypervisor_Driver) or enable WHPX"
}

Write-Step ("done. next: powershell -File tools\android-car-boot\avd\run.ps1 -AvdName " + $AvdName)
exit 0
