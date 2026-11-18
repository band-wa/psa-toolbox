#!/bin/bash
# 创建/修复 API 28 userdebug AVD（macOS arm64），幂等；不启动模拟器。
# cmdline-tools 12.0 的 avdmanager 读不了 SDK XML v4，故直接写 ini；不改运行中的 AVD。

set -u
SCRIPT_TAG=setup
. "$(dirname "$0")/macos-common.sh"

# tools/android-car-boot 依赖的 config.ini 键值（与下方 DESIRED_VALUES 一一对应）。
DESIRED_KEYS=(
    "hw.lcd.width"
    "hw.lcd.height"
    "hw.lcd.density"
    "hw.ramSize"
    "disk.dataPartition.size"
    "hw.gpu.enabled"
    "hw.gpu.mode"
    "hw.audioInput"
    "hw.audioOutput"
    "hw.camera.back"
    "hw.keyboard"
    "showDeviceFrame"
    "hw.initialOrientation"
    "fastboot.forceFastBoot"
    "playstore.enabled"
    "abi.type"
    "hw.cpu.arch"
    "image.sysdir.1"
    "tag.id"
    "target"
)
DESIRED_VALUES=(
    "1920"
    "720"
    "213"
    "4096M"
    "8G"
    "yes"
    "auto"
    "no"
    "no"
    "none"
    "yes"
    "no"
    "landscape"
    "no"
    "no"
    "arm64-v8a"
    "arm64"
    "system-images/android-28/default/arm64-v8a/"
    "default"
    "android-28"
)

REQUIRED_PACKAGES=("emulator" "platform-tools" "$ACB_SYSTEM_IMAGE_PKG")

usage() {
    cat <<'EOF'
Usage: bash tools/android-car-boot/avd/setup.sh [options]

Options (PowerShell spellings such as -AvdName / -VerifyOnly are accepted too):
  --avd-name NAME      AVD name (default: psa_api28)
  --sdk-root PATH      Android SDK root (default: $ANDROID_SDK_ROOT,
                       $ANDROID_HOME or ~/Library/Android/sdk)
  --java-home PATH     JDK used by sdkmanager (default: $JAVA_HOME, Android
                       Studio's bundled JBR, or java on PATH)
  --force              recreate the AVD even if it already exists
  --verify-only        read-only checks; no packages/AVD/config changes
  --skip-sdk-install   fail instead of installing missing SDK packages
  --dry-run            print the planned actions without changing anything
  -h, --help           show this help

The AVD is created without avdmanager (cmdline-tools 12.0 cannot parse this
SDK's XML v4 system image package.xml); <name>.ini and <name>.avd/config.ini
are written directly.  Exit code 0 on success, 1 on failure.
EOF
}

AvdName=psa_api28
SdkRoot=""
JavaHome=""
Force=0
VerifyOnly=0
SkipSdkInstall=0
DryRun=0

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
        sdkroot)
            if [ "$hasval" -eq 0 ]; then
                if [ "$argi" -ge "$argc" ]; then acb_fail "option $raw requires a value"; fi
                val=${args[$argi]}
                argi=$((argi + 1))
            fi
            SdkRoot=$val
            ;;
        javahome)
            if [ "$hasval" -eq 0 ]; then
                if [ "$argi" -ge "$argc" ]; then acb_fail "option $raw requires a value"; fi
                val=${args[$argi]}
                argi=$((argi + 1))
            fi
            JavaHome=$val
            ;;
        force)
            if [ "$hasval" -eq 1 ]; then acb_fail "option $raw does not take a value"; fi
            Force=1
            ;;
        verifyonly)
            if [ "$hasval" -eq 1 ]; then acb_fail "option $raw does not take a value"; fi
            VerifyOnly=1
            ;;
        skipsdkinstall)
            if [ "$hasval" -eq 1 ]; then acb_fail "option $raw does not take a value"; fi
            SkipSdkInstall=1
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

