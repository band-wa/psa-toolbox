# 架构说明

> 本文描述仓库**当前真实状态**。
> 维护约定：新增/删除模块、移动顶层包、调整模块依赖时，同步更新本文。

## 1. 模块总览

`settings.gradle.kts` include 五个主工程 Gradle 模块（`:app` 是唯一的 application 模块）和一个 tools 模块（`:tools:android-car-boot:service`）：

| 模块 | namespace | 类型 | 职责 | 依赖 |
| --- | --- | --- | --- | --- |
| `:app` | `com.bandwa.openadb` | application | 壳：`PSAHelperApplication`、`MainActivity`、9 个 Tab 的 feature 与 ViewModel；Manifest、DI 根、assets、签名 | `:core` `:ui` `:service` `:hook` |
| `:core` | `com.bandwa.openadb.core` | library | 无 UI 的通用能力：系统/进程/shell、车控网关、油耗账本、APK 下载与校验、smali/dex 补丁工具、hook 状态、实体类 | `:hook` |
| `:ui` | `com.bandwa.openadb.ui` | library | Compose 主题、颜色/形状/字体、通用组件、通用弹窗、预览；功能图标资源 | `:core` |
| `:service` | `com.bandwa.openadb.service` | library | 常驻前台服务、广播接收、无障碍、悬浮窗 overlay、安装器、AIDL bridge、服务侧 DI | `:core` `:ui` `:hook` |
| `:hook` | `com.bandwa.hook` | library（纯 Java） | 注入宿主应用的唯一入口 `Hook.java` + AIDL 回调；构建出 `hook.dex` | 无 |

非 Gradle 目录，不要当模块用：

- `tools/aosp-car-stub/`：`android.car` 的 clean-room 桩源码（`build.sh` 手工编译），不参与 Gradle。
- `tools/android-car-boot/`：bootclasspath 版 `android.car` 客户端（`api/build.sh` 手工编译成 jar）+ 系统 `com.android.car` 服务端（独立 Gradle 模块 `:tools:android-car-boot:service`）+ 官方 Google AVD 底座脚本（`avd/`：setup / run / provision / snapshot），方案与限制见其 README；仅用于本地测试模拟器，不参与主工程依赖。
- `third_party/android-car-stub.jar`：上述桩的产物；`:app`、`:core`、`:service` 以 `compileOnly` 引入（`:app` 同时 `implementation`，见 §6）。
- `verify/`：手工验证脚本与样本，不属于构建。
- `docs/`：文档与截图。

依赖图（箭头 = 编译期依赖，只能指向下层）：

```
:app ─┬─► :core ──► :hook
      ├─► :ui   ──► :core
      ├─► :service ─┬─► :core
      │             ├─► :ui
      │             └─► :hook
      └─► :hook
```

## 2. 包结构规范

### `:app` —— 壳 + feature

- 唯一 application 模块。`MainActivity.kt` 在 `presentation/main/`，单 Activity 承载 9 个 Tab（Compose，无 Fragment）。
- 每个业务 feature 固定三件套：`XxxRoute.kt`（Compose 路由/入口）、`XxxUIState.kt`（状态与事件类型）、`XxxViewModel.kt`（`@HiltViewModel` + `@Inject` 构造）。
- 现有 10 个 feature 包（`app/src/main/java/com/bandwa/openadb/presentation/main/`）：`about`、`binding`、`expert`、`fuel`、`music`、`navi`、`person`、`projection`、`system`、`tools`。其中 `binding` 是音乐/导航共用的按键绑定区块，不单独占 Tab。
- 壳层文件：`PSAHelperApplication.kt`（根包，`@HiltAndroidApp`）、`di/ApplicationModule.kt`。

### `:core` —— 纯业务与系统能力

- `util/`：`ShellUtils`、`ProcessUtils`、`OpenSysSetting`、`CarGateway`、`FuelLedger`、`HookState`、`TTSHelper` 等。
- `util/apk/`：APK 下载（`ApkDownloader`、`RemoteHttp`）。
- `util/apktools/`：补丁工具链 `SmaliMod`、`SmaliAssembler`、`DexInjector`、`FastApkPacker`、`ApkInspector`、`ApkSignUtil`。
- `entity/`：数据模型（如 `MusicPlayer`）。
- 一线约束：`:core` 不依赖 `:ui` / `:service` / `:app`，不出现 Compose、Activity、Service、Receiver。已用 grep 验证当前不违反。

### `:ui` —— 通用 UI

