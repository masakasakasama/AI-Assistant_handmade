package com.tatsu.homehub.domain

import com.tatsu.homehub.model.SwitchBotDevice
import org.junit.Assert.*
import org.junit.Test

class CompoundActionPolicyTest {
    private val ac = SwitchBotDevice("ac", "寝室のエアコン", "Air Conditioner", true)
    private val lamp = SwitchBotDevice("lamp", "デスク照明", "Color Bulb", false)
    private val resolver = ActionResolver(monotonicMs = { 1L })
    private fun plan(device: SwitchBotDevice, action: String) = resolver.resolve(DeviceIntent("device_action", device.deviceId, null, action, null, null, 1.0, "ja"), listOf(device), null)
    @Test fun twoDifferentOffCommandsRemainTwoExecutablePlans() {
        val batch = CompoundActionPolicy.evaluate(listOf(plan(ac, "turn_off"), plan(lamp, "turn_off")))
        assertEquals(ActionDecision.EXECUTE, batch.decision)
        assertEquals(listOf("ac", "lamp"), batch.plans.map { it.device?.deviceId })
    }
    @Test fun repeatedTargetOnlyRunsOnce() {
        val off = plan(ac, "turn_off")
        assertEquals(1, CompoundActionPolicy.evaluate(listOf(off, off)).plans.size)
    }
    @Test fun aBadStepCannotPartiallyExecuteTheOtherSteps() {
        val bad = plan(lamp, "factory_reset")
        val batch = CompoundActionPolicy.evaluate(listOf(plan(ac, "turn_off"), bad))
        assertEquals(ActionDecision.FALLBACK, batch.decision)
        assertTrue(batch.plans.isEmpty())
    }
    @Test fun conflictingCommandsToTheSameDeviceAreRejectedBeforeExecution() {
        val batch = CompoundActionPolicy.evaluate(listOf(plan(ac, "turn_off"), plan(ac, "turn_on")))
        assertEquals(ActionDecision.FALLBACK, batch.decision)
        assertTrue(batch.plans.isEmpty())
    }
    @Test fun mixedOffAndOnAsksOneConfirmationForTheWholeBatch() {
        assertEquals(ActionDecision.CONFIRM, CompoundActionPolicy.evaluate(listOf(plan(ac, "turn_off"), plan(lamp, "turn_on"))).decision)
    }
    @Test fun aStepRequiringConfirmationStopsTheWholeBatchForConfirmation() {
        val uncertain = plan(lamp, "turn_off").copy(decision = ActionDecision.CONFIRM)
        assertEquals(ActionDecision.CONFIRM, CompoundActionPolicy.evaluate(listOf(plan(ac, "turn_off"), uncertain)).decision)
    }
}
