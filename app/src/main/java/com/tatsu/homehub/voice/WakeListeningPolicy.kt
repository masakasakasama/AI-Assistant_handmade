package com.tatsu.homehub.voice

object WakeListeningPolicy {
    fun shouldListen(requested: Boolean, phase: VoicePhase, playbackActive: Boolean, interruptionEnabled: Boolean): Boolean {
        if (!requested) return false
        if (phase == VoicePhase.PREPARING || phase == VoicePhase.LISTENING || phase == VoicePhase.THINKING) return false
        if (phase == VoicePhase.SPEAKING || phase == VoicePhase.ANSWER_READY) return playbackActive && interruptionEnabled
        return true
    }
}
