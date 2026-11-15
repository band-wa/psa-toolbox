# bootclasspath 版 android.car 客户端（api）

本项目（PsA ToolBox，`com.bandwa.openadb`）自身打包了一个 no-op 的 `android.car` 客户端桩
（`tools/aosp-car-stub/`）。本目录用 Java 实现了一套**真实可用**的同名同 ABI 客户端，编译成
根目录含 `classes.dex` 的 `android.car.jar`，由 AVD 注入脚本（`avd/provision.py`）放进
bootclasspath 后生效：
APP 的 dex 里虽然仍带着桩类，但运行时类查找顺序 bootclasspath 优先，实际执行的是本 jar 的类。
它通过 Binder 事务与虚拟车服务（包名 `com.android.car`，见 `tools/android-car-boot/service/`，
双方共享 `com.bandwa.vcarlib.BootCarProtocol`）通信。

## 目录结构

```
tools/android-car-boot/api/
├── src/android/car/...                      # 6 个 Java 源文件（与桩同名、同包、同 ABI）
├── build.sh                                 # 构建：javac(JDK17,--release 8) -> d8 -> jar
├── check_abi.sh                             # 与参考桩 jar 的 ABI 兼容性核对
├── README.md
└── build/                                   # 构建产物（git 忽略）
    ├── classes/                             #   javac 输出
    ├── dex/classes.dex                      #   d8 输出（dex 037, min-api 24）
    ├── jar-root/classes.dex                 #   打入 jar 的 dex
    └── android.car.jar                      #   ★最终产物
```

协议类 `com.bandwa.vcarlib.BootCarProtocol` **不复制**，构建时直接编译
`tools/android-car-boot/protocol/java` 下的同一份源码（服务端 APK 也编译同一份），保证
客户端/服务端 wire format 不会漂移。

## 类清单（jar 内 16 个 dex 类）

| dex 类 | 说明 |
| --- | --- |
| `android.car.Car` | 客户端入口，绑定 `com.android.car/com.android.car.CarService` |
| `android.car.Car$1..$4` | 匿名 `DeathRecipient`/`ServiceConnection`/`Runnable`，非公开 API |
| `android.car.CarManagerBase` | 标记接口 |
| `android.car.CarNotConnectedException` | 未连接异常（4 个构造，与桩一致） |
| `android.car.hardware.CarPropertyConfig` | 属性配置（无参构造 + Parcelable 成员，见下） |
| `android.car.hardware.CarPropertyValue` | 属性值（propertyId/areaId/status/timestamp/value + Parcelable 成员，见下） |
| `android.car.hardware.property.CarPropertyManager` | Binder 事务属性管理器 |
| `android.car.hardware.property.CarPropertyManager$CarPropertyEventListener` | 监听回调接口 |
| `android.car.hardware.property.CarPropertyManager$ListenerCallback`（+`$1`） | 服务端事件回调 Binder |
| `com.bandwa.vcarlib.BootCarProtocol` | boot 协议常量与 Parcel 编解码（事务码 >= 0x1000） |

