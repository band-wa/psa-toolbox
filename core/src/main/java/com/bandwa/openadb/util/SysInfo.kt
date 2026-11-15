package com.bandwa.openadb.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.FileReader
import java.util.Locale

/** 车机运行状态快照（系统 / CPU / 内存 / 存储 / 温度）；[snapshot] 阻塞约 250ms，只在后台线程调。 */
object SysInfo {

    /** CPU 两次采样间隔（毫秒）。太短噪声大，太长界面刷新显迟钝。 */
    private const val CPU_SAMPLE_MS = 250L

    /** 0.95 GB：超过就用 GB 显示，否则退化 MB。 */
    private const val GB_THRESHOLD = 1_020_054_732L

    fun snapshot(ctx: Context): String {
        val sb = StringBuilder()
        sb.append(runCatching { deviceLine(ctx) }.getOrElse { "车机系统：-" }).append('\n')
        sb.append(runCatching { cpuLine() }.getOrElse { "CPU：-" }).append('\n')
        sb.append(runCatching { memLine(ctx) }.getOrElse { "内存：-" }).append('\n')
        sb.append(runCatching { dataLine() }.getOrElse { "存储 /data：-" }).append('\n')
        runCatching { extLine() }.getOrNull()?.let { sb.append(it).append('\n') }
        runCatching { tempLine() }.getOrNull()?.let { sb.append(it).append('\n') }
        while (sb.isNotEmpty() && sb.last() == '\n') sb.setLength(sb.length - 1)
        return sb.toString()
    }

    private fun deviceLine(ctx: Context): String {
        val res = runCatching {
            val dm = ctx.resources.displayMetrics
            if (dm.widthPixels > 0 && dm.heightPixels > 0) "${dm.widthPixels}x${dm.heightPixels}" else "-"
        }.getOrDefault("-")
        val rel = Build.VERSION.RELEASE ?: "-"
        val model = Build.MODEL ?: "-"
        val up = runCatching { SystemClock.elapsedRealtime() }.getOrDefault(0L)
        return "车机系统：Android $rel · $res · $model · 已运行 ${uptime(up)}"
    }

    private fun uptime(ms: Long): String {
        val s = (ms / 1000L).coerceAtLeast(0L)
        val d = s / 86400L
        val h = (s % 86400L) / 3600L
        val m = (s % 3600L) / 60L
        val sb = StringBuilder()
        if (d > 0L) sb.append(d).append(" 天 ")
        if (d > 0L || h > 0L) sb.append(h).append(" 小时 ")
        sb.append(m).append(" 分")
        return sb.toString()
    }

