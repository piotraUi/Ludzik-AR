package com.ludzik.ar.characters.doodle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

/** Komiksowy dymek z tekstem, rysowany „ręcznie” na bitmapie. */
object BubbleArt {

    /** Proporcja szerokość/wysokość jest potrzebna do rozmiaru billboardu. */
    class Result(val bitmap: Bitmap, val aspect: Float)

    fun render(text: String, seed: Long): Result {
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DoodlePen.INK
            textSize = 46f
            typeface = Typeface.create("casual", Typeface.BOLD)
        }
        val maxTextWidth = 420
        val measured = textPaint.measureText(text).toInt()
        val textWidth = minOf(maxTextWidth, measured + 4)
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, textWidth)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(0f, 0.95f)
            .build()

        val padX = 46
        val padY = 34
        val tail = 44
        val w = textWidth + padX * 2
        val bodyH = layout.height + padY * 2
        val h = bodyH + tail
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = Random(seed)

        val cx = w / 2f
        val cy = bodyH / 2f
        val rx = w / 2f - 6f
        val ry = bodyH / 2f - 6f
        val path = Path()
        val n = 40
        for (i in 0..n) {
            val a = i / n.toFloat() * 6.2831855f
            val wob = 1f + (rnd.nextFloat() - 0.5f) * 0.025f
            // „superelipsa” — bardziej prostokątny dymek dla dłuższych tekstów
            val ca = cos(a)
            val sa = sin(a)
            val x = cx + rx * wob * Math.signum(ca) * Math.pow(Math.abs(ca).toDouble(), 0.6).toFloat()
            val y = cy + ry * wob * Math.signum(sa) * Math.pow(Math.abs(sa).toDouble(), 0.6).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        val tailPath = Path().apply {
            moveTo(cx - 34f, bodyH - 14f)
            lineTo(cx - 52f + rnd.nextFloat() * 8f, h - 3f)
            lineTo(cx + 6f, bodyH - 12f)
            close()
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFDF6.toInt() }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DoodlePen.INK
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        c.drawPath(path, fill)
        c.drawPath(tailPath, fill)
        c.drawPath(path, stroke)
        // ogonek: tylko dwie boczne kreski, żeby nie przecinał dymka
        c.drawLine(cx - 34f, bodyH - 14f, cx - 50f, h - 3f, stroke)
        c.drawLine(cx - 50f, h - 3f, cx + 6f, bodyH - 12f, stroke)
        // druga, cieńsza kreska — szkicowy efekt
        stroke.strokeWidth = 2f
        stroke.alpha = 120
        c.save()
        c.translate(3f, -2f)
        c.drawPath(path, stroke)
        c.restore()

        c.save()
        c.translate(cx - textWidth / 2f, padY.toFloat())
        layout.draw(c)
        c.restore()
        return Result(bmp, w / max(1f, h.toFloat()))
    }
}
