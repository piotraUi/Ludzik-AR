package com.ludzik.game.characters

/** Płaskie „naklejki” leżące na powierzchni: plamy atramentu, okruchy gumki. */
enum class DecalType(val atlasIndex: Int) {
    SHADOW(0), INK_A(1), INK_B(2), SPLASH(3), CRUMBS(4)
}

class Decal(
    val type: DecalType,
    val position: Vec3,
    val size: Float,
    val rotation: Float,
    val lifetime: Float,
) {
    var age = 0f
    val alpha: Float
        get() {
            val fadeIn = (age / 0.15f).coerceIn(0f, 1f)
            val fadeOut = ((lifetime - age) / 2f).coerceIn(0f, 1f)
            return fadeIn * fadeOut
        }
    val expired get() = age >= lifetime
}

enum class StrokeStyle { FLOOR_LINE, BRIDGE, THREAD }

/**
 * Linia ołówka/długopisu: rysunek na podłodze, most albo nitka pająka.
 * Punkty mogą rosnąć w trakcie rysowania.
 */
class Stroke(
    val style: StrokeStyle,
    val width: Float,
    /** Kolor ARGB. */
    val color: Int,
    var lifetime: Float,
) {
    val points = ArrayList<Vec3>()
    var age = 0f

    /** Most: powierzchnie, które łączy (od dołu do góry albo odwrotnie). */
    var from: WalkSurface? = null
    var to: WalkSurface? = null
    var walkable = false

    val alpha: Float
        get() {
            val fadeOut = ((lifetime - age) / 3f).coerceIn(0f, 1f)
            return fadeOut
        }
    val expired get() = age >= lifetime

    fun length(): Float {
        var l = 0f
        for (i in 1 until points.size) l += points[i].distanceTo(points[i - 1])
        return l
    }
}

class Bubble(val id: Long, val owner: Character, val text: String, val lifetime: Float) {
    var age = 0f
    val alpha get() = ((lifetime - age) / 0.3f).coerceIn(0f, 1f) * (age / 0.12f).coerceIn(0f, 1f)
    val expired get() = age >= lifetime || owner.isErased
}
