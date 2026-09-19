package com.tatsu.homehub.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.tatsu.homehub.BuildConfig
import java.util.ArrayDeque
import java.util.Locale

class VoiceController(
    context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onRecognitionState(
            sessionId: Long,
            attemptId: Long,
            phase: VoicePhase,
            mode: VoiceRecognizerMode?,
            message: String?
        )
        fun onPartialText(sessionId: Long, attemptId: Long, text: String)
        fun onFinalText(sessionId: Long, attemptId: Long, text: String)
        fun onSpeakingChanged(sessionId: Long, speaking: Boolean)
        fun onError(sessionId: Long, attemptId: Long, message: String)
        fun onDiagnosticsChanged(report: String)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val guard = VoiceAttemptGuard()
    private val diagnostics = ArrayDeque<String>()
    private val unsupportedOnDeviceLanguages = mutableSetOf<String>()
    private val ttsSessions = mutableMapOf<String, Long>()

    private var recognizer: SpeechRecognizer? = null
    private var recognizerMode: VoiceRecognizerMode? = null
    private var activeSessionId = 0L
    private var activeAttemptId = 0L
    private var activeLanguageTag = "ja-JP"
    private var fallbackAttempted = false
    private var fallbackRequiresRepeat = false
    private var sessionStartedAt = 0L
    private var speechEndedAt: Long? = null
    private var fallbackStartedAt: Long? = null
    private var readyTimeout: Runnable? = null
    private var recognitionTimeout: Runnable? = null
    private var released = false
    private var ttsReady = false

    private val tts = TextToSpeech(appContext) { status ->
        ttsReady = status == TextToSpeech.SUCCESS
        if (!ttsReady) {
            listener.onError(activeSessionId, activeAttemptId, "TTSの初期化に失敗しました")
        }
    }.apply {
        setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                val id = utteranceId ?: return
                val sessionId = ttsSessions[id] ?: return
                mainHandler.post {
                    if (sessionId == activeSessionId && !released) {
                        recordExternal(sessionId, "tts_start")
                        listener.onSpeakingChanged(sessionId, true)
                    }
                }
            }

            override fun onDone(utteranceId: String?) {
                val id = utteranceId ?: return
                val sessionId = ttsSessions.remove(id) ?: return
                mainHandler.post {
                    if (sessionId == activeSessionId && !released) {
                        recordExternal(sessionId, "tts_done")
                        listener.onSpeakingChanged(sessionId, false)
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                val id = utteranceId ?: return
                val sessionId = ttsSessions.remove(id) ?: return
                mainHandler.post {
                    if (sessionId == activeSessionId && !released) {
                        listener.onSpeakingChanged(sessionId, false)
                        listener.onError(sessionId, activeAttemptId, "TTS再生に失敗しました")
                    }
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                onError(utteranceId)
            }
        })
    }

    fun startListening(sessionId: Long, languageTag: String) {
        if (ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            listener.onError(sessionId, 0, "マイク権限が必要です")
            return
        }

        mainHandler.post {
            if (released) return@post
            stopSpeaking()
            activeSessionId = sessionId
            activeLanguageTag = normalizeLanguageTag(languageTag)
            fallbackAttempted = false
            fallbackRequiresRepeat = false
            speechEndedAt = null
            fallbackStartedAt = null
            sessionStartedAt = SystemClock.elapsedRealtime()
            diagnostics.clear()
            guard.beginSession(sessionId)

            logEvent(
                sessionId,
                0,
                "session_start language=$activeLanguageTag onDeviceCachedUnsupported=" +
                    (activeLanguageTag in unsupportedOnDeviceLanguages)
            )

            val canTryOnDevice =
                activeLanguageTag !in unsupportedOnDeviceLanguages &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)

            startAttempt(
                sessionId = sessionId,
                languageTag = activeLanguageTag,
                mode = if (canTryOnDevice) VoiceRecognizerMode.ON_DEVICE else VoiceRecognizerMode.SYSTEM,
                switching = false,
                requiresRepeat = false
            )
        }
    }

    fun stopListening(sessionId: Long) {
        mainHandler.post {
            if (sessionId != activeSessionId || released) return@post
            logEvent(sessionId, activeAttemptId, "stop_requested")
            recognizer?.stopListening()
        }
    }

    fun cancelListening(sessionId: Long) {
        mainHandler.post {
            if (sessionId != activeSessionId) return@post
            logEvent(sessionId, activeAttemptId, "cancel_requested")
            guard.invalidate(sessionId)
            cancelTimeouts()
            destroyRecognizerOnly()
        }
    }

    fun resetLanguageSupport(languageTag: String) {
        mainHandler.post {
            unsupportedOnDeviceLanguages.remove(normalizeLanguageTag(languageTag))
        }
    }

    fun speak(text: String, languageCode: String, utteranceId: String, sessionId: Long) {
        if (text.isBlank()) return
        mainHandler.post {
            if (released || sessionId != activeSessionId) return@post
            if (!ttsReady) {
                listener.onError(sessionId, activeAttemptId, "TTSの準備中です")
                return@post
            }
            val locale = when (languageCode) {
                "de" -> Locale.GERMANY
                "en" -> Locale.US
                else -> Locale.JAPAN
            }
            tts.setLanguage(locale)
            ttsSessions[utteranceId] = sessionId
            logEvent(
                sessionId,
                activeAttemptId,
                "tts_request language=" + locale.toLanguageTag()
            )
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result == TextToSpeech.ERROR) {
                ttsSessions.remove(utteranceId)
                listener.onError(sessionId, activeAttemptId, "TTS再生を開始できませんでした")
            }
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            ttsSessions.clear()
            tts.stop()
        }
    }

    fun recordExternal(sessionId: Long, event: String) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            if (sessionId == activeSessionId && !released) {
                logEvent(sessionId, activeAttemptId, event)
            }
        } else {
            mainHandler.post {
                if (sessionId == activeSessionId && !released) {
                    logEvent(sessionId, activeAttemptId, event)
                }
            }
        }
    }

    fun release() {
        mainHandler.post {
            released = true
            guard.invalidate(activeSessionId)
            cancelTimeouts()
            destroyRecognizerOnly()
            ttsSessions.clear()
            tts.stop()
            tts.shutdown()
        }
    }

    private fun startAttempt(
        sessionId: Long,
        languageTag: String,
        mode: VoiceRecognizerMode,
        switching: Boolean,
        requiresRepeat: Boolean
    ) {
        if (released || sessionId != activeSessionId) return
        val attemptId = guard.nextAttempt(sessionId)
        if (attemptId < 0) return

        activeAttemptId = attemptId
        fallbackRequiresRepeat = requiresRepeat
        cancelTimeouts()
        destroyRecognizerOnly()
        recognizerMode = mode

        if (mode == VoiceRecognizerMode.SYSTEM &&
            !SpeechRecognizer.isRecognitionAvailable(appContext)
        ) {
            failAttempt(sessionId, attemptId, "利用できるシステム音声認識サービスがありません")
            return
        }

        val speech = runCatching {
            when (mode) {
                VoiceRecognizerMode.ON_DEVICE -> {
                    check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        "On-device recognition requires Android 12 or later"
                    }
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                }
                VoiceRecognizerMode.SYSTEM ->
                    SpeechRecognizer.createSpeechRecognizer(appContext)
            }
        }.getOrElse { error ->
            logEvent(
                sessionId,
                attemptId,
                "recognizer_create_failed mode=$mode error=" + safeMessage(error)
            )
            if (mode == VoiceRecognizerMode.ON_DEVICE && !fallbackAttempted) {
                fallbackAttempted = true
                fallbackStartedAt = SystemClock.elapsedRealtime()
                listener.onRecognitionState(
                    sessionId,
                    attemptId,
                    VoicePhase.SWITCHING,
                    mode,
                    "オンデバイス認識を開始できないためシステム認識へ切り替えます"
                )
                startAttempt(
                    sessionId,
                    languageTag,
                    VoiceRecognizerMode.SYSTEM,
                    switching = true,
                    requiresRepeat = false
                )
            } else {
                failAttempt(
                    sessionId,
                    attemptId,
                    error.message ?: "音声認識エンジンを開始できませんでした"
                )
            }
            return
        }

        recognizer = speech
        attachRecognitionListener(
            speech,
            sessionId,
            attemptId,
            languageTag,
            mode
        )

        listener.onRecognitionState(
            sessionId,
            attemptId,
            if (switching) VoicePhase.SWITCHING else VoicePhase.PREPARING,
            mode,
            if (switching) "音声入力を切り替えています" else "音声入力を準備しています"
        )
        logEvent(
            sessionId,
            attemptId,
            "attempt_prepare mode=$mode language=$languageTag switching=$switching"
        )

        val intent = recognitionIntent(languageTag)
        if (mode == VoiceRecognizerMode.ON_DEVICE &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            checkSupportThenStart(
                speech,
                intent,
                sessionId,
                attemptId,
                languageTag,
                mode
            )
        } else {
            startRecognizerNow(speech, intent, sessionId, attemptId, mode)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun checkSupportThenStart(
        speech: SpeechRecognizer,
        intent: Intent,
        sessionId: Long,
        attemptId: Long,
        languageTag: String,
        mode: VoiceRecognizerMode
    ) {
        var decided = false
        lateinit var timeout: Runnable

        fun startAfterCheck(reason: String) {
            if (decided || !guard.isCurrent(sessionId, attemptId) || released) return
            decided = true
            mainHandler.removeCallbacks(timeout)
            logEvent(sessionId, attemptId, "support_check_continue reason=$reason")
            startRecognizerNow(speech, intent, sessionId, attemptId, mode)
        }

        timeout = Runnable { startAfterCheck("timeout") }
        mainHandler.postDelayed(timeout, SUPPORT_CHECK_TIMEOUT_MS)

        runCatching {
            speech.checkRecognitionSupport(
                intent,
                ContextCompat.getMainExecutor(appContext),
                object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                        startAfterCheck("supported")
                    }

                    override fun onError(error: Int) {
                        if (decided || !guard.isCurrent(sessionId, attemptId) || released) return
                        if (
                            error == VoiceRecognitionPolicy.ERROR_LANGUAGE_NOT_SUPPORTED ||
                            error == VoiceRecognitionPolicy.ERROR_LANGUAGE_UNAVAILABLE
                        ) {
                            decided = true
                            mainHandler.removeCallbacks(timeout)
                            unsupportedOnDeviceLanguages += languageTag
                            fallbackAttempted = true
                            fallbackStartedAt = SystemClock.elapsedRealtime()
                            logEvent(
                                sessionId,
                                attemptId,
                                "support_check_language_unavailable error=$error"
                            )
                            listener.onRecognitionState(
                                sessionId,
                                attemptId,
                                VoicePhase.SWITCHING,
                                mode,
                                "この言語はオンデバイス認識で利用できないためシステム認識へ切り替えます"
                            )
                            startAttempt(
                                sessionId,
                                languageTag,
                                VoiceRecognizerMode.SYSTEM,
                                switching = true,
                                requiresRepeat = false
                            )
                        } else {
                            startAfterCheck("unsupported_check_api_error_$error")
                        }
                    }
                }
            )
        }.onFailure { error ->
            logEvent(
                sessionId,
                attemptId,
                "support_check_exception error=" + safeMessage(error)
            )
            startAfterCheck("check_exception")
        }
    }

    private fun startRecognizerNow(
        speech: SpeechRecognizer,
        intent: Intent,
        sessionId: Long,
        attemptId: Long,
        mode: VoiceRecognizerMode
    ) {
        if (!guard.isCurrent(sessionId, attemptId) || released || recognizer !== speech) return
        listener.onPartialText(sessionId, attemptId, "")
        logEvent(sessionId, attemptId, "recognizer_start mode=$mode")

        readyTimeout = Runnable {
            if (guard.isCurrent(sessionId, attemptId) && recognizer === speech && !released) {
                failAttempt(
                    sessionId,
                    attemptId,
                    "音声入力の準備がタイムアウトしました。もう一度お試しください"
                )
            }
        }.also { mainHandler.postDelayed(it, READY_TIMEOUT_MS) }

        runCatching {
            speech.startListening(intent)
        }.onFailure { error ->
            logEvent(
                sessionId,
                attemptId,
                "recognizer_start_failed error=" + safeMessage(error)
            )
            if (mode == VoiceRecognizerMode.ON_DEVICE && !fallbackAttempted) {
                fallbackAttempted = true
                fallbackStartedAt = SystemClock.elapsedRealtime()
                listener.onRecognitionState(
                    sessionId,
                    attemptId,
                    VoicePhase.SWITCHING,
                    mode,
                    "オンデバイス認識を開始できないためシステム認識へ切り替えます"
                )
                startAttempt(
                    sessionId,
                    activeLanguageTag,
                    VoiceRecognizerMode.SYSTEM,
                    switching = true,
                    requiresRepeat = false
                )
            } else {
                failAttempt(
                    sessionId,
                    attemptId,
                    error.message ?: "音声認識を開始できませんでした"
                )
            }
        }
    }

    private fun attachRecognitionListener(
        speech: SpeechRecognizer,
        sessionId: Long,
        attemptId: Long,
        languageTag: String,
        mode: VoiceRecognizerMode
    ) {
        speech.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (!isCurrent(speech, sessionId, attemptId)) return
                readyTimeout?.let(mainHandler::removeCallbacks)
                readyTimeout = null

                val sessionElapsed = elapsedSinceSessionStart()
                val fallbackElapsed = fallbackStartedAt?.let {
                    SystemClock.elapsedRealtime() - it
                }
                logEvent(
                    sessionId,
                    attemptId,
                    "ready mode=$mode button_to_ready_ms=$sessionElapsed" +
                        (fallbackElapsed?.let { " fallback_to_ready_ms=$it" } ?: "")
                )
                listener.onRecognitionState(
                    sessionId,
                    attemptId,
                    VoicePhase.LISTENING,
                    mode,
                    if (fallbackRequiresRepeat) "もう一度話してください" else "話してください"
                )

                recognitionTimeout = Runnable {
                    if (isCurrent(speech, sessionId, attemptId)) {
                        failAttempt(
                            sessionId,
                            attemptId,
                            "音声認識がタイムアウトしました。もう一度お試しください"
                        )
                    }
                }.also { mainHandler.postDelayed(it, RECOGNITION_TIMEOUT_MS) }
            }

            override fun onBeginningOfSpeech() {
                if (!isCurrent(speech, sessionId, attemptId)) return
                logEvent(sessionId, attemptId, "speech_begin")
            }

            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                if (!isCurrent(speech, sessionId, attemptId)) return
                speechEndedAt = SystemClock.elapsedRealtime()
                logEvent(sessionId, attemptId, "speech_end")
            }

            override fun onError(error: Int) {
                if (!isCurrent(speech, sessionId, attemptId)) return
                cancelTimeouts()
                logEvent(
                    sessionId,
                    attemptId,
                    "recognizer_error mode=$mode code=$error"
                )

                if (
                    VoiceRecognitionPolicy.shouldFallbackToSystem(
                        fromOnDevice = mode == VoiceRecognizerMode.ON_DEVICE,
                        fallbackAlreadyAttempted = fallbackAttempted,
                        errorCode = error
                    )
                ) {
                    unsupportedOnDeviceLanguages += languageTag
                    fallbackAttempted = true
                    fallbackRequiresRepeat = true
                    fallbackStartedAt = SystemClock.elapsedRealtime()
                    listener.onRecognitionState(
                        sessionId,
                        attemptId,
                        VoicePhase.SWITCHING,
                        mode,
                        "オンデバイス認識を利用できないためシステム認識へ切り替えます"
                    )
                    startAttempt(
                        sessionId,
                        languageTag,
                        VoiceRecognizerMode.SYSTEM,
                        switching = true,
                        requiresRepeat = true
                    )
                    return
                }

                failAttempt(sessionId, attemptId, errorMessage(error))
            }

            override fun onResults(results: Bundle?) {
                if (!isCurrent(speech, sessionId, attemptId)) return
                cancelTimeouts()
                if (!guard.acceptFinal(sessionId, attemptId)) return

                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()

                if (text.isBlank()) {
                    listener.onError(sessionId, attemptId, "音声を認識できませんでした")
                    guard.invalidate(sessionId)
                    destroyRecognizerOnly()
                    return
                }

                val afterEnd = speechEndedAt?.let {
                    SystemClock.elapsedRealtime() - it
                }
                logEvent(
                    sessionId,
                    attemptId,
                    "final_text chars=" + text.length +
                        (afterEnd?.let { " speech_end_to_final_ms=$it" } ?: "")
                )
                listener.onFinalText(sessionId, attemptId, text)
                guard.invalidate(sessionId)
                destroyRecognizerOnly()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!isCurrent(speech, sessionId, attemptId)) return
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                    .trim()
                if (text.isNotBlank()) {
                    listener.onPartialText(sessionId, attemptId, text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
    }

    private fun failAttempt(sessionId: Long, attemptId: Long, message: String) {
        if (!guard.isCurrent(sessionId, attemptId) || released) return
        cancelTimeouts()
        logEvent(sessionId, attemptId, "attempt_failed message=$message")
        listener.onError(sessionId, attemptId, message)
        guard.invalidate(sessionId)
        destroyRecognizerOnly()
    }

    private fun recognitionIntent(languageTag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
        }

    private fun isCurrent(
        speech: SpeechRecognizer,
        sessionId: Long,
        attemptId: Long
    ): Boolean =
        !released &&
            recognizer === speech &&
            guard.isCurrent(sessionId, attemptId)

    private fun destroyRecognizerOnly() {
        val current = recognizer
        recognizer = null
        recognizerMode = null
        runCatching { current?.cancel() }
        runCatching { current?.destroy() }
    }

    private fun cancelTimeouts() {
        readyTimeout?.let(mainHandler::removeCallbacks)
        recognitionTimeout?.let(mainHandler::removeCallbacks)
        readyTimeout = null
        recognitionTimeout = null
    }

    private fun logEvent(sessionId: Long, attemptId: Long, event: String) {
        val elapsed = elapsedSinceSessionStart()
        val mode = recognizerMode?.name ?: "NONE"
        diagnostics.addLast(
            "t=+$elapsed ms session=$sessionId attempt=$attemptId " +
                "mode=$mode language=$activeLanguageTag $event"
        )
        while (diagnostics.size > MAX_DIAGNOSTIC_LINES) {
            diagnostics.removeFirst()
        }
        listener.onDiagnosticsChanged(buildDiagnostics())
    }

    private fun buildDiagnostics(): String = buildString {
        appendLine("Voice diagnostics")
        appendLine(
            "app=" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ") " +
                "commit=" + BuildConfig.BUILD_COMMIT
        )
        appendLine(
            "device=" + Build.MANUFACTURER + " " + Build.MODEL +
                " sdk=" + Build.VERSION.SDK_INT
        )
        diagnostics.forEach(::appendLine)
    }.trimEnd()

    private fun elapsedSinceSessionStart(): Long =
        if (sessionStartedAt == 0L) 0L
        else SystemClock.elapsedRealtime() - sessionStartedAt

    private fun normalizeLanguageTag(value: String): String = when (
        value.trim().lowercase(Locale.ROOT)
    ) {
        "en", "en-us", "en_us" -> "en-US"
        "de", "de-de", "de_de" -> "de-DE"
        else -> "ja-JP"
    }

    private fun safeMessage(error: Throwable): String =
        error.message?.replace("\n", " ") ?: error::class.java.simpleName

    private fun errorMessage(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "マイク入力エラー"
        SpeechRecognizer.ERROR_CLIENT -> "音声認識をキャンセルしました"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "マイク権限がありません"
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "音声認識の通信エラー"
        SpeechRecognizer.ERROR_NO_MATCH ->
            "音声を認識できませんでした。もう一度お試しください"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "音声認識が使用中です。少し待ってから再試行してください"
        SpeechRecognizer.ERROR_SERVER -> "音声認識サービスエラー"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "発話が検出されませんでした。もう一度お試しください"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ->
            "この音声認識エンジンは現在の言語に対応していません"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "音声認識用の言語データが利用できません"
        else -> "音声認識エラー ($code)"
    }

    companion object {
        private const val SUPPORT_CHECK_TIMEOUT_MS = 1_500L
        private const val READY_TIMEOUT_MS = 8_000L
        private const val RECOGNITION_TIMEOUT_MS = 30_000L
        private const val MAX_DIAGNOSTIC_LINES = 80
    }
}
