package com.ludzik.game.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.opengl.Matrix
import android.util.Log
import com.google.android.filament.EntityManager
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Texture
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.FilamentInstance
import com.google.android.filament.utils.HDRLoader
import com.google.android.filament.utils.IBLPrefilterContext
import com.ludzik.game.characters.CharacterKind
import com.ludzik.game.characters.CharacterRegistry
import com.ludzik.game.characters.DecalType
import com.ludzik.game.characters.StrokeStyle
import com.ludzik.game.characters.Vec3
import com.ludzik.game.characters.doodle.BubbleArt
import com.ludzik.game.characters.doodle.DecalArt
import com.ludzik.game.characters.doodle.SpriteAtlas
import com.ludzik.game.game.GameController
import com.ludzik.game.physics.PhysicsBody
import com.ludzik.game.scene.CharacterSpace
import com.ludzik.game.scene.RoomLayout
import com.ludzik.game.scene.SpawnableSpec
import java.nio.ByteBuffer
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Buduje realistyczną scenę w Filament i co klatkę przepisuje do niej stan gry:
 * kamerę gracza, przedmioty z fizyki i rysunkowe ludziki (billboardy).
 *
 * Ładowanie jest podzielone na kroki wykonywane po jednym na klatkę ([loadStep]),
 * żeby ekran ładowania mógł pokazywać postęp.
 */
class GameRenderer(private val core: FilamentCore, private val game: GameController) {

    private val engine = core.engine
    private val loadTasks = ArrayList<Pair<String, () -> Unit>>()
    private var loaded = 0
    val isLoaded get() = loaded >= loadTasks.size
    val progress get() = if (loadTasks.isEmpty()) 0f else loaded / loadTasks.size.toFloat()
    val currentTask get() = loadTasks.getOrNull(loaded)?.first ?: "Gotowe"

    val loadErrors = ArrayList<String>()
    private val assets = ArrayList<FilamentAsset>()
    private val textures = ArrayList<Texture>()
    private val entities = ArrayList<Int>()
    private var indirectLight: IndirectLight? = null

    private class Pool(val spec: SpawnableSpec, val asset: FilamentAsset, val instances: Array<FilamentInstance>) {
        val owner = arrayOfNulls<PhysicsBody>(instances.size)
    }
    private val pools = HashMap<SpawnableSpec, Pool>()

    private lateinit var billboard: ByteBuffer
    private val kindBatches = HashMap<CharacterKind, BillboardBatch>()
    private lateinit var decals: BillboardBatch
    private lateinit var lines: BillboardBatch
    private lateinit var bubbles: BillboardBatch
    private lateinit var bubbleAtlas: Texture
    private val bubbleSlots = HashMap<Long, Int>()
    private val bubbleAspect = HashMap<Long, Float>()

    private val tmp = FloatArray(16)
    private val tmp2 = FloatArray(16)
    private val uv = FloatArray(4)

    init {
        loadTasks += "Zapalam światło" to ::setupLighting
        loadTasks += "Stawiam ściany" to { addStatic("models/room.glb", Vec3.ZERO, 0f) }
        for (f in RoomLayout.furniture) {
            loadTasks += "Wnoszę meble" to { addStatic(f.file, f.position, f.rotationDeg) }
        }
        for (s in RoomLayout.spawnables) loadTasks += "Przygotowuję zabawki" to { createPool(s) }
        loadTasks += "Ostrzę ołówki" to ::createBatches
    }

    /** Wykonuje jeden krok ładowania. */
    fun loadStep() {
        val task = loadTasks.getOrNull(loaded) ?: return
        try {
            task.second()
        } catch (e: Exception) {
            // Pojedynczy brakujący model nie powinien zatrzymać gry.
            Log.e(TAG, "Błąd ładowania: ${task.first}", e)
            loadErrors += "${task.first}: ${e.message}"
        }
        loaded++
    }

    // ------------------------------------------------------------------ budowa sceny

