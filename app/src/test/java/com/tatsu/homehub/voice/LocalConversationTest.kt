package com.tatsu.homehub.voice

import org.junit.Assert.*
import org.junit.Test

class LocalConversationTest {
    @Test fun standaloneGreetingsWorkInAllThreeLanguages() {
        assertEquals("ja", LocalConversation.reply("  こんにちは。 ")?.language)
        assertEquals("en", LocalConversation.reply("GOOD MORNING!")?.language)
        assertEquals("de", LocalConversation.reply("Guten Morgen.")?.language)
        assertTrue(LocalConversation.reply("ありがとう")!!.text.isNotBlank())
    }

    @Test fun commandsAndCompoundRequestsNeverUseTheGreetingShortcut() {
        listOf(
            "こんにちは、照明を消して", "おやすみ、7時に起こして", "ありがとう、でも消さないで",
            "Hello, turn off the bedroom light", "Danke, stelle einen Wecker auf 7 Uhr",
            "こんにちは？電気を消して", "おはようございます。今日の天気は？", "hello\nturn off the light"
        ).forEach { assertNull(it, LocalConversation.reply(it)) }
    }

    @Test fun confirmationAndAmbiguityGoThroughTheExistingFlow() {
        listOf("はい", "お願い", "やめて", "yes", "ja", "それを消して", "").forEach {
            assertNull(it, LocalConversation.reply(it))
        }
    }

    @Test fun playfulStandaloneGreetingsWorkWithoutSwallowingCommands() {
        assertEquals("ja", LocalConversation.reply("おはよ〜！")?.language)
        assertNotEquals(LocalConversation.reply("おはよう")?.text, LocalConversation.reply("おはよ〜")?.text)
        assertNotNull(LocalConversation.reply("ありがとー"))
        listOf("おはよ〜、電気消して", "おやすみ〜、7時に起こして", "ありがと〜、でも消さないで").forEach {
            assertNull(it, LocalConversation.reply(it))
        }
    }
}
