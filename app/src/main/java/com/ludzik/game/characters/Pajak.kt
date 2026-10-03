package com.ludzik.game.characters

import com.ludzik.game.audio.Sfx
import com.ludzik.game.characters.doodle.DoodlePen
import com.ludzik.game.characters.doodle.phase
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pająk narysowany długopisem: wspina się po ścianach i meblach,
 * zjeżdża na nitce. Długopisu nie da się wymazać, więc Gumka jest bezradna.
 */
class Pajak(id: Int, start: Vec3, surface: WalkSurface) : BaseCharacter(id, Kind, start, surface) {

    override val walkSpeed = 0.09f
    override val runSpeed = 0.22f
    override val erasable = false

    private var thread: Stroke? = null
    private var tickTimer = 0f
    private var afterPause: (() -> Unit)? = null

    override fun sense(dt: Float) {
        tickTimer -= dt
        if ((state == State.WALK || state == State.RUN) && tickTimer <= 0f) {
            tickTimer = 0.7f
            world.sound(Sfx.TICK, 0.35f)
        }
    }

    override fun onReactDone() {
        val next = afterPause
        afterPause = null
        next?.invoke()
    }

    override fun decide() {
        val s = surface
        if (s == null || (!airborne && !s.contains(position, 0f))) return defaultDecide()
        val r = rnd.nextFloat()
        when {
            r < 0.18f -> idle(0.6f + rnd.nextFloat() * 1.5f)
            r < 0.45f -> if (!wander(run = rnd.nextFloat() < 0.4f, radius = 0.7f)) idle(1f)
            r < 0.68f -> if (!tryClimbWall() && !tryClimbFurniture()) wander()
            r < 0.86f -> if (!tryRappel() && !tryClimbFurniture()) wander()
            else -> react(Anim.ACTION, 1.0f)
        }
    }

    /** Ostatni punkt odcinka from→to, który leży jeszcze na powierzchni. */
    private fun lastInside(s: WalkSurface, from: Vec3, to: Vec3): Vec3 {
        var best = from
        for (i in 1..20) {
            val p = from.lerp(to, i / 20f)
            if (s.contains(p, 0.03f)) best = p else break
        }
        return best.withY(s.height)
    }

    private fun tryClimbWall(): Boolean {
        val s = surface ?: return false
        val wall = world.walls
            .filter { it.distanceToWall(position) < 2f && it.wallTop - s.height > 0.35f }
            .minByOrNull { it.distanceToWall(position) } ?: return false
        val foot = wall.wallPointNear(position, s.height)
        val approach = lastInside(s, position, foot)
        if (approach.horizontalDistanceTo(foot) > 0.5f) return false
        val topY = (s.height + 0.3f + rnd.nextFloat() * 0.6f).coerceAtMost(wall.wallTop - 0.05f)
        val top = wall.wallPointNear(foot, topY)
        walkTo(approach) {
            airborne = true
            followPath(listOf(foot, top), 0.07f, Anim.CLIMB, null) {
                react(Anim.CLIMB, 0.8f + rnd.nextFloat())
                if (rnd.nextFloat() < 0.4f) world.say(this, "Widzę was z góry!", 1.6f)
                val n = wall.normal.horizontal().normalized()
                val hangStart = top + n * 0.05f
                val bottom = Vec3(hangStart.x + n.x * 0.04f, s.height, hangStart.z + n.z * 0.04f)
                afterPause = { descend(top, hangStart, bottom, s) }
            }
        }
        return true
    }

    /** Wspinaczka pionowo po krawędzi mebla (bez skoku). */
    private fun tryClimbFurniture(): Boolean {
        val s = surface ?: return false
        val dest = world.horizontalSurfaces
            .filter { it !== s && it.height - s.height in 0.15f..World.MAX_JUMP_HEIGHT }
            .shuffled(rnd)
            .firstOrNull { planTransfer(it)?.path == null && planTransfer(it) != null } ?: return false
        val tr = planTransfer(dest) ?: return false
        val edge = tr.takeoff.lerp(tr.landing, 0.5f)
        walkTo(tr.takeoff) {
            airborne = true
            followPath(
                listOf(Vec3(edge.x, s.height, edge.z), Vec3(edge.x, dest.height, edge.z), tr.landing),
                0.07f, Anim.CLIMB, dest,
            ) { airborne = false }
        }
        return true
    }

    /** Zjazd na nitce z wyższej płaszczyzny na niższą. */
    private fun tryRappel(): Boolean {
        val s = surface ?: return false
        val dest = world.horizontalSurfaces
            .filter { it !== s && s.height - it.height in 0.15f..World.MAX_JUMP_HEIGHT }
            .shuffled(rnd)
            .firstOrNull { planTransfer(it) != null } ?: return false
        val tr = planTransfer(dest) ?: return false
        if (tr.path != null) return false
        val anchor = tr.takeoff.lerp(tr.landing, 0.5f).withY(s.height)
        walkTo(tr.takeoff) {
            airborne = true
            world.say(this, listOf("Spadam stąd!", "Zjeżdżam!", "Wiszę sobie").random(rnd), 1.4f)
            followPath(listOf(anchor), 0.06f, Anim.CLIMB, null) {
                descend(anchor, anchor + Vec3(0f, -0.02f, 0f), tr.landing, dest)
            }
        }
        return true
    }

