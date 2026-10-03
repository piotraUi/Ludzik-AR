package com.ludzik.game.characters

import kotlin.math.abs
import kotlin.random.Random

/**
 * Powierzchnia, po której mogą chodzić postacie: podłoga, stół, kanapa (pozioma)
 * albo ściana (pionowa). Warstwa AR aktualizuje kształt co klatkę, a logika postaci
 * nie zależy od ARCore, więc da się ją testować na zwykłej JVM.
 */
class WalkSurface(val id: Int, val isVertical: Boolean) {
    var alive = true

    /**
     * Prostokąty (x0, z0, x1, z1), których ludziki nie mogą przejść (np. sofa stojąca na podłodze).
     * Margines z [contains] powiększa je tak samo jak odsuwa od krawędzi.
     */
    var obstacles: List<FloatArray> = emptyList()

    /** Wysokość (Y) powierzchni poziomej. Dla ściany: Y środka. */
    var height = 0f
        private set
    var center = Vec3.ZERO
        private set
    var normal = Vec3.UP
        private set

    /** Obwód w przestrzeni świata. */
    var boundary: List<Vec3> = emptyList()
        private set

    // Wielokąt w XZ dla powierzchni poziomych (ARCore zwraca wielokąty wypukłe).
    private var polyX = FloatArray(0)
    private var polyZ = FloatArray(0)
    private var orientation = 1f
    private var minX = 0f
    private var maxX = 0f
    private var minZ = 0f
    private var maxZ = 0f

    // Ściany: oś pozioma wzdłuż ściany oraz zakresy.
    var wallAxis = Vec3(1f, 0f, 0f)
        private set
    var wallMinS = 0f
        private set
    var wallMaxS = 0f
        private set
    var wallBottom = 0f
        private set
    var wallTop = 0f
        private set

    fun update(boundary: List<Vec3>, center: Vec3, normal: Vec3) {
        this.boundary = boundary
        this.center = center
        this.normal = normal.normalized()
        this.height = center.y
        if (isVertical) updateWall() else updateHorizontal()
    }

    private fun updateHorizontal() {
        val n = boundary.size
        polyX = FloatArray(n) { boundary[it].x }
        polyZ = FloatArray(n) { boundary[it].z }
        var area = 0f
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += polyX[i] * polyZ[j] - polyX[j] * polyZ[i]
        }
        orientation = if (area >= 0f) 1f else -1f
        minX = polyX.minOrNull() ?: center.x
        maxX = polyX.maxOrNull() ?: center.x
        minZ = polyZ.minOrNull() ?: center.z
        maxZ = polyZ.maxOrNull() ?: center.z
    }

    private fun updateWall() {
        val horizontalNormal = normal.horizontal().normalized()
        wallAxis = Vec3.UP.cross(horizontalNormal).normalized()
        var minS = 0f
        var maxS = 0f
        var bottom = center.y
        var top = center.y
        for (p in boundary) {
            val s = (p - center).dot(wallAxis)
            if (s < minS) minS = s
            if (s > maxS) maxS = s
            if (p.y < bottom) bottom = p.y
            if (p.y > top) top = p.y
        }
        wallMinS = minS
        wallMaxS = maxS
        wallBottom = bottom
        wallTop = top
    }

    val area: Float
        get() = if (isVertical) (wallMaxS - wallMinS) * (wallTop - wallBottom) else (maxX - minX) * (maxZ - minZ)

    /** Czy punkt (x, z) leży w wielokącie co najmniej [margin] od krawędzi. */
    fun contains(x: Float, z: Float, margin: Float = 0f): Boolean {
        if (isVertical) return false
        val n = polyX.size
        if (n < 3) return false
        if (x < minX - 0.01f || x > maxX + 0.01f || z < minZ - 0.01f || z > maxZ + 0.01f) return false
        for (i in 0 until n) {
            val j = (i + 1) % n
            val ex = polyX[j] - polyX[i]
            val ez = polyZ[j] - polyZ[i]
            val len = kotlin.math.sqrt(ex * ex + ez * ez)
            if (len < 1e-5f) continue
            // Odległość ze znakiem od krawędzi; dodatnia = wewnątrz wielokąta.
            val signed = orientation * (ex * (z - polyZ[i]) - ez * (x - polyX[i])) / len
            if (signed < margin) return false
        }
        for (o in obstacles) {
            if (x > o[0] - margin && x < o[2] + margin && z > o[1] - margin && z < o[3] + margin) return false
        }
        return true
    }

    fun contains(p: Vec3, margin: Float = 0f) = contains(p.x, p.z, margin)

    fun randomPoint(rnd: Random, margin: Float): Vec3? {
        if (isVertical) return null
        repeat(16) {
            val x = minX + rnd.nextFloat() * (maxX - minX)
            val z = minZ + rnd.nextFloat() * (maxZ - minZ)
            if (contains(x, z, margin)) return Vec3(x, height, z)
        }
        return if (contains(center.x, center.z, 0f)) center.withY(height) else null
    }

    /** Losowy punkt w promieniu [radius] od [from] (spacery „po okolicy” wyglądają naturalniej). */
    fun randomPointNear(rnd: Random, from: Vec3, radius: Float, margin: Float): Vec3? {
        repeat(10) {
            val a = rnd.nextFloat() * 6.2831855f
            val r = radius * (0.3f + 0.7f * rnd.nextFloat())
            val x = from.x + kotlin.math.cos(a) * r
            val z = from.z + kotlin.math.sin(a) * r
            if (contains(x, z, margin)) return Vec3(x, height, z)
        }
        return randomPoint(rnd, margin)
    }

    /** Punkt na ścianie najbliżej [p], na wysokości [y], lekko odsunięty od ściany. */
    fun wallPointNear(p: Vec3, y: Float, offset: Float = 0.015f): Vec3 {
        val margin = 0.05f
        val lo = wallMinS + margin
        val hi = wallMaxS - margin
        var s = (p - center).dot(wallAxis)
        s = if (lo < hi) s.coerceIn(lo, hi) else (wallMinS + wallMaxS) / 2f
        val onWall = center + wallAxis * s
        val n = normal.horizontal().normalized()
        return Vec3(onWall.x + n.x * offset, y, onWall.z + n.z * offset)
    }

    /** Pozioma odległość od punktu do płaszczyzny ściany. */
    fun distanceToWall(p: Vec3): Float = abs((p - center).dot(normal.horizontal().normalized()))

    override fun toString() = "Surface#$id(${if (isVertical) "wall" else "h=%.2f".format(height)})"
}
