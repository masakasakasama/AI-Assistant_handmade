package com.tatsu.homehub.voice

import org.junit.Assert.*
import org.junit.Test

class RecognitionLanguagePolicyTest {
    @Test fun automaticModeRequiresAllThreeLanguagesInsteadOfOnlyJapanese() {
        assertFalse(RecognitionLanguagePolicy.hasRequiredModels("auto", listOf("ja-JP")))
        assertFalse(RecognitionLanguagePolicy.hasRequiredModels("auto", listOf("ja-JP", "en-US")))
        assertTrue(RecognitionLanguagePolicy.hasRequiredModels("auto", listOf("ja-JP", "en-US", "de-DE")))
    }

    @Test fun fixedGermanRequiresGermanButNotOtherLanguages() {
        assertFalse(RecognitionLanguagePolicy.hasRequiredModels("de-DE", listOf("ja-JP", "en-US")))
        assertTrue(RecognitionLanguagePolicy.hasRequiredModels("de-DE", listOf("de-DE")))
        assertTrue(RecognitionLanguagePolicy.hasRequiredModels("de-DE", listOf("de-AT")))
        assertTrue(RecognitionLanguagePolicy.hasRequiredModels("ja-JP", listOf("ja-JP")))
    }
}
