package com.bandwa.openadb.receiver

import android.content.Context
import android.content.Intent

class CarlifeBroadcastSender {
    companion object {
        const val ACTION_MEDIA_PREVIOUS = "com.bandwa.musichelper.carlife_previous"
        const val ACTION_MEDIA_NEXT = "com.bandwa.musichelper.carlife_next"
        const val ACTION_MEDIA_PLAY_PAUSE = "com.bandwa.musichelper.carlife_toggle"

        fun sendBroadcast(context: Context, action: String) {
            val intent = Intent(action)
            context.sendBroadcast(intent)
        }
    }
}
