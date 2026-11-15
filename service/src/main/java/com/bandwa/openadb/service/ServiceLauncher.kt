package com.bandwa.openadb.service

import android.content.Context
import android.widget.Toast
import com.bandwa.openadb.util.NetworkUtils
import com.bandwa.openadb.util.Utils
import com.bandwa.openadb.util.apk.RemoteHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ServiceLauncher {

    fun startProjection(context: Context, scope: CoroutineScope) = scope.launch(Dispatchers.IO) {
        var isConnected = checkNetworkConnection()
        if (!isConnected) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "网络不佳，联网后将自动投屏", Toast.LENGTH_SHORT).show()
            }
        }
        while (!isConnected) {
            delay(3000)
            isConnected = checkNetworkConnection()
        }
        val intent = android.content.Intent(context, PSAToolBoxService::class.java).apply {
            putExtra(PSAToolBoxService.EXTRA_ACTION_KEY, ToolBoxServiceWrapper.ACTION_START_PROJECTION)
        }
        context.startService(intent)
    }

    fun showQRCode(context: Context, type: String, onBitmapReady: (android.graphics.Bitmap) -> Unit) {
        if (!NetworkUtils.isConnectedToWifi(context)) {
            Toast.makeText(context, "请在车机连接手机热点后重试", Toast.LENGTH_SHORT).show()
            return
        }
        val ipAddress = NetworkUtils.getLocalIpAddress()
        if (ipAddress == null) {
            Toast.makeText(context, "已连接热点但未获取到车机IP，请稍后重试", Toast.LENGTH_SHORT).show()
            return
        }
        val port = PicHttpServerHost.ensureStarted(context, ipAddress)
        if (port == null) {
            Toast.makeText(context, "上传服务启动失败，请重试", Toast.LENGTH_SHORT).show()
            return
        }
        val qrCodeData = when (type) {
            "audio" -> "http://$ipAddress:$port/upload-audio"
            "script" -> "http://$ipAddress:$port/upload-script"
            "apk" -> "http://$ipAddress:$port/upload-apk"
            else -> "http://$ipAddress:$port/"
        }
        val bitmap = Utils.createQRCodeBitmap(qrCodeData, 300, 300)
        onBitmapReady(bitmap)
    }

    private fun checkNetworkConnection(): Boolean = RemoteHttp.isReachable()
}