    private fun setupLighting() {
        // Oświetlenie otoczenia z HDRI wnętrza (odbicia + światło rozproszone).
        val equirect = if (core.safeMode) null else HDRLoader.createTexture(engine, core.readAsset("envs/room.hdr"))
        if (equirect != null) {
            val ctx = IBLPrefilterContext(engine)
            val toCube = IBLPrefilterContext.EquirectangularToCubemap(ctx)
            val specular = IBLPrefilterContext.SpecularFilter(ctx)
            val cube = toCube.run(equirect)
            val reflections = specular.run(cube)
            engine.destroyTexture(equirect)
            engine.destroyTexture(cube)
            toCube.destroy()
            specular.destroy()
            ctx.destroy()
            textures += reflections
            indirectLight = IndirectLight.Builder()
                .reflections(reflections)
                .intensity(IBL_INTENSITY)
                .build(engine)
        } else {
            indirectLight = IndirectLight.Builder()
                .irradiance(1, floatArrayOf(0.75f, 0.73f, 0.72f))
                .intensity(IBL_INTENSITY)
                .build(engine)
        }
        core.scene.indirectLight = indirectLight

        // Słońce wpadające przez okno — ściany rzucają cień, więc jasna plama jest tylko pod oknem.
        val sun = EntityManager.get().create()
        val d = Vec3(-0.2f, -0.5f, 0.84f).normalized()
        LightManager.Builder(LightManager.Type.SUN)
            .color(1f, 0.95f, 0.88f)
            .intensity(SUN_LUX)
            .direction(d.x, d.y, d.z)
            .sunAngularRadius(1.5f)
            .castShadows(!core.safeMode)
            .shadowOptions(LightManager.ShadowOptions().apply {
                mapSize = 2048
                shadowCascades = 1
                shadowFar = 9f
                stable = true
            })
            .build(engine, sun)
        core.scene.addEntity(sun)
        entities += sun
    }

    private fun loadAsset(file: String): FilamentAsset? {
        val asset = core.assetLoader.createAsset(core.readAsset(file)) ?: run {
            Log.e(TAG, "Nie wczytano $file")
            return null
        }
        core.resourceLoader.loadResources(asset)
        asset.releaseSourceData()
        assets += asset
        return asset
    }

    private fun addStatic(file: String, position: Vec3, rotationDeg: Float) {
        val asset = loadAsset(file) ?: return
        val tm = engine.transformManager
        Matrix.setIdentityM(tmp, 0)
        Matrix.translateM(tmp, 0, position.x, position.y, position.z)
        Matrix.rotateM(tmp, 0, rotationDeg, 0f, 1f, 0f)
        tm.setTransform(tm.getInstance(asset.root), tmp)
        core.scene.addEntities(asset.entities)
    }

    private fun createPool(spec: SpawnableSpec) {
        val instances = arrayOfNulls<FilamentInstance>(GameController.MAX_PER_TYPE)
        val asset = core.assetLoader.createInstancedAsset(core.readAsset(spec.file), instances) ?: return
        core.resourceLoader.loadResources(asset)
        asset.releaseSourceData()
        assets += asset
        @Suppress("UNCHECKED_CAST")
        pools[spec] = Pool(spec, asset, instances as Array<FilamentInstance>)
    }

    private fun createBatches() {
        billboard = core.readAsset("models/billboard.glb")
        for ((i, kind) in CharacterRegistry.kinds.withIndex()) {
            val atlas = SpriteAtlas.build(kind)
            val t = Textures.fromBitmap(engine, atlas, mipmaps = true)
            atlas.recycle()
            textures += t
            kindBatches[kind] = BillboardBatch(core, billboard.duplicate(), 16, t, Textures.mipmapped, 5 + i % 2)
        }
        val decalBmp = DecalArt.build()
        val decalTex = Textures.fromBitmap(engine, decalBmp, mipmaps = true)
        decalBmp.recycle()
        textures += decalTex
        decals = BillboardBatch(core, billboard.duplicate(), 220, decalTex, Textures.mipmapped, 2)
        lines = BillboardBatch(core, billboard.duplicate(), 1200, null, Textures.linear, 3)

        bubbleAtlas = Textures.create(engine, BUBBLE_ATLAS, BUBBLE_ATLAS, mipmaps = false)
        // wyczyść atlas (przezroczyste piksele)
        val clear = Bitmap.createBitmap(BUBBLE_ATLAS, BUBBLE_ATLAS, Bitmap.Config.ARGB_8888)
        Textures.upload(engine, bubbleAtlas, clear, 0, 0)
        clear.recycle()
        textures += bubbleAtlas
        bubbles = BillboardBatch(core, billboard.duplicate(), BUBBLE_COLS * BUBBLE_ROWS, bubbleAtlas, Textures.linear, 7)
    }

