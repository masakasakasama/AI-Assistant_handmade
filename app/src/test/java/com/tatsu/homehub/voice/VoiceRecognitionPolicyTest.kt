package com.tatsu.homehub.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRecognitionPolicyTest {
    @Test
    fun onDeviceLanguageNotSupported_fallsBackOnce() {
        assertTrue(
            VoiceRecognitionPolicy.shouldFallbackToSystem(
                fromOnDevice = true,
                fallbackAlreadyAttempted = false,
                errorCode = VoiceRecognitionPolicy.ERROR_LANGUAGE_NOT_SUPPORTED
            )
        )
        assertFalse(
            VoiceRecognitionPolicy.shouldFallbackToSystem(
                fromOnDevice = true,
                fallbackAlreadyAttempted = true,
                errorCode = VoiceRecognitionPolicy.ERROR_LANGUAGE_NOT_SUPPORTED
            )
        )
    }

    @Test
    fun onDeviceLanguageUnavailable_fallsBackOnce() {
        assertTrue(
            VoiceRecognitionPolicy.shouldFallbackToSystem(
                fromOnDevice = true,
                fallbackAlreadyAttempted = false,
                errorCode = VoiceRecognitionPolicy.ERROR_LANGUAGE_UNAVAILABLE
            )
        )
    }

    @Test
    fun systemRecognizer_neverFallsBackAgain() {
        assertFalse(
            VoiceRecognitionPolicy.shouldFallbackToSystem(
                fromOnDevice = false,
                fallbackAlreadyAttempted = false,
                errorCode = VoiceRecognitionPolicy.ERROR_LANGUAGE_NOT_SUPPORTED
            )
        )
    }

    @Test
    fun unrelatedError_doesNotFallback() {
        assertFalse(
            VoiceRecognitionPolicy.shouldFallbackToSystem(
                fromOnDevice = true,
                fallbackAlreadyAttempted = false,
                errorCode = 8
            )
        )
    }

    @Test
    fun staleAttempt_isRejected() {
        val guard = VoiceAttemptGuard()
        guard.beginSession(10)
        val first = guard.nextAttempt(10)
        val second = guard.nextAttempt(10)

        assertFalse(guard.isCurrent(10, first))
        assertTrue(guard.isCurrent(10, second))
    }

    @Test
    fun staleSession_isRejected() {
        val guard = VoiceAttemptGuard()
        guard.beginSession(10)
        val attempt = guard.nextAttempt(10)
        guard.beginSession(11)
        guard.nextAttempt(11)

        assertFalse(guard.isCurrent(10, attempt))
    }

    @Test
    fun finalResult_isAcceptedOnlyOnce() {
        val guard = VoiceAttemptGuard()
        guard.beginSession(5)
        val attempt = guard.nextAttempt(5)

        assertTrue(guard.acceptFinal(5, attempt))
        assertFalse(guard.acceptFinal(5, attempt))
    }

    @Test
    fun cancelInvalidatesLateCallbacks() {
        val guard = VoiceAttemptGuard()
        guard.beginSession(7)
        val attempt = guard.nextAttempt(7)
        guard.invalidate(7)

        assertFalse(guard.isCurrent(7, attempt))
        assertFalse(guard.acceptFinal(7, attempt))
    }
}
