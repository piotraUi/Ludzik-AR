package com.ludzik.game.player

import com.ludzik.game.characters.Vec3
import com.ludzik.game.physics.PhysicsWorld
import com.ludzik.game.physics.PlayerProxy
import com.ludzik.game.scene.RoomGeometry
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Gracz widziany z pierwszej osoby: pionowy walec, który chodzi, skacze, wchodzi na meble
 * i zderza się ze ścianami. yaw = 0 oznacza patrzenie w stronę -Z.
 */
class Player(private val geo: RoomGeometry) : PlayerProxy {
    override var feet = Vec3(0.6f, 0f, 1.4f)
        private set
    override var velocity = Vec3.ZERO
        private set
    override val radius = 0.28f
    override val height = 1.75f
    val eyeHeight = 1.6f

    var yaw = 0.35f
    var pitch = -0.18f
        set(value) {
            field = value.coerceIn(-1.45f, 1.45f)
        }

    var onGround = true
        private set

    /** Faza kroku — do bujania rąk na ekranie. */
    var walkPhase = 0f
        private set
    var walkAmount = 0f
        private set

    private var velY = 0f

    val eye get() = Vec3(feet.x, feet.y + eyeHeight, feet.z)

    fun forward(): Vec3 {
        val cp = cos(pitch)
        return Vec3(-sin(yaw) * cp, sin(pitch), -cos(yaw) * cp)
    }

    fun right() = Vec3(cos(yaw), 0f, -sin(yaw))

    fun up(): Vec3 = right().cross(forward()).normalized()

    fun look(dYaw: Float, dPitch: Float) {
        yaw += dYaw
        pitch += dPitch
    }

    fun jump() {
        if (onGround) {
            velY = JUMP_SPEED
            onGround = false
        }
    }

    fun teleport(p: Vec3) {
        feet = p
        velY = 0f
    }

    /**
     * @param moveX joystick w bok (-1..1, + = w prawo)
     * @param moveY joystick do przodu (-1..1, + = do przodu)
     */
    fun update(dt: Float, moveX: Float, moveY: Float) {
        val fwd = Vec3(-sin(yaw), 0f, -cos(yaw))
        val target = (right() * moveX + fwd * moveY) * WALK_SPEED
        // płynne przyspieszanie i hamowanie
        val k = min(1f, dt * if (onGround) 12f else 3f)
        val hv = velocity.horizontal()
        val nv = hv + (target - hv) * k
        velocity = Vec3(nv.x, velY, nv.z)

        moveAxis(nv.x * dt, 0f)
        moveAxis(0f, nv.z * dt)

        velY -= PhysicsWorld.GRAVITY * dt
        var y = feet.y + velY * dt
        val ground = geo.groundHeightAt(feet.x, feet.z, radius * 0.5f, max(feet.y, y) + STEP)
        if (y <= ground) {
            y = ground
            velY = 0f
            onGround = true
        } else {
            onGround = y - ground < 0.02f
        }
        if (y + height > geo.height) {
            y = geo.height - height
            velY = min(velY, 0f)
        }
        feet = feet.withY(y)
        velocity = Vec3(nv.x, velY, nv.z)

        val speed = nv.length()
        walkAmount += ((if (onGround) min(1f, speed / WALK_SPEED) else 0f) - walkAmount) * min(1f, dt * 8f)
        walkPhase += speed * dt * 4.2f
    }

    private fun moveAxis(dx: Float, dz: Float) {
        var x = feet.x + dx
        var z = feet.z + dz
        x = x.coerceIn(-geo.halfX + radius, geo.halfX - radius)
        z = z.coerceIn(-geo.halfZ + radius, geo.halfZ - radius)
        for (c in geo.colliders) {
            val b = c.box
            // na mebel da się wejść, jeśli jest niski (próg) albo już stoimy wyżej
            if (b.max.y <= feet.y + STEP) continue
            if (b.min.y >= feet.y + height) continue
            if (!b.containsXZ(x, z, radius)) continue
            // Już nachodzimy na bryłę (np. po lądowaniu na krawędzi) — pozwalamy z niej wyjść.
            if (b.containsXZ(feet.x, feet.z, radius - 1e-3f)) continue
            if (dx != 0f) x = if (dx > 0) min(x, b.min.x - radius) else max(x, b.max.x + radius)
            if (dz != 0f) z = if (dz > 0) min(z, b.min.z - radius) else max(z, b.max.z + radius)
        }
        // Zabezpieczenie: blokada nigdy nie przesuwa gracza dalej niż zamierzony krok.
        if (abs(x - feet.x) > abs(dx) + 1e-4f) x = feet.x
        if (abs(z - feet.z) > abs(dz) + 1e-4f) z = feet.z
        feet = Vec3(x, feet.y, z)
    }

    companion object {
        const val WALK_SPEED = 1.9f
        const val JUMP_SPEED = 4.3f
        const val STEP = 0.22f
    }
}