HOST_OS=$(acb_host_os)
HOST_ARCH=$(acb_host_arch)
SdkRoot=$(acb_detect_sdk_root "$SdkRoot")
AvdRoot=$(acb_detect_avd_root)
AvdDir="$AvdRoot/$AvdName.avd"
IniPath="$AvdRoot/$AvdName.ini"
ConfigPath="$AvdDir/config.ini"

acb_step "AVD name   : $AvdName"
acb_step "SDK root   : $SdkRoot"
acb_step "system image: $ACB_SYSTEM_IMAGE_PKG"
acb_step "AVD dir    : $AvdDir"

if [ "$HOST_OS" != "Darwin" ]; then
    acb_warn "this script targets macOS (host system is $HOST_OS)"
fi
if [ "$HOST_ARCH" != "arm64" ]; then
    acb_warn "host arch is $HOST_ARCH; the installed system image is arm64-v8a and cannot run on an x86_64 Mac"
fi

if [ ! -d "$SdkRoot" ]; then acb_fail "SDK root not found: $SdkRoot"; fi
if ! acb_find_sdkmanager "$SdkRoot"; then
    acb_fail "sdkmanager not found: $SdkRoot/cmdline-tools/latest/bin/sdkmanager (install Android cmdline-tools)"
fi
JavaHome=$(acb_detect_java_home "$JavaHome") ||
    acb_fail "JDK not found (use --java-home PATH or set JAVA_HOME; sdkmanager needs a JDK)"
if [ ! -x "$JavaHome/bin/java" ]; then
    acb_fail "JDK not found: $JavaHome/bin/java (use --java-home to override)"
fi
export JAVA_HOME=$JavaHome
PATH="$JAVA_HOME/bin:$PATH"
export PATH

# 键名含 "."，必须按字面匹配，不能用正则。
config_has_key() {
    [ -f "$1" ] || return 1
    awk -v k="$2" 'index($0, k "=") == 1 { found = 1; exit } END { exit found ? 0 : 1 }' "$1"
}

config_lookup() {
    [ -f "$1" ] || return 1
    awk -v k="$2" 'index($0, k "=") == 1 { print substr($0, length(k) + 2); exit }' "$1"
}

append_change() {
    if [ -z "$1" ]; then
        printf '%s' "$2"
    else
        printf '%s; %s' "$1" "$2"
    fi
}

# 只计算不写：输出 "key: old -> new; ..."。
plan_config_changes() {
    local i=0 key want cur out=""
    while [ "$i" -lt "${#DESIRED_KEYS[@]}" ]; do
        key=${DESIRED_KEYS[$i]}
        want=${DESIRED_VALUES[$i]}
        if config_has_key "$ConfigPath" "$key"; then
            cur=$(config_lookup "$ConfigPath" "$key")
            if [ "$cur" != "$want" ]; then
                out=$(append_change "$out" "$key: $cur -> $want")
            fi
        else
            out=$(append_change "$out" "$key: (missing) -> $want")
        fi
        i=$((i + 1))
    done
    printf '%s' "$out"
}

replace_config_value() {
    awk -v k="$2" -v v="$3" '
        BEGIN { done = 0 }
        {
            if (!done && index($0, k "=") == 1) { print k "=" v; done = 1 }
            else { print }
        }' "$1"
}

