package com.bandwa.openadb.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** 「应用」页的一项。 */
data class AppEntry(
    val label: String,
    val pkg: String,
    val versionName: String,
    val versionCode: Int,
    /** 排序用：lastUpdateTime 优先，其次 firstInstallTime。 */
    val whenMs: Long,
    val icon: Drawable?,
    /** 系统应用。 */
    val system: Boolean,
    /** 被覆盖安装过（真的能「卸载更新」）。 */
    val updated: Boolean,
)

/**
 * 「应用」页的名单与清理动作。
 *
 * 名单口径：固定 4 个（桌面 / 多媒体 / 高德 / 语音助手）先排好；其余只列 `FLAG_SYSTEM == 0`
 * 的第三方应用；再排除 [EXCLUDE] / [HIDDEN] 点名的包。
 */
object AppManage {

    /** 固定排在最前面的 4 个（顺序即显示顺序）。 */
    val FIXED = listOf(
        "com.android.launcher",
        "com.cusc.media",
        "com.autonavi.amapauto",
        "com.cusc.voiceassistant",
    )

    /** 永远显示「卸载更新」的包：桌面在本机是纯 /system 应用，按原判据不会出这个按钮。 */
    val ALWAYS_UPDATE = setOf("com.android.launcher")

    /** 额外排除（用户点名不显示）。 */
    val EXCLUDE = setOf("com.cusc.theme.style1")

    /** 选择列表里隐藏的包。 */
    val HIDDEN = setOf(
        "com.pro.multiscreen",
        "com.ivi.uwrite",
        "com.android.car.trust",
        "com.psa.automatetest",
        "com.cusc.execengine",
        "com.cusc.voiceassistant",
        "com.cusc.theme.style1",
    )

    /** 清缓存/清数据的等待上限（清完系统会重启目标进程，回调一般几百毫秒内到）。 */
    const val PM_CLEAR_TIMEOUT_MS = 20_000L

    enum class Sort { NAME, TIME }

