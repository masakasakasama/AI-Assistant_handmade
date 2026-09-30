package com.tatsu.homehub.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.openwakeword.OpenWakeWord

class WakeWordController(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onListeningChanged(listening: Boolean)
        fun onDetected(score: Float)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private var detector: OpenWakeWord? = null
    private var listening = false

    fun start() {
        if (listening) return
        if (
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            listener.onError("Wake Wordにはマイク権限が必要です")
            return
        }

        val instance = runCatching {
            detector ?: OpenWakeWord.Builder(appContext)
                .setModel(OpenWakeWord.BuiltInModel.HEY_JARVIS)
                .setThreshold(DEFAULT_THRESHOLD)
                .setDebounceMs(DEFAULT_DEBOUNCE_MS)
                .build()
                .also { detector = it }
        }.getOrElse { error ->
            listener.onError("Wake Word初期化失敗: ${error.message ?: error.javaClass.simpleName}")
            return
        }

        listening = true
        listener.onListeningChanged(true)
        runCatching {
            instance.start { score ->
                if (!listening) return@start
                stop()
                listener.onDetected(score)
            }
        }.onFailure { error ->
            listening = false
            listener.onListeningChanged(false)
            listener.onError("Wake Word開始失敗: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun stop() {
        if (!listening) return
        listening = false
        runCatching { detector?.stop() }
        listener.onListeningChanged(false)
    }

    fun release() {
        listening = false
        runCatching { detector?.release() }
        detector = null
        listener.onListeningChanged(false)
    }

    companion object {
        const val PHRASE = "Hey Jarvis"
        const val DEFAULT_THRESHOLD = 0.55f
        const val DEFAULT_DEBOUNCE_MS = 2_500L
    }
}
