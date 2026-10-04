package com.tatsu.homehub.domain

/** Validate a whole batch before allowing any adapter call. */
data class CompoundActionPlan(val decision: ActionDecision, val plans: List<ActionPlan>, val response: String? = null)

object CompoundActionPolicy {
    fun evaluate(plans: List<ActionPlan>): CompoundActionPlan {
        if (plans.isEmpty()) return CompoundActionPlan(ActionDecision.FALLBACK, emptyList(), "操作がないよ。まだ操作していないよ。")
        val invalid = plans.firstOrNull { it.device == null || it.decision == ActionDecision.FALLBACK || it.action == null && it.decision != ActionDecision.NOOP }
        if (invalid != null) return CompoundActionPlan(ActionDecision.FALLBACK, emptyList(), "${invalid.device?.name.orEmpty()}: ${invalid.response}\nまだどの操作も実行していないよ。")
        if (plans.groupBy { it.device?.deviceId }.values.any { group -> group.map { it.action }.distinct().size > 1 })
            return CompoundActionPlan(ActionDecision.FALLBACK, emptyList(), "同じ家電に異なる操作があるよ。最終的にどうしたいか教えてね。まだ操作していないよ。")
        val unique = plans.distinctBy { it.device?.deviceId }
        val executable = unique.filter { it.decision != ActionDecision.NOOP }
        val decision = when {
            executable.isEmpty() -> ActionDecision.NOOP
            unique.any { it.decision == ActionDecision.CONFIRM } || executable.any { it.action?.type == "power_on" } && executable.size > 1 -> ActionDecision.CONFIRM
            else -> ActionDecision.EXECUTE
        }
        return CompoundActionPlan(decision, unique)
    }
}
