package com.tatsu.homehub.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.tatsu.homehub.R
import com.tatsu.homehub.voice.VoicePhase

@Composable
internal fun TatsuMascot3D(phase: VoicePhase, modifier: Modifier = Modifier) {
    var attempt by remember { mutableIntStateOf(0) }
    key(attempt) { NativeMascotContent(phase, modifier) { attempt++ } }
}

@Composable
private fun NativeMascotContent(phase: VoicePhase, modifier: Modifier, onRetry: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val root = LocalView.current
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var visible by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var renderer by remember { mutableStateOf<NativeMascotView?>(null) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(renderer, phase, resumed, visible, failed) { renderer?.configure(phase, resumed && visible && !failed) }
    Box(modifier.clickable(role = Role.Button, onClickLabel = "なでる") {
        if (failed) onRetry() else renderer?.reactToTap()
    }.semantics { contentDescription = "Tatsu Homeの3Dマスコット" }
        .background(Brush.linearGradient(listOf(Color(0xFFFFF0F9), Color(0xFFE0F1FF))), RoundedCornerShape(44.dp))
        .onGloballyPositioned {
            val rect = it.boundsInWindow()
            visible = rect.width > 0 && rect.height > 0 && rect.right > 0 && rect.bottom > 0 && rect.left < root.width && rect.top < root.height
        }) {
        if (!ready || failed) {
            Image(painterResource(R.drawable.tatsu_mascot_3d), null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(44.dp)))
        }
        if (!failed) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                NativeMascotView(context).also { view ->
                    view.onRendered = { ready = true }
                    view.onFailure = { reason -> android.util.Log.e("TatsuMascot", reason); failed = true; ready = false }
                    renderer = view
                }
            }, onRelease = { view -> view.release(); if (renderer === view) renderer = null })
        } else {
            TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.BottomCenter)) { Text("3Dの動きを再試行") }
        }
    }
}
