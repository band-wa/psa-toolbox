# Android Car Boot：bootclasspath 版 `android.car` + 系统 `com.android.car` CarService

把标准 dex 形态的 `android.car` 客户端放进 **boot class path**，让普通应用在没有
`/system/framework/android.car.jar` 的设备上也能直接 `import android.car.*`；同时把
`com.android.car` 服务端作为系统应用装上，两者通过本仓库自定义的 boot 协议通信
（协议见 [`protocol/java/com/bandwa/vcarlib/BootCarProtocol.java`](protocol/java/com/bandwa/vcarlib/BootCarProtocol.java)，
服务端控制方式见 [`service/README.md`](service/README.md)）。

测试底座是 **Google 官方 Android 9 userdebug AVD**（`system-images;android-28;default;x86_64`）：
system-as-root，`adb root` + `adb remount` 后可直接安装；最小生效机制只需替换
`android.test.base.jar` 并删除它的 boot image 槽位，不改 ramdisk / zygote rc /
`BOOTCLASSPATH` / `-Ximage`，`android.car.*` 由 BootClassLoader 加载。改动只落在 AVD 的
system 覆盖层，不动 SDK 共享镜像，可随时回滚或重建。

> ⚠️ **仅用于本地测试模拟器**。会替换系统 `/system/framework/android.test.base.jar` 的
> 启动类并把 `com.android.car` 换成 debug 签名的模拟服务，**绝不能装到真车或日常使用的车机**。

详细步骤（建 AVD / 启动 / 注入 / 导出快照）见 [`avd/README.md`](avd/README.md)。

## 目录

```
tools/android-car-boot/
├── api/          # bootclasspath 客户端（android.car.* 标准 dex）构建：bash api/build.sh
├── protocol/     # 客户端/服务端共用的 boot 协议（BootCarProtocol）
├── service/      # com.android.car 服务端（独立 Gradle 模块 :tools:android-car-boot:service）
├── avd/          # 官方 AVD 底座：setup / run / provision / snapshot + README
└── dist/         # AVD 快照产物（gitignore）
```

## 注入机制（两步）

1. `/system/framework/android.test.base.jar`（BOOTCLASSPATH 第 15 槽）替换为
   `api/build.sh` 产出的标准 dex jar；
2. 删除 `/system/framework/x86_64/boot-android.test.base.{art,art.rel,oat,vdex}`
   四个 boot image 槽位文件。

槽位缺失时 ART 会回退到从该槽位对应的 bootclasspath jar 解 dex，`android.car.*` 因此由
**BootClassLoader** 加载；`com.android.car` 服务端 APK 放到
`/system/app/CarService/CarService.apk`，普通 APP 无需任何改动即可连接并读写 VHAL。
启动约 6–15 s，probe 打印 `CAR_LOADED_FROM=java.lang.BootClassLoader`，APP CarGateway 正常连接。

## 构建

需要 JDK 17、Android SDK Platform 34 / Build-Tools 34.0.0、Git Bash（Windows）。

```powershell
bash tools/android-car-boot/api/build.sh                                # android.car jar（javac + d8 + jar）
.\gradlew.bat :tools:android-car-boot:service:assembleDebug --offline    # com.android.car 服务 APK
.\gradlew.bat :app:assembleDebug -PavdAbi --offline                     # 工具箱 APP（-PavdAbi 额外打包 x86_64 native 库）
```

产物：`api/build/android.car.jar`、`service/build/outputs/apk/debug/service-debug.apk`、
`app/build/outputs/apk/debug/app-debug.apk`；三者分别对应注入用的 bootclasspath jar、
系统 CarService 与待测 APP。

## 快速开始（官方 AVD）

```powershell
# 创建（幂等，可跳过）并启动 AVD：psa_api28 @ 5554，无窗口、writable-system、no-snapshot
powershell -NoProfile -ExecutionPolicy Bypass -File tools/android-car-boot/avd/setup.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File tools/android-car-boot/avd/run.ps1

# 注入 + 校验：root/remount -> 备份 -> 换 jar -> 删槽位 -> 系统化服务 -> 重启 -> 校验
python tools/android-car-boot/avd/provision.py --serial emulator-5554 `
    --apk app/build/outputs/apk/debug/app-debug.apk

# 导出 AVD 快照（可分发、可恢复）
powershell -NoProfile -ExecutionPolicy Bypass -File tools/android-car-boot/avd/snapshot.ps1
```

## 限制与注意事项

- **仅 x86_64 Android 9（API 28）模拟器**：依赖官方 userdebug 镜像的 `android.test.base`
  bootclasspath 槽位与可写 `/system`；其他设备/镜像不适用。
- **不要装到真车**：会覆盖 `/system/framework/android.test.base.jar`（bootclasspath 类）并把
  `com.android.car` 换成 debug 签名的模拟服务；与真实车机的 `com.android.car` 包名冲突。
- **android.test.base 槽的 29 个类不再从 boot classpath 提供**（仅影响用到
  `android.test.base` 的调试/测试场景）。
- 注入可回滚：`avd/provision.py --rollback` 从 `/data/avd-orig` 备份还原原版 jar 与槽位；
  `-wipe-data` 或重建 AVD 后重新跑 `provision.py` 即可。