- `component/`：跨 feature 控件，如 `VerticalTabLayout`、`TabItem`、`TextButton`、`FuelAmountInput`、`LiquidDropdown`、`SquareSwitch`。
- `presentation/ui/`：主题与基础样式，`PSAHelperTheme`、`AppTheme`、`PASThemeColor`、`Shape`、`Type`、`Glassmorphism`、`LiquidDialog`、`LiquidInputDialog`、`ThemeManager`（`@Singleton`，注入 app 提供的 `SharedPreferences`）。
- `presentation/dialog/`：跨 feature 复用的弹窗，如 `ToolBoxSettingsDialog`、`BootAutoStartDialog`、`CacheWhitelistDialog`、`PlayerSelectDialog`。
- `preview/`：组合预览。
- `res/drawable/`：功能图标 `ic_tab_settings`、`ic_fuel_ledger` 等。
- 不许放：业务逻辑、网络/文件/APK 处理、`Activity`/`Service`。

### `:service` —— 后台与服务

- `service/`：`PSAToolBoxService`、`HookBridgeService`、`ApkInstallService`、`HvacAccessibilityService`、`PicHttpServer`、`ToolBoxServiceWrapper`、`ServiceLauncher`。
- `receiver/`：`BootReceiver`、`AppInstallReceiver`、车厂广播收发（Carlife/Hicar）。
- `install/`：`ApkInstaller`（安装/卸载）、`ModApk`（读取 `hook.dex` 注入宿主）。
- `di/ServiceModule.kt`：`@Binds ToolBoxServiceWrapperImpl -> ToolBoxServiceWrapper`。
- `presentation/overlay/`：服务侧 Compose 悬浮窗，如 `FuelAmountOverlay`。
- 不许放：feature 页面与导航；UI 代码仅限 overlay（沿用 `:ui` 的主题与组件）。

### `:hook` —— 注入端

- 只有 `Hook.java` 与 `aidl/com/bandwa/hook/*.aidl`，不应用 Kotlin 插件（Java 8）。
- 不引用仓库其他类、不引用 `R`：它最终被打成 `hook.dex` 注入**宿主 app**，只能依赖 `android.*` 与 AIDL。
- API 与注入方式见 `hook/README.md`；修改 `Hook.java` 后需要重新打包目标 APK 才生效。

### 跨模块调用的三条实际规则

1. **UI 组件**：一律从 `:ui` 取（主题、弹窗、通用控件）。`gradle.properties` 开了 `android.nonTransitiveRClass=true`，引用 ui 资源须显式 `import com.bandwa.openadb.ui.R as UiR`，不能在 `:app` 里用 `com.bandwa.openadb.R` 找 ui 资源。
2. **服务启动**：用 `:service` 的 `ServiceLauncher`（`startProjection`、`showQRCode`），由它统一拉起 `PSAToolBoxService` / `PicHttpServerHost`。不要自己拼 `Intent` 直接 `startService`——`MainActivity` 协议弹窗里的直接 `startService(PSAToolBoxService)` 是现存例外，新增代码不要照抄。服务内交互用 `ToolBoxServiceWrapper`。
3. **安装/补丁**：安装与卸载走 `:service` 的 `ApkInstaller`；重打包/注入走 `ModApk`，不要自建 `PackageInstaller` 或另写注入流程。

## 3. 资源 / Manifest / DI / Assets 归属

| 内容 | 位置 | 说明 |
| --- | --- | --- |
| `AndroidManifest.xml` | `:app` 的 `src/main/AndroidManifest.xml` | 唯一真实清单：权限（`sharedUserId=android.uid.system`、`REBOOT`、`WRITE_SECURE_SETTINGS`、`SYSTEM_ALERT_WINDOW` 等）、Activity/Service/Receiver、无障碍 metadata 全部在此声明。`:hook` 只有空占位清单；`:core`/`:ui`/`:service` 无清单文件，由 AGP 合并生成。 |
| 应用图标 | `:app` `res/mipmap/ic_launcher.png` | Launcher 图标、应用内静态图（赞助 logo、二维码、贡献者头像）留在 app。 |
| 功能图标 | `:ui` `res/drawable/ic_*.xml` | 被 feature 共用，故放 ui；新增 Tab 图标放这里。 |
| 服务通知小图标 | `:service` `res/mipmap/ic_service_launcher.png` | `PSAToolBoxService.setSmallIcon(...)` 使用。 |
| 字符串/样式/颜色/无障碍配置 | `:app` `res/values`、`res/xml` | Manifest 引用的 `app_name`、`service_name`、`@style/AppTheme`、`@xml/accessibility_hvac_monitor` 都在 app。 |
| `assets/` | `:app/src/main/assets/` | `index.html`/`css`/`upload_*.html`、`ass.mp3`、`modapk/<pkg>/config.json`（注入配置）、`keystore.jks`（运行时签名）。 |
| `hook.dex` | 不提交；生成于 `:hook/build/outputs/hook/`，由 `syncHookDex` 复制到 `:app/build/generated/hookAssets/hook/` | `ModApk` 通过 `context.assets.open("hook/hook.dex")` 读取。 |
| 构建签名 | `:app/keystore.jks`（alias `bandwa`） | debug 与 release 共用同一签名配置（Google 测试平台签名）。 |
| Hilt 根 | `:app` `PSAHelperApplication` + `di/ApplicationModule.kt` | 全应用唯一 `@HiltAndroidApp`；`SharedPreferences` 在此 `@Singleton` 提供。 |
| 服务侧 DI | `:service/di/ServiceModule.kt` | `ToolBoxServiceWrapper` 绑定实现。 |
| 主题单例 | `:ui` `ThemeManager`（`@Singleton @Inject`） | 依赖 app 的 `SharedPreferences` 绑定，ui 不放 DI 模块。 |

