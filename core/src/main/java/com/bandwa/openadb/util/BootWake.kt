package com.bandwa.openadb.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.util.Log

/**
 * 开机后台唤醒：让选中的应用"已经在跑"，但**一次界面都不显示**。
 *
 * 只用两条路，都不产生界面：
 * 1. startService 目标应用自己导出的 Service（优先 MediaBrowserService，音乐类应用都实现了它）；
 * 2. 一个可启动的 Service 都没有时，绑定一次它导出的 ContentProvider 再立刻释放 —— provider 的
 *    onCreate 同样会把进程拉起来，同样零界面。
 *
 * 两条都走不通就如实返回原因，不做假成功。
 *
 * 为什么能后台 startService：本应用与公共服务同为 android.uid.system（sharedUserId），
 * system uid 不受「后台启动 Service」限制。
 *
 * 能保证什么：开机后这些应用已经起来过，进程在内存里（dumpsys activity processes 能看到 cached 记录），
 * 第一次按键不会冷启动卡一下。不能保证常驻 —— 内存紧时系统照样回收。
 *
 * 替代原先 BootReceiver 里的 startActivity 循环：那条路会真的把界面弹出来闪一下，不叫后台。
 */
object BootWake {

    private const val TAG = "BootWake"

    private val PFX_PLAYER = arrayOf("player", "music", "playback", "media")

    /** 唤醒一个包。返回给界面/日志看的中文结果，失败也如实返回原因。 */
    fun wakeOne(ctx: Context, pkg: String): String {
        val pm = ctx.packageManager

        // 先试导出的 Service。
        val cand = servicesOf(pm, pkg)
        if (cand.isNotEmpty()) {
            val svc = pickService(cand)
            if (svc != null) {
                return runCatching {
                    val i = Intent().apply {
                        component = android.content.ComponentName(pkg, svc.name)
                    }
                    @Suppress("DEPRECATION")
                    ctx.startService(i)
                    "已启动服务 ${simpleName(svc.name)}"
                }.getOrElse { "启动服务失败（${it.javaClass.simpleName}: ${it.message}）" }
            }
        }

        // 退路：绑一次 ContentProvider。
        val prov = providersOf(pm, pkg).firstOrNull()
        if (prov != null) {
            return runCatching {
                val client = ctx.contentResolver.acquireContentProviderClient(Uri.parse("content://${prov.authority}"))
                if (client != null) {
                    client.release()
                    "已通过 ContentProvider 拉起（${prov.authority}）"
                } else {
                    "ContentProvider 没拿到引用（${prov.authority}）"
                }
            }.getOrElse { "ContentProvider 拉起失败（${it.javaClass.simpleName}: ${it.message}）" }
        }

        return "这个应用没有可后台启动的服务或 ContentProvider（只能靠打开界面，已跳过）"
    }

    /** 按名单逐个唤醒。**必须在后台线程调用**（要跨进程查 PackageManager + startService）。 */
    fun wakeAll(ctx: Context, pkgs: Collection<String>, onEach: ((String, String) -> Unit)? = null) {
        for (pkg in pkgs.sorted()) {
            val r = runCatching { wakeOne(ctx, pkg) }.getOrElse { "异常：${it.javaClass.simpleName}" }
            Log.i(TAG, "bootWake: $pkg → $r")
            onEach?.invoke(pkg, r)
        }
    }

    private fun servicesOf(pm: PackageManager, pkg: String): List<ServiceInfo> = runCatching {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(pkg, PackageManager.GET_SERVICES).services?.filter { it.exported } ?: emptyList()
    }.getOrDefault(emptyList())

    private fun providersOf(pm: PackageManager, pkg: String) = runCatching {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(pkg, PackageManager.GET_PROVIDERS).providers
            ?.filter { it.exported && !it.authority.isNullOrEmpty() }
            ?: emptyList()
    }.getOrDefault(emptyList())

    /** 优先 MediaBrowserService，其次名字像播放器的，最后取任意一个。 */
    private fun pickService(all: List<ServiceInfo>): ServiceInfo? {
        all.firstOrNull { it.name.contains("MediaBrowserService") }?.let { return it }
        all.firstOrNull { s -> PFX_PLAYER.any { s.name.lowercase().contains(it) } }?.let { return it }
        return all.firstOrNull()
    }

    private fun simpleName(fqcn: String) = fqcn.substringAfterLast('.')
}
