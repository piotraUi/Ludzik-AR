package com.ludzik.game.physics

import com.ludzik.game.characters.Vec3
import com.ludzik.game.math.Aabb
import com.ludzik.game.math.RayHit
import com.ludzik.game.scene.RoomGeometry
import com.ludzik.game.scene.SpawnableSpec
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Przedmiot w pokoju. Piłki/jabłka to kule (toczą się), pudełka i kaczki to prostopadłościany
 * stojące pionowo (obracają się tylko wokół osi Y).
 */
class PhysicsBody(val id: Int, val spec: SpawnableSpec, var position: Vec3, var velocity: Vec3) {
    /** Kwaternion orientacji (x, y, z, w). */
    val rotation = floatArrayOf(0f, 0f, 0f, 1f)
    var spinY = 0f
    var onSupport = false
    var age = 0f

    /** Pół-wysokość do kontaktu z podłogą i pół-szerokość do ścian. */
    val halfHeight get() = if (spec.rolls) spec.radius else spec.halfHeight
    val halfWidth get() = spec.radius

    fun box(): Aabb = Aabb(
        Vec3(position.x - halfWidth, position.y - halfHeight, position.z - halfWidth),
        Vec3(position.x + halfWidth, position.y + halfHeight, position.z + halfWidth),
    )
}

/** Prosta fizyka: grawitacja, odbicia od pokoju i mebli, zderzenia przedmiotów, kopanie przez gracza. */
class PhysicsWorld(private val geo: RoomGeometry) {
    val bodies = ArrayList<PhysicsBody>()
    private var nextId = 1

    /** Wywoływane przy mocnym uderzeniu (do dźwięku). */
    var onImpact: ((PhysicsBody, Float) -> Unit)? = null

    fun add(spec: SpawnableSpec, position: Vec3, velocity: Vec3 = Vec3.ZERO, yaw: Float = 0f): PhysicsBody {
        val b = PhysicsBody(nextId++, spec, position, velocity)
        setYaw(b, yaw)
        bodies.add(b)
        return b
    }

    fun remove(b: PhysicsBody) = bodies.remove(b)

    fun clear() = bodies.clear()

    fun step(dt: Float, player: PlayerProxy? = null) {
        val n = 3
        val h = dt / n
        repeat(n) { substep(h, player) }
    }

    private fun substep(dt: Float, player: PlayerProxy?) {
        for (b in bodies) {
            b.age += dt
            b.onSupport = false
            b.velocity = Vec3(b.velocity.x, b.velocity.y - GRAVITY * dt, b.velocity.z)
            // opór powietrza
            b.velocity = b.velocity * (1f - 0.05f * dt)
            b.position = b.position + b.velocity * dt
            collideRoom(b)
            for (c in geo.colliders) collideBox(b, c.box)
            if (player != null) collidePlayer(b, player)
            if (b.onSupport) applyGroundFriction(b, dt)
            integrateRotation(b, dt)
        }
        for (i in bodies.indices) for (j in i + 1 until bodies.size) collidePair(bodies[i], bodies[j])
    }

    private fun bounceAlong(b: PhysicsBody, n: Vec3) {
        val vn = b.velocity.dot(n)
        if (vn >= 0f) return
        if (-vn > 1.2f) onImpact?.invoke(b, -vn)
        // Wolne kontakty bez odbicia — przedmioty spokojnie leżą zamiast drgać.
        val e = if (-vn < 0.6f) 0f else b.spec.bounce
        b.velocity = b.velocity - n * (vn * (1f + e))
        if (n.y > 0.7f) b.onSupport = true
    }

    private fun collideRoom(b: PhysicsBody) {
        val hh = b.halfHeight
        val hw = b.halfWidth
        var p = b.position
        if (p.y - hh < 0f) {
            p = p.withY(hh); b.position = p; bounceAlong(b, Vec3.UP)
        }
        if (p.y + hh > geo.height) {
            p = p.withY(geo.height - hh); b.position = p; bounceAlong(b, Vec3(0f, -1f, 0f))
        }
        if (p.x - hw < -geo.halfX) {
            p = Vec3(-geo.halfX + hw, p.y, p.z); b.position = p; bounceAlong(b, Vec3(1f, 0f, 0f))
        }
        if (p.x + hw > geo.halfX) {
            p = Vec3(geo.halfX - hw, p.y, p.z); b.position = p; bounceAlong(b, Vec3(-1f, 0f, 0f))
        }
        if (p.z - hw < -geo.halfZ) {
            p = Vec3(p.x, p.y, -geo.halfZ + hw); b.position = p; bounceAlong(b, Vec3(0f, 0f, 1f))
        }
        if (p.z + hw > geo.halfZ) {
            p = Vec3(p.x, p.y, geo.halfZ - hw); b.position = p; bounceAlong(b, Vec3(0f, 0f, -1f))
        }
    }

