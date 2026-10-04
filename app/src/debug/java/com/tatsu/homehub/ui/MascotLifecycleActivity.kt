package com.tatsu.homehub.ui

import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import com.tatsu.homehub.voice.VoicePhase

/** Native view lifecycle fixture: its parent owns reparenting, unlike Compose's AndroidViewHolder. */
class MascotLifecycleActivity : ComponentActivity() {
    private lateinit var mascot: NativeMascotView
    var phase = VoicePhase.IDLE
        set(value) {
            field = value
            if (::mascot.isInitialized) mascot.configure(value, lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mascot = NativeMascotView(this)
        val size = (224 * resources.displayMetrics.density).toInt()
        val parent = FrameLayout(this)
        parent.addView(mascot, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        setContentView(parent)
    }

    override fun onResume() { super.onResume(); mascot.configure(phase, true) }
    override fun onPause() { mascot.configure(phase, false); super.onPause() }
    override fun onDestroy() { mascot.release(); super.onDestroy() }
}
