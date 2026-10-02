package com.tatsu.homehub.ui

import android.content.Context
import android.graphics.Outline
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider
import android.provider.Settings
import com.google.android.filament.*
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.*
import com.tatsu.homehub.voice.VoicePhase
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A real Android texture surface. No WebView, Javascript or network in this renderer. */
internal class NativeMascotView(context: Context) : TextureView(context), Choreographer.FrameCallback {
    var onRendered: (() -> Unit)? = null
    var onFailure: ((String) -> Unit)? = null
    var frames = 0L
        private set
    var phase = VoicePhase.IDLE
        private set
    var running = false
        private set
    var modelLoaded = false
        private set
    private var disposed = false
    private var previousFrame = 0L
    private var elapsed = 0f
    private var animation = 0
    private var previousAnimation = -1
    private var previousTime = 0f
    private var transition = 1f
    private val choreographer = Choreographer.getInstance()
    private lateinit var engine: Engine
    private lateinit var renderer: Renderer
    private lateinit var scene: Scene
    private lateinit var filamentView: com.google.android.filament.View
    private lateinit var camera: Camera
    private lateinit var provider: UbershaderProvider
    private lateinit var assetLoader: AssetLoader
    private lateinit var resourceLoader: ResourceLoader
    private lateinit var uiHelper: UiHelper
    private var asset: FilamentAsset? = null
    private var indirect: IndirectLight? = null
    private var swapChain: SwapChain? = null
    private val lightEntities = mutableListOf<Int>()
    private val clips = mutableMapOf<String, Int>()
    private var viewportWidth = 1
    private var viewportHeight = 1

