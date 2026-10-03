package com.ludzik.ar.characters.doodle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.ludzik.ar.characters.DecalType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Atlas tekstur płaskich naklejek (cień, plamy atramentu, okruchy gumki). */
object DecalArt {
    const val CELL = 128
    const val COLS = 4
    const val ROWS = 2

    private const val INK = 0xE016205A.toInt()

    fun build(): Bitmap {
        val bmp = Bitmap.createBitmap(CELL * COLS, CELL * ROWS, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        for (type in DecalType.entries) {
            val i = type.atlasIndex
            c.save()
            c.translate(((i % COLS) * CELL).toFloat(), ((i / COLS) * CELL).toFloat())
            c.clipRect(0, 0, CELL, CELL)
            when (type) {
                DecalType.SHADOW -> shadow(c)
                DecalType.INK_A -> stain(c, 11L, 0.32f, 6)
                DecalType.INK_B -> stain(c, 23L, 0.26f, 4)
                DecalType.SPLASH -> splash(c)
                DecalType.CRUMBS -> crumbs(c)
            }
            c.restore()
        }
        return bmp
    }

    fun uv(type: DecalType, out: FloatArray) {
        val i = type.atlasIndex
        val eps = 0.5f / (CELL * COLS)
        out[0] = (i % COLS) / COLS.toFloat() + eps
        out[1] = (i / COLS) / ROWS.toFloat() + eps
        out[2] = (i % COLS + 1) / COLS.toFloat() - eps
        out[3] = (i / COLS + 1) / ROWS.toFloat() - eps
    }

    private fun shadow(c: Canvas) {
        val h = CELL / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(h, h, h * 0.95f, intArrayOf(0x66202028, 0x33202028, 0x00202028), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(h, h, h, p)
    }

    private fun blobPath(cx: Float, cy: Float, r: Float, lumps: Int, rnd: Random): android.graphics.Path {
        val path = android.graphics.Path()
        val n = 32
        val phase = rnd.nextFloat() * 6f
        for (k in 0..n) {
            val a = k / n.toFloat() * 6.2831855f
            val rr = r * (1f + 0.18f * sin(a * lumps + phase) + 0.08f * sin(a * (lumps + 5)) + (rnd.nextFloat() - 0.5f) * 0.06f)
            val x = cx + cos(a) * rr
            val y = cy + sin(a) * rr
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return path
    }

    private fun stain(c: Canvas, seed: Long, r: Float, lumps: Int) {
        val rnd = Random(seed)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK }
        val h = CELL / 2f
        c.drawPath(blobPath(h, h, CELL * r, lumps, rnd), p)
    }

    private fun splash(c: Canvas) {
        val rnd = Random(5)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK }
        val h = CELL / 2f
        c.drawPath(blobPath(h, h, CELL * 0.22f, 7, rnd), p)
        repeat(12) {
            val a = rnd.nextFloat() * 6.28f
            val d = CELL * (0.28f + rnd.nextFloat() * 0.18f)
            val r = CELL * (0.015f + rnd.nextFloat() * 0.035f)
            c.drawCircle(h + cos(a) * d, h + sin(a) * d, r, p)
            // smuga od środka do kropli
            p.strokeWidth = r * 0.8f
            p.strokeCap = Paint.Cap.ROUND
            c.drawLine(h + cos(a) * CELL * 0.2f, h + sin(a) * CELL * 0.2f, h + cos(a) * d * 0.9f, h + sin(a) * d * 0.9f, p)
        }
    }

    private fun crumbs(c: Canvas) {
        val rnd = Random(9)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val h = CELL / 2f
        repeat(14) {
            p.color = if (rnd.nextBoolean()) 0xDDF48FB1.toInt() else 0xDD8E8E99.toInt()
            val a = rnd.nextFloat() * 6.28f
            val d = CELL * rnd.nextFloat() * 0.4f
            val w = CELL * (0.03f + rnd.nextFloat() * 0.05f)
            c.save()
            c.rotate(rnd.nextFloat() * 180f, h + cos(a) * d, h + sin(a) * d)
            c.drawRoundRect(h + cos(a) * d - w, h + sin(a) * d - w * 0.4f, h + cos(a) * d + w, h + sin(a) * d + w * 0.4f, w * 0.4f, w * 0.4f, p)
            c.restore()
        }
    }
}
