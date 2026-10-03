package com.ludzik.ar.characters

import com.ludzik.ar.audio.Sfx
import com.ludzik.ar.characters.doodle.DoodlePen
import com.ludzik.ar.characters.doodle.limb
import com.ludzik.ar.characters.doodle.phase
import com.ludzik.ar.characters.doodle.rotated
import kotlin.math.abs
import kotlin.math.sin

/**
 * Ołówek: rysuje grafitowe linie na podłodze, a czasem most-rampę na wyższą
 * płaszczyznę. Po gotowym moście mogą chodzić inne postacie.
 */
class Olowek(id: Int, start: Vec3, surface: WalkSurface) : BaseCharacter(id, Kind, start, surface) {

    override val walkSpeed = 0.06f
    override val runSpeed = 0.15f

    private var floorLine: Stroke? = null
    private var bridgeCooldown = 3f
    private var scribbleTimer = 0f

    override fun sense(dt: Float) {
        bridgeCooldown -= dt
        scribbleTimer -= dt
        if (state != State.WALK) floorLine = null
    }

    override fun onMoved(distance: Float) {
        val line = floorLine ?: return
        val s = surface ?: return
        val p = position.withY(s.height + 0.002f)
        if (line.points.isEmpty() || line.points.last().distanceTo(p) > 0.02f) line.points.add(p)
        scribble(0.35f)
    }

    private fun scribble(volume: Float) {
        if (scribbleTimer <= 0f) {
            scribbleTimer = 0.45f
            world.sound(Sfx.SCRIBBLE, volume)
        }
    }

    override fun decide() {
        floorLine = null
        val s = surface
        if (s == null || !s.contains(position, 0f)) return defaultDecide()
        if (bridgeCooldown <= 0f && rnd.nextFloat() < 0.45f && tryDrawBridge()) return
        val r = rnd.nextFloat()
        when {
            r < 0.22f -> idle(0.8f + rnd.nextFloat() * 1.5f)
            r < 0.67f -> {
                if (wander(radius = 0.5f)) {
                    floorLine = world.addStroke(Stroke(StrokeStyle.FLOOR_LINE, 0.006f, GRAPHITE, 25f)).also {
                        it.points.add(position.withY(s.height + 0.002f))
                    }
                } else {
                    idle(1f)
                }
            }
            r < 0.8f -> if (!tryVisitOtherSurface()) wander()
            else -> {
                react(Anim.ACTION, 1.2f)
                scribble(0.6f)
            }
        }
    }

    /** Idzie do krawędzi i rysuje rampę na inną płaszczyznę (w górę albo w dół). */
    private fun tryDrawBridge(): Boolean {
        val src = surface ?: return false
        val candidates = world.horizontalSurfaces.filter {
            it !== src && abs(it.height - src.height) in 0.1f..1.0f && world.bridgeBetween(src, it) == null
        }.shuffled(rnd)
        for (dest in candidates) {
            val tr = planTransfer(dest) ?: continue
            if (tr.path != null) continue
            val goingUp = dest.height > src.height
            val dh = abs(dest.height - src.height)
            // Rampa ma ~45°: przesuwamy dolny koniec od krawędzi wyższej płaszczyzny.
            val low = if (goingUp) src else dest
            val lowEdge = if (goingUp) tr.takeoff else tr.landing
            val highEdge = if (goingUp) tr.landing else tr.takeoff
            val out = (lowEdge - highEdge).horizontal().normalized()
            var lowEnd = lowEdge
            for (f in floatArrayOf(0.9f, 0.6f, 0.35f)) {
                val c = lowEdge + out * (dh * f)
                if (low.contains(c, 0.03f)) {
                    lowEnd = c
                    break
                }
            }
            val start = if (goingUp) lowEnd else highEdge
            val end = if (goingUp) highEdge else lowEnd
            bridgeCooldown = 25f
            walkTo(start) { drawBridge(start, end, src, dest) }
            return true
        }
        bridgeCooldown = 6f
        return false
    }

    private fun drawBridge(start: Vec3, end: Vec3, src: WalkSurface, dest: WalkSurface) {
        val bridge = world.addStroke(Stroke(StrokeStyle.BRIDGE, 0.012f, GRAPHITE, 1000f))
        bridge.from = src
        bridge.to = dest
        bridge.points.add(start)
        world.say(this, "Rysuję most!", 1.6f)
        // Lekki łuk, żeby most wyglądał na rysowany ręką.
        val pts = ArrayList<Vec3>()
        val n = 10
        for (i in 1..n) {
            val t = i / n.toFloat()
            val p = start.lerp(end, t)
            pts += p.withY(p.y + sin(t * 3.1415927f) * 0.03f)
        }
        followPath(pts, 0.07f, Anim.ACTION, dest, onStep = { p ->
            if (bridge.points.last().distanceTo(p) > 0.015f) bridge.points.add(p)
            scribble(0.7f)
        }) {
            bridge.points.add(end)
            bridge.walkable = true
            bridge.lifetime = bridge.age + 50f
            world.say(this, "Most gotowy!", 1.6f)
        }
    }

