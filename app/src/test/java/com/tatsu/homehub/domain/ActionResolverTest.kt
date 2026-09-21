package com.tatsu.homehub.domain

import com.tatsu.homehub.data.TemporaryRoomAssignments
import com.tatsu.homehub.model.SwitchBotDevice
import com.tatsu.homehub.model.SwitchBotDeviceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionResolverTest {
    private val ac = SwitchBotDevice("ac-bedroom", "寝室エアコン", "Air Conditioner", infrared = false)
    private val clock = object : () -> Long {
        var now = 100L
        override fun invoke(): Long = now++
    }
    private val resolver = ActionResolver(monotonicMs = clock)

    private fun intent(goal: String?, action: String? = null, target: String? = "寝室エアコン", confidence: Double = .94) =
        DeviceIntent("device_action", target, "air_conditioner", action, goal, null, confidence, "ja")

    private fun state(power: Boolean, temperature: Int) = SwitchBotDeviceState(
        power = power, temperature = temperature, mode = 2, fanSpeed = 1,
        retrievedAtElapsedMs = 100, rawJson = "{}"
    )

    @Test fun hotIsGoalAndDoesNotInventPowerActionWhenDeviceIsOff() {
        val plan = resolver.resolve(intent("cooler"), listOf(ac), state(false, 27))
        assertEquals(ActionDecision.CONFIRM, plan.decision)
        assertEquals(ActionPolicy.CONFIRM_REQUIRED, plan.policy)
        assertEquals("power_on", plan.action?.type)
        assertEquals("寝室エアコンをつけますか？", plan.response)
    }

    @Test fun hotOnRunningAirConditionerResolvesOneDegreeCooler() {
        val plan = resolver.resolve(intent("cooler"), listOf(ac), state(true, 27))
        assertEquals(ActionDecision.EXECUTE, plan.decision)
        assertEquals("set_temperature", plan.action?.type)
        assertEquals(26, plan.action?.temperatureC)
    }

    @Test fun alreadyCoolRequiresConfirmationAndDoesNotKeepDecreasing() {
        val plan = resolver.resolve(intent("cooler"), listOf(ac), state(true, 23))
        assertEquals(ActionDecision.CONFIRM, plan.decision)
        assertEquals(22, plan.action?.temperatureC)
    }

    @Test fun missingCurrentStateNeverProducesExecutableRelativeAction() {
        val plan = resolver.resolve(intent("cooler"), listOf(ac), null)
        assertEquals(ActionDecision.FALLBACK, plan.decision)
        assertNull(plan.action)
    }

    @Test fun lowConfidenceFallsBackBeforeAnyCommandIsCreated() {
        val plan = resolver.resolve(intent("cooler", confidence = .3), listOf(ac), state(true, 27))
        assertEquals(ActionDecision.FALLBACK, plan.decision)
        assertNull(plan.action)
    }

    @Test fun explicitTemperatureIsValidatedAndNormalizedToActionPlan() {
        val plan = resolver.resolve(intent("set", action = "set_ac").copy(temperatureC = 25.0), listOf(ac), null)
        assertEquals(ActionDecision.EXECUTE, plan.decision)
        assertEquals("set_temperature", plan.action?.type)
        assertEquals(25, plan.action?.temperatureC)
    }

    @Test fun ambiguousTargetRequiresClarification() {
        val second = ac.copy(deviceId = "ac-living", name = "リビングエアコン")
        val plan = resolver.resolve(intent("cooler", target = null), listOf(ac, second), state(true, 27))
        assertEquals(ActionDecision.CONFIRM, plan.decision)
        assertNull(plan.action)
    }

    @Test fun actualSwitchBotDevicesGetTemporaryRoomAliasesForComparison() {
        val bedroomAc = SwitchBotDevice("ac-a", "Air Conditioner", "Air Conditioner", infrared = true)
        val livingAc = SwitchBotDevice("ac-b", "Air Conditioner 2", "Air Conditioner", infrared = true)
        val bedroomLight = SwitchBotDevice("light-a", "寝室の電気", "Color Bulb", infrared = false)
        val livingLight = SwitchBotDevice("light-b", "Light 2", "Color Bulb", infrared = false)
        val actualDevices = listOf(bedroomAc, livingAc, bedroomLight, livingLight)
        val rooms = TemporaryRoomAssignments.inferDefaults(actualDevices)
        val comparisonDevices = actualDevices.map { device ->
            TemporaryRoomAssignments.applyToComparison(device, rooms[device.deviceId])
        }

        assertEquals("寝室", rooms[bedroomAc.deviceId])
        assertEquals("リビング", rooms[livingAc.deviceId])
        assertEquals("寝室", rooms[bedroomLight.deviceId])
        assertEquals("リビング", rooms[livingLight.deviceId])

        val bedroomIntent = DeviceIntent(
            route = "device_action", target = "寝室のエアコン", targetType = "air_conditioner",
            action = "set_ac", goal = "set", temperatureC = 26.0, confidence = .95, language = "ja"
        )
        val plan = resolver.resolve(bedroomIntent, comparisonDevices, null)
        assertEquals("ac-a", plan.device?.deviceId)
        assertEquals(ActionDecision.EXECUTE, plan.decision)
        assertEquals(26, plan.action?.temperatureC)
    }

    @Test fun explicitCurrentRoomNamesArePreservedAndGenericDeviceTargetsStayAmbiguous() {
        val bedroomAc = SwitchBotDevice("ac-a", "寝室のエアコン", "Air Conditioner", infrared = true)
        val livingAc = SwitchBotDevice("ac-b", "リビングのエアコン", "Air Conditioner", infrared = true)
        val actualDevices = listOf(bedroomAc, livingAc)
        val rooms = TemporaryRoomAssignments.inferDefaults(actualDevices)
        val comparisonDevices = actualDevices.map { device ->
            TemporaryRoomAssignments.applyToComparison(device, rooms[device.deviceId])
        }
        val genericIntent = DeviceIntent(
            route = "device_action", target = null, targetType = "air_conditioner",
            action = null, goal = "cooler", temperatureC = null, confidence = .95, language = "ja"
        )
        val plan = resolver.resolve(genericIntent, comparisonDevices, null)

        assertEquals("寝室", rooms[bedroomAc.deviceId])
        assertEquals("リビング", rooms[livingAc.deviceId])
        assertEquals(ActionDecision.CONFIRM, plan.decision)
        assertNull(plan.action)
    }

    @Test fun policyGateBlocksAnActionOutsideTheAdapterAllowlist() {
        val resolved = resolver.resolve(intent("set", action = "set_ac").copy(temperatureC = 25.0), listOf(ac), null)
            .copy(action = ResolvedAction("factory_reset"))
        var now = 1L
        val checked = ActionPolicyEngine(monotonicMs = { now++ }).evaluate(resolved)
        assertEquals(ActionDecision.FALLBACK, checked.decision)
        assertEquals(ActionPolicy.BLOCKED, checked.policy)
    }
}
