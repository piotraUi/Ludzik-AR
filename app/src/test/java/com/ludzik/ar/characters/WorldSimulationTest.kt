package com.ludzik.ar.characters

import com.ludzik.ar.audio.Haptic
import com.ludzik.ar.audio.Sfx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Symulacja świata bez ARCore: podłoga, stół i ściana zbudowane ręcznie. */
class WorldSimulationTest {

    private fun rect(id: Int, cx: Float, y: Float, cz: Float, hx: Float, hz: Float) = WalkSurface(id, false).apply {
        update(
            listOf(Vec3(cx - hx, y, cz - hz), Vec3(cx + hx, y, cz - hz), Vec3(cx + hx, y, cz + hz), Vec3(cx - hx, y, cz + hz)),
            Vec3(cx, y, cz), Vec3.UP,
        )
    }

    private fun wall(id: Int) = WalkSurface(id, true).apply {
        // ściana w z = -1.5, normalna w stronę pokoju (+z)
        update(
            listOf(Vec3(-1.5f, 0f, -1.5f), Vec3(1.5f, 0f, -1.5f), Vec3(1.5f, 2f, -1.5f), Vec3(-1.5f, 2f, -1.5f)),
            Vec3(0f, 1f, -1.5f), Vec3(0f, 0f, 1f),
        )
    }

    private class Counter : WorldListener {
        val sounds = HashMap<Sfx, Int>()
        override fun onSound(sfx: Sfx, volume: Float) {
            sounds[sfx] = (sounds[sfx] ?: 0) + 1
        }
        override fun onHaptic(haptic: Haptic) {}
    }

    @Test
    fun polygonContainsRespectsMargin() {
        val s = rect(1, 0f, 0f, 0f, 1f, 1f)
        assertTrue(s.contains(0f, 0f, 0.5f))
        assertTrue(!s.contains(0.95f, 0f, 0.1f))
        assertTrue(!s.contains(1.2f, 0f, 0f))
        // odwrotna kolejność wierzchołków też działa
        val r = WalkSurface(2, false).apply {
            update(listOf(Vec3(-1f, 0f, 1f), Vec3(1f, 0f, 1f), Vec3(1f, 0f, -1f), Vec3(-1f, 0f, -1f)), Vec3.ZERO, Vec3.UP)
        }
        assertTrue(r.contains(0.5f, 0.5f, 0.2f))
        assertTrue(!r.contains(1.5f, 0.5f, 0f))
    }

    @Test
    fun charactersLiveTogetherForTwoMinutes() {
        val floor = rect(1, 0f, 0f, 0f, 1.5f, 1.5f)
        val table = rect(2, 0.9f, 0.7f, 0.2f, 0.4f, 0.3f)
        val world = World(Random(42))
        val counter = Counter()
        world.listener = counter
        world.surfaces = listOf(floor, table, wall(3))

        val rnd = Random(1)
        for (kind in CharacterRegistry.kinds) repeat(3) {
            val p = floor.randomPoint(rnd, 0.2f)!!
            assertTrue(world.spawn(kind, p, floor) != null)
        }
        assertEquals(15, world.characters.size)
        assertNull("limit 15 postaci", world.spawn(Ludzik, Vec3.ZERO, floor))

        var maxY = 0f
        var spiderMaxY = 0f
        var erasedSeen = 0
        var bridgesSeen = 0
        var bridgeWalkers = 0
        val start = System.nanoTime()
        val dt = 1f / 60f
        repeat(60 * 180) { frame ->
            world.update(dt)
            for (c in world.characters) {
                assertTrue("NaN w ${c.kind.id}", c.position.isFinite())
                assertTrue("${c.kind.id} pod podłogą: ${c.position}", c.position.y > -0.05f)
                assertTrue("${c.kind.id} uciekł z pokoju: ${c.position}", c.position.horizontalDistanceTo(Vec3.ZERO) < 3f)
                if (c !is Pajak) maxY = maxOf(maxY, c.position.y)
                if (c is Pajak) spiderMaxY = maxOf(spiderMaxY, c.position.y)
                if (c.isErased) erasedSeen++
                if (c !is Olowek && c.state == State.PATH) bridgeWalkers++
            }
            bridgesSeen = maxOf(bridgesSeen, world.strokes.count { it.style == StrokeStyle.BRIDGE && it.walkable })
            if (frame % 600 == 0) {
                // Co 10 s ktoś dotyka postaci.
                world.characters[frame / 600 % 15].onTouched(world)
            }
        }
        val ms = (System.nanoTime() - start) / 1e6
        println("Symulacja 180 s zajęła %.0f ms (%.3f ms/klatkę)".format(ms, ms / (60 * 180)))
        println("maxY=$maxY spiderMaxY=$spiderMaxY erasedFrames=$erasedSeen bridges=$bridgesSeen bridgeWalkFrames=$bridgeWalkers")
        println("dźwięki: ${counter.sounds}")
        println("decals=${world.decals.size} strokes=${world.strokes.size}")
        for (c in world.characters) println("  ${c.kind.id} ${c.state} on ${c.surface} at ${c.position}")

        assertTrue("ktoś powinien wskoczyć na stół", maxY > 0.6f)
        assertTrue("pająk powinien się wspinać", spiderMaxY > 0.3f)
        assertTrue("gumka powinna kogoś wymazać", erasedSeen > 0)
        assertTrue("ołówek powinien narysować most", bridgesSeen > 0)
        assertTrue("kleks chlapie", (counter.sounds[Sfx.PLUM] ?: 0) > 0)
        assertTrue("za wolno: $ms ms", ms / (60 * 180) < 2.0)
    }
}
