package com.ludzik.game.math

import com.ludzik.game.characters.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Trafienie promienia: odległość wzdłuż promienia, punkt i normalna powierzchni. */
class RayHit(val t: Float, val point: Vec3, val normal: Vec3)

/** Prostopadłościan wyrównany do osi (w metrach, układ świata gry). */
data class Aabb(val min: Vec3, val max: Vec3) {
    val center get() = Vec3((min.x + max.x) / 2, (min.y + max.y) / 2, (min.z + max.z) / 2)

    fun containsXZ(x: Float, z: Float, pad: Float = 0f) =
        x >= min.x - pad && x <= max.x + pad && z >= min.z - pad && z <= max.z + pad

    fun contains(p: Vec3) = p.x in min.x..max.x && p.y in min.y..max.y && p.z in min.z..max.z

    /** Metoda „slabs”. Zwraca najbliższe trafienie z t w [0, maxT] albo null. */
    fun intersectRay(o: Vec3, d: Vec3, maxT: Float): RayHit? {
        var tMin = 0f
        var tMax = maxT
        var axis = -1
        var sign = 0f
        val os = floatArrayOf(o.x, o.y, o.z)
        val ds = floatArrayOf(d.x, d.y, d.z)
        val mins = floatArrayOf(min.x, min.y, min.z)
        val maxs = floatArrayOf(max.x, max.y, max.z)
        for (i in 0..2) {
            if (abs(ds[i]) < 1e-8f) {
                if (os[i] < mins[i] || os[i] > maxs[i]) return null
                continue
            }
            val inv = 1f / ds[i]
            var t0 = (mins[i] - os[i]) * inv
            var t1 = (maxs[i] - os[i]) * inv
            var s = -1f // wejście przez ścianę „min” → normalna w stronę ujemną
            if (t0 > t1) {
                val tmp = t0; t0 = t1; t1 = tmp
                s = 1f
            }
            if (t0 > tMin) {
                tMin = t0
                axis = i
                sign = s
            }
            tMax = min(tMax, t1)
            if (tMin > tMax) return null
        }
        if (axis < 0) return null // start wewnątrz
        val n = when (axis) {
            0 -> Vec3(sign, 0f, 0f)
            1 -> Vec3(0f, sign, 0f)
            else -> Vec3(0f, 0f, sign)
        }
        return RayHit(tMin, o + d * tMin, n)
    }

    fun expandedXZ(r: Float) = Aabb(Vec3(min.x - r, min.y, min.z - r), Vec3(max.x + r, max.y, max.z + r))

    companion object {
        fun of(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float) =
            Aabb(Vec3(min(x0, x1), min(y0, y1), min(z0, z1)), Vec3(max(x0, x1), max(y0, y1), max(z0, z1)))
    }
}
