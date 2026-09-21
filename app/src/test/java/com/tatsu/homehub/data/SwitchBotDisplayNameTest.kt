package com.tatsu.homehub.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SwitchBotDisplayNameTest {
    @Test
    fun parenthesizedRoomIsMovedToFront() {
        assertEquals(
            "寝室のエアコン",
            switchBotDisplayName("エアコン（寝室）", "Air Conditioner")
        )
    }

    @Test
    fun apiDeviceNameIsKeptForLocalRoomAssignment() {
        assertEquals(
            "Light",
            switchBotDisplayName("Light", "Color Bulb")
        )
    }

    @Test
    fun apiNameIsPreservedUntilTemporaryAssignmentIsSelected() {
        assertEquals(
            "Light",
            switchBotDisplayName("Light", "Bot")
        )
    }
}
