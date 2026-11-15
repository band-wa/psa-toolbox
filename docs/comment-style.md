# 注释规范

本项目采用**极简注释**：注释只写代码本身表达不了的信息，宁可不写，也不写废话。

约定参考了 Google Java Style Guide、Kotlin Coding Conventions、Linux Kernel Coding Style、Rust API Guidelines 等成熟项目的注释章节，并结合车机系统代码的特点（系统权限、HAL 线程约束、固件适配、刷机风险）收敛为以下规则。

参考：

- [Google Java Style Guide — Comments](https://google.github.io/styleguide/javaguide.html#s4.1-comments)
- [Kotlin Coding Conventions — Comments](https://kotlinlang.org/docs/coding-conventions.html#comments)
- [Linux Kernel Coding Style — Commenting](https://www.kernel.org/doc/html/latest/process/coding-style.html#commenting)
- [Rust API Guidelines — Documentation](https://rust-lang.github.io/api-guidelines/documentation.html)

## 只保留四类注释

1. **契约**：对外或跨模块 API 的非显然行为、副作用、失败语义（用 KDoc）。
2. **约束与原因**：系统限制、线程/生命周期要求、兼容性或权限前提、必须保持的写法。
3. **警告**：可能导致数据损坏、安装失败、系统异常或安全风险的实现。
4. **魔法值**：VHAL 属性 ID、Settings key、Intent action/extra、协议字段等名称无法自解释的常量。

## 禁止

- 复述代码（`// 设置标题`、`// 创建对象`）。
- 分段横幅与装饰线（`// ==== 投屏 ==== `、`// ── xxx ──`）。
- 历史与重构叙述（此前、原来、改为、统一后、重构前）。
- 作者、日期、版权、变更日志。
- 注释掉的代码。
- 无追踪的 TODO（确需保留时写成 `// TODO(name): 原因`）。
- 空泛 KDoc（类名或函数名已经说明的内容）。

## 书写格式

- 中文；`//` 后跟一个空格。
- 行注释放在被说明语句的上一行；魔法常量可放同一行行尾。
- 对外 API、核心类才用 KDoc：一句话讲清契约，必要时再补一行约束；不写 `@param`、`@return`、`@author`、`@since`。
- 短 KDoc 用一行：`/** ... */`；多行时长说明独立成行。
- 单个文件通常 **0 处**注释；只有关键契约/约束才加，单处不超过 2 行。
- 行宽 ≤ 100 字符。

## 示例

```kotlin
/** APK 安装/卸载入口：start 只发起，install/uninstall 等待回执，downloadAndInstall 下载校验后安装。 */
object ApkInstaller

// 不能加 FLAG_IMMUTABLE：系统通过 fillIn 回传安装结果 extras
val pendingIntent = PendingIntent.getBroadcast(...)
```
