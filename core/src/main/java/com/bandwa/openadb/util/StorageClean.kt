package com.bandwa.openadb.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.os.StatFs
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 存储清理：可用空间低于阈值自动腾空间，也可以手动「立即清理」。
 *
 * 三块真正能腾出空间的（都**不需要 root**）：
 * 1. `/data/log_temp`、`/data/anr`、`/data/tombstones` —— 属主 system:system，本应用同 uid 可直接删
 *    （车机实测 `/data/log_temp` 曾累积 3.2 GB，每次开机一个 `boot_N__xxx` 目录）；
 * 2. 酷我播放缓存（`com.cusc.media` 的 `kw/` 下那几个子目录的**内容**，实测 795 MB）；
 * 3. 崩溃记录与诊断包 —— 裁剪到最近 N 个，不是全删。
 *
 * ⚠ 原版还有几条 root 命令（各应用的内部 cache、`/data/dalvik-cache`、`/data/local/tmp`、
 * `/data/log`）**故意没有搬**：这台车机的 `su` 是 `rwsr-x--- root:shell`，只有 adb shell（uid 2000）
 * 能执行，本应用是 system(uid 1000)，那几条在原版里一直是空跑。搬进来只会让界面谎报"清过"。
 */
object StorageClean {

    private const val TAG = "PSA.StorageClean"

    private const val PREF = "psa_storageclean"
    private const val K_ON = "on"
    private const val K_AVAIL_FLOOR = "availFloor"
    private const val K_KW = "kwPlayCache"
    private const val K_CRASH = "crashDiag"
    private const val K_OTHER = "otherAppCache"
    private const val K_DEEP = "javaDeep"
    private const val K_LAST_AT = "lastAt"
    private const val K_LAST_TIME = "lastTime"
    private const val K_LAST_TRIGGER = "lastTrigger"
    private const val K_LAST_PCT = "lastPct"
    private const val K_LAST_RECLAIM = "lastReclaim"
    private const val K_LAST_RESULT = "lastResult"

    const val COOLDOWN_MS = 5L * 60L * 1000L
    const val DEFAULT_AVAIL_FLOOR = 10

    /** 酷我（我们维护的多媒体包）的数据根。 */
    private const val KW_DIR = "/sdcard/Android/data/com.cusc.media/files/kw"

    /** ✅ 白名单：只清这几个子目录的**内容**（目录本身保留）。 */
    private val KW_CLEAR = listOf("playcache", "xlog", "log", "offlinelog", "crash")

    /** ⛔ 黑名单：用户下载的歌 / 用户配置 —— **永远**不进删除路径。 */
    private val KW_NEVER = listOf("Song", "setting", "config", "Cache", "tencentcache")

    private const val KEEP_CRASH_FILES = 20
    private const val KEEP_CRASH_BYTES = 12L * 1024L * 1024L
    private val DIAG_DIRS = listOf("/sdcard/Download", "/sdcard/PSA")

    /** 非 root 深清目标：属主 system:system，本应用同 uid 可删。 */
    private val JAVA_DEEP_DIRS = listOf("/data/log_temp", "/data/anr", "/data/tombstones")

    private val CACHE_ROOTS = listOf("/sdcard/Android/data", "/data/data")
    private const val CACHE_NAME = "cache"

    /** ⛔ 路径里出现这些段一律跳过：`files/` 是用户数据，`databases/`、`shared_prefs/` 是设置与账号，`lib/` 删了应用起不来。 */
    private val FORBIDDEN_SEGS = listOf("/files/", "/databases/", "/shared_prefs/", "/lib/")

    fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /* ---------------- 开关与阈值 ---------------- */

    fun enabled(ctx: Context): Boolean =
        runCatching { prefs(ctx).getBoolean(K_ON, true) }.getOrDefault(true)

    fun setEnabled(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(K_ON, on).apply()
    }

    fun availFloor(ctx: Context): Int =
        runCatching { prefs(ctx).getInt(K_AVAIL_FLOOR, DEFAULT_AVAIL_FLOOR) }
            .getOrDefault(DEFAULT_AVAIL_FLOOR).coerceIn(1, 90)

    fun setAvailFloor(ctx: Context, floorPct: Int) {
        prefs(ctx).edit().putInt(K_AVAIL_FLOOR, floorPct.coerceIn(1, 90)).apply()
    }

    fun triggerLine(ctx: Context): String = "存储可用低于 ${availFloor(ctx)}% 时清理"

    fun javaDeep(ctx: Context): Boolean =
        runCatching { prefs(ctx).getBoolean(K_DEEP, true) }.getOrDefault(true)

