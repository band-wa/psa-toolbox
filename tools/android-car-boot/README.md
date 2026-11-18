# Android Car Boot：bootclasspath 版 `android.car` + 系统 `com.android.car` CarService

把标准 dex 形态的 `android.car` 客户端放进 **boot class path**，让普通应用在没有
`/system/framework/android.car.jar` 的设备上也能直接 `import android.car.*`；同时把
`com.android.car` 服务端作为系统应用装上，两者通过本仓库自定义的 boot 协议通信
（协议见 [`protocol/java/com/bandwa/vcarlib/BootCarProtocol.java`](protocol/java/com/bandwa/vcarlib/BootCarProtocol.java)，
服务端控制方式见 [`service/README.md`](service/README.md)）。

测试底座是 **Google 官方 Android 9 userdebug AVD**：Windows 用 `system-images;android-28;default;x86_64`，
macOS（Apple Silicon）用 `system-images;android-28;default;arm64-v8a`；两者都是
system-as-root，`adb root` + `adb remount` 后可直接安装；最小生效机制只需替换
`android.test.base.jar` 并删除它的 boot image 槽位，不改 ramdisk / zygote rc /
`BOOTCLASSPATH` / `-Ximage`，`android.car.*` 由 BootClassLoader 加载。改动只落在 AVD 的
system 覆盖层，不动 SDK 共享镜像，可随时回滚或重建。

> ⚠️ **仅用于本地测试模拟器**。会替换系统 `/system/framework/android.test.base.jar` 的
> 启动类并把 `com.android.car` 换成 debug 签名的模拟服务，**绝不能装到真车或日常使用的车机**。

详细步骤（建 AVD / 启动 / 注入 / 导出快照）见 [`avd/README.md`](avd/README.md)：第 1–7 节以 Windows
（PowerShell）流程为主（1.1 为 macOS 基线），第 8 节为 macOS（Apple Silicon）实现。

## 目录

```
tools/android-car-boot/
├── api/          # bootclasspath 客户端（android.car.* 标准 dex）构建：bash api/build.sh
├── protocol/     # 客户端/服务端共用的 boot 协议（BootCarProtocol）
├── service/      # com.android.car 服务端（独立 Gradle 模块 :tools:android-car-boot:service）
├── avd/          # 官方 AVD 底座：setup / run / provision / snapshot + README（.ps1 为 Windows，.sh 为 macOS）
└── dist/         # AVD 快照产物（gitignore）
```

## 注入机制（两步）

1. `/system/framework/android.test.base.jar`（BOOTCLASSPATH 第 15 槽）替换为
   `api/build.sh` 产出的标准 dex jar；
2. 删除对应 ABI 槽位目录下的四个 boot image 槽位文件：
   - Windows x86_64：`/system/framework/x86_64/boot-android.test.base.{art,art.rel,oat,vdex}`；
   - macOS arm64-v8a：`/system/framework/arm64/boot-android.test.base.{art,art.rel,oat,vdex}`
     （其中 `.vdex` 在原版镜像里是符号链接，备份/还原会记录链接本身）。

槽位缺失时 ART 会回退到从该槽位对应的 bootclasspath jar 解 dex，`android.car.*` 因此由
**BootClassLoader** 加载；`com.android.car` 服务端 APK 放到
`/system/app/CarService/CarService.apk`，普通 APP 无需任何改动即可连接并读写 VHAL。
启动约 6–15 s，probe 打印 `CAR_LOADED_FROM=java.lang.BootClassLoader`，APP CarGateway 正常连接。

## 构建

需要 JDK 17、Android SDK Platform 34 / Build-Tools 34.0.0。Windows 用 Git Bash；macOS（Apple Silicon）用系统
`/bin/bash` 3.2 即可；JDK 与 build-tools 版本和 Windows 相同（JDK 17 / build-tools 34.0.0）。

Windows（Git Bash）：

```powershell
bash tools/android-car-boot/api/build.sh                                # android.car jar（javac + d8 + jar）
.\gradlew.bat :tools:android-car-boot:service:assembleDebug --offline    # com.android.car 服务 APK
.\gradlew.bat :app:assembleDebug -PavdAbi --offline                     # 工具箱 APP（-PavdAbi 额外打包 x86_64 native 库）
```

macOS（Apple Silicon）：

