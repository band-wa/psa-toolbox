package com.bandwa.openadb.util

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 内存清理（一级 killBackground / 二级 forceStop）。
 *
 * 判定与显示统一「可用」口径：`可用% = availMem / totalMem`，`availMem` 与 `/proc/meminfo
 * MemAvailable` 交叉核对取更悲观者。**不要改用「物理空闲」当判据** —— page cache 可回收，
 * 用它会让触发线常年为真、每 5 分钟白杀一次进程。
 *
 * [run] 是阻塞的（每包 250ms 复测 + 收尾采样），只在后台线程调。
 */
object MemGuard {

    private const val TAG = "PSA.MemGuard"

    /** 本功能自己的 SharedPreferences，不复用别的键。 */
    private const val PREF = "psa_memguard"

    private const val K_ON = "on"
    private const val K_AVAIL_FLOOR = "availFloor"
    private const val K_LEVEL2 = "level2"
    private const val K_LIST = "list"
    private const val K_LAST_AT = "lastAt"
    private const val K_LAST_TIME = "lastTime"
    private const val K_LAST_TRIGGER = "lastTrigger"
    private const val K_LAST_LEVEL = "lastLevel"
    private const val K_LAST_PCT = "lastPct"
    private const val K_LAST_DELTA = "lastDeltaKb"
    private const val K_LAST_KILLED = "lastKilled"
    private const val K_LAST_RESULT = "lastResult"

    /** 两次**自动**清理的最小间隔，避免抖动式反复杀。 */
    private const val COOLDOWN_MS = 5L * 60L * 1000L

    /** 触发线：**可用内存低于这个百分比**才自动清理（默认 10 ⇔ 占用超过 90%）。 */
    const val DEFAULT_AVAIL_FLOOR = 10

    /** 应用 uid 起始值 `android.os.Process.FIRST_APPLICATION_UID`。 */
    private const val FIRST_APP_UID = 10000

    /** 默认名单：只放明确是大户且可牺牲的。 */
    private val DEFAULT_LIST = listOf("com.autonavi.amapauto")

    /** 保护名单（精确 / 前缀）；这些包一律不清理。 */
    private val NEVER_PREFIX = arrayOf("com.android.", "com.cusc.", "com.xiaozhi.")

    /** 候选里额外补的常用大户（已安装才出现）。 */
    private val SUGGEST = arrayOf("com.autonavi.amapauto", "com.netease.cloudmusic.iot", "com.cusc.appanel")

    fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun enabled(ctx: Context): Boolean = runCatching { prefs(ctx).getBoolean(K_ON, true) }.getOrDefault(true)

    fun setEnabled(ctx: Context, on: Boolean) {
        runCatching { prefs(ctx).edit().putBoolean(K_ON, on).apply() }
    }

    /** 触发线（**可用内存百分比**）。 */
    fun availFloor(ctx: Context): Int = runCatching {
        clampFloor(prefs(ctx).getInt(K_AVAIL_FLOOR, DEFAULT_AVAIL_FLOOR))
    }.getOrDefault(DEFAULT_AVAIL_FLOOR)

    private fun clampFloor(v: Int): Int = v.coerceIn(1, 60)

    fun setAvailFloor(ctx: Context, floorPct: Int) {
        runCatching { prefs(ctx).edit().putInt(K_AVAIL_FLOOR, clampFloor(floorPct)).apply() }
    }

    /** 等价的「占用」阈值，只用于文案对照。 */
    fun thresholdByOccupancy(ctx: Context): Int = 100 - availFloor(ctx)

    fun triggerLine(ctx: Context): String = "可用低于 ${availFloor(ctx)}% 时清理"

    fun level2(ctx: Context): Boolean = runCatching { prefs(ctx).getBoolean(K_LEVEL2, false) }.getOrDefault(false)

    fun setLevel2(ctx: Context, on: Boolean) {
        runCatching { prefs(ctx).edit().putBoolean(K_LEVEL2, on).apply() }
    }

