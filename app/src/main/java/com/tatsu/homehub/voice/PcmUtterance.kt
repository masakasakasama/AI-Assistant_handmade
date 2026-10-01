package com.tatsu.homehub.voice

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Adaptive silence detection; retain every captured sample, including before speech starts. */
class UtteranceBoundary {
    private var frames = 0
    private var speechFrames = 0
    private var silenceFrames = 0
    private var noiseRms = 120.0
    val heardSpeech: Boolean get() = speechFrames >= 3

    fun accept(samples: ShortArray, count: Int): Boolean {
        frames++
        val rms = sqrt((0 until count).sumOf { samples[it].toDouble() * samples[it] } / count)
        val speech = rms >= maxOf(220.0, noiseRms * 2.5)
        if (speech) { speechFrames++; silenceFrames = 0 }
        else {
            silenceFrames++
            if (!heardSpeech) noiseRms = noiseRms * 0.95 + rms * 0.05
        }
        return (heardSpeech && silenceFrames >= 15) || (!heardSpeech && frames >= 125) || frames >= 375
    }
}

object PcmWav {
    const val SAMPLE_RATE = 16_000
    const val MAX_PCM_BYTES = SAMPLE_RATE * 2 * 30
    fun encode(pcm: ByteArray): ByteArray {
        require(pcm.isNotEmpty() && pcm.size % 2 == 0 && pcm.size <= MAX_PCM_BYTES)
        return ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + pcm.size); put("WAVEfmt ".toByteArray())
            putInt(16); putShort(1); putShort(1); putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2)
            putShort(2); putShort(16); put("data".toByteArray()); putInt(pcm.size); put(pcm)
        }.array()
    }
}
