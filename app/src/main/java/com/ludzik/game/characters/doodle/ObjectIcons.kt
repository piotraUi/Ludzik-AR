package com.ludzik.game.characters.doodle

import android.graphics.Bitmap
import android.graphics.Canvas

/** Rysunkowe ikony przedmiotów do menu (w tym samym stylu co ludziki). */
object ObjectIcons {

    fun icon(id: String, sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        for (halo in booleanArrayOf(true, false)) {
            val pen = DoodlePen(canvas, sizePx.toFloat(), id.hashCode().toLong(), halo)
            draw(pen, id)
        }
        return bmp
    }

    private fun draw(pen: DoodlePen, id: String) {
        when {
            id.contains("football") -> {
                pen.circle(0.5f, 0.55f, 0.3f, fill = 0xFFFFFFFF.toInt())
                pen.polyline(floatArrayOf(0.5f, 0.45f, 0.58f, 0.51f, 0.55f, 0.6f, 0.45f, 0.6f, 0.42f, 0.51f), closed = true, fill = pen.ink)
                for ((x, y) in listOf(0.5f to 0.3f, 0.75f to 0.48f, 0.68f to 0.76f, 0.32f to 0.76f, 0.25f to 0.48f)) pen.dot(x, y, 0.035f)
            }
            id.contains("duck") -> {
                val yellow = 0xFFFFD54F.toInt()
                pen.ellipse(0.5f, 0.68f, 0.3f, 0.17f, fill = yellow)
                pen.circle(0.38f, 0.38f, 0.15f, fill = yellow)
                pen.polyline(floatArrayOf(0.23f, 0.38f, 0.1f, 0.42f, 0.23f, 0.46f), closed = true, fill = 0xFFFF8A3D.toInt())
                pen.dot(0.36f, 0.34f, 0.025f)
                pen.curve(floatArrayOf(0.48f, 0.66f, 0.6f, 0.6f, 0.72f, 0.66f), 0.015f)
            }
            id.contains("cardboard") -> {
                val brown = 0xFFD7A86E.toInt()
                pen.polyline(floatArrayOf(0.18f, 0.4f, 0.62f, 0.4f, 0.62f, 0.85f, 0.18f, 0.85f), closed = true, fill = brown)
                pen.polyline(floatArrayOf(0.18f, 0.4f, 0.36f, 0.25f, 0.82f, 0.25f, 0.62f, 0.4f), closed = true, fill = 0xFFE6BE8A.toInt())
                pen.polyline(floatArrayOf(0.62f, 0.4f, 0.82f, 0.25f, 0.82f, 0.7f, 0.62f, 0.85f), closed = true, fill = 0xFFC08F55.toInt())
                pen.line(0.4f, 0.4f, 0.4f, 0.55f, 0.014f)
            }
            id.contains("apple") -> {
                pen.blob(0.5f, 0.6f, 0.27f, 0.25f, 2, 1.6f, 0xFFE53935.toInt())
                pen.line(0.5f, 0.38f, 0.53f, 0.24f, 0.02f, 0xFF6D4C41.toInt())
                pen.ellipse(0.62f, 0.27f, 0.08f, 0.04f, fill = 0xFF66BB6A.toInt())
                pen.curve(floatArrayOf(0.37f, 0.5f, 0.35f, 0.6f, 0.38f, 0.68f), 0.014f, 0xFFFFFFFF.toInt())
            }
            else -> { // skrzynka
                val wood = 0xFFB8875A.toInt()
                pen.polyline(floatArrayOf(0.15f, 0.35f, 0.85f, 0.35f, 0.85f, 0.8f, 0.15f, 0.8f), closed = true, fill = wood)
                pen.line(0.15f, 0.5f, 0.85f, 0.5f, 0.012f)
                pen.line(0.15f, 0.65f, 0.85f, 0.65f, 0.012f)
                pen.line(0.15f, 0.35f, 0.85f, 0.8f, 0.016f)
                pen.line(0.15f, 0.8f, 0.85f, 0.35f, 0.016f)
            }
        }
    }
}
