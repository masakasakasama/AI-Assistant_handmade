package com.tatsu.homehub.data

import android.content.Context
import com.tatsu.homehub.model.LocalAlarm
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AlarmRepository(context: Context) {
    private val dao = AppDatabase.get(context).alarmDao()

    fun observeAll(): Flow<List<LocalAlarm>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun all(): List<LocalAlarm> =
        dao.all().map { it.toModel() }

    suspend fun get(id: String): LocalAlarm? =
        dao.get(id)?.toModel()

    suspend fun upsert(alarm: LocalAlarm) {
        dao.upsert(alarm.toEntity())
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        dao.setEnabled(id, enabled)
    }
}
