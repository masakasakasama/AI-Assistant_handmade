package com.tatsu.homehub.voice

enum class VoicePhase {
    IDLE,
    PREPARING,
    LISTENING,
    THINKING,
    ANSWER_READY,
    SPEAKING,
    ERROR
}

enum class VoiceMode {
    LUNA,
    JEV,
    ANSWER_COMPARE,
    ROUTER_COMPARE
}

data class AnswerComparisonSide(
    val result: com.tatsu.homehub.data.AiDispatchResult? = null,
    val error: String? = null,
    val clientLatencyMs: Long? = null,
    val pending: Boolean = true
)

data class AnswerComparisonState(
    val query: String,
    val luna: AnswerComparisonSide = AnswerComparisonSide(),
    val jev: AnswerComparisonSide = AnswerComparisonSide(),
    val startedAtElapsedMs: Long
)

data class VoiceSessionState(
    val phase: VoicePhase = VoicePhase.IDLE,
    val mode: VoiceMode = VoiceMode.LUNA,
    val generationId: Long = 0,
    val partialText: String = "",
    val finalText: String = "",
    val responseText: String = "",
    val route: String? = null,
    val latencyMs: Long? = null,
    val error: String? = null,
    val status: String = "",
    val diagnostic: String = "",
    val detectedLanguageTag: String? = null
)
