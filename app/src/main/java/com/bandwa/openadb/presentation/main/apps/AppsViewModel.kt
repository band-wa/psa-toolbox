package com.bandwa.openadb.presentation.main.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandwa.openadb.service.InstallShafa.installSFGJ
import com.bandwa.openadb.service.ServiceLauncher.showQRCode
import com.bandwa.openadb.service.install.ApkInstaller
import com.bandwa.openadb.util.AppEntry
import com.bandwa.openadb.util.AppManage
import com.bandwa.openadb.util.Utils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AppsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppsUIState())
    val uiState = _uiState.asStateFlow()

    // 扫码无线安装：手机上传完 APK 后，服务侧发 ACTION_APK_INSTALL_STARTED，
    // 这里跟着显示进度框；装好（PACKAGE_ADDED 或 PackageInstaller 回执）就补满进度。
    private var installProgressJob: Job? = null
    private var installCompleting = false

    private val packageAddedReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_PACKAGE_ADDED && _uiState.value.remoteInstallRunning) {
                markRemoteInstallCompleting()
                refresh()
            }
        }
    }

    private val installResultReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent == null || !intent.hasExtra(PackageInstaller.EXTRA_STATUS)) {
                Log.w(TAG, "commit broadcast without EXTRA_STATUS, ignore")
                return
            }
            val status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE
            )
            val rawMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            Log.d(TAG, "install result: status=$status, msg=$rawMessage")
            when (status) {
                PackageInstaller.STATUS_SUCCESS -> {
                    if (_uiState.value.remoteInstallRunning) markRemoteInstallCompleting()
                }
                PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                }
                else -> {
                    if (!_uiState.value.remoteInstallRunning) return
                    installProgressJob?.cancel()
                    _uiState.update {
                        it.copy(
                            remoteInstallRunning = false,
                            remoteInstallProgress = 0f,
                            remoteInstallProgressText = "",
                        )
                    }
                    Toast.makeText(
                        context,
                        Utils.mapInstallErrorMessage(rawMessage),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    init {
        refresh()
        val packageFilter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
            addDataScheme("package")
        }
        context.registerReceiver(packageAddedReceiver, packageFilter)
        context.registerReceiver(installResultReceiver, IntentFilter(Utils.ACTION_INSTALL_COMMIT))

        viewModelScope.launch {
            Utils.installEvents.collect { event ->
                if (event == Utils.ACTION_APK_INSTALL_STARTED) startRemoteInstall()
            }
        }
    }

    override fun onCleared() {
        installProgressJob?.cancel()
        runCatching { context.unregisterReceiver(packageAddedReceiver) }
        runCatching { context.unregisterReceiver(installResultReceiver) }
        super.onCleared()
    }

    fun refresh() = viewModelScope.launch {
        _uiState.update { it.copy(scanning = true) }
        val rows = withContext(Dispatchers.IO) { AppManage.scan(context) }
        _uiState.update { it.copy(rows = AppManage.sort(rows, it.sort), scanning = false) }
    }

    fun setSort(by: AppManage.Sort) {
        _uiState.update { it.copy(sort = by, rows = AppManage.sort(it.rows, by)) }
    }

    /** 清缓存（[cacheOnly]=true）或清数据。 */
    fun clear(row: AppEntry, cacheOnly: Boolean) = viewModelScope.launch {
        val what = if (cacheOnly) "缓存" else "数据"
        _uiState.update { it.copy(busyPkg = row.pkg, busyText = "正在清除${row.label}的$what…") }
        val err = withContext(Dispatchers.IO) { AppManage.clear(context, row.pkg, cacheOnly) }
        _uiState.update {
            it.copy(
                busyPkg = null,
                busyText = "",
                // 注意：中文也是合法标识符字符，`$what已清除` 会被当成变量名 ⇒ 必须用 ${'$'}{what}
                message = if (err == null) "${row.label}的$what 已清除" else "清除失败：$err",
            )
        }
    }

    /** 卸载更新 / 卸载应用。走 PackageInstaller（不依赖 shell）。 */
    fun uninstall(row: AppEntry) = viewModelScope.launch {
        val asUpdate = AppManage.uninstallKind(row) == AppManage.UninstallKind.UPDATE
        _uiState.update {
            it.copy(
                busyPkg = row.pkg,
                busyText = if (asUpdate) "正在卸载${row.label}的更新…" else "正在卸载${row.label}…",
            )
        }
        val ok = withContext(Dispatchers.IO) { ApkInstaller.uninstall(context, row.pkg) }
        if (ok) {
            // 卸载完包信息变了，重新扫一遍
            val rows = withContext(Dispatchers.IO) { AppManage.scan(context) }
            _uiState.update {
                it.copy(
                    rows = AppManage.sort(rows, it.sort),
                    busyPkg = null,
                    busyText = "",
                    message = if (asUpdate) "${row.label}的更新已卸载" else "${row.label}已卸载",
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    busyPkg = null,
                    busyText = "",
                    message = if (asUpdate) "卸载更新失败" else "卸载失败",
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    // ---- 扫码无线安装 ----

    fun requestApkQrCode() {
        showQRCode(context, "apk") { bitmap ->
            _uiState.update { it.copy(apkQrCodeBitmap = bitmap) }
        }
    }

    /** 收起二维码：二维码是个大图，一直挂着会把下面的应用列表挤出屏幕。 */
    fun hideApkQrCode() {
        _uiState.update { it.copy(apkQrCodeBitmap = null) }
    }

    private fun startRemoteInstall() {
        installProgressJob?.cancel()
        installCompleting = false
        _uiState.update {
            it.copy(
                remoteInstallRunning = true,
                remoteInstallProgress = 0f,
                remoteInstallProgressText = "正在解析并安装，请稍候...",
            )
        }
        installProgressJob = viewModelScope.launch {
            val increment = 0.9f / REMOTE_INSTALL_STEPS
            repeat(REMOTE_INSTALL_STEPS) {
                delay(1_000)
                _uiState.update { state ->
                    state.copy(
                        remoteInstallProgress = (state.remoteInstallProgress + increment)
                            .coerceAtMost(0.9f)
                    )
                }
            }

            delay(REMOTE_INSTALL_TIMEOUT_MS)
            if (_uiState.value.remoteInstallRunning) {
                _uiState.update {
                    it.copy(
                        remoteInstallRunning = false,
                        remoteInstallProgress = 0f,
                        remoteInstallProgressText = "",
                    )
                }
                Toast.makeText(context, "安装超时，请检查网络或 APK 后重试", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun markRemoteInstallCompleting() {
        if (installCompleting) return
        installCompleting = true
        installProgressJob?.cancel()
        installProgressJob = viewModelScope.launch {
            _uiState.update { it.copy(remoteInstallProgressText = "即将完成...") }
            val step = (1.0f - _uiState.value.remoteInstallProgress) / INSTALL_COMPLETING_STEPS
            repeat(INSTALL_COMPLETING_STEPS) {
                delay(50)
                _uiState.update { state ->
                    state.copy(
                        remoteInstallProgress = (state.remoteInstallProgress + step)
                            .coerceAtMost(1f)
                    )
                }
            }
            _uiState.update { it.copy(remoteInstallProgress = 1f) }
            delay(1_000)
            _uiState.update {
                it.copy(
                    remoteInstallRunning = false,
                    remoteInstallProgress = 0f,
                    remoteInstallProgressText = "",
                )
            }
        }
    }

    fun dismissRemoteInstall() {
        installProgressJob?.cancel()
        _uiState.update {
            it.copy(
                remoteInstallRunning = false,
                remoteInstallProgress = 0f,
                remoteInstallProgressText = "",
            )
        }
    }

    // ---- 安装应用市场（沙发管家） ----

    fun installShafaApp() = viewModelScope.launch {
        _uiState.update { it.copy(shafaInstallProgress = 0f, shafaInstallProgressText = "") }
        installSFGJ(
            context,
            onProgress = { progress, text ->
                _uiState.update {
                    it.copy(shafaInstallProgress = progress, shafaInstallProgressText = text)
                }
            },
            onFinished = { _, message ->
                _uiState.update { it.copy(shafaInstallProgress = -1f) }
                if (message != null) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            },
        )
    }

    private companion object {
        const val TAG = "AppsInstall"
        const val REMOTE_INSTALL_STEPS = 20
        const val INSTALL_COMPLETING_STEPS = 20
        const val REMOTE_INSTALL_TIMEOUT_MS = 90_000L
    }
}
