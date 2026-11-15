package com.bandwa.openadb.presentation.main.tools

import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager.CarPropertyEventListener
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bandwa.openadb.util.CarGateway
import com.bandwa.openadb.util.HotFixUdisk.fixUdisk
import com.bandwa.openadb.util.Utils.getSystemProp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ToolsSettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ToolsSettingsUIState())
    val uiState = _uiState.asStateFlow()

    private val propertyEventListener = object : CarPropertyEventListener {
        override fun onChangeEvent(value: CarPropertyValue<*>?) {
            val raw = value?.value ?: return
            _uiState.update { state -> applyValue(state, value.propertyId, raw) }
        }

        override fun onErrorEvent(propId: Int, areaId: Int) {
            Log.d(TAG, "onErrorEvent ${propToString(propId)} area=$areaId")
        }
    }

    init {
        observeGateway()
    }

    override fun onCleared() {
        CarGateway.unregister(propertyEventListener)
        super.onCleared()
    }

    private fun observeGateway() {
        CarGateway.ensureStarted(context)

        viewModelScope.launch(Dispatchers.IO) {
            CarGateway.manager.collect { manager ->
                if (manager == null) {
                    _uiState.value = ToolsSettingsUIState()
                    return@collect
                }

                _uiState.update { it.copy(isOnlyUnlockTrunkUsable = true) }

                WATCHED_PROPERTIES.forEach { prop ->
                    val value = try {
                        manager.getProperty<Any>(prop, 0)?.value
                    } catch (e: Exception) {
                        Log.d(TAG, "Snapshot read ${propToString(prop)} failed: ${e.javaClass.simpleName} ${e.message}")
                        null
                    }
                    if (value != null) {
                        _uiState.update { state -> applyValue(state, prop, value) }
                    }
                }

                WATCHED_PROPERTIES.forEach { prop ->
                    CarGateway.register(propertyEventListener, prop, rateOf(prop))
                }
            }
        }
    }

    private fun applyValue(state: ToolsSettingsUIState, propId: Int, raw: Any): ToolsSettingsUIState {
        return when (propId) {
            INFO_VIN -> state.copy(vinCode = raw as? String ?: state.vinCode)

            INFO_VIN_MATCH_STATE -> {
                val match = raw as? Int ?: return state
                state.copy(isVinCodeMatchSystem = vinCodeMatchConvert(match))
            }

            VEHICLE_TOTAL_MILEAGE -> {
                val km = raw as? Float ?: return state
                state.copy(totalMileage = "${km.toInt()}KM")
            }

            VEHICLE_REMAINING_OIL -> {
                val percent = raw as? Int ?: return state
                state.copy(remainOilPresent = "$percent%")
            }

            VEHICLE_AUTONOMIE_CYCLE -> {
                val km = raw as? Int ?: return state
                state.copy(remainDriveDistance = "${km}KM")
            }

            VEHICLE_FUEL_CONSUMPTION -> {
                val raw60 = raw as? Int ?: return state
                state.copy(
                    tripOilConsumption = String.format(Locale.ROOT, "%.3fL", raw60.toDouble() / 1_000_000)
                )
            }

            BODY_STEERING_WHEEL_STEERING_ANGLE -> {
                val angle = raw as? Int ?: return state
                state.copy(steeringAngle = "$angle°")
            }

            PERF_VEHICLE_SPEED -> {
                val speed = raw as? Float ?: return state
                state.copy(currentSpeed = String.format(Locale.ROOT, "%.0f km/h", speed))
            }

            GEAR -> {
                val gear = raw as? Int ?: return state
                state.copy(currentGearMode = gearConvert(gear))
            }

            BODY_DOOR_REAR_UNLOCKDOOR_MODE -> {
                val mode = raw as? Int ?: return state
                state.copy(
                    isOnlyUnlockTrunkUsable = true,
                    isOnlyUnlockTrunk = mode == 2
                )
            }

            else -> state
        }
    }

    fun changeOnlyUnlockTrunk(checked: Boolean) {
        if (getSystemProp("persist.sys.mcu_version") != "T93RR") {
            Toast.makeText(context, "抱歉，仅适配标致408", Toast.LENGTH_SHORT).show()
            return
        }
        val manager = CarGateway.manager.value
        if (manager == null) {
            Toast.makeText(context, "CarService 未连接，请稍后重试", Toast.LENGTH_SHORT).show()
            return
        }
        val mode = if (checked) MODE_UNLOCK_TRUNK_ONLY else MODE_UNLOCK_ALL_DOORS
        val ok = try {
            manager.setIntProperty(BODY_DOOR_REAR_UNLOCKDOOR_MODE_SET, 0, mode)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Set trunk-only unlock failed: ${e.javaClass.simpleName} ${e.message}")
            false
        }
        if (!ok) {
            Toast.makeText(context, "设置失败，请重试", Toast.LENGTH_SHORT).show()
            return
        }

        _uiState.update { it.copy(isOnlyUnlockTrunk = checked) }
        if (checked) {
            Toast.makeText(context, "已启用仅解锁后备箱", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "已禁用仅解锁后备箱", Toast.LENGTH_SHORT).show()
        }
    }

    fun fixUDisk() {
        val manager = CarGateway.manager.value
        if (manager == null) {
            Toast.makeText(context, "CarService 未连接，请稍后重试", Toast.LENGTH_SHORT).show()
            return
        }
        fixUdisk(context, manager)
    }

    private fun gearConvert(gearOriNum: Int): String {
        return when (gearOriNum) {
            1 -> "N"
            2 -> "R"
            4 -> "P"
            8 -> "D"
            16 -> "M"
            else -> "检测失败"
        }
    }

    private fun vinCodeMatchConvert(matchState: Int): String {
        return when (matchState) {
            1 -> "正常"
            0 -> "异常"
            else -> "检测失败"
        }
    }

    private fun rateOf(propId: Int): Float {
        return when (propId) {
            BODY_STEERING_WHEEL_STEERING_ANGLE -> 2.0f

            PERF_VEHICLE_SPEED -> 2.0f
            else -> 1.0f
        }
    }

    private fun propToString(propId: Int): String {
        return when (propId) {
            INFO_VIN -> "INFO_VIN"
            INFO_VIN_MATCH_STATE -> "INFO_VIN_MATCH_STATE"
            VEHICLE_TOTAL_MILEAGE -> "VEHICLE_TOTAL_MILEAGE"
            VEHICLE_REMAINING_OIL -> "VEHICLE_REMAINING_OIL"
            VEHICLE_AUTONOMIE_CYCLE -> "VEHICLE_AUTONOMIE_CYCLE"
            VEHICLE_FUEL_CONSUMPTION -> "VEHICLE_FUEL_CONSUMPTION"
            BODY_STEERING_WHEEL_STEERING_ANGLE -> "BODY_STEERING_WHEEL_STEERING_ANGLE"
            PERF_VEHICLE_SPEED -> "PERF_VEHICLE_SPEED"
            GEAR -> "GEAR_SELECTION_NEW"
            BODY_DOOR_REAR_UNLOCKDOOR_MODE -> "BODY_DOOR_REAR_UNLOCKDOOR_MODE"
            else -> "0x%08X".format(Locale.ROOT, propId)
        }
    }

    companion object {
        private const val TAG = "ToolsSettingsVM"

        private const val INFO_VIN = 0x11100100

        private const val INFO_VIN_MATCH_STATE = 0x2140f3f3

        private const val VEHICLE_TOTAL_MILEAGE = 0x2160f338

        private const val VEHICLE_REMAINING_OIL = 0x2140f334

        private const val VEHICLE_AUTONOMIE_CYCLE = 0x2140f525

        private const val VEHICLE_FUEL_CONSUMPTION = 0x2140f335

        private const val BODY_STEERING_WHEEL_STEERING_ANGLE = 0x21400ae5

        private const val PERF_VEHICLE_SPEED = 0x11600207

        private const val GEAR = 0x2140210F

        private const val BODY_DOOR_REAR_UNLOCKDOOR_MODE = 0x21400978

        private const val BODY_DOOR_REAR_UNLOCKDOOR_MODE_SET = 0x21400977

        private const val MODE_UNLOCK_ALL_DOORS = 1
        private const val MODE_UNLOCK_TRUNK_ONLY = 2

        private val WATCHED_PROPERTIES = intArrayOf(
            INFO_VIN,
            INFO_VIN_MATCH_STATE,
            VEHICLE_TOTAL_MILEAGE,
            VEHICLE_REMAINING_OIL,
            VEHICLE_AUTONOMIE_CYCLE,
            VEHICLE_FUEL_CONSUMPTION,
            BODY_STEERING_WHEEL_STEERING_ANGLE,
            GEAR,
            PERF_VEHICLE_SPEED,
            BODY_DOOR_REAR_UNLOCKDOOR_MODE,
        )
    }
}
