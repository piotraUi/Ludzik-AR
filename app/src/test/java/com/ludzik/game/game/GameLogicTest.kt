package com.ludzik.game.game

import com.ludzik.game.audio.Haptic
import com.ludzik.game.audio.Sfx
import com.ludzik.game.characters.CharacterRegistry
import com.ludzik.game.characters.Ludzik
import com.ludzik.game.characters.Vec3
import com.ludzik.game.scene.CharacterSpace
import com.ludzik.game.scene.RoomLayout
import com.ludzik.game.scene.SurfaceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.random.Random

/** Logika gry 3D bez Filament: kolizje, fizyka, celowanie, respienie i życie ludzików w pokoju. */
class GameLogicTest {

    private class Log : GameEvents {
        val messages = ArrayList<String>()
        val sounds = HashMap<Sfx, Int>()
        override fun onSound(sfx: Sfx, volume: Float) {
            sounds[sfx] = (sounds[sfx] ?: 0) + 1
        }
        override fun onHaptic(h: Haptic) {}
        override fun onMessage(text: String) {
            messages += text
        }
    }

    private fun game(seed: Int = 1) = GameController(Random(seed)).also { it.events = Log() }

    /** Ustawia gracza, by patrzył na punkt. */
    private fun lookAt(g: GameController, target: Vec3) {
        val d = target - g.player.eye
        g.player.yaw = atan2(-d.x, -d.z)
        g.player.pitch = atan2(d.y, sqrt(d.x * d.x + d.z * d.z))
    }

    private fun run(g: GameController, seconds: Float) {
        repeat((seconds * 60).toInt()) { g.step(1f / 60f) }
    }

    @Test
    fun layoutIsInsideRoom() {
        for (f in RoomLayout.furniture) {
            val b = f.bounds
            assertTrue("${f.file} poza pokojem: $b", b.min.x >= -RoomLayout.HALF_X - 0.05f && b.max.x <= RoomLayout.HALF_X + 0.05f)
            assertTrue("${f.file} poza pokojem: $b", b.min.z >= -RoomLayout.HALF_Z - 0.05f && b.max.z <= RoomLayout.HALF_Z + 0.05f)
        }
        // gracz nie startuje w meblu
        val g = game()
        assertTrue(g.geometry.colliders.none { it.box.containsXZ(g.player.feet.x, g.player.feet.z, g.player.radius) && it.box.max.y > 0.3f })
    }

    @Test
    fun aimingDownHitsFloorAndSpawnsDoodle() {
        val g = game()
        g.player.pitch = -1.3f
        val t = g.aim()
        assertTrue(t is AimTarget.Static && t.s.kind == SurfaceKind.FLOOR)
        g.selected = SpawnItem.Doodle(Ludzik)
        assertTrue(g.spawnAtCrosshair())
        assertEquals(1, g.world.characters.size)
        assertEquals(g.space.floor, g.world.characters[0].surface)
    }

    @Test
    fun spawnOnTableTop() {
        val g = game()
        val table = RoomLayout.furniture.first { it.file.contains("round_wooden_table") }
        lookAt(g, Vec3(table.bounds.center.x, table.walk!!.y, table.bounds.center.z))
        val t = g.aim()
        assertTrue("celownik: $t", t is AimTarget.Static && t.s.kind == SurfaceKind.FURNITURE)
        g.selected = SpawnItem.Doodle(Ludzik)
        assertTrue(g.spawnAtCrosshair())
        val c = g.world.characters.single()
        assertTrue("ludzik powinien stać na stole", abs(c.position.y * CharacterSpace.SCALE - table.walk!!.y) < 0.05f)
    }

    @Test
    fun droppedBallBouncesAndRests() {
        val g = game()
        val ball = RoomLayout.spawnables.first { it.rolls }
        val b = g.physics.add(ball, Vec3(0.3f, 1.8f, 0.2f))
        var bounced = false
        var prevVy = 0f
        repeat(60 * 6) {
            g.step(1f / 60f)
            if (prevVy < -1f && b.velocity.y > 0.5f) bounced = true
            prevVy = b.velocity.y
        }
        assertTrue("piłka powinna się odbić", bounced)
        assertEquals("piłka leży na podłodze", ball.radius, b.position.y, 0.02f)
        assertTrue(b.velocity.length() < 0.3f)
    }

    @Test
    fun boxLandsOnCoffeeTable() {
        val g = game()
        val box = RoomLayout.spawnables.first { !it.rolls && it.id.contains("cardboard") }
        val table = RoomLayout.furniture.first { it.file.contains("coffee_table") }
        val c = table.bounds.center
        val b = g.physics.add(box, Vec3(c.x, 1.4f, c.z))
        run(g, 4f)
        assertEquals("karton leży na stoliku", table.bounds.max.y + box.halfHeight, b.position.y, 0.03f)
    }

