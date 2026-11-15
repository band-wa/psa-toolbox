package com.bandwa.openadb.util.apktools

import java.io.File
import java.util.zip.ZipFile

object ApkInspector {
    private val HOOK_MARKER = "Lcom/bandwa/hook/Hook;".toByteArray(Charsets.US_ASCII)

    fun containsHookClasses(apkPath: String?): Boolean {
        if (apkPath == null) return false
        val apk = File(apkPath)
        if (!apk.isFile) return false
        return try {
            ZipFile(apk).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.startsWith("classes") && it.name.endsWith(".dex") }
                    .any { entry -> zip.getInputStream(entry).use { stream -> streamContains(stream, HOOK_MARKER) } }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun streamContains(stream: java.io.InputStream, marker: ByteArray): Boolean {
        val chunk = ByteArray(64 * 1024 + marker.size)
        var carry = 0
        while (true) {
            val read = stream.read(chunk, carry, chunk.size - carry)
            if (read <= 0) return false
            val length = carry + read
            var i = 0
            while (i <= length - marker.size) {
                var matched = true
                for (j in marker.indices) {
                    if (chunk[i + j] != marker[j]) {
                        matched = false
                        break
                    }
                }
                if (matched) return true
                i++
            }
            carry = minOf(marker.size - 1, length)
            System.arraycopy(chunk, length - carry, chunk, 0, carry)
        }
    }
}
