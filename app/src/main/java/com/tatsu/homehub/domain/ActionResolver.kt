package com.tatsu.homehub.domain

import com.tatsu.homehub.model.SwitchBotDevice
import com.tatsu.homehub.model.SwitchBotDeviceState
import java.util.UUID

enum class ActionDecision { EXECUTE, CONFIRM, FALLBACK, NOOP }
enum class ActionPolicy { SAFE_AUTO, CONFIRM_REQUIRED, BLOCKED }

data class DeviceIntent(
    val route: String,
    val target: String?,
    val targetType: String?,
    val action: String?,
    val goal: String?,
    val temperatureC: Double?,
    val confidence: Double,
    val language: String
)

data class ResolvedAction(
    val type: String,
    val temperatureC: Int? = null,
    val power: Boolean? = null
)

data class ActionPlan(
    val id: String = UUID.randomUUID().toString(),
    val intent: DeviceIntent,
    val device: SwitchBotDevice?,
    val currentState: SwitchBotDeviceState?,
    val action: ResolvedAction?,
    val decision: ActionDecision,
    val policy: ActionPolicy,
    val response: String,
    val reason: String,
    val stateFetchMs: Long? = null,
    val resolverMs: Long = 0,
    val policyMs: Long = 0,
    val timingError: String? = null
)

data class ActionSafetyPolicy(
    val minimumConfidence: Double = 0.72,
    val minTemperatureC: Int = 16,
    val maxTemperatureC: Int = 30,
    val comfortFloorC: Int = 23,
    val comfortCeilingC: Int = 28,
    val relativeTemperatureStepC: Int = 1,
    val confirmTemperatureDeltaC: Int = 3
)

