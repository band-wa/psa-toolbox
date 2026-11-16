package com.bandwa.openadb.util

import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object ShellUtils {
    data class Result(val exitCode: Int, val output: String) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    /** 命令的等待上限；pm 类命令正常几百毫秒，超过就是有别的毛病。 */
    private const val TIMEOUT_MS = 20_000L

    /** 仅限命令串完全由本仓库拼出、不含外部输入时使用；带参数（包名、路径）一律用 [execArgs]。 */
    fun exec(command: String): Result = execArgs(listOf("sh", "-c", command))

    /**
     * 按 argv 直接执行，参数里的 `;`、`$()`、引号都只是普通字符。
     *
     * 输出在独立线程里读：子进程写满管道就会停住，不能等它退出后才开始读；反过来先读干净
     * 再 waitFor 又会让超时失效（读操作本身就一直阻塞到进程结束）。超时返回 exitCode = -1，
     * 进程已被销毁。
     */
    fun execArgs(args: List<String>, timeoutMs: Long = TIMEOUT_MS): Result {
        val buf = StringBuilder()
        var process: Process? = null
        return try {
            val p = ProcessBuilder(args)
                .redirectErrorStream(true)
                .start()
            process = p
            val reader = thread(isDaemon = true, name = "shell-out") {
                runCatching {
                    p.inputStream.bufferedReader().use { r ->
                        val chunk = CharArray(4096)
                        while (true) {
                            val n = r.read(chunk)
                            if (n <= 0) break
                            synchronized(buf) { buf.append(chunk, 0, n) }
                        }
                    }
                }
            }
            if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
                p.destroyForcibly()
                reader.join(500)
                return Result(-1, "超时（${timeoutMs / 1000}s）")
            }
            reader.join(1000)
            Result(p.exitValue(), synchronized(buf) { buf.toString() })
        } catch (e: Exception) {
            process?.destroyForcibly()
            if (e is InterruptedException) Thread.currentThread().interrupt()
            Result(-1, e.message.orEmpty())
        }
    }
}
