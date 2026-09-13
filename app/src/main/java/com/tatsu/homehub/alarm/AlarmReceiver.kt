package com.tatsu.homehub.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.tatsu.homehub.data.AlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID) ?: return
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = AlarmRepository(context)
                val alarm = repo.get(id) ?: return@launch

                val manager = context.getSystemService(NotificationManager::class.java)
                val channelId = "tatsu_alarm"
                val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    manager.createNotificationChannel(
                        NotificationChannel(
                            channelId,
                            "Alarms",
                            NotificationManager.IMPORTANCE_HIGH
                        ).apply {
                            description = "Tatsu Home alarms"
                            enableVibration(true)
                            setSound(sound, null)
                        }
                    )
                }

                val ringIntent = Intent(context, AlarmRingActivity::class.java)
                    .putExtra(AlarmScheduler.EXTRA_ALARM_ID, id)
                    .putExtra("label", alarm.label)
                val fullScreenIntent = PendingIntent.getActivity(
                    context,
                    id.hashCode(),
                    ringIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(alarm.label.ifBlank { "Alarm" })
                    .setContentText(String.format("%02d:%02d", alarm.hour, alarm.minute))
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setSound(sound)
                    .setAutoCancel(true)
                    .setFullScreenIntent(fullScreenIntent, true)
                    .build()

                manager.notify(id.hashCode(), notification)

                if (alarm.repeatMask == 0) {
                    repo.setEnabled(id, false)
                } else {
                    AlarmScheduler.schedule(context, alarm)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