    companion object Kind : CharacterKind(
        id = "olowek",
        displayName = "Ołówek",
        spriteSizeMeters = 0.26f,
        phrases = listOf(
            "Skrzyp, skrzyp!", "Narysuję ci most!", "Muszę się zatemperować", "Uwaga, ostry!",
            "HB to ja!", "Kreska za kreską", "Zaraz coś naszkicuję",
        ),
    ) {
        const val GRAPHITE = 0xD93A3A44.toInt()
        private const val YELLOW = 0xFFFFD54F.toInt()
        private const val WOOD = 0xFFF3D9B1.toInt()
        private const val PINK = 0xFFF48FB1.toInt()
        private const val METAL = 0xFFB0BEC5.toInt()

        override fun create(id: Int, position: Vec3, surface: WalkSurface) = Olowek(id, position, surface)

        override fun draw(pen: DoodlePen, anim: Anim, frame: Int) {
            val ph = phase(frame, framesPerAnim)
            val s = sin(ph)
            var tilt = 0f
            var lift = 0f
            var armSwing = s * 15f
            var drawing = false
            when (anim) {
                Anim.IDLE -> tilt = s * 2f
                Anim.WALK -> {
                    tilt = 8f + s * 10f
                    lift = -abs(s) * 0.025f
                }
                Anim.RUN -> {
                    tilt = 22f + s * 6f
                    lift = -abs(s) * 0.04f
                    armSwing = s * 40f
                }
                Anim.JUMP -> {
                    lift = -0.06f
                    armSwing = 150f
                }
                Anim.ACTION -> {
                    tilt = 28f + s * 6f
                    drawing = true
                }
                Anim.CLIMB, Anim.HANG -> {
                    armSwing = 150f + s * 20f
                    tilt = s * 5f
                }
            }
            val px = 0.5f
            val py = 0.95f + lift
            val hw = 0.075f
            fun part(y0: Float, y1: Float, fill: Int) =
                pen.polyline(rotated(floatArrayOf(px - hw, y0 + lift, px + hw, y0 + lift, px + hw, y1 + lift, px - hw, y1 + lift), px, py, tilt), closed = true, fill = fill)

            if (drawing) {
                // zygzak grafitu pod czubkiem
                val zz = FloatArray(10) { i -> if (i % 2 == 0) 0.2f + i * 0.03f + frame * 0.01f else 0.95f - (i % 4) * 0.01f }
                pen.polyline(zz, 0.012f, GRAPHITE)
            }
            part(0.1f, 0.19f, PINK)
            part(0.19f, 0.26f, METAL)
            part(0.26f, 0.74f, YELLOW)
            // drewniany stożek i grafit
            pen.polyline(rotated(floatArrayOf(px - hw, 0.74f + lift, px + hw, 0.74f + lift, px + 0.02f, 0.9f + lift, px - 0.02f, 0.9f + lift), px, py, tilt), closed = true, fill = WOOD)
            pen.polyline(rotated(floatArrayOf(px - 0.02f, 0.9f + lift, px + 0.02f, 0.9f + lift, px, py), px, py, tilt), closed = true, fill = pen.ink)
            // krawędzie sześciokąta
            val ribs = rotated(floatArrayOf(px - 0.025f, 0.27f + lift, px - 0.025f, 0.73f + lift, px + 0.025f, 0.27f + lift, px + 0.025f, 0.73f + lift), px, py, tilt)
            pen.line(ribs[0], ribs[1], ribs[2], ribs[3], 0.008f)
            pen.line(ribs[4], ribs[5], ribs[6], ribs[7], 0.008f)
            // twarz i rączki
            val f = rotated(floatArrayOf(px + 0.0f, 0.38f + lift, px + 0.045f, 0.38f + lift, px + 0.02f, 0.45f + lift, px - hw, 0.52f + lift, px + hw, 0.52f + lift), px, py, tilt)
            pen.dot(f[0], f[1], 0.012f)
            pen.dot(f[2], f[3], 0.012f)
            pen.curve(floatArrayOf(f[4] - 0.025f, f[5], f[4], f[5] + 0.015f, f[4] + 0.025f, f[5]), 0.011f)
            pen.limb(f[6], f[7], -40f - armSwing * 0.5f, 0.1f, 20f, 0.08f, 0.021f)
            pen.limb(f[8], f[9], 40f + armSwing, 0.1f, -20f, 0.08f, 0.021f)
        }
    }
}
