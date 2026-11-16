package com.bandwa.openadb.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 崩溃与诊断包：本应用崩溃落盘、系统 dropbox 里 crash/anr/tombstone 复制、logcat -b crash 抓取。
 *
 * 目录优先 `/sdcard/PSA/crash`（车机上与 system 同 uid 可写、方便 adb pull），写不了退到 App 内部
 * `filesDir/crash`。**不要只写 /sdcard** —— 手机上没给存储权限就会静默丢文件。
 */
object CrashCollect {

    private const val TAG = "PSA.Crash"

    /** 收集总开关：`persist.bandwa.crash.on=0` 即关闭。 */
    private const val PROP_ON = "persist.bandwa.crash.on"

    /** 自动收集节流（手动不受限）。 */
    private const val AUTO_INTERVAL_MS = 10L * 60L * 1000L
    private const val KEY_LAST_COLLECT = "crash_last_collect"
    private const val PREFS = "psa_diag"

    private const val MAX_DROPBOX_PER_RUN = 30
    private const val MAX_FILES = 40
    private const val MAX_BYTES = 12L * 1024L * 1024L

    const val DIR = "/sdcard/PSA/crash"

    private const val LOG_TAIL = 400

    @Volatile
    private var appCtx: Context? = null

    @Volatile
    private var lastDir: String? = null

