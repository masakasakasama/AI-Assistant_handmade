package com.tatsu.homehub.voice

/** More conservative detection during playback; scores are model scores, not calibrated probabilities. */
class WakeDetectionGate {
    private var playback = false
    private var suppressPlayback = false
    private var blockedUntil = 0L
    private var streak = 0
    private var previousHighAt = 0L

    @Synchronized fun setPlayback(active: Boolean, containsWakePhrase: Boolean, nowMs: Long) {
        if (playback != active || suppressPlayback != containsWakePhrase) {
            streak = 0
            previousHighAt = 0L
            // Ignore playback startup and the remaining speaker/room tail after it ends.
            blockedUntil = nowMs + if (active) 500L else 350L
        }
        playback = active
        suppressPlayback = containsWakePhrase
    }

    @Synchronized fun reset() { streak = 0; previousHighAt = 0L }

    @Synchronized fun accept(score: Float, nowMs: Long): Boolean {
        if (!score.isFinite() || nowMs < blockedUntil || (playback && suppressPlayback)) {
            reset()
            return false
        }
        if (!playback) return score >= 0.55f
        if (score < 0.8f) { reset(); return false }
        streak = if (streak > 0 && nowMs - previousHighAt in 1..200) streak + 1 else 1
        previousHighAt = nowMs
        if (streak < 2) return false
        reset()
        return true
    }

    companion object {
        fun containsWakePhrase(text: String, phrase: String): Boolean {
            fun normalized(value: String) = value.lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }
            val key = normalized(phrase)
            return key.isNotEmpty() && normalized(text).contains(key)
        }
    }
}
