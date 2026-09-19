package com.tatsu.homehub.voice

enum class VoicePhase {
    IDLE,
    PREPARING,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

data class VoiceSessionState(
    val phase: VoicePhase = VoicePhase.IDLE,
    val generationId: Long = 0,
    val partialText: String = "",
    val finalText: String = "",
    val responseText: String = "",
    val route: String? = null,
    val latencyMs: Long? = null,
    val error: String? = null,
    val status: String = "",
    val diagnostic: String = ""
)
