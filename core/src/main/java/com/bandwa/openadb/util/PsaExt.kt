package com.bandwa.openadb.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.TextUtils

/**
 * 按键映射（原按键映射软件）并进来之后需要的那几个「车机侧小开关」与身份信息。
 *
 * 这里只放**跨页面共用**的东西：维护诊断用它读运行状态，关于软件用它改开机行为。
 * 与 `com.psa.keyconfig` 原实现保持同一套语义，键名/属性名都没改，便于两边互认。
 */
object PsaExt {

    /** 开机自动开 WiFi。公共服务的 `psaBoot` 读它：空 / "1" ⇒ 开，"0" ⇒ 不动。 */
    const val PROP_BOOT_WIFI = "persist.psa.boot.wifi"

    /** 车机自带的工程模式界面（`com.cusc.misc` 里的 Activity）。 */
    const val MISC_DEBUG_ACTIVITY = "com.cusc.misc.view.activity.DebugActivity"

    const val PKG_MISC = "com.cusc.misc"
    const val PKG_MEDIA = "com.cusc.media"
    const val PKG_LAUNCHER = "com.android.launcher"

    private const val PREF = "psa_toolbox_ext"
    private const val K_ENGINEER = "engineer_entry"
    private const val K_FULLSCREEN = "fullscreen"
    private const val K_ENGINEER_TAPS = "engineer_taps"

    /** 解锁工程模式入口需要连点的次数。 */
    const val ENGINEER_TAPS = 10

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /* ---------------- 开机 WiFi ---------------- */

    /** 默认开：属性非 "0" 就算开。 */
    fun bootWifiOn(): Boolean = !"0".equals(Utils.getSystemProp(PROP_BOOT_WIFI).ifEmpty { "1" })

    /** 写属性并回读校验；返回 false = 这台车机不让写。 */
    fun setBootWifi(on: Boolean): Boolean {
        val v = if (on) "1" else "0"
        Utils.setSystemProp(PROP_BOOT_WIFI, v)
        return Utils.getSystemProp(PROP_BOOT_WIFI) == v
    }

    /* ---------------- 界面全屏 ---------------- */

    /**
     * 本应用自己的全屏偏好（默认关）。
     *
     * 默认关的原因：车机左侧有一条约 128px 的系统停靠栏，全屏会把它遮掉。
     */
    fun fullscreenWanted(ctx: Context): Boolean =
        runCatching { prefs(ctx).getBoolean(K_FULLSCREEN, false) }.getOrDefault(false)

    fun setFullscreen(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(K_FULLSCREEN, on).apply()
    }

    /* ---------------- 工程模式入口 ---------------- */

    fun engineerUnlocked(ctx: Context): Boolean =
        runCatching { prefs(ctx).getBoolean(K_ENGINEER, false) }.getOrDefault(false)

    /**
     * 「关于软件」里连点版本号：每次加一，到 [ENGINEER_TAPS] 次解锁。
     *
     * @return 本次点击后是否**刚好**解锁（界面据此给一次提示）。
     */
    fun tapVersion(ctx: Context): Boolean {
        val p = prefs(ctx)
        val n = p.getInt(K_ENGINEER_TAPS, 0) + 1
        if (n >= ENGINEER_TAPS) {
            p.edit().putBoolean(K_ENGINEER, true).putInt(K_ENGINEER_TAPS, 0).apply()
            return true
        }
        p.edit().putInt(K_ENGINEER_TAPS, n).apply()
        return false
    }

    fun lockEngineer(ctx: Context) {
        prefs(ctx).edit().putBoolean(K_ENGINEER, false).putInt(K_ENGINEER_TAPS, 0).apply()
    }

    /** 还剩几次能解锁（0 = 已解锁或已到点）。 */
    fun engineerTapsLeft(ctx: Context): Int =
        if (engineerUnlocked(ctx)) 0
        else (ENGINEER_TAPS - prefs(ctx).getInt(K_ENGINEER_TAPS, 0)).coerceAtLeast(0)

    /** 打开车机自带工程模式；失败返回 false（有些车型没有这个 Activity）。 */
    fun openEngineer(ctx: Context): Boolean = runCatching {
        ctx.startActivity(
            Intent().apply {
                setClassName(PKG_MISC, MISC_DEBUG_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
        true
    }.getOrDefault(false)

    /* ---------------- 身份信息 ---------------- */

    /** 一行的身份摘要：车型 / 版本 / 三个核心件的版本。 */
    fun identityLine(ctx: Context): String = buildString {
        append(model())
        append(" · ")
        append(androidVersion())
    }

    fun model(): String {
        val m = Utils.getSystemProp("ro.product.model").ifEmpty { Build.MODEL }
        return if (TextUtils.isEmpty(m)) "未知车型" else m
    }

    fun androidVersion(): String {
        val flavor = Utils.getSystemProp("ro.build.version.flavor")
        val rel = Utils.getSystemProp("ro.build.version.release").ifEmpty { Build.VERSION.RELEASE }
        val sdk = Utils.getSystemProp("ro.build.version.sdk").ifEmpty { Build.VERSION.SDK_INT.toString() }
        return if (flavor.isEmpty()) "Android $rel（API $sdk）" else "Android $flavor（API $sdk）"
    }

    /** 显示串（车机上通常是 `3VS222_SW6.0.0_…` 这种）。 */
    fun buildDisplay(): String {
        val d = Utils.getSystemProp("ro.build.display.id")
        return if (d.isEmpty()) Utils.getSystemProp("ro.build.id").ifEmpty { "-" } else d
    }

    /** 车架号 / 序列号（脱敏前的原文，只在本机显示）。 */
    fun serial(): String {
        for (k in arrayOf("persist.sys.serialno", "ro.serialno", "persist.sys.vincode")) {
            val v = Utils.getSystemProp(k)
            if (v.isNotEmpty()) return v
        }
        return "-"
    }

    /** `包名 versionName (versionCode)`；没装返回「未安装」。 */
    fun ver(ctx: Context, pkg: String): String = runCatching {
        val pi = ctx.packageManager.getPackageInfo(pkg, 0)
        "${pi.versionName} (${pi.versionCode})"
    }.getOrDefault("未安装")

    /** 身份卡正文（多行）。 */
    /**
     * 关于页要显示的信息：只列 PSA工具箱 会去改的那几个组件。
     * 车型 / 系统版本 / 编译号 / 序列号 属于车机自身，用户 2026-11-16 明确要求删掉。
     */
    fun identityText(ctx: Context): String = buildString {
        append("公共服务：").append(ver(ctx, PKG_MISC)).append('\n')
        append("多媒体：").append(ver(ctx, PKG_MEDIA)).append('\n')
        append("桌面：").append(ver(ctx, PKG_LAUNCHER))
    }
}