> **Parcelable 编码（给 AOSP 车机应用用）**：`CarPropertyValue` / `CarPropertyConfig`
> **没有** `implements Parcelable`（桩的 ABI 基准类头不含该接口，加了会破坏 `check_abi.sh`），
> 按「只增成员」的方式提供 `describeContents()`、`writeToParcel(Parcel, int)`、静态
> `readFromParcel(Parcel)` 与 `public static final Parcelable.Creator<...> CREATOR`。
> 编码顺序（`CarPropertyValue`）：`propertyId(int)`、`areaId(int)`、`status(int)`、
> `timestamp(long)`、`type(int)`、值（`BootCarProtocol.writeValue`）；
> `CarPropertyConfig`：`propertyId`、`propertyType`、`changeMode`、`writable(int)`、
> `minValue(float)`、`maxValue(float)`、`areaCount`、各 `areaId(int)`。
> 厂商 `ICarProperty` proxy 会直接读 `CarPropertyValue.CREATOR`，所以 bootclasspath 里的
> jar 与 service APK 必须成对部署。
> `BootCarProtocol` 为此新增仅供 Parcelable 编解码使用的类型码：`TYPE_BYTE_ARRAY=6`
> （DOTE 配置）、`TYPE_INTEGER_ARRAY=7`（仪表个性化 / AVM 开关等 `Integer[]`）、
> `TYPE_FLOAT_ARRAY=8`（AOSP 浮点数组属性）；自定义协议事务仍只用 `0..5`。
>
> **AOSP car-lib API 超集**：设备上的厂商车机 APK（`com.cusc.misc`）自带**真实 AOSP 实现**
> 的 `CarCabinManager`/`CarMcuManager`/`CarSensorManager`/`CarTBoxManager`/`CarHvacManager`
> 等类，它们内部 `new CarPropertyManager(binder, handler, false, tag)` 并调用 AOSP 的
> `CarPropertyManager` 方法，而 bootclasspath 会用本 jar 的实现覆盖该类。若缺成员，厂商会在
> 构造/读取时抛 `NoSuchMethodError`。因此本 jar 按「只增不删」补齐了下列 AOSP 成员：
>
> - `CarPropertyManager`：`(IBinder, Handler, boolean, String)` 构造、`getIntArrayProperty`、
>   `getFloatArrayProperty`、`getByteArrayProperty`、`getPropertyList(ArraySet)`、
>   `getReadablePropertyList(ArraySet)`、`isPropertyAvailable`、`onCarDisconnected`、
>   `setByteArrayProperty`、`setFloatArrayProperty`。
> - `Car`：`public static checkCarNotConnectedExceptionFromCarService(IllegalStateException)`、
>   `public static hideCarNotConnectedExceptionFromCarService(IllegalStateException)`。
> - `CarPropertyConfig`：`ACCESS_READ/ACCESS_WRITE`、`getAccess()`（固定回 3=可读写，厂商据此
>   过滤监听属性）、`getConfigArray()`（回空列表）。
>
> 这些 AOSP 方法在内部仍转成本项目的自定义协议（`0x1000` 段）访问服务端；服务端
> `VendorServiceShims` 的 `getCarService("cabin"/"mcu_service"/"sensor"/"car_tbox_service"/
> "hvac"/"info")` 现在返回同一个属性垫片，因此厂商管理器读到的是 `CarState`/厂商属性映射
> 表中的虚拟值（例如 DOTE `0x2170F003` 的 `byte[100]`）。


## 运行时行为

### Car

- `createCar(Context, ServiceConnection[, Handler])`：保存 application context；Handler 缺省为主线程。
- `connect()`：显式 `ComponentName("com.android.car", "com.android.car.CarService")` +
  `Context.BIND_AUTO_CREATE` 调用 `bindService`；重复调用/已连接时为空操作。
- 绑定成功后 `linkToDeath`，构造 `CarPropertyManager(binder)`，并经主线程 Handler 回调外部
  `ServiceConnection.onServiceConnected`；断链/`disconnect()` 回调 `onServiceDisconnected`。
- `isConnected()` = binder 非空且 `isBinderAlive()`；`isConnecting()` 返回绑定进行中。
- `getCarManager(String)`：`Car.PROPERTY_SERVICE`（`"property"`）返回本 jar 的
  `CarPropertyManager`；`cabin`/`hvac`/`sensor`/`audio`/`mcu_service`/`car_tbox_service`/`info`
  用 `context.getClassLoader()` 反射按类名加载**调用方 APK 自带的 car-lib 管理器类**，以
  `(IBinder, Context, Handler)`（无此构造则 `(IBinder, Context)`）构造，binder 通过
  `ICar.getCarService(name)`（事务码 2）从服务端取。结果按服务名缓存，断连/死亡时清空；
  加载不到（宿主 APK 未打包对应类）或未知服务名时 `Log.w` 并返回 `null`。
  厂商 APK 自带的是真实 AOSP 实现：它们内部 `new CarPropertyManager(...)`，
  通过上面补齐的 AOSP 成员 + 服务端属性垫片读取 `CarState`/厂商属性映射值，因此
  `getCarManager(cabin)` 等不再返回 null 也不会 NPE；事件回调仍不会触发（服务端不发事件）。
- `disconnect()`：置空状态（含管理器缓存）、`unbindService`、`unlinkToDeath`、回调外部监听。

### CarPropertyManager 与服务的 wire 协议

