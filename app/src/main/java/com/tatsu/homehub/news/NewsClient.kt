package com.tatsu.homehub.news

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class HomeNewsAlert(val score: Int, val source: String, val summary: String, val url: String)
data class HomeNewsBatch(val id: String, val capturedAt: String, val digest: String, val alerts: List<HomeNewsAlert>)

class NewsClient {
    suspend fun load(baseUrl: String, token: String): List<HomeNewsBatch> = withContext(Dispatchers.IO) {
        require(token.isNotBlank() && !token.any(Char::isWhitespace)) { "Backend認証トークンが未設定です" }
        val endpoint = URL(baseUrl.trim().trimEnd('/') + "/api/news")
        require(endpoint.protocol == "https" ||
            (endpoint.protocol == "http" && endpoint.host in setOf("localhost", "127.0.0.1", "10.0.2.2"))) {
            "BackendにはHTTPSで接続してください"
        }
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode != 200) error("ニュースの取得に失敗（HTTP ${conn.responseCode}）")
            val raw = conn.inputStream.bufferedReader().use { it.readText() }
            val data = JSONObject(raw).optJSONArray("items") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until minOf(data.length(), 20)) {
                    val row = data.optJSONObject(i) ?: continue
                    val id = row.optString("id")
                    if (id.isBlank()) continue
                    val items = row.optJSONArray("alerts")
                    val alerts = buildList {
                        if (items != null) for (j in 0 until minOf(items.length(), 5)) {
                            val alert = items.optJSONObject(j) ?: continue
                            val url = alert.optString("url")
                            if (!url.startsWith("https://") && !url.startsWith("http://")) continue
                            add(HomeNewsAlert(alert.optInt("score"), alert.optString("source"),
                                alert.optString("summary"), url))
                        }
                    }
                    add(HomeNewsBatch(id, row.optString("capturedAt"),
                        row.optString("digest"), alerts))
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}
