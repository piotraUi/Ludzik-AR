package com.ludzik.ar.characters

import kotlin.math.sqrt

/** Niemutowalny wektor 3D w metrach (układ świata ARCore: Y w górę). */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(x * x + y * y + z * z)
    fun horizontal() = Vec3(x, 0f, z)
    fun withY(newY: Float) = Vec3(x, newY, z)

    fun normalized(): Vec3 {
        val l = length()
        return if (l < 1e-6f) ZERO else Vec3(x / l, y / l, z / l)
    }

    fun distanceTo(o: Vec3): Float = (this - o).length()

    /** Odległość w płaszczyźnie XZ (ignoruje wysokość). */
    fun horizontalDistanceTo(o: Vec3): Float {
        val dx = x - o.x
        val dz = z - o.z
        return sqrt(dx * dx + dz * dz)
    }

    fun lerp(o: Vec3, t: Float) = Vec3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t)

    fun isFinite() = x.isFinite() && y.isFinite() && z.isFinite()

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val UP = Vec3(0f, 1f, 0f)
    }
}
