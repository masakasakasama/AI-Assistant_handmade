package com.tatsu.homehub.data

import com.tatsu.homehub.model.SwitchBotDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SwitchBotPowerCommandTest {
    @Test fun k10FamilyStartsAndStopsCleaningInsteadOfSendingPowerCommands() {
        for (type in listOf("K10+", "K10+ Pro", "Mini Robot Vacuum K10+", "Mini Robot Vacuum K10+ Pro", " k10+ PRO ")) {
            val device = SwitchBotDevice("vacuum", "掃除機", type, infrared = false)
            assertEquals(type, "start", switchBotPowerCommand(device, true))
            assertEquals(type, "stop", switchBotPowerCommand(device, false))
        }
    }

    @Test fun lightsBotsAndInfraredAcKeepTheirExistingCommands() {
        for ((type, infrared) in listOf("Color Bulb" to false, "Bot" to false, "Air Conditioner" to true)) {
            val device = SwitchBotDevice("device", "家電", type, infrared, botMode = "switchMode")
            assertEquals("turnOn", switchBotPowerCommand(device, true))
            assertEquals("turnOff", switchBotPowerCommand(device, false))
        }
    }

    @Test fun otherVacuumFamiliesAreNotMistakenForK10() {
        assertFalse(SwitchBotDevice("s10", "掃除機", "Floor Cleaning Robot S10", false).isK10RobotVacuum)
    }

    @Test(expected = IllegalArgumentException::class)
    fun hubsCannotReceivePowerCommands() {
        switchBotPowerCommand(SwitchBotDevice("hub", "Hub", "Hub 2", false), true)
    }
}
