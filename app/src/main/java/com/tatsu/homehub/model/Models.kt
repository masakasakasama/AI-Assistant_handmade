package com.tatsu.homehub.model

data class SwitchBotDevice(
    val deviceId: String,
    val name: String,
    val type: String,
    val infrared: Boolean,
    val hubDeviceId: String? = null,
    val room: String? = null,
    val originalName: String? = null,
    val botMode: String? = null
) {
    val isAirConditioner: Boolean
        get() = infrared && type.equals("Air Conditioner", ignoreCase = true)

    val isK10RobotVacuum: Boolean
        get() = !infrared && type.trim().lowercase() in setOf(
            "k10+", "k10+ pro", "mini robot vacuum k10+", "mini robot vacuum k10+ pro"
        )

    val isHub: Boolean
        get() = type.contains("hub", ignoreCase = true)

    val controlProfile: DeviceControlProfile?
        get() = SwitchBotControlProfiles.forDevice(this)

    val supportsDirectPowerControl: Boolean
        get() = controlProfile != null
}

data class AcControlState(
    val temperature: Int = 26,
    val mode: Int = 2,
    val fanSpeed: Int = 1,
    val power: Boolean = false
)

data class SwitchBotDeviceState(
    val power: Boolean? = null,
    val temperature: Int? = null,
    val mode: Int? = null,
    val fanSpeed: Int? = null,
    val brightness: Int? = null,
    val retrievedAtElapsedMs: Long,
    val rawJson: String
)

data class HubEnvironmentState(
    val temperatureC: Double,
    val humidityPercent: Int,
    val lightLevel: Int?,
    val retrievedAtElapsedMs: Long
)

data class LocalAlarm(
    val id: String,
    val hour: Int,
    val minute: Int,
    val label: String,
    val repeatMask: Int,
    val enabled: Boolean = true
) {
    fun repeatsOn(dayIndexMondayZero: Int): Boolean =
        repeatMask and (1 shl dayIndexMondayZero) != 0
}
