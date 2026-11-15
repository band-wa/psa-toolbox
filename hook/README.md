# hook 模块

给被修改的第三方/系统应用注入一段可复用的 Java 代码，避免在 `config.json` 里手写大段 smali。

## 结构

```
hook/
├── build.gradle.kts                     # d8 打包任务 hookDex
└── src/main/
    ├── aidl/com/bandwa/hook/            # 与工具箱通信的 AIDL
    └── java/com/bandwa/hook/Hook.java   # 注入端唯一入口
```

构建时 `:hook:hookDex` 会产出 `hook.dex`，`app` 模块把它同步到
`app/build/generated/hookAssets/hook/hook.dex` 并作为 asset 打进工具箱 APK。
补丁流程（`ModApk` → `FastApkPacker`）读取该 dex，把其中的类一起写入目标 APK 的主 dex，
因此被修改的应用无需额外加载器即可直接调用 `com.bandwa.hook.Hook`。

## 在补丁中注入

`app/src/main/assets/modapk/<包名>/config.json` 里用一两行 smali 调用即可，例如：

```json
{
  "filePath": "smali/com/cusc/misc/MiscApplication.smali",
  "operations": [
    {
      "type": "replaceValue",
      "oldValue": "invoke-super {p0}, Landroid/app/Application;->onCreate()V",
      "newValue": "invoke-super {p0}, Landroid/app/Application;->onCreate()V\n\n    invoke-static {p0}, Lcom/bandwa/hook/Hook;->install(Landroid/content/Context;)V"
    }
  ]
}
```

按键分发只需要（`v1` 为属性名，`v2/v3` 为原厂包名与类名）：

```
invoke-static {v1, v2, v3}, Lcom/bandwa/hook/Hook;->launchByProp(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V
```

## API

| 方法 | 说明 |
| --- | --- |
| `install(Context)` | 幂等初始化，记录 Context 并连接工具箱服务 |
| `getProp(String key, String def)` | 反射读取 `SystemProperties` |
| `getConfig(String key, String def)` | 从工具箱读取配置（跨进程） |
| `isHvacForeground()` | 空调应用是否在前台（工具箱推送，连接时自动同步） |
| `isBcallRunning()` | 原厂 `tbox.bcall.running` 或空调前台，供按键分发判断“通话中” |
| `reportEvent(String event, String payload)` | 上报事件到工具箱 |
| `launchByProp(String propKey, String defPackage, String defClass)` | 连接工具箱时按属性值启动；未连接时启动原厂组件 `defPackage/defClass` |
| `launchByProp(Context, String, String, String)` | 同上，显式传 Context |
| `launchComponent(Context, String, String)` | 启动指定 `包名/类名` |
| `launchPackage(Context, String)` | 启动指定包名 |
| `addListener(Listener)` / `removeListener` | 接收工具箱下发的事件 |

所有入口都自带 `try/catch (Throwable)`，注入失败只会打日志，不会让宿主崩溃。

## 无工具箱时回退原厂行为

Hook 只在与工具箱建立连接后接管逻辑：

- `launchByProp`：`bridge == null` 时不读属性，直接启动配置里的原厂组件；属性为空同样回退原厂组件。
- `isHvacForeground()`：未连接（或连接断开）时为 `false`，等同于原厂读不到标记。
- `isBcallRunning()`：先读原厂属性 `tbox.bcall.running`，再与空调前台状态取或；本工具不写原厂属性。
- `getConfig()` 返回默认值，`reportEvent()` 直接丢弃。
- 工具箱被卸载后不再重试绑定（`toolboxInstalled()` 检查）；仅工具箱进程重启时会自动重连。

## 与工具箱通信

`IHookBridge`（工具箱实现）：

- `getConfig(key, defValue)`：读取工具箱 SharedPreferences
- `reportEvent(event, payload)`：写入工具箱日志
- `registerCallback` / `registerCallbackEx(pkg, version, callback)` / `unregisterCallback`：订阅工具箱事件（如 `projection_started`），`Ex` 版本同时把宿主包名和 hook 版本登记到工具箱

`IHookCallback`（hook 端实现）：`onEvent(event, payload)`。

连接成功后 hook 会主动上报一次 `hook_ready`，内容为 `<版本>@<宿主包名>`，
可在 logcat 中用 `BandwaHook` 或工具箱日志确认注入是否生效。

工具箱用这份运行时信息判断核心件是否已安装、版本是否最新（`HookState`），
不再依赖 SharedPreferences 里的版本标记；`Hook.VERSION` 是唯一版本来源，
修改 hook 或补丁内容时递增它即可让旧补丁被识别为过期。工具箱进程重启时，
hook 会自动重连并重新登记。

## 注意

- 注入类写在目标 APK 的主 dex，注意 64K 方法数上限。
- 类名固定为 `com.bandwa.hook.*`，不要与目标应用已有类重名。
- `hook.dex` 以 asset 形式存放，R8 不会处理它；修改 `Hook.java` 后需要重新打包目标应用才会生效。
- 复杂逻辑优先写在 `Hook` 里，`config.json` 只保留一到两行调用。
