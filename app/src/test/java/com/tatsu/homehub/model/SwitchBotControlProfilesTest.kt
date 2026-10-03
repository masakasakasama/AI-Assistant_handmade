package com.tatsu.homehub.model

import org.junit.Assert.*
import org.junit.Test

class SwitchBotControlProfilesTest {
    private fun device(type: String, infrared: Boolean = false, mode: String? = null) =
        SwitchBotDevice("test", type, type, infrared, botMode = mode)

    @Test fun differentVacuumGenerationsHaveDifferentCommandsAndParameters() {
        for (type in listOf("K10+", "K10+ Pro", "Robot Vacuum Cleaner S1", "Robot Vacuum Cleaner S1 Plus")) {
            val p = device(type).controlProfile!!
            assertEquals(DeviceControlCommand("start"), p.on)
            assertEquals(DeviceControlCommand("stop"), p.off)
        }
        for (type in listOf("Floor Cleaning Robot S10", "S20", "K10+ Pro Combo", "Robot Vacuum Cleaner K10+ Pro Combo", "K11+", "Robot Vacuum Cleaner K11+", "K20+ Pro", "Robot Vacuum Cleaner K20 Plus Pro")) {
            val p = device(type).controlProfile!!
            assertEquals("startClean", p.on.name)
            assertTrue(p.on.parameter.contains("\"action\":\"sweep\""))
            assertTrue(p.on.parameter.contains("\"times\":1"))
            assertEquals(DeviceControlCommand("pause"), p.off)
        }
    }
    @Test fun blindTiltUsesItsOwnOpenCloseCommands() {
        val p = device("Blind Tilt").controlProfile!!
        assertEquals(DeviceControlCommand("fullyOpen"), p.on)
        assertEquals(DeviceControlCommand("closeDown"), p.off)
    }
    @Test fun botModeControlsWhetherAnAbsoluteOffExists() {
        assertEquals("turnOff", device("Bot", mode = "switchMode").controlProfile!!.off!!.name)
        for (mode in listOf(null, "pressMode", "customizeMode")) {
            val p = device("Bot", mode = mode).controlProfile!!
            assertEquals("press", p.on.name)
            assertEquals("押す", p.onLabel)
            assertNull(p.off)
        }
    }
    @Test fun sensorsLocksUnknownAndUnspecifiedRelayChannelsNeverExposePower() {
        for (type in listOf("Hub 2", "Meter", "Meter Pro", "Lock", "Lock Pro", "Relay Switch 2PM", "Mystery Device", "Indoor Cam", "Water Leak Detector")) {
            assertNull(type, device(type).controlProfile)
            assertFalse(type, device(type).supportsDirectPowerControl)
        }
        assertNull(device("Others", true).controlProfile)
        assertEquals("turnOn", device("Light", true).controlProfile!!.on.name)
    }
    @Test fun normalLightsPlugsAndCurtainsRetainDocumentedPower() {
        for (type in listOf("Color Bulb", "Ceiling Light", "Plug Mini (JP)", "Plug Mini (US)", "Relay Switch 1PM", "Curtain", "Curtain 3", "Curtain3", "Humidifier2")) {
            val p = device(type).controlProfile!!
            assertEquals(DeviceControlCommand("turnOn"), p.on)
            assertEquals(DeviceControlCommand("turnOff"), p.off)
        }
    }
}
