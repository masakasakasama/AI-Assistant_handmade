package com.tatsu.homehub.model

data class SwitchBotDevice(
    val deviceId: String,
    val name: String,
    val type: String,
    val infrared: Boolean,
    val hubDeviceId: String? = null
) {
    val isAirConditioner: Boolean
        get() = type.equals("Air Conditioner", ignoreCase = true)
}

data class AcControlState(
    val temperature: Int = 26,
    val mode: Int = 2,
    val fanSpeed: Int = 1,
    val power: Boolean = false
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
