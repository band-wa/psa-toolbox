package com.bandwa.openadb.service

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.bandwa.openadb.util.ShellUtils
import com.bandwa.openadb.util.Utils
import com.bandwa.openadb.service.install.ApkInstaller
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.InputStream
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** 局域网扫码上传服务；接口无鉴权，请勿暴露到不可信网络。 */
@RequiresApi(Build.VERSION_CODES.O)
class PicHttpServer(
    host: String,
    port: Int,
    private val context: Context,
) : NanoHTTPD(host, port) {
    override fun serve(session: IHTTPSession): Response {
        return when (val uri = session.uri) {
            "/upload-audio" -> handleAudioUpload(session)
            "/upload-script" -> handleScriptUpload(session)
            "/upload-apk" -> handleApkUpload(session)
            "/css/style.css" -> serveCSS()
            else -> handleImageUpload(session)
        }
    }

    private fun handleApkUpload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val uploadedFile = files.entries.firstOrNull()?.value

        if (uploadedFile != null) {
            val appDataDir = "/data/data/${context.applicationInfo.packageName}"
            val tempApkPath = "$appDataDir/temp.apk"

            File(tempApkPath).delete()
            saveFile(uploadedFile, appDataDir, "temp.apk")

            Utils.emitInstallEvent(Utils.ACTION_APK_INSTALL_STARTED)

            ApkInstaller.start(context, File(tempApkPath), reportToUi = true)
        }

        return newFixedLengthResponse(Response.Status.OK, "text/html", getCachedHtml("upload_apk.html"))
    }

    private fun handleImageUpload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val uploadedFile = files.entries.firstOrNull()?.value

        if (uploadedFile != null) {
            saveFile(uploadedFile, "/storage/emulated/0/launcherPic", "wallpaper.png")
            ShellUtils.exec("pm grant com.android.launcher android.permission.READ_EXTERNAL_STORAGE")
            ShellUtils.exec("am force-stop com.android.launcher")
        }

        return newFixedLengthResponse(Response.Status.OK, "text/html", getCachedHtml("index.html"))
    }

    private fun handleAudioUpload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val uploadedFile = files.entries.firstOrNull()?.value

        if (uploadedFile != null) {
            val appDataDir = "/data/data/${context.applicationInfo.packageName}"
            val audioFilesDir = "$appDataDir/audioFiles"
            saveFile(uploadedFile, audioFilesDir, "uploaded_audio.mp3")
        }

        return newFixedLengthResponse(Response.Status.OK, "text/html", getCachedHtml("upload_audio.html"))
    }

    private fun handleScriptUpload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val uploadedFile = files.entries.firstOrNull()?.value

        val appDataDir = "/data/data/${context.applicationInfo.packageName}"

        if (uploadedFile != null) {
            val scriptFilesDir = "$appDataDir/scriptFiles"
            saveFile(uploadedFile, scriptFilesDir, "user.sh")
        }

        val scriptContent = readScriptFile("$appDataDir/scriptFiles/user.sh")
        val htmlContent = getCachedHtml("upload_script.html").replace("{{scriptContent}}", scriptContent)

        return newFixedLengthResponse(Response.Status.OK, "text/html", htmlContent)
    }

    private fun readScriptFile(scriptFilePath: String): String {
        val scriptFile = File(scriptFilePath)
        return if (scriptFile.exists()) scriptFile.readText() else ""
    }

    private fun serveCSS(): Response {
        return newFixedLengthResponse(Response.Status.OK, "text/css", getCachedHtml("css/style.css"))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun saveFile(uploadedFile: String, destinationDirectory: String, fileName: String) {
        val sourceFile = File(uploadedFile)
        val destinationDir = File(destinationDirectory)
        if (!destinationDir.exists()) {
            destinationDir.mkdirs()
        }
        val destinationFile = File(destinationDir, fileName)
        Files.copy(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    companion object {
        private val htmlCache = mutableMapOf<String, String>()

        private fun getCachedHtml(fileName: String): String {
            return htmlCache.getOrPut(fileName) {
                val inputStream: InputStream =
                    PicHttpServer::class.java.classLoader!!.getResourceAsStream("assets/$fileName")
                inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            }
        }

        fun findAvailablePort(): Int {
            var serverSocket: ServerSocket? = null
            return try {
                serverSocket = ServerSocket(0)
                serverSocket.localPort
            } finally {
                serverSocket?.close()
            }
        }
    }
}
