package com.tatsu.homehub.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tatsu.homehub.model.AcControlState
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AcSettingsPersistenceTest {
    private fun context(): Context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = super.getSharedPreferences("ac-settings-test", mode)
    }
    @Test fun legacySavedSettingsRemainAvailableForConfirmedRelativeProposals() {
        val context = context()
        val store = context.getSharedPreferences("ignored", Context.MODE_PRIVATE)
        store.edit().clear().putString("switchbot_ac_control_states_v1", """{"ac":{"temperature":27,"mode":2,"fanSpeed":3,"power":true}}""").commit()
        try {
            val state = AppPrefs(context).loadAcControlStates().getValue("ac")
            assertTrue(state.settingsKnown)
            assertEquals(27, state.temperature)
            assertEquals(3, state.fanSpeed)
        } finally { store.edit().clear().commit() }
    }
    @Test fun successfulFullSettingsSurviveReopeningButPowerOnlyDefaultsStayUnknown() {
        val context = context()
        val store = context.getSharedPreferences("ignored", Context.MODE_PRIVATE)
        store.edit().clear().commit()
        try {
            AppPrefs(context).saveAcControlStates(mapOf("known" to AcControlState(27,2,3,true,settingsKnown=true), "powerOnly" to AcControlState(power=true)))
            val reloaded = AppPrefs(context).loadAcControlStates()
            assertTrue(reloaded.getValue("known").settingsKnown)
            assertEquals(3, reloaded.getValue("known").fanSpeed)
            assertFalse(reloaded.getValue("powerOnly").settingsKnown)
        } finally { store.edit().clear().commit() }
    }
}
