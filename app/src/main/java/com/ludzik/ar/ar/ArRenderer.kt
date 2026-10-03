package com.ludzik.ar.ar

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.SessionPausedException
import com.ludzik.ar.ar.gl.BackgroundRenderer
import com.ludzik.ar.ar.gl.GlUtil
import com.ludzik.ar.ar.gl.LineRenderer
import com.ludzik.ar.ar.gl.PlaneRenderer
import com.ludzik.ar.ar.gl.SpriteRenderer
import com.ludzik.ar.capture.VideoRecorder
import com.ludzik.ar.characters.Character
import com.ludzik.ar.characters.CharacterKind
import com.ludzik.ar.characters.CharacterRegistry
import com.ludzik.ar.characters.DecalType
import com.ludzik.ar.characters.Vec3
import com.ludzik.ar.characters.World
import com.ludzik.ar.characters.doodle.BubbleArt
import com.ludzik.ar.characters.doodle.DecalArt
import com.ludzik.ar.characters.doodle.SpriteAtlas
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/** Wskazówka dla użytkownika wyświetlana nad sceną. */
enum class TrackingHint { NONE, STARTING, SEARCHING_PLANE, PLACE, LOST_MOTION, LOST_DARK, LOST_FEATURES, LOST_OTHER }

interface ArRendererListener {
    fun onHint(hint: TrackingHint)
    fun onCharacterCount(count: Int)
    fun onMessage(text: String)
    fun onPhoto(pixels: ByteBuffer, width: Int, height: Int)
    fun onError(text: String)
}

/**
 * Renderer GL: kamera, płaszczyzny w kratkę, naklejki, linie, postacie-billboardy i dymki.
 * Tu też co klatkę aktualizujemy symulację świata — wszystko na jednym wątku, bez blokad.
 */
