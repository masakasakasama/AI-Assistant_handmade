package com.tatsu.homehub.voice

enum class VoicePhase {
    IDLE,
    PREPARING,
    LISTENING,
    SWITCHING,
    THINKING,
    SPEAKING,
    ERROR
}

enum class VoiceRecognizerMode {
    ON_DEVICE,
    SYSTEM
}

data class VoiceSessionState(
    val phase: VoicePhase = VoicePhase.IDLE,
    val generationId: Long = 0,
    val sessionId: Long = 0,
    val attemptId: Long = 0,
    val languageTag: String = "ja-JP",
    val recognizerMode: VoiceRecognizerMode? = null,
    val statusMessage: String? = null,
    val partialText: String = "",
    val finalText: String = "",
    val responseText: String = "",
    val route: String? = null,
    val latencyMs: Long? = null,
    val error: String? = null,
    val diagnostics: String = ""
)
