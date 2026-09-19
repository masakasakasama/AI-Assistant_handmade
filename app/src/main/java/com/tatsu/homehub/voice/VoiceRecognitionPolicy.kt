package com.tatsu.homehub.voice

internal object VoiceRecognitionPolicy {
    const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
    const val ERROR_LANGUAGE_UNAVAILABLE = 13

    fun shouldFallbackToSystem(
        fromOnDevice: Boolean,
        fallbackAlreadyAttempted: Boolean,
        errorCode: Int
    ): Boolean =
        fromOnDevice &&
            !fallbackAlreadyAttempted &&
            (errorCode == ERROR_LANGUAGE_NOT_SUPPORTED ||
                errorCode == ERROR_LANGUAGE_UNAVAILABLE)
}

internal class VoiceAttemptGuard {
    private var activeSessionId: Long = 0
    private var activeAttemptId: Long = 0
    private var finalAccepted = false

    fun beginSession(sessionId: Long) {
        activeSessionId = sessionId
        activeAttemptId = 0
        finalAccepted = false
    }

    fun nextAttempt(sessionId: Long): Long {
        if (sessionId != activeSessionId) return -1
        activeAttemptId += 1
        finalAccepted = false
        return activeAttemptId
    }

    fun isCurrent(sessionId: Long, attemptId: Long): Boolean =
        sessionId == activeSessionId && attemptId == activeAttemptId

    fun acceptFinal(sessionId: Long, attemptId: Long): Boolean {
        if (!isCurrent(sessionId, attemptId) || finalAccepted) return false
        finalAccepted = true
        return true
    }

    fun invalidate(sessionId: Long) {
        if (sessionId != activeSessionId) return
        activeAttemptId += 1
        finalAccepted = true
    }
}
