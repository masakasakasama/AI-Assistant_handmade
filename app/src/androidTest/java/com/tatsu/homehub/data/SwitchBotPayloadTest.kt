package com.tatsu.homehub.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tatsu.homehub.model.SwitchBotDevice
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SwitchBotPayloadTest {
    @Test fun newVacuumParametersAreObjectsInTheActualSerializedRequest() {
        for (type in listOf("Floor Cleaning Robot S10", "S20", "K10+ Pro Combo", "K11+", "K20+ Pro")) {
            val control = SwitchBotDevice("test", "掃除機", type, false).controlProfile!!.on
            val payload = JSONObject(switchBotCommandPayload(control).toString())
            assertEquals("startClean", payload.getString("command"))
            assertEquals("command", payload.getString("commandType"))
            assertTrue(payload.get("parameter") is JSONObject)
            val parameter = payload.getJSONObject("parameter")
            assertEquals("sweep", parameter.getString("action"))
            assertEquals(1, parameter.getJSONObject("param").getInt("times"))
        }
    }
    @Test fun k10BulbBlindAndSwitchBotUseDefaultStringParameters() {
        for (type in listOf("K10+ Pro", "Color Bulb", "Blind Tilt", "Bot", "Curtain3")) {
            val profile = SwitchBotDevice("test", type, type, false, botMode="switchMode").controlProfile!!
            for (control in listOfNotNull(profile.on, profile.off)) {
                val payload = JSONObject(switchBotCommandPayload(control).toString())
                assertEquals(control.name, payload.getString("command"))
                assertEquals("default", payload.get("parameter"))
            }
        }
    }
}
