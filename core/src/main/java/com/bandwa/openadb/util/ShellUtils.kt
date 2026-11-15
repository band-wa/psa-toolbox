package com.bandwa.openadb.util

object ShellUtils {
    data class Result(val exitCode: Int, val output: String) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    fun exec(command: String): Result {
        var process: Process? = null
        return try {
            process = ProcessBuilder("sh", "-c", command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            Result(process.exitValue(), output)
        } catch (e: Exception) {
            process?.destroy()
            if (e is InterruptedException) Thread.currentThread().interrupt()
            Result(-1, e.message.orEmpty())
        }
    }
}
