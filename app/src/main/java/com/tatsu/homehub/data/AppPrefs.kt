package com.tatsu.homehub.data

import android.content.Context

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

    companion object {
        private const val KEY_WEATHER_LABEL = "weather_label"
        private const val KEY_WEATHER_LAT = "weather_lat"
        private const val KEY_WEATHER_LON = "weather_lon"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"

        private const val DEFAULT_LAT = 35.7126
        private const val DEFAULT_LON = 139.7800
    }
}
