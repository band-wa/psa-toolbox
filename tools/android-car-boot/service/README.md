# CarService（`com.android.car`）服务端 APK

冒充 AOSP `com.android.car` 的 CarService 系统应用：把 bootclasspath 版 `android.car` 客户端
（`com.bandwa.openadb`）通过显式 `ComponentName("com.android.car", "com.android.car.CarService")`
绑定到本 APK 的 Binder 服务端，再用共享的 `com.bandwa.vcarlib.BootCarProtocol` 事务码读写
VHAL 属性。**不改 APP、不依赖真实 VHAL**。同一个 Binder 也接受系统里按 AOSP 方式使用
`android.car.ICar` 的车机应用（如 `com.cusc.misc`）的连接，见下文「AOSP ICar 兼容垫片」。

本模块与 `tools/android-car-boot/api` 的 bootclasspath 版客户端成对部署：

- `CarState`：20 个属性、100ms ticker、按 `rate` 回推、`linkToDeath`、后备箱/AVM/MCU 侧效应、
  `forceWrite`（广播注入用）、`sim`/`reset`/`snapshot`。
- `CarService`：`onTransact` 按事务码分区：`code >= 0x1000` 走自定义协议，`code < 0x1000`
  走 AOSP ICar 兼容垫片（见下）。
- `ControlReceiver`：广播 action 为 `com.android.car.CONTROL`（set / key / reset / sim / log）。
- 协议：`com.bandwa.vcarlib.BootCarProtocol`，**只读引用** `tools/android-car-boot/protocol/java`
  （`sourceSets["main"].java.srcDir(...)`），客户端 jar 编译同一份源码；真车 bootclasspath 的
  `android.car` parent-first 优先，不受影响。

## 构建

要求 JDK 17 + Android SDK 34（`local.properties` 的 `sdk.dir` 指向 SDK）：

```powershell
# 仓库根目录
$env:JAVA_HOME='<jdk-17>'   # 换成本机 JDK 17
.\gradlew.bat :tools:android-car-boot:service:assembleDebug --offline
```

本模块在 `settings.gradle.kts` 中只有一行：`include(":tools:android-car-boot:service")`。
协议源码用相对路径引用：`tools/android-car-boot/service` 向上三级是仓库根，
`../../../tools/android-car-boot/protocol/java`。

产物：

```
tools/android-car-boot/service/build/outputs/apk/debug/service-debug.apk
```

debug 签名（Android Debug 证书），约 35 KB（35,703 字节；多 dex：AGP 8.4 debug 分包，运行时全部加载）。

## 系统化安装

APK 作为**系统应用**安装到 `/system/app/CarService/CarService.apk`（由 `avd/provision.py`
完成），包名 `com.android.car` 即可被 bootclasspath 客户端按 `ComponentName` 绑定；
组件 `exported`，客户端不校验签名，因此不要求平台签名：

```bash
# 由 tools/android-car-boot/avd/provision.py 把 APK 放到 /system/app/CarService/CarService.apk
adb root && adb remount
adb push tools/android-car-boot/service/build/outputs/apk/debug/service-debug.apk \
    /system/app/CarService/CarService.apk
adb reboot

# 重启后确认系统应用
adb shell pm path com.android.car     # package:/system/app/CarService/CarService.apk
adb shell dumpsys package com.android.car | grep -A3 CarService
```

注意：

- 若设备已有真实 AOSP `com.android.car`（真车/automotive 镜像），不要安装本 APK，会冲突。
- 首次安装前若曾用 `adb install` 装过同包名 APK，先 `adb uninstall com.android.car`（签名不同会失败）。
- 服务端只在客户端 `bindService`（`BIND_AUTO_CREATE`）时启动；无 Activity，无需手动拉起。

## 广播控制

`ControlReceiver` 已导出（无需服务先启动，操作同进程单例 `CarState`）：

```bash
adb shell am broadcast -n com.android.car/.ControlReceiver -a com.android.car.CONTROL \
  --es cmd set --es prop 0x2140f334 --es type int --es value 35
```

| 命令 | 说明 |
| --- | --- |
| `--es cmd set --es prop 0x... --es type int\|float\|bool\|string\|intarray --es value ...` | 注入属性值（绕过可写校验）；省略 `type` 时按属性类型自动推断；`intarray` 值形如 `1,1` |
| `--es cmd key --es key 0x2140f04c --es state 1` | 模拟 MCU 方控键（默认左方控键） |
| `--es cmd reset` | 恢复默认值 |
| `--es cmd sim --es on 1` | 打开模拟驾驶（车速/转角正弦、里程缓增），`on 0` 关闭 |
| `--es cmd log` | 打印全部属性快照到 logcat |

日志统一用 tag `VirtualCar` 排查：`adb logcat -s VirtualCar`。

