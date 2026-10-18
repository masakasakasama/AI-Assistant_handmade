package com.tatsu.homehub.news

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tatsu.homehub.MainActivity
import com.tatsu.homehub.data.AppPrefs
import com.tatsu.homehub.data.SecurePrefs

class NewsWorker(private val app: Context, params: WorkerParameters) : CoroutineWorker(app, params) {
    override suspend fun doWork(): Result {
        val prefs = AppPrefs(app)
        val token = SecurePrefs(app).get(SecurePrefs.KEY_AI_BACKEND_TOKEN).orEmpty()
        if (token.isBlank()) return Result.success() // Retry next hour after setup
        return try {
            val batches = NewsClient().load(prefs.aiBackendUrl, token)
            val latest = batches.firstOrNull()?.id ?: return Result.success()
            val state = app.getSharedPreferences("tatsu-home-news", Context.MODE_PRIVATE)
            val seen = state.getString("last-delivered-batch", null)
            if (seen == null) {
                state.edit().putString("last-delivered-batch", latest).apply()
                return Result.success() // No old-alert spam when enabling the feature
            }
            val fresh = batches.takeWhile { it.id != seen }
            if (fresh.isNotEmpty()) {
                val alerts = fresh.flatMap { it.alerts }.sortedByDescending { it.score }
                if (alerts.isNotEmpty()) notifyOwner(alerts.first())
                state.edit().putString("last-delivered-batch", latest).apply()
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry() // WorkManager retries without exposing token or article content
        }
    }

    private fun notifyOwner(alert: HomeNewsAlert) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) return
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(
            "tatsu-news", "重要ニュース", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(app, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_news", true)
        }
        val pending = PendingIntent.getActivity(app, 9274, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = android.app.Notification.Builder(app, "tatsu-news")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Tatsu Home・重要ニュース ${alert.score}/100")
            .setContentText(alert.summary)
            .setStyle(android.app.Notification.BigTextStyle().bigText(alert.summary))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(9274, notification)
    }
}
