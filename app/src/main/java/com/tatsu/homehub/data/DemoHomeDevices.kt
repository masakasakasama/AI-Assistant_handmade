package com.tatsu.homehub.data

import com.tatsu.homehub.model.SwitchBotDevice
import com.tatsu.homehub.model.SwitchBotDeviceState

/** Fixed fixtures for dry-run room/device resolution. These IDs must never reach SwitchBotClient. */
object DemoHomeDevices {
    data class Fixture(val device: SwitchBotDevice, val state: SwitchBotDeviceState?, val summary: String)

    val fixtures = listOf(
        Fixture(
            device = SwitchBotDevice("demo-bedroom-ac", "寝室のエアコン（仮）", "Air Conditioner", false, demoOnly = true),
            state = state(power = true, temperature = 27, mode = 2, fanSpeed = 1),
            summary = "ON · 冷房 · 27℃"
        ),
        Fixture(
            device = SwitchBotDevice("demo-living-ac", "リビングのエアコン（仮）", "Air Conditioner", false, demoOnly = true),
            state = state(power = false, temperature = 26, mode = 2, fanSpeed = 1),
            summary = "OFF · 冷房 · 26℃"
        ),
        Fixture(
            device = SwitchBotDevice("demo-bedroom-light", "寝室の照明（仮）", "Color Bulb", false, demoOnly = true),
            state = state(power = true, brightness = 45),
            summary = "ON · 明るさ45%"
        ),
        Fixture(
            device = SwitchBotDevice("demo-living-light", "リビングの照明（仮）", "Color Bulb", false, demoOnly = true),
            state = state(power = false, brightness = 0),
            summary = "OFF"
        )
    )

    val devices: List<SwitchBotDevice> = fixtures.map(Fixture::device)

    fun stateFor(deviceId: String): SwitchBotDeviceState? =
        fixtures.firstOrNull { it.device.deviceId == deviceId }?.state

    private fun state(
        power: Boolean,
        temperature: Int? = null,
        mode: Int? = null,
        fanSpeed: Int? = null,
        brightness: Int? = null
    ) = SwitchBotDeviceState(
        power = power,
        temperature = temperature,
        mode = mode,
        fanSpeed = fanSpeed,
        brightness = brightness,
        retrievedAtElapsedMs = 0L,
        rawJson = "{\"source\":\"demo_fixture\"}"
    )
}
