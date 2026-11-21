package com.bandwa.openadb.presentation.main.expert

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import com.bandwa.openadb.util.OpenSysSetting.openRunningServices
import com.bandwa.openadb.util.StorageOptimize
import com.bandwa.openadb.util.Utils.APP_AUTO_GRANT_KEY
import com.bandwa.openadb.util.Utils.COMPAT_MODE_KEY
import com.bandwa.openadb.util.Utils.PREFS_NAME
import com.bandwa.openadb.service.ServiceLauncher.showQRCode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class ExpertModeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExpertModeUIState())
    val uiState = _uiState.asStateFlow()

    private val localPrefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

    private val corePrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        restoreSettings()
    }

    private fun restoreSettings() {
        _uiState.update {
            it.copy(
                isListMod = localPrefs.getBoolean(KEY_LIST_MOD, false),
                isCompatMode = corePrefs.getBoolean(COMPAT_MODE_KEY, false),
                isAppAutoGrant = corePrefs.getBoolean(APP_AUTO_GRANT_KEY, true),
                isStorageOptimized = StorageOptimize.isEnabled(context),
                shouldRunUserSh = localPrefs.getBoolean(KEY_RUN_USER_SH, false),
            )
        }
    }

    fun setListMod(enabled: Boolean) {
        localPrefs.edit().putBoolean(KEY_LIST_MOD, enabled).commit()
        _uiState.update { it.copy(isListMod = enabled) }
    }

    fun setCompatMode(enabled: Boolean) {
        corePrefs.edit().putBoolean(COMPAT_MODE_KEY, enabled).apply()
        _uiState.update { it.copy(isCompatMode = enabled) }
        val message = if (enabled) {
            "兼容模式已开启，重启工具箱后生效"
        } else {
            "兼容模式已关闭，重启工具箱后生效"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun setAppAutoGrant(enabled: Boolean) {
        corePrefs.edit().putBoolean(APP_AUTO_GRANT_KEY, enabled).apply()
        _uiState.update { it.copy(isAppAutoGrant = enabled) }
        val message = if (enabled) {
            "应用自动授权已开启"
        } else {
            "应用自动授权已关闭"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun setStorageOptimized(enabled: Boolean) {
        StorageOptimize.setEnabled(context, enabled)
        _uiState.update { it.copy(isStorageOptimized = enabled) }
        val message = if (enabled) {
            "存储空间优化已开启"
        } else {
            "存储空间优化已关闭"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun setShouldRunUserSh(enabled: Boolean) {
        localPrefs.edit().putBoolean(KEY_RUN_USER_SH, enabled).commit()
        _uiState.update { it.copy(shouldRunUserSh = enabled) }
    }

    fun requestScriptQrCode() {
        showQRCode(context, "script") { bitmap ->
            _uiState.update { it.copy(scriptQrCodeBitmap = bitmap) }
        }
    }

    fun viewRunningServices() {
        openRunningServices(context)
    }

    private companion object {
        const val KEY_LIST_MOD = "isListMod"
        const val KEY_RUN_USER_SH = "shouldRunUserSh"
    }
}