    /** 扫描应用列表；阻塞（要读每个包的 PackageInfo/图标），只在后台线程调。 */
    fun scan(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val seen = HashSet<String>()
        val out = ArrayList<AppEntry>()

        for (pkg in FIXED) {
            val row = rowOf(pm, pkg) ?: continue
            if (seen.add(pkg)) out.add(row)
        }
        runCatching {
            pm.getInstalledApplications(0)?.forEach { ai ->
                val pkg = ai?.packageName
                if (pkg.isNullOrEmpty() || !seen.add(pkg)) return@forEach
                if (EXCLUDE.contains(pkg) || HIDDEN.contains(pkg)) {
                    seen.remove(pkg)
                    return@forEach
                }
                if ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0) {
                    seen.remove(pkg)
                    return@forEach
                }
                rowOf(pm, pkg)?.let { out.add(it) } ?: seen.remove(pkg)
            }
        }
        return out
    }

    fun sort(rows: List<AppEntry>, by: Sort): List<AppEntry> = when (by) {
        Sort.NAME -> rows.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        Sort.TIME -> rows.sortedWith(
            compareByDescending<AppEntry> { it.whenMs }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
        )
    }

    fun rowOf(pm: PackageManager, pkg: String?): AppEntry? {
        if (pkg.isNullOrEmpty()) return null
        return runCatching {
            val pi = pm.getPackageInfo(pkg, 0)
            val ai = pi.applicationInfo ?: return null
            AppEntry(
                label = runCatching { ai.loadLabel(pm).toString() }.getOrDefault(pkg),
                pkg = pkg,
                versionName = pi.versionName ?: "",
                versionCode = pi.versionCode,
                whenMs = if (pi.lastUpdateTime != 0L) pi.lastUpdateTime else pi.firstInstallTime,
                icon = runCatching { ai.loadIcon(pm) }.getOrNull(),
                system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                updated = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
            )
        }.getOrNull()
    }

    /** 这一行能不能「卸载」；[asUpdate] = 只是卸更新。 */
    fun uninstallKind(row: AppEntry): UninstallKind = when {
        row.updated || ALWAYS_UPDATE.contains(row.pkg) -> UninstallKind.UPDATE
        row.system -> UninstallKind.NONE
        else -> UninstallKind.APP
    }

    enum class UninstallKind { UPDATE, APP, NONE }

    /**
     * 用隐藏 API 清缓存（[cacheOnly]=true）或清数据。返回 null = 成功，否则是失败原因。
     *
     * `deleteApplicationCacheFiles(String, IPackageDataObserver)` 与
     * `clearApplicationUserData(String, IPackageDataObserver)` 都是 @hide；
     * `IPackageDataObserver` 只能用 Proxy 实现。阻塞至多 [PM_CLEAR_TIMEOUT_MS]。
     */
    fun clearHidden(ctx: Context, pkg: String, cacheOnly: Boolean): String? {
        val latch = CountDownLatch(1)
        val reported = AtomicBoolean(false)
        try {
            val obsCls = Class.forName("android.content.pm.IPackageDataObserver")
            val obs = java.lang.reflect.Proxy.newProxyInstance(
                obsCls.classLoader, arrayOf(obsCls)
            ) { _, m, args ->
                if (m.name == "onRemoveCompleted") {
                    if (args != null && args.size >= 2 && args[1] is Boolean) {
                        reported.set(args[1] as Boolean)
                    }
                    latch.countDown()
                }
                null
            }
            val pm = ctx.packageManager
            val want = if (cacheOnly) "deleteApplicationCacheFiles" else "clearApplicationUserData"
            var target: java.lang.reflect.Method? = null
            var callArgs: Array<Any>? = null
            for (m in PackageManager::class.java.methods) {
                if (m.name != want) continue
                val ps = m.parameterTypes
                if (ps.size == 2 && ps[0] == String::class.java) {
                    target = m
                    callArgs = arrayOf(pkg, obs)
                    break
                }
                if (ps.size == 3 && ps[0] == String::class.java && ps[1] == Int::class.javaPrimitiveType) {
                    target = m
                    callArgs = arrayOf(pkg, 0, obs)
                    break
                }
            }
            if (target == null || callArgs == null) {
                return "这个 ROM 的 PackageManager 里没有 $want（反射拿不到）"
            }
            val ret = target.invoke(pm, *callArgs)
            val sync = ret is Boolean && ret
            if (latch.await(PM_CLEAR_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return if (reported.get()) null else "系统回调报告失败（onRemoveCompleted succeeded=false）"
            }
            // deleteApplicationCacheFiles 的返回值本身即「请求已受理」；clearApplicationUserData
            // 是 void ⇒ 只能如实说没收到结果（调用方会再走 shell 回退）。
            return if (sync) null else "未收到结果（${PM_CLEAR_TIMEOUT_MS / 1000}s 内没有回调）"
        } catch (t: Throwable) {
            return "隐藏 API 调用失败：$t"
        }
    }

    /** shell 回退：`pm clear --cache-only` / `pm clear`。返回 null = 成功。 */
    fun clearByShell(pkg: String, cacheOnly: Boolean): String? {
        val cmd = if (cacheOnly) "pm clear --cache-only $pkg" else "pm clear $pkg"
        val r = ShellUtils.exec(cmd)
        val out = r.output.trim()
        val bad = !r.isSuccess || listOf("failure", "error", "exception", "denied", "not found")
            .any { out.contains(it, ignoreCase = true) }
        return if (bad) "shell 失败：${out.take(200)}" else null
    }

    /** 清缓存/清数据：先系统隐藏 API，失败再走 shell。返回 null = 成功。 */
    fun clear(ctx: Context, pkg: String, cacheOnly: Boolean): String? {
        val err = clearHidden(ctx, pkg, cacheOnly)
        if (err == null) return null
        return clearByShell(pkg, cacheOnly)
    }
}
