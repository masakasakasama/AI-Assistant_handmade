package com.tatsu.homehub.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tatsu.homehub.voice.VoicePhase

/** Debug-only harness: same production renderer, no audio/network startup side effects. */
class MascotPreviewActivity : ComponentActivity() {
    var phase by mutableStateOf(VoicePhase.IDLE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeHubTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    TatsuMascot3D(phase, Modifier.size(224.dp))
                }
            }
        }
    }
}
