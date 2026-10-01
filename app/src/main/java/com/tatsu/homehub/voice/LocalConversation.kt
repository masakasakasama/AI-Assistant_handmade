package com.tatsu.homehub.voice

import java.util.Locale

/** Only standalone greetings; compound requests always use the normal intent gate. */
object LocalConversation {
    data class Reply(val language: String, val text: String)

    private val replies = mapOf(
        "おはよう" to Reply("ja", "おはよう。今日もよろしくね。"),
        "おはようございます" to Reply("ja", "おはようございます。今日もよろしくお願いします。"),
        "こんにちは" to Reply("ja", "こんにちは。何か手伝えることはある？"),
        "こんばんは" to Reply("ja", "こんばんは。今日もおつかれさま。"),
        "ありがとう" to Reply("ja", "どういたしまして。"),
        "おやすみ" to Reply("ja", "おやすみ。ゆっくり休んでね。"),
        "hello" to Reply("en", "Hello. How can I help?"),
        "hi" to Reply("en", "Hi. How can I help?"),
        "good morning" to Reply("en", "Good morning. How can I help today?"),
        "good night" to Reply("en", "Good night. Sleep well."),
        "thank you" to Reply("en", "You're welcome."),
        "thanks" to Reply("en", "You're welcome."),
        "hallo" to Reply("de", "Hallo. Wie kann ich helfen?"),
        "guten morgen" to Reply("de", "Guten Morgen. Wie kann ich heute helfen?"),
        "gute nacht" to Reply("de", "Gute Nacht. Schlaf gut."),
        "danke" to Reply("de", "Gern geschehen.")
    )

    fun reply(text: String): Reply? {
        val normalized = text.trim().lowercase(Locale.ROOT)
            .replace(Regex("[.!?。！？]+$"), "").trim()
        return replies[normalized]
    }
}
