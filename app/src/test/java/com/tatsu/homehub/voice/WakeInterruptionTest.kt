package com.tatsu.homehub.voice

import org.junit.Assert.*
import org.junit.Test

class WakeInterruptionTest {
    @Test fun playbackNeedsTwoAdjacentHighScoresAndRejectsStartup() {
        val gate = WakeDetectionGate()
        gate.setPlayback(true, false, 1000)
        assertFalse(gate.accept(0.99f, 1400))
        assertFalse(gate.accept(0.79f, 1500))
        assertFalse(gate.accept(0.9f, 1580))
        assertTrue(gate.accept(0.9f, 1660))
    }

    @Test fun lowScoreOrLongGapBreaksConsecutiveDetection() {
        val gate = WakeDetectionGate()
        gate.setPlayback(true, false, 0)
        assertFalse(gate.accept(0.9f, 500))
        assertFalse(gate.accept(0.1f, 580))
        assertFalse(gate.accept(0.9f, 660))
        assertFalse(gate.accept(0.9f, 1000))
        assertTrue(gate.accept(0.9f, 1080))
    }

    @Test fun ownWakePhraseCannotInterruptAndPlaybackTailIsIgnored() {
        val gate = WakeDetectionGate()
        gate.setPlayback(true, true, 0)
        assertFalse(gate.accept(0.99f, 1000))
        assertFalse(gate.accept(0.99f, 1080))
        gate.setPlayback(false, false, 1200)
        assertFalse(gate.accept(0.99f, 1500))
        assertTrue(gate.accept(0.6f, 1600))
        assertFalse(gate.accept(Float.NaN, 1700))
    }

    @Test fun wakePhraseMentionsIgnoreSpacingCaseAndPunctuation() {
        assertTrue(WakeDetectionGate.containsWakePhrase("Say HEY, Jarvis!", "Hey Jarvis"))
        assertTrue(WakeDetectionGate.containsWakePhrase("Alexa is available.", "Alexa"))
        assertFalse(WakeDetectionGate.containsWakePhrase("Good morning!", "Hey Jarvis"))
    }

    @Test fun playbackCaptureNeverCompetesWithSpeechRecognitionOrProcessing() {
        for (phase in listOf(VoicePhase.PREPARING, VoicePhase.LISTENING, VoicePhase.THINKING)) {
            assertFalse(WakeListeningPolicy.shouldListen(true, phase, true, true))
        }
        assertTrue(WakeListeningPolicy.shouldListen(true, VoicePhase.SPEAKING, true, true))
        assertTrue(WakeListeningPolicy.shouldListen(true, VoicePhase.ANSWER_READY, true, true))
        assertFalse(WakeListeningPolicy.shouldListen(true, VoicePhase.SPEAKING, true, false))
        assertFalse(WakeListeningPolicy.shouldListen(false, VoicePhase.SPEAKING, true, true))
        assertTrue(WakeListeningPolicy.shouldListen(true, VoicePhase.IDLE, false, true))
    }
}