    init {
        isOpaque = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isFocusable = false
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, minOf(view.width, view.height) * .1964f)
            }
        }
        runCatching { initialize() }.onFailure { error ->
            release()
            post { onFailure?.invoke(error.message ?: "Native 3D initialization failed") }
        }
    }

    private fun initialize() {
        Gltfio.init()
        engine = Engine.create(Engine.Backend.OPENGL)
        renderer = engine.createRenderer()
        renderer.clearOptions = Renderer.ClearOptions().apply {
            clear = true
            clearColor = floatArrayOf(0f, 0f, 0f, 0f)
        }
        scene = engine.createScene()
        camera = engine.createCamera(EntityManager.get().create()).apply {
            setExposure(16f, 1f / 125f, 100f)
            lookAt(0.0, 2.25, 7.6, 0.0, 1.42, 0.0)
        }
        filamentView = engine.createView().apply {
            this.scene = this@NativeMascotView.scene
            this.camera = this@NativeMascotView.camera
            blendMode = com.google.android.filament.View.BlendMode.TRANSLUCENT
            isShadowingEnabled = false
            antiAliasing = com.google.android.filament.View.AntiAliasing.FXAA
        }
        val irradiance = FloatArray(27).apply { this[0] = .9f; this[1] = .94f; this[2] = 1f }
        indirect = IndirectLight.Builder().irradiance(3, irradiance).intensity(30_000f).build(engine)
        scene.indirectLight = indirect
        addLight(-.45f, -.65f, -.7f, 1f, .965f, .925f, 65_000f)
        addLight(.55f, -.3f, -.6f, .8f, .9f, 1f, 18_000f)
        provider = UbershaderProvider(engine)
        assetLoader = AssetLoader(engine, provider, EntityManager.get())
        resourceLoader = ResourceLoader(engine)
        val bytes = context.assets.open("mascot-native/tatsu.glb").use { it.readBytes() }
        val buffer = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply { flip() }
        asset = checkNotNull(assetLoader.createAsset(buffer)) { "Invalid mascot GLB" }
        resourceLoader.loadResources(asset!!)
        scene.addEntities(asset!!.entities)
        val animator = asset!!.instance.animator
        for (i in 0 until animator.animationCount) clips[animator.getAnimationName(i)] = i
        check(VoicePhase.entries.all { clips.containsKey(it.name) }) { "Missing voice animation" }
        animation = clips.getValue(phase.name)
        asset!!.releaseSourceData()
        modelLoaded = true
        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
            isOpaque = false
            renderCallback = object : UiHelper.RendererCallback {
                override fun onNativeWindowChanged(surface: Surface) {
                    swapChain?.let { engine.destroySwapChain(it) }
                    swapChain = engine.createSwapChain(surface, SwapChain.CONFIG_TRANSPARENT)
                    schedule()
                }
                override fun onDetachedFromSurface() {
                    swapChain?.let { engine.destroySwapChain(it); engine.flushAndWait() }
                    swapChain = null
                }
                override fun onResized(width: Int, height: Int) {
                    viewportWidth = width.coerceAtLeast(1)
                    viewportHeight = height.coerceAtLeast(1)
                    filamentView.viewport = Viewport(0, 0, viewportWidth, viewportHeight)
                    val aspect = viewportWidth.toDouble() / viewportHeight
                    camera.setProjection(Camera.Projection.ORTHO, -2.175 * aspect, 2.175 * aspect, -2.175, 2.175, .1, 30.0)
                    invalidateOutline()
                }
            }
            attachTo(this@NativeMascotView)
        }
    }

    private fun addLight(dx: Float, dy: Float, dz: Float, r: Float, g: Float, b: Float, intensity: Float) {
        val entity = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.DIRECTIONAL).direction(dx, dy, dz)
            .color(r, g, b).intensity(intensity).castShadows(false).build(engine, entity)
        scene.addEntity(entity)
        lightEntities.add(entity)
    }

    fun configure(nextPhase: VoicePhase, active: Boolean) {
        if (disposed) return
        if (phase != nextPhase && modelLoaded) {
            previousAnimation = animation
            previousTime = elapsed
            animation = clips.getValue(nextPhase.name)
            elapsed = 0f
            transition = 0f
        }
        phase = nextPhase
        running = active
        previousFrame = 0L
        choreographer.removeFrameCallback(this)
        schedule()
    }

    private fun schedule() {
        if (!disposed && running && modelLoaded && swapChain != null) {
            choreographer.removeFrameCallback(this)
            choreographer.postFrameCallback(this)
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (disposed || !running || swapChain == null) return
        if (previousFrame == 0L || frameTimeNanos - previousFrame >= 41_666_666L) {
            val delta = if (previousFrame == 0L) 0f else ((frameTimeNanos - previousFrame) / 1_000_000_000f).coerceAtMost(.1f)
            previousFrame = frameTimeNanos
            val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            if (!reduced) elapsed += delta
            transition = (transition + delta / .28f).coerceAtMost(1f)
            val animator = asset!!.instance.animator
            animator.applyAnimation(animation, if (reduced) 0f else elapsed)
            if (previousAnimation >= 0 && transition < 1f && !reduced) animator.applyCrossFade(previousAnimation, previousTime, transition)
            animator.updateBoneMatrices()
            runCatching {
                if (renderer.beginFrame(swapChain!!, frameTimeNanos)) {
                    renderer.render(filamentView)
                    renderer.endFrame()
                    frames++
                    if (frames == 2L) onRendered?.invoke()
                }
            }.onFailure { error ->
                running = false
                onFailure?.invoke(error.message ?: "Native rendering failed")
            }
            if (reduced && frames >= 3L) return
        }
        schedule()
    }

    fun release() {
        if (disposed) return
        disposed = true
        running = false
        choreographer.removeFrameCallback(this)
        if (::uiHelper.isInitialized) uiHelper.detach()
        if (!::engine.isInitialized) return
        asset?.let { if (::scene.isInitialized) scene.removeEntities(it.entities); assetLoader.destroyAsset(it) }
        asset = null
        if (::resourceLoader.isInitialized) resourceLoader.destroy()
        if (::assetLoader.isInitialized) assetLoader.destroy()
        if (::provider.isInitialized) { provider.destroyMaterials(); provider.destroy() }
        indirect?.let { engine.destroyIndirectLight(it) }
        lightEntities.forEach { engine.destroyEntity(it); EntityManager.get().destroy(it) }
        if (::camera.isInitialized) { engine.destroyCameraComponent(camera.entity); EntityManager.get().destroy(camera.entity) }
        if (::filamentView.isInitialized) engine.destroyView(filamentView)
        if (::scene.isInitialized) engine.destroyScene(scene)
        if (::renderer.isInitialized) engine.destroyRenderer(renderer)
        engine.destroy()
    }
    override fun onDetachedFromWindow() { release(); super.onDetachedFromWindow() }
}
