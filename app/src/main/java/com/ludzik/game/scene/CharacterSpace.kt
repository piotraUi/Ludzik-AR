package com.ludzik.game.scene

import com.ludzik.game.characters.Vec3
import com.ludzik.game.characters.WalkSurface
import kotlin.math.abs

/**
 * Most między pokojem 3D a symulacją ludzików. Ludziki z zeszytu są projektowane w skali „blatu”
 * (ok. 24 cm), więc w pokoju żyją w skali [SCALE]: w świecie gry są 3× większe (~72 cm),
 * a pokój widziany ich oczami jest 3× mniejszy. Dzięki temu cała logika postaci zostaje bez zmian.
 */
class CharacterSpace(geometry: RoomGeometry, furniture: List<FurnitureSpec> = RoomLayout.furniture) {
    companion object {
        const val SCALE = 3f
    }

    fun toSim(p: Vec3) = p * (1f / SCALE)
    fun toReal(p: Vec3) = p * SCALE

    val floor: WalkSurface
    val tops: List<Pair<WalkSurface, WalkTop>>
    val walls: List<WalkSurface>
    val all: List<WalkSurface>

    init {
        var id = 1
        val hx = (geometry.halfX - 0.04f) / SCALE
        val hz = (geometry.halfZ - 0.04f) / SCALE
        floor = WalkSurface(id++, false).apply {
            update(rect(-hx, -hz, hx, hz, 0f), Vec3.ZERO, Vec3.UP)
            obstacles = furniture.filter { it.collides && it.floorObstacle }.map {
                val b = it.bounds
                floatArrayOf(b.min.x / SCALE, b.min.z / SCALE, b.max.x / SCALE, b.max.z / SCALE)
            }
        }
        tops = furniture.mapNotNull { f ->
            val w = f.walk ?: return@mapNotNull null
            val inset = 0.03f
            val s = WalkSurface(id++, false)
            val y = w.y / SCALE
            s.update(
                rect((w.x0 + inset) / SCALE, (w.z0 + inset) / SCALE, (w.x1 - inset) / SCALE, (w.z1 - inset) / SCALE, y),
                Vec3((w.x0 + w.x1) / 2 / SCALE, y, (w.z0 + w.z1) / 2 / SCALE), Vec3.UP,
            )
            s to w
        }
        val h = geometry.height / SCALE
        val gx = geometry.halfX / SCALE
        val gz = geometry.halfZ / SCALE
        walls = listOf(
            wall(id++, Vec3(-gx, 0f, -gz), Vec3(gx, 0f, -gz), h, Vec3(0f, 0f, 1f)),
            wall(id++, Vec3(gx, 0f, gz), Vec3(-gx, 0f, gz), h, Vec3(0f, 0f, -1f)),
            wall(id++, Vec3(-gx, 0f, gz), Vec3(-gx, 0f, -gz), h, Vec3(1f, 0f, 0f)),
            wall(id++, Vec3(gx, 0f, -gz), Vec3(gx, 0f, gz), h, Vec3(-1f, 0f, 0f)),
        )
        all = listOf(floor) + tops.map { it.first } + walls
    }

    private fun rect(x0: Float, z0: Float, x1: Float, z1: Float, y: Float) =
        listOf(Vec3(x0, y, z0), Vec3(x1, y, z0), Vec3(x1, y, z1), Vec3(x0, y, z1))

    private fun wall(id: Int, a: Vec3, b: Vec3, h: Float, n: Vec3) = WalkSurface(id, true).apply {
        update(listOf(a, b, b.withY(h), a.withY(h)), Vec3((a.x + b.x) / 2, h / 2, (a.z + b.z) / 2), n)
    }

    /** Powierzchnia ludzików pod punktem trafienia (w metrach gry) albo null. */
    fun surfaceAt(real: Vec3): WalkSurface? {
        val s = toSim(real)
        for ((surface, top) in tops) {
            if (abs(real.y - top.y) < 0.08f && surface.contains(s, 0f)) return surface
        }
        if (real.y < 0.05f && floor.contains(s, 0f)) return floor
        return null
    }
}
