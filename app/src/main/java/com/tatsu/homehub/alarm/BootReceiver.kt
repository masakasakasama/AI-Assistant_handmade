package com.tatsu.homehub.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tatsu.homehub.data.AlarmRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AlarmRepository(context)
                    .all()
                    .filter { it.enabled }
                    .forEach { AlarmScheduler.schedule(context, it) }
            } finally {
                pending.finish()
            }
        }
    }
}
