package com.bandwa.openadb.util

import android.util.Log

/** 解析 `am stack list` 并通过 `am display move-stack` 迁移 Task（Android 9）。 */
object AtmsHelper {
    private const val TAG = "AtmsHelper"

    data class RunningTask(
        val stackId: Int,
        val displayId: Int
    )

    fun findRunningTask(packageName: String): RunningTask? {
        return try {
            val output = ShellUtils.exec("am stack list 2>&1").output
            if (output.isBlank()) {
                Log.w(TAG, "am stack list returned empty output")
                return null
            }

            val lines = output.lines()
            var currentStackId = -1
            var currentDisplayId = -1

            for (line in lines) {
                val trimmed = line.trim()

                val headerMatch = Regex("Stack\\s+id=(\\d+).*displayId=(\\d+)").find(trimmed)
                if (headerMatch != null) {
                    currentStackId = headerMatch.groupValues[1].toIntOrNull() ?: -1
                    currentDisplayId = headerMatch.groupValues[2].toIntOrNull() ?: -1
                }

                if (trimmed.contains(packageName) &&
                    extractTaskId(trimmed) != null &&
                    currentStackId >= 0 &&
                    currentDisplayId >= 0
                ) {
                    return RunningTask(currentStackId, currentDisplayId)
                }
            }

            null
        } catch (e: Exception) {
            Log.e(TAG, "findRunningTask failed: ${e.message}", e)
            null
        }
    }

    private fun extractTaskId(line: String): Int? {
        return Regex("taskId=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull()
    }

    fun moveTaskToDisplay(stackId: Int, displayId: Int): Boolean {
        val cmd = "am display move-stack $stackId $displayId 2>&1"
        return try {
            val output = ShellUtils.exec(cmd).output

            val failed = output.contains("Error", ignoreCase = true)
                || output.contains("Exception", ignoreCase = true)
                || output.contains("not found", ignoreCase = true)
                || output.contains("Unknown", ignoreCase = true)
            if (failed) {
                Log.e(TAG, "moveTaskToDisplay($stackId, $displayId) failed: $output")
            }
            !failed
        } catch (e: Exception) {
            Log.e(TAG, "$cmd failed: ${e.message}")
            false
        }
    }
}
