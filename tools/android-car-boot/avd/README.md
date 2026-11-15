# 官方 Android 9 (API 28) AVD：最小 bootclasspath 注入 + `com.android.car` 系统服务

用 **Google 官方 userdebug AVD** 复现一套可写、可复现、可分发快照的 Automotive Android 9 环境：

- 官方 `system-images;android-28;default;x86_64`（可 `adb root/remount`）；
- 用**最小机制**让普通应用从 **BootClassLoader** 解析 `android.car.*`：
  1. `/system/framework/android.test.base.jar` 替换为本项目的 `android.car.jar`；
  2. 删除 `/system/framework/x86_64/boot-android.test.base.{art,art.rel,oat,vdex}` 四个 boot image 槽位文件；
  3. 不改 ramdisk、不改 zygote rc、不改 `BOOTCLASSPATH`、不用 `-Ximage`。
  槽位缺失时 ART 从对应 bootclasspath jar 解 dex，由 BootClassLoader 加载。
  启动耗时：新建后首启约 44 s，日常冷启动约 22 s，注入后 `adb reboot` 约 6–15 s。
- 把本项目的 `com.android.car` 服务 APK 系统化到 `/system/app/CarService/CarService.apk`，开机即可用；
- 可选安装 APP（`com.bandwa.openadb`，`-PavdAbi` 构建）并验证 CarGateway 连通；
- 导出整个 AVD 目录 + `manifest.json` + `RESTORE.md` 为 zip 快照，可分发、可恢复。

> ⚠️ 仅用于测试模拟器。会修改 AVD 的 system 覆盖层，**绝不能用于真车**。
> 脚本只会停止命令行里带 `-avd <name>` 的本 AVD 进程，不会碰其他正在运行的 emulator。

## 1. 基线

| 项 | 值 |
| --- | --- |
| SDK | `<sdk>`（脚本默认值，用 `-SdkRoot` 覆盖；Python 脚本另读 `ANDROID_SDK_ROOT` / `ANDROID_HOME`） |
| emulator | `37.2.13` |
| system image | `system-images;android-28;default;x86_64` revision **4**（official userdebug，test-keys，system-as-root） |
| platform-tools | `37.0.1` |
| 加速 | AEHD `2.2.0`（`extras;google;Android_Emulator_Hypervisor_Driver`） |
| JDK | `<jdk-17>`（`setup.ps1` 默认值，用 `-JavaHome` 覆盖；sdkmanager/avdmanager 需要） |
| AVD 设备档 | `automotive_1024p_landscape`：1920×720、density 160、4G RAM、8G data、swiftshader、无音视频、无相机、键盘、无边框、4 核 |
| bootclasspath jar | `tools/android-car-boot/api/build/android.car.jar` 12,341 B，sha256 `cb71ec77…6bf1d42b` |
| 服务 APK | `tools/android-car-boot/service/build/outputs/apk/debug/service-debug.apk` 35,703 B，sha256 `aa17a87a…f47d8fdd` |
| probe APK（可选，自备） | `verify/bootinject/out/probe-app.apk`，包 `com.bandwa.bootinject.probe`，logcat tag `CARPROBE`；不存在时跳过该项校验 |
| APP APK（可选） | `app/build/outputs/apk/debug/app-debug.apk`，包 `com.bandwa.openadb`，需 `-PavdAbi` |

注入成功的判据（`provision.py --verify` 会全部检查）：

- `getprop sys.boot_completed` == `1`；
- 设备上 `/system/framework/android.test.base.jar` 的 sha256 等于本地 `android.car.jar`；
- `/system/framework/x86_64/` 下 4 个 `boot-android.test.base.*` 槽位文件都不存在；
- `/init.zygote64_32.rc` 与 `/init.environ.rc` 保持原版（sha256 `93c8ecbd…` / `2d8a8381…`）；
- `pm path com.android.car` 指向 `/system/app/CarService/CarService.apk`；
- probe 应用 logcat（tag `CARPROBE`）输出 `CAR_LOADED_FROM=java.lang.BootClassLoader`（仅当提供 probe APK）；
- APP（仅当提供 `--apk`，或设备已安装该包）：`provision.py --verify` 会自动拉起 APP → 点掉系统
  「兼容性」对话框 → 同意使用协议（首启按钮带倒计时）→ 打开「维护诊断」页，再按 logcat `CarGateway`
  或 `dumpsys … Client AppBindRecord` 判定连通。CarGateway 只在该页的 ViewModel 里初始化，停在主界面
  不算数；shell 不能直接 `am startservice`（`PSAToolBoxService` 未导出）；UI 自动化走不通时该项记 `SKIP`
  （最后一行会列出未验证项，不会谎报全部通过）。

