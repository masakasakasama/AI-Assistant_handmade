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
    fun temporaryBedroomLightAliasIsApplied() {
        assertEquals(
            "寝室の電気",
            switchBotDisplayName("Light", "Color Bulb")
        )
    }

    @Test
    fun lightAliasIsNotAppliedToNonLightDevice() {
        assertEquals(
            "Light",
            switchBotDisplayName("Light", "Bot")
        )
    }
}
