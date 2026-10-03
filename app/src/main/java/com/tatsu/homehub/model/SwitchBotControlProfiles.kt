package com.tatsu.homehub.model

/** Only documented commands may leave the app. Sources: docs/SWITCHBOT_COMMAND_AUDIT.md. */
data class DeviceControlCommand(val name: String, val parameter: String = "default")
data class DeviceControlProfile(
    val on: DeviceControlCommand, val off: DeviceControlCommand?,
    val onLabel: String = "ON", val offLabel: String = "OFF"
)

object SwitchBotControlProfiles {
    private val standardPowerTypes = setOf(
        "air purifier pm2.5",
        "air purifier table pm2.5",
        "air purifier table voc",
        "air purifier voc",
        "battery circulator fan",
        "battery circulator fan 2 pro",
        "candle warmer lamp",
        "ceiling light",
        "ceiling light pro",
        "circulator fan",
        "color bulb",
        "curtain",
        "curtain 3",
        "floor lamp",
        "garage door opener",
        "humidifier",
        "humidifier2",
        "humidifier2",
        "permanent outdoor lights",
        "plug",
        "plug mini (eu)",
        "plug mini (jp)",
        "plug mini (us)",
        "relay switch 1",
        "relay switch 1pm",
        "rgbic neon rope light",
        "rgbic neon wire rope light",
        "rgbicww ceiling light",
        "rgbicww floor lamp",
        "rgbicww strip light",
        "smart radiator thermostat",
        "standing fan",
        "strip light",
        "strip light 3"
    )
    private val simpleVacuumTypes = setOf("k10+", "k10+ pro", "mini robot vacuum k10+", "mini robot vacuum k10+ pro",
        "s1", "s1 plus", "robot vacuum cleaner s1", "robot vacuum cleaner s1 plus")
    private val wetVacuumTypes = setOf("s10", "s20", "floor cleaning robot s10", "floor cleaning robot s20", "robot vacuum cleaner s10", "robot vacuum cleaner s20")
    private val advancedVacuumTypes = setOf("k10+ pro combo", "robot vacuum cleaner k10+ pro combo", "k11+", "robot vacuum k11+", "k20+ pro", "robot vacuum cleaner k20+ pro")

    fun forDevice(device: SwitchBotDevice): DeviceControlProfile? {
        val type = device.type.trim().lowercase()
        if (device.infrared) {
            // The official virtual-remote API permits standard power for every appliance except Others.
            return if (type in setOf("", "others", "other", "unknown", "infrared")) null
            else DeviceControlProfile(DeviceControlCommand("turnOn"), DeviceControlCommand("turnOff"))
        }
        return when {
            type == "bot" -> if (device.botMode == "switchMode") {
                DeviceControlProfile(DeviceControlCommand("turnOn"), DeviceControlCommand("turnOff"))
            } else DeviceControlProfile(DeviceControlCommand("press"), null, "押す")
            type in simpleVacuumTypes -> DeviceControlProfile(DeviceControlCommand("start"), DeviceControlCommand("stop"), "掃除開始", "停止")
            type in wetVacuumTypes || type in advancedVacuumTypes -> {
                val parameter = if (type in wetVacuumTypes)
                    "{\"action\":\"sweep\",\"param\":{\"fanLevel\":1,\"waterLevel\":1,\"times\":1}}"
                else "{\"action\":\"sweep\",\"param\":{\"fanLevel\":1,\"times\":1}}"
                DeviceControlProfile(DeviceControlCommand("startClean", parameter), DeviceControlCommand("pause"), "掃除開始", "一時停止")
            }
            type == "blind tilt" -> DeviceControlProfile(DeviceControlCommand("fullyOpen"), DeviceControlCommand("closeDown"), "開く", "閉じる（下向き）")
            type in standardPowerTypes -> DeviceControlProfile(DeviceControlCommand("turnOn"), DeviceControlCommand("turnOff"),
                if (type in setOf("curtain", "curtain 3")) "開く" else "ON", if (type in setOf("curtain", "curtain 3")) "閉じる" else "OFF")
            else -> null // Sensors, hubs, locks, unspecified channels and unknown types are never guessed.
        }
    }
}