# 只改目标键，保留其它行和原文件顺序。
apply_config_changes() {
    local work work_tmp i=0 key want cur changed=0
    work=$(mktemp "${TMPDIR:-/tmp}/psa-avd-config.XXXXXX") || acb_fail "mktemp failed"
    cp "$ConfigPath" "$work" || { rm -f "$work"; acb_fail "cannot read $ConfigPath"; }
    while [ "$i" -lt "${#DESIRED_KEYS[@]}" ]; do
        key=${DESIRED_KEYS[$i]}
        want=${DESIRED_VALUES[$i]}
        i=$((i + 1))
        if config_has_key "$work" "$key"; then
            cur=$(config_lookup "$work" "$key")
            if [ "$cur" = "$want" ]; then
                continue
            fi
            work_tmp="$work.new"
            replace_config_value "$work" "$key" "$want" > "$work_tmp" || {
                rm -f "$work" "$work_tmp"
                acb_fail "failed to update $ConfigPath"
            }
            mv "$work_tmp" "$work"
            CHANGES=$(append_change "$CHANGES" "$key: $cur -> $want")
        else
            printf '%s=%s\n' "$key" "$want" >> "$work"
            CHANGES=$(append_change "$CHANGES" "$key: (missing) -> $want")
        fi
        changed=1
    done
    if [ "$changed" -eq 1 ]; then
        cp "$work" "$ConfigPath" || { rm -f "$work"; acb_fail "failed to write $ConfigPath"; }
    fi
    rm -f "$work"
}

# 新 AVD 的 config.ini 基线，取值与模拟器为该 arm64 镜像生成的一致。
write_base_config() {
    {
        cat <<EOF
PlayStore.enabled=no
abi.type=${ACB_SYSTEM_IMAGE_ABI}
avd.id=${AvdName}
avd.ini.displayname=${AvdName}
avd.ini.encoding=UTF-8
avd.name=${AvdName}
disk.cachePartition=yes
disk.cachePartition.size=66MB
disk.dataPartition.path=<temp>
disk.dataPartition.size=6442450944
disk.systemPartition.size=0
disk.vendorPartition.size=0
environment.height=0
environment.width=0
fastboot.forceChosenSnapshotBoot=no
fastboot.forceColdBoot=no
fastboot.forceFastBoot=yes
firstboot.bootFromDownloadableSnapshot=yes
firstboot.bootFromLocalSnapshot=yes
firstboot.saveToLocalSnapshot=yes
hw.accelerometer=yes
hw.accelerometer_uncalibrated=yes
hw.arc=false
hw.audioInput=yes
hw.audioOutput=yes
hw.battery=yes
hw.camera.back=emulated
hw.camera.back.orientation=90
hw.camera.front=none
hw.camera.front.orientation=90
hw.cpu.arch=arm64
hw.cpu.ncore=4
hw.dPad=no
hw.device.hash2=MD5:914a6a50cd5dbc5e6c0c5ec988ac20fa
hw.device.manufacturer=Google
hw.device.name=pixel
hw.display1.density=0
hw.display1.flag=0
hw.display1.height=0
hw.display1.width=0
hw.display1.xOffset=-1
hw.display1.yOffset=-1
hw.display2.density=0
hw.display2.flag=0
hw.display2.height=0
hw.display2.width=0
hw.display2.xOffset=-1
hw.display2.yOffset=-1
hw.display3.density=0
hw.display3.flag=0
hw.display3.height=0
hw.display3.width=0
hw.display3.xOffset=-1
hw.display3.yOffset=-1
hw.displayRegion.0.1.height=0
hw.displayRegion.0.1.width=0
hw.displayRegion.0.1.xOffset=-1
hw.displayRegion.0.1.yOffset=-1
hw.displayRegion.0.2.height=0
hw.displayRegion.0.2.width=0
hw.displayRegion.0.2.xOffset=-1
hw.displayRegion.0.2.yOffset=-1
hw.displayRegion.0.3.height=0
hw.displayRegion.0.3.width=0
hw.displayRegion.0.3.xOffset=-1
hw.displayRegion.0.3.yOffset=-1
hw.gltransport=pipe
hw.gltransport.asg.dataRingSize=32768
hw.gltransport.asg.writeBufferSize=1048576
hw.gltransport.asg.writeStepSize=4096
hw.gltransport.drawFlushInterval=800
hw.gps=yes
hw.gpu.enabled=yes
hw.gpu.mode=host
hw.gsmModem=yes
hw.gyroscope=yes
hw.hotplug_multi_display=no
hw.initialOrientation=landscape
hw.keyboard=yes
hw.keyboard.charmap=qwerty2
hw.keyboard.lid=yes
hw.lcd.backlight=yes
hw.lcd.circular=false
hw.lcd.density=420
hw.lcd.depth=32
hw.lcd.height=1920
hw.lcd.transparent=false
hw.lcd.vsync=60
hw.lcd.width=1080
hw.mainKeys=no
hw.multi_display_window=no
hw.ramSize=2048M
hw.rotaryInput=no
hw.screen=multi-touch
hw.sdCard=yes
hw.sensor.hinge=no
hw.sensor.hinge.count=0
hw.sensor.hinge.fold_to_displayRegion.0.1_at_posture=1
hw.sensor.hinge.resizable.config=1
hw.sensor.hinge.sub_type=0
hw.sensor.hinge.type=0
hw.sensor.roll=no
hw.sensor.roll.count=0
hw.sensor.roll.resize_to_displayRegion.0.1_at_posture=6
hw.sensor.roll.resize_to_displayRegion.0.2_at_posture=6
hw.sensor.roll.resize_to_displayRegion.0.3_at_posture=6
hw.sensors.gyroscope_uncalibrated=yes
hw.sensors.heading=no
hw.sensors.heart_rate=no
hw.sensors.humidity=yes
hw.sensors.light=yes
hw.sensors.magnetic_field=yes
hw.sensors.magnetic_field_uncalibrated=yes
hw.sensors.orientation=yes
hw.sensors.pressure=yes
hw.sensors.proximity=yes
hw.sensors.rgbclight=no
hw.sensors.temperature=yes
hw.sensors.wrist_tilt=no
hw.touchpad0=no
hw.touchpad0.height=400
hw.touchpad0.width=600
hw.trackBall=no
hw.useext4=yes
image.sysdir.1=${ACB_SYSTEM_IMAGE_REL}/
kernel.newDeviceNaming=autodetect
kernel.supportsYaffs2=autodetect
runtime.network.latency=none
runtime.network.speed=full
sdcard.size=512 MB
showDeviceFrame=yes
tag.display=Default Android System Image
tag.id=default
tag.ids=default
target=android-28
test.delayAdbTillBootComplete=0
test.monitorAdb=0
test.quitAfterBootTimeOut=-1
userdata.useQcow2=no
vm.heapSize=228M
AvdId=${AvdName}
EOF
    } > "$ConfigPath"
}

