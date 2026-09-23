package com.tatsu.homehub.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchBotDeviceCapabilityTest {
    @Test
    fun hubsDoNotExposeDirectPowerControl() {
        assertFalse(
            SwitchBotDevice("hub2", "寝室のハブ2", "Hub 2", infrared = false)
                .supportsDirectPowerControl
        )
        assertFalse(
            SwitchBotDevice("mini", "寝室のハブミニ", "Hub Mini2", infrared = false)
                .supportsDirectPowerControl
        )
    }

    @Test
    fun normalDevicesKeepDirectPowerControl() {
        assertTrue(
            SwitchBotDevice("light", "寝室の照明", "Color Bulb", infrared = false)
                .supportsDirectPowerControl
        )
        assertTrue(
            SwitchBotDevice("bot", "ボット", "Bot", infrared = false)
                .supportsDirectPowerControl
        )
    }
}
