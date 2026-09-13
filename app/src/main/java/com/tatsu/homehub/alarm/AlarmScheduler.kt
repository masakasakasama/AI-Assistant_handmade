package com.tatsu.homehub.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.tatsu.homehub.model.LocalAlarm
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

object AlarmScheduler {
    fun schedule(context: Context, alarm: LocalAlarm) {
        if (!alarm.enabled) {
            cancel(context, alarm.id)
            return
        }

        val manager = context.getSystemService(AlarmManager::class.java)
        val triggerAt = nextTrigger(alarm)
        val pending = pendingIntent(context, alarm.id)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context, id: String) {
        context.getSystemService(AlarmManager::class.java)
            .cancel(pendingIntent(context, id))
    }

    fun nextTrigger(alarm: LocalAlarm, now: LocalDateTime = LocalDateTime.now()): Long {
        val base = now.withHour(alarm.hour).withMinute(alarm.minute).withSecond(0).withNano(0)

        val target = if (alarm.repeatMask == 0) {
            if (base.isAfter(now)) base else base.plusDays(1)
        } else {
            (0L..7L)
                .map { offset -> now.toLocalDate().plusDays(offset).atTime(alarm.hour, alarm.minute) }
                .first { candidate ->
                    val index = dayIndex(candidate.dayOfWeek)
                    alarm.repeatsOn(index) && candidate.isAfter(now)
                }
        }

        return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun dayIndex(day: DayOfWeek): Int = day.value - 1

    private fun pendingIntent(context: Context, id: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_ALARM_ID, id)
        return PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    const val EXTRA_ALARM_ID = "alarm_id"
}
