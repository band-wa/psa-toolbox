package com.bandwa.openadb.util.apk

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/** APK 下载与 MD5 校验：`.part` 原子写入、同目标文件串行、进度回调在 IO 线程。 */
object ApkDownloader {
    private const val TAG = "ApkDownloader"

    private const val BUFFER_SIZE = 8 * 1024

    private val downloadLocks = HashMap<String, Mutex>()

    fun downloadDir(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun cleanupDownloads(context: Context) {
        val files = downloadDir(context).listFiles() ?: return
        for (file in files) {
            if (!file.isFile || !isInstallerFileName(file.name)) continue
            if (!file.delete()) Log.w(TAG, "cleanup failed: ${file.absolutePath}")
        }
    }

    internal fun isInstallerFileName(name: String): Boolean =
        name.endsWith(".apk", ignoreCase = true) || name.endsWith(".part", ignoreCase = true)

    fun fileNameFromUrl(url: String, fallback: String = "download.apk"): String {
        val raw = url.substringBefore('?').substringBefore('#').substringAfterLast('/')

        // 仅保留 ASCII 字符，避免车机文件系统编码差异
        val safe = raw.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '.' || it == '_' || it == '-' }
        return if (safe.isNotBlank() && !safe.startsWith('.') && safe.length <= 64) safe else fallback
    }

    /** 下载到下载目录；expectedMd5 非空时在锁内校验，失败删除文件。 */
    suspend fun download(
        context: Context,
        url: String,
        fileName: String = fileNameFromUrl(url),
        expectedMd5: String? = null,
        onProgress: ((percent: Int, bytesRead: Long, totalBytes: Long) -> Unit)? = null,
    ): File {
        val target = File(downloadDir(context), fileName)
        return lockFor(target.absolutePath).withLock {
            val file = downloadToFile(url, target, onProgress)
            if (expectedMd5 != null && !verifyMd5(file, expectedMd5)) {
                file.delete()
                throw IOException("安装包校验失败")
            }

            onProgress?.invoke(100, file.length(), file.length())
            file
        }
    }

    private fun lockFor(path: String): Mutex = synchronized(downloadLocks) {
        downloadLocks.getOrPut(path) { Mutex() }
    }

    private suspend fun downloadToFile(
        url: String,
        target: File,
        onProgress: ((percent: Int, bytesRead: Long, totalBytes: Long) -> Unit)?,
    ): File = withContext(Dispatchers.IO) {
        val temp = File(target.parentFile, "${target.name}.part")
        try {
            val request = Request.Builder().url(url).build()
            RemoteHttp.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}: $url")
                val body = response.body ?: throw IOException("响应体为空: $url")
                val total = body.contentLength()
                var read = 0L
                var lastPercent = -1
                body.byteStream().use { input ->
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count == -1) break
                            output.write(buffer, 0, count)
                            read += count
                            if (total > 0) {
                                val percent = (read * 100 / total).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress?.invoke(percent, read, total)
                                }
                            }
                        }
                        output.fd.sync()
                    }
                }
                if (total > 0 && read != total) throw IOException("下载不完整: $read/$total")
            }
            if (!temp.renameTo(target)) {
                target.delete()
                if (!temp.renameTo(target)) throw IOException("无法写入 ${target.absolutePath}")
            }
            target
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
    }

    suspend fun downloadVerified(
        context: Context,
        url: String,
        md5Url: String,
        fileName: String,
        onProgress: ((percent: Int, bytesRead: Long, totalBytes: Long) -> Unit)? = null,
    ): File {
        val expected = RemoteHttp.fetchText(md5Url).trim()
        if (expected.isEmpty()) throw IOException("校验文件为空: $md5Url")
        return download(context, url, fileName, expectedMd5 = expected, onProgress = onProgress)
    }

    fun md5Of(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifyMd5(file: File, expected: String): Boolean {
        val exp = expected.trim()
        return exp.isNotEmpty() && md5Of(file).equals(exp, ignoreCase = true)
    }
}