补充：`BuildConfig` 只在需要时开启——`:app`、`:core`、`:service` 开启（`RELEASE_MODE` 等），`:ui`、`:hook` 关闭。新增模块若要读 `BuildConfig`，记得在 `buildFeatures` 打开。

## 4. 构建与验证

### 环境

- `JAVA_HOME` 指向 JDK 17（AGP 8.4.2 要求；各模块 `compileOptions`/`jvmTarget` 均为 17；`:hook` 调 d8 时也会把 `java.home` 传给子进程）。
- Android SDK Platform 34、Build-Tools 34.0.0、platform-tools。SDK 位置优先 `local.properties` 的 `sdk.dir`，未写时回退 `ANDROID_HOME`；`:hook` 靠它定位 `build-tools/34.0.0/d8(.bat)` 与 `platforms/android-34/android.jar`，缺失会直接 `check` 失败（`d8 not found` / `android.jar not found`）。
- Gradle 8.6（wrapper 指向腾讯镜像），构建脚本无 build-logic 约定插件，版本集中在 `gradle/libs.versions.toml`。
- 在线功能与统计的地址 / AppKey / 接口地址不在仓库内：根 `build.gradle.kts` 统一读取 `-Ppsa.otaBase` / `psa.infoBase` / `psa.umengKey` / `psa.shafaEndpoint`（对应环境变量 `PSA_OTA_BASE` / `PSA_INFO_BASE` / `PSA_UMENG_KEY` / `PSA_SHAFA_ENDPOINT`）与根目录 `local.properties`，再经 `BuildConfig` 传给 `:app`（`config/RemoteEndpoints` 拼接）与 `:service`（`InstallShafa`），缺失时对应功能自动禁用（见 README「部署配置」）。

### 常用命令（Windows 用 `gradlew.bat`）

```bat
gradlew :app:compileDebugKotlin                 :: 快速编译壳 + 全部依赖模块
gradlew :core:testDebugUnitTest :service:testDebugUnitTest :app:testDebugUnitTest
gradlew :app:assembleDebug
gradlew :app:assembleRelease                    :: minify + shrinkResources + R8
gradlew :hook:hookDex                           :: 只重建 hook.dex
gradlew :service:testDebugUnitTest -Dpatch.fixtures=<目录> -Dpatch.fixtures.limit=<n>
```

- 单测现状：`:core` 只有 `ApkDownloaderTest`；`:service` 有 `HvacWindowEventHubTest` 与 `PatchRegressionTest`（补丁回归，靠 `-Dpatch.fixtures` 提供样本，`:core`/`:service` 的 Test 任务已 `dependsOn(":hook:hookDex")` 并配 4g 堆）；`:app/src/test` 目前是空目录，`:app:testDebugUnitTest` 只是保持 CI 地位，没有用例。

### hook.dex 生成与同步链路

```
Hook.java + AIDL
  └─ :hook:compileDebugJavaWithJavac
       └─ :hook:hookClassesJar        (hook/build/outputs/jar/hook-classes.jar)
            └─ :hook:hookDex          d8 --min-api 24 --lib android.jar
                 └─ hook/build/outputs/hook/hook.dex
                      └─ :app:syncHookDex (Copy) → :app/build/generated/hookAssets/hook/hook.dex
                           └─ android.sourceSets.main.assets.srcDir(...)
                                └─ *Assets / lint 任务自动依赖 syncHookDex → 打进 APK
```

`hook.dex` 不入库；`:hook:assembleDebug` 也依赖 `hookDex`。运行时由 `ModApk` 从 assets 取出，注入宿主主 dex。

### CI

`.github/workflows/ci.yml`（唯一工作流）：`ubuntu-latest`、JDK 17 temurin、`setup-android` 后 `sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"`、Gradle 8.6；跑三模块单测 + `:app:assembleDebug`/`assembleRelease` + `tools` 模块 `assembleDebug`，再构建 android-car-boot 的 bootclasspath 客户端 jar（只验证链路），失败上传测试报告。本地提交前至少跑一遍 `:app:compileDebugKotlin` 与受影响模块的单测。