    /** 清理名单（已过滤保护名单）。 */
    fun list(ctx: Context): List<String> {
        val raw = runCatching { prefs(ctx).getString(K_LIST, null) }.getOrNull()
            ?: return DEFAULT_LIST.toList()
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() && !isNever(it) }.distinct()
    }

    fun setList(ctx: Context, pkgs: List<String>) {
        val joined = pkgs.map { it.trim() }.filter { it.isNotEmpty() && !isNever(it) }.joinToString(",")
        runCatching { prefs(ctx).edit().putString(K_LIST, joined).apply() }
    }

    fun listText(ctx: Context): String {
        val l = list(ctx)
        return if (l.isEmpty()) "（空：不会清理任何包）" else l.joinToString("、")
    }

    fun isNever(pkg: String?): Boolean {
        if (pkg.isNullOrEmpty()) return true
        if (pkg == "com.psa.keyconfig") return true
        return NEVER_PREFIX.any { pkg.startsWith(it) }
    }

    fun neverReason(pkg: String?): String = when {
        pkg.isNullOrEmpty() -> "空"
        pkg == "com.cusc.voiceassistant" -> "语音助手（另一条产品线，禁止清理）"
        pkg.startsWith("com.cusc.") -> "车机 OEM 应用（com.cusc.*，车机功能）"
        pkg.startsWith("com.android.") -> "Android 系统应用"
        pkg.startsWith("com.xiaozhi.") -> "小智（另一条产品线）"
        else -> "系统/保护名单"
    }

    /** 一次内存快照（KB + 百分比）。 */
    class Level {
        var totalKb = 0L
        /** 可用（MemoryInfo.availMem 与 MemAvailable 取更悲观）。 */
        var availKb = -1L
        /** 物理空闲（MemFree）—— 含可回收缓存，只作参考。 */
        var freeKb = -1L
        /** **可用%**（判定用）。 */
        var availPct = -1
        /** 占用% = 100 − 可用%，与运行状态卡同一口径。 */
        var usedPct = -1
        /** 物理占用%（含可回收缓存），次要参考。 */
        var physUsedPct = -1
        var lowMemory = false
        var src = "-"
    }

    fun level(ctx: Context): Level {
        val lv = Level()
        val mi = memoryInfo(ctx, lv)
        val pm = procMemInfo()
        var total = 0L
        var avail = -1L
        var free = -1L
        var src = "-"
        if (mi != null && mi[0] > 0L) {
            total = mi[0]
            avail = mi[1]
            src = "MemoryInfo"
        }
        if (pm != null) {
            if (total <= 0L) {
                total = pm[0]
                src = "meminfo"
            }
            free = pm[2]
            if (pm[1] >= 0L) {
                avail = if (avail < 0L) pm[1] else minOf(avail, pm[1])
                src = "$src+meminfo"
            }
        }
        lv.totalKb = total
        lv.availKb = avail
        lv.freeKb = free
        lv.src = src
        if (total > 0L) {
            if (avail >= 0L) {
                lv.availPct = pct(avail, total)
                if (lv.availPct >= 0) lv.usedPct = 100 - lv.availPct
            }
            if (free >= 0L) {
                val fp = pct(free, total)
                if (fp >= 0) lv.physUsedPct = 100 - fp
            }
        }
        return lv
    }

    private fun pct(used: Long, total: Long): Int {
        if (total <= 0L) return -1
        return Math.round(used * 100.0 / total).toInt().coerceIn(0, 100)
    }

    private fun memoryInfo(ctx: Context, lv: Level?): LongArray? = try {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am == null) null else {
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            lv?.lowMemory = mi.lowMemory
            if (mi.totalMem <= 0L) null else longArrayOf(mi.totalMem / 1024L, mi.availMem / 1024L)
        }
    } catch (t: Throwable) {
        null
    }

    /** `/proc/meminfo` → `{MemTotal, MemAvailable, MemFree}`（KB）。 */
    private fun procMemInfo(): LongArray? {
        var r: BufferedReader? = null
        try {
            r = BufferedReader(FileReader("/proc/meminfo"))
            var total = -1L
            var avail = -1L
            var free = -1L
            var n = 0
            while (n < 6) {
                val line = r.readLine() ?: break
                when {
                    line.startsWith("MemTotal:") -> total = kb(line)
                    line.startsWith("MemAvailable:") -> avail = kb(line)
                    line.startsWith("MemFree:") -> free = kb(line)
                }
                n++
            }
            if (total > 0L) return longArrayOf(total, avail, free)
        } catch (t: Throwable) {
        } finally {
            close(r)
        }
        return null
    }

    private fun kb(line: String): Long = try {
        val p = line.trim().split(Regex("\\s+"))
        if (p.size < 2) -1L else p[1].toLong()
    } catch (t: Throwable) {
        -1L
    }

    /** KB → MB/GB（与 [SysInfo.size] 同口径）。 */
    fun mb(kb: Long): String {
        if (kb < 0L) return "-"
        return if (kb >= 1048576L) String.format(Locale.US, "%.2f GB", kb / 1048576.0)
        else String.format(Locale.US, "%.0f MB", kb / 1024.0)
    }

    /** 卡顶部两行内存信息（主显示可用率）。 */
    fun levelLine(ctx: Context): String {
        val lv = level(ctx)
        if (lv.totalKb <= 0L) return "当前内存：读不到内存信息"
        val usedKb = (lv.totalKb - lv.availKb).coerceAtLeast(0L)
        val sb = StringBuilder()
        sb.append("当前内存：可用 ").append(mb(lv.availKb)).append(" ／ 总量 ").append(mb(lv.totalKb))
            .append("（已用 ").append(mb(usedKb)).append("，占 ")
            .append(if (lv.usedPct < 0) "-" else "${lv.usedPct}%").append("）")
        sb.append('\n').append("判定用的可用率 ").append(if (lv.availPct < 0) "-" else "${lv.availPct}%")
            .append("（触发线：可用低于 ").append(availFloor(ctx)).append("% 时清理）")
        if (lv.freeKb >= 0L) {
            sb.append("；物理空闲 ").append(mb(lv.freeKb))
                .append("（").append(if (lv.physUsedPct < 0) "-" else "物理占用 ${lv.physUsedPct}%")
                .append("，含可回收缓存，仅供参考）")
        }
        if (lv.lowMemory) sb.append("；系统标记 lowMemory")
        return sb.toString()
    }

    /** `pidof <pkg>` 的第一个 pid；没有进程返回 -1。 */
    fun pidOf(pkg: String?): Int {
        if (pkg.isNullOrEmpty()) return -1
        try {
            val p = Runtime.getRuntime().exec(arrayOf("pidof", pkg))
            val line = BufferedReader(p.inputStream.reader()).use { it.readLine() }
            runCatching { p.waitFor() }
            if (line != null) {
                for (s in line.trim().split(Regex("\\s+"))) {
                    s.toIntOrNull()?.let { return it }
                }
            }
        } catch (t: Throwable) {
        }
        return procPidOf(pkg)
    }

    private fun procPidOf(pkg: String): Int {
        try {
            val names = File("/proc").list() ?: return -1
            for (n in names) {
                if (n.isEmpty() || n[0] !in '0'..'9') continue
                try {
                    val line = BufferedReader(FileReader("/proc/$n/cmdline")).use { it.readLine() }
                        ?: continue
                    val z = line.indexOf('\u0000')
                    val cmd = if (z >= 0) line.substring(0, z) else line
                    if (pkg == cmd) return n.toInt()
                } catch (t: Throwable) {
                }
            }
        } catch (t: Throwable) {
        }
        return -1
    }

    /** 进程的 oom_score_adj；没有进程返回 Int.MIN_VALUE。 */
    private fun oomAdj(pkg: String): Int {
        val pid = pidOf(pkg)
        if (pid <= 0) return Int.MIN_VALUE
        return try {
            BufferedReader(FileReader("/proc/$pid/oom_score_adj")).use { it.readLine()?.trim()?.toInt() }
                ?: Int.MIN_VALUE
        } catch (t: Throwable) {
            Int.MIN_VALUE
        }
    }

    /** 目标包的 importance；两条路都失败返回 -1（未知**不**当作前台）。 */
    private fun importanceOf(ctx: Context, pkg: String): Int {
        var best = -1
        runCatching {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am?.runningAppProcesses?.forEach { p ->
                val name = p?.processName ?: return@forEach
                if (name != pkg && !name.startsWith("$pkg:")) return@forEach
                if (best < 0 || p.importance < best) best = p.importance
            }
        }
        if (best >= 0) return best
        val adj = oomAdj(pkg)
        if (adj == Int.MIN_VALUE) return -1
        return when {
            adj <= 0 -> ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
            adj <= 200 -> ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
            adj <= 300 -> ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE
            adj <= 500 -> ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE
            adj <= 700 -> ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED
            else -> ActivityManager.RunningAppProcessInfo.IMPORTANCE_EMPTY
        }
    }

    private fun uidOf(ctx: Context, pkg: String): Int =
        runCatching { ctx.packageManager.getApplicationInfo(pkg, 0).uid }.getOrDefault(-1)

    private fun importanceWord(imp: Int): String = when {
        imp < 0 -> "未知"
        imp <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "前台"
        imp <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "前台服务"
        imp <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "可见"
        imp <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "可感知"
        imp <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "后台服务"
        imp <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "后台缓存"
        else -> "空进程"
    }

    private fun foregroundPkg(ctx: Context): String? = runCatching {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.runningAppProcesses?.firstOrNull {
            it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                !it.pkgList.isNullOrEmpty()
        }?.pkgList?.firstOrNull()
    }.getOrNull()

    /** `FORCE_STOP_PACKAGES` 是否真的授予（界面要如实显示）。 */
    fun forceStopPermission(ctx: Context): Boolean = runCatching {
        ctx.checkCallingOrSelfPermission("android.permission.FORCE_STOP_PACKAGES") ==
            PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    /** 二级：反射 `ActivityManager.forceStopPackage`；null = 成功。 */
    private fun forceStop(ctx: Context, pkg: String): String? {
        return try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return "拿不到 ActivityManager"
            ActivityManager::class.java.getMethod("forceStopPackage", String::class.java).invoke(am, pkg)
            null
        } catch (t: Throwable) {
            val c = t.cause ?: t
            "${c.javaClass.simpleName}: ${c.message}"
        }
    }

    /** 一级：请求系统清理该包的后台进程（前台服务天然不会被杀）。 */
    private fun killBackground(ctx: Context, pkg: String): String? {
        return try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return "拿不到 ActivityManager"
            am.killBackgroundProcesses(pkg)
            null
        } catch (t: Throwable) {
            "${t.javaClass.simpleName}: ${t.message}"
        }
    }

    /** 一次清理的结果。 */
    class Result {
        /** 是否真的执行了（被节流/未到触发线时 false）。 */
        var ran = false
        var trigger = ""
        /** 没执行时的原因。 */
        var reason = ""
        var availPct = -1
        var beforeAvailKb = -1L
        var afterAvailKb = -1L

        /** 可用内存差值（after − before，可能为负）。 */
        var deltaKb = 0L

        /** 进程真的消失的包数。 */
        var killed = 0
        var requested = 0
        var skipped = 0
        var detail = ""
        var prune = ""
        var at = 0L
        var time = ""
    }

    /**
     * 跑一次清理。自动触发条件：`可用% < availFloor`；[manual] 不受开关/触发线/冷却限制。
     *
     * @param trigger 中文触发来源（"手动" 等），只用于记录
     */
    fun run(ctx: Context, trigger: String?, manual: Boolean): Result {
        val r = Result()
        r.trigger = trigger ?: "?"
        r.at = System.currentTimeMillis()
        r.time = stamp(r.at)
        val lv0 = level(ctx)
        r.availPct = lv0.availPct
        r.beforeAvailKb = lv0.availKb

        if (!manual) {
            if (!enabled(ctx)) {
                r.reason = "总开关关闭"
                return r
            }
            val floor = availFloor(ctx)
            if (lv0.availPct >= 0 && lv0.availPct >= floor) {
                r.reason = "可用 ${lv0.availPct}% ≥ $floor%（触发线），未触发"
                return r
            }
            val last = runCatching { prefs(ctx).getLong(K_LAST_AT, 0L) }.getOrDefault(0L)
            if (last > 0L && r.at - last < COOLDOWN_MS) {
                r.reason = "可用 ${lv0.availPct}% 已低于 $floor% 但冷却中" +
                    "（距上次 ${(r.at - last) / 1000L}s < ${COOLDOWN_MS / 1000L}s）"
                return r
            }
        }

        val useL2 = level2(ctx)
        r.ran = true
        val fg = foregroundPkg(ctx)
        val sb = StringBuilder()
        val used0Kb = (lv0.totalKb - lv0.availKb).coerceAtLeast(0L)
        sb.append("触发时：可用 ").append(mb(lv0.availKb))
            .append(" ／ 总量 ").append(mb(lv0.totalKb))
            .append("（已用 ").append(mb(used0Kb)).append("，占 ")
            .append(if (lv0.usedPct < 0) "-" else "${lv0.usedPct}%").append("）")
            .append("；可用率 ").append(if (lv0.availPct < 0) "-" else "${lv0.availPct}%")
            .append("（物理占用 ").append(if (lv0.physUsedPct < 0) "-" else "${lv0.physUsedPct}%")
            .append(" 含缓存，仅参考）；触发线：可用低于 ").append(availFloor(ctx)).append("%")
            .append("；方式：").append(if (useL2) "二级（forceStop）" else "一级（killBackground）")
            .append('\n')

        val pkgs = list(ctx)
        if (pkgs.isEmpty()) sb.append("名单为空，没有可清理的包\n")
        for (pkg in pkgs) {
            val skip = skipReason(ctx, pkg, fg)
            if (skip != null) {
                r.skipped++
                sb.append("· ").append(pkg).append(" → 跳过（").append(skip).append("）\n")
                continue
            }
            val pidBefore = pidOf(pkg)
            val uid = uidOf(ctx, pkg)
            val err = if (useL2) forceStop(ctx, pkg) else killBackground(ctx, pkg)
            r.requested++
            // 复测：绝不假设成功。判「杀掉了」必须比 pid —— 桌面 widget 会在几百毫秒内用新 pid 拉起。
            sleepQuiet(250L)
            val pidAfter = pidOf(pkg)
            val gone = pidBefore > 0 && (pidAfter <= 0 || pidAfter != pidBefore)
            val imp = importanceOf(ctx, pkg)
            val uidTag = if (uid > 0 && uid < FIRST_APP_UID) "，uid $uid=系统共享" else ""
            sb.append("· ").append(pkg).append(" → ")
            when {
                err != null -> sb.append("调用失败（").append(err).append("）\n")
                gone -> {
                    r.killed++
                    sb.append("已终止（原 pid ").append(pidBefore).append(uidTag).append("，")
                        .append(if (useL2) "强制停止" else "后台清理").append("生效")
                    if (pidAfter > 0) sb.append("；复测时已被系统/桌面重新拉起为 pid ").append(pidAfter)
                    sb.append("）\n")
                }
                pidBefore <= 0 -> sb.append("本来就没有进程").append(uidTag).append('\n')
                else -> sb.append("进程仍在（pid ").append(pidAfter).append(uidTag)
                    .append("，importance=").append(if (imp < 0) "?" else imp.toString())
                    .append("/").append(importanceWord(imp)).append("）—— ")
                    .append(if (useL2) "forceStop 未生效" else "有前台服务或被前台绑定，Android 保护性未终止")
                    .append('\n')
            }
        }

        // 等内核把死进程的页回收掉再采样，否则「释放了多少」会低估。
        sleepQuiet(if (r.killed > 0) 1200L else 250L)
        val lv1 = level(ctx)
        r.afterAvailKb = lv1.availKb
        if (lv0.availKb >= 0L && lv1.availKb >= 0L) {
            r.deltaKb = lv1.availKb - lv0.availKb
            sb.append("可用内存：清理前 ").append(mb(lv0.availKb))
                .append(" → 清理后 ").append(mb(lv1.availKb))
                .append("（").append(if (r.deltaKb >= 0) "释放 " else "减少 ")
                .append(mb(Math.abs(r.deltaKb)))
                .append(if (r.deltaKb >= 0) "）" else "，期间其他进程又吃掉了）")
                .append("；可用率 ").append(if (lv0.availPct < 0) "-" else "${lv0.availPct}%")
                .append(" → ").append(if (lv1.availPct < 0) "-" else "${lv1.availPct}%").append('\n')
        }
        if (!useL2 && r.killed == 0 && r.requested > 0) {
            sb.append("提示：一级清理动不了「有前台服务」的进程；要让「强制停止」生效，请打开二级开关。\n")
        }

        r.prune = pruneExisting(ctx)
        sb.append("顺带裁剪：").append(r.prune).append('\n')
        r.detail = sb.toString().trim()

        save(ctx, r)
        Log.i(
            TAG, "clean[${r.trigger}] 可用=${r.availPct}% 请求=${r.requested}" +
                " 终止=${r.killed} 跳过=${r.skipped} 可用差值=${r.deltaKb}KB\n${r.detail}"
        )
        return r
    }

    fun skipReason(ctx: Context, pkg: String?, fgPkg: String?): String? {
        if (pkg.isNullOrEmpty()) return "空包名"
        if (pkg == ctx.packageName) return "本应用"
        if (isNever(pkg)) return neverReason(pkg)
        if (fgPkg != null && fgPkg == pkg) return "前台（用户正在使用）"
        val ai = runCatching { ctx.packageManager.getApplicationInfo(pkg, 0) }.getOrNull()
            ?: return "未安装"
        // 不能按 "uid < 10000 就跳过" 挡系统应用：本车机上高德就是 uid 1000 的 sharedUser 应用。
        if ((ai.flags and ApplicationInfo.FLAG_PERSISTENT) != 0) return "常驻系统应用（FLAG_PERSISTENT）"
        val imp = importanceOf(ctx, pkg)
        if (imp in 0..ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
            return "前台运行中（importance $imp）"
        }
        return null
    }

    private fun sleepQuiet(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (ie: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    /** 顺带裁剪：崩溃目录 + 本应用 cacheDir/bundle 里超过 30 分钟的解包残留。 */
    fun pruneExisting(ctx: Context): String {
        val sb = StringBuilder()
        try {
            CrashCollect.pruneDir(File("/sdcard/PSA/crash"), 40, 12L * 1024L * 1024L)
            CrashCollect.pruneDir(File(ctx.filesDir, "crash"), 40, 12L * 1024L * 1024L)
            sb.append("崩溃目录已裁剪")
        } catch (t: Throwable) {
        }
        try {
            val freed = trimOld(File(ctx.cacheDir, "bundle"), 30L * 60L * 1000L)
            if (freed > 0L) {
                if (sb.isNotEmpty()) sb.append("；")
                sb.append("缓存释放 ").append(mb(freed / 1024L))
            }
        } catch (t: Throwable) {
        }
        return if (sb.isEmpty()) "无" else sb.toString()
    }

    private fun trimOld(dir: File?, ageMs: Long): Long {
        val fs = dir?.listFiles() ?: return 0L
        val now = System.currentTimeMillis()
        var freed = 0L
        for (f in fs) {
            try {
                if (f.isFile && now - f.lastModified() > ageMs) {
                    val len = f.length()
                    if (f.delete()) freed += len
                }
            } catch (t: Throwable) {
            }
        }
        return freed
    }

    private fun save(ctx: Context, r: Result) {
        runCatching {
            prefs(ctx).edit()
                .putLong(K_LAST_AT, r.at)
                .putString(K_LAST_TIME, r.time)
                .putString(K_LAST_TRIGGER, r.trigger)
                .putString(K_LAST_LEVEL, if (level2(ctx)) "二级（强制停止）" else "一级（后台清理）")
                .putInt(K_LAST_PCT, r.availPct)
                .putLong(K_LAST_DELTA, r.deltaKb)
                .putInt(K_LAST_KILLED, r.killed)
                .putString(K_LAST_RESULT, r.detail)
                .apply()
        }
    }

    /** 自动路径（给开机广播 / 周期任务用）。开关、触发线、冷却任一不满足时什么都不做。 */
    fun auto(ctx: Context, trigger: String) {
        try {
            val r = run(ctx, trigger, false)
            if (!r.ran && r.reason.isNotEmpty()) {
                Log.i(TAG, "check[$trigger] ${r.reason}")
            }
        } catch (t: Throwable) {
            Log.i(TAG, "auto 异常（已吞掉）: $t")
        }
    }

    /** 上次清理结果（单行摘要）。 */
    fun lastLine(ctx: Context): String = try {
        val p = prefs(ctx)
        val t = p.getString(K_LAST_TIME, null)
        if (t == null) "（还没清理过）" else {
            val d = p.getLong(K_LAST_DELTA, 0L)
            "$t · ${p.getString(K_LAST_TRIGGER, "?")} · ${p.getString(K_LAST_LEVEL, "一级")}" +
                " · 触发时可用 ${p.getInt(K_LAST_PCT, -1)}%" +
                " · 终止 ${p.getInt(K_LAST_KILLED, 0)} 个" +
                " · ${if (d >= 0) "释放 " else "减少 "}${mb(Math.abs(d))}（可用内存变化）"
        }
    } catch (t: Throwable) {
        "（读不到）"
    }

    /** 上次清理的逐包明细。 */
    fun lastDetail(ctx: Context): String =
        runCatching { prefs(ctx).getString(K_LAST_RESULT, null) }.getOrNull() ?: "（还没清理过）"

    /** 多行状态（界面/命令共用）。 */
    fun statusText(ctx: Context): String {
        val sb = StringBuilder()
        val lv = level(ctx)
        val floor = availFloor(ctx)
        val usedKb = (lv.totalKb - lv.availKb).coerceAtLeast(0L)
        sb.append("memguard: 开关=").append(if (enabled(ctx)) "开" else "关")
            .append("，触发线=可用低于 ").append(floor).append("%")
            .append("（等价占用超过 ").append(100 - floor).append("%）")
            .append("，二级=").append(if (level2(ctx)) "开(forceStop)" else "关").append('\n')
        sb.append("内存: 可用 ").append(mb(lv.availKb))
            .append(" ／ 总量 ").append(mb(lv.totalKb))
            .append("（已用 ").append(mb(usedKb)).append("，占 ")
            .append(if (lv.usedPct < 0) "-" else "${lv.usedPct}%").append("）")
            .append("；可用率 ").append(if (lv.availPct < 0) "-" else "${lv.availPct}%")
            .append("，lowMemory=").append(lv.lowMemory).append("，来源=").append(lv.src).append('\n')
        sb.append("FORCE_STOP_PACKAGES=").append(if (forceStopPermission(ctx)) "已授予" else "未授予").append('\n')
        sb.append("名单: ").append(listText(ctx)).append('\n')
        sb.append("上次: ").append(lastLine(ctx))
        return sb.toString().trim()
    }

    fun stamp(ms: Long): String =
        runCatching { SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(ms)) }.getOrDefault("?")

    /** 可勾选的候选包：`{包名, 显示名}`。 */
    fun candidates(ctx: Context): List<Pair<String, String>> {
        val seen = LinkedHashSet<String>()
        val out = ArrayList<Pair<String, String>>()
        val pm = ctx.packageManager
        runCatching {
            pm.getInstalledApplications(0)?.forEach { ai ->
                val name = ai?.packageName ?: return@forEach
                if (seen.contains(name) || isNever(name)) return@forEach
                if ((ai.flags and ApplicationInfo.FLAG_PERSISTENT) != 0) return@forEach
                seen.add(name)
                var label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(name)
                if (ai.uid < FIRST_APP_UID) label = "$label（系统 uid ${ai.uid}）"
                out.add(name to label)
            }
        }
        for (p in SUGGEST) {
            if (seen.contains(p) || isNever(p)) continue
            val ai = runCatching { pm.getApplicationInfo(p, 0) }.getOrNull() ?: continue
            seen.add(p)
            out.add(p to runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(p))
        }
        for (p in list(ctx)) {
            if (!seen.contains(p)) {
                seen.add(p)
                out.add(p to "$p（未安装）")
            }
        }
        return out
    }

    private fun close(c: Closeable?) {
        runCatching { c?.close() }
    }
}