    private fun descend(anchor: Vec3, from: Vec3, bottom: Vec3, dest: WalkSurface) {
        val t = world.addStroke(Stroke(StrokeStyle.THREAD, 0.0025f, Kind.INK_THREAD, 1000f))
        t.points.add(anchor)
        t.points.add(from)
        thread = t
        world.sound(Sfx.ZIP, 0.6f)
        val bodyOffset = Kind.spriteSizeMeters * 0.35f
        followPath(listOf(from, bottom), 0.05f, Anim.HANG, dest, onStep = { p ->
            t.points[1] = p + Vec3(0f, bodyOffset, 0f)
        }) {
            airborne = false
            t.lifetime = t.age + 2.5f
            thread = null
        }
    }

    companion object Kind : CharacterKind(
        id = "pajak",
        displayName = "Pająk",
        spriteSizeMeters = 0.18f,
        phrases = listOf(
            "Tik-tik-tik!", "Wiszę sobie", "Długopisu nie da się wymazać!", "Osiem nóg, zero butów",
            "Pajęczynka gotowa?", "Nie bój się, jestem z długopisu", "Lubię wysokości",
        ),
    ) {
        private const val BLUE = DoodlePen.BALLPOINT
        const val INK_THREAD = 0xCC1E3A8A.toInt()

        override fun create(id: Int, position: Vec3, surface: WalkSurface) = Pajak(id, position, surface)

        override fun draw(pen: DoodlePen, anim: Anim, frame: Int) {
            val ph = phase(frame, framesPerAnim)
            pen.ink = BLUE
            pen.width = 0.016f
            var bx = 0.5f
            var by = 0.7f
            var rx = 0.12f
            var ry = 0.09f
            var curl = 0f
            var front = false
            var legLift = 0.03f
            when (anim) {
                Anim.IDLE -> legLift = 0.008f
                Anim.WALK -> legLift = 0.035f
                Anim.RUN -> {
                    legLift = 0.05f
                    by = 0.68f
                }
                Anim.JUMP -> {
                    by = 0.55f
                    curl = 0.5f
                }
                Anim.ACTION -> { // taniec
                    legLift = 0.06f
                    by = 0.68f + sin(ph) * 0.02f
                }
                Anim.CLIMB, Anim.HANG -> {
                    front = true
                    rx = 0.09f
                    ry = 0.13f
                    by = 0.55f
                    curl = if (anim == Anim.HANG) 0.45f else 0f
                    legLift = 0.04f
                }
            }
            // nogi: 4 po każdej stronie
            for (side in intArrayOf(-1, 1)) {
                for (i in 0 until 4) {
                    val wave = sin(ph + i * 1.57f + if (side > 0) 0.8f else 0f)
                    val sx = bx + side * rx * 0.6f
                    val sy = by - ry * 0.2f + i / 3f * ry * 0.5f
                    val reach = 0.24f * (1f - curl)
                    val kneeX: Float
                    val kneeY: Float
                    val footX: Float
                    val footY: Float
                    if (front) {
                        val ang = (-60f + i * 40f) * 0.0174533f
                        kneeX = sx + side * cos(ang) * reach * 0.7f
                        kneeY = sy + sin(ang) * reach * 0.7f - 0.05f
                        footX = kneeX + side * 0.06f * (1f - curl)
                        footY = kneeY + 0.08f + wave * legLift
                    } else {
                        // nogi jak zagnieżdżone łuki: zewnętrzne wyżej i szerzej
                        val lift = wave.coerceAtLeast(0f) * legLift
                        kneeX = sx + side * (0.21f - 0.05f * i) * (1f - curl * 0.5f)
                        kneeY = by - 0.21f + 0.05f * i - lift
                        footX = sx + side * (0.36f - 0.08f * i) * (1f - curl)
                        footY = 0.95f - lift - curl * 0.2f
                    }
                    pen.polyline(floatArrayOf(sx, sy, kneeX, kneeY, footX, footY), 0.014f, BLUE)
                }
            }
            pen.scribble(bx, by, rx, ry, 5, BLUE)
            // głowa z oczami
            val hx = if (front) bx else bx + rx * 0.95f
            val hy = if (front) by - ry * 0.95f else by - 0.02f
            pen.circle(hx, hy, 0.055f, 0.014f, BLUE, DoodlePen.PAPER)
            pen.dot(hx - 0.018f + if (front) 0f else 0.012f, hy - 0.008f, 0.011f, BLUE)
            pen.dot(hx + 0.018f + if (front) 0f else 0.012f, hy - 0.008f, 0.011f, BLUE)
        }
    }
}