## 5. 新增 feature 的落地步骤

1. 在 `:app` 建 `presentation/main/<feature>/`，加 `XxxRoute.kt`、`XxxUIState.kt`、`XxxViewModel.kt`（VM 用 `@HiltViewModel`）。
2. 能复用的控件/主题放 `:ui`，不要复制到 feature 包里。
3. 纯逻辑（网络、文件、APK、系统设置）放 `:core`；需要常驻/广播/安装/overlay 放 `:service`。
4. 在 `MainActivity` 注册 `TabItem` 与路由；`binding` 这类共享区块可复用现有 `AppKeyBindingSection`。
5. 图标放 `:ui/res/drawable`，文案/样式放 `:app/res`。
6. 补单测：纯逻辑进 `:core`/`:service` 的 `test`；feature 交互放 `:app/src/test`（目前为空，首次新增即建目录）。
7. `gradlew :app:compileDebugKotlin` + 对应模块 `testDebugUnitTest`，再跑 `assembleDebug` 真机冒烟。

### 代码归属决策表

| 要写的东西 | 放哪 | 位置 |
| --- | --- | --- |
| 新页面 / 状态 / ViewModel | `:app` | `presentation/main/<feature>/` |
| 跨 feature 的 Compose 组件、主题、通用弹窗 | `:ui` | `component/`、`presentation/ui/`、`presentation/dialog/` |
| 功能图标（多 feature 共用） | `:ui` | `res/drawable/` |
| Shell/进程/文件/网络/系统设置等纯逻辑 | `:core` | `util/`、`util/apk/` |
| APK 签名、smali/dex 补丁、重打包 | `:core` | `util/apktools/` |
| 前台服务、广播、无障碍、悬浮窗、安装器、AIDL bridge | `:service` | `service/`、`receiver/`、`install/`、`presentation/overlay/` |
| 通知小图标 | `:service` | `res/mipmap/` |
| Hook 注入入口 / AIDL 回调 | `:hook` | `com.bandwa.hook` |
| Manifest 组件声明、权限、Application、DI 根、assets、签名 | `:app` | 见 §3 |

判断口诀：**只有一处用 → 就地放；两处以上复用 → 下沉；依赖 Android UI → 不回头依赖业务。** 依赖方向只能向下（app → service/ui → core → hook），禁止反向与环。

## 6. 已知取舍

- **包名基本未改**：模块 namespace 是 `com.bandwa.openadb.core/.ui/.service`，但源码包名仍大量沿用 `com.bandwa.openadb.util`、`.entity`、`.component`、`.presentation.*`，feature 也仍在 `:app`。目的是减少一次性改动、避免牵动 R 类、反射字符串、smali 补丁与序列化数据；代价是模块名与包名不完全对应，以本文为准。
- **`:app` 依赖全部模块**，feature 无法单独编译/测试；后续若 feature 变多，再拆 `:feature:*` 模块。
- **无 build-logic**：各模块 `build.gradle.kts` 有重复配置；根 `build.gradle.kts` 仍以 inline version 声明 AGP/Kotlin/kapt/Hilt，与 `libs.versions.toml` 的 `plugins` 段重复，后续统一成 alias。
- **targetSdk 25 是车机固件定制**，非上架应用商店：依赖 `android.uid.system`、旧 API 行为与签名安装，不要随手升 targetSdk；`lint` 已禁用 `ExpiredTargetSdkVersion`。
- **`android.car` 桩**：`tools/aosp-car-stub` 编译出的 jar 以 `compileOnly` 提供给 `:core`/`:service`；`:app` 额外 `implementation` 引入，使所有变体都打包桩类，避免非 automotive 设备缺类。真车与官方 AVD 底座（bootclasspath 里是真实 `android.car`）都是 parent-first 优先，桩不生效，行为不变。
- **hook.dex 只有一份**：`hookDex` 基于 `compileDebugJavaWithJavac`，release 包经 `syncHookDex` 复用同一份 debug 产物；hook 代码与构建类型无关，这是有意的简化。
- **签名单证书**：构建签名 `:app/keystore.jks`（debug/release 共用）与运行时签名 `:app/src/main/assets/keystore.jks`（`ApkSignUtil` 给重打包 APK 签名）是同一份 Google 测试平台签名证书的两个副本。
- **CI 只做编译 + 单测**：没有 ktlint/detekt/lint 门禁，`:app` 单测为空，覆盖主要靠 `:core`/`:service`；`:service` 的补丁回归测试需要 fixtures，CI 未传该参数；`:tools:android-car-boot:service` 只做 `assembleDebug` 编译检查，另构建 bootclasspath 客户端 jar 验证链路（不产出发布件）。
