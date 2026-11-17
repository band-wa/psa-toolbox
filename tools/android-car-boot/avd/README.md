# 官方 Android 9 (API 28) AVD：最小 bootclasspath 注入 + `com.android.car` 系统服务

用 **Google 官方 userdebug AVD** 复现一套可写、可复现、可分发快照的 Automotive Android 9 环境：

- 官方 `system-images;android-28;default;x86_64`（Windows，可 `adb root/remount`）；macOS（Apple Silicon）
  对应 `system-images;android-28;default;arm64-v8a`（rev 2，userdebug/test-keys），脚本为 `.sh` 版；
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
>
> 📌 **macOS（Apple Silicon）用户请直接看 [第 8 节](#8-macosapple-silicon)**：第 1–7 节以 Windows
> （PowerShell）流程为主（1.1 为 macOS 基线），第 8 节是 macOS 实现（`setup.sh` / `run.sh` /
> `snapshot.sh`，共用同一份 `provision.py`）。

## 1. 基线

| 项 | 值 |
| --- | --- |
| SDK | `<sdk>`（脚本默认值，用 `-SdkRoot` 覆盖；Python 脚本另读 `ANDROID_SDK_ROOT` / `ANDROID_HOME`） |
| emulator | `37.2.13` |
| system image | `system-images;android-28;default;x86_64` revision **4**（official userdebug，test-keys，system-as-root） |
| platform-tools | `37.0.1` |
| 加速 | AEHD `2.2.0`（`extras;google;Android_Emulator_Hypervisor_Driver`） |
| JDK | `<jdk-17>`（`setup.ps1` 默认值，用 `-JavaHome` 覆盖；sdkmanager/avdmanager 需要） |
| AVD 设备档 | `automotive_1024p_landscape`：1920×720、density 213、4G RAM、8G data、swiftshader、无音视频、无相机、键盘、无边框、4 核 |
| bootclasspath jar | `tools/android-car-boot/api/build/android.car.jar` 12,341 B，sha256 `cb71ec77…6bf1d42b` |
| 服务 APK | `tools/android-car-boot/service/build/outputs/apk/debug/service-debug.apk` 35,703 B，sha256 `aa17a87a…f47d8fdd` |
| probe APK（可选，自备） | `verify/bootinject/out/probe-app.apk`，包 `com.bandwa.bootinject.probe`，logcat tag `CARPROBE`；不存在时跳过该项校验 |
| APP APK（可选） | `app/build/outputs/apk/debug/app-debug.apk`，包 `com.bandwa.openadb`，需 `-PavdAbi` |

### 1.1 macOS（Apple Silicon）基线

| 项 | 值 |
| --- | --- |
| SDK | `~/Library/Android/sdk`（脚本默认值，用 `--sdk-root` 覆盖；`ANDROID_SDK_ROOT` / `ANDROID_HOME` 优先） |
| emulator | SDK 组件 `emulator`（快照与版本绑定，见 8.4） |
| system image | `system-images;android-28;default;arm64-v8a` revision **2**（official userdebug，test-keys，system-as-root） |
| platform-tools | SDK 组件 `platform-tools` |
| 加速 | 系统 Hypervisor.framework（HVF），无需安装；`emulator-check accel` 输出 `Hypervisor.Framework` |
| JDK | JDK 17（`--java-home` 可覆盖；sdkmanager 需要） |
| AVD 设备档 | `setup.sh` 直写 config.ini：1920×720、density 213、4G RAM、8G data、swiftshader_indirect、无音视频、无相机、键盘、无边框、4 核、横屏 |
| bootclasspath jar | `tools/android-car-boot/api/build/android.car.jar` 12,341 B，sha256 `cb71ec77…6bf1d42b`（与 Windows 相同） |
| 服务 APK | `tools/android-car-boot/service/build/outputs/apk/debug/service-debug.apk` 24,903 B，sha256 `30af2683…c29d45e` |
| APP APK（可选） | `app/build/outputs/apk/debug/app-debug.apk` 14,586,486 B，sha256 `886819b7…ff745eb4`，包 `com.bandwa.openadb` 5.3.4 |
| probe APK（可选，自备） | 同 Windows：`verify/bootinject/out/probe-app.apk`；不存在时跳过该项校验 |

> jar 与设备 ABI 无关，macOS 用 JDK 17 + build-tools 34.0.0 构建时与 Windows 产物相同（12,341 B /
> `cb71ec77…`）；服务 APK 因 Gradle/AGP 环境不同而大小/指纹不同。脚本始终信任本地实际 sha256，
> 只在日志里提示与基线指纹不同，不阻断构建与注入。

注入成功的判据（`provision.py --verify` 会全部检查）：

- `getprop sys.boot_completed` == `1`；
- 设备上 `/system/framework/android.test.base.jar` 的 sha256 等于本地 `android.car.jar`；
- `/system/framework/x86_64/` 下 4 个 `boot-android.test.base.*` 槽位文件都不存在（macOS arm64 镜像为
  `/system/framework/arm64/`，见 8.3）；
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
- python 3.10+，仅用标准库。
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

> macOS（Apple Silicon）的对应流程与故障排查见 [第 8 节](#8-macosapple-silicon)。

## 8. macOS（Apple Silicon）

第 1–7 节以 Windows 流程为主；本节是同一套机制的 macOS 实现。三个 `.sh` 按 `uname`、`provision.py`
按设备 ABI 自适应；`.sh` 同时接受 `--kebab-case` 与 `-PascalCase`，`provision.py` 只用 `--long` 形式。

### 8.1 脚本与参数

| Windows | macOS | 说明 |
| --- | --- | --- |
| `setup.ps1` | `setup.sh` | 创建/校验 AVD（不经 `avdmanager`，见 8.6） |
| `run.ps1` | `run.sh` | 启动/停止/查询/重启 AVD |
| `snapshot.ps1` | `snapshot.sh` | 导出可恢复的 AVD zip |
| `provision.py` | 同一份 `provision.py` | 按设备 ABI 选择槽位目录，无需两份 |

调用方式：脚本已提交可执行位（100755），可直接 `./tools/android-car-boot/avd/<script>.sh`，也可统一写
`bash tools/android-car-boot/avd/<script>.sh`。所有脚本接受 kebab-case（`--avd-name`）与
PowerShell 风格（`-AvdName`）两种拼写；布尔型参数还接受 `1/true/yes/on` 与 `0/false/no/off`
（如 `--window`、`--no-snapshot`、`--writable-system`）。退出码：三个 shell 脚本 `0` 成功 / `1` 失败；
`provision.py` `0` 成功 / `2` 失败（注入阶段失败会自动回滚）。

选项（`bash ... --help` 可查看）：

| 脚本 | 选项 |
| --- | --- |
| `setup.sh` | `--avd-name`（默认 `psa_api28`）、`--sdk-root`、`--java-home`、`--force`、`--verify-only`、`--skip-sdk-install`、`--dry-run`、`-h` |
| `run.sh` | `--avd-name`、`--port`（偶数 `5554`–`5584`，默认 `5554`）、`--sdk-root`、`--window`、`--no-snapshot`、`--writable-system`、`--timeout-sec`（300）、`--status`、`--stop`、`--restart`、`--dry-run`、`-h` |
| `snapshot.sh` | `--avd-name`、`--zip-path`、`--sdk-root`、`--no-stop`、`--keep-stage`、`--force`、`--dry-run`、`--timeout-sec`（120）、`--checksum-max-mb`（64）、`-h` |
| `provision.py` | 互斥 `--verify` / `--rollback` / `--dry-run` / `--print-device-info`，另有 `--serial`、`--adb`、`--abi`、`--api-jar`、`--service-apk`、`--apk`、`--probe-apk`、`--probe-wait`、`--no-reboot`、`--timeout`（600）、`--force` |

`macos-common.sh` 是被 source 的公共库（日志、SDK/JDK/adb/emulator 探测、AVD 启停、空间检查），不直接执行。

### 8.2 快速开始（可复制粘贴）

前置：Apple Silicon Mac、Xcode Command Line Tools（提供 `python3`）、JDK 17、SDK 默认
`~/Library/Android/sdk`。

```bash
# 0. 一次性安装 SDK 组件（已有可跳过）
sdkmanager --install "platform-tools" "emulator" "build-tools;34.0.0" \
  "platforms;android-34" "system-images;android-28;default;arm64-v8a"

# 1. 构建：api jar → 服务 APK →（可选）APP APK
bash tools/android-car-boot/api/build.sh
bash ./gradlew :tools:android-car-boot:service:assembleDebug
bash ./gradlew :app:assembleDebug -PavdAbi

# 2. 创建（幂等）并启动 AVD：psa_api28 @ emulator-5554，headless/writable-system/no-snapshot
bash tools/android-car-boot/avd/setup.sh
bash tools/android-car-boot/avd/run.sh                 # 冷启动后等 sys.boot_completed=1

# 3. 注入 + 校验（注入后自动 reboot）；需要 APP CarGateway 校验时给第 1 条加
#    --apk app/build/outputs/apk/debug/app-debug.apk（须先构建 APP）
python3 tools/android-car-boot/avd/provision.py
python3 tools/android-car-boot/avd/provision.py --verify

# 4. 状态 / 停止 / 快照；回滚成原版用 --rollback
bash tools/android-car-boot/avd/run.sh --status
bash tools/android-car-boot/avd/run.sh --stop
bash tools/android-car-boot/avd/snapshot.sh
```

只读诊断与预览：`python3 tools/android-car-boot/avd/provision.py --print-device-info`、
`setup.sh --verify-only`、`run.sh --dry-run`、`snapshot.sh --dry-run`、
`provision.py --dry-run --abi arm64-v8a`。可选 `--probe-apk <path>` 传自备 probe APK（不存在时
默认跳过该项校验）；`--apk` 用于 APP CarGateway 校验。`setup.sh` 从不启动模拟器，同名 AVD 正在运行时它拒绝
创建/重建（先 `run.sh --stop`；只有 `--force` 才会删掉旧 AVD 重新生成）；`run.sh` 只停止命令行里带
`-avd <name>` 的 emulator 进程，不会碰其他实例。

### 8.3 注入、校验与回滚（ABI 自适应）

`provision.py` 先读 `ro.product.cpu.abi` / `ro.product.cpu.abilist`，再决定槽位目录：

| 设备 ABI | 槽位目录 | 备注 |
| --- | --- | --- |
| `arm64-v8a` / `arm64` | `/system/framework/arm64/` | macOS Apple Silicon 镜像；原版 4 槽位共 87,697 B |
| `x86_64` | `/system/framework/x86_64/` | Windows 流程；原版 4 槽位共 87,649 B |
| `armeabi-v7a` / `armeabi` | `/system/framework/arm/` | 预留 |
| `x86` | `/system/framework/x86/` | 预留 |

arm64 镜像的 `boot-android.test.base.vdex` 是**符号链接**（指向 `/system/framework/` 下的同名目标），
备份/还原会记录并恢复链接本身，而不是把它展开成普通文件；设备端备份在 `/data/avd-orig`
（含 `SHA256SUMS` 与 `SYMLINKS`），本地缓存在 `tools/android-car-boot/build/avd-orig/`
（jar 与两个清单文件在根，槽位文件在 `<slot>/`；arm64 镜像即 `arm64/`）。
注入阶段（push/删槽位/装服务/装 APP）失败自动回滚并退出 `2`；重启后校验不通过则保留现状，提示重跑
`--verify` 或 `--rollback`；成功后写
`tools/android-car-boot/build/avd-provision-state.json`（schema 1，含 ABI、槽位、设备指纹、各产物
size+sha256、`verified: true`）。`--print-device-info` 只读地打印 ABI/指纹、槽位文件状态、stock 判定、
两份备份是否可信、`pm path com.android.car` 与 zygote rc，不改设备。

`--verify` 的判据：

| # | 判据 | arm64 含义 |
| --- | --- | --- |
| 1 | `sys.boot_completed=1` | 注入后系统完成启动 |
| 2 | `/system/framework/android.test.base.jar` sha256 == 本地 `api/build/android.car.jar` | 替换并 push 成功；路径与 ABI 无关 |
| 3 | `/system/framework/arm64/` 下 4 个 `boot-android.test.base.*` 槽位文件全部消失 | 对应 Windows 的 `x86_64/` 目录；arm64 的 `.vdex` 链接一并清除 |
| 4 | `/init.zygote64_32.rc` sha256 = `93c8ecbd…` | 原版系统文件未被改动（两种 ABI 同指纹） |
| 5 | `/init.environ.rc` sha256 = `2d8a8381…` | 同上 |
| 6 | `pm path com.android.car` == `/system/app/CarService/CarService.apk` | 模拟服务成功移到 system 分区，重启后生效 |
| 7 | （可选，传了 `--apk` 或设备已安装该包）CarGateway 连通：logcat `Subscribed 0x21400978 rate=1.0` / logcat `connected` / `dumpsys` 的 `Client AppBindRecord com.bandwa.openadb` 任一命中 | 首次启动自动点「同意」使用协议并打开「维护诊断」页 |

回滚：`python3 tools/android-car-boot/avd/provision.py --rollback`。只使用可信备份（优先设备端
`/data/avd-orig`，退而用本地 `build/avd-orig/<abi>/`；两者都不可信则拒绝并提示重建 AVD）。两条路径
回滚后设备回到 stock（jar 与各槽位恢复原样、`CarService` 移除），再次注入即恢复
PROVISIONED 状态。

### 8.4 快照（snapshot.sh）

```bash
bash tools/android-car-boot/avd/snapshot.sh                        # 默认 dist/<name>-<yyyyMMdd-HHmmss>.zip
bash tools/android-car-boot/avd/snapshot.sh --dry-run              # 只报告 AVD 大小/所需空间/产物 sha256
bash tools/android-car-boot/avd/snapshot.sh --no-stop --keep-stage  # 不自动停、保留 staging 便于排查
```

与 Windows 版相同的排除规则（`*.lock`、`hardware-qemu.ini`、`*.log`、`snapshots/`）与顶层布局
（`<name>.ini` + `<name>.avd/` + `manifest.json` + `RESTORE.md`），差异是打包用 `python3` 的
`zipfile`（ZIP64，替代 `tar.exe -a`），复制 stage 时优先 `cp -c`（APFS clonefile）。要求目标空间
≥ 2×AVD 大小 + 200 MB；AVD 仍在运行时默认先停（`--no-stop` 只 warn，之后仍会失败并提示
`run.sh --stop`）。`manifest.json`（schema 1，kind `psa-avd-snapshot`）含 emulator 版本、system image
package/revision/sdk、AVD 配置子集、文件清单（≤`--checksum-max-mb` 的文件带 sha256）、
api jar/服务 APK/APP APK/probe APK 的 size+sha256 与已知改动；`host.psVersion` 为
`n/a (macOS bash port)`。导出整份 AVD 的 zip（稀疏盘经压缩后显著减小），可用
`python3 -m zipfile -t <zip>` 自检。**快照与 ABI 绑定**，arm64 快照不能用于 Windows x86_64 流程。

恢复：解压 zip 到 `~/.android/avd`（保持 `<name>.ini` + `<name>.avd/`，`.ini` 里的 `path=` 是绝对
路径，换机器/换用户后需改成解压后的实际路径）→ `bash run.sh --avd-name <name>` → `provision.py --verify`
→ 需要原版时 `provision.py --rollback`。

### 8.5 与 Windows 的差异和环境变量

| 项 | Windows | macOS（Apple Silicon） |
| --- | --- | --- |
| 镜像 / ABI | `x86_64` rev 4 | `arm64-v8a` rev 2（与真车 ABI 一致） |
| 加速 | AEHD 2.2.0（WHPX 可替代） | 系统 Hypervisor.framework（HVF），无需安装 |
| 槽位目录 | `/system/framework/x86_64/` | `/system/framework/arm64/` |
| SDK 默认 | `D:\Tools\Android\sdk` | `~/Library/Android/sdk` |
| d8 | `d8.bat`（build-tools 34.0.0） | POSIX `d8`（build-tools 34.0.0） |
| 文件大小 | GNU `stat -c %s` | BSD `stat -f %z` |
| 快照打包 | `tar.exe -a` + PS 5.1 | `python3` `zipfile` |
| AVD 目录 | `%USERPROFILE%\.android\avd` | `~/.android/avd` |

`.sh` 脚本认 `ANDROID_SDK_ROOT` / `ANDROID_HOME`、`ANDROID_AVD_HOME`、`ANDROID_EMULATOR_HOME`、
`ANDROID_USER_HOME`、`JAVA_HOME`（供 `setup.sh` 调 sdkmanager），各自另有 `--sdk-root`（`setup.sh` 还有
`--java-home`），适合 CI 或 `$HOME` 只读环境；Gradle 构建另认 `GRADLE_USER_HOME`。脚本只用 bash 3.2
特性（macOS 自带版本），不用 `mapfile`/关联数组/`${var^^}`。

### 8.6 macOS 故障排查

| 现象 | 处理 |
| --- | --- |
| `avdmanager` 报 `This version only understands SDK XML versions up to 3` / 解析镜像 `package.xml` 失败 | 不用 `avdmanager`；`bash setup.sh` 直接生成 `<name>.ini` + `<name>.avd/config.ini`（cmdline-tools 12.0 的已知限制） |
| `FATAL \| A snapshot operation for '<avd>' is pending and timeout has expired` | 上次快照/启动留下的锁：删除 `<avd>.avd/*.lock`、`read-snapshot.txt`、`bootcompleted.ini`（先 `run.sh --stop`），或直接 `run.sh --no-snapshot` 启动 |
| AVD 目录没有写权限 / `$HOME` 只读 | 设 `ANDROID_AVD_HOME`（以及 `ANDROID_EMULATOR_HOME`、`ANDROID_USER_HOME`）到可写目录 |
| 残留 emulator 进程占用端口 | `bash run.sh --status` 看 pid/serial，`bash run.sh --stop` 只杀本 AVD 的实例 |
| `./gradlew` 报 `Permission denied` | 检出时丢了可执行位：用 `bash ./gradlew ...`，或 `chmod +x gradlew` |
| `java: command not found` 或用到系统 `java` stub | 设 `JAVA_HOME` 为 Android Studio JBR（`/Applications/Android Studio.app/Contents/jbr/Contents/Home`），或给脚本 `--java-home` |
| `python3` 不存在（`snapshot.sh` 需要） | `xcode-select --install` 安装 Command Line Tools |
| 快照在另一台机器/Windows 起不来 | 快照与 ABI、emulator 版本绑定；恢复时用同 ABI 的 macOS 流程，Windows x86_64 流程不通用 |
| `-wipe-data` 或重建 AVD 后设备回到 stock | 重新跑 `provision.py`（注入可重复执行） |
| 校验报设备不在线 / 端口不对 | `run.sh --status` 确认 serial 是 `emulator-5554`，`provision.py --serial` 与 `run.sh --port` 保持一致 |