## 2. 目录

```
tools/android-car-boot/avd/
├── setup.ps1     # 检查/安装 SDK 包，创建并配置 AVD（幂等，不启动、不注入）
├── run.ps1       # WMI 启动/停止/查询 AVD，等待 sys.boot_completed == 1，adb root
├── provision.py  # 注入 bootclasspath 槽位、系统化服务、装 APP/probe、重启、校验、失败回滚
├── snapshot.ps1  # 停 AVD，导出目录 + manifest + RESTORE.md，打包成 zip
└── README.md
```

生成物（已被 `.gitignore` 忽略）：`tools/android-car-boot/build/avd-provision-state.json`、
`tools/android-car-boot/build/avd-logs/*.log`、`tools/android-car-boot/dist/*.zip`。

## 3. 前置

- Windows 10/11 x64，BIOS 开启 VT-x/AMD-V；AEHD 2.2 已安装并启动（`Get-Service aehd`）；
  只有 WHPX 也能跑，脚本只告警不阻断。
- PowerShell 5.1 或 7 均可（脚本按 5.1 兼容语法写）。
- python 3.10+（3.14 已验证），仅用标准库。
- 先构建产物（仓库根目录，Git Bash 或 PowerShell 均可）：

  ```powershell
  # bootclasspath jar（需要 JDK：javac/d8/jar）
  bash tools/android-car-boot/api/build.sh

  # com.android.car 服务 APK
  .\gradlew.bat :tools:android-car-boot:service:assembleDebug --offline

  # APP APK（打包 x86_64，必须带 -PavdAbi）
  .\gradlew.bat :app:assembleDebug -PavdAbi --offline
  ```

- 磁盘：AVD 约 9 GB（system 覆盖层 + 8 GB userdata）；导出快照再加约 9 GB，打包 zip 再加一份。

### 脚本编码说明

- `.ps1` 全部为 **纯 ASCII**：Windows PowerShell 5.1 按系统 ANSI（cp936）读取无 BOM 的 UTF-8，
  中文注释/字符串会破坏语法解析，所以 PowerShell 脚本只用英文注释与输出。
- `.py` 与本文档为 UTF-8（无 BOM）中文。

## 4. 快速开始

所有命令在仓库根目录执行；`-ExecutionPolicy Bypass` 仅为避免本机策略拦截本地脚本。

### 4.1 环境准备（setup.ps1）

```powershell
# 创建/校正默认 AVD psa_api28（SDK 组件缺失会自动装；首次会接受 license）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\setup.ps1

# 只读检查（不装包、不建 AVD）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\setup.ps1 -VerifyOnly

# 预览动作；-Force 重建 AVD；-SkipSdkInstall 禁止自动装包；-AvdName 换名字
powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\setup.ps1 -DryRun
```

参数：`-AvdName`（默认 `psa_api28`）、`-SdkRoot`、`-JavaHome`、`-Force`、`-VerifyOnly`、
`-SkipSdkInstall`、`-DryRun`。退出码 `0` 成功 / `1` 失败。

### 4.2 启动 / 停止（run.ps1）

```powershell
# 启动（默认 psa_api28 @ 5554，无窗口，writable-system，no-snapshot），等 boot 后自动 adb root
powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\run.ps1

# 换端口 / 显示窗口 / 允许快照 / 不带 writable-system
powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\run.ps1 -Port 5556 -Window
powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\run.ps1 -NoSnapshot $false
powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\run.ps1 -WritableSystem $false

# 查询 / 停止 / 重启 / 预览命令（不启动）
powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\run.ps1 -Status
powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\run.ps1 -Stop
powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\run.ps1 -Restart
powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\run.ps1 -DryRun
```

参数：`-AvdName`、`-Port`（偶数，5554–5584）、`-SdkRoot`、`-NoSnapshot`、
`-WritableSystem`、`-Window`、`-Stop`、`-Restart`、`-Status`、`-DryRun`、`-TimeoutSec`（默认 300）。

说明：通过 WMI（`Win32_Process.Create`）启动，避免父 shell 退出时连带杀掉模拟器；
日志在 `tools/android-car-boot/build/avd-logs/emulator-<port>.log`。

### 4.3 注入 + 校验 + 回滚（provision.py）

