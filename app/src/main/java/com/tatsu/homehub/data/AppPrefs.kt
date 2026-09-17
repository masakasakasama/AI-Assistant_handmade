package com.tatsu.homehub.data

import android.content.Context
import org.json.JSONObject

class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var weatherLabel: String
        get() = prefs.getString(KEY_WEATHER_LABEL, "元浅草") ?: "元浅草"
        set(value) = prefs.edit().putString(KEY_WEATHER_LABEL, value).apply()

    var weatherLatitude: Double
        get() = java.lang.Double.longBitsToDouble(
            prefs.getLong(KEY_WEATHER_LAT, java.lang.Double.doubleToRawLongBits(DEFAULT_LAT))
        )
        set(value) = prefs.edit()
            .putLong(KEY_WEATHER_LAT, java.lang.Double.doubleToRawLongBits(value))
            .apply()

    var weatherLongitude: Double
        get() = java.lang.Double.longBitsToDouble(
            prefs.getLong(KEY_WEATHER_LON, java.lang.Double.doubleToRawLongBits(DEFAULT_LON))
        )
        set(value) = prefs.edit()
            .putLong(KEY_WEATHER_LON, java.lang.Double.doubleToRawLongBits(value))
            .apply()

    var lastUpdateCheckMillis: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()

    var aiBackendUrl: String
        get() = prefs.getString(KEY_AI_BACKEND_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AI_BACKEND_URL, value.trim()).apply()

    fun roomForDevice(deviceId: String): String? =
        prefs.getString(KEY_DEVICE_ROOM_PREFIX + deviceId, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    fun setRoomForDevice(deviceId: String, room: String?) {
        val key = KEY_DEVICE_ROOM_PREFIX + deviceId
        val clean = room?.trim().orEmpty()
        if (clean.isBlank()) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, clean).apply()
        }
    }

    fun saveWeatherCache(snapshot: WeatherSnapshot) {
        val json = JSONObject()
            .put("label", snapshot.label)
            .put("temperatureC", snapshot.temperatureC)
            .put("apparentTemperatureC", snapshot.apparentTemperatureC)
            .put("weatherCode", snapshot.weatherCode)
            .put("observedAt", snapshot.observedAt)
        prefs.edit().putString(KEY_WEATHER_CACHE, json.toString()).apply()
    }

    fun loadWeatherCache(): WeatherSnapshot? {
        val raw = prefs.getString(KEY_WEATHER_CACHE, null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            WeatherSnapshot(
                label = json.getString("label"),
                temperatureC = json.getDouble("temperatureC"),
                apparentTemperatureC = json.getDouble("apparentTemperatureC"),
                weatherCode = json.getInt("weatherCode"),
                observedAt = json.optString("observedAt")
            )
        }.getOrNull()
    }

    companion object {
        private const val KEY_WEATHER_LABEL = "weather_label"
        private const val KEY_WEATHER_LAT = "weather_lat"
        private const val KEY_WEATHER_LON = "weather_lon"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_WEATHER_CACHE = "weather_cache"
        private const val KEY_AI_BACKEND_URL = "ai_backend_url"
        private const val KEY_DEVICE_ROOM_PREFIX = "device_room_"

        private const val DEFAULT_LAT = 35.7126
        private const val DEFAULT_LON = 139.7800
    }
}
