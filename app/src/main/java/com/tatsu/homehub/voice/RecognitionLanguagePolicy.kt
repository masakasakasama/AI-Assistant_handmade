package com.tatsu.homehub.voice

import java.util.Locale

object RecognitionLanguagePolicy {
    val supportedTags = listOf("ja-JP", "en-US", "de-DE")

    fun requiredLanguages(languageTag: String): Set<String> =
        (if (languageTag == "auto") supportedTags else listOf(languageTag))
            .map { Locale.forLanguageTag(it).language.lowercase(Locale.ROOT) }.toSet()

    fun hasRequiredModels(languageTag: String, installedTags: List<String>): Boolean {
        val installed = installedTags.map { Locale.forLanguageTag(it).language.lowercase(Locale.ROOT) }.toSet()
        return requiredLanguages(languageTag).all { it in installed }
    }
}