    private fun cpuLine(): String {
        val cores = runCatching { Runtime.getRuntime().availableProcessors() }.getOrDefault(-1)
        var pct = -1
        val a = cpuStat()
        if (a != null) {
            try {
                Thread.sleep(CPU_SAMPLE_MS)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            val b = cpuStat()
            if (b != null) {
                val dt = b[0] - a[0]
                val di = b[1] - a[1]
                if (dt > 0L) {
                    pct = Math.round((dt - di) * 100.0 / dt).toInt().coerceIn(0, 100)
                }
            }
        }
        val sb = StringBuilder("CPU：")
        sb.append(if (pct < 0) "-" else "$pct%")
        sb.append("（").append(if (cores > 0) "$cores 核" else "- 核")
        loadAvg()?.let { sb.append("，负载 ").append(it) }
        sb.append("）")
        return sb.toString()
    }

    /** /proc/stat 第一行 → {总 jiffies, 空闲 jiffies}；空闲按惯例算 idle + iowait。 */
    private fun cpuStat(): LongArray? {
        var r: BufferedReader? = null
        try {
            r = BufferedReader(FileReader("/proc/stat"))
            val line = r.readLine() ?: return null
            if (!line.startsWith("cpu ")) return null
            val p = line.trim().split(Regex("\\s+"))
            var total = 0L
            var idle = 0L
            for (i in 1 until p.size) {
                val v = p[i].toLongOrNull() ?: continue
                total += v
                if (i == 4 || i == 5) idle += v
            }
            return if (total > 0L) longArrayOf(total, idle) else null
        } catch (t: Throwable) {
            return null
        } finally {
            close(r)
        }
    }

    private fun loadAvg(): String? = runCatching {
        BufferedReader(FileReader("/proc/loadavg")).use { r ->
            val p = (r.readLine() ?: return@use null).trim().split(Regex("\\s+"))
            if (p.size < 3) null else "${p[0]} / ${p[1]} / ${p[2]}"
        }
    }.getOrNull()

    private fun memLine(ctx: Context): String {
        var total = 0L
        var avail = 0L
        runCatching {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (am != null) {
                val mi = ActivityManager.MemoryInfo()
                am.getMemoryInfo(mi)
                total = mi.totalMem
                avail = mi.availMem
            }
        }
        if (total <= 0L || avail < 0L) {
            memInfoFallback()?.let {
                total = it[0]
                avail = it[1]
            }
        }
        if (total <= 0L) return "内存：-"
        val used = (total - avail).coerceAtLeast(0L)
        return "内存：可用 ${size(avail, 2)} ／ 总量 ${size(total, 2)}（已用 ${size(used, 2)}，占 ${percent(used, total)}）"
    }

    private fun memInfoFallback(): LongArray? {
        var r: BufferedReader? = null
        try {
            r = BufferedReader(FileReader("/proc/meminfo"))
            var total = -1L
            var avail = -1L
            while (true) {
                val line = r.readLine() ?: break
                if (line.startsWith("MemTotal:")) total = firstKbValue(line)
                else if (line.startsWith("MemAvailable:")) avail = firstKbValue(line)
                if (total > 0L && avail >= 0L) break
            }
            return if (total > 0L && avail >= 0L) longArrayOf(total, avail) else null
        } catch (t: Throwable) {
            return null
        } finally {
            close(r)
        }
    }

    private fun firstKbValue(line: String): Long = try {
        val p = line.trim().split(Regex("\\s+"))
        if (p.size < 2) -1L else p[1].toLong() * 1024L
    } catch (t: Throwable) {
        -1L
    }

    private fun dataLine(): String {
        val path = runCatching { Environment.getDataDirectory()?.absolutePath }.getOrNull() ?: "/data"
        return fsLine("存储 /data", path)
    }

    /** 外部存储行；与 /data 同分区时不重复显示。 */
    private fun extLine(): String? {
        val path = runCatching { Environment.getExternalStorageDirectory()?.absolutePath }.getOrNull()
        val ext = fsStat(path) ?: return null
        if (ext[1] <= 0L) return null
        val data = runCatching {
            fsStat(runCatching { Environment.getDataDirectory()?.absolutePath }.getOrNull() ?: "/data")
        }.getOrNull()
        if (data != null && data[1] > 0L && Math.abs(ext[1] - data[1]) < data[1] / 100L) return null
        val used = (ext[1] - ext[0]).coerceAtLeast(0L)
        return "存储 /sdcard：剩余 ${size(ext[0], 1)} ／ 总量 ${size(ext[1], 1)}（已用 ${size(used, 1)}，占 ${percent(used, ext[1])}）"
    }

    private fun fsLine(label: String, path: String): String {
        val v = fsStat(path)
        if (v == null || v[1] <= 0L) return "$label：-"
        val used = (v[1] - v[0]).coerceAtLeast(0L)
        return "$label：剩余 ${size(v[0], 1)} ／ 总量 ${size(v[1], 1)}（已用 ${size(used, 1)}，占 ${percent(used, v[1])}）"
    }

    /** StatFs → {可用字节, 总字节}；路径不存在/没权限返回 null。 */
    private fun fsStat(path: String?): LongArray? {
        if (path == null) return null
        return try {
            val fs = StatFs(path)
            val bs = fs.blockSizeLong
            val total = fs.blockCountLong * bs
            val avail = fs.availableBlocksLong * bs
            if (total <= 0L || bs <= 0L) null else longArrayOf(avail.coerceAtLeast(0L), total)
        } catch (t: Throwable) {
            null
        }
    }

    /** 温度行；读不到返回 null（界面不显示这一行）。 */
    private fun tempLine(): String? {
        val paths = arrayOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
        )
        for (p in paths) {
            val s = firstLine(p) ?: continue
            var v = s.trim().toDoubleOrNull() ?: continue
            if (v > 1000.0) v /= 1000.0
            if (v > 0.0 && v < 150.0) return String.format(Locale.US, "温度：%.1f ℃", v)
        }
        return null
    }

    private fun firstLine(path: String): String? {
        var r: BufferedReader? = null
        return try {
            r = BufferedReader(FileReader(path))
            r.readLine()
        } catch (t: Throwable) {
            null
        } finally {
            close(r)
        }
    }

    private fun close(c: Closeable?) {
        runCatching { c?.close() }
    }

    /** 字节 → 人可读（≥0.95GB 用 GB，否则 MB）。 */
    fun size(bytes: Long, decimals: Int): String {
        if (bytes < 0L) return "-"
        if (bytes >= GB_THRESHOLD) {
            return String.format(Locale.US, "%.${decimals}f GB", bytes / 1073741824.0)
        }
        return String.format(Locale.US, "%.0f MB", bytes / 1048576.0)
    }

    /** KB → MB/GB（内存口径与 [size] 一致）。 */
    fun kb(availKb: Long): String = size(availKb * 1024L, 2)

    fun percent(used: Long, total: Long): String {
        if (total <= 0L) return "-"
        val p = Math.round(used * 100.0 / total).coerceIn(0L, 100L)
        return "$p%"
    }
}
