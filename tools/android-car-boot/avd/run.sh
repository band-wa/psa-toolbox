#!/bin/bash
# 启动/停止/查看 API 28 arm64 AVD（macOS Apple Silicon）。
# 按命令行 "-avd <name>" 匹配本 AVD 进程；默认 -writable-system、不加载快照。

set -u
SCRIPT_TAG=run
. "$(dirname "$0")/macos-common.sh"

AvdName=psa_api28
Port=5554
SdkRoot=""
NoSnapshot=1
WritableSystem=1
Window=1
Gpu="auto"
Stop=0
Restart=0
Status=0
DryRun=0
TimeoutSec=300

usage() {
    cat <<'EOF'
Usage: bash tools/android-car-boot/avd/run.sh [options]

Options (PowerShell spellings such as -AvdName / -Port / -Status are accepted):
  --avd-name NAME        AVD name (default: psa_api28)
  --port PORT            emulator console port, even, 5554-5584 (default: 5554)
  --sdk-root PATH        Android SDK root (default: $ANDROID_SDK_ROOT,
                         $ANDROID_HOME or ~/Library/Android/sdk)
  --window[=BOOL]        show the emulator window (default: true; 0 = headless)
  --gpu MODE             emulator -gpu mode (default: auto)
  --no-snapshot[=BOOL]   boot with -no-snapshot (default: true)
  --writable-system[=BOOL]  boot with -writable-system (default: true)
  --timeout-sec N        boot wait timeout (default: 300)
  --status               print the running state and adb properties
  --stop                 stop the emulator for this AVD
  --restart              stop, then start it
  --dry-run              print the emulator command without starting
  -h, --help             show this help

Only an emulator process whose command line contains "-avd <name>" is ever
stopped.  Exit code 0 on success, 1 on failure.
EOF
}

set_bool_value() {
    case "$1" in
        1|true|yes|on) printf '1' ;;
        0|false|no|off) printf '0' ;;
        *) acb_fail "invalid boolean value: $1" ;;
    esac
}

args=("$@")
argi=0
argc=${#args[@]}
while [ "$argi" -lt "$argc" ]; do
    raw=${args[$argi]}
    argi=$((argi + 1))
    hasval=0
    val=""
    case "$raw" in
        *=*)
            key=${raw%%=*}
            val=${raw#*=}
            hasval=1
            ;;
        *)
            key=$raw
            ;;
    esac
    norm=$(printf '%s' "$key" | sed 's/^-*//' | tr 'A-Z' 'a-z' | tr -d '_-')
    case "$norm" in
        avdname|name)
            if [ "$hasval" -eq 0 ]; then
                if [ "$argi" -ge "$argc" ]; then acb_fail "option $raw requires a value"; fi
                val=${args[$argi]}
                argi=$((argi + 1))
            fi
            AvdName=$val
            ;;
        port)
            if [ "$hasval" -eq 0 ]; then
                if [ "$argi" -ge "$argc" ]; then acb_fail "option $raw requires a value"; fi
                val=${args[$argi]}
                argi=$((argi + 1))
            fi
            Port=$val
            ;;
        sdkroot)
            if [ "$hasval" -eq 0 ]; then
                if [ "$argi" -ge "$argc" ]; then acb_fail "option $raw requires a value"; fi
                val=${args[$argi]}
                argi=$((argi + 1))
            fi
            SdkRoot=$val
            ;;
        timeoutsec)
            if [ "$hasval" -eq 0 ]; then
                if [ "$argi" -ge "$argc" ]; then acb_fail "option $raw requires a value"; fi
                val=${args[$argi]}
                argi=$((argi + 1))
            fi
            TimeoutSec=$val
            ;;
        nosnapshot)
            if [ "$hasval" -eq 1 ]; then NoSnapshot=$(set_bool_value "$val"); else NoSnapshot=1; fi
            ;;
        writablesystem)
            if [ "$hasval" -eq 1 ]; then WritableSystem=$(set_bool_value "$val"); else WritableSystem=1; fi
            ;;
        window)
            if [ "$hasval" -eq 1 ]; then Window=$(set_bool_value "$val"); else Window=1; fi
            ;;
        gpu)
            if [ "$hasval" -eq 0 ]; then
                if [ "$argi" -ge "$argc" ]; then acb_fail "option $raw requires a value"; fi
                val=${args[$argi]}
                argi=$((argi + 1))
            fi
            Gpu=$val
            ;;
        stop)
            if [ "$hasval" -eq 1 ]; then acb_fail "option $raw does not take a value"; fi
            Stop=1
            ;;
        restart)
            if [ "$hasval" -eq 1 ]; then acb_fail "option $raw does not take a value"; fi
            Restart=1
            ;;
        status)
            if [ "$hasval" -eq 1 ]; then acb_fail "option $raw does not take a value"; fi
            Status=1
            ;;
        dryrun)
            if [ "$hasval" -eq 1 ]; then acb_fail "option $raw does not take a value"; fi
            DryRun=1
            ;;
        h|help)
            usage
            exit 0
            ;;
        "")
            acb_fail "empty option"
            ;;
        *)
            acb_fail "unknown option: $raw (see --help)"
            ;;
    esac
