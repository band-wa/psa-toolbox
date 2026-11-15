#!/usr/bin/env python3
"""把本项目 android.car 注入官方 Android 9 AVD 的“最小 bootclasspath 机制”，并安装 com.android.car 系统服务。

机制：
  1) 用 api/build/android.car.jar 替换 /system/framework/android.test.base.jar；
  2) 删除 /system/framework/x86_64/boot-android.test.base.{art,art.rel,oat,vdex}
     四个 boot image 槽位文件。
  官方 AVD 是 system-as-root，ramdisk 不参与启动；boot image 槽位缺失时 ART 会从
  对应的 bootclasspath jar 解 dex，zygote 通过 BootClassLoader 加载 android.car.*。
  zygote rc 保持 stock：不改 BOOTCLASSPATH、不加 -Ximage、不动 ramdisk。

服务系统化：service-debug.apk 拷到 /system/app/CarService/CarService.apk，
重启后 pm path 显示 /system/app/CarService/CarService.apk。

用法（在仓库根目录执行）：
  python tools/android-car-boot/avd/provision.py --dry-run
  python tools/android-car-boot/avd/provision.py --serial emulator-5554 \
      --apk app/build/outputs/apk/debug/app-debug.apk \
      --probe-apk verify/bootinject/out/probe-app.apk
  python tools/android-car-boot/avd/provision.py --verify --serial emulator-5554
  python tools/android-car-boot/avd/provision.py --rollback --serial emulator-5554

备份与回滚：只有注入前设备是干净原版（jar 指纹 + 4 个槽位齐全）时才建立备份并写 .complete；
AVD 已被改过时跳过备份，--rollback 会拒绝执行（用 setup.ps1 -Force 重建 AVD）。回滚只从可信
备份恢复（优先设备 /data/avd-orig，其次本地 build/avd-orig），不会恢复残留的旧 jar。
--verify 不改系统镜像，但会 adb root、安装/拉起 probe 与 APP 并做 UI 交互，同时写状态文件。
校验前会自动 adb root，zygote/environ rc 必须与已知原版 sha256 完全一致。

退出码：0 成功；2 失败（失败时尽量自动回滚已做的系统修改）。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]  # avd -> android-car-boot -> tools -> 仓库根
ACB = ROOT / "tools" / "android-car-boot"
JAR_DEFAULT = ACB / "api" / "build" / "android.car.jar"
SERVICE_APK_DEFAULT = ACB / "service" / "build" / "outputs" / "apk" / "debug" / "service-debug.apk"
PROBE_APK_DEFAULT = ROOT / "verify" / "bootinject" / "out" / "probe-app.apk"
APP_APK_DEFAULT = ROOT / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
BUILD_DIR = ACB / "build"
STATE_PATH = BUILD_DIR / "avd-provision-state.json"
LOCAL_BACKUP = BUILD_DIR / "avd-orig"

DEFAULT_SERIAL = "emulator-5554"
TMP_DIR = "/data/local/tmp"

# 设备路径
BOOT_JAR = "/system/framework/android.test.base.jar"
SLOT_DIR = "/system/framework/x86_64"
SLOT_NAMES = (
    "boot-android.test.base.art",
    "boot-android.test.base.art.rel",
    "boot-android.test.base.oat",
    "boot-android.test.base.vdex",
)
DEVICE_BACKUP = "/data/avd-orig"
CAR_SYSTEM_DIR = "/system/app/CarService"
CAR_SYSTEM_APK = CAR_SYSTEM_DIR + "/CarService.apk"
ZYGOTE_RC = "/init.zygote64_32.rc"
ENVIRON_RC = "/init.environ.rc"

# 已知实测指纹（在 setup.ps1 -Force 重建的干净 AVD 上标定；校验始终以本地文件为准）
REF_JAR = ("cb71ec777cba3566ba3127e3dcd8134bc3f4aad176cc9dcebb5bdeff6bf1d42b", 12341)
REF_SERVICE_APK = ("aa17a87a6cb5e388bb3bb7c82de344d1e46762880389f908e0bffae8f47d8fdd", 35703)
REF_STOCK_JAR = ("1c16f5976318fadcf7add92c518a93896f99e2a78f742b7347d8539103264882", 183)
REF_ZYGOTE_RC_SHA256 = "93c8ecbdf792990fde0c839e03f816d552c18fc7f2c70561db2d1895bc4983c8"
REF_ENVIRON_RC_SHA256 = "2d8a8381e90e8a25367088ed2c5a8fd8d35f9a36ceb416d74c6de5d25c6fc66a"
REF_SLOT_BYTES = 87649

CAR_PKG = "com.android.car"
APP_PKG = "com.bandwa.openadb"
# PSAToolBoxService 未导出，adb shell 不能 am startservice（报 not exported from uid 1000），
# 校验一律从 launcher 入口拉起（monkey 兜底 am start）。
APP_ACTIVITY = APP_PKG + "/.presentation.main.MainActivity"
# 首次启动会弹「使用协议」（倒计时结束才能点「同意」）；CarGateway 只在「维护诊断」页初始化
# （ToolsSettingsViewModel 里 CarGateway.ensureStarted），所以要自动走一遍 UI。
APP_UI_TERMS = "同意"
APP_UI_TOOLS_TAB = "维护诊断"
# 「维护诊断」页独有的内容文本（页签文字在页签栏里始终存在，不能用来判断是否已切页）
APP_UI_TOOLS_MARKERS = ("当前档位", "剩余油量", "原装车机检测")
APP_WAIT_SECONDS = 8
PROBE_PKG = "com.bandwa.bootinject.probe"
PROBE_ACTIVITY = ".ProbeActivity"
PROBE_TAG = "CARPROBE"


# --------------------------------------------------------------------------- 基础工具

def log(msg: str) -> None:
    print(msg, flush=True)


def die(msg: str) -> int:
    log("错误：" + msg)
    return 2


class ProvisionError(RuntimeError):
    pass


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def bytes_human(size: int) -> str:
    for unit in ("B", "KB", "MB", "GB"):
        if size < 1024 or unit == "GB":
            return f"{size:.1f} {unit}" if unit != "B" else f"{size} B"
        size //= 1024
    return f"{size} B"


def find_adb() -> str:
    candidates = []
    for env in ("ANDROID_SDK_ROOT", "ANDROID_HOME", "ANDROID_SDK"):
        root = os.environ.get(env)
        if root:
            candidates.append(Path(root) / "platform-tools" / "adb.exe")
    candidates.append(Path(r"D:\Tools\Android\sdk\platform-tools\adb.exe"))
    candidates.append(Path(r"D:\Tools\Android\sdk\platform-tools\adb"))
    found = shutil.which("adb")
    if found:
        candidates.append(Path(found))
    for cand in candidates:
        if cand.is_file():
            return str(cand)
    return "adb"


# --------------------------------------------------------------------------- adb

class Device:
    def __init__(self, adb: str, serial: str) -> None:
        self.adb = adb
        self.serial = serial

    def _call(self, *args: str, timeout: int = 180) -> subprocess.CompletedProcess:
        cmd = [self.adb, "-s", self.serial, *args]
        try:
            return subprocess.run(
                cmd, capture_output=True, text=True, encoding="utf-8",
                errors="replace", timeout=timeout,
            )
        except FileNotFoundError as exc:
            raise ProvisionError(f"找不到 adb：{self.adb}（用 --adb 指定）") from exc
        except subprocess.TimeoutExpired as exc:
            raise ProvisionError(f"adb 命令超时（{timeout}s）：{' '.join(args)}") from exc

    def out(self, *args: str, timeout: int = 180) -> str:
        proc = self._call(*args, timeout=timeout)
        return ((proc.stdout or "") + (proc.stderr or "")).strip()

    def shell(self, script: str, timeout: int = 180, check: bool = False) -> str:
        proc = self._call("shell", script, timeout=timeout)
        text = ((proc.stdout or "") + (proc.stderr or "")).strip()
        if check and proc.returncode != 0:
            raise ProvisionError(f"设备命令失败（exit {proc.returncode}）：{script}")
        return text

    def online(self) -> bool:
        return self.out("get-state", timeout=20) == "device"

    def wait_online(self, timeout: int = 30) -> bool:
        deadline = time.time() + timeout
        while time.time() < deadline:
            if self.online():
                return True
            time.sleep(2)
        return False

    def boot_completed(self) -> bool:
        return self.shell("getprop sys.boot_completed", timeout=20) == "1"

    def wait_boot(self, timeout: int) -> int:
        start = time.time()
        while time.time() - start < timeout:
            if self.online() and self.boot_completed():
                return int(time.time() - start)
            time.sleep(3)
        raise ProvisionError(f"等待开机超时（{timeout}s）：{self.serial}")

    def prop(self, name: str) -> str:
        return self.shell(f"getprop {name}", timeout=20)

    def push(self, local: Path, remote: str) -> None:
        proc = self._call("push", str(local), remote, timeout=600)
        if proc.returncode != 0:
            raise ProvisionError(f"adb push 失败：{local} -> {remote}：{proc.stderr or proc.stdout}")

    def pull(self, remote: str, local: Path) -> None:
        proc = self._call("pull", remote, str(local), timeout=300)
        if proc.returncode != 0 or not local.is_file():
            raise ProvisionError(f"adb pull 失败：{remote} -> {local}：{proc.stderr or proc.stdout}")

    def exists(self, remote: str) -> bool:
        return self.shell(f"[ -e {remote} ] && echo YES || echo NO") == "YES"

    def sha256(self, remote: str) -> str:
        text = self.shell(f"sha256sum {remote} 2>/dev/null")
        match = re.search(r"\b([0-9a-f]{64})\b", text)
        return match.group(1) if match else ""

    def install_apk(self, apk: Path) -> str:
        proc = self._call("install", "-r", "-d", str(apk), timeout=600)
        text = ((proc.stdout or "") + (proc.stderr or "")).strip()
        if proc.returncode != 0 or "Success" not in text:
            raise ProvisionError(f"adb install 失败：{apk}：{text}")
        return text.splitlines()[-1]

    def uninstall(self, package: str) -> bool:
        proc = self._call("uninstall", package, timeout=180)
        text = ((proc.stdout or "") + (proc.stderr or "")).lower()
        return "success" in text

    def pm_path(self, package: str) -> str:
        for line in self.shell(f"pm path {package} 2>/dev/null").splitlines():
            if line.startswith("package:"):
                return line.strip()
        return ""


# --------------------------------------------------------------------------- 本地产物

class Context:
    def __init__(self) -> None:
        self.jar: Path = JAR_DEFAULT
        self.jar_sha = ""
        self.service: Path = SERVICE_APK_DEFAULT
        self.service_sha = ""
        self.app: Path | None = None
        self.app_sha = ""
        self.probe: Path | None = None
        self.probe_sha = ""
        self.serial = DEFAULT_SERIAL
        self.modified = False


def check_local_artifacts(ctx: Context, args: argparse.Namespace) -> None:
    ctx.jar = Path(args.api_jar).resolve()
    ctx.service = Path(args.service_apk).resolve()
    if not ctx.jar.is_file():
        raise ProvisionError(f"api jar 不存在：{ctx.jar}（先运行 bash tools/android-car-boot/api/build.sh）")
    if not ctx.service.is_file():
        raise ProvisionError(
            f"服务 APK 不存在：{ctx.service}"
            "（先运行 .\\gradlew.bat :tools:android-car-boot:service:assembleDebug --offline）"
        )
    ctx.jar_sha = sha256_file(ctx.jar)
    ctx.service_sha = sha256_file(ctx.service)
    log(f"api jar   : {ctx.jar}  {ctx.jar.stat().st_size} B  sha256={ctx.jar_sha}")
    log(f"服务 APK  : {ctx.service}  {ctx.service.stat().st_size} B  sha256={ctx.service_sha}")
    if (ctx.jar_sha, ctx.jar.stat().st_size) != REF_JAR:
        log(f"提示：jar 与实测指纹不同（实测 {REF_JAR[1]} B / {REF_JAR[0][:12]}…），继续以当前文件为准")
    if (ctx.service_sha, ctx.service.stat().st_size) != REF_SERVICE_APK:
        log(f"提示：服务 APK 与实测指纹不同（实测 {REF_SERVICE_APK[1]} B），继续以当前文件为准")

    app = args.apk
    if app:
        ctx.app = Path(app).resolve()
        if not ctx.app.is_file():
            raise ProvisionError(f"app APK 不存在：{ctx.app}")
        ctx.app_sha = sha256_file(ctx.app)
        log(f"APP APK   : {ctx.app}  {ctx.app.stat().st_size} B  sha256={ctx.app_sha}")

    probe = args.probe_apk
    if probe:
        ctx.probe = Path(probe).resolve()
    elif PROBE_APK_DEFAULT.is_file():
        ctx.probe = PROBE_APK_DEFAULT
    if ctx.probe is not None:
        if not ctx.probe.is_file():
            raise ProvisionError(f"probe APK 不存在：{ctx.probe}")
        ctx.probe_sha = sha256_file(ctx.probe)
        log(f"probe APK : {ctx.probe}  {ctx.probe.stat().st_size} B  sha256={ctx.probe_sha}")


# --------------------------------------------------------------------------- 预检 / root

def preflight(dev: Device) -> dict:
    log("== 设备预检")
    # run.ps1 已保证 boot 完成，这里设备缺失就快速失败
    if not dev.wait_online(timeout=15):
        raise ProvisionError(f"adb 设备不在线：{dev.serial}（先运行 run.ps1 启动 AVD）")
    if not dev.boot_completed():
        raise ProvisionError(f"{dev.serial} 尚未开机完成（sys.boot_completed != 1）")
    info = {
        "serial": dev.serial,
        "sdk": dev.prop("ro.build.version.sdk"),
        "release": dev.prop("ro.build.version.release"),
        "build_type": dev.prop("ro.build.type"),
        "debuggable": dev.prop("ro.debuggable"),
        "system_root_image": dev.prop("ro.build.system_root_image"),
        "fingerprint": dev.prop("ro.build.fingerprint"),
    }
    for key in ("sdk", "release", "build_type", "debuggable", "system_root_image", "fingerprint"):
        log(f"  {key:16s}= {info[key]}")
    if info["sdk"] != "28":
        raise ProvisionError(f"系统版本不是 API 28：ro.build.version.sdk={info['sdk']}")
    if info["build_type"] not in ("userdebug", "eng"):
        raise ProvisionError(f"需要 userdebug/eng 镜像（当前 ro.build.type={info['build_type']}）")
    if info["debuggable"] != "1":
        raise ProvisionError("ro.debuggable != 1，无法 adb root（官方 AVD 必须装 userdebug 镜像）")
    if info["system_root_image"] != "true":
        raise ProvisionError("ro.build.system_root_image != true，本机制只适用于 system-as-root AVD")
    return info


def ensure_root(dev: Device) -> None:
    log("== adb root")
    text = dev.out("root", timeout=60)
    log("  " + (text or "(no output)"))
    if "cannot run as root" in text.lower() or "not allowed" in text.lower():
        raise ProvisionError("该镜像不允许 adb root（必须是 userdebug/eng 版本）")
    if not dev.wait_online(timeout=60):
        raise ProvisionError("adb root 后设备未恢复在线")
    uid = dev.shell("id -u")
    if uid != "0":
        raise ProvisionError(f"adb shell 不是 root（id -u={uid}）")
    log("  uid=0")


def ensure_system_rw(dev: Device) -> None:
    log("== 挂载 /system 可写")
    attempts = (("adb remount", "remount"), ("mount -o rw,remount /system", None))
    last = ""
    for label, sub in attempts:
        last = dev.out(sub, timeout=120) if sub else dev.shell(label, timeout=120)
        log(f"  {label}: {last or '(no output)'}")
        probe = "/system/framework/.psa_provision_probe"
        result = dev.shell(
            f"echo probe > {probe} 2>/dev/null && rm -f {probe} && echo RW_OK || echo RW_FAIL"
        )
        if result.strip().endswith("RW_OK"):
            log("  /system 可写")
            return
    raise ProvisionError(
        "adb remount 后 /system 仍不可写；请确认 AVD 以 -writable-system 启动，"
        "必要时先 adb disable-verity 再重启"
    )


# --------------------------------------------------------------------------- 备份

def _slot_bytes(dev: Device, prefix: str) -> int | None:
    """统计槽位总字节数（跟随符号链接）；统计不出来时返回 None。

    设备的 toybox `du` 不支持 `-b`，所以逐文件用 `wc -c` 统计再取 total 行。
    """
    out = dev.shell(f"wc -c {prefix}/boot-android.test.base.* 2>/dev/null | tail -1").strip()
    parts = out.split()
    if len(parts) >= 2 and parts[0].isdigit():
        return int(parts[0])
    return None


def _local_backup_problems() -> list[str]:
    """本地缓存不可信的原因；空列表表示可用。"""
    if not (LOCAL_BACKUP / ".complete").is_file():
        return ["缺少 .complete 标记"]
    problems: list[str] = []
    jar = LOCAL_BACKUP / "android.test.base.jar"
    if not jar.is_file():
        problems.append("缺少 android.test.base.jar")
    elif sha256_file(jar) != REF_STOCK_JAR[0]:
        problems.append(f"jar 不是原版（sha256={sha256_file(jar)[:12]}…）")
    missing = [name for name in SLOT_NAMES if not (LOCAL_BACKUP / "x86_64" / name).is_file()]
    if missing:
        problems.append("缺少槽位文件：" + ", ".join(missing))
    else:
        total = sum((LOCAL_BACKUP / "x86_64" / name).stat().st_size for name in SLOT_NAMES)
        if total != REF_SLOT_BYTES:
            problems.append(f"槽位总字节数 {total}（原版 {REF_SLOT_BYTES} B）")
    sums = LOCAL_BACKUP / "SHA256SUMS"
    if sums.is_file():
        entries = _read_sums(sums.read_text(encoding="utf-8"))
        if len(entries) != 1 + len(SLOT_NAMES):
            problems.append(f"SHA256SUMS 条目数 {len(entries)}（应为 {1 + len(SLOT_NAMES)}）")
        for rel, expected in entries:
            path = LOCAL_BACKUP / rel
            if not path.is_file():
                problems.append(f"SHA256SUMS 列出的 {rel} 不存在")
            elif sha256_file(path) != expected:
                problems.append(f"{rel} 内容与 SHA256SUMS 不符")
    return problems


def _read_sums(text: str) -> list[tuple[str, str]]:
    """解析 sha256sum 输出（`<sha>  <相对路径>`）；无法解析的行忽略。"""
    entries: list[tuple[str, str]] = []
    for line in text.splitlines():
        parts = line.split()
        if len(parts) == 2 and len(parts[0]) == 64:
            entries.append((parts[1].lstrip("*"), parts[0]))
    return entries


def _write_local_sums() -> None:
    """把本地缓存的实际 sha256 写成 SHA256SUMS，供下次校验内容是否被改动。"""
    lines = [f"{sha256_file(LOCAL_BACKUP / 'android.test.base.jar')}  android.test.base.jar"]
    lines += [
        f"{sha256_file(LOCAL_BACKUP / 'x86_64' / name)}  x86_64/{name}" for name in SLOT_NAMES
    ]
    (LOCAL_BACKUP / "SHA256SUMS").write_text("\n".join(lines) + "\n", encoding="utf-8")


def local_backup_complete() -> bool:
    """本地缓存是否可用：原版 jar + 4 个槽位齐全。"""
    return not _local_backup_problems()


def _stock_problems(dev: Device) -> list[str]:
    """设备当前 boot 产物不是干净原版的原因；空列表表示是原版。"""
    problems: list[str] = []
    jar_sha = dev.sha256(BOOT_JAR)
    jar_size = dev.shell(f"wc -c < {BOOT_JAR} 2>/dev/null").strip()
    if (jar_sha, jar_size) != (REF_STOCK_JAR[0], str(REF_STOCK_JAR[1])):
        problems.append(
            f"{BOOT_JAR} 不是原版（{jar_size or '?'} B / {(jar_sha or '?')[:12]}…，"
            f"原版 {REF_STOCK_JAR[1]} B / {REF_STOCK_JAR[0][:12]}…）"
        )
    missing = [name for name in SLOT_NAMES if not dev.exists(f"{SLOT_DIR}/{name}")]
    if missing:
        problems.append("槽位文件缺失：" + ", ".join(missing))
    else:
        total = _slot_bytes(dev, SLOT_DIR)
        if total is None:
            problems.append(f"无法统计槽位文件大小（{SLOT_DIR}/boot-android.test.base.*）")
        elif total != REF_SLOT_BYTES:
            problems.append(f"槽位总字节数 {total}（原版 {REF_SLOT_BYTES} B）")
    return problems


def _device_backup_problems(dev: Device, require_marker: bool = True) -> list[str]:
    """设备备份不可信的原因；空列表表示可信。

    require_marker=False 时只校验内容（用于「内容完好但丢了 .complete 标记」的修复场景）。
    """
    if require_marker and not dev.exists(DEVICE_BACKUP + "/.complete"):
        return ["缺少 .complete 标记"]
    problems: list[str] = []
    jar = DEVICE_BACKUP + "/android.test.base.jar"
    if not dev.exists(jar):
        problems.append("缺少 android.test.base.jar")
    else:
        jar_sha = dev.sha256(jar)
        if jar_sha != REF_STOCK_JAR[0]:
            problems.append(f"jar 不是原版（sha256={(jar_sha or '?')[:12]}…）")
    missing = [name for name in SLOT_NAMES if not dev.exists(f"{DEVICE_BACKUP}/x86_64/{name}")]
    if missing:
        problems.append("缺少槽位文件：" + ", ".join(missing))
    else:
        total = _slot_bytes(dev, DEVICE_BACKUP + "/x86_64")
        if total is None:
            problems.append(f"无法统计备份槽位文件大小（{DEVICE_BACKUP}/x86_64/boot-android.test.base.*）")
        elif total != REF_SLOT_BYTES:
            problems.append(f"备份槽位总字节数 {total}（原版 {REF_SLOT_BYTES} B）")
    sums = DEVICE_BACKUP + "/SHA256SUMS"
    if dev.exists(sums):
        out = dev.shell(f"cd {DEVICE_BACKUP} && sha256sum -c SHA256SUMS 2>&1", timeout=120)
        failed = [line.strip() for line in out.splitlines() if line.strip().endswith("FAILED")]
        ok_count = sum(1 for line in out.splitlines() if line.strip().endswith(": OK"))
        if failed:
            problems.append("备份内容与 SHA256SUMS 不符：" + "；".join(failed[:2]))
        elif ok_count != 1 + len(SLOT_NAMES):
            problems.append(f"备份 SHA256SUMS 只通过 {ok_count} 项（应为 {1 + len(SLOT_NAMES)}）")
    return problems


def _device_backup_ok(dev: Device) -> bool:
    """设备备份是否可信：有 .complete 标记且原版 jar + 4 个槽位齐全。"""
    return not _device_backup_problems(dev)


def ensure_backup(dev: Device, ctx: Context) -> None:
    """首次注入前把原版 jar 与 4 个槽位文件备份到 /data/avd-orig/，并缓存到 repo build/ 下。

    只有设备当前是干净原版（jar 指纹 + 4 个槽位齐全）时才建立备份；否则不写 .complete 标记，
    restore_originals() 会拒绝回滚——此时应 setup.ps1 -Force 重建 AVD 再注入。
    设备上的旧备份不可信会被标 .untrusted 并在设备干净时重建；本地缓存只在不可信时重建，
    不会因为设备侧备份丢失/不可信而删掉仍然可信的本地缓存。
    """
    marker = DEVICE_BACKUP + "/.complete"
    log("== 备份原版 boot 产物")
    if (
        not _device_backup_ok(dev)
        and dev.exists(DEVICE_BACKUP)
        and not _device_backup_problems(dev, require_marker=False)
    ):
        # 内容完好、只是缺 .complete 标记（例如标记被误删）：补写标记，别删掉这份可信备份
        dev.shell(
            f"cd {DEVICE_BACKUP} && sha256sum android.test.base.jar x86_64/* > SHA256SUMS; echo ok > .complete",
            check=True,
        )
        log(f"  设备备份内容完好但缺少 .complete 标记，已补写：{DEVICE_BACKUP}")
    if _device_backup_ok(dev):
        log(f"  设备备份已存在：{DEVICE_BACKUP}")
    else:
        problems = _stock_problems(dev)
        if problems:
            log("  警告：当前 AVD 不是干净原版，无法建立可回滚的备份：")
            for item in problems:
                log("    - " + item)
            if dev.exists(marker):
                dev.shell(f"rm -f {marker}", check=False)
                dev.shell(f"echo not-stock > {DEVICE_BACKUP}/.untrusted", check=False)
                log(f"  设备上的旧备份不可信，已标记 {DEVICE_BACKUP}/.untrusted（不会用于回滚）")
            else:
                dev.shell(f"rm -rf {DEVICE_BACKUP}", check=False)
            log("  跳过备份；--rollback 将不可用（注入本身继续）")
            if local_backup_complete():
                log(f"  注意：本地缓存仍然可信（{LOCAL_BACKUP}），--rollback 会使用它")
            log("  需要干净基线时：setup.ps1 -Force 重建 AVD 后再运行 provision.py")
            return
        dev.shell(f"rm -rf {DEVICE_BACKUP} && mkdir -p {DEVICE_BACKUP}/x86_64", check=True)
        # cp -a 保留 x86_64/boot-android.test.base.vdex 的符号链接形态
        dev.shell(f"cp -a {BOOT_JAR} {DEVICE_BACKUP}/android.test.base.jar", check=True)
        for name in SLOT_NAMES:
            dev.shell(f"cp -a {SLOT_DIR}/{name} {DEVICE_BACKUP}/x86_64/{name}", check=True)
        dev.shell(
            f"cd {DEVICE_BACKUP} && sha256sum android.test.base.jar x86_64/* > SHA256SUMS; echo ok > .complete",
            check=True,
        )
        log("  已备份到 " + DEVICE_BACKUP)
        problems = _device_backup_problems(dev)
        if problems:
            raise ProvisionError("设备备份写入后校验失败：" + "；".join(problems))

    if local_backup_complete():
        log(f"  本地缓存已存在：{LOCAL_BACKUP}")
        if not (LOCAL_BACKUP / "SHA256SUMS").is_file():
            _write_local_sums()
            log("  已补充本地缓存的 SHA256SUMS（下次可校验内容是否被改动）")
        return
    LOCAL_BACKUP.mkdir(parents=True, exist_ok=True)
    (LOCAL_BACKUP / "x86_64").mkdir(parents=True, exist_ok=True)
    (LOCAL_BACKUP / ".complete").unlink(missing_ok=True)
    dev.pull(f"{DEVICE_BACKUP}/android.test.base.jar", LOCAL_BACKUP / "android.test.base.jar")
    for name in SLOT_NAMES:
        dev.pull(f"{DEVICE_BACKUP}/x86_64/{name}", LOCAL_BACKUP / "x86_64" / name)
    problems = _local_backup_problems()
    if problems:
        (LOCAL_BACKUP / ".complete").unlink(missing_ok=True)
        raise ProvisionError("本地缓存校验失败：" + "；".join(problems))
    _write_local_sums()
    (LOCAL_BACKUP / ".complete").write_text("ok\n", encoding="utf-8")
    log(f"  本地缓存：{LOCAL_BACKUP}")


def hardware_changes(ctx: Context) -> list[str]:
    changes = [
        f"{BOOT_JAR} 替换为 android.car.jar（{ctx.jar.name}）",
        "删除 " + ", ".join(f"{SLOT_DIR}/{name}" for name in SLOT_NAMES),
        f"{CAR_SYSTEM_APK} 放入服务 APK",
    ]
    if ctx.app is not None:
        changes.append(f"安装 APP APK（{ctx.app.name}）")
    if ctx.probe is not None:
        changes.append(f"安装 probe APK（{ctx.probe.name}）")
    return changes


# --------------------------------------------------------------------------- 注入

def apply_boot_jar(dev: Device, ctx: Context) -> None:
    log("== 替换 /system/framework/android.test.base.jar")
    tmp = f"{TMP_DIR}/android.car.jar"
    dev.push(ctx.jar, tmp)
    dev.shell(
        f"cp -f {tmp} {BOOT_JAR} && chown root:root {BOOT_JAR} && chmod 0644 {BOOT_JAR}"
        f"; restorecon {BOOT_JAR} 2>/dev/null || true",
        check=True,
    )
    remote_sha = dev.sha256(BOOT_JAR)
    if remote_sha != ctx.jar_sha:
        raise ProvisionError(f"jar 写入校验失败：设备 {remote_sha or '?'} != 本地 {ctx.jar_sha}")
    log(f"  已写入，sha256={remote_sha}")


def remove_slot_files(dev: Device) -> None:
    log("== 删除 boot image 槽位文件（ART 改从 jar 解 dex）")
    dev.shell("rm -f " + " ".join(f"{SLOT_DIR}/{name}" for name in SLOT_NAMES), check=True)
    left = dev.shell(f"ls {SLOT_DIR} 2>/dev/null | grep -c 'boot-android.test.base' || true").strip()
    if left not in ("0", ""):
        raise ProvisionError(f"槽位文件仍存在（{left} 个）")
    log("  槽位已清空")


def install_system_service(dev: Device, ctx: Context) -> None:
    log("== 安装 com.android.car 为系统应用")
    tmp = f"{TMP_DIR}/CarService.apk"
    dev.push(ctx.service, tmp)
    dev.shell(
        f"mkdir -p {CAR_SYSTEM_DIR} && cp -f {tmp} {CAR_SYSTEM_APK}"
        f" && chown root:root {CAR_SYSTEM_APK} && chmod 0644 {CAR_SYSTEM_APK}"
        f"; restorecon -R {CAR_SYSTEM_DIR} 2>/dev/null || true",
        check=True,
    )
    remote_sha = dev.sha256(CAR_SYSTEM_APK)
    if remote_sha != ctx.service_sha:
        raise ProvisionError(f"服务 APK 写入校验失败：设备 {remote_sha or '?'} != 本地 {ctx.service_sha}")
    log(f"  已写入 {CAR_SYSTEM_APK}，sha256={remote_sha}")

    # /data 里已有的旧安装会遮挡系统版，先卸载（失败不致命）
    path = dev.pm_path(CAR_PKG)
    if path.startswith("package:/data"):
        log(f"  卸载 /data 旧安装：{path}")
        dev.uninstall(CAR_PKG)
    elif path:
        log(f"  当前安装：{path}")


def install_app(dev: Device, ctx: Context) -> None:
    if ctx.app is None:
        return
    log("== 安装 APP APK")
    log("  " + dev.install_apk(ctx.app))


def reboot(dev: Device, timeout: int) -> None:
    log("== 重启 AVD")
    try:
        proc = dev._call("reboot", timeout=60)
        log("  " + (((proc.stdout or "") + (proc.stderr or "")).strip() or "(reboot sent)"))
    except ProvisionError:
        raise
    time.sleep(3)
    dev.wait_online(timeout=180)
    seconds = dev.wait_boot(timeout)
    log(f"  开机完成（{seconds}s）")


def restore_originals(dev: Device, ctx: Context) -> None:
    """回滚：还原 jar 与槽位、删除系统服务、重启。

    恢复源必须自身可信（原版 jar + 4 个槽位齐全）：优先设备备份，设备备份不可信时用本地缓存；
    不会因为 /data/avd-orig 里残留旧 jar 就恢复它。
    """
    log("== 回滚系统修改")
    ensure_root(dev)
    ensure_system_rw(dev)
    device_ok = _device_backup_ok(dev)
    local_ok = local_backup_complete()
    if not (device_ok or local_ok):
        raise ProvisionError(
            f"没有可信的原版备份（{DEVICE_BACKUP}/.complete 与 {LOCAL_BACKUP}/.complete 均不可用）："
            "该 AVD 在注入前不是干净原版，无法回滚；需要干净基线请 setup.ps1 -Force 重建 AVD"
        )
    if device_ok:
        log(f"  从设备备份恢复：{DEVICE_BACKUP}")
        dev.shell(f"cp -a {DEVICE_BACKUP}/android.test.base.jar {BOOT_JAR} && chown root:root {BOOT_JAR}"
                  f" && chmod 0644 {BOOT_JAR}; restorecon {BOOT_JAR} 2>/dev/null || true", check=True)
        for name in SLOT_NAMES:
            remote = f"{DEVICE_BACKUP}/x86_64/{name}"
            dev.shell(f"cp -a {remote} {SLOT_DIR}/{name} && chown root:root {SLOT_DIR}/{name}"
                      f" && restorecon {SLOT_DIR}/{name} 2>/dev/null || true", check=True)
    else:
        log(f"  设备备份不可信或缺失，使用本地缓存：{LOCAL_BACKUP}")
        # 先把 jar 与槽位全部推到 /data/local/tmp 并逐个校验 sha256，再统一写回 /system，
        # 避免中途失败留在「jar 已还原、槽位只还原一半」的不可用状态。
        staged = [("android.test.base.jar", LOCAL_BACKUP / "android.test.base.jar")]
        staged += [(name, LOCAL_BACKUP / "x86_64" / name) for name in SLOT_NAMES]
        for name, local in staged:
            remote = f"{TMP_DIR}/{name}"
            dev.push(local, remote)
            pushed_sha = dev.sha256(remote)
            if pushed_sha != sha256_file(local):
                raise ProvisionError(
                    f"本地缓存 {local.name} 推送到设备后 sha256 不一致（{pushed_sha or '?'}），"
                    "回滚中止（/system 未被修改）"
                )
        dev.shell(f"cp -f {TMP_DIR}/android.test.base.jar {BOOT_JAR} && chown root:root {BOOT_JAR}"
                  f" && chmod 0644 {BOOT_JAR}; restorecon {BOOT_JAR} 2>/dev/null || true", check=True)
        for name in SLOT_NAMES:
            dev.shell(f"cp -f {TMP_DIR}/{name} {SLOT_DIR}/{name} && chown root:root {SLOT_DIR}/{name}"
                      f" && chmod 0644 {SLOT_DIR}/{name}; restorecon {SLOT_DIR}/{name} 2>/dev/null || true",
                      check=True)
    restored_sha = dev.sha256(BOOT_JAR)
    log(f"  已还原 jar（sha256={restored_sha or '?'}）")
    if restored_sha != REF_STOCK_JAR[0]:
        raise ProvisionError(
            f"回滚后 {BOOT_JAR} 的 sha256={restored_sha or '?'} 不是原版 {REF_STOCK_JAR[0]}；"
            "备份内容不可靠，请用 setup.ps1 -Force 重建 AVD"
        )
    missing = [name for name in SLOT_NAMES if not dev.exists(f"{SLOT_DIR}/{name}")]
    if missing:
        raise ProvisionError("回滚后槽位仍缺失：" + ", ".join(missing) + "；请用 setup.ps1 -Force 重建 AVD")

    if dev.exists(CAR_SYSTEM_DIR):
        dev.shell(f"rm -rf {CAR_SYSTEM_DIR}", check=True)
        log(f"  已删除 {CAR_SYSTEM_DIR}")
    dev.uninstall(CAR_PKG)
    ctx.modified = False
    reboot(dev, 600)


# --------------------------------------------------------------------------- 校验

def _check_rc_stock(dev: Device, path: str, expected_sha: str) -> tuple[bool, str]:
    actual = dev.sha256(path)
    if actual == expected_sha:
        return True, "sha256 与已知原版一致"
    if not actual:
        return False, f"无法读取 {path}（需要 adb root）"
    content = dev.shell(f"cat {path} 2>/dev/null")
    if "android.car" in content or "-Ximage" in content:
        return False, f"内容含注入痕迹（sha256={actual}）"
    return False, (
        f"与已知原版不同（sha256={actual}，期望 {expected_sha[:12]}…）；"
        "该 AVD 已被改过，可用 setup.ps1 -Force 重建"
    )


def _ui_dump(dev: Device) -> str:
    """返回 uiautomator 窗口 XML；任何失败（含 adb 超时）都返回空串，不抛异常。"""
    for path in ("/sdcard/window_dump.xml", "/data/local/tmp/window_dump.xml"):
        try:
            ready = dev.shell(f"uiautomator dump {path} >/dev/null 2>&1; [ -s {path} ] && echo YES", timeout=90)
            if ready == "YES":
                xml = dev.shell(f"cat {path}", timeout=60)
                dev.shell(f"rm -f {path}", timeout=30)
                return xml
        except ProvisionError as exc:
            log(f"  （uiautomator dump 失败：{exc}）")
    return ""


def _ui_bounds(xml: str, prefix: str) -> tuple[int, int, int, int] | None:
    match = re.search(
        r'text="' + re.escape(prefix) + r'[^"]*"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
        xml,
    )
    if not match:
        return None
    return tuple(int(value) for value in match.groups())  # type: ignore[return-value]


def _ui_bounds_by_id(xml: str, resource_id: str) -> tuple[int, int, int, int] | None:
    match = re.search(
        r'resource-id="' + re.escape(resource_id) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',
        xml,
    )
    if not match:
        return None
    return tuple(int(value) for value in match.groups())  # type: ignore[return-value]


def _ui_tap(dev: Device, bounds: tuple[int, int, int, int]) -> None:
    x1, y1, x2, y2 = bounds
    dev.shell(f"input tap {(x1 + x2) // 2} {(y1 + y2) // 2}", timeout=60)


def _ui_accept_terms(dev: Device) -> bool:
    """确认首次启动的「使用协议」（按钮带倒计时）。返回是否已无该弹窗。"""
    for _ in range(12):
        xml = _ui_dump(dev)
        if not xml:
            return False
        if APP_UI_TERMS not in xml:
            return True
        countdown = re.search(r'text="' + APP_UI_TERMS + r" \((\d+)\s*s\)", xml)
        if countdown:
            time.sleep(int(countdown.group(1)) + 1)
            continue
        bounds = _ui_bounds(xml, APP_UI_TERMS)
        if bounds is None:
            return False
        log("  APP 首次启动：点击「同意」使用协议")
        _ui_tap(dev, bounds)
        time.sleep(2)
    return False


def _ui_tools_page_shown(xml: str) -> bool:
    """「维护诊断」页是否已显示（用页面独有的内容文本判断，页签文字不算）。"""
    return any(marker in xml for marker in APP_UI_TOOLS_MARKERS)


def _ui_open_tools_tab(dev: Device, initial_xml: str) -> bool:
    """打开「维护诊断」页并确认页面内容已切换（最多点 3 次）。"""
    if _ui_tools_page_shown(initial_xml):
        return True
    bounds = _ui_bounds(initial_xml, APP_UI_TOOLS_TAB)
    for attempt in range(3):
        if bounds is None:
            return False
        log(f"  APP：打开「{APP_UI_TOOLS_TAB}」页（CarGateway 在该页初始化）")
        _ui_tap(dev, bounds)
        deadline = time.time() + 8
        while time.time() < deadline:
            time.sleep(2)
            xml = _ui_dump(dev)
            if not xml:
                continue
            if _ui_tools_page_shown(xml):
                return True
            bounds = _ui_bounds(xml, APP_UI_TOOLS_TAB)
        if attempt < 2:
            xml = _ui_dump(dev)
            if not xml:
                return False
            bounds = _ui_bounds(xml, APP_UI_TOOLS_TAB)
    return False


def _ui_prepare_app(dev: Device) -> bool:
    """首启 UI 走查：关系统「兼容性」对话框 → 同意使用协议 → 打开「维护诊断」页。"""
    try:
        for _ in range(8):
            xml = _ui_dump(dev)
            if not xml:
                time.sleep(3)
                continue
            # targetSdk 25 的 APP/probe 在 API 28 首启会弹「built for an older version」系统对话框，
            # 不点掉会盖住 APP 界面（按钮 resource-id 固定为 android:id/button1）。
            ok_bounds = _ui_bounds_by_id(xml, "android:id/button1")
            if ok_bounds is not None:
                log("  APP：关闭系统「兼容性」对话框")
                _ui_tap(dev, ok_bounds)
                time.sleep(2)
                continue
            if APP_UI_TERMS in xml:
                if not _ui_accept_terms(dev):
                    return False
                continue
            if _ui_bounds(xml, APP_UI_TOOLS_TAB) is not None or _ui_tools_page_shown(xml):
                return _ui_open_tools_tab(dev, xml)
            time.sleep(3)
        return False
    except ProvisionError as exc:
        log(f"  （APP UI 自动化失败：{exc}）")
        return False


def run_verify(dev: Device, ctx: Context, args: argparse.Namespace) -> bool:
    log("== 校验")
    # rc 与槽位校验要读 /init*.rc、/system/framework，必须是 root（重启后 adbd 会掉回 shell）
    ensure_root(dev)
    checks: list[tuple[str, bool | None, str]] = []

    checks.append(("sys.boot_completed=1", dev.boot_completed(), ""))

    jar_sha = dev.sha256(BOOT_JAR)
    checks.append(("android.test.base.jar 是 api jar", jar_sha == ctx.jar_sha, f"sha256={jar_sha or '?'}"))

    slots = dev.shell(f"ls {SLOT_DIR} 2>/dev/null | grep -c 'boot-android.test.base' || true").strip()
    checks.append(("boot image 槽位已删除", slots in ("0", ""), f"剩余 {slots or '0'} 个"))
    # 槽位文件都删掉后 wc 统计不出来（None），只有残留文件时这项才会出现
    leftover_bytes = _slot_bytes(dev, SLOT_DIR)
    if leftover_bytes:
        checks.append(("槽位已无残留字节", False, f"残留 {leftover_bytes} B"))

    ok, detail = _check_rc_stock(dev, ZYGOTE_RC, REF_ZYGOTE_RC_SHA256)
    checks.append(("zygote rc 为 stock", ok, detail))
    ok, detail = _check_rc_stock(dev, ENVIRON_RC, REF_ENVIRON_RC_SHA256)
    checks.append(("init.environ.rc 未改 BOOTCLASSPATH", ok, detail))

    path = dev.pm_path(CAR_PKG)
    checks.append(("com.android.car 来自 /system/app", path.startswith("package:/system/app/CarService"), path or "(未安装)"))

    if ctx.probe is not None:
        log("== probe：验证 BootClassLoader")
        try:
            log("  " + dev.install_apk(ctx.probe))
        except ProvisionError:
            log("  probe APK 已安装或安装失败，继续尝试启动")
        dev.shell(f"logcat -c")
        # 不要用 `am start -W`：该 AVD 上 Activity 立即结束时 -W 会一直等 idle，实测挂起
        dev.shell(f"am start -n {PROBE_PKG}/{PROBE_ACTIVITY}", timeout=120)
        time.sleep(max(args.probe_wait, 1))
        logs = dev.shell(f"logcat -d -v brief -s {PROBE_TAG}", timeout=120)
        loaded = "CAR_LOADED_FROM=java.lang.BootClassLoader" in logs
        evidence = next((line.strip() for line in logs.splitlines() if "CAR_LOADED_FROM" in line), "")
        checks.append(("probe CAR_LOADED_FROM=BootClassLoader", loaded, evidence or "no probe log"))

    app_installed = dev.pm_path(APP_PKG)
    if ctx.app is not None or app_installed:
        log("== APP：验证 CarGateway" + ("" if ctx.app is not None else "（设备已安装）"))
        # PSAToolBoxService 未导出，shell 不能 am startservice；从 launcher 入口拉起（与手动点图标一致）
        dev.shell("logcat -c", timeout=60)
        launch = dev.shell(f"monkey -p {APP_PKG} -c android.intent.category.LAUNCHER 1 2>&1", timeout=120).strip()
        if launch:
            log("  " + launch.splitlines()[-1])
        if "No activities found" in launch or "aborted" in launch.lower():
            log(f"  monkey 失败，改用 am start {APP_ACTIVITY}")
            dev.shell(f"am start -n {APP_ACTIVITY}", timeout=120)
        time.sleep(5)  # 等首帧、首启协议弹窗与系统兼容性对话框
        ui_ready = _ui_prepare_app(dev)
        if not ui_ready:
            log(f"  （没能走通 APP 首启 UI / 打开「{APP_UI_TOOLS_TAB}」页，校验退化为日志兜底）")
        time.sleep(APP_WAIT_SECONDS)
        logs = dev.shell("logcat -d -v brief", timeout=180)
        hits = [line.strip() for line in logs.splitlines() if "CarGateway" in line]
        connected = any(re.search(r"connected", line, re.IGNORECASE) for line in hits)
        detail = hits[-1] if hits else "no CarGateway log"
        if not connected:
            # 已连接状态不会重复打印 "connected"；以 CarService 的 Client 绑定记录为准
            services = dev.shell("dumpsys activity services com.android.car", timeout=120)
            if re.search(r"Client AppBindRecord.*com\.bandwa\.openadb/", services):
                connected = True
                detail = "dumpsys Client AppBindRecord com.bandwa.openadb"
            elif any("Subscribed" in line for line in hits):
                connected = True
                detail = hits[-1]
        if connected:
            checks.append(("APP CarGateway connected", True, detail))
        elif not ui_ready:
            checks.append((
                "APP CarGateway connected",
                None,
                "UI 自动化未走通（协议弹窗/维护诊断页），无法判定",
            ))
        else:
            checks.append(("APP CarGateway connected", False, detail))

    failed = 0
    skipped: list[str] = []
    for name, ok, detail in checks:
        mark = "SKIP" if ok is None else ("OK  " if ok else "FAIL")
        log(f"  [{mark}] {name}" + (f"  ({detail})" if detail else ""))
        if ok is False:
            failed += 1
        elif ok is None:
            skipped.append(name)
    if failed:
        log(f"== 校验失败：{failed} 项")
        return False
    if skipped:
        log(f"== 校验通过，但有 {len(skipped)} 项 SKIP（未验证）：" + "、".join(skipped))
        log("   这些项需要 UI 交互（APP 首启协议/维护诊断页），请按 README 手动确认")
    else:
        log("== 校验通过")
    return True


# --------------------------------------------------------------------------- 状态

def write_state(
    ctx: Context,
    args: argparse.Namespace,
    info: dict,
    changes: list[str],
    verified: bool,
    merge_missing: bool = False,
) -> None:
    """写状态文件；merge_missing=True（--verify）时保留上次的 changes/app_apk/probe_apk，避免覆盖注入记录。"""
    previous: dict = {}
    if merge_missing and STATE_PATH.is_file():
        try:
            previous = json.loads(STATE_PATH.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            previous = {}
    if merge_missing and not changes:
        changes = previous.get("changes", [])
    app_info = (
        {"path": str(ctx.app), "size": ctx.app.stat().st_size, "sha256": ctx.app_sha}
        if ctx.app is not None else previous.get("app_apk")
    )
    probe_info = (
        {"path": str(ctx.probe), "size": ctx.probe.stat().st_size, "sha256": ctx.probe_sha}
        if ctx.probe is not None else previous.get("probe_apk")
    )
    BUILD_DIR.mkdir(parents=True, exist_ok=True)
    state = {
        "schema": 1,
        "kind": "psa-avd-provision",
        "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "serial": ctx.serial,
        "device": info,
        "api_jar": {"path": str(ctx.jar), "size": ctx.jar.stat().st_size, "sha256": ctx.jar_sha},
        "service_apk": {"path": str(ctx.service), "size": ctx.service.stat().st_size, "sha256": ctx.service_sha},
        "app_apk": app_info,
        "probe_apk": probe_info,
        "device_backup": DEVICE_BACKUP,
        "local_backup": str(LOCAL_BACKUP),
        "changes": changes,
        "verified": verified,
        "args": {"no_reboot": args.no_reboot, "force": args.force},
    }
    STATE_PATH.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    log(f"状态已写入 {STATE_PATH}")


# --------------------------------------------------------------------------- 命令

def plan_text(ctx: Context, args: argparse.Namespace) -> str:
    lines = [
        "== 计划（dry-run，不接触设备）",
        f"目标串口：{ctx.serial}",
        f"api jar  ：{ctx.jar}  {ctx.jar.stat().st_size} B  sha256={ctx.jar_sha}",
        f"服务 APK ：{ctx.service}  {ctx.service.stat().st_size} B  sha256={ctx.service_sha}",
        f"APP APK  ：{ctx.app if ctx.app is not None else '(未指定，跳过)'}",
        f"probe APK：{ctx.probe if ctx.probe is not None else '(未指定，跳过)'}",
        "步骤：",
        "  1. adb root；adb remount（写入探测 /system/framework）",
        f"  2. 首次备份 {BOOT_JAR} 与 {SLOT_DIR}/boot-android.test.base.* 到 {DEVICE_BACKUP}/，"
        f"并缓存到 {LOCAL_BACKUP}/（仅当 AVD 为干净原版；否则跳过且 --rollback 不可用）",
        f"  3. push 并替换 {BOOT_JAR}（0644 root:root，restorecon）",
        "  4. 删除 " + "、".join(SLOT_NAMES),
        f"  5. push 服务 APK 到 {CAR_SYSTEM_APK}；卸载 /data 里的旧安装",
    ]
    step = 6
    if ctx.app is not None:
        lines.append(f"  {step}. adb install -r -d " + str(ctx.app))
        step += 1
    if ctx.probe is not None:
        lines.append(f"  {step}. 安装 probe APK 并收集 CARPROBE 日志")
        step += 1
    lines += [
        f"  {step}. adb reboot，等待 sys.boot_completed=1",
        f"  {step + 1}. 校验 jar sha、槽位缺失、zygote rc stock、服务来自 /system/app、probe/APP 日志",
        "回滚：python tools/android-car-boot/avd/provision.py --rollback",
        "（失败时脚本会自动尝试回滚已做的系统修改）",
    ]
    return "\n".join(lines)


def cmd_provision(dev: Device, ctx: Context, args: argparse.Namespace) -> int:
    info = preflight(dev)
    ensure_root(dev)
    ensure_system_rw(dev)
    ensure_backup(dev, ctx)

    changes = hardware_changes(ctx)
    try:
        ctx.modified = True
        apply_boot_jar(dev, ctx)
        remove_slot_files(dev)
        install_system_service(dev, ctx)
        install_app(dev, ctx)
    except ProvisionError as exc:
        log("== 注入失败，尝试回滚")
        try:
            restore_originals(dev, ctx)
        except Exception as rollback_exc:  # noqa: BLE001 - 报告原始错误更重要
            log(f"  回滚失败：{rollback_exc}")
        raise ProvisionError(str(exc)) from exc

    if args.no_reboot:
        log("== 已跳过重启（--no-reboot）；重启后运行 --verify")
        write_state(ctx, args, info, changes, False)
        return 0

    reboot(dev, args.timeout)
    verified = run_verify(dev, ctx, args)
    write_state(ctx, args, info, changes, verified)
    if not verified:
        raise ProvisionError("注入完成但校验未通过（可重跑 --verify 或 --rollback）")
    return 0


def cmd_verify(dev: Device, ctx: Context, args: argparse.Namespace) -> int:
    info = preflight(dev)
    verified = run_verify(dev, ctx, args)
    write_state(ctx, args, info, [], verified, merge_missing=True)
    if not verified:
        raise ProvisionError("校验未通过")
    return 0


def cmd_rollback(dev: Device, ctx: Context, args: argparse.Namespace) -> int:
    log("== 回滚")
    if not dev.wait_online(timeout=15):
        raise ProvisionError(f"adb 设备不在线：{dev.serial}（先运行 run.ps1 启动 AVD）")
    restore_originals(dev, ctx)
    log("== 回滚后校验（原版 jar / 槽位恢复 / 服务移除）")
    checks: list[tuple[str, bool, str]] = []
    jar_sha = dev.sha256(BOOT_JAR)
    checks.append((
        "原版 jar 已恢复",
        jar_sha == REF_STOCK_JAR[0],
        (jar_sha[:12] + "…") if jar_sha else "?",
    ))
    slots = dev.shell(f"ls {SLOT_DIR} 2>/dev/null | grep -c 'boot-android.test.base' || true").strip()
    checks.append(("槽位文件已恢复", slots == str(len(SLOT_NAMES)), f"{slots or '0'} 个"))
    checks.append(("系统服务目录已删除", not dev.exists(CAR_SYSTEM_DIR), CAR_SYSTEM_DIR))
    ok = True
    for name, good, detail in checks:
        log(f"  [{'OK  ' if good else 'FAIL'}] {name}  ({detail})")
        ok = ok and good
    write_state(ctx, args, {}, ["回滚到原版 boot 产物"], ok)
    if not ok:
        raise ProvisionError("回滚后校验未通过")
    log("== 回滚完成")
    return 0


# --------------------------------------------------------------------------- 入口

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="官方 Android 9 AVD：最小 bootclasspath 注入 + com.android.car 系统服务 + 校验/回滚",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=(
            "示例：\n"
            "  python tools/android-car-boot/avd/provision.py --dry-run\n"
            "  python tools/android-car-boot/avd/provision.py --serial emulator-5554 \\\n"
            "      --apk app/build/outputs/apk/debug/app-debug.apk \\\n"
            "      --probe-apk verify/bootinject/out/probe-app.apk\n"
            "  python tools/android-car-boot/avd/provision.py --verify --serial emulator-5554\n"
            "  python tools/android-car-boot/avd/provision.py --rollback --serial emulator-5554\n"
        ),
    )
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--verify", action="store_true", help="校验（设备已注入并开机；会拉起 APP 做 UI 交互，不改系统镜像）")
    mode.add_argument("--rollback", action="store_true", help="还原原版 jar/槽位、删除系统服务并重启")
    mode.add_argument("--dry-run", action="store_true", help="只打印计划，不接触设备")
    parser.add_argument("--serial", default=DEFAULT_SERIAL, help=f"adb 串口（默认 {DEFAULT_SERIAL}）")
    parser.add_argument("--adb", default=find_adb(), help="adb 可执行文件路径")
    parser.add_argument("--api-jar", default=str(JAR_DEFAULT), help="android.car bootclasspath jar")
    parser.add_argument("--service-apk", default=str(SERVICE_APK_DEFAULT), help="com.android.car 服务 APK")
    parser.add_argument("--apk", default=None, help="APP APK（需 .\\gradlew.bat :app:assembleDebug -PavdAbi 构建）")
    parser.add_argument("--probe-apk", default=None, help="probe APK（默认自动使用 verify/bootinject/out/probe-app.apk）")
    parser.add_argument("--probe-wait", type=int, default=5, help="启动 probe 后等待日志的秒数")
    parser.add_argument("--no-reboot", action="store_true", help="注入后不重启（之后手动重启再 --verify）")
    parser.add_argument("--force", action="store_true", help="兼容保留：注入步骤本就无条件执行，此参数当前不改变行为")
    parser.add_argument("--timeout", type=int, default=600, help="重启后等待开机的秒数")
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    ctx = Context()
    ctx.serial = args.serial

    try:
        check_local_artifacts(ctx, args)

        if args.dry_run:
            log(plan_text(ctx, args))
            return 0

        dev = Device(args.adb, args.serial)
        if args.verify:
            return cmd_verify(dev, ctx, args)
        if args.rollback:
            return cmd_rollback(dev, ctx, args)
        log("== 目标串口：" + args.serial)
        return cmd_provision(dev, ctx, args)
    except ProvisionError as exc:
        return die(str(exc))
    except KeyboardInterrupt:
        log("已中断")
        return 2


if __name__ == "__main__":
    sys.exit(main())