```bash
bash tools/android-car-boot/api/build.sh                                 # 跨平台：POSIX d8/dexdump + BSD stat
bash ./gradlew :tools:android-car-boot:service:assembleDebug            # 服务 APK
bash ./gradlew :app:assembleDebug -PavdAbi                              # APP（可选，用于 CarGateway 校验）
```

macOS 注意：`gradlew` 与各 `*.sh` 已提交可执行位（100755），`./gradlew`、`./tools/android-car-boot/avd/setup.sh`
可直接运行；Windows 检出若丢了权限位，用 `bash ./gradlew` / `bash ...setup.sh` 亦可。

产物：`api/build/android.car.jar`、`service/build/outputs/apk/debug/service-debug.apk`、
`app/build/outputs/apk/debug/app-debug.apk`；三者分别对应注入用的 bootclasspath jar、
系统 CarService 与待测 APP。

## 快速开始（官方 AVD）

```powershell
# 创建（幂等，可跳过）并启动 AVD：psa_api28 @ 5554，窗口（-NoWindow 为 headless）、writable-system、no-snapshot
powershell -NoProfile -ExecutionPolicy Bypass -File tools/android-car-boot/avd/setup.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools/android-car-boot/avd/run.ps1

# 注入 + 校验：root/remount -> 备份 -> 换 jar -> 删槽位 -> 系统化服务 -> 重启 -> 校验
python tools/android-car-boot/avd/provision.py --serial emulator-5554 `
    --apk app/build/outputs/apk/debug/app-debug.apk

# 导出 AVD 快照（可分发、可恢复）
powershell -NoProfile -ExecutionPolicy Bypass -File tools/android-car-boot/avd/snapshot.ps1
```

macOS（Apple Silicon）不使用 PowerShell：脚本为同目录的 `setup.sh` / `run.sh` / `snapshot.sh`（与
`provision.py` 共用），可复制粘贴的命令与前置条件见下一节
[`macOS（Apple Silicon）`](#macosapple-silicon)。

## macOS（Apple Silicon）

两组脚本并存，`provision.py` 是同一份文件，按设备 `ro.product.cpu.abi` 自动选择槽位目录。

### 前置条件

- Apple Silicon（arm64）Mac；Xcode Command Line Tools（提供 `python3`）；
- JDK 17（与 Windows 基线一致；sdkmanager 也用它）；
- Android SDK 默认 `~/Library/Android/sdk`；缺失组件可一次性安装：

```bash
sdkmanager --install "platform-tools" "emulator" "build-tools;34.0.0" \
  "platforms;android-34" "system-images;android-28;default;arm64-v8a"
```

`x86_64` 的 API 28 镜像**不能**在 Apple Silicon 上运行（emulator 只随包提供 `darwin-aarch64` 的 QEMU），
必须用 `arm64-v8a` 镜像；它同时与真车 ABI 一致。加速是系统内置的 Hypervisor.framework（HVF），
没有也不需要使用 AEHD/HAXM。`cmdline-tools` 12.0 的 `avdmanager` 解析不了本 SDK 的 XML v4 镜像
`package.xml`（`This version only understands SDK XML versions up to 3` + `unexpected <abis>`），
所以 `setup.sh` 不调用 `avdmanager`，直接写 `<name>.ini` 与 `<name>.avd/config.ini`。

### 快速开始（可复制粘贴）

在仓库根目录执行（脚本用 `bash` 调用，不依赖 git 里的可执行位）：

```bash
# 1. 构建三个产物（第 3 条可选，仅用于 APP CarGateway 校验）
bash tools/android-car-boot/api/build.sh
bash ./gradlew :tools:android-car-boot:service:assembleDebug
bash ./gradlew :app:assembleDebug -PavdAbi

# 2. 创建（幂等）并启动 AVD：psa_api28 @ emulator-5554，窗口（--window=0 为 headless）、writable-system、no-snapshot
bash tools/android-car-boot/avd/setup.sh
bash tools/android-car-boot/avd/run.sh

# 3. 注入 + 校验：root/remount → 备份 → 换 jar → 删 arm64 槽位 → 系统化服务 → 重启 → 校验
#    --apk 可选，需先构建 APP（见第 1 条），开启 APP CarGateway 校验
python3 tools/android-car-boot/avd/provision.py --apk app/build/outputs/apk/debug/app-debug.apk
python3 tools/android-car-boot/avd/provision.py --verify