    /** Kula albo pionowy prostopadłościan kontra bryła mebla. */
    private fun collideBox(b: PhysicsBody, box: Aabb) {
        if (b.spec.rolls) {
            val p = b.position
            val q = Vec3(p.x.coerceIn(box.min.x, box.max.x), p.y.coerceIn(box.min.y, box.max.y), p.z.coerceIn(box.min.z, box.max.z))
            val diff = p - q
            val d = diff.length()
            val r = b.spec.radius
            if (d >= r) return
            val n = if (d > 1e-5f) diff * (1f / d) else Vec3.UP
            b.position = p + n * (r - d)
            bounceAlong(b, n)
            return
        }
        val me = b.box()
        val ox = min(me.max.x, box.max.x) - max(me.min.x, box.min.x)
        val oy = min(me.max.y, box.max.y) - max(me.min.y, box.min.y)
        val oz = min(me.max.z, box.max.z) - max(me.min.z, box.min.z)
        if (ox <= 0f || oy <= 0f || oz <= 0f) return
        val c = box.center
        val p = b.position
        // Najmniejsze zagłębienie wyznacza kierunek wypchnięcia; spadające z góry lądują na blacie.
        val fromAbove = p.y > c.y && b.velocity.y <= 0.5f && oy < 0.12f
        when {
            fromAbove || (oy <= ox && oy <= oz) -> {
                val s = if (p.y >= c.y) 1f else -1f
                b.position = p + Vec3(0f, oy * s, 0f)
                bounceAlong(b, Vec3(0f, s, 0f))
            }
            ox <= oz -> {
                val s = if (p.x >= c.x) 1f else -1f
                b.position = p + Vec3(ox * s, 0f, 0f)
                bounceAlong(b, Vec3(s, 0f, 0f))
            }
            else -> {
                val s = if (p.z >= c.z) 1f else -1f
                b.position = p + Vec3(0f, 0f, oz * s)
                bounceAlong(b, Vec3(0f, 0f, s))
            }
        }
    }

    private fun collidePair(a: PhysicsBody, b: PhysicsBody) {
        val d = b.position - a.position
        val dist = d.length()
        val r = a.halfWidth + b.halfWidth
        if (dist >= r || dist < 1e-5f) return
        val n = d * (1f / dist)
        val pen = r - dist
        val wa = 1f / a.spec.mass
        val wb = 1f / b.spec.mass
        val sum = wa + wb
        a.position = a.position - n * (pen * wa / sum)
        b.position = b.position + n * (pen * wb / sum)
        val rel = (b.velocity - a.velocity).dot(n)
        if (rel >= 0f) return
        val e = min(a.spec.bounce, b.spec.bounce)
        val j = -(1f + e) * rel / sum
        a.velocity = a.velocity - n * (j * wa)
        b.velocity = b.velocity + n * (j * wb)
        if (-rel > 1.2f) onImpact?.invoke(a, -rel)
    }

    /** Gracz jako pionowy walec: wypycha przedmioty i nadaje im swoją prędkość (kopnięcie). */
    private fun collidePlayer(b: PhysicsBody, pl: PlayerProxy) {
        val feet = pl.feet
        if (b.position.y + b.halfHeight < feet.y || b.position.y - b.halfHeight > feet.y + pl.height) return
        val dx = b.position.x - feet.x
        val dz = b.position.z - feet.z
        val dist = sqrt(dx * dx + dz * dz)
        val r = pl.radius + b.halfWidth
        if (dist >= r) return
        val nx = if (dist > 1e-4f) dx / dist else 1f
        val nz = if (dist > 1e-4f) dz / dist else 0f
        b.position = Vec3(feet.x + nx * r, b.position.y, feet.z + nz * r)
        val pv = pl.velocity
        val push = max(0f, pv.x * nx + pv.z * nz)
        val kick = (push * 1.6f + 0.3f) / max(0.2f, b.spec.mass * 2f).coerceAtMost(3f)
        val vn = b.velocity.x * nx + b.velocity.z * nz
        if (vn < kick) {
            b.velocity = Vec3(b.velocity.x + nx * (kick - vn), b.velocity.y + if (push > 0.5f) 0.8f * kick / 3f else 0f, b.velocity.z + nz * (kick - vn))
        }
    }