    // ------------------------------------------------------------------ klatka

    fun update() {
        val p = game.player
        val eye = p.eye
        val f = p.forward()
        val target = eye + f
        core.camera.lookAt(
            eye.x.toDouble(), eye.y.toDouble(), eye.z.toDouble(),
            target.x.toDouble(), target.y.toDouble(), target.z.toDouble(),
            0.0, 1.0, 0.0,
        )
        if (!isLoaded) return
        syncBodies()
        drawDoodles(eye, f, p.right())
    }

    /** Przypisuje ciała fizyczne do instancji modeli i ustawia ich transformacje. */
    private fun syncBodies() {
        val tm = engine.transformManager
        val alive = game.physics.bodies.toHashSet()
        for (pool in pools.values) {
            for (i in pool.owner.indices) {
                val b = pool.owner[i]
                if (b != null && b !in alive) {
                    core.scene.removeEntities(pool.instances[i].entities)
                    pool.owner[i] = null
                }
            }
        }
        for (b in game.physics.bodies) {
            val pool = pools[b.spec] ?: continue
            var idx = pool.owner.indexOf(b)
            if (idx < 0) {
                idx = pool.owner.indexOfFirst { it == null }
                if (idx < 0) continue
                pool.owner[idx] = b
                core.scene.addEntities(pool.instances[idx].entities)
            }
            bodyMatrix(b, tmp)
            tm.setTransform(tm.getInstance(pool.instances[idx].root), tmp)
        }
    }

    /** T(pozycja) · R(kwaternion) · T(-środek modelu) */
    private fun bodyMatrix(b: PhysicsBody, out: FloatArray) {
        val (x, y, z, w) = b.rotation.let { arrayOf(it[0], it[1], it[2], it[3]) }
        // macierz obrotu z kwaternionu (kolumnowo, jak w OpenGL)
        tmp2[0] = 1 - 2 * (y * y + z * z); tmp2[1] = 2 * (x * y + z * w); tmp2[2] = 2 * (x * z - y * w); tmp2[3] = 0f
        tmp2[4] = 2 * (x * y - z * w); tmp2[5] = 1 - 2 * (x * x + z * z); tmp2[6] = 2 * (y * z + x * w); tmp2[7] = 0f
        tmp2[8] = 2 * (x * z + y * w); tmp2[9] = 2 * (y * z - x * w); tmp2[10] = 1 - 2 * (x * x + y * y); tmp2[11] = 0f
        tmp2[12] = 0f; tmp2[13] = 0f; tmp2[14] = 0f; tmp2[15] = 1f
        Matrix.setIdentityM(out, 0)
        Matrix.translateM(out, 0, b.position.x, b.position.y, b.position.z)
        val r = FloatArray(16)
        Matrix.multiplyMM(r, 0, out, 0, tmp2, 0)
        val c = b.spec.modelCenter
        Matrix.translateM(r, 0, -c.x, -c.y, -c.z)
        System.arraycopy(r, 0, out, 0, 16)
    }

