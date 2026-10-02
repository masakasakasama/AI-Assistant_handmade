package com.tatsu.homehub.data

import android.content.Context
import com.tatsu.homehub.BuildConfig
import com.tatsu.homehub.model.AcControlState
import com.tatsu.homehub.voice.WakeWordChoice
import com.tatsu.homehub.voice.WakeWordSettings
import org.json.JSONObject

class AppPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)

    var wakeWordSettings: WakeWordSettings
        get() = WakeWordSettings(
            WakeWordChoice.fromStored(prefs.getString("wake_word_choice", null)),
            prefs.getString("wake_word_custom_phrase", null)?.takeIf { it.isNotBlank() } ?: "独自ウェイクワード"
        )
        set(value) = prefs.edit()
            .putString("wake_word_choice", value.choice.name)
            .putString("wake_word_custom_phrase", value.customPhrase)
            .apply()

    var wakeInterruptionEnabled: Boolean
        get() = prefs.getBoolean("wake_interruption_enabled", true)
        set(value) = prefs.edit().putBoolean("wake_interruption_enabled", value).apply()

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

    var answerMode: String
        get() = prefs.getString(KEY_ANSWER_MODE, "balanced")
            ?.takeIf { it in setOf("quick", "balanced", "deep") } ?: "balanced"
        set(value) {
            if (value in setOf("quick", "balanced", "deep")) {
                prefs.edit().putString(KEY_ANSWER_MODE, value).apply()
            }
        }

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

    var preferOnDeviceRecognition: Boolean
        get() = prefs.getBoolean("prefer_on_device_recognition", false)
        set(value) = prefs.edit().putBoolean("prefer_on_device_recognition", value).apply()

    fun loadTemporaryRoomAssignments(): Map<String, String> {
        val raw = prefs.getString(KEY_TEMPORARY_ROOM_ASSIGNMENTS, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap { json.keys().forEach { deviceId -> put(deviceId, json.optString(deviceId, "")) } }
        }.getOrDefault(emptyMap())
    }

    fun saveTemporaryRoomAssignments(assignments: Map<String, String>) {
        val json = JSONObject()
        assignments.forEach { (deviceId, room) -> json.put(deviceId, room) }
        prefs.edit().putString(KEY_TEMPORARY_ROOM_ASSIGNMENTS, json.toString()).apply()
    }

    fun loadAcControlStates(): Map<String, AcControlState> {
        val raw = prefs.getString(KEY_AC_CONTROL_STATES, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { deviceId ->
                    val state = json.optJSONObject(deviceId) ?: return@forEach
                    put(
                        deviceId,
                        AcControlState(
                            temperature = state.optInt("temperature", 26).coerceIn(16, 30),
                            mode = state.optInt("mode", 2).coerceIn(1, 5),
                            fanSpeed = state.optInt("fanSpeed", 1).coerceIn(1, 4),
                            power = state.optBoolean("power", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun saveAcControlStates(states: Map<String, AcControlState>) {
        val json = JSONObject()
        states.forEach { (deviceId, state) ->
            json.put(
                deviceId,
                JSONObject()
                    .put("temperature", state.temperature)
                    .put("mode", state.mode)
                    .put("fanSpeed", state.fanSpeed)
                    .put("power", state.power)
            )
        }
        prefs.edit().putString(KEY_AC_CONTROL_STATES, json.toString()).apply()
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
        private const val KEY_ANSWER_MODE = "answer_mode"
        private const val KEY_AI_BACKEND_URL = "ai_backend_url"
        private const val KEY_VOICE_LANGUAGE = "voice_language_tag"
        private const val KEY_VOICE_AUTO_MIGRATED = "voice_language_auto_migrated_v044"
        private const val KEY_TEMPORARY_ROOM_ASSIGNMENTS = "temporary_switchbot_room_assignments_v1"
        private const val KEY_AC_CONTROL_STATES = "switchbot_ac_control_states_v1"

        const val VOICE_LANGUAGE_AUTO = "auto"

        private const val DEFAULT_LAT = 35.7126
        private const val DEFAULT_LON = 139.7800

    }
}
