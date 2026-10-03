package com.ludzik.game.game

import com.ludzik.game.audio.Haptic
import com.ludzik.game.audio.Sfx
import com.ludzik.game.characters.Character
import com.ludzik.game.characters.CharacterKind
import com.ludzik.game.characters.CharacterRegistry
import com.ludzik.game.characters.Vec3
import com.ludzik.game.characters.World
import com.ludzik.game.characters.WorldListener
import com.ludzik.game.math.RayHit
import com.ludzik.game.physics.PhysicsBody
import com.ludzik.game.physics.PhysicsWorld
import com.ludzik.game.player.Player
import com.ludzik.game.scene.CharacterSpace
import com.ludzik.game.scene.RoomGeometry
import com.ludzik.game.scene.RoomLayout
import com.ludzik.game.scene.SpawnableSpec
import com.ludzik.game.scene.StaticHit
import com.ludzik.game.scene.SurfaceKind
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/** To, co można zrespić: ludzik z zeszytu albo realistyczny przedmiot. */
sealed interface SpawnItem {
    val label: String

    data class Doodle(val kind: CharacterKind) : SpawnItem {
        override val label get() = kind.displayName
    }

    data class Thing(val spec: SpawnableSpec) : SpawnItem {
        override val label get() = spec.name
    }

    companion object {
        val all: List<SpawnItem> = CharacterRegistry.kinds.map { Doodle(it) } + RoomLayout.spawnables.map { Thing(it) }
    }
}

/** Co jest na celowniku. */
sealed interface AimTarget {
    val hit: RayHit

    class Static(val s: StaticHit) : AimTarget {
        override val hit get() = s.hit
    }

    class Body(val body: PhysicsBody, override val hit: RayHit) : AimTarget
    class Doodle(val character: Character, override val hit: RayHit) : AimTarget
}

interface GameEvents {
    fun onSound(sfx: Sfx, volume: Float)
    fun onHaptic(h: Haptic)
    fun onMessage(text: String)
}

/**
 * Cała logika gry bez Androida: gracz, fizyka przedmiotów, ludziki i respienie.
 * Wywoływana co klatkę z wątku UI ([step]); renderer tylko czyta stan.
 */
class GameController(random: Random = Random.Default) {
    val geometry = RoomGeometry()
    val space = CharacterSpace(geometry)
    val world = World(random).apply { surfaces = space.all }
    val physics = PhysicsWorld(geometry)
    val player = Player(geometry)
    private val rnd = random

    var events: GameEvents? = null
        set(value) {
            field = value
            world.listener = value?.let { ev ->
                object : WorldListener {
                    override fun onSound(sfx: Sfx, volume: Float) = ev.onSound(sfx, volume)
                    override fun onHaptic(haptic: Haptic) = ev.onHaptic(haptic)
                }
            }
        }

    var selected: SpawnItem = SpawnItem.all.first()

    // wejście z interfejsu
    var moveX = 0f
    var moveY = 0f

    private val hitCooldown = HashMap<Int, Float>()
    var time = 0f
        private set

    init {
        physics.onImpact = { _, speed ->
            events?.onSound(Sfx.THUD, (speed / 5f).coerceIn(0.2f, 1f))
        }
    }

    fun step(dt: Float) {
        time += dt
        player.update(dt, moveX, moveY)
        physics.step(dt, player)
        world.update(dt)
        bodiesHitDoodles(dt)
    }

    fun look(dYaw: Float, dPitch: Float) = player.look(dYaw, dPitch)

    fun jump() = player.jump()

    // ------------------------------------------------------------------ celowanie

    fun aim(maxDist: Float = 12f): AimTarget? = raycast(player.eye, player.forward(), maxDist)

    /** Kierunek promienia dla punktu ekranu (nx, ny w -1..1, ny w górę). */
    fun rayThrough(nx: Float, ny: Float, aspect: Float): Vec3 {
        val tanV = tan(Math.toRadians(FOV_VERTICAL_DEG / 2.0)).toFloat()
        val tanH = tanV * aspect
        return (player.forward() + player.right() * (nx * tanH) + player.up() * (ny * tanV)).normalized()
    }

    fun raycast(o: Vec3, d: Vec3, maxDist: Float): AimTarget? {
        var best: AimTarget? = geometry.raycast(o, d, maxDist)?.let { AimTarget.Static(it) }
        physics.raycast(o, d, best?.hit?.t ?: maxDist)?.let { (b, h) -> best = AimTarget.Body(b, h) }
        for (c in world.characters) {
            if (c.isErased) continue
            val size = c.kind.spriteSizeMeters * CharacterSpace.SCALE
            val center = space.toReal(c.position) + Vec3(0f, size * 0.42f, 0f)
            val r = size * 0.32f
            val oc = o - center
            val b = oc.dot(d)
            val disc = b * b - (oc.dot(oc) - r * r)
            if (disc < 0f) continue
            val t = -b - sqrt(disc)
            if (t < 0f || t > (best?.hit?.t ?: maxDist)) continue
            best = AimTarget.Doodle(c, RayHit(t, o + d * t, (o + d * t - center).normalized()))
        }
        return best
    }

    // ------------------------------------------------------------------ respienie

