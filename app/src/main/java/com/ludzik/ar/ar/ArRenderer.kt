package com.ludzik.ar.ar

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.google.ar.core.Camera
import com.google.ar.core.Frame
import com.google.ar.core.InstantPlacementPoint
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
import com.ludzik.ar.characters.WalkSurface
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
enum class TrackingHint { NONE, STARTING, SEARCHING_PLANE, ONLY_WALLS, TAP_ANYWAY, PLACE, LOST_MOTION, LOST_DARK, LOST_FEATURES, LOST_OTHER }

interface ArRendererListener {
    fun onHint(hint: TrackingHint)
    fun onCharacterCount(count: Int)
    fun onMessage(text: String)
    fun onPhoto(pixels: ByteBuffer, width: Int, height: Int)
    fun onError(text: String)
    fun onDebug(text: String)
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

    /** Czy sesja ma włączone Instant Placement (stawianie przed wykryciem podłogi). */
    @Volatile
    var instantPlacement = false

    @Volatile
    var selectedKind: CharacterKind = CharacterRegistry.kinds.first()

    val rotationHelper = DisplayRotationHelper(context)
    val recorder = VideoRecorder(context)

    private val background = BackgroundRenderer()
    private val planes = PlaneRenderer()
    private val sprites = SpriteRenderer()
    private val lines = LineRenderer()
    private val planeSurfaces = PlaneSurfaces()

    /**
     * „Podłoga na oko”: gdy ARCore nie znalazł jeszcze płaszczyzny, stawiamy postać na punkcie
     * Instant Placement i tworzymy wokół niego wirtualny kwadrat podłogi.
     */
    private class VirtualFloor(val point: InstantPlacementPoint, val surface: WalkSurface)
    private val virtualFloors = ArrayList<VirtualFloor>()
    private var nextVirtualId = 1_000_000
    private val allSurfaces = ArrayList<WalkSurface>()
    private var searchingSinceNs = 0L

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

    /** Podgląd diagnostyczny (śledzenie, płaszczyzny, wynik ostatniego dotknięcia). */
    @Volatile
    var debugEnabled = false
    private var lastTapResult = "—"
    private var lastDebugNs = 0L
    private var trackingFrames = 0
    private var totalFrames = 0

