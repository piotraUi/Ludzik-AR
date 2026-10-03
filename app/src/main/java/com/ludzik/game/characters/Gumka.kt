package com.ludzik.game.characters

import com.ludzik.game.audio.Haptic
import com.ludzik.game.audio.Sfx
import com.ludzik.game.characters.doodle.DoodlePen
import com.ludzik.game.characters.doodle.limb
import com.ludzik.game.characters.doodle.phase
import com.ludzik.game.characters.doodle.rotated
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Gumka do mazania: poluje na inne postacie i je wymazuje (znikają na kilka sekund). */
class Gumka(id: Int, start: Vec3, surface: WalkSurface) : BaseCharacter(id, Kind, start, surface) {

    override val walkSpeed = 0.06f
    override val runSpeed = 0.2f
    override val erasable = false

    private var prey: Character? = null
    private var chaseTime = 0f
    private var cooldown = 3f
    private var crumbTimer = 0f

    override fun sense(dt: Float) {
        cooldown -= dt
        val p = prey ?: return
        if (p.isErased || state != State.RUN) {
            prey = null
            return
        }
        chaseTime += dt
        if (chaseTime > 7f) {
            prey = null
            cooldown = 4f
            if (rnd.nextFloat() < 0.5f) world.say(this, "Uciekł mi...", 1.5f)
            idle(1.2f)
            return
        }
        if (p.surface !== surface) {
            // Ofiara uciekła na inną płaszczyznę — za chwilę spróbujemy ją dogonić.
            prey = null
            cooldown = 0.8f
            idle(0.4f)
            return
        }
        retarget(p.position)
        if (p.position.horizontalDistanceTo(position) < 0.08f && abs(p.position.y - position.y) < 0.08f) catch(p)
    }

    private fun catch(p: Character) {
        prey = null
        cooldown = 6f
        if (p.erase(world, 4.5f)) {
            faceTowards(p.position)
            react(Anim.ACTION, 1.0f)
            world.sound(Sfx.SQUEAK)
            world.haptic(Haptic.MEDIUM)
            if (rnd.nextFloat() < 0.5f) world.say(this, listOf("Wymazane!", "Szur szur!", "Czysto!").random(rnd), 1.6f)
        } else {
            react(Anim.IDLE, 1.0f)
            world.say(this, "Długopisu nie zmażę!", 1.8f)
        }
    }

    override fun afterUpdate(dt: Float) {
        if (state == State.REACT && anim == Anim.ACTION) {
            crumbTimer -= dt
            val s = surface
            if (crumbTimer <= 0f && s != null) {
                crumbTimer = 0.25f
                world.addDecal(DecalType.CRUMBS, position.withY(s.height) + randomDirection() * 0.04f, 0.06f, 10f)
            }
        }
    }

    override fun decide() {
        val s = surface
        if (s != null && s.contains(position, 0f) && cooldown <= 0f) {
            val victim = world.nearest(this, 2.5f) { it !is Gumka }
            if (victim != null) {
                val vs = victim.surface
                if (vs === s && sameLevel(victim)) {
                    prey = victim
                    chaseTime = 0f
                    walkTo(victim.position, run = true)
                    if (rnd.nextFloat() < 0.3f) world.say(this, "Mam cię!", 1.2f)
                    return
                }
                if (vs != null && goToSurface(vs, run = true)) {
                    cooldown = 0.5f
                    return
                }
            }
        }
        defaultDecide()
    }

    companion object Kind : CharacterKind(
        id = "gumka",
        displayName = "Gumka",
        spriteSizeMeters = 0.18f,
        phrases = listOf(
            "Wymażę cię!", "Nie przeszkadzaj, mażę!", "Szur, szur!", "Zostaw, bo zmażę!",
            "Porządek musi być!", "Kto tu nabazgrał?", "Jestem dwukolorowa!",
        ),
    ) {
        private const val PINK = 0xFFF48FB1.toInt()
        private const val BLUE = 0xFF64B5F6.toInt()

        override fun create(id: Int, position: Vec3, surface: WalkSurface) = Gumka(id, position, surface)

        override fun draw(pen: DoodlePen, anim: Anim, frame: Int) {
            val ph = phase(frame, framesPerAnim)
            val s = sin(ph)
            var tilt = 0f
            var lift = 0f
            var legSwing = 0f
            var angry = false
            var crumbs = false
            when (anim) {
                Anim.IDLE -> lift = s * 0.006f
                Anim.WALK -> {
                    legSwing = s * 25f
                    lift = -abs(s) * 0.015f
                    tilt = s * 3f
                }
                Anim.RUN -> {
                    legSwing = s * 45f
                    lift = -abs(s) * 0.03f
                    tilt = 12f
                    angry = true
                }
                Anim.JUMP, Anim.CLIMB, Anim.HANG -> {
                    lift = -0.08f
                    legSwing = 30f
                }
                Anim.ACTION -> {
                    tilt = s * 16f
                    angry = true
                    crumbs = true
                }
            }
            val cx = 0.5f
            val cy = 0.62f + lift
            val hw = 0.21f
            val hh = 0.15f
            // nóżki
            val legY = cy + hh - 0.01f
            pen.limb(cx - 0.08f, legY, -legSwing, 0.1f, 10f, 0.09f)
            pen.limb(cx + 0.08f, legY, legSwing, 0.1f, 10f, 0.09f)
            // dwukolorowy klocek gumki (lekko skośny)
            val left = rotated(floatArrayOf(cx - hw, cy - hh + 0.02f, cx, cy - hh, cx, cy + hh, cx - hw, cy + hh - 0.02f), cx, cy, tilt)
            val right = rotated(floatArrayOf(cx, cy - hh, cx + hw, cy - hh + 0.025f, cx + hw, cy + hh - 0.015f, cx, cy + hh), cx, cy, tilt)
            pen.polyline(left, closed = true, fill = PINK)
            pen.polyline(right, closed = true, fill = BLUE)
            // rączki
            val arm = rotated(floatArrayOf(cx - hw, cy, cx + hw, cy), cx, cy, tilt)
            pen.limb(arm[0], arm[1], -60f - s * 20f, 0.08f, -20f, 0.06f, 0.021f)
            pen.limb(arm[2], arm[3], 60f + s * 20f, 0.08f, 20f, 0.06f, 0.021f)
            // twarz po niebieskiej stronie
            val face = rotated(floatArrayOf(cx + 0.06f, cy - 0.04f, cx + 0.14f, cy - 0.04f, cx + 0.1f, cy + 0.05f), cx, cy, tilt)
            pen.dot(face[0], face[1], 0.015f)
            pen.dot(face[2], face[3], 0.015f)
            if (angry) {
                pen.line(face[0] - 0.025f, face[1] - 0.05f, face[0] + 0.02f, face[1] - 0.03f, 0.013f)
                pen.line(face[2] + 0.025f, face[3] - 0.05f, face[2] - 0.02f, face[3] - 0.03f, 0.013f)
                pen.line(face[4] - 0.03f, face[5], face[4] + 0.03f, face[5], 0.013f)
            } else {
                pen.curve(floatArrayOf(face[4] - 0.03f, face[5] - 0.005f, face[4], face[5] + 0.015f, face[4] + 0.03f, face[5] - 0.005f), 0.012f)
            }
            if (crumbs) {
                for (i in 0 until 5) {
                    val a = ph + i * 1.3f
                    pen.dot(0.5f + cos(a) * 0.25f, 0.93f - abs(sin(a)) * 0.05f, 0.012f, PINK)
                }
            }
        }
    }
}
