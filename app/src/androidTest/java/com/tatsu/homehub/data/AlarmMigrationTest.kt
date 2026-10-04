package com.tatsu.homehub.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmMigrationTest {
    @Test fun upgradingPreservesOldAlarmsAndNewDatesSurviveReopening() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "alarm-migration-test.db"
        context.deleteDatabase(name)
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("CREATE TABLE alarms (id TEXT NOT NULL PRIMARY KEY, hour INTEGER NOT NULL, minute INTEGER NOT NULL, label TEXT NOT NULL, repeatMask INTEGER NOT NULL, enabled INTEGER NOT NULL)")
            old.execSQL("INSERT INTO alarms VALUES ('old',7,0,'朝',0,1)")
            old.version = 1
        }
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_1_2).build()
        try {
            open().use { db ->
                val previous = db.alarmDao().get("old")!!
                assertEquals("朝", previous.label)
                assertEquals(7, previous.hour)
                assertNull(previous.dateLocal)
                db.alarmDao().upsert(previous.copy(id = "dated", dateLocal = "2026-10-05"))
            }
            open().use { db ->
                assertEquals("2026-10-05", db.alarmDao().get("dated")!!.dateLocal)
                assertEquals(2, db.alarmDao().all().size)
            }
        } finally { context.deleteDatabase(name) }
    }
}
