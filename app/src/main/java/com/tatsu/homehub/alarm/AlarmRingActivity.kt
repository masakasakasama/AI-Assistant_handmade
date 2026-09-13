package com.tatsu.homehub.alarm

import android.app.Activity
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tatsu.homehub.ui.HomeHubTheme

class AlarmRingActivity : Activity() {
    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        val label = intent.getStringExtra("label").orEmpty().ifBlank { "Alarm" }
        ringtone = RingtoneManager.getRingtone(
            this,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ).also { it.play() }

        setContent {
            HomeHubTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(label, style = MaterialTheme.typography.headlineLarge)
                    Button(
                        onClick = { finish() },
                        modifier = Modifier.padding(top = 24.dp)
                    ) {
                        Text("Stop")
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        ringtone?.stop()
        super.onDestroy()
    }
}
