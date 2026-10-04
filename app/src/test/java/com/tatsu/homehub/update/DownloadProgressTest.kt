package com.tatsu.homehub.update

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class DownloadProgressTest {
    @Test fun completeDownloadPreservesBytesAndReportsCompletionAfterFlush() {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val reports = mutableListOf<DownloadProgress>()
        var flushed = false
        val output = object : ByteArrayOutputStream() {
            override fun flush() { super.flush(); flushed = true }
        }
        copyDownload(ByteArrayInputStream(bytes), output, bytes.size.toLong(), {
            if (it.percent == 100) assertTrue(flushed)
            reports.add(it)
        })
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(0, reports.first().percent)
        assertEquals(100, reports.last().percent)
        assertEquals(bytes.size.toLong(), reports.last().downloadedBytes)
        assertTrue(reports.zipWithNext().all { (a, b) -> b.downloadedBytes >= a.downloadedBytes })
    }

    @Test fun truncatedTransferNeverReportsSuccess() {
        val reports = mutableListOf<DownloadProgress>()
        try {
            copyDownload(ByteArrayInputStream(ByteArray(100)), ByteArrayOutputStream(), 200, { reports.add(it) })
            fail("A partial APK was accepted")
        } catch (_: IOException) { }
        assertTrue(reports.none { it.percent == 100 })
    }

    @Test fun unknownLengthReportsBytesWithoutInventingPercentage() {
        val reports = mutableListOf<DownloadProgress>()
        copyDownload(ByteArrayInputStream(ByteArray(123)), ByteArrayOutputStream(), null, { reports.add(it) })
        assertEquals(123L, reports.last().downloadedBytes)
        assertTrue(reports.all { it.percent == null })
    }

    @Test fun cancellationStopsCopyingAndDoesNotReportCompletion() {
        val output = ByteArrayOutputStream()
        val reports = mutableListOf<DownloadProgress>()
        try {
            copyDownload(ByteArrayInputStream(ByteArray(100)), output, 100, { reports.add(it) }) {
                throw java.util.concurrent.CancellationException()
            }
            fail("Cancellation ignored")
        } catch (_: java.util.concurrent.CancellationException) { }
        assertEquals(0, output.size())
        assertTrue(reports.none { it.percent == 100 })
    }
}
