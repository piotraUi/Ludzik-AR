package com.ludzik.game.render

import android.content.Context
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Podstawa Filament: silnik, scena, kamera, łańcuch wymiany na SurfaceView i ładowarki glTF.
 * Wszystkie wywołania z wątku głównego (tak jak pętla Choreographera).
 */
/**
 * @param safeMode bez SSAO, bloomu, cieni i dynamicznej rozdzielczości — na wypadek problemów
 *                 ze sterownikiem grafiki.
 */
class FilamentCore(private val context: Context, private val surfaceView: SurfaceView, val safeMode: Boolean = false) {

    val engine: Engine = Engine.create()
    val renderer: Renderer = engine.createRenderer()
    val scene: Scene = engine.createScene()
    val view: View = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    val camera: Camera = engine.createCamera(cameraEntity)

    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val displayHelper = DisplayHelper(context)
    private var swapChain: SwapChain? = null

    private val materialProvider = UbershaderProvider(engine)
    val assetLoader = AssetLoader(engine, materialProvider, EntityManager.get())
    val resourceLoader = ResourceLoader(engine, true)

    var aspect = 16f / 9f
        private set
    var width = 1
        private set
    var height = 1
        private set

    init {
        view.scene = scene
        view.camera = camera
        configureQuality()
        camera.setExposure(16f, 1f / 125f, 100f)

        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
                displayHelper.attach(renderer, surfaceView.display)
            }

            override fun onDetachedFromSurface() {
                displayHelper.detach()
                swapChain?.let {
                    engine.destroySwapChain(it)
                    // Surface może zniknąć zaraz po tym wywołaniu — czekamy, aż silnik przestanie jej używać.
                    engine.flushAndWait()
                }
                swapChain = null
            }

            override fun onResized(width: Int, height: Int) {
                this@FilamentCore.width = width
                this@FilamentCore.height = height
                aspect = width.toFloat() / height.coerceAtLeast(1)
                view.viewport = Viewport(0, 0, width, height)
                updateProjection()
            }
        }
        uiHelper.attachTo(surfaceView)
    }

    /** Ustawienia „realistyczne, ale na telefon”: AgX, SSAO, delikatny bloom, miękkie cienie. */
    private fun configureQuality() {
        view.antiAliasing = View.AntiAliasing.FXAA
        view.colorGrading = ColorGrading.Builder()
            .toneMapper(ToneMapper.Agx())
            .build(engine)
        if (safeMode) {
            view.setShadowingEnabled(false)
            return
        }
        view.ambientOcclusionOptions = view.ambientOcclusionOptions.apply {
            enabled = true
            radius = 0.35f
            intensity = 1.1f
            quality = View.QualityLevel.MEDIUM
        }
        view.bloomOptions = view.bloomOptions.apply {
            enabled = true
            strength = 0.07f
            lensFlare = false
        }
        view.dynamicResolutionOptions = view.dynamicResolutionOptions.apply {
            enabled = true
            minScale = 0.6f
            quality = View.QualityLevel.MEDIUM
        }
        view.setShadowType(View.ShadowType.DPCF)
    }

    fun updateProjection() {
        camera.setProjection(FOV_VERTICAL, aspect.toDouble(), 0.03, 40.0, Camera.Fov.VERTICAL)
    }

    /** Czy ostatnia klatka faktycznie trafiła na ekran. */
    var framesPresented = 0
        private set

    fun render(frameTimeNanos: Long) {
        val sc = swapChain ?: return
        if (!uiHelper.isReadyToRender || width <= 1 || height <= 1) return
        if (renderer.beginFrame(sc, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
            framesPresented++
        }
    }

    fun readAsset(path: String): ByteBuffer {
        val bytes = context.assets.open(path).use { it.readBytes() }
        return ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
            put(bytes)
            flip()
        }
    }

    fun destroy() {
        uiHelper.detach()
        displayHelper.detach()
        swapChain?.let { engine.destroySwapChain(it) }
        swapChain = null
        resourceLoader.destroy()
        assetLoader.destroy()
        materialProvider.destroyMaterials()
        materialProvider.destroy()
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        engine.destroy()
    }

    companion object {
        const val FOV_VERTICAL = 62.0

        init {
            // ładuje filament-jni, gltfio-jni i filament-utils-jni
            Gltfio.init()
            Utils.init()
        }
    }
}