```powershell
# 预览计划（不接触设备）
python tools\android-car-boot\avd\provision.py --dry-run

# 完整注入：root/remount → 备份原版（首次）→ 换 jar → 删槽位 → 系统化服务 → 重启 → 校验
python tools\android-car-boot\avd\provision.py

# 可选：顺便安装 APP（-PavdAbi 构建）并校验 CarGateway；安装/指定 probe
python tools\android-car-boot\avd\provision.py --apk app\build\outputs\apk\debug\app-debug.apk
python tools\android-car-boot\avd\provision.py --probe-apk verify\bootinject\out\probe-app.apk

# 校验（已开机的已注入设备；不改系统镜像，但会安装/拉起 probe 与 APP 做 UI 交互并写状态文件）
python tools\android-car-boot\avd\provision.py --verify

# 回滚成原版：还原 test.base.jar 与 4 个槽位、删除系统服务、重启（需要可信备份）
python tools\android-car-boot\avd\provision.py --rollback

# 其他：--serial emulator-5556、--no-reboot、--timeout 600、--api-jar/--service-apk 覆盖
```

流程与幂等性：

- 预检：API 28、`ro.build.type=userdebug`、`ro.debuggable=1`、`ro.build.system_root_image=true`；
  设备 15s 内不在线直接失败（先 `run.ps1` 启动）。
- `adb root` + `adb remount`（失败再 `mount -o rw,remount /system`），用真实写入探测可写；
- **仅首次**把原版 `android.test.base.jar` 与 4 个槽位备份到设备 `/data/avd-orig/`（含 SHA256SUMS），
  并在本地 `tools/android-car-boot/build/avd-orig/` 缓存一份；备份前校验 AVD 是否为干净原版
  （jar 183 B / `1c16f597…` + 4 个槽位齐全），不满足则**跳过备份并禁用 `--rollback`**
  （已被改过的 AVD 需先 `setup.ps1 -Force` 重建）；「可信备份」的判据是原版 jar + 4 个槽位齐全、
  槽位总字节数与 `SHA256SUMS` 内容一致（缺标记但内容完好的备份补写标记而非丢弃）；设备备份与本地
  缓存各自校验：设备上的旧备份会被标 `.untrusted` 并在设备干净时重建，本地缓存只在不可信时重建
  （设备备份丢失不会连累仍然可信的本地缓存）；`--rollback` 只从可信备份恢复并再次校验原版指纹；
- push `android.car.jar` 到 `/data/local/tmp` → `cp` 到 `/system/framework/android.test.base.jar`
  （`root:root 0644`、`restorecon`），设备端 sha256 与本地一致才继续；
- 删除 `/system/framework/x86_64/boot-android.test.base.*` 并确认 0 个残留；
- 服务 APK 放到 `/system/app/CarService/CarService.apk`；若 `com.android.car` 当前在 `/data/app` 先卸载；
- 可选 `adb install -r -d` 安装 APP；
- `adb reboot` 后等 `sys.boot_completed=1`（默认最长 600s）；校验前自动 `adb root`
  （重启后 adbd 掉回 shell，否则 `/init*.rc` 读不到）；
- 校验（见第 1 节判据）；不通过抛错并退出码 `2`；
- 注入阶段任意异常自动回滚：还原 jar+槽位、删 CarService、卸载 `com.android.car`、重启。
- 状态写入 `tools/android-car-boot/build/avd-provision-state.json`（时间、serial、产物 sha256、改动、校验结果）。

### 4.4 端到端验证（手动）

```powershell
adb -s emulator-5554 shell getprop sys.boot_completed          # 1
adb -s emulator-5554 shell pm path com.android.car             # package:/system/app/CarService/CarService.apk
adb -s emulator-5554 logcat -d -v brief -s CARPROBE            # CAR_LOADED_FROM=java.lang.BootClassLoader
adb -s emulator-5554 shell "ps -A | grep zygote"               # 然后读 /proc/<pid>/environ，BOOTCLASSPATH 里 jar 已换成…（可选）

# 服务控制面（广播）
adb -s emulator-5554 shell am broadcast -n com.android.car/.ControlReceiver `
  -a com.android.car.CONTROL --es cmd log

# APP 连通：在 APP 里进「维护诊断」页（CarGateway 只在该页的 ViewModel 里初始化）
adb -s emulator-5554 shell "logcat -d -v brief | grep -i CarGateway"
adb -s emulator-5554 shell "dumpsys activity services com.android.car | grep -i AppBindRecord"
```

## 5. 快照导出与恢复（snapshot.ps1）

```powershell
# 默认输出 tools\android-car-boot\dist\psa_api28-<yyyyMMdd-HHmmss>.zip
powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\snapshot.ps1

