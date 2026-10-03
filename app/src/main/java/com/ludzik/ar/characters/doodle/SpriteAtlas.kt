package com.ludzik.ar.characters.doodle

import android.graphics.Bitmap
import android.graphics.Canvas
import com.ludzik.ar.characters.Anim
import com.ludzik.ar.characters.CharacterKind

/**
 * Generuje proceduralnie atlas klatek dla rodzaju postaci:
 * wiersze = [Anim], kolumny = klatki. Nie potrzeba żadnych plików graficznych.
 */
object SpriteAtlas {
    const val CELL = 160

    fun build(kind: CharacterKind, cell: Int = CELL): Bitmap {
        val cols = kind.framesPerAnim
        val rows = Anim.entries.size
        val bmp = Bitmap.createBitmap(cols * cell, rows * cell, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        for (anim in Anim.entries) {
            for (f in 0 until cols) {
                canvas.save()
                canvas.translate((f * cell).toFloat(), (anim.ordinal * cell).toFloat())
                canvas.clipRect(0, 0, cell, cell)
                drawFrame(canvas, kind, anim, f, cell.toFloat())
                canvas.restore()
            }
        }
        return bmp
    }

    fun drawFrame(canvas: Canvas, kind: CharacterKind, anim: Anim, frame: Int, size: Float) {
        val seed = kind.id.hashCode() * 31L + anim.ordinal * 101L + frame * 7L
        kind.draw(DoodlePen(canvas, size, seed, halo = true), anim, frame)
        kind.draw(DoodlePen(canvas, size, seed, halo = false), anim, frame)
    }

    /** Ikona do menu wyboru postaci. */
    fun icon(kind: CharacterKind, sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        drawFrame(Canvas(bmp), kind, Anim.IDLE, 0, sizePx.toFloat())
        return bmp
    }

    /** UV (u0, v0, u1, v1) komórki w atlasie; v0 = góra komórki. */
    fun uv(kind: CharacterKind, anim: Anim, frame: Int, out: FloatArray) {
        val cols = kind.framesPerAnim.toFloat()
        val rows = Anim.entries.size.toFloat()
        // Pół teksela marginesu, żeby filtrowanie nie łapało sąsiednich klatek.
        val eps = 0.5f / (CELL * cols)
        val epsV = 0.5f / (CELL * rows)
        out[0] = frame / cols + eps
        out[1] = anim.ordinal / rows + epsV
        out[2] = (frame + 1) / cols - eps
        out[3] = (anim.ordinal + 1) / rows - epsV
    }
}
