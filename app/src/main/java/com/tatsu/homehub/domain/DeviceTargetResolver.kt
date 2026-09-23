package com.tatsu.homehub.domain

import com.tatsu.homehub.data.TemporaryRoomAssignments
import com.tatsu.homehub.model.SwitchBotDevice

/** The same target selection for status reads, voice execution and comparison. */
object DeviceTargetResolver {
    fun resolve(target: String?, targetType: String?, devices: List<SwitchBotDevice>): List<SwitchBotDevice> {
        val text = target.orEmpty().trim()
        val room = TemporaryRoomAssignments.explicitRoom(text)
        val normalized = normalize(text)
        val type = when (normalize(targetType.orEmpty())) {
            "エアコン" -> "ac"
            "照明" -> "light"
            else -> when {
                normalized.contains("エアコン") -> "ac"
                normalized.contains("照明") -> "light"
                else -> null
            }
        }
        val candidates = devices.filter { device ->
            (room == null || (device.room ?: TemporaryRoomAssignments.explicitRoom(device.name)) == room) &&
                when (type) {
                    "ac" -> device.isAirConditioner
                    "light" -> device.type.contains("light", true) || device.type.contains("bulb", true)
                    else -> true
                }
        }.distinctBy { it.deviceId }
        if (text.isBlank()) return if (type == null) emptyList() else candidates
        val generic = setOf("エアコン", "照明", room.orEmpty(), room.orEmpty() + "エアコン", room.orEmpty() + "照明")
        if (type != null && normalized in generic) return candidates
        val exact = candidates.filter { device ->
            device.deviceId == text || normalize(device.name) == normalized ||
                (room == null && device.originalName?.let(::normalize) == normalized)
        }
        if (exact.isNotEmpty()) return exact
        // Only a generic type or room + type may expand to all matching devices.
        // An unknown explicit name must never fall back to a different device.
        return emptyList()
    }

    private fun normalize(value: String): String = value.lowercase()
        .replace("air_conditioner", "エアコン").replace("air conditioner", "エアコン")
        .replace("klimaanlage", "エアコン")
        .replace("living room", "リビング").replace("livingroom", "リビング").replace("wohnzimmer", "リビング")
        .replace("bedroom", "寝室").replace("schlafzimmer", "寝室")
        .replace("lights", "照明").replace("light", "照明").replace("licht", "照明")
        .replace("電気", "照明").replace("ライト", "照明")
        .replace(Regex("[\\s　の]"), "")
}
