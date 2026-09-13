package com.tatsu.homehub.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class WeatherSnapshot(
    val label: String,
    val temperatureC: Double,
    val apparentTemperatureC: Double,
    val weatherCode: Int,
    val observedAt: String
)

class WeatherClient {
    suspend fun current(
        label: String,
        latitude: Double,
        longitude: Double
    ): Result<WeatherSnapshot> = withContext(Dispatchers.IO) {
        runCatching {
            val query = "latitude=" + enc(latitude.toString()) +
                "&longitude=" + enc(longitude.toString()) +
                "&current=temperature_2m,apparent_temperature,weather_code" +
                "&timezone=auto"
            val connection = (URL(BASE_URL + "?" + query).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "TatsuHome/0.1")
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()

            if (code !in 200..299) error("Weather HTTP " + code + ": " + body)

            val current = JSONObject(body).getJSONObject("current")
            WeatherSnapshot(
                label = label,
                temperatureC = current.getDouble("temperature_2m"),
                apparentTemperatureC = current.getDouble("apparent_temperature"),
                weatherCode = current.getInt("weather_code"),
                observedAt = current.optString("time")
            )
        }
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"

        fun weatherLabel(code: Int): String = when (code) {
            0 -> "快晴"
            1 -> "晴れ"
            2 -> "一部くもり"
            3 -> "くもり"
            45, 48 -> "霧"
            51, 53, 55, 56, 57 -> "霧雨"
            61, 63, 65, 66, 67 -> "雨"
            71, 73, 75, 77 -> "雪"
            80, 81, 82 -> "にわか雨"
            85, 86 -> "にわか雪"
            95, 96, 99 -> "雷雨"
            else -> "天気コード " + code
        }
    }
}
