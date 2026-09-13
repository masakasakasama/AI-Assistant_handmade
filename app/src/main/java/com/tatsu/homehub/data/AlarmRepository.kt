package com.tatsu.homehub.data

import android.content.Context
import com.tatsu.homehub.model.LocalAlarm
import org.json.JSONArray
import org.json.JSONObject

class AlarmRepository(context: Context) {
    private val prefs = context.getSharedPreferences("alarms", Context.MODE_PRIVATE)

    fun all(): List<LocalAlarm> {
        val raw = prefs.getString(KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        LocalAlarm(
                            id = o.getString("id"),
                            hour = o.getInt("hour"),
                            minute = o.getInt("minute"),
                            label = o.optString("label", "Alarm"),
                            repeatMask = o.optInt("repeatMask", 0),
                            enabled = o.optBoolean("enabled", true)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun get(id: String): LocalAlarm? = all().firstOrNull { it.id == id }

    fun upsert(alarm: LocalAlarm) =
        write(all().filterNot { it.id == alarm.id } + alarm)

    fun delete(id: String) =
        write(all().filterNot { it.id == id })

    fun setEnabled(id: String, enabled: Boolean) =
        write(all().map { if (it.id == id) it.copy(enabled = enabled) else it })

    private fun write(items: List<LocalAlarm>) {
        val array = JSONArray()
        items.forEach { a ->
            array.put(
                JSONObject()
                    .put("id", a.id)
                    .put("hour", a.hour)
                    .put("minute", a.minute)
                    .put("label", a.label)
                    .put("repeatMask", a.repeatMask)
                    .put("enabled", a.enabled)
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    companion object {
        private const val KEY = "alarm_list"
    }
}
