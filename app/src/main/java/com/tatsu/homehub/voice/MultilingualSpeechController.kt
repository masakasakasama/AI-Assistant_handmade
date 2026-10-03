package com.tatsu.homehub.voice

import android.content.Context
import com.tatsu.homehub.data.SecurePrefs
import android.media.AudioRecord
import android.os.SystemClock
import android.util.Base64
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** One utterance → original-language transcription, independent of Android recognizer locale. */
class MultilingualSpeechController(context: Context, private val listener: VoiceController.Listener, private val audioInput: PreferredAudioInput) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var generation = 0L
    @Volatile private var stopRequested = false
    @Volatile private var recorder: AudioRecord? = null

    fun start(sessionId: Long, baseUrl: String) {
        cancel()
        val current = generation
        stopRequested = false
        job = scope.launch {
            try {
                require(baseUrl.isNotBlank()) { "自動音声認識にはBackend URLが必要です" }
                val wav = withContext(Dispatchers.IO) {
                    capture(sessionId) { event -> scope.launch { if (current == generation) event() } }
                }
                if (current != generation) return@launch
                listener.onListeningChanged(sessionId, false)
                listener.onSpeechEnded(sessionId, SystemClock.elapsedRealtime())
                listener.onStatus(sessionId, "音声を確認しています")
                val result = withContext(Dispatchers.IO) { upload(baseUrl, wav) }
                if (current != generation) return@launch
                val languages = result.optJSONArray("languages")
                val tags = mapOf("ja" to "ja-JP", "en" to "en-US", "de" to "de-DE")
                // Multiple languages are retained in diagnostics; do not invent a single detected language.
                if (languages?.length() == 1) tags[languages.optString(0)]?.let { listener.onLanguageDetected(sessionId, it) }
                listener.onDiagnostic(sessionId, "recognizer=multilingual-cloud; model=${result.optString("model")}; detected=$languages; source=${result.optString("languageSource", "audio")}; audio-detected=${result.optJSONArray("audioLanguages")}; stt-ms=${result.optLong("elapsedMs")}")
                val text = result.getString("text").trim()
                require(text.isNotBlank()) { "音声を認識できませんでした" }
                listener.onStatus(sessionId, "認識しました")
                listener.onFinalText(sessionId, text)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (current == generation) {
                    listener.onListeningChanged(sessionId, false)
                    listener.onError(sessionId, error.message ?: "自動音声認識に失敗しました")
                }
            }
        }
    }

    fun stop() { stopRequested = true }
    fun cancel() {
        generation++
        stopRequested = true
        job?.cancel()
        job = null
        runCatching { recorder?.stop() }
    }
    fun release() { cancel(); scope.cancel() }

    private suspend fun capture(sessionId: Long, post: (() -> Unit) -> Unit): ByteArray {
        currentCoroutineContext().ensureActive()
        val frameSize = 1280 // 80 ms
        val capture = audioInput.open()
        val audio = capture.audio
        recorder = audio
        try {
            currentCoroutineContext().ensureActive()
            capture.start()
            var routeReported = false
            val output = ByteArrayOutputStream()
            val frame = ShortArray(frameSize)
            val boundary = UtteranceBoundary()
            while (!stopRequested && output.size() < PcmWav.MAX_PCM_BYTES) {
                currentCoroutineContext().ensureActive()
                val count = audio.read(frame, 0, frameSize, AudioRecord.READ_BLOCKING)
                if (count <= 0) {
                    if (stopRequested) break
                    error("マイクの録音に失敗しました")
                }
                capture.verifyRoute()
                if (!routeReported) {
                    val diagnostic = capture.diagnostic()
                    post {
                        listener.onDiagnostic(sessionId, "stt; $diagnostic; auto-languages=ja,en,de")
                        listener.onStatus(sessionId, "話してください")
                        listener.onListeningChanged(sessionId, true)
                    }
                    routeReported = true
                }
                for (i in 0 until count) { output.write(frame[i].toInt() and 255); output.write((frame[i].toInt() shr 8) and 255) }
                if (boundary.accept(frame, count)) break
            }
            check(boundary.heardSpeech) { "発話が検出されませんでした。もう一度話してください" }
            return PcmWav.encode(output.toByteArray())
        } finally {
            capture.close()
            if (recorder === audio) recorder = null
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private suspend fun upload(baseUrl: String, wav: ByteArray): JSONObject {
        val connection = URL(baseUrl.trim().trimEnd('/') + "/api/transcribe").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 12_000
        connection.readTimeout = 60_000
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        val token = SecurePrefs(appContext).get(SecurePrefs.KEY_AI_BACKEND_TOKEN).orEmpty().trim()
        check(token.isNotEmpty() && token.none { it.isWhitespace() }) { "設定からBackend認証トークンを入力してください" }
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.doOutput = true
        val cancellation = currentCoroutineContext().job.invokeOnCompletion(onCancelling = true, invokeImmediately = true) {
            cause -> if (cause != null) connection.disconnect()
        }
        try {
            val body = JSONObject().put("audioBase64", Base64.encodeToString(wav, Base64.NO_WRAP))
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val result = runCatching { JSONObject(raw) }.getOrNull()
            check(code in 200..299) {
                when (result?.optString("error")) {
                    "backend_auth_not_configured" -> "音声認識サーバーの認証設定が未完了です"
                    "unauthorized" -> "Backend認証トークンを設定で確認してください"
                    "usage_limits_not_configured" -> "音声認識サーバーの利用上限設定が未完了です"
                    else -> result?.optString("message")?.takeIf { it.isNotBlank() } ?: "自動音声認識 HTTP $code"
                }
            }
            return requireNotNull(result) { "自動音声認識の応答を読めません" }
        } finally { cancellation.dispose(); connection.disconnect() }
    }
}
