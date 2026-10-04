package com.tatsu.homehub.update

import com.tatsu.homehub.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object WebPairingClient {
    suspend fun createLink(owner: String, token: String, secret: String): String = withContext(Dispatchers.IO) {
        check(owner.isNotBlank() && owner.none { it.isWhitespace() }) { "AndroidにBackend認証が保存されていません" }
        val origin = BuildConfig.DEFAULT_AI_BACKEND_URL.trimEnd('/')
        val connection = (URL("$origin/api/web-pairing").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $owner")
        }
        try {
            val body = JSONObject().put("switchbotToken", token).put("switchbotSecret", secret)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status != 200) error("Web版への引き継ぎに失敗しました（HTTP $status）。もう一度試してください")
            val result = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val code = result.optString("code")
            check(Regex("[A-Za-z0-9_-]{43}").matches(code)) { "引き継ぎリンクを作成できませんでした" }
            "$origin/#pair=$code"
        } finally { connection.disconnect() }
    }
}
