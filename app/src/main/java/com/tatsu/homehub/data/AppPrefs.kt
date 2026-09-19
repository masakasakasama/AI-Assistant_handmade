package com.tatsu.homehub.data

import android.content.Context
import com.tatsu.homehub.BuildConfig
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
        get() = prefs.getString(KEY_AI_BACKEND_URL, null)
            ?.takeIf(String::isNotBlank)
            ?: BuildConfig.DEFAULT_AI_BACKEND_URL
        set(value) = prefs.edit().putString(KEY_AI_BACKEND_URL, value.trim()).apply()

    var voiceLanguageTag: String
        get() {
            if (!prefs.getBoolean(KEY_VOICE_AUTO_MIGRATED, false)) {
                prefs.edit()
                    .putString(KEY_VOICE_LANGUAGE, VOICE_LANGUAGE_AUTO)
                    .putBoolean(KEY_VOICE_AUTO_MIGRATED, true)
                    .apply()
                return VOICE_LANGUAGE_AUTO
            }
            return prefs.getString(KEY_VOICE_LANGUAGE, VOICE_LANGUAGE_AUTO) ?: VOICE_LANGUAGE_AUTO
        }
        set(value) = prefs.edit()
            .putString(KEY_VOICE_LANGUAGE, value)
            .putBoolean(KEY_VOICE_AUTO_MIGRATED, true)
            .apply()

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
        private const val KEY_VOICE_LANGUAGE = "voice_language_tag"
        private const val KEY_VOICE_AUTO_MIGRATED = "voice_language_auto_migrated_v044"

        const val VOICE_LANGUAGE_AUTO = "auto"

        private const val DEFAULT_LAT = 35.7126
        private const val DEFAULT_LON = 139.7800

    }
}
