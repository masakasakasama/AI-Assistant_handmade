package com.tatsu.homehub.domain

import com.tatsu.homehub.data.SwitchBotClient
import com.tatsu.homehub.model.AcControlState

data class DeviceExecutionResult(
    val adapter: String,
    val command: String,
    val accepted: Boolean,
    val elapsedMs: Long,
    val error: String? = null,
    val timingError: String? = null
)

interface DeviceActionAdapter {
    suspend fun execute(plan: ActionPlan): DeviceExecutionResult
}

class SwitchBotActionAdapter(
    private val client: SwitchBotClient,
    private val getKnownAcState: (String) -> AcControlState,
    private val saveKnownAcState: (String, AcControlState) -> Unit
) : DeviceActionAdapter {
    override suspend fun execute(plan: ActionPlan): DeviceExecutionResult {
        val device = requireNotNull(plan.device)
        val action = requireNotNull(plan.action)
        require(plan.decision == ActionDecision.EXECUTE) { "Only executable plans can reach a Device Adapter" }
        val started = android.os.SystemClock.elapsedRealtime()
        if (device.demoOnly) {
            return DeviceExecutionResult(
                adapter = "switchbot",
                command = action.type,
                accepted = false,
                elapsedMs = android.os.SystemClock.elapsedRealtime() - started,
                error = "demo fixture devices are comparison-only"
            )
        }
        val operation = when (action.type) {
            "power_on", "power_off" -> if (device.isAirConditioner) {
                val previous = getKnownAcState(device.deviceId)
                val next = previous.copy(power = action.type == "power_on")
                client.setAirConditioner(device.deviceId, next.temperature, next.mode, next.fanSpeed, next.power)
                    .onSuccess { saveKnownAcState(device.deviceId, next) }
            } else if (action.type == "power_on") {
                client.turnOn(device.deviceId)
            } else {
                client.turnOff(device.deviceId)
            }
            "set_temperature" -> {
                require(device.isAirConditioner) { "This device has no temperature capability" }
                val previous = getKnownAcState(device.deviceId)
                val current = plan.currentState
                val next = AcControlState(
                    temperature = action.temperatureC ?: error("Missing temperature"),
                    mode = current?.mode ?: previous.mode,
                    fanSpeed = current?.fanSpeed ?: previous.fanSpeed,
                    power = action.power ?: true
                )
                client.setAirConditioner(device.deviceId, next.temperature, next.mode, next.fanSpeed, next.power)
                    .onSuccess { saveKnownAcState(device.deviceId, next) }
            }
            else -> error("Unsupported SwitchBot action ${action.type}")
        }
        val elapsed = android.os.SystemClock.elapsedRealtime() - started
        val timingError = if (elapsed < 0) "device adapter interval was negative" else null
        return operation.fold(
            onSuccess = { DeviceExecutionResult("switchbot", action.type, true, elapsed, timingError = timingError) },
            onFailure = { error -> DeviceExecutionResult("switchbot", action.type, false,
                elapsed, error.message ?: "device command failed", timingError) }
        )
    }
}