    private fun drawDoodles(eye: Vec3, forward: Vec3, camRight: Vec3) {
        val s = CharacterSpace.SCALE
        val space = game.space
        val world = game.world
        // Billboard z poziomą osią X, zwrócony do kamery (jak papierowa wycinanka).
        val toViewer = -forward
        val rh = camRight.horizontal()
        val billRight = if (rh.length() > 0.2f) rh.normalized() else camRight
        var billUp = toViewer.cross(billRight).normalized()
        if (billUp == Vec3.ZERO) billUp = Vec3.UP
        val camUp = camRight.cross(forward).normalized()

        // --- naklejki: cienie ludzików i przedmiotów, plamy, okruchy
        decals.begin()
        for (c in world.characters) {
            val surf = c.surface ?: continue
            if (c.alpha <= 0f) continue
            val above = (c.position.y - surf.height).coerceAtLeast(0f) * s
            val size = c.kind.spriteSizeMeters * s * 0.5f / (1f + above * 1.2f)
            val gy = if (c.position.y >= surf.height - 0.02f) surf.height * s else c.position.y * s
            decal(DecalType.SHADOW, Vec3(c.position.x * s, gy, c.position.z * s), size, 0f, c.alpha * 0.85f)
        }
        for (b in game.physics.bodies) {
            val gy = game.geometry.groundHeightAt(b.position.x, b.position.z, 0f, b.position.y)
            val h = (b.position.y - b.halfHeight - gy).coerceAtLeast(0f)
            decal(DecalType.SHADOW, Vec3(b.position.x, gy, b.position.z), b.halfWidth * 2.6f / (1f + h * 2f), 0f, 0.55f / (1f + h * 2f))
        }
        for (d in world.decals) decal(d.type, d.position * s, d.size * s, d.rotation, d.alpha)
        decals.end()

        // --- linie ołówka, mosty, nitki pająka
        lines.begin()
        for (st in world.strokes) {
            val a = st.alpha
            if (a <= 0.01f || st.points.size < 2) continue
            val col = st.color
            val ca = ((col ushr 24) and 0xFF) / 255f * a
            val r = ((col shr 16) and 0xFF) / 255f
            val g = ((col shr 8) and 0xFF) / 255f
            val bl = (col and 0xFF) / 255f
            val half = st.width * s * 0.5f
            for (i in 0 until st.points.size - 1) {
                if (lines.isFull) break
                val p0 = st.points[i] * s
                val p1 = st.points[i + 1] * s
                val dir = (p1 - p0).normalized()
                val side = if (st.style == StrokeStyle.FLOOR_LINE) {
                    dir.cross(Vec3.UP).normalized()
                } else {
                    dir.cross((eye - p0).normalized()).normalized()
                }
                val lift = if (st.style == StrokeStyle.FLOOR_LINE) Vec3(0f, 0.004f, 0f) else Vec3.ZERO
                val w0 = half * (0.75f + 0.25f * sin(i * 2.3f))
                val w1 = half * (0.75f + 0.25f * sin((i + 1) * 2.3f))
                lines.quad(p0 - side * w0 + lift, p1 - side * w1 + lift, p1 + side * w1 + lift, p0 + side * w0 + lift, 0f, 0f, 1f, 1f, r, g, bl, ca)
            }
        }
        lines.end()

        // --- ludziki: od najdalszego do najbliższego w każdej grupie
        for (b in kindBatches.values) b.begin()
        val sorted = world.characters.sortedByDescending { (space.toReal(it.position) - eye).length() }
        for (c in sorted) {
            if (c.alpha <= 0f) continue
            val batch = kindBatches[c.kind] ?: continue
            val size = c.kind.spriteSizeMeters * s
            val w = size * c.scaleX
            val h = size * c.scaleY
            SpriteAtlas.uv(c.kind, c.anim, c.animFrame, uv)
            val origin = space.toReal(c.position) - billUp * (h * 0.05f)
            batch.sprite(origin, billRight, billUp, w, h, uv, c.heading.dot(billRight) < 0f, c.alpha)
        }
        for (b in kindBatches.values) b.end()

        // --- dymki
        updateBubbleAtlas()
        bubbles.begin()
        for (bub in world.bubbles) {
            val slot = bubbleSlots[bub.id] ?: continue
            val owner = bub.owner
            val pos = space.toReal(owner.position)
            val size = owner.kind.spriteSizeMeters * s
            val dist = (pos - eye).length()
            val w = 0.42f * (dist / 2.2f).coerceIn(0.7f, 2.2f)
            val aspect = bubbleAspect[bub.id] ?: 2f
            val h = w / aspect
            val origin = pos + billUp * (size * 0.95f * owner.scaleY) + camRight * (w * 0.3f)
            slotUv(slot, aspect, uv)
            bubbles.sprite(origin, camRight, camUp, w, h, uv, false, bub.alpha)
        }
        bubbles.end()
    }

