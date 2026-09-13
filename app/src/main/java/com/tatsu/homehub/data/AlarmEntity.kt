package com.tatsu.homehub.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.tatsu.homehub.model.LocalAlarm

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey val id: String,
    val hour: Int,
    val minute: Int,
    val label: String,
    val repeatMask: Int,
    val enabled: Boolean
)

fun AlarmEntity.toModel(): LocalAlarm = LocalAlarm(
    id = id,
    hour = hour,
    minute = minute,
    label = label,
    repeatMask = repeatMask,
    enabled = enabled
)

fun LocalAlarm.toEntity(): AlarmEntity = AlarmEntity(
    id = id,
    hour = hour,
    minute = minute,
    label = label,
    repeatMask = repeatMask,
    enabled = enabled
)
