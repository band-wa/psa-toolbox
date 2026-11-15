package com.bandwa.openadb.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
class CarlifeBroadcastReceiver : BroadcastReceiver() {
    companion object {
        var carlifeStopped: Boolean = true
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent != null) {
            when (intent.action) {
                "Musichelper.CarlifeStop" -> {
                    carlifeStopped = true
                }
                "Musichelper.CarlifeResume" -> {
                    carlifeStopped = false
                }
            }
        }
    }
}