# 4. 状态 / 停止 / 快照；回滚成原版用 --rollback
bash tools/android-car-boot/avd/run.sh --status
bash tools/android-car-boot/avd/run.sh --stop
bash tools/android-car-boot/avd/snapshot.sh
```

可选只读诊断：`python3 tools/android-car-boot/avd/provision.py --print-device-info`；预览命令：
`bash tools/android-car-boot/avd/setup.sh --verify-only`、`run.sh --dry-run`、`snapshot.sh --dry-run`、
`provision.py --dry-run`。shell 脚本的详细参数（`.sh` 同时接受 `--avd-name` 与 `-AvdName`、`--stop` 与
`-Stop` 两种拼写；`provision.py` 只用 `--long` 形式）与故障排查见 [`avd/README.md`](avd/README.md) 第 8 节。

### Windows 与 macOS 对照

| 项 | Windows | macOS（Apple Silicon） |
| --- | --- | --- |
| 脚本 | `setup.ps1` / `run.ps1` / `snapshot.ps1` + `provision.py` | `setup.sh` / `run.sh` / `snapshot.sh` + 同一份 `provision.py`（ABI 自适应） |
| 系统镜像 | `system-images;android-28;default;x86_64`（rev 4） | `system-images;android-28;default;arm64-v8a`（rev 2） |
| ABI | x86_64 | arm64-v8a（与真车一致） |
| 加速 | AEHD 2.2.0（WHPX 可替代） | 系统 Hypervisor.framework（HVF），无需安装 |
| 注入槽位 | `/system/framework/x86_64/` | `/system/framework/arm64/` |
| SDK 默认位置 | `D:\Tools\Android\sdk`（脚本默认值，可覆盖） | `~/Library/Android/sdk` |
| d8 | `d8.bat`（build-tools 34.0.0） | POSIX `d8`（build-tools 34.0.0） |
| 文件大小 | GNU `stat -c %s` | BSD `stat -f %z` |
| 快照打包 | 系统 `tar.exe -a` | `python3` 的 `zipfile`（ZIP64，适合大容量稀疏盘 AVD） |
| 启动方式 | WMI 后台启动 | `nohup` 后台启动 + pid 文件 |
| AVD 目录 | `%USERPROFILE%\.android\avd` | `~/.android/avd` |

macOS `.sh` 脚本认 `ANDROID_SDK_ROOT` / `ANDROID_HOME`、`ANDROID_AVD_HOME`、`ANDROID_EMULATOR_HOME`、
`ANDROID_USER_HOME`、`JAVA_HOME`（供 `setup.sh` 调 sdkmanager）；Windows `.ps1` 用 `-SdkRoot` 参数
（`setup.ps1` 另有 `-JavaHome`）。三个 shell 脚本都支持 `--sdk-root`，`--java-home` 仅 `setup.sh` 需要；
Gradle 构建另认 `GRADLE_USER_HOME`。

## 限制与注意事项

- **仅 Android 9（API 28）官方 userdebug 模拟器**：依赖镜像的 `android.test.base` bootclasspath 槽位与
  可写 `/system`；Windows 用 `x86_64` 镜像、macOS（Apple Silicon）用 `arm64-v8a` 镜像，其他设备/镜像不适用。
- **不要装到真车**：会覆盖 `/system/framework/android.test.base.jar`（bootclasspath 类）并把
  `com.android.car` 换成 debug 签名的模拟服务；与真实车机的 `com.android.car` 包名冲突。
- **android.test.base 槽的 29 个类不再从 boot classpath 提供**（仅影响用到
  `android.test.base` 的调试/测试场景）。
- 注入可回滚：`avd/provision.py --rollback` 从 `/data/avd-orig` 备份还原原版 jar 与槽位；
  `-wipe-data` 或重建 AVD 后重新跑 `provision.py` 即可。macOS 的 arm64 槽位文件 `.vdex` 是符号链接，
  备份/还原会记录并恢复链接本身。
- **快照是 ABI 相关的**：arm64-v8a 的 macOS 快照不能在 Windows x86_64 流程里直接启动，反之亦然；
  跨平台只共享 `manifest.json` 里的元数据信息，不共享系统镜像。
- **产物指纹**：服务 APK / APP APK 的大小与 sha256 由 Gradle/AGP 环境决定，两端可能不同；api jar
  在 JDK 17 + build-tools 34.0.0 下相同。注入脚本以本地实际文件为准。
