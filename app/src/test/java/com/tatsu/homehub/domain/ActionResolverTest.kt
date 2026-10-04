package com.tatsu.homehub.domain

import com.tatsu.homehub.data.TemporaryRoomAssignments
import com.tatsu.homehub.model.SwitchBotDevice
import com.tatsu.homehub.model.SwitchBotDeviceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActionResolverTest {
    private val ac = SwitchBotDevice("ac-bedroom", "寝室エアコン", "Air Conditioner", infrared = true)
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

    @Test fun lowConfidenceGoalOffersAValidatedProposalInsteadOfExecuting() {
        val plan = resolver.resolve(intent("cooler", confidence = .3), listOf(ac), state(true, 27))
        assertEquals(ActionDecision.CONFIRM, plan.decision)
        assertEquals(26, plan.action?.temperatureC)
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
            TemporaryRoomAssignments.applyRoom(device, rooms[device.deviceId])
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
            TemporaryRoomAssignments.applyRoom(device, rooms[device.deviceId])
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
    @Test fun bedroomLightSynonymsSelectSameRealDevice() {
        val devices = listOf(
            TemporaryRoomAssignments.applyRoom(SwitchBotDevice("bed", "Light", "Light", true), "寝室"),
            TemporaryRoomAssignments.applyRoom(SwitchBotDevice("living", "Light 2", "Light", true), "リビング")
        )
        for (target in listOf("寝室の電気", "寝室照明", "bedroom light", "Schlafzimmer Licht")) {
            assertEquals("bed", DeviceTargetResolver.resolve(target, "light", devices).single().deviceId)
        }
        assertEquals(2, DeviceTargetResolver.resolve("電気", "light", devices).size)
    }

    @Test fun explicitMissingRoomNeverFallsBackToAnotherRoom() {
        val living = TemporaryRoomAssignments.applyRoom(ac, "リビング")
        assertEquals(emptyList<SwitchBotDevice>(), DeviceTargetResolver.resolve("寝室のエアコン", "air_conditioner", listOf(living)))
        assertEquals(emptyList<SwitchBotDevice>(), DeviceTargetResolver.resolve("書斎のエアコン", "air_conditioner", listOf(living)))
    }

    @Test fun changingRoomPreservesDeviceIdAndOriginalName() {
        val raw = SwitchBotDevice("actual-id", "寝室の電気", "Light", true)
        val assigned = TemporaryRoomAssignments.applyRoom(raw, "リビング")
        assertEquals("actual-id", assigned.deviceId)
        assertEquals(raw.name, assigned.originalName)
        assertEquals("actual-id", DeviceTargetResolver.resolve("リビングの電気", "light", listOf(assigned)).single().deviceId)
        assertEquals(emptyList<SwitchBotDevice>(), DeviceTargetResolver.resolve("寝室の電気", "light", listOf(assigned)))
    }

    @Test fun sameRoomDuplicatesRemainAmbiguous() {
        val devices = listOf(ac.copy(deviceId = "a"), ac.copy(deviceId = "b")).map {
            TemporaryRoomAssignments.applyRoom(it, "寝室")
        }
        assertEquals(2, DeviceTargetResolver.resolve("寝室のエアコン", "air_conditioner", devices).size)
        assertEquals("b", DeviceTargetResolver.resolve("b", "air_conditioner", devices).single().deviceId)
    }

    @Test fun unsupportedPowerNeverProducesAnExecutableVoicePlan() {
        for (type in listOf("Lock", "Hub 2", "Meter", "Relay Switch 2PM", "Unknown")) {
            val device = SwitchBotDevice("target", "対象", type, false)
            val request = DeviceIntent("device_action", "target", null, "turn_on", null, null, .99, "ja")
            val plan = resolver.resolve(request, listOf(device), null)
            assertEquals(type, ActionPolicy.BLOCKED, plan.policy)
            assertNull(plan.action)
        }
    }
    @Test fun momentaryBotRequiresConfirmationAndHasNoOffAction() {
        val device = SwitchBotDevice("target", "対象", "Bot", false, botMode = "pressMode")
        val request = DeviceIntent("device_action", "target", null, "turn_on", null, null, .99, "ja")
        assertEquals(ActionDecision.CONFIRM, resolver.resolve(request, listOf(device), null).decision)
        assertEquals(ActionPolicy.BLOCKED, resolver.resolve(request.copy(action="turn_off"), listOf(device), null).policy)
    }

    @Test fun powerOffExpandsAllMatchingDevicesOnceAndKeepsRoomAndNameFilters() {
        val living = ac.copy(deviceId = "living", name = "リビングのエアコン")
        val bedroom = ac.copy(deviceId = "bedroom", name = "寝室のエアコン")
        val lamp = SwitchBotDevice("lamp", "照明", "Color Bulb", false)
        val devices = listOf(living, bedroom, living, lamp)
        fun targets(name: String, action: String = "turn_off") =
            DeviceTargetResolver.powerOffTargets(name, "air_conditioner", action, devices)
        assertEquals(listOf("living", "bedroom"), targets("エアコン").map { it.deviceId })
        assertEquals(listOf("bedroom"), targets("寝室のエアコン").map { it.deviceId })
        assertEquals(listOf("living"), targets("living").map { it.deviceId })
        assertEquals(emptyList<SwitchBotDevice>(), targets("書斎のエアコン"))
        assertEquals(emptyList<SwitchBotDevice>(), targets("エアコン", "turn_on"))
        for (device in targets("エアコン")) {
            val plan = resolver.resolve(intent(null, "turn_off", device.deviceId), devices, null)
            assertEquals(ActionDecision.EXECUTE, plan.decision)
            assertEquals("power_off", plan.action?.type)
        }
    }

    @Test fun batchPowerOffStillValidatesConfidenceAndDeviceCapabilitiesIndividually() {
        val devices = listOf(ac.copy(deviceId="a"), ac.copy(deviceId="b"))
        for (device in DeviceTargetResolver.powerOffTargets("エアコン", "air_conditioner", "turn_off", devices)) {
            assertEquals(ActionDecision.CONFIRM,
                resolver.resolve(intent(null,"turn_off",device.deviceId,.2),devices,null).decision)
        }
        val unknown = SwitchBotDevice("unknown", "照明", "Unknown", false)
        assertEquals(ActionPolicy.BLOCKED,
            resolver.resolve(DeviceIntent("device_action","unknown",null,"turn_off",null,null,.99,"ja"), listOf(unknown), null).policy)
    }

    @Test fun lowCategoryConfidenceHasAConcreteConfirmablePowerPlanInsteadOfAnUnanswerableRejection() {
        for (action in listOf("turn_on", "turn_off")) {
            for (confidence in listOf(.2, Double.NaN)) {
                val plan = resolver.resolve(intent(null, action, ac.deviceId, confidence), listOf(ac), null)
                assertEquals(ActionDecision.CONFIRM, plan.decision)
                assertEquals(ac.deviceId, plan.device?.deviceId)
                assertEquals(if (action == "turn_off") "power_off" else "power_on", plan.action?.type)
            }
        }
        val lamp = SwitchBotDevice("desk", "デスク照明", "Color Bulb", false)
        val request = DeviceIntent("device_action", "desk", "light", "turn_off", null, null, 1.0, "ja")
        assertEquals(ActionDecision.EXECUTE, resolver.resolve(request, listOf(lamp), null).decision)
        val unsupported = SwitchBotDevice("lock", "玄関", "Lock", false)
        assertEquals(ActionPolicy.BLOCKED,
            resolver.resolve(request.copy(target="lock", targetType=null), listOf(unsupported), null).policy)
    }

    @Test fun lastSentSettingsAllowRelativeChangeButAlwaysRequireConfirmation() {
        val previous = state(true, 27).copy(fromSavedSettings = true)
        val plan = resolver.resolve(intent("cooler"), listOf(ac), previous)
        assertEquals(ActionDecision.CONFIRM, plan.decision)
        assertEquals(26, plan.action?.temperatureC)
        org.junit.Assert.assertTrue(plan.response.contains("最後に送った設定"))
        assertEquals(2, plan.currentState?.mode)
    }
    @Test fun savedPowerStateCannotPretendToBeAnActualNoop() {
        val plan = resolver.resolve(intent("off", action = "turn_off"), listOf(ac), state(false, 27).copy(fromSavedSettings = true))
        assertEquals(ActionDecision.EXECUTE, plan.decision)
    }
}
