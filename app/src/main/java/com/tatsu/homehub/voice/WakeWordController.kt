package com.tatsu.homehub.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

class WakeWordController(
    context: Context,
    private val listener: Listener,
    private val audioInput: PreferredAudioInput
) {
    interface Listener {
        fun onListeningChanged(listening: Boolean)
        fun onDetected(score: Float)
        fun onDiagnostic(diagnostic: String)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private var detector: RoutedWakeWordEngine? = null
    private var listening = false
    private var settings = WakeWordSettings()
    private var generation = 0L
    private val detectionGate = WakeDetectionGate()
    private var playbackText = ""
    var playbackActive = false
        private set

    fun setPlayback(active: Boolean, text: String = "") {
        playbackText = text
        playbackActive = active
        detectionGate.setPlayback(active, active && WakeDetectionGate.containsWakePhrase(text, settings.phrase),
            android.os.SystemClock.elapsedRealtime())
    }
    private val modelStore = WakeWordModelStore(appContext)

    fun configure(value: WakeWordSettings) {
        if (value == settings) return
        release()
        settings = value
        if (playbackActive) setPlayback(true, playbackText)
    }

    fun reloadModel() = release()

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
            detector ?: RoutedWakeWordEngine.Builder(appContext)
                .apply {
                    when (settings.choice) {
                        WakeWordChoice.HEY_JARVIS -> setModel(RoutedWakeWordEngine.BuiltInModel.HEY_JARVIS)
                        WakeWordChoice.ALEXA -> setModel(RoutedWakeWordEngine.BuiltInModel.ALEXA)
                        WakeWordChoice.HEY_MYCROFT -> setModel(RoutedWakeWordEngine.BuiltInModel.HEY_MYCROFT)
                        WakeWordChoice.CUSTOM -> setModelBytes(modelStore.modelBytes())
                    }
                }
                .setAudioInput(audioInput)
                .setDetectionGate(detectionGate)
                .setDiagnostic { listener.onDiagnostic(it) }
                .setCaptureError { message ->
                    listening = false
                    listener.onListeningChanged(false)
                    listener.onError(message)
                }
                .setThreshold(DEFAULT_THRESHOLD)
                .setDebounceMs(DEFAULT_DEBOUNCE_MS)
                .build()
                .also { detector = it }
        }.getOrElse { error ->
            listener.onError("Wake Word初期化失敗: ${error.message ?: error.javaClass.simpleName}")
            return
        }

        listening = true
        val activeGeneration = ++generation
        listener.onListeningChanged(true)
        runCatching {
            instance.start { score ->
                if (listening && generation == activeGeneration) {
                    stop()
                    listener.onDetected(score)
                }
            }
        }.onFailure { error ->
            listening = false
            listener.onListeningChanged(false)
            listener.onError("Wake Word開始失敗: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun stop() {
        generation++
        if (!listening) return
        listening = false
        runCatching { detector?.stop() }
        listener.onListeningChanged(false)
    }

    fun release() {
        generation++
        listening = false
        runCatching { detector?.release() }
        detector = null
        listener.onListeningChanged(false)
    }

    companion object {
        const val DEFAULT_THRESHOLD = 0.55f
        const val DEFAULT_DEBOUNCE_MS = 2_500L
    }
}