done
acb_require_avd_name "$AvdName"

case "$Port" in
    ''|*[!0-9]*) acb_fail "invalid port: $Port" ;;
esac
case "$TimeoutSec" in
    ''|*[!0-9]*) acb_fail "invalid timeout: $TimeoutSec" ;;
esac

SdkRoot=$(acb_detect_sdk_root "$SdkRoot")
AvdRoot=$(acb_detect_avd_root)
EmulatorExe="$SdkRoot/emulator/emulator"
AdbExe="$SdkRoot/platform-tools/adb"
Serial="emulator-$Port"

find_tools() {
    if [ ! -x "$EmulatorExe" ]; then
        if ! acb_find_emulator "$SdkRoot"; then
            acb_fail "emulator not found: $EmulatorExe"
        fi
        EmulatorExe=$ACB_EMULATOR
    fi
    if [ ! -x "$AdbExe" ]; then
        if ! acb_find_adb "$SdkRoot"; then
            acb_fail "adb not found: $AdbExe"
        fi
        AdbExe=$ACB_ADB
    fi
    ACB_ADB=$AdbExe
    ACB_EMULATOR=$EmulatorExe
}

instance_pids() {
    acb_emulator_pids "$AvdName" | acb_join_lines
}

instance_serial() {
    local line
    line=$(acb_emulator_instances "$AvdName" | head -n 1)
    if [ -z "$line" ]; then
        printf '%s' "$Serial"
    else
        printf 'emulator-%s' "$(acb_instance_port "$line" "$Port")"
    fi
}

show_status() {
    local pids state boot sdk
    pids=$(instance_pids)
    if [ -n "$pids" ]; then
        acb_step "running: pid $pids serial=$(instance_serial)"
    else
        acb_step "not running"
    fi
    state=$(acb_adb_text -s "$Serial" get-state | tr -d '[:space:]')
    if [ -z "$state" ]; then
        acb_step "adb state: not connected"
    else
        acb_step "adb state: $state"
    fi
    if [ "$state" = "device" ]; then
        boot=$(acb_adb_text -s "$Serial" shell getprop sys.boot_completed | tr -d '[:space:]')
        acb_step "sys.boot_completed=$boot"
        sdk=$(acb_adb_text -s "$Serial" shell getprop ro.build.version.sdk | tr -d '[:space:]')
        acb_step "ro.build.version.sdk=$sdk"
    fi
}

stop_avd() {
    acb_stop_avd "$AvdName" 60 "emulator for AVD $AvdName is not running"
}

