package com.tatsu.homehub.ui

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewAssetLoader
import com.tatsu.homehub.R
import com.tatsu.homehub.voice.VoicePhase
import java.io.ByteArrayInputStream
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val MASCOT_ORIGIN = "https://appassets.androidplatform.net"
private const val MASCOT_PATH = "/assets/mascot3d/"
private val MASCOT_FILES = setOf("index.html", "mascot.js", "mascot.css", "three.min.js")

/** The web renderer receives enums only. Audio, text, permissions and controls stay native. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun TatsuMascot3D(phase: VoicePhase, modifier: Modifier = Modifier) {
    var attempt by remember { mutableIntStateOf(0) }
    key(attempt) { Mascot3DContent(phase, modifier) { attempt++ } }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun Mascot3DContent(phase: VoicePhase, modifier: Modifier, onRetry: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val nativeRoot = LocalView.current
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var visible by remember { mutableStateOf(false) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val running = resumed && visible && !failed

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(webView, loaded, running, phase, failed) {
        val view = webView ?: return@LaunchedEffect
        if (failed) return@LaunchedEffect
        if (running) view.onResume()
        if (loaded) {
            // name is from a closed Kotlin enum, never speech or generated content.
            view.evaluateJavascript(
                "window.tatsuMascot?.setPhase('${phase.name}');window.tatsuMascot?.setPaused(${!running});",
                null
            )
        }
        if (loaded && !running) view.onPause()
    }
    LaunchedEffect(webView, loaded, running, failed) {
        val view = webView ?: return@LaunchedEffect
        if (!running || failed) return@LaunchedEffect
        val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
        while (!failed) {
            val result = withTimeoutOrNull(if (ready) 2_000 else 10_000) {
                suspendCancellableCoroutine<String?> { continuation ->
                    view.evaluateJavascript(
                        "window.tatsuMascot?.failed ? 'failed' : window.tatsuMascot?.ready ? 'ready' : 'loading'"
                    ) { value -> if (continuation.isActive) continuation.resume(value) }
                }
            }
            if (webView !== view) return@LaunchedEffect
            when (result) {
                "\"ready\"" -> ready = true
                "\"failed\"", null -> { failed = true; ready = false }
                else -> if (android.os.SystemClock.elapsedRealtime() >= deadline) {
                    failed = true
                    ready = false
                }
            }
            delay(if (ready) 1_000 else 100)
        }
    }

    Box(modifier.semantics { contentDescription = "Tatsu Homeのマスコット" }
        .onGloballyPositioned { coordinates ->
            val rect = coordinates.boundsInWindow()
            visible = rect.width > 0 && rect.height > 0 && rect.right > 0 && rect.bottom > 0 &&
                rect.left < nativeRoot.width && rect.top < nativeRoot.height
        }) {
        Image(
            painterResource(R.drawable.tatsu_mascot), contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
        )
        if (!failed) {
            AndroidView(
                // A real visible hardware surface is required during WebGL bootstrap.
                // The page stays transparent until its first successful frame.
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    val assetLoader = WebViewAssetLoader.Builder()
                        .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                        .build()
                    object : WebView(context) {
                        override fun onTouchEvent(event: android.view.MotionEvent?): Boolean = false
                    }.apply {
                        setBackgroundColor(AndroidColor.TRANSPARENT)
                        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                        isFocusable = false
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        settings.apply {
                            javaScriptEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            domStorageEnabled = false
                            databaseEnabled = false
                            setSupportMultipleWindows(false)
                            javaScriptCanOpenWindowsAutomatically = false
                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            mediaPlaybackRequiresUserGesture = true
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                                val uri = request.url
                                val file = uri.path?.removePrefix(MASCOT_PATH)
                                if (request.method == "GET" && uri.scheme == "https" &&
                                    uri.host == "appassets.androidplatform.net" && uri.port == -1 &&
                                    uri.path == "$MASCOT_PATH$file" && file in MASCOT_FILES &&
                                    uri.query == null && uri.fragment == null) {
                                    assetLoader.shouldInterceptRequest(uri)?.let { response ->
                                        response.mimeType = when (file) {
                                            "index.html" -> "text/html"
                                            "mascot.css" -> "text/css"
                                            else -> "text/javascript"
                                        }
                                        response.encoding = "UTF-8"
                                        return response
                                    }
                                }
                                return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                            }
                            override fun onPageFinished(view: WebView, url: String) {
                                if (webView === view && url == "$MASCOT_ORIGIN${MASCOT_PATH}index.html") loaded = true
                            }
                            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                                failed = true
                                ready = false
                                return true
                            }
                        }
                        webView = this
                        loadUrl("$MASCOT_ORIGIN${MASCOT_PATH}index.html")
                    }
                },
                onRelease = { view ->
                    if (!failed) view.evaluateJavascript("window.tatsuMascot?.dispose()", null)
                    if (!failed) { view.stopLoading(); view.onPause() }
                    view.removeAllViews()
                    view.destroy()
                    if (webView === view) webView = null
                }
            )
        } else {
            TextButton(onClick = onRetry, modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
                Text("3D表示を再試行")
            }
        }
    }
}
