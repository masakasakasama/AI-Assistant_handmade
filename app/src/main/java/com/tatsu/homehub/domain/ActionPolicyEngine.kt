package com.tatsu.homehub.domain

/** Final deterministic gate between a resolved plan and any Device Adapter. */
class ActionPolicyEngine(
    private val minTemperatureC: Int = 16,
    private val maxTemperatureC: Int = 30,
    private val monotonicMs: () -> Long = { android.os.SystemClock.elapsedRealtime() }
) {
    fun evaluate(plan: ActionPlan): ActionPlan {
        val started = monotonicMs()
        val action = plan.action
        val invalid = when {
            plan.decision != ActionDecision.EXECUTE -> null
            plan.device == null -> "device is missing"
            action == null -> "resolved action is missing"
            action.type == "set_temperature" && !plan.device.isAirConditioner -> "temperature is unsupported by target"
            action.type == "set_temperature" && action.temperatureC !in minTemperatureC..maxTemperatureC -> "temperature is outside policy range"
            action.type !in setOf("set_temperature", "power_on", "power_off") -> "action is not allowlisted"
            else -> null
        }
        val elapsed = monotonicMs() - started
        return if (invalid == null) plan.copy(policyMs = elapsed,
            timingError = plan.timingError ?: if (elapsed < 0) "policy interval was negative" else null)
        else plan.copy(
            decision = ActionDecision.FALLBACK,
            policy = ActionPolicy.BLOCKED,
            response = when (plan.intent.language) {
                "en" -> "I blocked that action because it failed the safety policy."
                "de" -> "Ich habe die Aktion durch die Sicherheitsrichtlinie blockiert."
                else -> "安全ポリシーに適合しないため、操作を止めました"
            },
            reason = "policy blocked: $invalid",
            policyMs = elapsed,
            timingError = plan.timingError ?: if (elapsed < 0) "policy interval was negative" else null
        )
    }
}
