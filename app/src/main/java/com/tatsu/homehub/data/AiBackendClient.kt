package com.tatsu.homehub.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AiDispatchResult(
    val requestId: String,
    val routerModel: String,
    val reasoningModel: String,
    val language: String,
    val route: String,
    val confidence: Double,
    val action: String?,
    val target: String?,
    val temperatureC: Double?,
    val timeLocal: String?,
    val referenceTimeLocal: String?,
    val answerModel: String?,
    val answerText: String?,
    val clientLatencyMs: Long = 0,
    val routerMs: Long = 0,
    val answerMs: Long = 0,
    val latencyMs: Long
)

class AiBackendClient {
    suspend fun dispatch(
        baseUrl: String,
        text: String,
        context: String = ""
    ): Result<AiDispatchResult> = withContext(Dispatchers.IO) {
        runCatching {
            val started = android.os.SystemClock.elapsedRealtime()
            val endpoint = baseUrl.trim().trimEnd('/') + "/api/dispatch"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 60_000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                doOutput = true
            }

            val payload = JSONObject()
                .put("text", text)
                .put("context", context)

            connection.outputStream.use {
                it.write(payload.toString().toByteArray(Charsets.UTF_8))
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()

            if (code !in 200..299) {
                error("AI Backend HTTP $code: $raw")
            }

            val json = JSONObject(raw)
            val route = json.getJSONObject("route")
            val answer = json.optJSONObject("answer")

            AiDispatchResult(
                requestId = json.optString("requestId", ""),
                routerModel = json.optString("routerModel", "unknown"),
                reasoningModel = json.optString("reasoningModel", "unknown"),
                language = route.optString("language", "ja"),
                route = route.optString("route", "unknown"),
                confidence = route.optDouble("confidence", 0.0),
                action = route.optString("action").takeIf { it.isNotBlank() && it != "null" },
                target = route.optString("target").takeIf { it.isNotBlank() && it != "null" },
                temperatureC = if (route.isNull("temperatureC")) null else route.optDouble("temperatureC"),
                timeLocal = route.optString("timeLocal").takeIf { it.isNotBlank() && it != "null" },
                referenceTimeLocal = route.optString("referenceTimeLocal").takeIf { it.isNotBlank() && it != "null" },
                answerModel = answer?.optString("model")?.takeIf { it.isNotBlank() },
                answerText = answer?.optString("text")?.takeIf { it.isNotBlank() },
                clientLatencyMs = android.os.SystemClock.elapsedRealtime() - started,
                routerMs = json.optJSONObject("timings")?.optLong("routerMs") ?: 0,
                answerMs = json.optJSONObject("timings")?.optLong("answerMs") ?: 0,
                latencyMs = json.optLong("latencyMs", 0L)
            )
        }
    }

    suspend fun health(baseUrl: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val endpoint = baseUrl.trim().trimEnd('/') + "/api/health"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/json")
            }
            val code = connection.responseCode
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            code in 200..299 && JSONObject(body).optBoolean("ok", false)
        }
    }
}