## 默认属性表（20 个）

| 属性 ID | 类型 | 默认值 | 可写 | 用途 |
| --- | --- | --- | --- | --- |
| `0x11100100` | string | `VF3XXXXXXXXXXXXXX` | 否 | VIN |
| `0x2140F3F3` | int | 1 | 否 | VIN 匹配状态 |
| `0x2160F338` | float | 12345.0 | 否 | 总里程 |
| `0x2140F334` | int | 62 | 否 | 剩余油量 % |
| `0x2140F525` | int | 520 | 否 | 预估续航 |
| `0x2140F335` | int | 6500000 | 否 | 累计油耗（×1e-6 L） |
| `0x21400AE5` | int | 0 | 否 | 方向盘转角 |
| `0x11600207` | float | 0 | 否 | 车速 |
| `0x2140210F` | int | 4 | 否 | 挡位：1=N 2=R 4=P 8=D 16=M |
| `0x21400978` | int | 1 | 否 | 后备箱解锁模式（由 SET 属性联动） |
| `0x21400977` | int | 1 | 是 | 后备箱解锁模式设置 |
| `0x2140F141` | int | 1 | 是 | USB OTG：1=device 2=host |
| `0x2140EEB4` | int | 0 | 否 | AVM 显示状态：2=已打开 |
| `0x2141EEB0` | int[] | `[0,0]` | 是 | AVM 开关，`[1,1]` 开、`[0,0]` 关 |
| `0x2141F04B` | int[] | `[0,0]` | 否 | MCU 方控键事件 `[keyId, state]` |
| `0x2140F04C` / `0x2140F04D` | int | 0 | 否 | 左/右方控键状态 |
| `0x2540051E` | int | 0 | 是 | 空调 AC 开关 |
| `0x25411912` | int[] | `[0,0]` | 是 | 空调温度调节 `[方向, 2按下/1抬起]` |
| `0x25411908` | int[] | `[0,0]` | 是 | 空调风量调节 `[方向, 2按下/1抬起]` |

## 协议（自定义协议，事务码 >= 0x1000）

- 事务码：`TX_GET=0x1001`、`TX_SET=0x1002`、`TX_REGISTER=0x1003`、
  `TX_UNREGISTER=0x1004`、`TX_GET_CONFIGS=0x1005`、`TX_PING=0x1006`、
  `TX_EVENT=0x1100`（服务端 → 客户端 oneway）。**客户端与服务端必须成对升级**。
- 结果码：`OK=0`、`NOT_FOUND=-1`、`NOT_WRITABLE=-2`、`TYPE_MISMATCH=-3`。
- 类型码：`INT=1`、`FLOAT=2`、`BOOL=3`、`STRING=4`、`INT_ARRAY=5`；另有仅供
  `CarPropertyValue`/AOSP 属性编解码的 `BYTE_ARRAY=6`、`INTEGER_ARRAY=7`、`FLOAT_ARRAY=8`。
- 无 AIDL、无 `getInterfaceDescriptor()`：客户端必须直接用上述事务码 `transact()` 到服务端
  `Binder.onTransact`。

### AOSP ICar 兼容垫片（事务码 < 0x1000）

同一个 Binder 被系统车机应用按 AOSP 方式 bind（action `android.car.ICar`）时，`onTransact`
不会把 AOSP 的小号事务码当成自定义协议，而是交给垫片。厂商 `com.cusc.misc` 的 `ICar` proxy
布局（`verify/vendor_icar_proxy.smali`）：

| 事务码 | 含义 |
| --- | --- |
| `1` | `setCarServiceHelper(IBinder)`，oneway |
| `2` | `getCarService(String name)` → IBinder |
| `3` | `getCarConnectionType()` → int（固定回 `5` = `CONNECTION_TYPE_EMBEDDED`） |

垫片先 try/catch 跳过 AIDL 的 `int strictModePolicy + String descriptor` 前缀（畸形数据也不崩），
所有写 reply 处都判空（oneway 时 reply 可能为 null，但仍返回 true），未知小号事务码只
`writeNoException()`。`getCarService(name)` 返回三种 Binder：

- `"audio"` → `ICarAudio` 兼容 Binder：`getGroupMaxVolume=30`、`getGroupMinVolume=0`、
  `getGroupVolume=20`、`getVolumeGroupCount=1`、`getVolumeGroupIdForUsage=0`、
  `getExternalSources=`空 `String[]`、`getUsagesForVolumeGroupId=`空 `int[]`、
  `createAudioPatch=0`（null）、其余 int getter=0、bool getter=false、
  `registerVolumeCallback` 等 void 事务只回 no-exception。
