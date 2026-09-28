package com.tatsu.homehub.screenshot

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class ScreenshotAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
        ScreenshotCacheFiles.cleanupOlderThan(this, STALE_FILE_AGE_MS)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService === this) {
            activeService = null
        }
        super.onDestroy()
    }

    private fun captureInternal(callback: (Result<Uri>) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            callback(Result.failure(IllegalStateException("Screenshot capture requires Android 11 or later")))
            return
        }

        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val hardwareBuffer = screenshot.hardwareBuffer
                    val hardwareBitmap = Bitmap.wrapHardwareBuffer(
                        hardwareBuffer,
                        screenshot.colorSpace
                    )

                    if (hardwareBitmap == null) {
                        hardwareBuffer.close()
                        callback(Result.failure(IllegalStateException("Unable to read screenshot buffer")))
                        return
                    }

                    val softwareBitmap = try {
                        hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                    } finally {
                        hardwareBuffer.close()
                    }

                    Thread {
                        val result = runCatching {
                            saveToClipboardAndScheduleDelete(softwareBitmap)
                        }
                        softwareBitmap.recycle()
                        mainExecutor.execute { callback(result) }
                    }.start()
                }

                override fun onFailure(errorCode: Int) {
                    callback(
                        Result.failure(
                            IllegalStateException("Screenshot failed with error code $errorCode")
                        )
                    )
                }
            }
        )
    }

    private fun saveToClipboardAndScheduleDelete(bitmap: Bitmap): Uri {
        ScreenshotCacheFiles.cleanupOlderThan(this, STALE_FILE_AGE_MS)

        val fileName = "screen_${System.currentTimeMillis()}.png"
        val file = File(ScreenshotCacheFiles.directory(this), fileName)

        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Failed to encode screenshot"
            }
        }

        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.files",
            file
        )

        grantReadAccess(uri)

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newUri(contentResolver, "Screenshot", uri)
        )

        scheduleDelete(fileName)
        return uri
    }

    private fun grantReadAccess(uri: Uri) {
        val packages = linkedSetOf(CHATGPT_PACKAGE)
        val defaultIme = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        )
        ComponentName.unflattenFromString(defaultIme ?: "")?.packageName?.let(packages::add)

        packages.forEach { targetPackage ->
            runCatching {
                grantUriPermission(
                    targetPackage,
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
    }

    private fun scheduleDelete(fileName: String) {
        Handler(Looper.getMainLooper()).postDelayed(
            { ScreenshotCacheFiles.delete(this, fileName) },
            DELETE_DELAY_MS
        )

        val alarmManager = getSystemService(AlarmManager::class.java)
        val deleteIntent = Intent(this, ScreenshotCleanupReceiver::class.java)
            .setAction(ScreenshotCleanupReceiver.ACTION_DELETE)
            .putExtra(ScreenshotCleanupReceiver.EXTRA_FILE_NAME, fileName)

        val pendingIntent = PendingIntent.getBroadcast(
            this,
            fileName.hashCode(),
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerAt = System.currentTimeMillis() + DELETE_DELAY_MS

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            alarmManager.canScheduleExactAlarms()
        ) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }

    companion object {
        const val CHATGPT_PACKAGE = "com.openai.chatgpt"

        private const val DELETE_DELAY_MS = 5 * 60 * 1000L
        private const val STALE_FILE_AGE_MS = 30 * 60 * 1000L

        @Volatile
        private var activeService: ScreenshotAccessibilityService? = null

        fun isAvailable(): Boolean = activeService != null

        fun capture(callback: (Result<Uri>) -> Unit) {
            val service = activeService
            if (service == null) {
                callback(
                    Result.failure(
                        IllegalStateException("Screenshot accessibility service is disabled")
                    )
                )
                return
            }
            service.captureInternal(callback)
        }
    }
}
