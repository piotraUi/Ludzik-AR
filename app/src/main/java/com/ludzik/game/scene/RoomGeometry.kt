package com.ludzik.game.scene

import com.ludzik.game.characters.Vec3
import com.ludzik.game.math.Aabb
import com.ludzik.game.math.RayHit
import kotlin.math.abs

enum class SurfaceKind { FLOOR, CEILING, WALL, FURNITURE }

class StaticHit(val hit: RayHit, val kind: SurfaceKind, val furniture: FurnitureSpec?)

/** Bryła kolizji mebla (meble z siedziskiem dzielimy na siedzisko i oparcie). */
class Collider(val box: Aabb, val furniture: FurnitureSpec)

/**
 * Statyczna geometria pokoju do kolizji i celowania: podłoga, sufit, ściany i proste bryły mebli.
 * Czysty Kotlin — testowalny bez telefonu.
 */
class RoomGeometry(
    val halfX: Float = RoomLayout.HALF_X,
    val halfZ: Float = RoomLayout.HALF_Z,
    val height: Float = RoomLayout.HEIGHT,
    furniture: List<FurnitureSpec> = RoomLayout.furniture,
) {
    val colliders: List<Collider> = furniture.filter { it.collides }.flatMap { collidersFor(it) }

    private fun collidersFor(f: FurnitureSpec): List<Collider> {
        val b = f.bounds
        val w = f.walk ?: return listOf(Collider(b, f))
        val coversAll = abs(w.x0 - b.min.x) < 0.01f && abs(w.x1 - b.max.x) < 0.01f &&
            abs(w.z0 - b.min.z) < 0.01f && abs(w.z1 - b.max.z) < 0.01f
        if (coversAll && abs(w.y - b.max.y) < 0.02f) {
            // Stół: tylko blat (pod spodem można przejść / przetoczyć piłkę).
            val slab = Aabb(Vec3(b.min.x, w.y - 0.06f, b.min.z), b.max)
            return listOf(Collider(slab, f))
        }
        // Siedzisko od podłogi do wysokości siedzenia + oparcie w pozostałej części.
        val seat = Aabb(Vec3(w.x0, b.min.y, w.z0), Vec3(w.x1, w.y, w.z1))
        val back = when {
            w.z0 > b.min.z + 0.01f -> Aabb(b.min, Vec3(b.max.x, b.max.y, w.z0))
            w.z1 < b.max.z - 0.01f -> Aabb(Vec3(b.min.x, b.min.y, w.z1), b.max)
            w.x0 > b.min.x + 0.01f -> Aabb(b.min, Vec3(w.x0, b.max.y, b.max.z))
            w.x1 < b.max.x - 0.01f -> Aabb(Vec3(w.x1, b.min.y, b.min.z), b.max)
            else -> null
        }
        return listOfNotNull(Collider(seat, f), back?.let { Collider(it, f) })
    }

    /** Najbliższe trafienie w ścianę, podłogę, sufit albo mebel. */
    fun raycast(o: Vec3, d: Vec3, maxT: Float = 20f): StaticHit? {
        var best: StaticHit? = null
        fun consider(t: Float, n: Vec3, kind: SurfaceKind) {
            if (t <= 1e-4f || t > maxT) return
            val p = o + d * t
            if (abs(p.x) > halfX + 1e-3f || abs(p.z) > halfZ + 1e-3f || p.y < -1e-3f || p.y > height + 1e-3f) return
            if (best == null || t < best!!.hit.t) best = StaticHit(RayHit(t, p, n), kind, null)
        }
        if (d.y < 0) consider(-o.y / d.y, Vec3.UP, SurfaceKind.FLOOR)
        if (d.y > 0) consider((height - o.y) / d.y, Vec3(0f, -1f, 0f), SurfaceKind.CEILING)
        if (d.x > 0) consider((halfX - o.x) / d.x, Vec3(-1f, 0f, 0f), SurfaceKind.WALL)
        if (d.x < 0) consider((-halfX - o.x) / d.x, Vec3(1f, 0f, 0f), SurfaceKind.WALL)
        if (d.z > 0) consider((halfZ - o.z) / d.z, Vec3(0f, 0f, -1f), SurfaceKind.WALL)
        if (d.z < 0) consider((-halfZ - o.z) / d.z, Vec3(0f, 0f, 1f), SurfaceKind.WALL)
        for (c in colliders) {
            val h = c.box.intersectRay(o, d, best?.hit?.t ?: maxT) ?: continue
            if (best == null || h.t < best!!.hit.t) best = StaticHit(h, SurfaceKind.FURNITURE, c.furniture)
        }
        return best
    }

    /** Najwyższy wierzch pod punktem, na który da się wejść z wysokości [maxY]. */
    fun groundHeightAt(x: Float, z: Float, pad: Float, maxY: Float): Float {
        var g = 0f
        for (c in colliders) {
            val top = c.box.max.y
            if (top <= maxY && top > g && c.box.containsXZ(x, z, pad)) g = top
        }
        return g
    }
}
