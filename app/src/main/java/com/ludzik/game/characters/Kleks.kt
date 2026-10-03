package com.ludzik.game.characters

import com.ludzik.game.audio.Sfx
import com.ludzik.game.characters.doodle.DoodlePen
import com.ludzik.game.characters.doodle.phase
import kotlin.math.cos
import kotlin.math.sin

/** Atramentowa plama: pełza, zostawia ślady, chlapie przy lądowaniu i boi się Gumki. */
class Kleks(id: Int, start: Vec3, surface: WalkSurface) : BaseCharacter(id, Kind, start, surface) {

    override val walkSpeed = 0.045f
    override val runSpeed = 0.24f
    private var sinceStain = 0f
    private var scaredCooldown = 0f

    override fun onMoved(distance: Float) {
        sinceStain += distance
        val s = surface ?: return
        if (sinceStain > 0.07f && !airborne) {
            sinceStain = 0f
            world.addDecal(
                if (rnd.nextBoolean()) DecalType.INK_A else DecalType.INK_B,
                position.withY(s.height),
                0.045f + rnd.nextFloat() * 0.035f,
                18f,
            )
        }
    }

    override fun sense(dt: Float) {
        scaredCooldown -= dt
        if (state == State.JUMP || state == State.PATH || state == State.RUN) return
        val eraser = world.nearest(this, 0.7f) { it is Gumka } ?: return
        if (!sameLevel(eraser)) return
        if (flee(eraser.position, 0.55f) && scaredCooldown <= 0f) {
            world.say(this, listOf("Ratunku!", "Tylko nie gumka!", "Chlup! Uciekam!").random(rnd), 1.5f)
            scaredCooldown = 4f
        }
    }

    override fun decide() {
        val s = surface
        if (s == null || !s.contains(position, 0f)) return defaultDecide()
        val r = rnd.nextFloat()
        when {
            r < 0.22f -> idle(1f + rnd.nextFloat() * 2f)
            r < 0.62f -> if (!wander(radius = 0.4f)) idle(1f)
            r < 0.74f -> hop(0.16f)
            r < 0.86f -> if (!tryVisitOtherSurface()) hop(0.12f)
            else -> {
                react(Anim.ACTION, 1.0f)
                splash(0.12f)
            }
        }
    }

    override fun onLanded() = splash(0.15f)

    private fun splash(size: Float) {
        val s = surface ?: return
        val base = position.withY(s.height)
        world.addDecal(DecalType.SPLASH, base, size, 20f)
        repeat(3) {
            val a = rnd.nextFloat() * 6.28f
            val r = size * (0.6f + rnd.nextFloat() * 0.5f)
            world.addDecal(DecalType.INK_B, base + Vec3(cos(a) * r, 0f, sin(a) * r), 0.02f + rnd.nextFloat() * 0.02f, 16f)
        }
        world.sound(Sfx.PLUM)
    }

    companion object Kind : CharacterKind(
        id = "kleks",
        displayName = "Kleks",
        spriteSizeMeters = 0.18f,
        phrases = listOf(
            "Plum!", "Chlup!", "Nie dotykaj, poplamisz się!", "Jestem artystą!",
            "Atrament to moja pasja", "Ojej, kapnęło mi się", "Bul bul!", "Jestem niebieski czy czarny?",
        ),
    ) {
        private const val INK = 0xFF16205A.toInt()
        private const val WHITE = 0xFFFFFFFF.toInt()

        override fun create(id: Int, position: Vec3, surface: WalkSurface) = Kleks(id, position, surface)

        override fun draw(pen: DoodlePen, anim: Anim, frame: Int) {
            val ph = phase(frame, framesPerAnim)
            val s = sin(ph)
            var cx = 0.5f
            var cy = 0.8f
            var rx = 0.2f
            var ry = 0.13f
            val drops = ArrayList<FloatArray>()
            when (anim) {
                Anim.IDLE -> {
                    rx += s * 0.008f
                    ry -= s * 0.008f
                }
                Anim.WALK -> {
                    rx = 0.21f + s * 0.035f
                    ry = 0.12f - s * 0.025f
                    cx = 0.5f + s * 0.02f
                    cy = 0.95f - ry
                }
                Anim.RUN -> {
                    rx = 0.25f + s * 0.02f
                    ry = 0.1f
                    cy = 0.85f
                    drops += floatArrayOf(0.18f, 0.82f + s * 0.03f, 0.022f)
                    drops += floatArrayOf(0.1f, 0.88f - s * 0.02f, 0.014f)
                }
                Anim.JUMP, Anim.CLIMB, Anim.HANG -> {
                    rx = 0.14f + s * 0.01f
                    ry = 0.2f
                    cy = 0.6f
                    drops += floatArrayOf(0.48f, 0.88f + s * 0.02f, 0.02f)
                    drops += floatArrayOf(0.58f, 0.93f, 0.013f)
                }
                Anim.ACTION -> { // chlapnięcie
                    rx = 0.3f
                    ry = 0.07f - s * 0.01f
                    cy = 0.89f
                    val up = 0.1f + frame * 0.06f
                    drops += floatArrayOf(0.2f, 0.8f - up, 0.025f)
                    drops += floatArrayOf(0.82f, 0.78f - up * 0.8f, 0.02f)
                    drops += floatArrayOf(0.5f, 0.7f - up * 1.2f, 0.03f)
                    drops += floatArrayOf(0.66f, 0.75f - up, 0.015f)
                }
            }
            pen.blob(cx, cy, rx, ry, 5, ph, INK, 0.018f)
            for (d in drops) pen.circle(d[0], d[1], d[2], 0.012f, INK, INK)
            // połysk
            pen.curve(floatArrayOf(cx - rx * 0.6f, cy - ry * 0.2f, cx - rx * 0.5f, cy - ry * 0.55f, cx - rx * 0.25f, cy - ry * 0.7f), 0.014f, WHITE)
            // oczy
            val ey = cy - ry * 0.25f
            val er = 0.04f
            pen.ellipse(cx + rx * 0.15f, ey, er, er * 1.2f, 0.01f, pen.ink, WHITE)
            pen.ellipse(cx + rx * 0.55f, ey, er, er * 1.2f, 0.01f, pen.ink, WHITE)
            pen.dot(cx + rx * 0.15f + 0.012f, ey + 0.005f, 0.016f)
            pen.dot(cx + rx * 0.55f + 0.012f, ey + 0.005f, 0.016f)
        }
    }
}