    /** Respi wybrany element tam, gdzie patrzy celownik. */
    fun spawnAtCrosshair(): Boolean {
        val target = aim()
        return when (val item = selected) {
            is SpawnItem.Doodle -> spawnDoodle(item.kind, target)
            is SpawnItem.Thing -> spawnThing(item.spec, target)
        }
    }

    private fun spawnDoodle(kind: CharacterKind, target: AimTarget?): Boolean {
        if (world.isFull) {
            events?.onMessage("W pokoju mieści się najwyżej ${World.MAX_CHARACTERS} ludzików!")
            return false
        }
        val s = (target as? AimTarget.Static)?.s
        if (s == null) {
            events?.onMessage("Celuj w podłogę albo w blat stołu.")
            return false
        }
        var p = s.hit.point
        var surface = if (s.hit.normal.y > 0.7f) space.surfaceAt(p) else null
        if (surface == null && s.kind != SurfaceKind.CEILING) {
            // Trafienie w ścianę/bok mebla — stawiamy na podłodze tuż przed nim.
            val back = (player.feet - p).horizontal().normalized() * 0.35f
            val floorP = Vec3(p.x + back.x, 0f, p.z + back.z)
            if (space.floor.contains(space.toSim(floorP), 0.02f)) {
                p = floorP
                surface = space.floor
            }
        }
        if (surface == null) {
            events?.onMessage("Tu ${kind.displayName} się nie zmieści — celuj w podłogę albo blat.")
            return false
        }
        world.spawn(kind, space.toSim(p).withY(surface.height), surface)
        return true
    }

    private fun spawnThing(spec: SpawnableSpec, target: AimTarget?): Boolean {
        val hit = target?.hit
        val base = if (hit != null) hit.point + hit.normal * (spec.radius + 0.03f) else player.eye + player.forward() * 1.5f
        // Przedmiot pojawia się trochę nad celem i spada — widać, gdzie wylądował.
        val p = Vec3(
            base.x.coerceIn(-geometry.halfX + spec.radius, geometry.halfX - spec.radius),
            (base.y + 0.45f).coerceAtMost(geometry.height - spec.radius - 0.05f),
            base.z.coerceIn(-geometry.halfZ + spec.radius, geometry.halfZ - spec.radius),
        )
        addBody(spec, p, Vec3((rnd.nextFloat() - 0.5f) * 0.3f, 0f, (rnd.nextFloat() - 0.5f) * 0.3f))
        events?.onSound(Sfx.POP, 0.8f)
        events?.onHaptic(Haptic.LIGHT)
        return true
    }

    /** Rzuca wybrany przedmiot przed siebie. */
    fun throwSelected(): Boolean {
        val spec = (selected as? SpawnItem.Thing)?.spec ?: return false
        val f = player.forward()
        val p = player.eye + f * 0.45f + player.right() * 0.18f - Vec3(0f, 0.15f, 0f)
        val b = addBody(spec, p, f * THROW_SPEED + Vec3(0f, 1.2f, 0f) + player.velocity.horizontal())
        b.spinY = (rnd.nextFloat() - 0.5f) * 10f
        events?.onSound(Sfx.BOING, 0.6f)
        events?.onHaptic(Haptic.LIGHT)
        return true
    }

    private fun addBody(spec: SpawnableSpec, p: Vec3, v: Vec3): PhysicsBody {
        val same = physics.bodies.filter { it.spec === spec }
        if (same.size >= MAX_PER_TYPE) physics.remove(same.minBy { it.id })
        return physics.add(spec, p, v, rnd.nextFloat() * 6.28f)
    }

    /** Dotknięcie ekranu: zaczepia ludzika albo popycha przedmiot. */
    fun interact(dir: Vec3) {
        when (val t = raycast(player.eye, dir, 12f)) {
            is AimTarget.Doodle -> t.character.onTouched(world)
            is AimTarget.Body -> {
                physics.push(t.body, (dir + Vec3(0f, 0.4f, 0f)) * (1.4f * t.body.spec.mass.coerceAtMost(1.5f)))
                events?.onSound(Sfx.THUD, 0.5f)
                events?.onHaptic(Haptic.LIGHT)
            }
            else -> Unit
        }
    }

    fun clearAll() {
        world.clear()
        physics.clear()
    }

    /** Lecący przedmiot trafia ludzika → ten podskakuje i krzyczy. */
    private fun bodiesHitDoodles(dt: Float) {
        val it = hitCooldown.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            e.setValue(e.value - dt)
            if (e.value <= 0f) it.remove()
        }
        for (b in physics.bodies) {
            if (b.velocity.length() < 1.0f) continue
            for (c in world.characters) {
                if (c.isErased || hitCooldown.containsKey(c.id)) continue
                val size = c.kind.spriteSizeMeters * CharacterSpace.SCALE
                val center = space.toReal(c.position) + Vec3(0f, size * 0.4f, 0f)
                if (center.distanceTo(b.position) < b.halfWidth + size * 0.3f && abs(center.y - b.position.y) < size) {
                    c.onTouched(world)
                    world.say(c, listOf("Ała!", "Hej, uważaj!", "Kto rzuca?!", "Auć!").random(rnd), 1.6f)
                    hitCooldown[c.id] = 1.2f
                }
            }
        }
    }

    companion object {
        const val FOV_VERTICAL_DEG = 62.0
        const val MAX_PER_TYPE = 6
        const val THROW_SPEED = 5.5f
    }
}