与 `BootCarProtocol` 完全一致（`Parcel` 手工读写，事务码/结果码/类型码见该类常量）。
事务码整体位于 `0x1000` 段，与 AOSP `android.car.ICar` AIDL 的 0..4 小号事务码隔离
（服务端对 <0x1000 的码走 ICar 兼容垫片，见 `tools/android-car-boot/service/README.md`）：

| 事务 | code | 请求 | 响应/回调 |
| --- | --- | --- | --- |
| `TX_GET` | 0x1001 | `int propId, int areaId` | `int result, int type, long timestamp, int status, value` |
| `TX_SET` | 0x1002 | `int propId, int areaId, int type, value` | `int result` |
| `TX_REGISTER` | 0x1003 | `int propId, float rate, IBinder callback` | `int result` |
| `TX_UNREGISTER` | 0x1004 | `int propId, IBinder callback` | 无（`propId<0` 表示注销全部） |
| `TX_GET_CONFIGS` | 0x1005 | 无 | `int count` + 每项 `propId,type,changeMode,writable,min,max,areaCount,int[] areas` |
| `TX_PING` | 0x1006 | 无 | `int 1`（客户端暂未使用） |
| `TX_EVENT` | 0x1100 | 服务端 → 客户端 one-way 回调：`propId,areaId,status,timestamp,type,value` | — |

错误语义（与 AOSP 客户端大致对齐，便于 APP 的 `runCatching` 逻辑）：

- binder 空/失效 → 抛 `CarNotConnectedException`；
- `getProperty` 结果码非 OK（属性不存在）→ 返回 `null`；
- `setXxxProperty` 结果码 `NOT_FOUND/NOT_WRITABLE/TYPE_MISMATCH` → 抛 `IllegalArgumentException`；
- 类型映射：int→`Integer`、float→`Float`、bool→`Boolean`、string→`String`、int[]→**`Integer[]`**
  （`PSAToolBoxService` 用 `value as? Array<*>` 消费，必须是装箱数组）；
- 事件回调经主线程 Handler 投递 `CarPropertyValue`。

## 构建

Git Bash（Windows）中执行，可重复运行：

```bash
bash tools/android-car-boot/api/build.sh
```

脚本流程：清空 `build/` → `javac --release 8 -classpath <android.jar>` 编译
`api/src` + `tools/android-car-boot/protocol/java`（`BootCarProtocol`）→ `d8.bat --min-api 24 --lib <android.jar>`
生成 `classes.dex` → `jar --create --no-manifest` 把 `classes.dex` 放在 jar 根 → 打印 jar 大小、
dex 类清单，并自动跑一次 `check_abi.sh`。

默认工具链（均可用环境变量覆盖：`JAVA_HOME`、`ANDROID_HOME`/`ANDROID_SDK_ROOT`、`ANDROID_JAR`、
`D8`、`DEXDUMP`）：

