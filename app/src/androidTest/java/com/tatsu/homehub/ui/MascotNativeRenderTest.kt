package com.tatsu.homehub.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.tatsu.homehub.MainActivity
import com.tatsu.homehub.voice.VoicePhase
import java.io.File
import java.io.FileInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MascotNativeRenderTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun findRenderer(view: View): NativeMascotView? {
        if (view is NativeMascotView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findRenderer(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun assertPixels(bitmap: Bitmap, requireOpenEyes: Boolean = true) {
        var visible = 0; var blue = 0; var white = 0
        for (y in 0 until bitmap.height step 2) for (x in 0 until bitmap.width step 2) {
            val c = bitmap.getPixel(x, y)
            val a = android.graphics.Color.alpha(c)
            val r = android.graphics.Color.red(c); val g = android.graphics.Color.green(c); val b = android.graphics.Color.blue(c)
            if (a > 128) {
                visible++
                if (b > r * 1.15 && g > r * 1.05 && r < 230) blue++
                if (r > 140 && g > 140 && b > 140 && kotlin.math.abs(r - b) < 60) white++
            }
        }
        val samples = bitmap.width * bitmap.height / 4
        assertTrue("Empty native texture: visible=$visible / $samples", visible > samples / 10)
        if (requireOpenEyes) assertTrue("3D blue eyes absent: blue=$blue", blue > 20)
        assertTrue("3D white plush absent: white=$white", white > samples / 12)
    }
    @Test fun aNativePhasesPauseAndResume() {
        ActivityScenario.launch(MascotLifecycleActivity::class.java).use { scenario ->
            var renderer: NativeMascotView? = null
            val deadline = System.currentTimeMillis() + 30_000
            while ((renderer?.frames ?: 0L) < 4 && System.currentTimeMillis() < deadline) {
                scenario.onActivity { renderer = findRenderer(it.window.decorView) }
                Thread.sleep(100)
            }
            assertNotNull("Native renderer unavailable", renderer)
            assertTrue("No native GL frames", renderer!!.frames >= 4)
            for (phase in VoicePhase.entries) {
                scenario.onActivity { it.phase = phase }
                Thread.sleep(200)
                scenario.onActivity { assertEquals(phase, renderer!!.phase); assertTrue(renderer!!.running) }
            }
            // Reparent through the view's owning FrameLayout. Directly modifying
            // Compose's AndroidViewHolder bypasses its layout-node ownership.
            var parent: ViewGroup? = null
            var childIndex = 0
            var layout: ViewGroup.LayoutParams? = null
            repeat(3) {
                scenario.onActivity {
                    parent = renderer!!.parent as ViewGroup
                    childIndex = parent!!.indexOfChild(renderer)
                    layout = renderer!!.layoutParams
                    parent!!.removeView(renderer)
                    assertFalse(renderer!!.running)
                }
                Thread.sleep(100)
                val detachedFrames = renderer!!.frames
                scenario.onActivity { parent!!.addView(renderer, childIndex, layout); parent!!.requestLayout() }
                val reattachDeadline = System.currentTimeMillis() + 5_000
                while (renderer!!.frames <= detachedFrames && System.currentTimeMillis() < reattachDeadline) Thread.sleep(100)
                scenario.onActivity {
                    assertTrue("Temporary reattachment froze mascot: running=${renderer!!.running}, loaded=${renderer!!.modelLoaded}, attached=${renderer!!.isAttachedToWindow}, texture=${renderer!!.isAvailable}, swapChain=${renderer!!.hasRenderSurface}", renderer!!.frames > detachedFrames)
                    assertTrue(renderer!!.running)
                }
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(200)
            instrumentation.runOnMainSync { assertFalse("Not paused", renderer!!.running) }
            scenario.moveToState(Lifecycle.State.RESUMED)
            Thread.sleep(200)
            var before = 0L
            scenario.onActivity { before = renderer!!.frames }
            Thread.sleep(500)
            scenario.onActivity {
                assertTrue("No animation frames after resume", renderer!!.frames > before)
                val bitmap = renderer!!.bitmap
                assertNotNull("Texture readback unavailable", bitmap)
                assertPixels(bitmap!!)
                bitmap.recycle()
            }
        }
    }
    @Test fun bActualAiPageContainsVisible3dPixels() {
        instrumentation.uiAutomation.executeShellCommand("pm grant com.tatsu.homehub android.permission.RECORD_AUDIO").use { fd ->
            FileInputStream(fd.fileDescriptor).use { it.readBytes() }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val device = UiDevice.getInstance(instrumentation)
            val aiTab = device.wait(Until.findObject(By.text("AI")), 15_000)
            assertNotNull("Actual AI navigation not found", aiTab)
            aiTab!!.click()
            var renderer: NativeMascotView? = null
            val deadline = System.currentTimeMillis() + 30_000
            while ((renderer?.frames ?: 0L) < 5 && System.currentTimeMillis() < deadline) {
                scenario.onActivity { renderer = findRenderer(it.window.decorView) }
                Thread.sleep(100)
            }
            assertNotNull("AI page has no native renderer", renderer)
            assertTrue("AI page has no rendered frames", renderer!!.frames >= 5)
            // Verify the real Compose screen, as well as the isolated native fixture.
            scenario.moveToState(Lifecycle.State.CREATED)
            Thread.sleep(200)
            scenario.onActivity { assertFalse("AI renderer kept running in background", renderer!!.running) }
            val backgroundFrames = renderer!!.frames
            scenario.moveToState(Lifecycle.State.RESUMED)
            val resumeDeadline = System.currentTimeMillis() + 10_000
            while (renderer!!.frames <= backgroundFrames && System.currentTimeMillis() < resumeDeadline) Thread.sleep(100)
            assertTrue("Actual AI page did not resume rendering", renderer!!.frames > backgroundFrames)
            val latch = CountDownLatch(1)
            var result = -1
            lateinit var windowImage: Bitmap
            lateinit var destination: File
            scenario.onActivity { activity ->
                val texture = renderer!!.bitmap
                assertNotNull("AI native texture readback unavailable", texture)
                assertPixels(texture!!)
                File(activity.filesDir, "mascot-native.png").outputStream().use { texture.compress(Bitmap.CompressFormat.PNG, 100, it) }
                texture.recycle()
                windowImage = Bitmap.createBitmap(activity.window.decorView.width, activity.window.decorView.height, Bitmap.Config.ARGB_8888)
                destination = File(activity.filesDir, "mascot-ai-window.png")
                PixelCopy.request(activity.window, windowImage, { result = it; latch.countDown() }, Handler(Looper.getMainLooper()))
            }
            assertTrue("Actual AI screenshot timed out", latch.await(10, TimeUnit.SECONDS))
            assertEquals("Actual AI screenshot failed", PixelCopy.SUCCESS, result)
            destination.outputStream().use { windowImage.compress(Bitmap.CompressFormat.PNG, 100, it) }
            windowImage.recycle()
        }
    }
    @Test fun cIdleAndSpeakingChangeRenderedPixelsEvenWhenSystemAnimationsAreOff() {
        val fd = instrumentation.uiAutomation.executeShellCommand("settings get global animator_duration_scale")
        val oldScale = fd.use { FileInputStream(it.fileDescriptor).use { stream -> String(stream.readBytes()).trim() } }
        fun setScale(value: String) {
            instrumentation.uiAutomation.executeShellCommand("settings put global animator_duration_scale $value").use {
                FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() }
            }
        }
        setScale("0")
        try {
            ActivityScenario.launch(MascotPreviewActivity::class.java).use { scenario ->
                var renderer: NativeMascotView? = null
                val deadline = System.currentTimeMillis() + 30_000
                while ((renderer?.frames ?: 0L) < 5 && System.currentTimeMillis() < deadline) {
                    scenario.onActivity { renderer = findRenderer(it.window.decorView) }
                    Thread.sleep(100)
                }
                assertNotNull(renderer)
                val pictures = mutableListOf<Bitmap>()
                for (phase in listOf(VoicePhase.IDLE, VoicePhase.SPEAKING)) {
                    scenario.onActivity { it.phase = phase }
                    Thread.sleep(400)
                    val mouthAreas = mutableListOf<Int>()
                    repeat(12) { frame ->
                        scenario.onActivity {
                            val bitmap = renderer!!.bitmap!!
                            assertPixels(bitmap, requireOpenEyes = false)
                            var brownPixels = 0
                            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                                val color = bitmap.getPixel(x, y)
                                val r = android.graphics.Color.red(color); val g = android.graphics.Color.green(color); val b = android.graphics.Color.blue(color)
                                if (android.graphics.Color.alpha(color) > 200 && r in 40..180 && r > g * 1.15 && b < 145) brownPixels++
                            }
                            mouthAreas.add(brownPixels)
                            pictures.add(bitmap)
                            File(it.filesDir, "mascot-${phase.name.lowercase()}-$frame.png").outputStream().use { stream ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                            }
                        }
                        Thread.sleep(150)
                    }
                    if (phase == VoicePhase.SPEAKING) {
                        assertTrue("Speaking mouth never visibly opens: $mouthAreas", mouthAreas.max() > mouthAreas.min() + 5)
                    }
                    val first = pictures[pictures.size-12]
                    var mostChanged = 0
                    for (second in pictures.takeLast(11)) {
                        var changed = 0
                        for (y in 0 until first.height step 2) for (x in 0 until first.width step 2) {
                            val a = first.getPixel(x, y); val b = second.getPixel(x, y)
                            if (android.graphics.Color.alpha(a) > 200 || android.graphics.Color.alpha(b) > 200) {
                                val difference = kotlin.math.abs(android.graphics.Color.red(a)-android.graphics.Color.red(b)) +
                                    kotlin.math.abs(android.graphics.Color.green(a)-android.graphics.Color.green(b)) +
                                    kotlin.math.abs(android.graphics.Color.blue(a)-android.graphics.Color.blue(b))
                                if (difference > 60) changed++
                            }
                        }
                        mostChanged = maxOf(mostChanged, changed)
                    }
                    android.util.Log.i("MascotMotionTest", "$phase changedPixels=$mostChanged")
                    assertTrue("$phase is visually static: changedPixels=$mostChanged", mostChanged > first.width * first.height / 200)
                }
                val width = pictures.first().width; val height = pictures.first().height
                val evidence = Bitmap.createBitmap(width*3, height*2, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(evidence)
                canvas.drawColor(android.graphics.Color.rgb(245,240,255))
                listOf(0, 5, 11, 12, 17, 23).forEachIndexed { index, frame -> canvas.drawBitmap(pictures[frame], (index%3*width).toFloat(), (index/3*height).toFloat(), null) }
                scenario.onActivity { activity ->
                    File(activity.filesDir, "mascot-motion.png").outputStream().use { evidence.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
                evidence.recycle(); pictures.forEach { it.recycle() }
            }
        } finally { setScale(if (oldScale == "null") "1" else oldScale) }
    }

    @Test fun dPettingByTouchWorksWithoutAiCredentials() {
        ActivityScenario.launch(MascotPreviewActivity::class.java).use { scenario ->
            var renderer: NativeMascotView? = null
            val deadline = System.currentTimeMillis() + 30_000
            while ((renderer?.frames ?: 0L) < 5 && System.currentTimeMillis() < deadline) {
                scenario.onActivity { renderer = findRenderer(it.window.decorView) }
                Thread.sleep(100)
            }
            assertNotNull(renderer)
            scenario.onActivity { it.phase = VoicePhase.ERROR }
            Thread.sleep(300)
            lateinit var beforeTap: Bitmap
            scenario.onActivity { beforeTap = renderer!!.bitmap!! }
            var changedPixels = 0
            val position = IntArray(2)
            var x = 0; var y = 0
            scenario.onActivity {
                renderer!!.getLocationOnScreen(position)
                x = position[0] + renderer!!.width / 2
                y = position[1] + renderer!!.height / 2
            }
            UiDevice.getInstance(instrumentation).click(x, y)
            var maxLift = 0f
            repeat(12) { frame ->
                Thread.sleep(100)
                scenario.onActivity {
                    assertEquals("Touch never arrived at mascot", 1L, renderer!!.tapCount)
                    assertEquals("Petting changed voice status", VoicePhase.ERROR, renderer!!.phase)
                    maxLift = maxOf(maxLift, renderer!!.tapLift)
                    val bitmap = renderer!!.bitmap!!
                    assertPixels(bitmap, requireOpenEyes = false)
                    var changed = 0
                    for (py in 0 until bitmap.height step 2) for (px in 0 until bitmap.width step 2) {
                        val a = beforeTap.getPixel(px, py); val b = bitmap.getPixel(px, py)
                        val difference = kotlin.math.abs(android.graphics.Color.alpha(a)-android.graphics.Color.alpha(b)) +
                            kotlin.math.abs(android.graphics.Color.red(a)-android.graphics.Color.red(b)) +
                            kotlin.math.abs(android.graphics.Color.green(a)-android.graphics.Color.green(b)) +
                            kotlin.math.abs(android.graphics.Color.blue(a)-android.graphics.Color.blue(b))
                        if (difference > 80) changed++
                    }
                    changedPixels = maxOf(changedPixels, changed)
                    File(it.filesDir, "mascot-tap-$frame.png").outputStream().use { stream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    }
                    bitmap.recycle()
                }
            }
            assertTrue("Tap produced no visible movement", changedPixels > beforeTap.width * beforeTap.height / 100)
            beforeTap.recycle()
            assertTrue("Tap bounce is too small: $maxLift", maxLift > .15f)
            Thread.sleep(200)
            scenario.onActivity { assertTrue("Tap reaction never finishes", renderer!!.tapLift < .001f) }
            UiDevice.getInstance(instrumentation).click(x, y)
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertEquals("Repeated taps stopped working", 2L, renderer!!.tapCount) }
        }
    }

    @Test fun eHomeShowsLargeMascotBeforeDashboard() {
        instrumentation.uiAutomation.executeShellCommand("pm grant com.tatsu.homehub android.permission.RECORD_AUDIO").use { fd ->
            FileInputStream(fd.fileDescriptor).use { it.readBytes() }
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var renderer: NativeMascotView? = null
            val deadline = System.currentTimeMillis() + 30_000
            while ((renderer?.frames ?: 0L) < 5 && System.currentTimeMillis() < deadline) {
                scenario.onActivity { renderer = findRenderer(it.window.decorView) }
                Thread.sleep(100)
            }
            assertNotNull("Home page mascot absent", renderer)
            assertTrue("Home mascot never rendered", renderer!!.frames >= 5)
            scenario.onActivity {
                assertTrue("Home character is too small", renderer!!.width > it.window.decorView.width * .72f)
                val bitmap = renderer!!.bitmap!!
                assertPixels(bitmap, requireOpenEyes = false)
                File(it.filesDir, "mascot-home.png").outputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream) }
                bitmap.recycle()
            }
            val device = UiDevice.getInstance(instrumentation)
            assertNotNull("Home speech control absent", device.findObject(By.text("話す")))
            assertTrue("Home exposes model selection", device.findObject(By.text("Jevで話す")) == null &&
                device.findObject(By.text("Lunaで話す")) == null)
            lateinit var destination: File
            scenario.onActivity { destination = File(it.filesDir, "mascot-home-window.png") }
            assertTrue("Home screen capture failed", device.takeScreenshot(destination))
        }
    }

}
