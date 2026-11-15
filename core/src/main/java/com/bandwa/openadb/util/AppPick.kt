package com.bandwa.openadb.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable

/**
 * 一条可选的映射目标。
 *
 * [target] 是**最终写进属性的那个字符串**：包名，或 `包名/Activity全名`
 * —— 一个包装了多个入口时（比如公共服务的 AVM）必须点名 Activity 才不会启错。
 */
data class PickApp(
    val label: String,
    val target: String,
    val icon: Drawable?,
) {
    companion object {
        /** 把属性里的现值解析回可显示的一行；空串与 `none` 返回 null（= 走原厂）。 */
        fun of(context: Context, value: String): PickApp? {
            val target = value.trim()
            if (target.isEmpty() || target == "none") return null
            val pkg = target.substringBefore('/')
            val pm = context.packageManager
            val label = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault(pkg)
            val icon = runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
            return PickApp(label, target, icon)
        }
    }
}

/**
 * 可选应用名单 —— 口径**照抄按键映射软件**的 `buildFullAppList`。
 *
 * 为什么不能只查 `ACTION_MAIN + CATEGORY_LAUNCHER`：车机版高德、AVM 这类没有标准
 * launcher 入口，只查 launcher 会漏；反过来一堆系统内部件（执行引擎、自动化测试、
 * 主题包）会混进来。所以照抄它的三段口径：launcher 结果 - 黑名单 + 补全 + 白名单放行。
 */
object AppPick {
    /** 永远不进候选列表的包（用户点名的内部件）。 */
    val HIDDEN = setOf(
        "com.pro.multiscreen",
        "com.ivi.uwrite",
        "com.android.car.trust",
        "com.psa.automatetest",
        "com.cusc.execengine",
        "com.cusc.voiceassistant",
        "com.cusc.theme.style1",
    )

    /** 带 `FLAG_SYSTEM` 但确实要能选的自定义系统应用。 */
    val ALLOW_SYSTEM = setOf("com.xiaozhi.voice")

    /** 「回到桌面」固定写这个组件串（原厂 launcher 的真实入口）。 */
    const val HOME_TARGET = "com.android.launcher/com.android.launcher2.Launcher"

    /** 小智语音助手绑 WakeActivity 才等于「点悬浮窗说话」；绑主界面只拉起服务。 */
    private const val XIAOZHI_PKG = "com.xiaozhi.voice"
    private const val XIAOZHI_TARGET = "$XIAOZHI_PKG/$XIAOZHI_PKG.WakeActivity"

    /**
     * 候选列表：第一项固定是「回到桌面」，其余按名字排序。
     *
     * 会遍历已装应用，**别在主线程调**。
     */
    fun list(context: Context): List<PickApp> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val found = pm.queryIntentActivities(launcherIntent, 0)
            .filter { it.activityInfo.packageName !in HIDDEN }
            .toMutableList()

        val covered = found.mapTo(HashSet()) { it.activityInfo.packageName }
        for (ai in pm.getInstalledApplications(0)) {
            val pkg = ai.packageName
            if (pkg in HIDDEN || pkg in covered) continue
            val system = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (system && !pkg.startsWith("com.psa.") && !pkg.startsWith("com.cusc.") &&
                pkg !in ALLOW_SYSTEM
            ) {
                continue
            }
            val entry = launchEntry(pm, pkg) ?: continue
            found.add(entry)
            covered.add(pkg)
        }

        found.sortBy { runCatching { it.loadLabel(pm).toString().lowercase() }.getOrDefault("") }

        val out = ArrayList<PickApp>(found.size + 1)
        out.add(
            PickApp(
                label = "回到桌面",
                target = HOME_TARGET,
                icon = runCatching { pm.getApplicationIcon("com.android.launcher") }.getOrNull(),
            )
        )
        for (ri in found) out.add(toPick(pm, ri))
        return out
    }

    /** 优先标准 launch intent（且 Activity 必须 exported），否则找第一个 ACTION_MAIN + exported。 */
    private fun launchEntry(pm: PackageManager, pkg: String): ResolveInfo? {
        val li = pm.getLaunchIntentForPackage(pkg)
        val comp = li?.component
        if (comp != null) {
            val act = runCatching { pm.getActivityInfo(comp, 0) }.getOrNull()
            if (act != null && act.exported) {
                return ResolveInfo().also { it.activityInfo = act }
            }
        }
        val probe = Intent(Intent.ACTION_MAIN).setPackage(pkg)
        return pm.queryIntentActivities(probe, 0).firstOrNull { it.activityInfo.exported }
    }

    private fun toPick(pm: PackageManager, ri: ResolveInfo): PickApp {
        val info = ri.activityInfo
        val pkg = info.packageName
        val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault(pkg)
        val icon = runCatching { ri.loadIcon(pm) }.getOrNull()
        val defaultClass = pm.getLaunchIntentForPackage(pkg)?.component?.className
        val target = when {
            pkg == XIAOZHI_PKG -> XIAOZHI_TARGET
            defaultClass != info.name -> "$pkg/${info.name}"
            else -> pkg
        }
        return PickApp(label, target, icon)
    }
}
