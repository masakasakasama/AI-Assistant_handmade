package com.tatsu.homehub.update

import java.io.InputStream
import java.io.OutputStream
import java.io.IOException

data class DownloadProgress(val downloadedBytes: Long, val totalBytes: Long?) {
    val percent: Int? get() = totalBytes?.takeIf { it > 0 }?.let {
        ((downloadedBytes.toDouble() / it) * 100).toInt().coerceIn(0, 100)
    }
}

/** Reports completion only after the expected bytes have arrived and the file is flushed. */
internal fun copyDownload(
    input: InputStream,
    output: OutputStream,
    totalBytes: Long?,
    onProgress: (DownloadProgress) -> Unit,
    checkCancelled: () -> Unit = {}
) {
    var received = 0L
    var lastReport = System.nanoTime()
    onProgress(DownloadProgress(0, totalBytes))
    val buffer = ByteArray(64 * 1024)
    while (true) {
        checkCancelled()
        val count = input.read(buffer)
        if (count < 0) break
        output.write(buffer, 0, count)
        received += count
        if (totalBytes != null && received > totalBytes) throw IOException("APKのサイズが一致しません。もう一度更新してください")
        val now = System.nanoTime()
        if (now - lastReport >= 100_000_000 && (totalBytes == null || received < totalBytes)) {
            onProgress(DownloadProgress(received, totalBytes))
            lastReport = now
        }
    }
    if (received == 0L || (totalBytes != null && received != totalBytes)) {
        throw IOException("ダウンロードが途中で切れました。もう一度更新してください")
    }
    checkCancelled()
    output.flush()
    onProgress(DownloadProgress(received, totalBytes))
}
