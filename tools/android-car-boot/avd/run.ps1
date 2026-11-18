#requires -Version 5.1
<#
.SYNOPSIS
    Start / stop / inspect the official Google Android 9 AVD used by android-car-boot.

.DESCRIPTION
    Starts the emulator through WMI (Win32_Process.Create) so it survives the shell
    and job object that launched it, then waits for adb online and
    sys.boot_completed=1 (exact match), and finally runs "adb root".

    Default command line:
      emulator -avd <name> -port <port> -no-audio -no-boot-anim
               -gpu auto -writable-system -no-snapshot

    The script refuses mismatched AVD names.  Only an emulator process whose
    command line contains "-avd <name>" is ever stopped.

.EXAMPLE
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\run.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\run.ps1 -Status
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\run.ps1 -Stop
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\run.ps1 -Port 5556 -Gpu host
    powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\run.ps1 -NoWindow
#>
[CmdletBinding()]
param(
    [string]$AvdName = "psa_api28",
    [int]$Port = 5554,
    [string]$SdkRoot = "D:\Tools\Android\sdk",
    [bool]$NoSnapshot = $true,
    [bool]$WritableSystem = $true,
    [switch]$NoWindow,
    [string]$Gpu = "auto",
    [switch]$Stop,
    [switch]$Restart,
    [switch]$Status,
    [switch]$DryRun,
    [int]$TimeoutSec = 300
)

$ErrorActionPreference = "Stop"

$SdkRoot = $SdkRoot.TrimEnd('\', '/')
$EmulatorExe = Join-Path $SdkRoot "emulator\emulator.exe"
$AdbExe = Join-Path $SdkRoot "platform-tools\adb.exe"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$LogDir = Join-Path $RepoRoot "tools\android-car-boot\build\avd-logs"
$PidFile = Join-Path $LogDir ($AvdName + "-emulator-" + $Port + ".pid")

function Write-Step([string]$Text) { Write-Host ("[run] " + $Text) }
function Write-Warn([string]$Text) { Write-Host ("[run] WARN: " + $Text) -ForegroundColor Yellow }
function Fail([string]$Text) {
    Write-Host ("[run] ERROR: " + $Text) -ForegroundColor Red
    exit 1
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

function Get-AdbOnline {
    param([string]$TargetSerial = $Serial)
    $text = Get-AdbText @("devices")
    return [bool]($text -match ('(?m)^' + [regex]::Escape($TargetSerial) + '\s+device$'))
}

function Get-EmulatorInstance {
    # Returns the emulator.exe process(es) whose command line names this AVD.
    Get-CimInstance Win32_Process -Filter "Name='emulator.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -and ($_.CommandLine -match ('-avd\s+"?' + [regex]::Escape($AvdName) + '"?(\s|$)')) }
}

function Get-InstancePort($Process) {
    if ($Process -and $Process.CommandLine -and ($Process.CommandLine -match '-port\s+(\d+)')) {
        return [int]$Matches[1]
    }
    return $Port
}

function Wait-Booted {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $misses = 0
    while ((Get-Date) -lt $deadline) {
        $state = Get-AdbText @("-s", $Serial, "get-state")
        if ($state -eq "device") {
            $boot = Get-AdbText @("-s", $Serial, "shell", "getprop", "sys.boot_completed")
            if ($boot -match '^1$') { return }
            $misses = 0
        } elseif (Get-EmulatorInstance) {
            $misses = 0
        } else {
            $misses++
            if ($misses -ge 15) { throw ("emulator-" + $Port + " exited before boot; see " + $LogDir) }
        }
        Start-Sleep -Seconds 2
    }
    throw ("timeout after " + $TimeoutSec + " s waiting for " + $Serial + " sys.boot_completed=1")
}

function Wait-AdbGone {
    param([string]$TargetSerial = $Serial)
    $deadline = (Get-Date).AddSeconds(30)
    while ((Get-Date) -lt $deadline) {
        if (-not (Get-AdbOnline $TargetSerial)) { return }
        Start-Sleep -Milliseconds 500
    }
}

function Stop-Avd {
    $instances = @(Get-EmulatorInstance)
    if ($instances.Count -eq 0) {
        Write-Step ("emulator for AVD " + $AvdName + " is not running")
        return
    }
    $oldPort = Get-InstancePort $instances[0]
    $oldSerial = "emulator-" + $oldPort
    Write-Step ("stopping " + $oldSerial + " (pid " + (($instances | ForEach-Object { $_.ProcessId }) -join ",") + ")")
    Get-AdbText @("-s", $oldSerial, "emu", "kill") | Out-Null
    $deadline = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $deadline -and @(Get-EmulatorInstance).Count -gt 0) { Start-Sleep -Milliseconds 500 }
    if (@(Get-EmulatorInstance).Count -gt 0) {
        Write-Warn "adb emu kill did not stop it; terminating the matching emulator process only"
        Get-EmulatorInstance | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        Start-Sleep -Seconds 2
    }
    if (@(Get-EmulatorInstance).Count -gt 0) { Fail "failed to stop the emulator" }
    Wait-AdbGone $oldSerial
    Start-Sleep -Seconds 1
    Write-Step "stopped"
}

function Show-Status {
    $instances = @(Get-EmulatorInstance)
    if ($instances.Count -gt 0) {
        $p = $instances[0]
        $serial = "emulator-" + (Get-InstancePort $p)
        Write-Step ("running: pid " + (($instances | ForEach-Object { $_.ProcessId }) -join ",") + " serial=" + $serial)
    } else {
        Write-Step "not running"
        $serial = $Serial
    }
    $state = Get-AdbText @("-s", $serial, "get-state")
    Write-Step ("adb state: " + $(if ($state) { $state } else { "not connected" }))
    if ($state -eq "device") {
        $boot = Get-AdbText @("-s", $serial, "shell", "getprop", "sys.boot_completed")
        Write-Step ("sys.boot_completed=" + $boot)
        $sdk = Get-AdbText @("-s", $serial, "shell", "getprop", "ro.build.version.sdk")
        Write-Step ("ro.build.version.sdk=" + $sdk)
    }
}

function Ensure-AdbRoot {
    param([string]$TargetSerial = $Serial)
    $uid = Get-AdbText @("-s", $TargetSerial, "shell", "id -u")
    if ($uid -eq "0") { return $true }
    [void](Get-AdbText @("-s", $TargetSerial, "root"))
    Start-Sleep -Seconds 1
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-Date) -lt $deadline) {
        if ((Get-AdbText @("-s", $TargetSerial, "get-state")) -eq "device") { break }
        Start-Sleep -Milliseconds 500
    }
    return ((Get-AdbText @("-s", $TargetSerial, "shell", "id -u")) -eq "0")
}