    private fun applyGroundFriction(b: PhysicsBody, dt: Float) {
        val k = if (b.spec.rolls) 0.35f else 4f * b.spec.friction
        val f = max(0f, 1f - k * dt)
        b.velocity = Vec3(b.velocity.x * f, b.velocity.y, b.velocity.z * f)
        if (abs(b.velocity.x) < 0.01f && abs(b.velocity.z) < 0.01f) b.velocity = Vec3(0f, b.velocity.y, 0f)
        b.spinY *= max(0f, 1f - 3f * dt)
    }

    private fun integrateRotation(b: PhysicsBody, dt: Float) {
        if (b.spec.rolls) {
            // toczenie: oś obrotu = UP × v, prędkość kątowa = |v| / r
            val v = b.velocity.horizontal()
            val speed = v.length()
            if (speed > 1e-3f && (b.onSupport || b.age < 0.05f)) {
                val axis = Vec3.UP.cross(v).normalized()
                rotate(b.rotation, axis, speed / b.spec.radius * dt)
            } else if (!b.onSupport) {
                rotate(b.rotation, Vec3(1f, 0f, 0f), b.spinY * dt)
            }
        } else if (abs(b.spinY) > 1e-3f) {
            rotate(b.rotation, Vec3.UP, b.spinY * dt)
        }
    }

    /** Promień kontra przedmioty (kule i pudełka jako kule). */
    fun raycast(o: Vec3, d: Vec3, maxT: Float): Pair<PhysicsBody, RayHit>? {
        var best: Pair<PhysicsBody, RayHit>? = null
        for (b in bodies) {
            val r = max(b.halfWidth, b.halfHeight)
            val oc = o - b.position
            val bb = oc.dot(d)
            val c = oc.dot(oc) - r * r
            val disc = bb * bb - c
            if (disc < 0f) continue
            val t = -bb - sqrt(disc)
            if (t < 0f || t > (best?.second?.t ?: maxT)) continue
            val p = o + d * t
            best = b to RayHit(t, p, (p - b.position).normalized())
        }
        return best
    }

    /** Uderzenie palcem / rzut w przedmiot. */
    fun push(b: PhysicsBody, impulse: Vec3) {
        b.velocity = b.velocity + impulse * (1f / b.spec.mass)
        b.spinY += (impulse.x - impulse.z) * 3f
    }

    companion object {
        const val GRAVITY = 9.81f

        fun setYaw(b: PhysicsBody, yaw: Float) {
            b.rotation[0] = 0f
            b.rotation[1] = sin(yaw / 2)
            b.rotation[2] = 0f
            b.rotation[3] = cos(yaw / 2)
        }

        /** q = rot(axis, angle) * q */
        fun rotate(q: FloatArray, axis: Vec3, angle: Float) {
            val s = sin(angle / 2)
            val rx = axis.x * s
            val ry = axis.y * s
            val rz = axis.z * s
            val rw = cos(angle / 2)
            val x = rw * q[0] + rx * q[3] + ry * q[2] - rz * q[1]
            val y = rw * q[1] - rx * q[2] + ry * q[3] + rz * q[0]
            val z = rw * q[2] + rx * q[1] - ry * q[0] + rz * q[3]
            val w = rw * q[3] - rx * q[0] - ry * q[1] - rz * q[2]
            val l = sqrt(x * x + y * y + z * z + w * w)
            q[0] = x / l; q[1] = y / l; q[2] = z / l; q[3] = w / l
        }
    }
}

/** To, czego fizyka potrzebuje od gracza. */
interface PlayerProxy {
    val feet: Vec3
    val velocity: Vec3
    val radius: Float
    val height: Float
}