    fun queueTap(x: Float, y: Float) {
        // trzeci element: czas dotknięcia w ms, żeby nie gubić dotknięć przy chwilowej utracie śledzenia
        taps.add(floatArrayOf(x, y, (System.nanoTime() / 1_000_000L % 10_000_000L).toFloat()))
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

        val realSurfaces = planeSurfaces.update(session.getAllTrackables(Plane::class.java))
        updateVirtualFloors()
        allSurfaces.clear()
        allSurfaces.addAll(realSurfaces)
        for (v in virtualFloors) if (v.surface.alive) allSurfaces.add(v.surface)
        world.surfaces = allSurfaces

        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 0f else ((now - lastNs) / 1e9f).coerceIn(0f, 0.05f)
        lastNs = now
        if (tracking) {
            processTaps(frame)
            world.update(dt)
        } else {
            dropStaleTaps()
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

    /**
     * Gdy ARCore na chwilę traci śledzenie, dotknięcia czekają do 1,5 s zamiast znikać.
     * Starsze odrzucamy z wyjaśnieniem.
     */
    private fun dropStaleTaps() {
        val nowMs = (System.nanoTime() / 1_000_000L % 10_000_000L).toFloat()
        var dropped = false
        while (true) {
            val t = taps.peek() ?: break
            val age = nowMs - t[2]
            if (age in 0f..1500f) break
            taps.poll()
            dropped = true
        }
        if (dropped) {
            lastTapResult = "odrzucone: brak śledzenia"
            listener.onMessage("Telefon zgubił trop — poruszaj nim powoli i dotknij jeszcze raz.")
        }
    }

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
        if (world.isFull) {
            lastTapResult = "limit postaci"
            listener.onMessage("W zeszycie mieści się najwyżej ${World.MAX_CHARACTERS} postaci!")
            return
        }
        val hits = frame.hitTest(x, y)
        // 1) trafienie dokładnie w kratkę; 2) trafienie w płaszczyznę tuż obok kratki
        //    (na początku wykryte fragmenty są małe, więc nie wymagamy idealnej celności)
        for (strict in booleanArrayOf(true, false)) {
            for (hit in hits) {
                val plane = hit.trackable as? Plane ?: continue
                if (plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING) continue
                val surface = planeSurfaces.surfaceFor(plane) ?: continue
                val pose = hit.hitPose
                val p = Vec3(pose.tx(), pose.ty(), pose.tz())
                val ok = if (strict) plane.isPoseInPolygon(pose) else surface.contains(p, -NEAR_PLANE_TOLERANCE)
                if (!ok) continue
                world.spawn(selectedKind, p, surface)
                lastTapResult = if (strict) "postawiono na kratce" else "postawiono obok kratki"
                return
            }
        }
        if (instantPlacement && placeOnVirtualFloor(frame, x, y)) {
            lastTapResult = "postawiono na oko"
            return
        }
        val types = hits.mapNotNull { (it.trackable as? Plane)?.type?.name?.lowercase() ?: it.trackable.javaClass.simpleName }
        lastTapResult = "pudło (trafienia: ${types.ifEmpty { listOf("brak") }.joinToString()})"
        listener.onMessage(
            if (world.surfaces.any { !it.isVertical }) "Dotknij kratkowanej podłogi, żeby postawić postać."
            else "Jeszcze nie widzę podłogi — powoli przesuwaj telefon nad podłogą."
        )
    }

    private fun placeOnVirtualFloor(frame: Frame, x: Float, y: Float): Boolean {
        val hit = try {
            frame.hitTestInstantPlacement(x, y, APPROX_FLOOR_DISTANCE).firstOrNull()
        } catch (e: Exception) {
            Log.w(TAG, "Instant Placement niedostępne", e)
            null
        } ?: return false
        val point = hit.trackable as? InstantPlacementPoint ?: return false
        if (world.isFull) {
            listener.onMessage("W zeszycie mieści się najwyżej ${World.MAX_CHARACTERS} postaci!")
            return true
        }
        val pose = hit.hitPose
        val p = Vec3(pose.tx(), pose.ty(), pose.tz())
        var floor = virtualFloors.firstOrNull { it.surface.alive && it.surface.contains(p, 0.05f) && kotlin.math.abs(it.surface.height - p.y) < 0.2f }
        if (floor == null) {
            if (virtualFloors.size >= MAX_VIRTUAL_FLOORS) {
                virtualFloors.removeAt(0).surface.alive = false
            }
            floor = VirtualFloor(point, WalkSurface(nextVirtualId++, false))
            shapeVirtualFloor(floor.surface, p)
            virtualFloors.add(floor)
            listener.onMessage("Stawiam na oko — gdy telefon rozpozna podłogę, ludzik się dopasuje.")
        }
        world.spawn(selectedKind, p.withY(floor.surface.height), floor.surface)
        return true
    }

    private fun shapeVirtualFloor(s: WalkSurface, c: Vec3) {
        val h = VIRTUAL_FLOOR_HALF
        s.update(
            listOf(Vec3(c.x - h, c.y, c.z - h), Vec3(c.x + h, c.y, c.z - h), Vec3(c.x + h, c.y, c.z + h), Vec3(c.x - h, c.y, c.z + h)),
            c, Vec3.UP,
        )
    }

    /** Punkt Instant Placement doprecyzowuje pozycję z czasem — przesuwamy za nim wirtualną podłogę. */
    private fun updateVirtualFloors() {
        val iter = virtualFloors.iterator()
        while (iter.hasNext()) {
            val v = iter.next()
            when (v.point.trackingState) {
                TrackingState.STOPPED -> {
                    v.surface.alive = false
                    iter.remove()
                }
                TrackingState.TRACKING -> {
                    val pose = v.point.pose
                    val c = Vec3(pose.tx(), pose.ty(), pose.tz())
                    if (c.distanceTo(v.surface.center) > 0.005f) shapeVirtualFloor(v.surface, c)
                }
                else -> Unit
            }
        }
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
            world.surfaces.none { !it.isVertical } -> {
                val now = System.nanoTime()
                if (searchingSinceNs == 0L) searchingSinceNs = now
                val searchingFor = (now - searchingSinceNs) / 1e9f
                when {
                    instantPlacement && searchingFor > 6f -> TrackingHint.TAP_ANYWAY
                    world.surfaces.isNotEmpty() -> TrackingHint.ONLY_WALLS
                    else -> TrackingHint.SEARCHING_PLANE
                }
            }
            world.characters.isEmpty() -> TrackingHint.PLACE
            else -> TrackingHint.NONE
        }
        if (hint != lastHint) {
            lastHint = hint
            listener.onHint(hint)
        }
        totalFrames++
        if (tracking) trackingFrames++
        val nowNs = System.nanoTime()
        if (debugEnabled && nowNs - lastDebugNs > 250_000_000L) {
            lastDebugNs = nowNs
            val h = world.surfaces.count { !it.isVertical }
            val v = world.surfaces.count { it.isVertical }
            val pct = if (totalFrames > 0) trackingFrames * 100 / totalFrames else 0
            listener.onDebug(
                "śledzenie: ${camera.trackingState.name.lowercase()} (${camera.trackingFailureReason.name.lowercase()}), " +
                    "OK $pct% klatek\npłaszczyzny: $h poziome, $v pionowe, wirtualne: ${virtualFloors.size}\n" +
                    "ostatnie dotknięcie: $lastTapResult"
            )
        }
        if (world.characters.size != lastCount) {
            lastCount = world.characters.size
            listener.onCharacterCount(lastCount)
        }
    }

    fun clearWorld() = runOnGl {
        world.clear()
        for (v in virtualFloors) v.surface.alive = false
        virtualFloors.clear()
        searchingSinceNs = 0L
    }

    /** Po wznowieniu sesji ARCore stare płaszczyzny tracą ważność. */
    fun onSessionResumed() = runOnGl {
        cameraTextureSet = false
        lastNs = 0L
    }

    private companion object {
        const val TAG = "ArRenderer"
        const val APPROX_FLOOR_DISTANCE = 1.2f
        const val VIRTUAL_FLOOR_HALF = 0.6f
        const val MAX_VIRTUAL_FLOORS = 3
        const val NEAR_PLANE_TOLERANCE = 0.35f
    }
}
