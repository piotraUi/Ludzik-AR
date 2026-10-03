package com.ludzik.game.game

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ludzik.game.characters.World
import com.ludzik.game.render.FilamentCore
import com.ludzik.game.render.GameRenderer

/** Stan nakładki (HUD) czytany przez Compose; aktualizowany tylko przy zmianie wartości. */
class HudState {
    var loading by mutableFloatStateOf(0f)
    var loadingText by mutableStateOf("")
    var ready by mutableStateOf(false)
    var doodles by mutableIntStateOf(0)
    var things by mutableIntStateOf(0)
    var aimLabel by mutableStateOf<String?>(null)
    var walkPhase by mutableFloatStateOf(0f)
    var walkAmount by mutableFloatStateOf(0f)
}

/**
 * Gospodarz gry: SurfaceView z Filament, pętla klatek (Choreographer) i most do Compose.
 * Logika, fizyka i renderowanie działają na wątku głównym, jedno po drugim.
 */
class GameHost(
    private val context: Context,
    val game: GameController,
    safeMode: Boolean,
    /** Wywoływane zamiast wywalenia aplikacji, gdy w pętli gry poleci wyjątek. */
    private val onError: (Throwable) -> Unit,
) : Choreographer.FrameCallback {
    val surfaceView = SurfaceView(context)
    private val core = FilamentCore(context, surfaceView, safeMode)
    private val renderer = GameRenderer(core, game)
    val hud = HudState()

    private var running = false
    private var lastNs = 0L

    val aspect get() = core.aspect

    fun resume() {
        if (running) return
        running = true
        lastNs = 0L
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun pause() {
        running = false
        Choreographer.getInstance().removeFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!running) return
        Choreographer.getInstance().postFrameCallback(this)
        try {
            frame(frameTimeNanos)
        } catch (t: Throwable) {
            pause()
            onError(t)
        }
    }

    private fun frame(frameTimeNanos: Long) {
        if (!renderer.isLoaded) {
            renderer.loadStep()
            hud.loading = renderer.progress
            hud.loadingText = renderer.currentTask
            if (renderer.isLoaded) hud.ready = true
        } else {
            val dt = if (lastNs == 0L) 0f else ((frameTimeNanos - lastNs) / 1e9f).coerceIn(0f, 0.05f)
            game.step(dt)
            publishHud()
        }
        lastNs = frameTimeNanos
        renderer.update()
        core.render(frameTimeNanos)
        // Pierwsze klatki pełnej sceny dotarły na ekran — start silnika się udał.
        if (renderer.isLoaded && core.framesPresented > 30 && !startConfirmed) {
            startConfirmed = true
            StartupGuard.confirm(context)
        }
    }

    private var startConfirmed = false

    private fun publishHud() {
        if (hud.doodles != game.world.characters.size) hud.doodles = game.world.characters.size
        if (hud.things != game.physics.bodies.size) hud.things = game.physics.bodies.size
        val label = when (val t = game.aim()) {
            is AimTarget.Doodle -> t.character.kind.displayName
            is AimTarget.Body -> t.body.spec.name
            else -> null
        }
        if (hud.aimLabel != label) hud.aimLabel = label
        hud.walkPhase = game.player.walkPhase
        hud.walkAmount = game.player.walkAmount
    }

    /** Zrzut tego, co widać na SurfaceView (PixelCopy działa od Androida 7). */
    fun capture(onResult: (Bitmap?) -> Unit) {
        val w = surfaceView.width
        val h = surfaceView.height
        if (w <= 0 || h <= 0) return onResult(null)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        PixelCopy.request(surfaceView, bmp, { result ->
            onResult(if (result == PixelCopy.SUCCESS) bmp else null)
        }, Handler(Looper.getMainLooper()))
    }

    fun destroy() {
        pause()
        renderer.destroy()
        core.destroy()
    }

    companion object {
        const val MAX_DOODLES = World.MAX_CHARACTERS
    }
}

/**
 * Znacznik „gra właśnie startuje”. Jeśli przy następnym uruchomieniu wciąż istnieje,
 * poprzedni start silnika 3D się wywalił — proponujemy tryb bezpieczny.
 */
object StartupGuard {
    private fun file(context: Context) = java.io.File(context.filesDir, "game_starting")

    fun begin(context: Context) = file(context).writeText(System.currentTimeMillis().toString())
    fun confirm(context: Context) {
        file(context).delete()
    }
    fun crashedLastTime(context: Context) = file(context).exists()
}
