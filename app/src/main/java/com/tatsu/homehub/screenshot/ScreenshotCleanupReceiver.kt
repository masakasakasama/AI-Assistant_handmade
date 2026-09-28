package com.tatsu.homehub.screenshot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File

internal object ScreenshotCacheFiles {
    const val DIRECTORY = "clipboard_screens"

    fun directory(context: Context): File =
        File(context.cacheDir, DIRECTORY).apply { mkdirs() }

    fun delete(context: Context, fileName: String): Boolean {
        if (File(fileName).name != fileName) return false
        val dir = directory(context)
        val file = File(dir, fileName)
        return !file.exists() || file.delete()
    }

    fun cleanupOlderThan(context: Context, ageMs: Long) {
        val cutoff = System.currentTimeMillis() - ageMs
        directory(context).listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }
}

class ScreenshotCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME) ?: return
        ScreenshotCacheFiles.delete(context, fileName)
    }

    companion object {
        const val ACTION_DELETE = "com.tatsu.homehub.action.DELETE_CLIPBOARD_SCREENSHOT"
        const val EXTRA_FILE_NAME = "screenshot_file_name"
    }
}