    /** 装未捕获异常钩子：先落盘，再交回原处理器（不改变崩溃行为）。 */
    fun installHandler(ctx: Context) {
        appCtx = ctx.applicationContext
        try {
            val prev = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, ex ->
                runCatching {
                    // 免费版崩溃后台只显示 Process/Thread Name 与堆栈：把身份写进 Thread Name 才看得见。
                    if (thread != null) {
                        val tag = identityTag()
                        val nm = thread.name ?: "?"
                        if (!nm.contains("|$tag")) thread.name = "$nm|$tag"
                    }
                    writeOwnCrash(thread, ex)
                }
                prev?.uncaughtException(thread, ex)
            }
        } catch (t: Throwable) {
            Log.i(TAG, "crash: 装钩子失败（忽略）: $t")
        }
    }

    private fun identityTag(): String = runCatching {
        val ctx = appCtx
        val model = (Build.MODEL ?: "?").replace(' ', '_')
        val fp = ctx?.let { shortId(it) } ?: "?"
        "$model.$fp"
    }.getOrDefault("?")

    private fun shortId(ctx: Context): String {
        val raw = Utils.getSystemProp("persist.sys.serialno").ifBlank {
            Utils.getSystemProp("ro.serialno").ifBlank {
                Utils.getSystemProp("persist.sys.vincode").ifBlank {
                    android.provider.Settings.Secure.getString(
                        ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID
                    ) ?: "?"
                }
            }
        }
        return raw.take(8)
    }

    /** 目录自带身份，pull 走一份就知道哪台车。 */
    fun identityText(ctx: Context): String {
        val pkg = runCatching {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            "${pi.versionName} (${pi.versionCode})"
        }.getOrDefault("?")
        return buildString {
            append("app=").append(ctx.packageName).append('\n')
            append("version=").append(pkg).append('\n')
            append("android=").append(Build.VERSION.RELEASE)
            append(" (api ").append(Build.VERSION.SDK_INT).append(")\n")
            append("model=").append(Build.MODEL).append('\n')
            append("serial=").append(shortId(ctx)).append('\n')
            append("time=").append(Date().toString()).append('\n')
        }
    }

    private fun writeOwnCrash(thread: Thread?, ex: Throwable) {
        val ctx = appCtx
        val text = buildString {
            append("time=").append(Date().toString()).append('\n')
            append("thread=").append(thread?.name ?: "?").append('\n')
            append("android=").append(Build.VERSION.RELEASE)
            append(" (api ").append(Build.VERSION.SDK_INT).append(")\n")
            append("model=").append(Build.MODEL).append('\n')
            if (ctx != null) append("identity: ").append(oneLine(ctx)).append('\n')
            append('\n')
            append(Log.getStackTraceString(ex)).append('\n')
        }
        val out = (if (ctx != null) resolveOutDir(ctx) else null) ?: File(DIR)
        pruneDir(out, MAX_FILES, MAX_BYTES)
        writeFile(File(out, "crash_${stamp()}.txt"), text)
        Log.i(TAG, "crash: 已落盘 ${out.absolutePath}")
    }

    private fun oneLine(ctx: Context): String =
        "${Build.MODEL}/${Build.VERSION.RELEASE}/id=${shortId(ctx)}"

    /** 自动收集（带节流）。只在后台线程调。 */
    fun autoCollect(ctx: Context) {
        val last = runCatching {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_COLLECT, 0L)
        }.getOrDefault(0L)
        if (System.currentTimeMillis() - last < AUTO_INTERVAL_MS) return
        collect(ctx, false)
    }

    private fun resolveOutDir(ctx: Context): File? {
        val sd = File(DIR)
        if ((sd.exists() || sd.mkdirs()) && sd.canWrite()) {
            lastDir = DIR
            return sd
        }
        val inner = File(ctx.filesDir, "crash")
        if (inner.exists() || inner.mkdirs()) {
            lastDir = inner.absolutePath
            return inner
        }
        return null
    }

    /**
     * 收集一次。
     *
     * @param force true = 手动触发，不写节流时间也不受节流限制
     * @return 人可读摘要（**一定**写 logcat）
     */
    fun collect(ctx: Context, force: Boolean): String {
        if (!force && Utils.getSystemProp(PROP_ON) == "0") {
            val r = "crash: 已关闭（$PROP_ON=0）；要看就把属性设成 1"
            Log.i(TAG, r)
            return r
        }
        val r = doCollect(ctx, force)
        // 结果只在这里打日志：以前在内部提前 return 就丢了这条，远程看 adb 的人永远等不到结果。
        Log.i(TAG, r.replace('\n', ' '))
        return r
    }

    private fun doCollect(ctx: Context, force: Boolean): String {
        val sb = StringBuilder()
        var copied = 0
        try {
            val out = resolveOutDir(ctx)
                ?: return "crash: 没有可写目录（/sdcard/PSA/crash 与 App 内部目录都建不出来）"
            runCatching { writeFile(File(out, "identity.txt"), identityText(ctx)) }

            // ① dropbox：系统把每个崩溃/ANR 存一条，含完整堆栈。
            val db = File("/data/system/dropbox")
            val fs = db.listFiles()
            if (fs == null) {
                sb.append("dropbox: 读不到（").append(db.absolutePath)
                    .append("；需要与 system 同 uid）\n")
            } else {
                val sorted = fs.sortedByDescending { it.lastModified() }
                var n = 0
                for (f in sorted) {
                    if (n >= MAX_DROPBOX_PER_RUN) break
                    val name = f.name.lowercase(Locale.US)
                    val interesting = name.contains("crash") || name.contains("anr") ||
                        name.contains("tombstone") || name.contains("watchdog")
                    if (!interesting || !f.isFile) continue
                    n++
                    val dst = File(out, "dropbox_${f.name}")
                    if (dst.exists() && dst.length() == f.length()) continue
                    if (copyFile(f, dst)) copied++
                }
                sb.append("dropbox: 命中 ").append(n).append(" 条 crash/anr，新复制 ")
                    .append(copied).append(" 个\n")
            }

            // ② logcat -b crash：native/低层崩溃只在 logcat 里。
            val r = ShellUtils.exec("logcat -b crash -d -v time -t $LOG_TAIL")
            val lc = r.output
            if (lc.isNotBlank()) {
                writeFile(File(out, "logcat_crash_${stamp()}.txt"), lc)
                sb.append("logcat -b crash: ").append(lc.split("\n").size).append(" 行 → 已落盘\n")
            } else {
                sb.append("logcat -b crash: 空或读不到（需要 READ_LOGS；platform 签名可获得）\n")
            }

            sb.append("本应用崩溃文件: ").append(countOwn(out)).append(" 个\n")
        } catch (t: Throwable) {
            sb.append("收集异常（已忽略）：").append(t).append('\n')
        }

        if (copied > 0 || force) {
            val d = lastDir ?: DIR
            sb.append("目录：").append(d)
                .append(
                    if (d.startsWith("/sdcard")) "（用 adb pull /sdcard/PSA/crash 取回）"
                    else "（App 内部目录：/sdcard 写不了时的后备）"
                ).append('\n')
        }
        if (!force) {
            runCatching {
                ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong(KEY_LAST_COLLECT, System.currentTimeMillis()).apply()
            }
        }
        return "crash: $sb"
    }

    /** 一行摘要（轻量：只数文件、看最新一个，不复制任何东西）。 */
    fun summary(ctx: Context): String {
        val sd = summaryOf(File(DIR))
        if (!sd.startsWith("0 个")) return "$sd  ← /sdcard/PSA/crash"
        val inner = runCatching { summaryOf(File(ctx.filesDir, "crash")) }.getOrDefault("0 个")
        return if (!inner.startsWith("0 个")) "$inner  ← App 内部目录（/sdcard 写不了）" else sd
    }

    private fun summaryOf(dir: File): String {
        val fs = runCatching { dir.listFiles() }.getOrNull()
        if (fs == null || fs.isEmpty()) return "0 个（未收集过）"
        val total = fs.sumOf { if (it.isFile) it.length() else 0L }
        val newest = fs.maxByOrNull { it.lastModified() }
        return "${fs.size} 个 / ${total / 1024} KB，最新 ${newest?.name}" +
            "（${newest?.let { Date(it.lastModified()).toString() } ?: "?"}）"
    }

    private fun countOwn(dir: File): Int {
        val fs = runCatching { dir.listFiles() }.getOrNull() ?: return 0
        return fs.count { it.name.startsWith("crash_") }
    }

    private fun copyFile(src: File, dst: File): Boolean = try {
        dst.parentFile?.mkdirs()
        src.inputStream().use { i -> FileOutputStream(dst).use { o -> i.copyTo(o, 1 shl 16) } }
        true
    } catch (t: Throwable) {
        false
    }

    private fun writeFile(f: File, text: String) {
        runCatching {
            f.parentFile?.mkdirs()
            FileOutputStream(f).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        }
    }

    private fun stamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    /** 修剪目录：超过 maxFiles 或总字节超过 maxBytes 时删最旧的。 */
    fun pruneDir(dir: File, maxFiles: Int, maxBytes: Long) {
        try {
            val fs = dir.listFiles() ?: return
            if (fs.isEmpty()) return
            val sorted = fs.sortedBy { it.lastModified() }
            var total = sorted.sumOf { if (it.isFile) it.length() else 0L }
            var keep = sorted.size
            for (f in sorted) {
                if (f.isFile && (keep > maxFiles || total > maxBytes)) {
                    val len = f.length()
                    if (f.delete()) {
                        keep--
                        total -= len
                    }
                }
            }
        } catch (t: Throwable) {
        }
    }
}
