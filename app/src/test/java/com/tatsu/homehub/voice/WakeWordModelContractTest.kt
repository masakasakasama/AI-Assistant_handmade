package com.tatsu.homehub.voice

import org.junit.Assert.*
import org.junit.Test

class WakeWordModelContractTest {
    @Test fun acceptsOnlyCompatibleEmbeddingWindows() {
        assertTrue(WakeWordModelContract.acceptsShape(longArrayOf(1, 16, 96)))
        assertTrue(WakeWordModelContract.acceptsShape(longArrayOf(-1, -1, 96)))
        assertFalse(WakeWordModelContract.acceptsShape(longArrayOf(1, 32, 96)))
        assertFalse(WakeWordModelContract.acceptsShape(longArrayOf(1, 16, 80)))
        assertFalse(WakeWordModelContract.acceptsShape(longArrayOf(16, 96)))
        assertFalse(WakeWordModelContract.acceptsShape(longArrayOf(2, 16, 96)))
    }

    @Test fun rejectsOversizeAndEmptyStreams() {
        assertThrows(IllegalArgumentException::class.java) {
            WakeWordModelContract.readModel(ByteArray(5).inputStream(), limit = 4)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WakeWordModelContract.readModel(ByteArray(0).inputStream())
        }
        val bytes = byteArrayOf(1, 2, 3, 4)
        assertArrayEquals(bytes, WakeWordModelContract.readModel(bytes.inputStream(), limit = 4))
    }

    @Test fun unknownSavedChoiceFallsBackToWorkingDefault() {
        assertEquals(WakeWordChoice.HEY_JARVIS, WakeWordChoice.fromStored("removed-model"))
        assertEquals(WakeWordChoice.HEY_JARVIS, WakeWordChoice.fromStored(null))
        assertEquals("Hey Mycroft", WakeWordSettings(WakeWordChoice.HEY_MYCROFT).phrase)
        assertEquals("Hey Tatsu", WakeWordSettings(WakeWordChoice.CUSTOM, "Hey Tatsu").phrase)
    }
}
