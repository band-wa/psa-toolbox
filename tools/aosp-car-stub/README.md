# android.car 编译期桩

**用途**：工程只需编译期看到 `android.car.*` 的类型形状，实现由运行时提供：真车用系统
`/system/framework/android.car.jar`，官方 Android 9 AVD 底座
（[`tools/android-car-boot`](../android-car-boot/README.md)）把真实实现注入 boot class path。
两者都是 bootclasspath parent-first，会覆盖 APK 内的同名类，桩不生效。

本目录为 clean-room 源码：只声明 API 形状，不含实现逻辑。

## 生成与引入

```bash
bash tools/aosp-car-stub/build.sh   # → third_party/android-car-stub.jar（7 个 class，约 4 KB）
```

需要 **JDK 17**（`javac`/`jar`）与 SDK platform **android-34** 的 `android.jar`；脚本依次取 `JAVA_HOME`/`PATH`、`ANDROID_JAR`、
`ANDROID_HOME`/`ANDROID_SDK_ROOT`、`local.properties` 的 `sdk.dir`。
Windows 上用 Git Bash 执行（`C:\Program Files\Git\bin\bash.exe`）。
jar 已入库，只有改 `src/` 时才需重新生成。

- `:app` 以 `implementation` 引入（`app/build.gradle.kts`）：所有变体都把桩打进 dex——非 automotive 镜像
  没有 `android.car.jar`，缺类会在 Service 实例化时 `NoClassDefFoundError`。
- `:core`、`:service` 只用 `compileOnly`；运行时真实车机 boot classpath parent-first 优先，行为不变。

## 覆盖面（`src/` 6 个源文件 / 7 个 class）

`android.car.Car`、`CarManagerBase`、`CarNotConnectedException`、
`android.car.hardware.CarPropertyValue`、`CarPropertyConfig`、
`android.car.hardware.property.CarPropertyManager`（含嵌套接口 `CarPropertyEventListener`，这是能摆脱反射的关键）。

## 厂商封装 → AOSP 属性（均实车验证）

| 原厂商调用 | 现实现（权威定义见右栏所指源码） |
|---|---|
| `CarCabinManager` 回调 | `registerListener`：`0x2141F04B` `0x2140F04C` `0x2140F04D` `0x21400AE5`（见 `service/src/main/java/com/bandwa/openadb/service/PSAToolBoxService.kt:908-913`） |
| `IHvac.tempUp/DownStart/End` | `setIntArrayProperty(0x25411912 HVAC_TEMPERATURE_ADJUST, [方向,阶段])`，1=加 0=减，2=按下 1=抬起 |
| `IHvac.windLevelUp/DownStart/End` | `setIntArrayProperty(0x25411908 HVAC_FAN_SPEED_ADJUST, [方向,阶段])` |
| `IHvac.acState` | `0x2540051E HVAC_AC_ON_SET` 写 2 再写 1 |
| `IAudio.currentActiveAudioPkg` | 反射 `AudioManager.getCurrentActiveAudioPkg()`（不在 SDK jar 里，厂商实现亦如此） |
| `CarPropertyManager.isDeviceMode/setDeviceMode` | `0x2140F141 USB_OTG_MODE_INFO`：读 ==1 为 device，写 1/2（见 `core/src/main/java/com/bandwa/openadb/util/HotFixUdisk.kt:22`） |
| `settingslib.GlobalUtils.sendKeyDownUpSync` | `core` 的 `KeyInjector`（`Instrumentation.sendKeyDownUpSync`，后台线程） |
| `settingslib.ActivityManagerWrapper.killSystemUI` | `core` 的 `ProcessUtils.killSystemUi`（遍历 `getRunningAppProcesses()` 后 `killProcess`） |

## 注意

- `:app` 会把桩打进 dex（见上），以保证非 automotive 设备可启动；真机运行时优先使用系统 `android.car`。
- `/system/framework/android.car.jar` 这一路径尚未在车机上复核，下次上车 `ls -l` 一条命令即可确认。
