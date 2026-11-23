package com.bandwa.openadb.presentation.main.person

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandwa.openadb.util.AvatarMod
import com.bandwa.openadb.service.InstallShafa.installSFGJ
import com.bandwa.openadb.util.Utils
import com.bandwa.openadb.util.WifiStateMonitor
import com.bandwa.openadb.service.ServiceLauncher.showQRCode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PersonSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PersonSettingsUIState())
    val uiState = _uiState.asStateFlow()

    private val localPrefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

    private var installProgressJob: Job? = null

    private var installCompleting = false

    private val packageAddedReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_PACKAGE_ADDED) {
                if (_uiState.value.remoteInstallRunning) {
                    markRemoteInstallCompleting()
                }
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
                    if (_uiState.value.remoteInstallRunning) {
                        markRemoteInstallCompleting()
                    }
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
        _uiState.update {
            it.copy(shouldRemindCloseAss = localPrefs.getBoolean(KEY_REMIND_CLOSE_ASS, false))
        }
        val packageFilter = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply {
            addDataScheme("package")
        }
        context.registerReceiver(packageAddedReceiver, packageFilter)
        context.registerReceiver(installResultReceiver, IntentFilter(Utils.ACTION_INSTALL_COMMIT))

        viewModelScope.launch {
            Utils.installEvents.collect { event ->
                if (event == Utils.ACTION_APK_INSTALL_STARTED) {
                    startRemoteInstall()
                }
            }
        }

        viewModelScope.launch {
            WifiStateMonitor.isWifiConnected.collect { connected ->
                if (!connected) {
                    _uiState.update {
                        it.copy(welcomeQrCodeBitmap = null, apkQrCodeBitmap = null)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        installProgressJob?.cancel()
        runCatching { context.unregisterReceiver(packageAddedReceiver) }
        runCatching { context.unregisterReceiver(installResultReceiver) }
        super.onCleared()
    }

    fun setShouldRemindCloseAss(enabled: Boolean) {
        localPrefs.edit().putBoolean(KEY_REMIND_CLOSE_ASS, enabled).apply()
        _uiState.update { it.copy(shouldRemindCloseAss = enabled) }
    }

    fun requestWelcomeQrCode() {
        showQRCode(context, "audio") { bitmap ->
            _uiState.update { it.copy(welcomeQrCodeBitmap = bitmap) }
        }
    }

    fun requestApkQrCode() {
        showQRCode(context, "apk") { bitmap ->
            _uiState.update { it.copy(apkQrCodeBitmap = bitmap) }
        }
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

    fun installShafaApp() = viewModelScope.launch {
        _uiState.update {
            it.copy(shafaInstallProgress = 0f, shafaInstallProgressText = "")
        }
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

    fun updateAvatarFromQq(qqNumber: String) {
        val avatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=$qqNumber&s=100"
        AvatarMod.setMemberCenterHeadImgUrl(context, avatarUrl)
    }

    private companion object {
        const val TAG = "PersonInstall"
        const val KEY_REMIND_CLOSE_ASS = "shouldRemindCloseAss"
        const val REMOTE_INSTALL_STEPS = 20
        const val INSTALL_COMPLETING_STEPS = 20
        const val REMOTE_INSTALL_TIMEOUT_MS = 90_000L
    }
}