class ActionResolver(
    private val safety: ActionSafetyPolicy = ActionSafetyPolicy(),
    private val monotonicMs: () -> Long = { android.os.SystemClock.elapsedRealtime() }
) {
    fun resolve(
        intent: DeviceIntent,
        devices: List<SwitchBotDevice>,
        currentState: SwitchBotDeviceState?,
        stateFetchMs: Long? = null
    ): ActionPlan {
        val resolverStarted = monotonicMs()
        val policyStarted = resolverStarted
        val candidates = resolveTargets(intent, devices)
        if (candidates.size != 1) {
            return plan(
                intent, candidates.singleOrNull(), currentState, null,
                if (candidates.isEmpty()) ActionDecision.FALLBACK else ActionDecision.CONFIRM,
                ActionPolicy.CONFIRM_REQUIRED,
                if (candidates.isEmpty()) localized(intent.language, "対象の家電が見つかりませんでした", "I couldn't find that device.", "Ich konnte das Gerät nicht finden.")
                else localized(intent.language, "対象の家電が複数あります。部屋か名前を指定してください", "More than one device matches. Please specify a room or name.", "Mehrere Geräte passen. Bitte nenne Raum oder Namen."),
                "target candidates=${candidates.size}", stateFetchMs, resolverStarted, policyStarted
            )
        }
        val device = candidates.single()
        if (intent.confidence < safety.minimumConfidence) {
            return plan(intent, device, currentState, null, ActionDecision.FALLBACK, ActionPolicy.CONFIRM_REQUIRED,
                localized(intent.language, "操作内容を確認したいです", "I need to clarify the requested action.", "Ich muss die gewünschte Aktion klären."),
                "confidence below policy threshold", stateFetchMs, resolverStarted, policyStarted)
        }

        val action = when (intent.action) {
            "turn_on" -> ResolvedAction("power_on", power = true)
            "turn_off" -> ResolvedAction("power_off", power = false)
            "set_ac" -> {
                val temperature = intent.temperatureC?.toInt()
                if (!device.isAirConditioner || temperature == null || temperature !in safety.minTemperatureC..safety.maxTemperatureC) {
                    return plan(intent, device, currentState, null, ActionDecision.FALLBACK, ActionPolicy.BLOCKED,
                        localized(intent.language, "エアコンの対象または温度を確認できません", "I couldn't validate the air conditioner or temperature.", "Klimaanlage oder Temperatur konnten nicht bestätigt werden."),
                        "unsupported capability or out-of-range temperature", stateFetchMs, resolverStarted, policyStarted)
                }
                ResolvedAction("set_temperature", temperature, power = true)
            }
            null, "none" -> null
            else -> return plan(intent, device, currentState, null, ActionDecision.FALLBACK, ActionPolicy.BLOCKED,
                localized(intent.language, "その操作にはまだ対応していません", "That action is not supported yet.", "Diese Aktion wird noch nicht unterstützt."),
                    "unsupported explicit action", stateFetchMs, resolverStarted, policyStarted)
        }

        if (action != null) {
            if (action.type == "set_temperature" && currentState?.temperature != null &&
                kotlin.math.abs(action.temperatureC!! - currentState.temperature) > safety.confirmTemperatureDeltaC
            ) {
                return plan(intent, device, currentState, action, ActionDecision.CONFIRM, ActionPolicy.CONFIRM_REQUIRED,
                    localized(intent.language, "${currentState.temperature}℃から${action.temperatureC}℃に変更しますか？", "Change from ${currentState.temperature}°C to ${action.temperatureC}°C?", "Von ${currentState.temperature}°C auf ${action.temperatureC}°C ändern?"),
                    "temperature delta exceeds auto policy", stateFetchMs, resolverStarted, policyStarted)
            }
            if (action.type == "power_on" && currentState?.power == true || action.type == "power_off" && currentState?.power == false) {
                return plan(intent, device, currentState, action, ActionDecision.NOOP, ActionPolicy.SAFE_AUTO,
                    localized(intent.language, "${device.name}はすでにその状態です", "${device.name} is already in that state.", "${device.name} ist bereits in diesem Zustand."),
                    "requested power state already matches", stateFetchMs, resolverStarted, policyStarted)
            }
            return plan(intent, device, currentState, action, ActionDecision.EXECUTE, ActionPolicy.SAFE_AUTO,
                actionResponse(intent.language, device.name, action), "explicit action validated", stateFetchMs, resolverStarted, policyStarted)
        }

        val goal = intent.goal
        if (goal !in setOf("cooler", "warmer", "increase", "decrease", "brighter", "darker")) {
            return plan(intent, device, currentState, null, ActionDecision.FALLBACK, ActionPolicy.BLOCKED,
                localized(intent.language, "操作内容を決められませんでした", "I couldn't determine the requested action.", "Ich konnte die gewünschte Aktion nicht bestimmen."),
                "no supported goal", stateFetchMs, resolverStarted, policyStarted)
        }
        if (device.isAirConditioner && goal in setOf("cooler", "warmer", "increase", "decrease")) {
            if (currentState == null || currentState.power == null || currentState.temperature == null ||
                currentState.mode == null || currentState.fanSpeed == null
            ) {
                return plan(intent, device, currentState, null, ActionDecision.FALLBACK, ActionPolicy.CONFIRM_REQUIRED,
                    localized(intent.language, "エアコンの現在状態を取得できませんでした。状態を推測して操作しません", "I couldn't read the air conditioner's current state, so I won't guess.", "Ich konnte den aktuellen Zustand der Klimaanlage nicht lesen und rate nicht."),
                    "required device state unavailable", stateFetchMs, resolverStarted, policyStarted)
            }
            if (!currentState.power) {
                val proposed = ResolvedAction("power_on", power = true)
                return plan(intent, device, currentState, proposed, ActionDecision.CONFIRM, ActionPolicy.CONFIRM_REQUIRED,
                    localized(intent.language, "${device.name}をつけますか？", "Turn on ${device.name}?", "${device.name} einschalten?"),
                    "device is off for comfort goal", stateFetchMs, resolverStarted, policyStarted)
            }
            val delta = when (goal) {
                "cooler", "decrease" -> -safety.relativeTemperatureStepC
                else -> safety.relativeTemperatureStepC
            }
            val next = currentState.temperature + delta
            if (next !in safety.minTemperatureC..safety.maxTemperatureC) {
                return plan(intent, device, currentState, null, ActionDecision.FALLBACK, ActionPolicy.BLOCKED,
                    localized(intent.language, "安全な設定範囲を超えるため変更しません", "I won't go outside the safe setting range.", "Ich ändere nichts außerhalb des sicheren Einstellbereichs."),
                    "temperature outside policy bounds", stateFetchMs, resolverStarted, policyStarted)
            }
            if (goal == "cooler" && currentState.temperature <= safety.comfortFloorC ||
                goal == "warmer" && currentState.temperature >= safety.comfortCeilingC
            ) {
                return plan(intent, device, currentState, ResolvedAction("set_temperature", next, power = true), ActionDecision.CONFIRM, ActionPolicy.CONFIRM_REQUIRED,
                    localized(intent.language, "すでに${currentState.temperature}℃です。さらに変更しますか？", "It is already ${currentState.temperature}°C. Change it further?", "Es sind bereits ${currentState.temperature}°C. Noch weiter ändern?"),
                    "comfort bound reached", stateFetchMs, resolverStarted, policyStarted)
            }
            val resolved = ResolvedAction("set_temperature", next, power = true)
            return plan(intent, device, currentState, resolved, ActionDecision.EXECUTE, ActionPolicy.SAFE_AUTO,
                localized(intent.language, "${currentState.temperature}℃から${next}℃に下げます", "I will change ${currentState.temperature}°C to ${next}°C.", "Ich ändere von ${currentState.temperature}°C auf ${next}°C."),
                "current state read; one-step relative temperature change", stateFetchMs, resolverStarted, policyStarted)
        }
        return plan(intent, device, currentState, null, ActionDecision.FALLBACK, ActionPolicy.BLOCKED,
            localized(intent.language, "その家電ではこの操作に対応していません", "That device does not support this action.", "Dieses Gerät unterstützt diese Aktion nicht."),
            "goal does not match a supported device capability", stateFetchMs, resolverStarted, policyStarted)
    }

    private fun resolveTargets(intent: DeviceIntent, devices: List<SwitchBotDevice>): List<SwitchBotDevice> =
        DeviceTargetResolver.resolve(intent.target, intent.targetType, devices)

    private fun plan(
        intent: DeviceIntent, device: SwitchBotDevice?, state: SwitchBotDeviceState?, action: ResolvedAction?,
        decision: ActionDecision, policy: ActionPolicy, response: String, reason: String,
        stateFetchMs: Long?, started: Long, policyStarted: Long
    ): ActionPlan {
        val finished = monotonicMs()
        val elapsed = finished - started
        val policyElapsed = finished - policyStarted
        return ActionPlan(intent = intent, device = device, currentState = state, action = action,
            decision = decision, policy = policy, response = response, reason = reason,
            stateFetchMs = stateFetchMs, resolverMs = elapsed, policyMs = policyElapsed,
            timingError = if (elapsed < 0 || policyElapsed < 0 || stateFetchMs?.let { it < 0 } == true) "monotonic interval was negative" else null)
    }

    private fun actionResponse(language: String, target: String, action: ResolvedAction): String = when (action.type) {
        "power_on" -> localized(language, "${target}をつけます", "Turning on ${target}.", "${target} wird eingeschaltet.")
        "power_off" -> localized(language, "${target}を消します", "Turning off ${target}.", "${target} wird ausgeschaltet.")
        else -> localized(language, "${target}を${action.temperatureC}℃に設定します", "Setting ${target} to ${action.temperatureC}°C.", "${target} wird auf ${action.temperatureC}°C eingestellt.")
    }

    private fun normalize(value: String): String = value.lowercase().replace(Regex("[\\s　]"), "")
    private fun localized(language: String, ja: String, en: String, de: String) = when (language) { "en" -> en; "de" -> de; else -> ja }
}