    fun setJavaDeep(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(K_DEEP, on).apply()
    }

    fun kwPlayCache(ctx: Context): Boolean =
        runCatching { prefs(ctx).getBoolean(K_KW, true) }.getOrDefault(true)

    fun setKwPlayCache(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(K_KW, on).apply()
    }

    fun crashDiag(ctx: Context): Boolean =
        runCatching { prefs(ctx).getBoolean(K_CRASH, true) }.getOrDefault(true)

    fun setCrashDiag(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(K_CRASH, on).apply()
    }

    /** 其它 App 的 cache：默认**关** —— 会影响别的应用，交给用户自己决定。 */
    fun otherAppCache(ctx: Context): Boolean =
        runCatching { prefs(ctx).getBoolean(K_OTHER, false) }.getOrDefault(false)

    fun setOtherAppCache(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(K_OTHER, on).apply()
    }

    /* ---------------- 容量与格式 ---------------- */

    /** 存储状态：{可用字节, 总字节, 可用%}。读不到返回 null。 */
    fun level(): LongArray? = try {
        val fs = StatFs(Environment.getDataDirectory().path)
        val avail = fs.availableBytes
        val total = fs.totalBytes
        if (total <= 0L) null else longArrayOf(avail, total, avail * 100L / total)
    } catch (t: Throwable) {
        null
    }

    fun mb(bytes: Long): String {
        val kb = bytes / 1024L
        return when {
            kb >= 1024L * 1024L -> String.format(Locale.US, "%.2f GB", kb / 1024.0 / 1024.0)
            kb >= 1024L -> String.format(Locale.US, "%.1f MB", kb / 1024.0)
            else -> "$kb KB"
        }
    }

    /** 一行现状（界面上标题下面那行）。 */
    fun levelLine(ctx: Context): String {
        val lv = level() ?: return "当前存储：读不到"
        val used = (lv[1] - lv[0]).coerceAtLeast(0L)
        val pct = if (lv[2] < 0) "-" else (100 - lv[2]).toString()
        return "剩余 ${mb(lv[0])} ／ 总量 ${mb(lv[1])}（已用 ${mb(used)}，占 $pct%；可用率 ${lv[2]}%）\n" +
            triggerLine(ctx)
    }

    /* ---------------- 结果 ---------------- */

    data class Result(
        val ran: Boolean = false,
        val reason: String = "",
        val time: String = "",
        val trigger: String = "",
        val availPct: Int = -1,
        /** 本次**真的删掉**的文件字节数（不是 /data 可用变化 —— 那个会被并发写入冲掉）。 */
        val reclaimed: Long = 0L,
        val detail: String = "",
    ) {
        fun summary(): String = if (!ran) {
            "未清理（$reason）"
        } else {
            "上次：$time · $trigger · 可用 $availPct% · 释放 ${mb(reclaimed)}"
        }
    }

    /* ---------------- 跑一次 ---------------- */

    /**
     * @param manual true = 手动「立即清理」，忽略总开关、触发线与冷却
     */
    fun run(ctx: Context, trigger: String?, manual: Boolean): Result {
        val at = System.currentTimeMillis()
        val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(at))
        val t = trigger ?: "?"
        val lv = level() ?: return Result(false, "读不到 /data 容量", time, t)
        val availPct = lv[2].toInt()

        if (!manual) {
            if (!enabled(ctx)) return Result(false, "总开关关闭", time, t, availPct)
            val floor = availFloor(ctx)
            if (availPct >= floor) {
                return Result(false, "可用 $availPct% ≥ $floor%（触发线），未触发", time, t, availPct)
            }
            val last = runCatching { prefs(ctx).getLong(K_LAST_AT, 0L) }.getOrDefault(0L)
            if (last > 0L && at - last < COOLDOWN_MS) {
                return Result(
                    false,
                    "可用 $availPct% 已低于 $floor% 但冷却中（距上次 ${(at - last) / 1000L}s < ${COOLDOWN_MS / 1000L}s）",
                    time, t, availPct,
                )
            }
        }

        val sb = StringBuilder()
        var reclaimed = 0L

        // ① 不用 root 的深度清理：这是这台车上绝对值最大的一块
        if (javaDeep(ctx)) {
            val log = StringBuilder()
            val freed = cleanJavaDeep(log)
            reclaimed += freed
            sb.append("深度清理（log_temp / anr / tombstones）：").append(mb(freed)).append('\n')
            if (log.isNotEmpty()) sb.append(log)
        } else {
            sb.append("深度清理：跳过（勾选项关）\n")
        }