    private fun decal(type: DecalType, center: Vec3, size: Float, rotation: Float, alpha: Float) {
        if (alpha <= 0.01f) return
        val r = Vec3(cos(rotation), 0f, sin(rotation)) * (size / 2f)
        val u = Vec3(-sin(rotation), 0f, cos(rotation)) * (size / 2f)
        val c = Vec3(center.x, center.y + 0.003f, center.z)
        DecalArt.uv(type, uv)
        decals.quad(c - r + u, c + r + u, c + r - u, c - r - u, uv[0], uv[1], uv[2], uv[3], 1f, 1f, 1f, alpha)
    }

    /** Nowe dymki rysujemy do wolnych komórek atlasu, zakończone zwalniamy. */
    private fun updateBubbleAtlas() {
        val live = game.world.bubbles.map { it.id }.toHashSet()
        bubbleSlots.keys.retainAll(live)
        bubbleAspect.keys.retainAll(live)
        for (b in game.world.bubbles) {
            if (bubbleSlots.containsKey(b.id)) continue
            val used = bubbleSlots.values.toHashSet()
            val slot = (0 until BUBBLE_COLS * BUBBLE_ROWS).firstOrNull { it !in used } ?: break
            val art = BubbleArt.render(b.text, b.id)
            val cell = Bitmap.createBitmap(BUBBLE_W, BUBBLE_H, Bitmap.Config.ARGB_8888)
            val scale = min(BUBBLE_W / art.bitmap.width.toFloat(), BUBBLE_H / art.bitmap.height.toFloat())
            Canvas(cell).apply {
                scale(scale, scale)
                drawBitmap(art.bitmap, 0f, 0f, null)
            }
            art.bitmap.recycle()
            Textures.upload(engine, bubbleAtlas, cell, (slot % BUBBLE_COLS) * BUBBLE_W, (slot / BUBBLE_COLS) * BUBBLE_H)
            cell.recycle()
            bubbleSlots[b.id] = slot
            bubbleAspect[b.id] = art.aspect
        }
    }

    private fun slotUv(slot: Int, aspect: Float, out: FloatArray) {
        // dymek przeskalowany do komórki zajmuje jej lewy-górny fragment
        val wFrac = min(1f, aspect * BUBBLE_H / BUBBLE_W)
        val hFrac = min(1f, BUBBLE_W / (aspect * BUBBLE_H))
        val cw = 1f / BUBBLE_COLS
        val ch = 1f / BUBBLE_ROWS
        val x = (slot % BUBBLE_COLS) * cw
        val y = (slot / BUBBLE_COLS) * ch
        out[0] = x + 0.5f / BUBBLE_ATLAS
        out[1] = y + 0.5f / BUBBLE_ATLAS
        out[2] = x + cw * wFrac - 0.5f / BUBBLE_ATLAS
        out[3] = y + ch * hFrac - 0.5f / BUBBLE_ATLAS
    }

    fun destroy() {
        kindBatches.values.forEach { it.destroy() }
        if (::decals.isInitialized) decals.destroy()
        if (::lines.isInitialized) lines.destroy()
        if (::bubbles.isInitialized) bubbles.destroy()
        for (a in assets) {
            core.scene.removeEntities(a.entities)
            core.assetLoader.destroyAsset(a)
        }
        indirectLight?.let { engine.destroyIndirectLight(it) }
        for (t in textures) engine.destroyTexture(t)
        for (e in entities) {
            engine.destroyEntity(e)
            EntityManager.get().destroy(e)
        }
    }

    companion object {
        private const val TAG = "GameRenderer"
        const val IBL_INTENSITY = 36_000f
        const val SUN_LUX = 95_000f
        private const val BUBBLE_ATLAS = 1024
        private const val BUBBLE_W = 256
        private const val BUBBLE_H = 128
        private const val BUBBLE_COLS = BUBBLE_ATLAS / BUBBLE_W
        private const val BUBBLE_ROWS = BUBBLE_ATLAS / BUBBLE_H
    }
}
