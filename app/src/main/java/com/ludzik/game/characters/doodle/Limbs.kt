package com.ludzik.game.characters.doodle

import kotlin.math.cos
import kotlin.math.sin

private const val DEG = 0.017453292f

/**
 * Kończyna z dwóch odcinków. Kąty w stopniach od pionu w dół
 * (0 = prosto w dół, dodatnie = do przodu, czyli w prawo).
 */
fun DoodlePen.limb(x0: Float, y0: Float, a1: Float, l1: Float, a2: Float, l2: Float, w: Float = width, color: Int = ink): FloatArray {
    val kx = x0 + sin(a1 * DEG) * l1
    val ky = y0 + cos(a1 * DEG) * l1
    val fx = kx + sin((a1 + a2) * DEG) * l2
    val fy = ky + cos((a1 + a2) * DEG) * l2
    polyline(floatArrayOf(x0, y0, kx, ky, fx, fy), w, color)
    return floatArrayOf(fx, fy)
}

/** Obrót punktów (x,y,x,y,...) wokół (px,py) o [deg] stopni (dodatnie = zgodnie z zegarem na ekranie). */
fun rotated(xy: FloatArray, px: Float, py: Float, deg: Float): FloatArray {
    val c = cos(deg * DEG)
    val s = sin(deg * DEG)
    val out = FloatArray(xy.size)
    var i = 0
    while (i < xy.size) {
        val dx = xy[i] - px
        val dy = xy[i + 1] - py
        out[i] = px + dx * c - dy * s
        out[i + 1] = py + dx * s + dy * c
        i += 2
    }
    return out
}

/** Faza animacji 0..2π dla klatki. */
fun phase(frame: Int, frames: Int = 4) = frame / frames.toFloat() * 6.2831855f