        // ② 本应用自己的 cache（最安全）
        val own = clearDir(ctx.cacheDir)
        reclaimed += own
        sb.append("本应用 cache：").append(mb(own)).append('\n')

        // ③ 酷我播放缓存
        if (kwPlayCache(ctx)) {
            val root = File(KW_DIR)
            val before = dirBytes(root)
            var dirs = 0
            var freed = 0L
            for (name in KW_CLEAR) {
                if (isNever(name)) continue
                val d = File(root, name)
                if (!d.isDirectory) continue
                val b0 = dirBytes(d)
                clearDir(d)
                val cut = (b0 - dirBytes(d)).coerceAtLeast(0L)
                if (cut > 0L) {
                    freed += cut
                    dirs++
                }
            }
            reclaimed += freed
            sb.append("酷我播放缓存：清 ").append(dirs).append(" 个目录，").append(mb(freed))
                .append("（kw 合计 ").append(mb(before)).append(" → ").append(mb(dirBytes(root))).append("）\n")
        } else {
            sb.append("酷我播放缓存：跳过（勾选项关）\n")
        }

        // ④ 崩溃记录与诊断包：裁剪，不是全删
        if (crashDiag(ctx)) {
            var cut = 0
            var freed = 0L
            for (dir in crashDirs(ctx)) {
                val b0 = dirBytes(dir)
                CrashCollect.pruneDir(dir, KEEP_CRASH_FILES, KEEP_CRASH_BYTES)
                freed += (b0 - dirBytes(dir)).coerceAtLeast(0L)
            }
            var zips = 0
            for (d in DIAG_DIRS) zips += pruneDiagZips(File(d), KEEP_CRASH_FILES)
            reclaimed += freed
            cut = zips
            sb.append("崩溃与诊断包：裁剪后释放 ").append(mb(freed))
                .append("，删诊断包 ").append(cut).append(" 个（各留最近 ").append(KEEP_CRASH_FILES).append(" 个）\n")
        } else {
            sb.append("崩溃与诊断包：跳过（勾选项关）\n")
        }

        // ⑤ 其它 App 的 cache（默认关）
        if (otherAppCache(ctx)) {
            var freed = 0L
            var pkgs = 0
            for (rootPath in CACHE_ROOTS) {
                val pkgsDirs = File(rootPath).listFiles() ?: continue
                for (p in pkgsDirs) {
                    if (!p.isDirectory) continue
                    val cache = File(p, CACHE_NAME)
                    if (!isCacheOnly(cache)) continue
                    val b0 = dirBytes(cache)
                    clearDir(cache)
                    val cut = (b0 - dirBytes(cache)).coerceAtLeast(0L)
                    if (cut > 0L) {
                        freed += cut
                        pkgs++
                    }
                }
            }
            reclaimed += freed
            sb.append("其它 App cache：").append(mb(freed)).append("（").append(pkgs).append(" 个包，只清 cache/）\n")
        } else {
            sb.append("其它 App cache：跳过（默认关）\n")
        }

