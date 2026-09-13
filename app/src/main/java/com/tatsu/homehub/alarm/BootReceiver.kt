package com.tatsu.homehub.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tatsu.homehub.data.AlarmRepository

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AlarmRepository(context)
            .all()
            .filter { it.enabled }
            .forEach { AlarmScheduler.schedule(context, it) }
    }
}