write_avd_ini() {
    cat > "$IniPath" <<EOF
avd.ini.encoding=UTF-8
path=$AvdDir
path.rel=avd/$AvdName.avd
target=android-28
EOF
}

sdk_list=""
if ! sdk_list=$(acb_list_installed "$SdkRoot"); then
    acb_fail "sdkmanager --list_installed failed; check JAVA_HOME (--java-home) and $SdkRoot"
fi

package_installed() {
    printf '%s\n' "$sdk_list" | awk -v p="$1" '$1 == p { found = 1 } END { exit found ? 0 : 1 }'
}

show_versions() {
    acb_step "emulator version: $(acb_emulator_version "$SdkRoot")"
    acb_step "system image revision: $(acb_image_revision "$SdkRoot" "$ACB_SYSTEM_IMAGE_REL" "$ACB_SYSTEM_IMAGE_PKG")"
}

running_pids=$(acb_emulator_pids "$AvdName" | acb_join_lines)
if [ -n "$running_pids" ]; then
    acb_step "AVD is running (pid $running_pids)"
    avd_running=1
else
    acb_step "AVD is not running"
    avd_running=0
fi

if [ "$VerifyOnly" -eq 1 ]; then
    problems=""
    add_problem() { problems=$(append_change "$problems" "$1"); }

    missing_pkgs=""
    for pkg in "${REQUIRED_PACKAGES[@]}"; do
        if ! package_installed "$pkg"; then
            missing_pkgs=$(append_change "$missing_pkgs" "$pkg")
        fi
    done
    if [ -n "$missing_pkgs" ]; then
        # problems 用 "; " 分隔，缺包只能逐个加入。
        for pkg in "${REQUIRED_PACKAGES[@]}"; do
            if ! package_installed "$pkg"; then
                add_problem "SDK package missing: $pkg"
            fi
        done
    fi

    if [ ! -x "$SdkRoot/emulator/emulator" ] && [ ! -f "$SdkRoot/emulator/emulator" ]; then
        add_problem "emulator binary missing: $SdkRoot/emulator/emulator"
    fi
    if [ ! -f "$IniPath" ]; then add_problem "AVD ini missing: $IniPath"; fi
    if [ ! -f "$ConfigPath" ]; then
        add_problem "AVD config.ini missing: $ConfigPath"
    else
        mismatch_list=$(plan_config_changes)
        if [ -n "$mismatch_list" ]; then
            printf '%s\n' "$mismatch_list" | tr ';' '\n' | while IFS= read -r m; do
                m=$(printf '%s' "$m" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')
                if [ -n "$m" ]; then
                    add_problem "config.ini: $m"
                fi
            done
        fi
    fi

    show_versions
    acb_accel_probe "$SdkRoot"
    acb_step "acceleration probe (exit $ACCEL_CODE): $(acb_accel_summary)"

    problem_count=$(printf '%s\n' "$problems" | tr ';' '\n' | sed '/^[[:space:]]*$/d' | wc -l | tr -d '[:space:]')
    if [ "$problem_count" -gt 0 ]; then
        printf '%s\n' "$problems" | tr ';' '\n' | while IFS= read -r p; do
            p=$(printf '%s' "$p" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')
            if [ -n "$p" ]; then acb_warn "$p"; fi
        done
        acb_fail "verification failed: $problem_count problem(s)"
    fi
    acb_step "verification OK"
    exit 0
fi

# AVD 约 9~13 GB；快照复制需约 2 倍空间，剩余低于 30 GB 就告警。
free_kb=$(acb_free_kb "$AvdRoot")
if [ "$free_kb" -ge 0 ]; then
    free_gb=$(awk -v kb="$free_kb" 'BEGIN { printf "%.1f", kb / 1048576 }')
    if [ "$free_kb" -lt $((30 * 1048576)) ]; then
        acb_warn "only $free_gb GB free on the AVD volume; the AVD plus snapshot needs ~30 GB"
    fi
fi

missing=()
for pkg in "${REQUIRED_PACKAGES[@]}"; do
    if ! package_installed "$pkg"; then
        missing[${#missing[@]}]=$pkg
    fi
done
if [ "${#missing[@]}" -gt 0 ]; then
    missing_str=$(printf '%s' "${missing[0]}")
    idx=1
    while [ "$idx" -lt "${#missing[@]}" ]; do
        missing_str="$missing_str, ${missing[$idx]}"
        idx=$((idx + 1))
    done
    if [ "$SkipSdkInstall" -eq 1 ]; then
        acb_fail "SDK packages missing and --skip-sdk-install set: $missing_str"
    fi
    acb_step "missing SDK packages: $missing_str"
    if [ "$DryRun" -eq 1 ]; then
        acb_step "[dry-run] would run: sdkmanager --sdk_root=$SdkRoot --install $missing_str"
    else
        acb_step "accepting SDK licenses"
        yes 2>/dev/null | "$ACB_SDKMANAGER" --sdk_root="$SdkRoot" --licenses >/dev/null 2>&1 || true
        acb_step "installing: $missing_str"
        install_log=$(mktemp "${TMPDIR:-/tmp}/psa-sdk-install.XXXXXX") || acb_fail "mktemp failed"
        if ! yes 2>/dev/null | "$ACB_SDKMANAGER" --sdk_root="$SdkRoot" --install "${missing[@]}" >"$install_log" 2>&1; then
            acb_warn "sdkmanager --install failed; last output:"
            tail -n 20 "$install_log" | sed 's/^/  /'
            rm -f "$install_log"
            acb_fail "sdkmanager --install failed"
        fi
        rm -f "$install_log"
        if ! sdk_list=$(acb_list_installed "$SdkRoot"); then
            acb_fail "sdkmanager --list_installed failed after install"
        fi
        for pkg in "${REQUIRED_PACKAGES[@]}"; do
            if ! package_installed "$pkg"; then
                acb_fail "SDK package still missing after install: $pkg"
            fi
        done
        acb_step "SDK packages installed"
    fi
else
    acb_step "required SDK packages are installed"
fi

show_versions

avd_exists=0
if [ -f "$IniPath" ] && [ -f "$ConfigPath" ]; then
    avd_exists=1
fi

if [ "$Force" -eq 1 ] && [ "$avd_exists" -eq 1 ]; then
    if [ "$avd_running" -eq 1 ]; then
        acb_fail "refusing to recreate a running AVD; stop it first (run.sh --stop)"
    fi
    acb_step "-Force: recreate AVD $AvdName"
    if [ "$DryRun" -eq 1 ]; then
        acb_step "[dry-run] would remove $AvdDir and $IniPath"
        acb_step "[dry-run] would write <name>.ini + <name>.avd/config.ini directly (avdmanager create avd is not usable with this SDK)"
    else
        rm -rf "$AvdDir" "$IniPath"
        avd_exists=0
    fi
fi

if [ "$avd_exists" -eq 0 ]; then
    if [ "$DryRun" -eq 1 ]; then
        acb_step "[dry-run] would create AVD $AvdName (arm64-v8a, $ACB_DEVICE_PROFILE-equivalent) by writing $IniPath and $ConfigPath directly"
    else
        if [ "$avd_running" -eq 1 ]; then
            acb_fail "refusing to create AVD while an emulator with the same name is running"
        fi
        mkdir -p "$AvdRoot" || acb_fail "cannot create AVD root: $AvdRoot"
        mkdir -p "$AvdDir" || acb_fail "cannot create AVD directory: $AvdDir"
        acb_step "creating AVD $AvdName (arm64-v8a, $ACB_DEVICE_PROFILE-equivalent)"
        write_base_config
        write_avd_ini
        if [ ! -f "$ConfigPath" ] || [ ! -f "$IniPath" ]; then
            acb_fail "AVD generation did not create $ConfigPath / $IniPath"
        fi
        acb_step "AVD created"
    fi
else
    acb_step "AVD already exists"
fi

if [ "$DryRun" -eq 1 ]; then
    planned=$(plan_config_changes)
    if [ -z "$planned" ]; then
        acb_step "[dry-run] config.ini already matches the required values"
    else
        acb_step "[dry-run] would update config.ini: $planned"
    fi
else
    CHANGES=""
    apply_config_changes
    if [ -z "$CHANGES" ]; then
        acb_step "config.ini matches the required values"
    else
        acb_step "config.ini updated: $CHANGES"
    fi
fi

# macOS 的加速是内置 Hypervisor.framework（HVF），探测仅作信息展示。
acb_accel_probe "$SdkRoot"
if [ "$ACCEL_CODE" -eq 0 ]; then
    acb_step "acceleration OK: $(acb_accel_summary)"
else
    acb_warn "acceleration probe failed (exit $ACCEL_CODE): $ACCEL_TEXT"
    acb_warn "on macOS acceleration needs Hypervisor.framework (built in on Apple Silicon); check that the host is an arm64 Mac"
fi

acb_step "done. next: bash ${ACB_SCRIPT_DIR#"$REPO_ROOT"/}/run.sh --avd-name $AvdName"
exit 0
