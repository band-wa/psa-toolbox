package com.bandwa.openadb.presentation.main.navi

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandwa.openadb.util.HackAmap.clearAmapActivationCode
import com.bandwa.openadb.util.HackAmap.isAmapSafe
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class NaviSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NaviSettingsUIState())
    val uiState = _uiState.asStateFlow()

    init {
        checkAmapSignature()
    }

    private fun checkAmapSignature() = viewModelScope.launch {
        val safe = withContext(Dispatchers.IO) { isAmapSafe(context) }
        _uiState.update { it.copy(isAmapSafe = safe) }
    }

    fun activateAmap() {
        clearAmapActivationCode()
        Toast.makeText(context, "激活高德成功！", Toast.LENGTH_SHORT).show()
    }
}
