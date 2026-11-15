package com.bandwa.openadb.util

import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object HotFixUdisk {
    private const val PROP_USB_OTG_MODE_INFO = 0x2140F141
    private const val AREA_GLOBAL = 0

    private const val MODE_DEVICE = 1
    private const val MODE_HOST = 2

    fun fixUdisk(context: Context, carPropertyManager: CarPropertyManager) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(context, "尝试重载U盘中", Toast.LENGTH_LONG).show()
            val isDeviceMode = try {
                carPropertyManager.getIntProperty(PROP_USB_OTG_MODE_INFO, AREA_GLOBAL) == MODE_DEVICE
            } catch (e: Exception) {
                false
            }
            try {
                if (isDeviceMode) {
                    setDeviceMode(carPropertyManager, false)
                } else {
                    setDeviceMode(carPropertyManager, true)
                    delay(1000)
                    setDeviceMode(carPropertyManager, false)
                }
            } catch (e: Exception) {
            }
            delay(3000)
            Toast.makeText(context, "已重载，仍不识别请重插U盘", Toast.LENGTH_LONG).show()
        }
    }

    private fun setDeviceMode(carPropertyManager: CarPropertyManager, device: Boolean) {
        carPropertyManager.setIntProperty(
            PROP_USB_OTG_MODE_INFO, AREA_GLOBAL, if (device) MODE_DEVICE else MODE_HOST
        )
    }
}
