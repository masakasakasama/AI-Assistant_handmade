package com.tatsu.homehub.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import java.util.Locale

class VoiceController(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onListeningChanged(listening: Boolean)
        fun onPartialText(text: String)
        fun onFinalText(text: String)
        fun onSpeakingChanged(speaking: Boolean)
        fun onError(message: String)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var recognizerUsesOnDevice = false
    private var forceSystemRecognizer = false
    private var fallbackAttempted = false
    private var lastListenIntent: Intent? = null
    private var ttsReady = false

    private val tts = TextToSpeech(appContext) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
        if (!ttsReady) listener.onError("TTSの初期化に失敗しました")
    }.apply {
        setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post { listener.onSpeakingChanged(true) }
            }
            override fun onDone(utteranceId: String?) {
                mainHandler.post { listener.onSpeakingChanged(false) }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                mainHandler.post {
                    listener.onSpeakingChanged(false)
                    listener.onError("TTS再生に失敗しました")
                }
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                onError(utteranceId)
            }
        })
    }

    fun startListening(languageTag: String? = null) {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listener.onError("マイク権限が必要です")
            return
        }

        mainHandler.post {
            stopSpeaking()
            fallbackAttempted = false
            val intent = recognitionIntent(languageTag)
            lastListenIntent = intent
            startRecognizer(intent)
        }
    }

    fun stopListening() {
        mainHandler.post { recognizer?.stopListening() }
    }

    fun cancelListening() {
        mainHandler.post {
            recognizer?.cancel()
            listener.onListeningChanged(false)
        }
    }

    fun speak(text: String, languageCode: String, utteranceId: String) {
        if (text.isBlank()) return
        mainHandler.post {
            if (!ttsReady) {
                listener.onError("TTSの準備中です")
                return@post
            }
            val locale = when (languageCode) {
                "de" -> Locale.GERMAN
                "en" -> Locale.ENGLISH
                else -> Locale.JAPANESE
            }
            tts.setLanguage(locale)
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result == TextToSpeech.ERROR) {
                listener.onError("TTS再生を開始できませんでした")
            }
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            tts.stop()
            listener.onSpeakingChanged(false)
        }
    }

    fun release() {
        mainHandler.post {
            destroyRecognizer()
            tts.stop()
            tts.shutdown()
        }
    }

    private fun recognitionIntent(languageTag: String?): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            if (!languageTag.isNullOrBlank()) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            }
        }

    private fun startRecognizer(intent: Intent) {
        val speech = recognizer ?: createRecognizer().also { recognizer = it }
        runCatching {
            listener.onPartialText("")
            listener.onListeningChanged(true)
            speech.startListening(intent)
        }.onFailure {
            listener.onListeningChanged(false)
            listener.onError(it.message ?: "音声認識を開始できませんでした")
        }
    }

    private fun createRecognizer(): SpeechRecognizer {
        val canUseOnDevice =
            !forceSystemRecognizer &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)

        recognizerUsesOnDevice = canUseOnDevice
        val speech = if (canUseOnDevice) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
        } else {
            SpeechRecognizer.createSpeechRecognizer(appContext)
        }

        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    recognizerUsesOnDevice &&
                    !fallbackAttempted &&
                    (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
                ) {
                    fallbackAttempted = true
                    forceSystemRecognizer = true
                    val retryIntent = lastListenIntent
                    destroyRecognizer()
                    if (retryIntent != null) {
                        listener.onPartialText("オンデバイス認識を利用できないためシステム認識へ切替中…")
                        startRecognizer(retryIntent)
                        return
                    }
                }

                listener.onListeningChanged(false)
                listener.onError(errorMessage(error))
            }

            override fun onResults(results: Bundle?) {
                listener.onListeningChanged(false)
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()
                if (text.isBlank()) listener.onError("音声を認識できませんでした")
                else listener.onFinalText(text)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()
                if (text.isNotBlank()) listener.onPartialText(text)
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        return speech
    }

    private fun destroyRecognizer() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
        recognizerUsesOnDevice = false
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "マイク入力エラー"
        SpeechRecognizer.ERROR_CLIENT -> "音声認識をキャンセルしました"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "マイク権限がありません"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "音声認識の通信エラー"
        SpeechRecognizer.ERROR_NO_MATCH -> "音声を認識できませんでした"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "音声認識が使用中です"
        SpeechRecognizer.ERROR_SERVER -> "音声認識サービスエラー"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "発話が検出されませんでした"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "この音声認識エンジンは現在の言語に対応していません"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "音声認識用の言語データが利用できません"
        else -> "音声認識エラー ($code)"
    }
}
