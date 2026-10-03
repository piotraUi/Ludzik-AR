package com.ludzik.ar.characters

import com.ludzik.ar.audio.Haptic
import com.ludzik.ar.audio.Sfx
import kotlin.math.abs
import kotlin.random.Random

/** Zdarzenia świata przekazywane do dźwięku/wibracji (żeby postacie nie znały Androida). */
interface WorldListener {
    fun onSound(sfx: Sfx, volume: Float)
    fun onHaptic(haptic: Haptic)
}

/**
 * Cały stan „zeszytowego” świata: postacie, plamy, linie, dymki i powierzchnie z AR.
 * Aktualizowany wyłącznie z wątku renderującego.
 */
class World(val random: Random = Random.Default) {
    companion object {
        const val MAX_CHARACTERS = 15
        const val MAX_DECALS = 160
        const val MAX_STROKES = 48
        const val MAX_JUMP_HEIGHT = 1.2f
    }

    var listener: WorldListener? = null

    val characters = ArrayList<Character>()
    val decals = ArrayList<Decal>()
    val strokes = ArrayList<Stroke>()
    val bubbles = ArrayList<Bubble>()

    var surfaces: List<WalkSurface> = emptyList()
    var time = 0f
        private set

    private var nextId = 1
    private var nextBubbleId = 1L

    val horizontalSurfaces: List<WalkSurface>
        get() = surfaces.filter { it.alive && !it.isVertical }

    val walls: List<WalkSurface>
        get() = surfaces.filter { it.alive && it.isVertical }

    val isFull get() = characters.size >= MAX_CHARACTERS

    fun spawn(kind: CharacterKind, position: Vec3, surface: WalkSurface): Character? {
        if (isFull) return null
        val c = kind.create(nextId++, position, surface)
        characters.add(c)
        sound(Sfx.POP)
        haptic(Haptic.LIGHT)
        return c
    }

    fun clear() {
        characters.clear()
        decals.clear()
        strokes.clear()
        bubbles.clear()
    }

    fun update(dt: Float) {
        time += dt
        // Indeksowo — postacie mogą w trakcie dodawać efekty, ale nie postacie.
        for (i in characters.indices) characters[i].update(dt, this)

        for (d in decals) d.age += dt
        decals.removeAll { it.expired }
        for (s in strokes) s.age += dt
        strokes.removeAll { it.expired || (it.from?.alive == false) || (it.to?.alive == false) }
        for (b in bubbles) b.age += dt
        bubbles.removeAll { it.expired }
    }

    fun addDecal(type: DecalType, position: Vec3, size: Float, lifetime: Float) {
        if (decals.size >= MAX_DECALS) decals.removeAt(0)
        decals.add(Decal(type, position, size, random.nextFloat() * 6.2831855f, lifetime))
    }

    fun addStroke(stroke: Stroke): Stroke {
        if (strokes.size >= MAX_STROKES) {
            // Najpierw usuwamy najstarsze rysunki na podłodze, mosty są cenniejsze.
            val victim = strokes.firstOrNull { it.style == StrokeStyle.FLOOR_LINE } ?: strokes.first()
            strokes.remove(victim)
        }
        strokes.add(stroke)
        return stroke
    }

    fun say(owner: Character, text: String, seconds: Float = 2.6f) {
        bubbles.removeAll { it.owner === owner }
        bubbles.add(Bubble(nextBubbleId++, owner, text, seconds))
    }

    fun sound(sfx: Sfx, volume: Float = 1f) = listener?.onSound(sfx, volume)
    fun haptic(h: Haptic) = listener?.onHaptic(h)

    /** Najwyższa pozioma powierzchnia pod punktem (nie wyżej niż [maxAbove] nad nim). */
    fun surfaceUnder(p: Vec3, maxAbove: Float = 0.25f): WalkSurface? {
        var best: WalkSurface? = null
        for (s in surfaces) {
            if (!s.alive || s.isVertical) continue
            if (s.height > p.y + maxAbove) continue
            if (!s.contains(p.x, p.z, -0.03f)) continue
            if (best == null || s.height > best.height) best = s
        }
        return best
    }

    /** Powierzchnia „najbliższa” punktowi — gdy postać straciła grunt pod nogami. */
    fun nearestHorizontal(p: Vec3): WalkSurface? =
        horizontalSurfaces.minByOrNull { it.center.horizontalDistanceTo(p) + abs(it.height - p.y) * 2f }

    /** Największa z najniżej położonych powierzchni — przyjmujemy, że to podłoga. */
    fun floor(): WalkSurface? {
        val hs = horizontalSurfaces
        val lowest = hs.minOfOrNull { it.height } ?: return null
        return hs.filter { it.height < lowest + 0.15f }.maxByOrNull { it.area }
    }

    fun bridgeBetween(a: WalkSurface, b: WalkSurface): Stroke? = strokes.firstOrNull {
        it.walkable && it.alpha > 0.3f &&
            ((it.from === a && it.to === b) || (it.from === b && it.to === a))
    }

    fun nearest(from: Character, maxDistance: Float, filter: (Character) -> Boolean): Character? {
        var best: Character? = null
        var bestD = maxDistance
        for (c in characters) {
            if (c === from || c.isErased || !filter(c)) continue
            val d = c.position.distanceTo(from.position)
            if (d < bestD) {
                bestD = d
                best = c
            }
        }
        return best
    }
}
