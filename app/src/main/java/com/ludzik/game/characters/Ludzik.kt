package com.ludzik.game.characters

import com.ludzik.game.characters.doodle.DoodlePen
import com.ludzik.game.characters.doodle.limb
import com.ludzik.game.characters.doodle.phase
import kotlin.math.abs
import kotlin.math.sin

/** Klasyczny ludzik z patyków: spokojny spacerowicz, macha do znajomych. */
class Ludzik(id: Int, start: Vec3, surface: WalkSurface) : BaseCharacter(id, Kind, start, surface) {

    override val walkSpeed = 0.07f
    override val runSpeed = 0.2f
    private var greetCooldown = 2f
    private var panicCooldown = 0f

    override fun sense(dt: Float) {
        greetCooldown -= dt
        panicCooldown -= dt
        if (state == State.JUMP || state == State.PATH) return

        val eraser = world.nearest(this, 0.45f) { it is Gumka && it.state == State.RUN }
        if (eraser != null && sameLevel(eraser) && state != State.RUN) {
            if (flee(eraser.position, 0.5f) && panicCooldown <= 0f) {
                world.say(this, listOf("Aaa! Gumka!", "Nie wymazuj mnie!", "Uciekam!").random(rnd), 1.6f)
                panicCooldown = 5f
            }
            return
        }
        if (greetCooldown <= 0f && (state == State.IDLE || state == State.WALK)) {
            val friend = world.nearest(this, 0.3f) { it !is Gumka }
            if (friend != null && sameLevel(friend)) {
                faceTowards(friend.position)
                react(Anim.ACTION, 1.6f)
                greetCooldown = 9f
                if (rnd.nextFloat() < 0.5f) world.say(this, "Cześć, ${friend.kind.displayName}!", 2f)
            }
        }
    }

    companion object Kind : CharacterKind(
        id = "ludzik",
        displayName = "Ludzik",
        spriteSizeMeters = 0.24f,
        phrases = listOf(
            "Cześć!", "Ładny pokój!", "Hej, to łaskocze!", "Jestem z zeszytu od matmy",
            "Uważaj, bo się rozmażę!", "Gdzie moja kartka?", "Idę na spacerek", "Ale tu wysoko!",
            "Pa pa!", "Kto mnie narysował?",
        ),
    ) {
        override fun create(id: Int, position: Vec3, surface: WalkSurface) = Ludzik(id, position, surface)

        override fun draw(pen: DoodlePen, anim: Anim, frame: Int) {
            val ph = phase(frame, framesPerAnim)
            val s = sin(ph)
            var lean = 0f
            var bob = 0f
            var hipY = 0.6f
            // kąty: [lewe ramię, prawe ramię, lewa noga, prawa noga] + zgięcia
            var armA = floatArrayOf(-20f, 20f)
            var armB = floatArrayOf(10f, -10f)
            var legA = floatArrayOf(-8f, 8f)
            var legB = floatArrayOf(0f, 0f)
            var smile = 0.02f
            when (anim) {
                Anim.IDLE -> {
                    bob = s * 0.006f
                    armA = floatArrayOf(-14f - s * 4f, 14f + s * 4f)
                }
                Anim.WALK -> {
                    val sw = s * 28f
                    legA = floatArrayOf(sw, -sw)
                    legB = floatArrayOf(-abs(sw) * 0.4f, -abs(sw) * 0.4f)
                    armA = floatArrayOf(-sw * 0.8f, sw * 0.8f)
                    bob = -abs(s) * 0.012f
                }
                Anim.RUN -> {
                    val sw = s * 48f
                    lean = 0.05f
                    legA = floatArrayOf(sw + 8f, -sw + 8f)
                    legB = floatArrayOf(-45f, -45f)
                    armA = floatArrayOf(-sw + 30f, sw + 30f)
                    armB = floatArrayOf(70f, 70f)
                    bob = -abs(s) * 0.025f
                    smile = -0.01f
                }
                Anim.JUMP -> {
                    hipY = 0.5f
                    legA = floatArrayOf(-35f, 40f)
                    legB = floatArrayOf(60f, -70f)
                    armA = floatArrayOf(-150f - s * 10f, 150f + s * 10f)
                    armB = floatArrayOf(0f, 0f)
                    smile = 0.03f
                }
                Anim.ACTION -> { // machanie
                    armA = floatArrayOf(-15f, 118f + s * 22f)
                    armB = floatArrayOf(5f, 25f + s * 20f)
                    bob = s * 0.004f
                    smile = 0.035f
                }
                Anim.CLIMB -> {
                    armA = floatArrayOf(-160f + s * 20f, 160f + s * 20f)
                    armB = floatArrayOf(0f, 0f)
                    legA = floatArrayOf(-20f - s * 15f, 20f - s * 15f)
                    legB = floatArrayOf(30f, -30f)
                }
                Anim.HANG -> {
                    armA = floatArrayOf(-170f, 170f)
                    armB = floatArrayOf(0f, 0f)
                    legA = floatArrayOf(s * 12f, s * 12f + 6f)
                    hipY = 0.55f
                }
            }
            val headX = 0.5f + lean
            val headY = 0.21f + bob + (hipY - 0.6f)
            val neckY = headY + 0.09f
            val hip = hipY + bob

            pen.circle(headX, headY, 0.085f)
            pen.line(headX - lean * 0.3f, neckY, 0.5f, hip)
            val shoulderY = neckY + 0.05f
            val sx = headX - lean * 0.4f
            pen.limb(sx, shoulderY, armA[0], 0.13f, armB[0], 0.12f)
            pen.limb(sx, shoulderY, armA[1], 0.13f, armB[1], 0.12f)
            pen.limb(0.5f, hip, legA[0], 0.18f, legB[0], 0.17f)
            pen.limb(0.5f, hip, legA[1], 0.18f, legB[1], 0.17f)
            // twarz patrzy w prawo
            pen.dot(headX + 0.012f, headY - 0.018f, 0.011f)
            pen.dot(headX + 0.05f, headY - 0.018f, 0.011f)
            pen.curve(
                floatArrayOf(headX - 0.005f, headY + 0.03f, headX + 0.025f, headY + 0.03f + smile, headX + 0.055f, headY + 0.025f),
                0.012f,
            )
        }
    }
}
