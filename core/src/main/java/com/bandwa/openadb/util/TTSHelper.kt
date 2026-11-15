package com.bandwa.openadb.util

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.content.res.AssetManager
import android.media.AudioManager
import android.media.MediaPlayer
import java.io.File

object TTSHelper {
    private var mediaPlayer: MediaPlayer? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioManager.OnAudioFocusChangeListener? = null

    fun playMp3(context: Context, filePath: String) {
        mediaPlayer?.release()
        mediaPlayer = null

        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        audioFocusRequest = AudioManager.OnAudioFocusChangeListener { focusChange ->
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                stopPlaying()
            }
        }

        val result = audioManager?.requestAudioFocus(
            audioFocusRequest,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        )

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            mediaPlayer = MediaPlayer().apply {
                try {
                    if (filePath.startsWith("asset://")) {
                        val assetManager: AssetManager = context.assets
                        val descriptor: AssetFileDescriptor = assetManager.openFd(filePath.substring(8))
                        setDataSource(descriptor.fileDescriptor, descriptor.startOffset, descriptor.length)
                    } else {
                        val file = File(filePath)
                        if (file.exists()) {
                            setDataSource(file.absolutePath)
                        } else {
                            throw Exception("File does not exist: $filePath")
                        }
                    }

                    prepare()

                    setOnCompletionListener {
                        stopPlaying()
                    }

                    start()
                } catch (e: Exception) {
                    e.printStackTrace()
                    stopPlaying()
                }
            }
        }
    }

    private fun stopPlaying() {
        mediaPlayer?.release()
        mediaPlayer = null
        audioManager?.abandonAudioFocus(audioFocusRequest)
    }
}