function Start-BtShim {
    param([string]$TargetSerial = $Serial)
    if ($DryRun) { return }
    # The AVD image skips BluetoothService (ro.kernel.qemu=1); the launcher bluetooth
    # card NPEs on a null adapter. Register a fake bluetooth_manager as root.
    $jar = Join-Path $RepoRoot "tools\android-car-boot\bt-shim\build\bt-shim.jar"
    if (-not (Test-Path $jar)) {
        Write-Warn ("bt shim not built: " + $jar + " (run tools/android-car-boot/bt-shim/build.sh)")
        return
    }
    if (-not (Ensure-AdbRoot $TargetSerial)) {
        $uid = Get-AdbText @("-s", $TargetSerial, "shell", "id -u")
        Write-Warn ("bt shim: adb root failed (uid=" + $uid + "), skipped")
        return
    }
    $check = Get-AdbText @("-s", $TargetSerial, "shell", "service", "check", "bluetooth_manager")
    if ($check -match ": found") {
        Write-Step "bt shim: bluetooth_manager already registered"
        return
    }
    $pushExit = -1
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        & $AdbExe -s $TargetSerial push $jar /data/local/tmp/bt-shim.jar 2>$null | Out-Null
        $pushExit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prev
    }
    if ($pushExit -ne 0) {
        Write-Warn "bt shim: push failed"
        return
    }
    [void](Get-AdbText @("-s", $TargetSerial, "shell", "CLASSPATH=/data/local/tmp/bt-shim.jar app_process /system/bin com.bandwa.vcarlib.BtShim >/dev/null 2>&1 &"))
    $deadline = (Get-Date).AddSeconds(10)
    while ((Get-Date) -lt $deadline) {
        $check = Get-AdbText @("-s", $TargetSerial, "shell", "service", "check", "bluetooth_manager")
        if ($check -match ": found") {
            Write-Step "bt shim: bluetooth_manager registered"
            return
        }
        Start-Sleep -Milliseconds 500
    }
    Write-Warn "bt shim: bluetooth_manager registration failed"
}

function Ensure-Locale {
    param([string]$TargetSerial = $Serial)
    if ($DryRun) { return }
    if (-not (Ensure-AdbRoot $TargetSerial)) {
        Write-Warn "locale: adb root failed; skipped"
        return
    }
    $locale = Get-AdbText @("-s", $TargetSerial, "shell", "getprop", "persist.sys.locale")
    if ($locale -eq "zh-CN") {
        Write-Step "locale: zh-CN already set"
        return
    }
    [void](Get-AdbText @("-s", $TargetSerial, "shell", "setprop", "persist.sys.locale", "zh-CN"))
    [void](Get-AdbText @("-s", $TargetSerial, "shell", "stop"))
    Start-Sleep -Seconds 2
    $deadline = (Get-Date).AddSeconds(30)
    while ((Get-Date) -lt $deadline) {
        if ((Get-AdbText @("-s", $TargetSerial, "get-state")) -eq "device") { break }
        Start-Sleep -Milliseconds 500
    }
    [void](Get-AdbText @("-s", $TargetSerial, "shell", "start"))
    $deadline = (Get-Date).AddSeconds(60)
    while ((Get-Date) -lt $deadline) {
        $check = Get-AdbText @("-s", $TargetSerial, "shell", "service", "check", "activity")
        if ($check -match ": found") {
            Write-Step "locale: zh-CN (framework restarted)"
            return
        }
        Start-Sleep -Milliseconds 500
    }
    Write-Warn "locale: zh-CN written but framework did not restart within 60 s"
}

