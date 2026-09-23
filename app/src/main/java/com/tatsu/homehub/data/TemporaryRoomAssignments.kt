package com.tatsu.homehub.data

import com.tatsu.homehub.model.SwitchBotDevice

/** Persistent local room assignments and aliases for the devices the user actually synchronized from SwitchBot. */
object TemporaryRoomAssignments {
    const val BEDROOM = "寝室"
    const val LIVING_ROOM = "リビング"
    const val OTHER = "その他"
    const val UNASSIGNED = "未設定"
    val choices = listOf(BEDROOM, LIVING_ROOM, OTHER, UNASSIGNED)

    fun inferDefaults(devices: List<SwitchBotDevice>): Map<String, String> {
        val assigned = linkedMapOf<String, String>()
        devices.forEach { device ->
            explicitRoom(device.name)?.let { assigned[device.deviceId] = it }
        }

        devices.groupBy(::category).values.forEach { group ->
            val reserved = group.mapNotNull { assigned[it.deviceId] }.toSet()
            val availableRooms = listOf(BEDROOM, LIVING_ROOM, OTHER).filterNot(reserved::contains).toMutableList()
            group.filterNot { assigned.containsKey(it.deviceId) }.forEach { device ->
                assigned[device.deviceId] = if (availableRooms.isNotEmpty()) availableRooms.removeAt(0) else OTHER
            }
        }
        return assigned
    }

    fun applyRoom(device: SwitchBotDevice, room: String?): SwitchBotDevice {
        if (room.isNullOrBlank() || room == UNASSIGNED) return device
        val baseName = stripRoom(device.name)
        val label = when {
            device.isAirConditioner -> "エアコン"
            isLight(device) -> "照明"
            else -> baseName
        }
        return device.copy(name = "${room}の${label}", room = room, originalName = device.originalName ?: device.name)
    }

    fun explicitRoom(name: String): String? {
        val normalized = name.lowercase()
        return when {
            "寝室" in name || "bedroom" in normalized || "schlafzimmer" in normalized -> BEDROOM
            "リビング" in name || "living room" in normalized || "livingroom" in normalized || "wohnzimmer" in normalized -> LIVING_ROOM
            "その他" in name -> OTHER
            else -> null
        }
    }

    private fun category(device: SwitchBotDevice): String = when {
        device.isAirConditioner -> "air_conditioner"
        isLight(device) -> "light"
        else -> "type:${device.type.lowercase()}"
    }

    private fun isLight(device: SwitchBotDevice): Boolean =
        device.type.contains("light", ignoreCase = true) ||
            device.type.contains("bulb", ignoreCase = true) ||
            device.name.contains("電気") || device.name.contains("照明")

    private fun stripRoom(name: String): String {
        val roomPrefix = Regex("^(?:(寝室|リビング|その他)の|(?:bedroom|living room|livingroom)\\s+)", RegexOption.IGNORE_CASE)
        return name.replace(roomPrefix, "").removeSuffix("（仮）").ifBlank { name }
    }
}
