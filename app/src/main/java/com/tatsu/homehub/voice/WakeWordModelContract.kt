package com.tatsu.homehub.voice

import java.io.ByteArrayOutputStream
import java.io.InputStream

/** The classifier consumes the library's fixed 16 x 96 embedding window. */
object WakeWordModelContract {
    const val MAX_BYTES = 10 * 1024 * 1024

    fun acceptsShape(shape: LongArray): Boolean =
        shape.size == 3 && shape[0] in longArrayOf(-1, 1) &&
            shape[1] in longArrayOf(-1, 16) && shape[2] == 96L

    fun readModel(input: InputStream, limit: Int = MAX_BYTES): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit) { "モデルは10MB以下にしてください" }
            output.write(buffer, 0, count)
        }
        require(output.size() > 0) { "モデルファイルが空です" }
        return output.toByteArray()
    }
}
