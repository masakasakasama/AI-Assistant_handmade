package com.tatsu.homehub.voice

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmUtteranceTest {
    @Test fun wavHeaderPreservesSamplesAndDeclaresMono16kPcm() {
        val pcm = byteArrayOf(1, 2, 3, 4)
        val wav = PcmWav.encode(pcm)
        val header = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(48, wav.size)
        assertEquals(16000, header.getInt(24))
        assertEquals(1.toShort(), header.getShort(22))
        assertEquals(16.toShort(), header.getShort(34))
        assertEquals(4, header.getInt(40))
        assertArrayEquals(pcm, wav.copyOfRange(44, wav.size))
        assertThrows(IllegalArgumentException::class.java) { PcmWav.encode(byteArrayOf(1)) }
        assertThrows(IllegalArgumentException::class.java) { PcmWav.encode(ByteArray(960002)) }
    }

    @Test fun utteranceWaitsForSpeechAndDoesNotCutBriefPauses() {
        val boundary = UtteranceBoundary()
        val silence = ShortArray(1280)
        val speech = ShortArray(1280) { if (it % 2 == 0) 3000 else -3000 }
        repeat(10) { assertFalse(boundary.accept(silence, silence.size)) }
        repeat(8) { assertFalse(boundary.accept(speech, speech.size)) }
        repeat(5) { assertFalse(boundary.accept(silence, silence.size)) }
        repeat(8) { assertFalse(boundary.accept(speech, speech.size)) }
        repeat(14) { assertFalse(boundary.accept(silence, silence.size)) }
        assertTrue(boundary.accept(silence, silence.size))
        assertTrue(boundary.heardSpeech)
    }

    @Test fun silenceTimesOutWithoutFabricatingSpeech() {
        val boundary = UtteranceBoundary()
        val silence = ShortArray(1280)
        repeat(124) { assertFalse(boundary.accept(silence, silence.size)) }
        assertTrue(boundary.accept(silence, silence.size))
        assertFalse(boundary.heardSpeech)
    }
}
