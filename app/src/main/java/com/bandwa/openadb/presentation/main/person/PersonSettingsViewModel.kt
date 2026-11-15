package com.bandwa.openadb.presentation.main.person

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandwa.openadb.service.ServiceLauncher.showQRCode
import com.bandwa.openadb.util.AvatarMod
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class PersonSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PersonSettingsUIState())
    val uiState = _uiState.asStateFlow()

    private val localPrefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

    init {
        _uiState.update {
            it.copy(shouldRemindCloseAss = localPrefs.getBoolean(KEY_REMIND_CLOSE_ASS, false))
        }
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

    fun updateAvatarFromQq(qqNumber: String) {
        val avatarUrl = "https://q1.qlogo.cn/g?b=qq&nk=$qqNumber&s=100"
        AvatarMod.setMemberCenterHeadImgUrl(context, avatarUrl)
    }

    private companion object {
        const val KEY_REMIND_CLOSE_ASS = "shouldRemindCloseAss"
    }
}