        val detail = sb.toString()
        runCatching {
            prefs(ctx).edit()
                .putLong(K_LAST_AT, at)
                .putString(K_LAST_TIME, time)
                .putString(K_LAST_TRIGGER, t)
                .putInt(K_LAST_PCT, availPct)
                .putLong(K_LAST_RECLAIM, reclaimed)
                .putString(K_LAST_RESULT, detail)
                .apply()
        }
        Log.i(TAG, "clean[$t] 可用=${availPct}% 实删=${reclaimed / 1024L}KB")
        return Result(true, "", time, t, availPct, reclaimed, detail)
    }

    /** 自动路径：不满足开关/阈值/冷却时什么都不做。 */
    fun auto(ctx: Context, trigger: String) {
        try {
            val r = run(ctx, trigger, false)
            if (!r.ran && r.reason.isNotEmpty()) Log.i(TAG, "check[$trigger] ${r.reason}")
        } catch (t: Throwable) {
            Log.i(TAG, "auto 异常（已吞掉）: $t")
        }
    }

    fun lastLine(ctx: Context): String = try {
        val p = prefs(ctx)
        val time = p.getString(K_LAST_TIME, null)
        if (time.isNullOrEmpty()) {
            "上次：还没清理过"
        } else {
            "上次：$time · ${p.getString(K_LAST_TRIGGER, "")} · 触发时可用 " +
                "${p.getInt(K_LAST_PCT, -1)}% · 释放 ${mb(p.getLong(K_LAST_RECLAIM, 0L))}"
        }
    } catch (t: Throwable) {
        "上次：还没清理过"
    }

    fun statusText(ctx: Context): String {
        val sb = StringBuilder()
        sb.append("存储清理：").append(if (enabled(ctx)) "开" else "关").append("；")
            .append(triggerLine(ctx)).append('\n')
        sb.append(levelLine(ctx)).append('\n')
        sb.append(lastLine(ctx)).append('\n')
        sb.append("勾选：深度清理").append(if (javaDeep(ctx)) "开" else "关")
            .append(" / 酷我播放缓存").append(if (kwPlayCache(ctx)) "开" else "关")
            .append(" / 崩溃与诊断包").append(if (crashDiag(ctx)) "开" else "关")
            .append(" / 其它 App cache").append(if (otherAppCache(ctx)) "开" else "关")
        return sb.toString()
    }

    /* ---------------- 深度清理（不用 root） ---------------- */

    /**
     * 用 Java API 直接清 `system:system` 的那几个目录，**不经 su**。
     *
     * @return 删掉的字节数；每条明细写进 [rlog]
     */
    fun cleanJavaDeep(rlog: StringBuilder?): Long {
        var freed = 0L
        for (path in JAVA_DEEP_DIRS) {
            try {
                val d = File(path)
                if (!d.isDirectory) {
                    rlog?.append("   $path：不存在，跳过\n")
                    continue
                }
                val kids = d.listFiles()
                if (kids == null || kids.isEmpty()) {
                    rlog?.append("   $path：空\n")
                    continue
                }
                var one = 0L
                var n = 0
                for (k in kids) {
                    val sz = sizeOf(k)
                    if (deleteRec(k)) {
                        one += sz
                        n++
                    }
                }
                freed += one
                rlog?.append("   $path：删 $n 项，释放 ${mb(one)}\n")
            } catch (t: Throwable) {
                rlog?.append("   $path：异常 $t\n")
            }
        }
        return freed
    }

    /* ---------------- 小工具 ---------------- */

    private fun crashDirs(ctx: Context): List<File> = listOf(
        File(CrashCollect.DIR),
        File(ctx.filesDir, "crash"),
    )

    /** 只认诊断包这一种形状：`PSA_diag-*.zip`（与导出时的命名同一口径）。 */
    private fun pruneDiagZips(dir: File, keep: Int): Int {
        val fs = dir.listFiles() ?: return 0
        var n = 0
        fs.filter { it.isFile && it.name.startsWith("PSA_diag-") && it.name.endsWith(".zip") }
            .sortedByDescending { it.lastModified() }
            .drop(keep)
            .forEach { if (it.delete()) n++ }
        // 打包中途被打断留下的临时目录（正常路径下导出流程自己会删，这里兜底）
        fs.filter { it.isDirectory && it.name.startsWith("diag_") }
            .forEach { d -> if (deleteRec(d)) n++ }
        return n
    }

    /** 名字是否命中黑名单（大小写不敏感）。 */
    fun isNever(name: String?): Boolean {
        if (name == null) return true
        return KW_NEVER.any { it.equals(name, ignoreCase = true) }
    }

    /** 是不是"可以清的 cache 目录"：必须以 `/cache` 结尾、不含禁段、不含 `..`、且真是目录。 */
    fun isCacheOnly(d: File?): Boolean {
        if (d == null) return false
        val p = d.absolutePath.replace('\\', '/')
        if (!p.endsWith("/$CACHE_NAME")) return false
        if (p.contains("..")) return false
        if (FORBIDDEN_SEGS.any { p.contains(it) }) return false
        return d.isDirectory
    }

    fun sizeOf(f: File): Long = try {
        if (f.isFile) f.length() else (f.listFiles()?.sumOf { sizeOf(it) } ?: 0L)
    } catch (t: Throwable) {
        0L
    }

    fun deleteRec(f: File): Boolean = try {
        if (f.isDirectory) f.listFiles()?.forEach { deleteRec(it) }
        f.delete()
    } catch (t: Throwable) {
        false
    }

    /** 递归清一个目录里的文件（保留目录本身），返回释放的字节数。 */
    fun clearDir(dir: File?): Long {
        if (dir == null || !dir.isDirectory) return 0L
        val fs = dir.listFiles() ?: return 0L
        var freed = 0L
        for (f in fs) {
            try {
                if (f.isDirectory) {
                    freed += clearDir(f)
                    f.delete()
                } else {
                    val n = f.length()
                    if (f.delete()) freed += n
                }
            } catch (t: Throwable) {
            }
        }
        return freed
    }

    fun dirBytes(dir: File?): Long {
        val fs = dir?.listFiles() ?: return 0L
        var total = 0L
        for (f in fs) total += if (f.isDirectory) dirBytes(f) else f.length()
        return total
    }
}