- JDK 17：`<jdk-17>`
- android.jar：`<sdk>\platforms\android-34\android.jar`
- d8/dexdump：`<sdk>\build-tools\34.0.0\`

### 产物

- 路径：`tools/android-car-boot/api/build/android.car.jar`
- 大小：约 **12341 字节**（jar 根仅 `classes.dex`；dex 037，16 个类）
- sha256：`CB71EC777CBA3566BA3127E3DCD8134BC3F4AAD176CC9DCEBB5BDEFF6BF1D42B`（JDK 17 +
  build-tools 34.0.0 下构建，可用于设备侧比对）
- `build/` 被 `api/.gitignore` 忽略，属构建产物，不入库。

## ABI 兼容性核对

判据：**参考桩 jar 里的每个 public/protected 类/字段/方法/构造，其「名称 + 泛型擦除后的 JVM
描述符」必须在本 jar 中存在**（多出的成员不算问题）。
`check_abi.sh` 用 `javap -s -protected` 提取规范化 API 行后逐类比对。

```bash
# 默认参考 third_party/android-car-stub.jar（7 类）
bash tools/android-car-boot/api/check_abi.sh
```

可选参数：`check_abi.sh [参考桩 jar] [classes 目录]`。

结果：

| 参考桩 | 类数 | 通过 | 缺失成员 | 超集新增成员 |
| --- | --- | --- | --- | --- |
| `third_party/android-car-stub.jar`（no-op 桩） | 7 | 7 | 0 | 28（`Car` +2、`CarPropertyConfig` +10、`CarPropertyValue` +4、`CarPropertyManager` +12） |

即：APP 按该 no-op 桩编译，运行时解析到本 jar 都不会 `NoSuchMethodError`/`NoSuchFieldError`。

设备侧同类验证：用 `verify/abi_scan.py` 扫描设备同版本厂商车机 APK（`com.cusc.misc`
v202403121801）的 smali 中所有对 `android.car.*` 的方法/字段引用，**缺失成员 = 0**
（报告 `verify/abi_scan_report.txt`）。

### APP 使用点与实现的对应

| APP 位置 | 用到的 API | 本实现 |
| --- | --- | --- |
| `core/.../util/CarGateway.kt` | `Car.createCar/connect/disconnect/isConnected/getCarManager(PROPERTY_SERVICE)`、`registerListener(listener,propId,rate)`、`unregisterListener(listener)`、`getProperty(propId,0)?.value` | 全部真实转发到 Binder；连接状态由 binder 存活判定 |
| `service/.../PSAToolBoxService.kt` | `onChangeEvent` 中 `value as? Array<*>`；`getIntProperty`、`setIntProperty`、`setIntArrayProperty`、`getProperty(...).value` | int[] 结果转 `Integer[]`；属性不存在时 get 返回 null / 0 |
| `app/.../ToolsSettingsViewModel.kt` | 监听事件后按 `String`/`Int`/`Float` 解析；`setIntProperty` | 值类型按服务端 type 精确映射 |
| `core/.../util/HotFixUdisk.kt` | `getIntProperty`/`setIntProperty` | 同上 |

## 已知限制

1. **真正实现的只有属性服务**：`CarPropertyManager`/`BootCarProtocol` 是完整实现（含 AOSP
   car-lib 的 `getIntArrayProperty`/`getByteArrayProperty`/`getFloatArrayProperty`/
   `getPropertyList(ArraySet)`/`isPropertyAvailable` 等超集方法）；`cabin`/`hvac`/`sensor`/
   `audio`/`mcu_service`/`car_tbox_service`/`info` 的 `getCarManager` 反射构造的是**宿主 APK
   自带的 AOSP 管理器类**，其属性读写会落到本 jar 的 `CarPropertyManager` + 服务端属性垫片，
   因此能拿到虚拟值；但没有事件回调、无真实 MCU/ECU 语义。宿主 APK 未打包这些类（例如本项目
   的 APP）时返回 `null`。没有异步/批量 API。
2. **自定义 Binder 事务协议，仅适配本项目的虚拟 CarService**（`com.android.car` 假服务）。
   事务码从 `0x1000` 起，服务端把 `<0x1000` 的 AOSP ICar 小号事务码交给兼容垫片，系统车机应用
   按 AOSP 方式 bind 同一个 Binder 也不会收到畸形 reply；但本 jar 只覆盖 APP 使用面，不要与
   真实 AOSP CarService 混用。
3. 必须注入 bootclasspath 才生效（官方 AVD 用 `tools/android-car-boot/avd/provision.py`）；
   未注入时 APP 仍使用自身 dex 中的 no-op 桩。
4. ABI 结论来自桩 jar 的静态核对与设备厂商 APK 的 live smali 引用扫描（缺失成员 0）。运行侧已在
   模拟器上通过 `com.cusc.misc` 的 AOSP `ICar` 绑定链路验证：`getCarService(audio/property/cabin/`
   `mcu_service/car_tbox_service)`、`ICarProperty` 的 `registerListener`/`getProperty`
   （`CarPropertyValue.CREATOR` 解码）、`Car.getCarManager(...)` 反射构造、
   `CarCabinManager.registerCallback`、`CarMcuManager.getByteArrayProperty(0x2170F003)`（DOTE）
   均正常。即：**本 jar 是专为本仓库的假 `com.android.car` CarService 与虚拟属性数据设计的
   兼容层**，不承诺替换真实 AOSP `android.car` 运行时。
5. 服务端协议变更后需重新执行 `build.sh`；`BootCarProtocol` 随 jar 一起进 bootclasspath，
   服务端 APK 内也有同源副本，二者由构建脚本共享同一份源码保证一致；**api.jar 与 service.apk
   必须成对升级**。
6. `CarPropertyConfig()` 无参构造、`CarPropertyManager` 的 `(IBinder)`/无参构造等额外成员属
   有意为之，不是 AOSP 语义。