# 指定路径 / 覆盖已有 / 预览 / 不自动停 AVD / 保留 staging
powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\snapshot.ps1 -ZipPath <path>\psa.zip
powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\snapshot.ps1 -Force
powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\snapshot.ps1 -DryRun
powershell -NoProfile -ExecutionPolicy Bypass -File tools\android-car-boot\avd\snapshot.ps1 -NoStop -KeepStage
```

步骤：停止该 AVD（`adb emu kill` + 进程/adb 释放确认）→ 复制 `<name>.ini` 与 `<name>.avd/` 到 staging
（排除 `*.lock`、`hardware-qemu.ini`、`*.log`、`snapshots/`）→ 生成 `manifest.json`
（emulator 版本、system image revision、AVD 配置子集、jar/服务/APP/probe 的 size+sha256、已知改动清单）与
`RESTORE.md` → `tar.exe -a` 打包 → 报告 zip 体积与 sha256。

恢复：

1. 用 `setup.ps1` 锁定同版本 SDK 组件（emulator 37.2.13、image rev 4）；
2. 关闭占用该 AVD 的模拟器；
3. 解压 zip 到 `%USERPROFILE%\.android\avd`（保持 `<name>.ini` + `<name>.avd/` 结构）；
4. `run.ps1 -AvdName <name>` 启动；
5. `provision.py --verify` 校验注入仍在；
6. 需要原版系统时 `provision.py --rollback`（只用可信备份恢复：优先快照里的 `/data/avd-orig`，
   不可信时退回本地 `build/avd-orig` 缓存；两者都不可用则拒绝执行，用 `setup.ps1 -Force` 重建）。

参数：`-AvdName`、`-ZipPath`、`-SdkRoot`、`-NoStop`、`-KeepStage`、`-Force`、`-DryRun`、
`-TimeoutSec`、`-ChecksumMaxMB`（清单里超过该大小的文件不记 sha256，默认 64）。

## 6. 已知限制

- **Windows/AEHD**：AEHD 2.2 需要 VT-x；没有加速时启动极慢甚至超时。WHPX 可替代但未专门验证。
- **system-as-root**：`/` 即 system；修改只在 AVD 的 `system.img.qcow2` 覆盖层里，**不会**影响 SDK 共享
  system image；换 AVD 或 `-wipe-data` 后需重新 `provision.py`。
- **AVD 不含车机 vendor 应用**：`android.hardware.automotive.*` 等真实 HAL/服务缺失，`com.android.car`
  由本项目的模拟服务端顶替（boot 协议）；不是完整的 Automotive 镜像。
- **快照体积大**：`userdata-qemu.img` 预分配 8 GB，AVD 目录约 9 GB；导出/zip 需要约 2× 空间与
  数分钟；PowerShell 5.1 的 `Compress-Archive` 不支持 >2 GB，脚本用系统 `tar.exe -a`。
- **服务需重启生效**：`pm path` 变成 `/system/app/...` 需要重启（脚本已处理）。
- **APP 首启**：「使用协议」倒计时与系统「兼容性」对话框由 `provision.py` 自动处理（判据见 1 节）。
- **注入前必须是干净原版**：非干净 AVD 跳过备份且 `--rollback` 拒绝执行；本地 `build/avd-orig/`
  有可信缓存时仍可用它回滚。
- **脚本编码**：`.ps1` 纯 ASCII（见 3 节）；修改时不要加中文，否则 PS 5.1 解析失败。
- **并发**：同一 AVD 不要被两个 emulator 实例同时使用；快照默认会先停 AVD（`-NoStop` 可能不一致）。

## 7. 故障排查

| 现象 | 处理 |
| --- | --- |
| `sdkmanager` 报 `JAVA_HOME` 未设置 | 用 `-JavaHome` 指向 JDK 17，或手动设 `JAVA_HOME` |
| `run.ps1` 超时未 boot | 看 `build\avd-logs\emulator-<port>.log`；确认 AEHD（`emulator-check accel`）、内存、`-gpu` |
| `adb remount` 失败 | 确认 `adb root` 成功、`ro.build.system_root_image=true`；脚本会退回 `mount -o rw,remount /system` |
| probe 日志没有 `CAR_LOADED_FROM` | 确认 4 个槽位已删、jar sha256 一致、重启过；`provision.py --verify` 会指出哪一步失败 |
| `pm path com.android.car` 仍是 `/data/app` | 先 `adb uninstall com.android.car` 再重启；或重跑 `provision.py`（注入步骤本就无条件执行） |
| `--verify` 报设备不在线 | 先用 `run.ps1` 启动，注意 `--serial` 端口一致 |
| 快照 zip 创建失败 | 确认 `C:\Windows\System32\tar.exe` 存在、目标盘空间 ≥ 2×AVD，必要时 `-KeepStage` 排查 |
