package com.bandwa.openadb.presentation.main.fuel

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandwa.openadb.util.FuelLedger
import com.bandwa.openadb.util.Utils
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
class FuelLedgerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FuelLedgerUIState())
    val uiState = _uiState.asStateFlow()

    private val prefs = context.getSharedPreferences(Utils.PREFS_NAME, Context.MODE_PRIVATE)

    init {
        _uiState.update {
            it.copy(
                promptEnabled = prefs.getBoolean(Utils.FUEL_PROMPT_KEY, false),
                tankLiters = prefs.getInt(Utils.FUEL_TANK_KEY, Utils.FUEL_TANK_DEFAULT),
            )
        }
        reload()
    }

    fun reload() = viewModelScope.launch {
        val tankLiters = _uiState.value.tankLiters
        val entries = withContext(Dispatchers.IO) {
            FuelLedger.readAll(context).sortedByDescending { it.timestamp }
        }
        _uiState.update { it.withDerived(entries, tankLiters) }
    }

    fun setPromptEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(Utils.FUEL_PROMPT_KEY, enabled).apply()
        _uiState.update { it.copy(promptEnabled = enabled) }
        val message = if (enabled) "已开启加油记账提醒" else "已关闭加油记账提醒"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun setTankLiters(liters: Int) {
        prefs.edit().putInt(Utils.FUEL_TANK_KEY, liters).apply()
        _uiState.update {
            it.withDerived(it.entries, liters)
        }
    }

    fun deleteEntry(entry: FuelLedger.Entry) = viewModelScope.launch {
        withContext(Dispatchers.IO) { FuelLedger.delete(context, entry) }
        reload()
    }

    fun updateEntryAmount(entry: FuelLedger.Entry, amount: Int) = viewModelScope.launch {
        val updated = withContext(Dispatchers.IO) { FuelLedger.updateAmount(context, entry, amount) }
        val message = if (updated) "已更新金额 ¥$amount" else "保存失败，请重试"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        reload()
    }

    fun addManualEntry(amount: Int) = viewModelScope.launch {
        val appended = withContext(Dispatchers.IO) {
            FuelLedger.append(
                context,
                FuelLedger.Entry(
                    timestamp = System.currentTimeMillis(),
                    amount = amount,
                    percentBefore = -1,
                    percentAfter = -1,
                    totalMileageKm = null,
                    manual = true,
                ),
            )
        }
        if (appended) {
            Toast.makeText(context, "已补录加油 ¥$amount", Toast.LENGTH_SHORT).show()
        }
        reload()
    }

    private fun FuelLedgerUIState.withDerived(
        entries: List<FuelLedger.Entry>,
        tankLiters: Int,
    ): FuelLedgerUIState {
        val totalAmount = entries.sumOf { it.amount }
        val averageAmount = if (entries.isEmpty()) 0 else totalAmount / entries.size
        val segments = FuelLedger.consumptionByEntry(entries, tankLiters)
        val averagePer100Km = if (segments.isEmpty()) {
            null
        } else {
            val km = segments.values.sumOf { it.km.toDouble() }
            val liters = segments.values.sumOf { it.liters.toDouble() }
            if (km > 0) (liters * 100.0 / km).toFloat() else null
        }
        val knownCostSegments = segments.values.filter { it.costPerKm != null }
        val averageCostPerKm = if (knownCostSegments.isEmpty()) {
            null
        } else {
            val km = knownCostSegments.sumOf { it.km.toDouble() }
            val cost = knownCostSegments.sumOf { (it.costPerKm!! * it.km).toDouble() }
            if (km > 0) (cost / km).toFloat() else null
        }
        return copy(
            entries = entries,
            segments = segments,
            totalAmount = totalAmount,
            averageAmount = averageAmount,
            averagePer100Km = averagePer100Km,
            averageCostPerKm = averageCostPerKm,
            tankLiters = tankLiters,
        )
    }
}
