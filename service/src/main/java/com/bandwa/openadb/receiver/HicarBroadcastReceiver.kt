package com.bandwa.openadb.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class HicarBroadcastReceiver : BroadcastReceiver() {
    companion object {
        var hicarStopped: Boolean = true
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent != null) {
            when (intent.action) {
                "Musichelper.HicarStop" -> {
                    hicarStopped = true
                }
                "Musichelper.HicarResume" -> {
                    hicarStopped = false
                }
            }
        }
    }
}
