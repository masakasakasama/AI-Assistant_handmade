package com.tatsu.homehub.alarm

import com.tatsu.homehub.model.LocalAlarm
import com.tatsu.homehub.data.toEntity
import com.tatsu.homehub.data.toModel
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmDateTest {
    private val alarm = LocalAlarm("test", 7, 0, "朝", 0)
    private fun local(timestamp: Long) = java.time.Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
    @Test fun tomorrowAtSevenDoesNotBecomeTodayAtSevenBeforeSeven() {
        val now = LocalDateTime.of(2026, 10, 4, 6, 0)
        assertEquals(LocalDateTime.of(2026, 10, 5, 7, 0), local(AlarmScheduler.nextTrigger(alarm.copy(dateLocal = "2026-10-05"), now)))
    }
    @Test fun explicitDateRemainsTheSameAfterRebootOrWhenItIsPast() {
        assertEquals(LocalDateTime.of(2026, 10, 5, 7, 0), local(AlarmScheduler.nextTrigger(alarm.copy(dateLocal = "2026-10-05"), LocalDateTime.of(2026, 10, 6, 6, 0))))
    }
    @Test fun legacyTimeOnlyAlarmStillUsesNextOccurrence() {
        assertEquals(LocalDateTime.of(2026, 10, 4, 7, 0), local(AlarmScheduler.nextTrigger(alarm, LocalDateTime.of(2026, 10, 4, 6, 0))))
        assertEquals(LocalDateTime.of(2026, 10, 5, 7, 0), local(AlarmScheduler.nextTrigger(alarm, LocalDateTime.of(2026, 10, 4, 8, 0))))
    }
    @Test fun dateSurvivesDatabaseModelConversion() {
        val dated = alarm.copy(dateLocal = "2026-10-05")
        assertEquals(dated, dated.toEntity().toModel())
    }
}