    @Test
    fun thingsStayInRoomAfterThrowing() {
        val g = game()
        for (spec in RoomLayout.spawnables) {
            g.selected = SpawnItem.Thing(spec)
            repeat(3) {
                g.player.yaw += 0.7f
                g.player.pitch = 0.2f
                assertTrue(g.throwSelected())
                run(g, 0.3f)
            }
        }
        run(g, 8f)
        for (b in g.physics.bodies) {
            val p = b.position
            assertTrue("${b.spec.id} uciekł: $p", abs(p.x) <= RoomLayout.HALF_X && abs(p.z) <= RoomLayout.HALF_Z && p.y >= 0f && p.y <= RoomLayout.HEIGHT)
            assertTrue("${b.spec.id} nie leży: ${b.velocity}", b.velocity.length() < 0.5f)
        }
    }

    @Test
    fun playerIsBlockedBySofaButCanJumpOnTable() {
        val g = game()
        val sofa = RoomLayout.furniture.first { it.file.contains("sofa") }
        // idziemy prosto na sofę
        g.player.teleport(Vec3(-0.9f, 0f, -1.4f))
        lookAt(g, Vec3(sofa.bounds.center.x, 1.6f, -1.4f))
        g.player.pitch = 0f
        g.moveY = 1f
        run(g, 3f)
        assertTrue("gracz wszedł w sofę: ${g.player.feet}", g.player.feet.x > sofa.bounds.max.x + g.player.radius - 0.02f || g.player.feet.z < sofa.bounds.min.z)

        // skok na niski stolik kawowy
        val table = RoomLayout.furniture.first { it.file.contains("coffee_table") }
        g.player.teleport(Vec3(table.bounds.center.x + 0.75f, 0f, table.bounds.center.z))
        g.player.yaw = 1.5708f // patrzymy w -X
        g.player.pitch = 0f
        g.moveY = 0f
        run(g, 0.5f)
        g.jump()
        g.moveY = 1f
        run(g, 0.45f)
        g.moveY = 0f
        run(g, 1f)
        assertEquals("gracz stoi na stoliku", table.bounds.max.y, g.player.feet.y, 0.02f)
    }

    @Test
    fun ballKickedByWalkingPlayer() {
        val g = game()
        val ball = RoomLayout.spawnables.first { it.rolls && it.radius > 0.08f }
        g.player.teleport(Vec3(0.05f, 0f, 1.5f))
        g.player.yaw = 0f
        g.player.pitch = 0f
        val b = g.physics.add(ball, Vec3(0.05f, ball.radius, 0.6f))
        g.moveY = 1f
        run(g, 1.2f)
        g.moveY = 0f
        assertTrue("piłka powinna potoczyć się do przodu: ${b.position}", b.position.z < 0.3f)
    }

    @Test
    fun doodlesLiveInRoomForTwoMinutes() {
        val g = game(7)
        val rnd = Random(3)
        // respimy po 3 z każdego rodzaju w losowych miejscach podłogi
        for (kind in CharacterRegistry.kinds) repeat(3) {
            val p = g.space.floor.randomPoint(rnd, 0.05f)!!
            assertNotNull(g.world.spawn(kind, p, g.space.floor))
        }
        var maxSimY = 0f
        var onTops = 0
        val topIds = g.space.tops.map { it.first.id }.toSet()
        repeat(60 * 120) {
            g.step(1f / 60f)
            for (c in g.world.characters) {
                val real = g.space.toReal(c.position)
                assertTrue("${c.kind.id} poza pokojem: $real", abs(real.x) <= RoomLayout.HALF_X + 0.05f && abs(real.z) <= RoomLayout.HALF_Z + 0.05f)
                assertTrue(real.y > -0.05f && real.y < RoomLayout.HEIGHT)
                maxSimY = maxOf(maxSimY, c.position.y)
                if (c.surface?.id in topIds && c.state != com.ludzik.game.characters.State.JUMP) onTops++
                // na podłodze nie wchodzą w sofę/regał
                if (c.surface == g.space.floor && c.state == com.ludzik.game.characters.State.WALK) {
                    assertTrue("${c.kind.id} wszedł w mebel: $real", g.space.floor.contains(c.position, -0.03f) || !g.space.floor.contains(c.position, -1f))
                }
            }
        }
        assertTrue("ludziki powinny wskakiwać na meble", onTops > 0)
    }
}