class ArRenderer(
    private val context: Context,
    private val world: World,
    private val listener: ArRendererListener,
) : GLSurfaceView.Renderer {

    @Volatile
    var session: Session? = null

    @Volatile
    var selectedKind: CharacterKind = CharacterRegistry.kinds.first()

    val rotationHelper = DisplayRotationHelper(context)
    val recorder = VideoRecorder(context)

    private val background = BackgroundRenderer()
    private val planes = PlaneRenderer()
    private val sprites = SpriteRenderer()
    private val lines = LineRenderer()
    private val planeSurfaces = PlaneSurfaces()

    private val atlases = HashMap<CharacterKind, Int>()
    private var decalTexture = 0
    private class BubbleTex(val texture: Int, val aspect: Float)
    private val bubbleTextures = HashMap<Long, BubbleTex>()

    private val taps = ConcurrentLinkedQueue<FloatArray>()
    private val photoRequested = AtomicBoolean(false)
    private val pendingGl = ConcurrentLinkedQueue<() -> Unit>()

    private val view = FloatArray(16)
    private val proj = FloatArray(16)
    private val viewProj = FloatArray(16)
    private val clip = FloatArray(4)
    private val tmp4 = FloatArray(4)
    private val uv = FloatArray(4)

    private var camPos = Vec3.ZERO
    private var camRight = Vec3(1f, 0f, 0f)
    private var camUp = Vec3.UP
    private var billUp = Vec3.UP
    private var billRight = Vec3(1f, 0f, 0f)

    private var width = 1
    private var height = 1
    private var cameraTextureSet = false
    private var lastNs = 0L
    private var lastHint: TrackingHint? = null
    private var lastCount = -1
    private var planeFade = 1f
    private val drawOrder = ArrayList<Character>()

    fun queueTap(x: Float, y: Float) {
        taps.add(floatArrayOf(x, y))
    }

    fun requestPhoto() = photoRequested.set(true)

    /** Zadanie do wykonania na wątku GL na początku następnej klatki. */
    fun runOnGl(block: () -> Unit) {
        pendingGl.add(block)
    }

    // ------------------------------------------------------------------ cykl GL

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.98f, 0.96f, 0.9f, 1f)
        background.create()
        planes.create()
        sprites.create()
        lines.create()
        cameraTextureSet = false
        // Nowy kontekst = stare tekstury przepadły.
        atlases.clear()
        bubbleTextures.clear()
        for (kind in CharacterRegistry.kinds) {
            val bmp = SpriteAtlas.build(kind)
            atlases[kind] = GlUtil.texture(bmp)
            bmp.recycle()
        }
        DecalArt.build().let {
            decalTexture = GlUtil.texture(it)
            it.recycle()
        }
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
        GLES20.glViewport(0, 0, w, h)
        rotationHelper.onSurfaceChanged(w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        while (true) (pendingGl.poll() ?: break).invoke()
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val session = session ?: return
        rotationHelper.updateSessionIfNeeded(session)
        if (!cameraTextureSet) {
            session.setCameraTextureName(background.textureId)
            cameraTextureSet = true
        }
        val frame = try {
            session.update()
        } catch (e: SessionPausedException) {
            return
        } catch (e: CameraNotAvailableException) {
            listener.onError("Kamera jest zajęta przez inną aplikację.")
            return
        } catch (e: Exception) {
            Log.e(TAG, "session.update", e)
            return
        }
        val camera = frame.camera
        background.updateGeometry(frame)
        val tracking = camera.trackingState == TrackingState.TRACKING
        if (tracking) updateCamera(camera)

        world.surfaces = planeSurfaces.update(session.getAllTrackables(Plane::class.java))

        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 0f else ((now - lastNs) / 1e9f).coerceIn(0f, 0.05f)
        lastNs = now
        if (tracking) {
            processTaps(frame)
            world.update(dt)
        } else {
            taps.clear()
        }
        updateBubbleTextures()
        planeFade += ((if (world.characters.isEmpty()) 1f else 0.45f) - planeFade) * (dt * 2f).coerceAtMost(1f)

        renderScene(frame, tracking)

        if (photoRequested.getAndSet(false)) {
            val buf = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
            listener.onPhoto(buf, width, height)
        }
        if (recorder.isRecording) {
            recorder.captureFrame(now) { w, h ->
                GLES20.glViewport(0, 0, w, h)
                renderScene(frame, tracking)
            }
            GLES20.glViewport(0, 0, width, height)
        }
        publishStatus(camera, tracking)
    }

    private fun updateCamera(camera: Camera) {
        camera.getProjectionMatrix(proj, 0, 0.03f, 60f)
        camera.getViewMatrix(view, 0)
        Matrix.multiplyMM(viewProj, 0, proj, 0, view, 0)
        val pose = camera.displayOrientedPose
        camPos = Vec3(pose.tx(), pose.ty(), pose.tz())
        // Wiersze macierzy widoku = osie kamery w układzie świata.
        camRight = Vec3(view[0], view[4], view[8]).normalized()
        camUp = Vec3(view[1], view[5], view[9]).normalized()
        val toViewer = Vec3(view[2], view[6], view[10]).normalized()
        // Billboard z poziomą osią X (bez przechyłu), zwrócony do kamery.
        val rh = camRight.horizontal()
        billRight = if (rh.length() > 0.2f) rh.normalized() else camRight
        billUp = toViewer.cross(billRight).normalized()
        if (billUp == Vec3.ZERO) billUp = Vec3.UP
    }

    // ------------------------------------------------------------------ rysowanie

    private fun renderScene(frame: Frame, tracking: Boolean) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        background.draw(frame)
        if (!tracking) return
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        planes.draw(world.surfaces, viewProj, planeFade)

        // Płaskie naklejki: cienie postaci + plamy/okruchy
        sprites.begin(viewProj)
        for (c in world.characters) {
            val s = c.surface ?: continue
            if (c.alpha <= 0f) continue
            val above = (c.position.y - s.height).coerceAtLeast(0f)
            val size = c.kind.spriteSizeMeters * 0.5f / (1f + above * 3f)
            val groundY = if (c.position.y >= s.height - 0.05f) s.height else c.position.y
            drawDecal(DecalType.SHADOW, Vec3(c.position.x, groundY, c.position.z), size, 0f, c.alpha * 0.9f)
        }
        for (d in world.decals) drawDecal(d.type, d.position, d.size, d.rotation, d.alpha)
        sprites.end()

        lines.draw(world.strokes, viewProj, camPos)

        // Postacie od najdalszej do najbliższej (przezroczystość bez bufora głębi).
        drawOrder.clear()
        drawOrder.addAll(world.characters)
        drawOrder.sortByDescending { it.position.distanceTo(camPos) }
        sprites.begin(viewProj)
        for (c in drawOrder) drawCharacter(c)
        for (b in world.bubbles) {
            val tex = bubbleTextures[b.id] ?: continue
            val owner = b.owner
            val dist = owner.position.distanceTo(camPos)
            val w = 0.17f * (dist / 0.9f).coerceIn(0.8f, 2.6f)
            val h = w / tex.aspect
            val size = owner.kind.spriteSizeMeters
            val origin = owner.position + billUp * (size * 0.92f * owner.scaleY) + camRight * (w * 0.32f)
            uv[0] = 0f; uv[1] = 0f; uv[2] = 1f; uv[3] = 1f
            sprites.draw(tex.texture, origin, camRight, camUp, w, h, uv, false, b.alpha)
        }
        sprites.end()
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun drawDecal(type: DecalType, center: Vec3, size: Float, rotation: Float, alpha: Float) {
        val r = Vec3(cos(rotation), 0f, sin(rotation))
        val u = Vec3(-sin(rotation), 0f, cos(rotation))
        // podniesione o 2 mm, żeby nie migotało z płaszczyzną
        val origin = Vec3(center.x, center.y + 0.002f, center.z) - u * (size / 2f)
        DecalArt.uv(type, uv)
        sprites.draw(decalTexture, origin, r, u, size, size, uv, false, alpha)
    }

    private fun drawCharacter(c: Character) {
        if (c.alpha <= 0f) return
        val tex = atlases[c.kind] ?: return
        val size = c.kind.spriteSizeMeters
        val w = size * c.scaleX
        val h = size * c.scaleY
        SpriteAtlas.uv(c.kind, c.anim, c.animFrame, uv)
        // Stopy w klatce są na y≈0.95, więc obniżamy prostokąt o 5% wysokości.
        val origin = c.position - billUp * (h * 0.05f)
        val flip = c.heading.dot(billRight) < 0f
        sprites.draw(tex, origin, billRight, billUp, w, h, uv, flip, c.alpha)
    }

    private fun updateBubbleTextures() {
        val iter = bubbleTextures.entries.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            if (world.bubbles.none { it.id == e.key }) {
                GlUtil.deleteTexture(e.value.texture)
                iter.remove()
            }
        }
        for (b in world.bubbles) {
            if (bubbleTextures.containsKey(b.id)) continue
            val art = BubbleArt.render(b.text, b.id)
            bubbleTextures[b.id] = BubbleTex(GlUtil.texture(art.bitmap), art.aspect)
            art.bitmap.recycle()
        }
    }

    // ------------------------------------------------------------------ dotyk

    private fun processTaps(frame: Frame) {
        while (true) {
            val tap = taps.poll() ?: break
            val hit = pickCharacter(tap[0], tap[1])
            if (hit != null) {
                hit.onTouched(world)
                continue
            }
            placeCharacter(frame, tap[0], tap[1])
        }
    }

    /** Rzutuje stopy i głowę postaci na ekran i sprawdza, czy palec trafił w prostokąt. */
    private fun pickCharacter(x: Float, y: Float): Character? {
        var best: Character? = null
        var bestDepth = Float.MAX_VALUE
        val pad = 28f * context.resources.displayMetrics.density
        for (c in world.characters) {
            if (c.isErased || c.alpha < 0.3f) continue
            val size = c.kind.spriteSizeMeters
            val feet = project(c.position) ?: continue
            val head = project(c.position + billUp * (size * 0.9f)) ?: continue
            val hPx = kotlin.math.abs(feet[1] - head[1]).coerceAtLeast(1f)
            val half = hPx * 0.45f + pad
            val minY = minOf(feet[1], head[1]) - pad
            val maxY = maxOf(feet[1], head[1]) + pad
            val cx = (feet[0] + head[0]) / 2f
            if (x in (cx - half)..(cx + half) && y in minY..maxY && feet[2] < bestDepth) {
                bestDepth = feet[2]
                best = c
            }
        }
        return best
    }

    /** Zwraca (x px, y px, głębokość) albo null, gdy punkt jest za kamerą. */
    private fun project(p: Vec3): FloatArray? {
        tmp4[0] = p.x; tmp4[1] = p.y; tmp4[2] = p.z; tmp4[3] = 1f
        Matrix.multiplyMV(clip, 0, viewProj, 0, tmp4, 0)
        if (clip[3] <= 0.01f) return null
        val nx = clip[0] / clip[3]
        val ny = clip[1] / clip[3]
        return floatArrayOf((nx + 1f) * 0.5f * width, (1f - ny) * 0.5f * height, clip[3])
    }

    private fun placeCharacter(frame: Frame, x: Float, y: Float) {
        for (hit in frame.hitTest(x, y)) {
            val plane = hit.trackable as? Plane ?: continue
            if (plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING) continue
            if (!plane.isPoseInPolygon(hit.hitPose)) continue
            val surface = planeSurfaces.surfaceFor(plane) ?: continue
            if (world.isFull) {
                listener.onMessage("W zeszycie mieści się najwyżej ${World.MAX_CHARACTERS} postaci!")
                return
            }
            val pose = hit.hitPose
            world.spawn(selectedKind, Vec3(pose.tx(), pose.ty(), pose.tz()), surface)
            return
        }
        if (world.surfaces.isNotEmpty()) listener.onMessage("Dotknij kratkowanej powierzchni, żeby postawić postać.")
    }

    // ------------------------------------------------------------------ status

    private fun publishStatus(camera: Camera, tracking: Boolean) {
        val hint = when {
            !tracking -> when (camera.trackingFailureReason) {
                TrackingFailureReason.EXCESSIVE_MOTION -> TrackingHint.LOST_MOTION
                TrackingFailureReason.INSUFFICIENT_LIGHT -> TrackingHint.LOST_DARK
                TrackingFailureReason.INSUFFICIENT_FEATURES -> TrackingHint.LOST_FEATURES
                TrackingFailureReason.NONE -> TrackingHint.STARTING
                else -> TrackingHint.LOST_OTHER
            }
            world.surfaces.none { !it.isVertical } -> TrackingHint.SEARCHING_PLANE
            world.characters.isEmpty() -> TrackingHint.PLACE
            else -> TrackingHint.NONE
        }
        if (hint != lastHint) {
            lastHint = hint
            listener.onHint(hint)
        }
        if (world.characters.size != lastCount) {
            lastCount = world.characters.size
            listener.onCharacterCount(lastCount)
        }
    }

    fun clearWorld() = runOnGl {
        world.clear()
    }

    /** Po wznowieniu sesji ARCore stare płaszczyzny tracą ważność. */
    fun onSessionResumed() = runOnGl {
        cameraTextureSet = false
        lastNs = 0L
    }

    private companion object {
        const val TAG = "ArRenderer"
    }
}
