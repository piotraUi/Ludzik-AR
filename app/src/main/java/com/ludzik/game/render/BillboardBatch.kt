package com.ludzik.game.render

import com.google.android.filament.Box
import com.google.android.filament.IndexBuffer
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import com.google.android.filament.gltfio.FilamentAsset
import com.ludzik.game.characters.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Zestaw płaskich prostokątów z jedną teksturą, przebudowywany co klatkę
 * (postacie jednego rodzaju, plamy, linie albo dymki).
 *
 * Materiał bierzemy z billboard.glb (unlit + przezroczystość z gltfio), a geometrię
 * podmieniamy na własne bufory — dzięki temu nie potrzeba kompilować własnych materiałów.
 */
class BillboardBatch(
    private val core: FilamentCore,
    template: ByteBuffer,
    private val maxQuads: Int,
    texture: Texture?,
    sampler: TextureSampler,
    priority: Int,
) {
    private val engine = core.engine
    private val asset: FilamentAsset = core.assetLoader.createAsset(template)
        ?: throw IllegalStateException("Nie udało się wczytać billboard.glb")
    private val entity: Int
    private val material: MaterialInstance
    private val vb: VertexBuffer
    private val ib: IndexBuffer

    // Filament wysyła dane do GPU asynchronicznie — trzy bufory na zmianę, żeby nie nadpisać
    // pamięci, której silnik jeszcze nie skopiował.
    private val buffers = Array(3) { ByteBuffer.allocateDirect(maxQuads * 4 * STRIDE).order(ByteOrder.nativeOrder()) }
    private var current = 0
    private var data = buffers[0]
    private var quads = 0
    private var visible = true

    init {
        core.resourceLoader.loadResources(asset)
        asset.releaseSourceData()
        entity = asset.renderableEntities.first()
        val rm = engine.renderableManager
        val ri = rm.getInstance(entity)
        material = rm.getMaterialInstanceAt(ri, 0)
        texture?.let { material.setParameter("baseColorMap", it, sampler) }

        vb = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(maxQuads * 4)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0, VertexBuffer.AttributeType.FLOAT3, 0, STRIDE)
            .attribute(VertexBuffer.VertexAttribute.UV0, 0, VertexBuffer.AttributeType.FLOAT2, 12, STRIDE)
            // materiał gltfio wymaga też drugiego zestawu UV — podajemy ten sam
            .attribute(VertexBuffer.VertexAttribute.UV1, 0, VertexBuffer.AttributeType.FLOAT2, 12, STRIDE)
            .attribute(VertexBuffer.VertexAttribute.COLOR, 0, VertexBuffer.AttributeType.FLOAT4, 20, STRIDE)
            .build(engine)
        ib = IndexBuffer.Builder()
            .indexCount(maxQuads * 6)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        val idx = ByteBuffer.allocateDirect(maxQuads * 6 * 2).order(ByteOrder.nativeOrder())
        for (q in 0 until maxQuads) {
            val b = (q * 4).toShort()
            for (o in intArrayOf(0, 1, 2, 0, 2, 3)) idx.putShort((b + o).toShort())
        }
        idx.flip()
        ib.setBuffer(engine, idx)

        rm.setAxisAlignedBoundingBox(ri, Box(0f, 1.4f, 0f, 3.5f, 1.6f, 3.5f))
        rm.setPriority(ri, priority)
        rm.setCastShadows(ri, false)
        rm.setReceiveShadows(ri, false)
        rm.setCulling(ri, false)
        core.scene.addEntities(asset.entities)
        setVisible(false)
    }

    fun setTexture(texture: Texture, sampler: TextureSampler) = material.setParameter("baseColorMap", texture, sampler)

    fun begin() {
        current = (current + 1) % buffers.size
        data = buffers[current]
        data.clear()
        quads = 0
    }

    val isFull get() = quads >= maxQuads

    /**
     * Prostokąt o rogach: lewy-dół, prawy-dół, prawy-góra, lewy-góra.
     * UV: (u0, v0) to lewy-górny róg tekstury, (u1, v1) prawy-dolny.
     */
    fun quad(
        bl: Vec3, br: Vec3, tr: Vec3, tl: Vec3,
        u0: Float, v0: Float, u1: Float, v1: Float,
        r: Float = 1f, g: Float = 1f, b: Float = 1f, a: Float = 1f,
    ) {
        if (quads >= maxQuads) return
        vertex(bl, u0, v1, r, g, b, a)
        vertex(br, u1, v1, r, g, b, a)
        vertex(tr, u1, v0, r, g, b, a)
        vertex(tl, u0, v0, r, g, b, a)
        quads++
    }

    /** Prostokąt rozpięty od środka dolnej krawędzi [origin] wzdłuż osi [right] i [up]. */
    fun sprite(origin: Vec3, right: Vec3, up: Vec3, width: Float, height: Float, uv: FloatArray, flip: Boolean, alpha: Float) {
        val hr = right * (width / 2f)
        val u = up * height
        val bl = origin - hr
        val br = origin + hr
        val (ua, ub) = if (flip) uv[2] to uv[0] else uv[0] to uv[2]
        quad(bl, br, br + u, bl + u, ua, uv[1], ub, uv[3], 1f, 1f, 1f, alpha)
    }

    private fun vertex(p: Vec3, u: Float, v: Float, r: Float, g: Float, b: Float, a: Float) {
        data.putFloat(p.x).putFloat(p.y).putFloat(p.z)
        data.putFloat(u).putFloat(v)
        data.putFloat(r).putFloat(g).putFloat(b).putFloat(a)
    }

    fun end() {
        val rm = engine.renderableManager
        val ri = rm.getInstance(entity)
        if (quads == 0) {
            setVisible(false)
            return
        }
        data.flip()
        vb.setBufferAt(engine, 0, data)
        rm.setGeometryAt(ri, 0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib, 0, quads * 6)
        setVisible(true)
    }

    private fun setVisible(v: Boolean) {
        if (v == visible) return
        visible = v
        val rm = engine.renderableManager
        rm.setLayerMask(rm.getInstance(entity), 0xFF, if (v) 0x01 else 0x00)
    }

    fun destroy() {
        core.scene.removeEntities(asset.entities)
        core.assetLoader.destroyAsset(asset)
        engine.destroyVertexBuffer(vb)
        engine.destroyIndexBuffer(ib)
    }

    companion object {
        /** pozycja (3) + uv (2) + kolor (4) floatów */
        const val STRIDE = 36
    }
}