- `"property"`（以及 `"cabin"`/`"mcu_service"`/`"sensor"`/`"car_tbox_service"`/`"hvac"`/`"info"`）
  → 属性垫片 Binder，同一个 Binder 同时服务两种事务码：
  - **AOSP `ICarProperty` AIDL（1..5）**：`1` registerListener / `2` unregisterListener /
    `3` getPropertyList / `4` getProperty / `5` setProperty。register/unregister 解析 `propId`
    （+`rate`）记日志；`getProperty` 按厂商 `CarPropertyManager` 实际读取的 ID 回
    `CarPropertyValue`（VIN `0x11100100` 取 `CarState` 默认值 `VF3XXXXXXXXXXXXXX`、MCU 版本
    `0x2110F056`=`"VirtualCar-1.0.0"`、DOTE `0x2170F003`=`byte[100]` 且 `byte[16]=1`、
    夜间模式 `0x2140F05C`/语言 `0x2140F308`/保养 `0x2140F526`/`0x2140F336`、
    仪表个性化 `0x2141F30C`=`Integer[8]{1,…}`、仪表亮度/显示/OK/ECO/ACC/休眠等=0；
    `CarState` 已有的属性走默认映射，未知 ID 回整数 0）；`getPropertyList` 回空列表；
    `setProperty` 解码 `CarPropertyValue` 后经 `CarState.forceWrite` 写回虚拟属性
    （未知/类型不匹配只记日志，不抛异常）。
  - **本项目自定义协议（`>=0x1000`）**：bootclasspath 的 api jar 里，厂商自带 AOSP 管理器
    （`CarCabinManager`/`CarMcuManager`/…）内部创建的 `CarPropertyManager` 会用
    `TX_GET=0x1001`/`TX_SET=0x1002`/`TX_REGISTER=0x1003`/`TX_UNREGISTER=0x1004`/
    `TX_GET_CONFIGS=0x1005` 访问同一个 binder。注意：**自定义协议不带 AIDL 的 exception
    前缀**（api 的 `CarPropertyManager.transact()` 直接从位置 0 读业务字段，`getProperty`
    第一项读 `result`、`getPropertyList` 第一项读 `count`、`setValue` 第一项读 `result`）。
    `TX_GET` 应答 = `result`+`type`+`timestamp`+`status`+`value`，`TX_SET` 应答 = `result`，
    `TX_GET_CONFIGS` 应答 = `count`，`TX_REGISTER` 应答 = `result`；`CarState`/厂商属性映射表中
    存在的属性回真实值（DOTE `byte[100]`、VIN、仪表个性化等），未知属性回 `NOT_FOUND` 让 api 侧
    兜底（空数组/0/false），不做伪造数据；`TX_SET` 经 `CarState.forceWrite` 写回并回结果码。
- 其它 name → 通用 Binder：任何事务只回 `writeNoException()`。

注意：`CarPropertyValue` / `CarPropertyConfig` 的 Parcelable 编码由 **bootclasspath 的 api jar**
提供（`tools/android-car-boot/api`）。设备上 `/system/framework/android.test.base.jar`
必须是带 `CREATOR` 的新版本，**api jar 与 service APK 必须成对部署**，否则厂商反序列化
`CarPropertyValue` 会抛 `NoSuchFieldError: CREATOR` 而 FATAL。

此外，api 里的 `android.car.Car.getCarManager()` 对 `cabin`/`hvac`/`sensor`/`audio`/
`mcu_service`/`car_tbox_service`/`info` 返回**厂商 APK 自带的 car-lib 管理器类**（反射构造，
非 null）。这些类是**真实 AOSP 实现**，内部会 `new CarPropertyManager(...)` 并调用 AOSP 方法，
所以 api jar 必须带 AOSP 超集成员（`getIntArrayProperty`/`getByteArrayProperty`/
`getFloatArrayProperty`/`getPropertyList(ArraySet)` 等，见 `tools/android-car-boot/api/README.md`）。
属性读写最终落到本服务端属性垫片的同一 Binder（`getCarService("cabin"/"mcu_service"/…)` 也返回
属性垫片），因此能读到 `CarState`/厂商属性映射表中的虚拟值；但**没有事件回调**、没有真实
MCU/ECU 语义（按键、空调、真实音量等仍不会真正生效）。厂商后续版本若读取更多属性 ID 或依赖
事件，需扩展 `VendorServiceShims` 映射表。

## 兼容性结论（模拟器 Android 9 / SDK 28，vendor `com.cusc.misc` v202403121801）

在 `avd/provision.py` 真实注入（`/system/framework/android.test.base.jar` = bootclasspath 版 api
jar + `/system/app/CarService/CarService.apk` = 本 APK）并重启后：

- AOSP `ICar` 1/2/3 垫片生效：`getCarService("audio"/"property"/"cabin"/"mcu_service"/
  "car_tbox_service")` 均返回 Binder；`CarAudioManager` 绑定成功、四音量=20；`ICarProperty` 6 个
  `registerListener` 成功。
