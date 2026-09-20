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
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import androidx.annotation.RequiresApi
import com.tatsu.homehub.data.AppPrefs
import java.util.Locale

class VoiceController(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onListeningChanged(sessionId: Long, listening: Boolean)
        fun onStatus(sessionId: Long, status: String)
        fun onDiagnostic(sessionId: Long, diagnostic: String)
        fun onLanguageDetected(sessionId: Long, languageTag: String)
        fun onPartialText(sessionId: Long, text: String)
        fun onFinalText(sessionId: Long, text: String)
        fun onSpeakingChanged(speaking: Boolean)
        fun onError(sessionId: Long, message: String)
    }

    private data class Attempt(
        val id: Long,
        val sessionId: Long,
        val onDevice: Boolean,
        val recognizer: SpeechRecognizer,
        var ready: Boolean = false,
        var readyTimeout: Runnable? = null,
        var detectedLanguageTag: String? = null
    )

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentAttempt: Attempt? = null
    private var activeSessionId: Long? = null
    private var nextAttemptId = 0L
    private var fallbackAttempted = false
    private var currentLanguageTag = AppPrefs.VOICE_LANGUAGE_AUTO
    private var ttsReady = false

    private val tts = TextToSpeech(appContext) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
        if (!ttsReady) listener.onError(activeSessionId ?: 0L, "読み上げを初期化できませんでした")
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
                    listener.onError(activeSessionId ?: 0L, "読み上げに失敗しました")
                }
            }
            override fun onError(utteranceId: String?, errorCode: Int) = onError(utteranceId)
        })
    }

    fun startListening(sessionId: Long, languageTag: String) {
        mainHandler.post {
            if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                listener.onError(sessionId, "マイク権限が必要です。Androidのアプリ設定でマイクを許可してください")
                return@post
            }
            invalidateCurrentAttempt()
            activeSessionId = sessionId
            currentLanguageTag = languageTag
            fallbackAttempted = false
            stopSpeakingNow()
            listener.onStatus(sessionId, "音声入力を準備しています")
            val onDeviceAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && runCatching {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
            }.getOrDefault(false)
            listener.onDiagnostic(sessionId, "lang=$languageTag; resolved=${recognitionLanguageTag()}; recognizer=${if (onDeviceAvailable) "on-device-first" else "system-only"}; version=${appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName}")
            startAttempt(sessionId, preferOnDevice = onDeviceAvailable)
        }
    }

    fun stopListening() {
        mainHandler.post {
            val attempt = currentAttempt ?: return@post
            if (isCurrent(attempt)) {
                listener.onStatus(attempt.sessionId, "音声を確認しています")
                runCatching { attempt.recognizer.stopListening() }
            }
        }
    }

    fun cancelListening(sessionId: Long) {
        mainHandler.post {
            if (activeSessionId != sessionId) return@post
            invalidateCurrentAttempt()
            activeSessionId = null
            listener.onListeningChanged(sessionId, false)
            listener.onStatus(sessionId, "待機")
        }
    }

    fun speak(text: String, languageCode: String, utteranceId: String) {
        if (text.isBlank()) return
        mainHandler.post {
            if (!ttsReady) {
                listener.onError(activeSessionId ?: 0L, "読み上げの準備中です")
                return@post
            }
            val locale = when (languageCode) {
                "de" -> Locale.GERMAN
                "en" -> Locale.ENGLISH
                else -> Locale.JAPANESE
            }
            tts.setLanguage(locale)
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId) == TextToSpeech.ERROR) {
                listener.onError(activeSessionId ?: 0L, "読み上げを開始できませんでした")
            }
        }
    }

    fun stopSpeaking() = mainHandler.post { stopSpeakingNow() }

    fun release() {
        mainHandler.post {
            invalidateCurrentAttempt()
            activeSessionId = null
            tts.stop()
            tts.shutdown()
        }
    }

    private fun stopSpeakingNow() {
        tts.stop()
        listener.onSpeakingChanged(false)
    }

    private fun recognitionIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, recognitionLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        if (currentLanguageTag == AppPrefs.VOICE_LANGUAGE_AUTO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
            putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
            putStringArrayListExtra(
                RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES,
                ArrayList(SUPPORTED_LANGUAGE_TAGS)
            )
            putStringArrayListExtra(
                RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES,
                ArrayList(SUPPORTED_LANGUAGE_TAGS)
            )
        }
    }

    private fun recognitionLanguageTag(): String =
        if (currentLanguageTag == AppPrefs.VOICE_LANGUAGE_AUTO) {
            AUTO_BASE_LANGUAGE_TAG
        } else {
            currentLanguageTag
        }

    private fun startAttempt(sessionId: Long, preferOnDevice: Boolean) {
        if (activeSessionId != sessionId) return
        val attemptId = ++nextAttemptId
        val recognizer = try {
            if (preferOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                SpeechRecognizer.createSpeechRecognizer(appContext)
            }
        } catch (error: Exception) {
            if (preferOnDevice && !fallbackAttempted) {
                fallbackAttempted = true
                listener.onStatus(sessionId, "音声入力を切り替えています")
                listener.onDiagnostic(sessionId, "attempt=$attemptId; on-device creation failed: ${error.javaClass.simpleName}")
                startAttempt(sessionId, preferOnDevice = false)
            } else {
                finishWithError(sessionId, "音声認識サービスを起動できませんでした。端末の音声入力設定を確認してください", "attempt=$attemptId; create=${error.javaClass.simpleName}")
            }
            return
        }
        val attempt = Attempt(attemptId, sessionId, preferOnDevice, recognizer)
        currentAttempt = attempt
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (isCurrent(attempt)) {
                    attempt.ready = true
                    attempt.readyTimeout?.let(mainHandler::removeCallbacks)
                    attempt.readyTimeout = null
                    listener.onStatus(sessionId, if (fallbackAttempted) "準備できました。もう一度話してください" else "話してください")
                    listener.onListeningChanged(sessionId, true)
                }
            }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() {
                if (isCurrent(attempt)) listener.onStatus(sessionId, "音声を確認しています")
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onLanguageDetection(results: Bundle) {
                if (!isCurrent(attempt) || currentLanguageTag != AppPrefs.VOICE_LANGUAGE_AUTO) return
                val detected = normalizeSupportedLanguage(
                    results.getString(SpeechRecognizer.DETECTED_LANGUAGE)
                ) ?: return
                if (attempt.detectedLanguageTag != detected) {
                    attempt.detectedLanguageTag = detected
                    listener.onLanguageDetected(sessionId, detected)
                    val confidence = results.getInt(
                        SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL,
                        SpeechRecognizer.LANGUAGE_DETECTION_CONFIDENCE_LEVEL_UNKNOWN
                    )
                    listener.onDiagnostic(sessionId, "attempt=$attemptId; detected-language=$detected; confidence=$confidence")
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                if (!isCurrent(attempt)) return
                val text = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                if (text.isNotBlank()) listener.onPartialText(sessionId, text)
            }
            override fun onResults(results: Bundle?) {
                if (!isCurrent(attempt)) return
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                if (currentLanguageTag == AppPrefs.VOICE_LANGUAGE_AUTO && attempt.detectedLanguageTag == null) {
                    inferJapanese(text)?.let {
                        attempt.detectedLanguageTag = it
                        listener.onLanguageDetected(sessionId, it)
                    }
                }
                finishAttempt(attempt)
                listener.onListeningChanged(sessionId, false)
                if (text.isBlank()) finishWithError(sessionId, "音声を認識できませんでした。もう一度話してください", "attempt=$attemptId; result=blank")
                else {
                    listener.onStatus(sessionId, "認識しました")
                    listener.onFinalText(sessionId, text)
                }
            }
            override fun onError(error: Int) {
                if (!isCurrent(attempt)) return
                if (attempt.onDevice && !fallbackAttempted && isLanguageUnavailable(error)) {
                    fallbackAttempted = true
                    listener.onListeningChanged(sessionId, false)
                    listener.onStatus(sessionId, "音声入力を切り替えています")
                    listener.onDiagnostic(sessionId, "attempt=$attemptId; recognizer=on-device; error=$error; fallback=system")
                    finishAttempt(attempt)
                    startAttempt(sessionId, preferOnDevice = false)
                } else {
                    finishAttempt(attempt)
                    finishWithError(sessionId, errorMessage(error), "attempt=$attemptId; recognizer=${if (attempt.onDevice) "on-device" else "system"}; lang=$currentLanguageTag; error=$error")
                }
            }
        })
        listener.onDiagnostic(sessionId, "attempt=$attemptId; recognizer=${if (preferOnDevice) "on-device" else "system"}; lang=$currentLanguageTag")
        if (preferOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkOnDeviceLanguageSupport(attempt)
        } else {
            beginRecognition(attempt)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkOnDeviceLanguageSupport(attempt: Attempt) {
        var resolved = false
        val intent = recognitionIntent()
        val supportTimeout = Runnable {
            if (isCurrent(attempt) && !resolved) {
                resolved = true
                listener.onDiagnostic(attempt.sessionId, "attempt=${attempt.id}; support-check=timeout; continuing=on-device")
                beginRecognition(attempt)
            }
        }
        mainHandler.postDelayed(supportTimeout, SUPPORT_CHECK_TIMEOUT_MS)
        try {
            attempt.recognizer.checkRecognitionSupport(intent, appContext.mainExecutor, object : RecognitionSupportCallback {
                override fun onSupportResult(support: android.speech.RecognitionSupport) {
                    if (!isCurrent(attempt) || resolved) return
                    resolved = true
                    mainHandler.removeCallbacks(supportTimeout)
                    val requestedLanguages = setOf(
                        Locale.forLanguageTag(recognitionLanguageTag()).language
                    )
                    val installedLanguages = support.installedOnDeviceLanguages
                        .map { Locale.forLanguageTag(it).language.lowercase() }
                        .toSet()
                    val ready = requestedLanguages.all { it.lowercase() in installedLanguages }
                    if (ready) {
                        beginRecognition(attempt)
                    } else {
                        listener.onDiagnostic(attempt.sessionId, "attempt=${attempt.id}; lang=$currentLanguageTag; on-device language not installed; falling back to system")
                        fallbackToSystem(attempt)
                    }
                }

                override fun onError(error: Int) {
                    if (!isCurrent(attempt) || resolved) return
                    resolved = true
                    mainHandler.removeCallbacks(supportTimeout)
                    listener.onDiagnostic(attempt.sessionId, "attempt=${attempt.id}; support-check-error=$error; continuing=on-device")
                    beginRecognition(attempt)
                }
            })
        } catch (error: Exception) {
            if (isCurrent(attempt) && !resolved) {
                resolved = true
                mainHandler.removeCallbacks(supportTimeout)
                listener.onDiagnostic(attempt.sessionId, "attempt=${attempt.id}; support-check-unavailable=${error.javaClass.simpleName}; continuing=on-device")
                beginRecognition(attempt)
            }
        }
    }

    private fun beginRecognition(attempt: Attempt) {
        if (!isCurrent(attempt)) return
        try {
            attempt.recognizer.startListening(recognitionIntent())
            if (isCurrent(attempt) && !attempt.ready) {
                val timeout = Runnable {
                    if (isCurrent(attempt) && !attempt.ready) {
                        finishAttempt(attempt)
                        finishWithError(attempt.sessionId, "音声入力の準備に時間がかかっています。マイク権限と音声認識サービスを確認して、もう一度お試しください", "attempt=${attempt.id}; ready-timeout")
                    }
                }
                attempt.readyTimeout = timeout
                mainHandler.postDelayed(timeout, READY_TIMEOUT_MS)
            }
        } catch (error: Exception) {
            if (isCurrent(attempt) && attempt.onDevice && !fallbackAttempted) {
                listener.onDiagnostic(attempt.sessionId, "attempt=${attempt.id}; start-failed=${error.javaClass.simpleName}; fallback=system")
                fallbackToSystem(attempt)
            } else if (isCurrent(attempt)) {
                finishAttempt(attempt)
                finishWithError(attempt.sessionId, "音声認識を開始できませんでした。もう一度お試しください", "attempt=${attempt.id}; start=${error.javaClass.simpleName}")
            }
        }
    }

    private fun fallbackToSystem(attempt: Attempt) {
        if (!isCurrent(attempt) || fallbackAttempted) return
        fallbackAttempted = true
        listener.onListeningChanged(attempt.sessionId, false)
        listener.onStatus(attempt.sessionId, "音声入力を切り替えています")
        finishAttempt(attempt)
        startAttempt(attempt.sessionId, preferOnDevice = false)
    }

    private fun isLanguageUnavailable(error: Int): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)

    private fun normalizeSupportedLanguage(languageTag: String?): String? {
        val language = languageTag?.let(Locale::forLanguageTag)?.language?.lowercase() ?: return null
        return SUPPORTED_LANGUAGE_TAGS.firstOrNull {
            Locale.forLanguageTag(it).language.lowercase() == language
        }
    }

    private fun inferJapanese(text: String): String? =
        if (text.any { it in '\u3040'..'\u30ff' || it in '\u4e00'..'\u9fff' }) "ja-JP" else null

    private fun isCurrent(attempt: Attempt): Boolean = activeSessionId == attempt.sessionId && currentAttempt?.id == attempt.id

    private fun finishAttempt(attempt: Attempt) {
        if (currentAttempt?.id != attempt.id) return
        currentAttempt = null
        attempt.readyTimeout?.let(mainHandler::removeCallbacks)
        attempt.readyTimeout = null
        runCatching { attempt.recognizer.destroy() }
    }

    private fun invalidateCurrentAttempt() {
        val old = currentAttempt
        currentAttempt = null
        if (old != null) {
            old.readyTimeout?.let(mainHandler::removeCallbacks)
            runCatching { old.recognizer.cancel() }
            runCatching { old.recognizer.destroy() }
        }
    }

    private fun finishWithError(sessionId: Long, message: String, diagnostic: String) {
        if (activeSessionId != sessionId) return
        activeSessionId = null
        listener.onListeningChanged(sessionId, false)
        listener.onStatus(sessionId, "音声入力を利用できません")
        listener.onDiagnostic(sessionId, diagnostic)
        listener.onError(sessionId, message)
    }

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "マイクに接続できません。マイクが他のアプリで使用中でないか確認してください"
        SpeechRecognizer.ERROR_CLIENT -> "音声入力を開始できませんでした。もう一度お試しください"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "マイク権限がありません。Androidのアプリ設定で許可してください"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "音声認識サービスに接続できません。通信状態を確認してください"
        SpeechRecognizer.ERROR_NO_MATCH -> "音声を認識できませんでした。少しはっきり話して、もう一度お試しください"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "音声認識が使用中です。少し待ってからお試しください"
        SpeechRecognizer.ERROR_SERVER -> "音声認識サービスでエラーが発生しました。もう一度お試しください"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "発話が検出されませんでした。もう一度お試しください"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "選択中の言語はこの音声認識サービスで利用できません。設定から別の言語を選んでください"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "選択中の言語データがありません。端末の音声入力設定で言語データを追加してください"
        else -> "音声認識を開始できませんでした。音声入力の設定を確認してください"
    }

    private companion object {
        const val SUPPORT_CHECK_TIMEOUT_MS = 1_800L
        const val READY_TIMEOUT_MS = 10_000L
        const val AUTO_BASE_LANGUAGE_TAG = "ja-JP"
        val SUPPORTED_LANGUAGE_TAGS = listOf("ja-JP", "en-US", "de-DE")
    }
}
