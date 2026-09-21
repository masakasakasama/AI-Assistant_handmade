package com.tatsu.homehub.data

import com.tatsu.homehub.model.AcControlState
import com.tatsu.homehub.model.SwitchBotDevice
import kotlin.math.roundToInt

enum class ActionDecision { EXECUTE, CONFIRM, FALLBACK, NOOP, BLOCKED }
enum class ActionPolicy { SAFE_AUTO, CONFIRM_REQUIRED, BLOCKED }
data class DeviceAction(val type: String, val value: Int? = null)
data class DeviceActionPlan(
    val target: SwitchBotDevice?, val goal: String?, val currentState: AcControlState?,
    val decision: ActionDecision, val policy: ActionPolicy,
    val actions: List<DeviceAction> = emptyList(), val response: String, val reason: String
)

class DeviceActionResolver {
    fun resolve(result: AiDispatchResult, devices: List<SwitchBotDevice>, acStates: Map<String, AcControlState>): DeviceActionPlan {
        val target = resolveTarget(result, devices)
        if (target == null) return DeviceActionPlan(null, result.goal, null, ActionDecision.CONFIRM, ActionPolicy.CONFIRM_REQUIRED,
            response = "どの家電を操作しますか？", reason = "target_not_unique")

        if (result.action == "turn_on") return direct(target, result.goal, "turn_on", "電源を入れます")
        if (result.action == "turn_off") return direct(target, result.goal, "turn_off", "電源を切ります")
        if (result.action == "set_ac") {
            if (!target.isAirConditioner) return blocked(target, result.goal, "temperature_not_supported")
            val temp = result.temperatureC?.roundToInt()
                ?: return confirm(target, result.goal, "温度を指定してください", "temperature_missing")
            if (temp !in 16..30) return blocked(target, result.goal, "temperature_out_of_policy")
            return DeviceActionPlan(target, result.goal, acStates[target.deviceId], ActionDecision.EXECUTE, ActionPolicy.SAFE_AUTO,
                listOf(DeviceAction("set_temperature", temp)), "${temp}℃に設定します", "explicit_temperature")
        }
        return when (result.goal) {
            "cooler" -> resolveThermal(target, acStates[target.deviceId], true)
            "warmer" -> resolveThermal(target, acStates[target.deviceId], false)
            "on" -> direct(target, result.goal, "turn_on", "電源を入れます")
            "off" -> direct(target, result.goal, "turn_off", "電源を切ります")
            else -> DeviceActionPlan(target, result.goal, acStates[target.deviceId], ActionDecision.FALLBACK, ActionPolicy.CONFIRM_REQUIRED,
                response = "操作内容を確認します", reason = "goal_not_resolved")
        }
    }

    private fun resolveTarget(result: AiDispatchResult, devices: List<SwitchBotDevice>): SwitchBotDevice? {
        val named = result.target?.trim().orEmpty()
        if (named.isNotBlank()) {
            val n = normalize(named)
            val exact = devices.filter { normalize(it.name) == n }
            val matches = if (exact.isNotEmpty()) exact else devices.filter { normalize(it.name).contains(n) || n.contains(normalize(it.name)) }
            if (matches.size == 1) return matches.single()
        }
        if (result.goal in setOf("cooler", "warmer")) return devices.filter { it.isAirConditioner }.singleOrNull()
        return null
    }

    private fun resolveThermal(device: SwitchBotDevice, state: AcControlState?, cooler: Boolean): DeviceActionPlan {
        val goal = if (cooler) "cooler" else "warmer"
        if (!device.isAirConditioner) return blocked(device, goal, "not_air_conditioner")
        if (state == null) return confirm(device, goal, "エアコンの状態を確認できません。操作しますか？", "state_unknown")
        if (!state.power) return DeviceActionPlan(device, goal, state, ActionDecision.CONFIRM, ActionPolicy.CONFIRM_REQUIRED,
            listOf(DeviceAction("turn_on")), "エアコンをつけますか？", "air_conditioner_off")
        val next = (state.temperature + if (cooler) -1 else 1).coerceIn(20, 28)
        if (next == state.temperature) return DeviceActionPlan(device, goal, state, ActionDecision.CONFIRM, ActionPolicy.CONFIRM_REQUIRED,
            response = "すでに設定範囲の端です。さらに変更しますか？", reason = "comfort_boundary")
        return DeviceActionPlan(device, goal, state, ActionDecision.EXECUTE, ActionPolicy.SAFE_AUTO,
            listOf(DeviceAction("set_temperature", next)), "${state.temperature}℃から${next}℃に変更します", "relative_temperature_step")
    }

    private fun direct(device: SwitchBotDevice, goal: String?, action: String, response: String) =
        DeviceActionPlan(device, goal, null, ActionDecision.EXECUTE, ActionPolicy.SAFE_AUTO, listOf(DeviceAction(action)), response, "explicit_action")
    private fun confirm(device: SwitchBotDevice, goal: String?, response: String, reason: String) =
        DeviceActionPlan(device, goal, null, ActionDecision.CONFIRM, ActionPolicy.CONFIRM_REQUIRED, response = response, reason = reason)
    private fun blocked(device: SwitchBotDevice, goal: String?, reason: String) =
        DeviceActionPlan(device, goal, null, ActionDecision.BLOCKED, ActionPolicy.BLOCKED, response = "その操作は実行できません", reason = reason)
    private fun normalize(value: String) = value.lowercase().replace(" ", "").replace("　", "")
}