- `Car.getCarManager("cabin")` 返回非 null，`CarCabinManager.registerCallback` + 约 45 个 cabin
  属性 `registerListener` 成功，`BdstarKeyImpl.registerCallback` 不再 NPE；`getVinCode()` =
  `VF3XXXXXXXXXXXXXX`；mqtt 进程正常启动。
- `ICarProperty.getProperty(0x2170F003)` 自定义协议解码正常：vendor 打出
  `BdstarDriveImpl: getDoteConfig size: [100 字节]`、`dote[76]=0`、`AQS_CONFIG=false`；
  `MQTTService` 用虚拟 VIN 连上 `ssl://117.78.34.59:53483` 并 `sendMsg ai2/client`
  （payload 含 `clientId=VF3XXXXXXXXXXXXXX`）；AVM activity `onCreate`→`onDestroy` 正常。
- 全程 crash buffer 为空，无 `NoSuchFieldError`/`NoSuchMethodError`/FATAL。

已知限制：服务端不主动发 `TX_EVENT`，厂商 UI 的按键/空调等异步事件不会真正刷新；所有属性值为
虚拟数据（无真实 MCU/ECU 语义）；MQTT 等服务使用虚拟 VIN 连云，云端账号校验类响应属预期；包名
`com.android.car` 与真车系统 CarService 冲突，仅限模拟器/开发环境使用。

## 验证

```powershell
$bt = '<sdk>\build-tools\34.0.0'
& "$bt\aapt.exe" dump badging tools\android-car-boot\service\build\outputs\apk\debug\service-debug.apk
# package: name='com.android.car' versionCode='1' versionName='1.0.0' sdkVersion:'24' targetSdkVersion:'28'
# 且 xmltree 里 service=com.android.car.CarService、receiver=com.android.car.ControlReceiver

# dex 类校验（APK 多 dex：classes2/classes3）
& "$bt\dexdump.exe" -f <unzip 出来的 classes2.dex> | findstr "Class descriptor"
# com/android/car/CarService、CarState、ControlReceiver、VendorServiceShims；classes3.dex 里是 com/bandwa/vcarlib/BootCarProtocol
# （VendorServiceShims 含 serviceFor / handleCustomGet / handleCustomSet / applyPropertyResult / customPropertyValue）

# 设备侧验证（不安装、不重启，仅检查现有系统镜像里的表现）
bash verify/vendor-icar-test.sh
```

## 风险与注意

- `ControlReceiver` 导出且无鉴权，仅用于模拟器/开发调试，**不要装到量产车机**。
- debug 签名：AGP 默认 debug 证书（`CN=Android Debug`），与主工程签名不同；同包名已装过其他签名版本时需先卸载。
- 包名 `com.android.car` 与真实车机系统 CarService 冲突，只在模拟器/无 car 服务设备使用。
- 本 APK 只负责服务端；客户端需由 bootclasspath 版 `android.car` 桩显式绑定
  `com.android.car/.CarService`。AOSP `ICar` 垫片提供的是**虚拟数据**（见上）：`ICarProperty`
  只按厂商实际读取的属性 ID 回 `CarState` 默认值/固定值，`getPropertyList` 为空、不主动推送
  属性事件；`ICarAudio` 为固定默认音量组。系统车机应用依赖真实 MCU/ECU 语义的功能（按键、
  空调、真实音量、跨进程事件回调）不会真的生效，但 binder 链路不会再因
  `SecurityException` / `NoSuchFieldError` / `NoSuchMethodError` / null 管理器而 FATAL。
  若厂商后续版本改为读取更多属性 ID 或依赖事件回调，需扩展 `VendorServiceShims` 的映射表。
- 部署成对性：bootclasspath 的 `android.car` api jar 与 system/app 的 service APK 必须来自
  同一版本构建（`tools/android-car-boot/api` + `tools/android-car-boot/service`），否则
  `CarPropertyValue`/`CarPropertyConfig` 的 Parcel 编解码会不一致；且 api jar 必须带 AOSP
  超集成员（`Car` 两个静态 helper、`CarPropertyConfig.getAccess/getConfigArray`、
  `CarPropertyManager` 的 10 个 AOSP 方法、`TYPE_FLOAT_ARRAY`），否则厂商真实 AOSP 管理器类
  会抛 `NoSuchMethodError`。ABI 结论：`check_abi.sh` 7/7、缺失 0、超集新增 28；设备侧 smali
  引用扫描缺失 0。
- 若客户端 `targetSdk >= 30` 且显式绑定返回 false，检查包可见性：
  `AndroidManifest.xml` 的 `<queries>` 需声明 `<package android:name="com.android.car"/>`
  （系统镜像内的预装应用通常默认可查询，真机除外）。