# 模拟环境蓝牙栈替身：AVD 按 ro.kernel.qemu=1 跳过 BluetoothService，launcher 蓝牙卡片
# 在 adapter 为 null 时空指针闪退；以 root 注册 bluetooth_manager 兜底。
ensure_bt_shim() {
    if [ "$DryRun" -eq 1 ]; then
        return 0
    fi
    local target="${1:-$Serial}"
    local jar="$REPO_ROOT/tools/android-car-boot/bt-shim/build/bt-shim.jar"
    if [ ! -f "$jar" ]; then
        acb_warn "bt shim 未构建（${jar}）；先运行 bash tools/android-car-boot/bt-shim/build.sh"
        return 0
    fi
    local uid deadline
    uid=$(acb_adb_text -s "$target" shell id -u | tr -d '[:space:]')
    if [ "$uid" != "0" ]; then
        # adb root 会重启 adbd，最多等 20 s 重新上线
        acb_adb_text -s "$target" root >/dev/null || true
        deadline=$(( $(date +%s) + 20 ))
        while [ "$(date +%s)" -lt "$deadline" ]; do
            if [ "$(acb_adb_text -s "$target" get-state | tr -d '[:space:]')" = "device" ]; then break; fi
            sleep 1
        done
        uid=$(acb_adb_text -s "$target" shell id -u | tr -d '[:space:]')
        if [ "$uid" != "0" ]; then
            acb_warn "bt shim: adb root 未生效（uid=$uid），跳过注册"
            return 0
        fi
    fi
    if acb_adb_text -s "$target" shell service check bluetooth_manager 2>/dev/null | grep -q ": found"; then
        acb_step "bt shim: bluetooth_manager already registered（重建 jar 后需重启 AVD 才更新）"
        return 0
    fi
    if ! "$ACB_ADB" -s "$target" push "$jar" /data/local/tmp/bt-shim.jar >/dev/null 2>&1; then
        acb_warn "bt shim: push 到 /data/local/tmp 失败"
        return 0
    fi
    acb_adb_text -s "$target" shell \
        "CLASSPATH=/data/local/tmp/bt-shim.jar app_process /system/bin com.bandwa.vcarlib.BtShim >/dev/null 2>&1 &"
    deadline=$(( $(date +%s) + 10 ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        if acb_adb_text -s "$target" shell service check bluetooth_manager 2>/dev/null | grep -q ": found"; then
            acb_step "bt shim: bluetooth_manager registered"
            return 0
        fi
        sleep 1
    done
    acb_warn "bt shim: bluetooth_manager 注册失败（launcher 蓝牙卡片可能仍会崩溃）"
}

# 等 adb 在线且 sys.boot_completed 恰为 1；失败时写入 BOOT_ERR。
BOOT_ERR=""
wait_booted() {
    local deadline misses=0 state boot
    deadline=$(( $(date +%s) + TimeoutSec ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        state=$(acb_adb_text -s "$Serial" get-state | tr -d '[:space:]')
        if [ "$state" = "device" ]; then
            boot=$(acb_adb_text -s "$Serial" shell getprop sys.boot_completed | tr -d '[:space:]')
            if [ "$boot" = "1" ]; then
                return 0
            fi
            misses=0
        elif [ -n "$(acb_emulator_pids "$AvdName")" ]; then
            misses=0
        else
            misses=$((misses + 1))
            if [ "$misses" -ge 15 ]; then
                BOOT_ERR="emulator-$Port exited before boot; see $ACB_LOG_DIR"
                return 1
            fi
        fi
        sleep 2
    done
    BOOT_ERR="timeout after $TimeoutSec s waiting for $Serial sys.boot_completed=1"
    return 1
}

start_avd() {
    find_tools

    local pids
    pids=$(instance_pids)
    if [ -n "$pids" ]; then
        local running_serial boot deadline
        running_serial=$(instance_serial)
        acb_step "already running on $running_serial; nothing to do"
        # 可能由其它 --port 启动：一律以实际实例串号为准，boot 未完成时等它就绪
        deadline=$(( $(date +%s) + 60 ))
        while [ "$(date +%s)" -lt "$deadline" ]; do
            boot=$(acb_adb_text -s "$running_serial" shell getprop sys.boot_completed | tr -d '[:space:]')
            if [ "$boot" = "1" ]; then break; fi
            sleep 2
        done
        if [ "$boot" = "1" ]; then
            ensure_bt_shim "$running_serial"
        else
            acb_warn "已运行的实例 sys.boot_completed != 1，跳过 bt shim"
        fi
        return 0
    fi
    if [ $((Port % 2)) -ne 0 ]; then acb_fail "--port must be even (5554, 5556, ...)"; fi
    if [ "$Port" -lt 5554 ] || [ "$Port" -gt 5584 ]; then acb_fail "--port must be between 5554 and 5584"; fi

    if [ ! -f "$AvdRoot/$AvdName.ini" ]; then
        acb_fail "AVD $AvdName not found; run setup.sh first"
    fi

    local emu_args=("-avd" "$AvdName" "-port" "$Port"
                    "-no-audio" "-no-boot-anim" "-gpu" "$Gpu")
    if [ "$Window" -eq 0 ]; then
        emu_args=("-no-window" "${emu_args[@]}")
    fi
    if [ "$WritableSystem" -eq 1 ]; then emu_args[${#emu_args[@]}]="-writable-system"; fi
    if [ "$NoSnapshot" -eq 1 ]; then emu_args[${#emu_args[@]}]="-no-snapshot"; fi

    acb_step "command: $(acb_quote_cmd "$EmulatorExe" "${emu_args[@]}")"
    if [ "$DryRun" -eq 1 ]; then
        acb_step "[dry-run] would start it and wait up to $TimeoutSec s for boot; not starting"
        return 0
    fi

    mkdir -p "$ACB_LOG_DIR" || acb_fail "cannot create log directory: $ACB_LOG_DIR"
    local log="$ACB_LOG_DIR/emulator-$Port.log"
    rm -f "$log"

    # 必须脱离本 shell：macOS 无 setsid(1)。
    # 用 nohup + 重定向 stdio 让模拟器在脚本退出后存活。
    (
        cd "$REPO_ROOT" || exit 1
        nohup "$EmulatorExe" "${emu_args[@]}" </dev/null >"$log" 2>&1 &
        printf '%s' "$!" > "$ACB_LOG_DIR/$AvdName-emulator-$Port.pid"
    ) </dev/null >/dev/null 2>&1
    local pidfile="$ACB_LOG_DIR/$AvdName-emulator-$Port.pid"
    if [ ! -s "$pidfile" ]; then
        acb_fail "failed to record the emulator pid; see $log"
    fi
    acb_step "started, log: $log"

    if ! wait_booted; then
        acb_warn "$BOOT_ERR"
        if [ -f "$log" ]; then
            acb_step "last log lines:"
            tail -n 20 "$log" | sed 's/^/  /'
        fi
        acb_step "hints: 1) setup.sh --verify-only  2) emulator-check accel  3) check free disk  4) delete stale *.lock in the AVD dir"
        acb_fail "boot wait failed"
    fi
    acb_step "boot completed: $Serial"

    local root_out
    root_out=$(acb_adb_text -s "$Serial" root)
    acb_step "adb root: $root_out"
    sleep 1
    local deadline state uid
    deadline=$(( $(date +%s) + 30 ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        state=$(acb_adb_text -s "$Serial" get-state | tr -d '[:space:]')
        if [ "$state" = "device" ]; then break; fi
        sleep 1
    done
    uid=$(acb_adb_text -s "$Serial" shell id -u | tr -d '[:space:]')
    if [ "$uid" != "0" ]; then
        acb_warn "adb shell uid is '$uid' (expected 0); is this a userdebug image?"
    else
        acb_step "adb shell is root"
    fi
    ensure_bt_shim "$Serial"
}

if [ "$Stop" -eq 1 ]; then
    find_tools
    stop_avd
    exit 0
fi
if [ "$Status" -eq 1 ]; then
    find_tools
    show_status
    exit 0
fi
if [ "$Restart" -eq 1 ]; then
    find_tools
    stop_avd
    start_avd
    exit 0
fi
start_avd
exit 0
