package com.tatsu.homehub.ui

import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.ConsoleMessage
import android.webkit.WebView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tatsu.homehub.voice.VoicePhase
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MascotWebViewTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun findWeb(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findWeb(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun evaluate(view: WebView, script: String): String? {
        val latch = CountDownLatch(1)
        var result: String? = null
        instrumentation.runOnMainSync { view.evaluateJavascript(script) { result = it; latch.countDown() } }
        assertTrue("Javascript response timeout", latch.await(10, TimeUnit.SECONDS))
        return result
    }
    @Test fun productionWebViewRendersAndAnimatesVoicePhases() {
        val logs = CopyOnWriteArrayList<String>()
        val scenario = ActivityScenario.launch(MascotPreviewActivity::class.java)
        var web: WebView? = null
        try {
            val deadline = System.currentTimeMillis() + 30_000
            var ready = false
            while (System.currentTimeMillis() < deadline && !ready) {
                scenario.onActivity { activity ->
                    val candidate = findWeb(activity.window.decorView)
                    if (candidate != null && web !== candidate) {
                        web = candidate
                        candidate.webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                                logs.add("${message.messageLevel()}: ${message.message()} @ ${message.sourceId()}:${message.lineNumber()}")
                                android.util.Log.d("MascotTest", logs.last())
                                return true
                            }
                        }
                    }
                }
                if (web != null) ready = evaluate(web!!, "Boolean(window.tatsuMascot?.ready)") == "true"
                if (!ready) Thread.sleep(200)
            }
            assertTrue("Production renderer failed to initialize. Console: $logs", ready)
            val renderer = web!!
            for (phase in VoicePhase.entries) {
                scenario.onActivity { it.phase = phase }
                Thread.sleep(200)
                assertEquals("State failed for $phase; console=$logs", "\"${phase.name}\"", evaluate(renderer, "window.tatsuMascot.diagnostics().phase"))
                assertEquals("Renderer unexpectedly paused; console=$logs", "false", evaluate(renderer, "window.tatsuMascot.diagnostics().paused"))
            }
            val before = evaluate(renderer, "window.tatsuMascot.diagnostics().frames")!!.toInt()
            Thread.sleep(300)
            val after = evaluate(renderer, "window.tatsuMascot.diagnostics().frames")!!.toInt()
            assertTrue("No live WebGL frames", after > before)
            scenario.onActivity { it.phase = VoicePhase.IDLE }
            Thread.sleep(200)
            assertTrue("Errors in production WebView: $logs", logs.none { it.startsWith("ERROR:") })
        } finally {
            instrumentation.uiAutomation.takeScreenshot()?.let { screenshot ->
                File(instrumentation.targetContext.getExternalFilesDir(null), "tatsu-mascot.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                screenshot.recycle()
            }
            scenario.close()
        }
    }
}
