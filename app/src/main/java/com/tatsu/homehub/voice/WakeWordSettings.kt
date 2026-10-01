package com.tatsu.homehub.voice

enum class WakeWordChoice(val label: String) {
    HEY_JARVIS("Hey Jarvis"),
    ALEXA("Alexa"),
    HEY_MYCROFT("Hey Mycroft"),
    CUSTOM("独自モデル");

    companion object {
        fun fromStored(value: String?) = entries.firstOrNull { it.name == value } ?: HEY_JARVIS
    }
}

data class WakeWordSettings(
    val choice: WakeWordChoice = WakeWordChoice.HEY_JARVIS,
    val customPhrase: String = "独自ウェイクワード"
) {
    val phrase: String get() = if (choice == WakeWordChoice.CUSTOM) customPhrase else choice.label
}