function Start-Avd {
    if (-not (Test-Path $EmulatorExe)) { Fail ("emulator not found: " + $EmulatorExe) }
    if (-not (Test-Path $AdbExe)) { Fail ("adb not found: " + $AdbExe) }

    $instances = @(Get-EmulatorInstance)
    if ($instances.Count -gt 0) {
        $p = $instances[0]
        $runningSerial = "emulator-" + (Get-InstancePort $p)
        Write-Step ("already running on " + $runningSerial + "; nothing to do")
        # May have been started with another -Port: use the actual instance serial.
        $boot = ""
        $deadline = (Get-Date).AddSeconds(60)
        while ((Get-Date) -lt $deadline) {
            $boot = Get-AdbText @("-s", $runningSerial, "shell", "getprop", "sys.boot_completed")
            if ($boot -eq "1") { break }
            Start-Sleep -Seconds 2
        }
        if ($boot -eq "1") {
            Start-BtShim -TargetSerial $runningSerial
            Ensure-Locale -TargetSerial $runningSerial
        } else {
            Write-Warn "already running but sys.boot_completed != 1; bt shim and locale skipped"
        }
        return
    }
    if (($Port % 2) -ne 0) { Fail "-Port must be even (5554, 5556, ...)" }
    if ($Port -lt 5554 -or $Port -gt 5584) { Fail "-Port must be between 5554 and 5584" }

    $avdIni = Join-Path (Join-Path $env:USERPROFILE ".android\avd") ($AvdName + ".ini")
    if (-not (Test-Path $avdIni)) {
        Fail ("AVD " + $AvdName + " not found; run setup.ps1 first")
    }

    $emuArgs = @("-avd", $AvdName, "-port", "$Port",
                 "-no-audio", "-no-boot-anim", "-gpu", $Gpu)
    if ($NoWindow) { $emuArgs = @("-no-window") + $emuArgs }
    if ($WritableSystem) { $emuArgs += "-writable-system" }
    if ($NoSnapshot) { $emuArgs += "-no-snapshot" }

    $inner = '"' + $EmulatorExe + '"'
    foreach ($a in $emuArgs) {
        if ($a -match '[\s"]') { $inner += ' "' + ($a -replace '"', '\"') + '"' } else { $inner += ' ' + $a }
    }

    Write-Step ("command: " + $inner)
    if ($DryRun) {
        Write-Step ("[dry-run] would start it and wait up to " + $TimeoutSec + " s for boot; not starting")
        return
    }

    New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
    $log = Join-Path $LogDir ("emulator-" + $Port + ".log")
    Remove-Item -LiteralPath $log -ErrorAction SilentlyContinue

    # cmd /d /s: no AutoRun, literal arguments; WMI creation avoids the parent Job Object
    $launch = 'cmd.exe /d /s /c "' + $inner + ' >"' + $log + '" 2>&1"'
    $created = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
        CommandLine     = $launch
        CurrentDirectory = $RepoRoot
    }
    if ($created.ReturnValue -ne 0) {
        Fail ("Win32_Process.Create failed with code " + $created.ReturnValue)
    }
    Set-Content -LiteralPath $PidFile -Value ([int]$created.ProcessId) -Encoding Ascii
    Write-Step ("started, log: " + $log)

    try {
        Wait-Booted
    } catch {
        $msg = $_.Exception.Message
        Write-Warn $msg
        if (Test-Path $log) {
            Write-Step "last log lines:"
            Get-Content -LiteralPath $log -Tail 20 | ForEach-Object { Write-Host ("  " + $_) }
        }
        Write-Step "hints: 1) setup.ps1 -VerifyOnly  2) emulator-check accel  3) check free disk  4) delete stale *.lock in the AVD dir"
        Fail "boot wait failed"
    }
    Write-Step ("boot completed: " + $Serial)

    if (Ensure-AdbRoot $Serial) {
        Write-Step "adb shell is root"
    } else {
        $uid = Get-AdbText @("-s", $Serial, "shell", "id -u")
        Write-Warn ("adb shell uid is '" + $uid + "' (expected 0); is this a userdebug image?")
    }
    Start-BtShim
    Ensure-Locale
}

# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------

$Serial = "emulator-" + $Port

if ($Stop) {
    Stop-Avd
    exit 0
}
if ($Status) {
    Show-Status
    exit 0
}
if ($Restart) {
    Stop-Avd
    Start-Avd
    exit 0
}
Start-Avd
exit 0
