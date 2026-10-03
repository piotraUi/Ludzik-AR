package com.ludzik.game.characters.doodle

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * „Ołówek” do rysowania niedbałych kresek w jednostkowym kwadracie (0..1).
 *
 * Każdą figurę rysujemy dwukrotnie: najpierw szeroką białą „obwódką papieru”
 * (wygląda jak wycięty z zeszytu ludzik i jest widoczny na ciemnym tle),
 * potem właściwą kreską. Oba przebiegi używają tego samego ziarna losowości,
 * więc kształty się pokrywają, a różne klatki mają różne ziarno — daje to efekt
 * „gotującej się” linii z animacji poklatkowej.
 */
class DoodlePen(
    private val canvas: Canvas,
    private val size: Float,
    seed: Long,
    private val halo: Boolean,
) {
    private val rnd = Random(seed)

    var ink: Int = INK
    var jitter = 0.010f
    var width = 0.022f

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private fun j() = (rnd.nextFloat() - 0.5f) * 2f * jitter

    private fun strokeWith(path: Path, w: Float, color: Int) {
        if (halo) {
            strokePaint.color = HALO
            strokePaint.strokeWidth = (w + HALO_EXTRA) * size
        } else {
            strokePaint.color = color
            strokePaint.strokeWidth = w * size
        }
        canvas.drawPath(path, strokePaint)
    }

    private fun fillWith(path: Path, color: Int) {
        fillPaint.color = if (halo) HALO else color
        canvas.drawPath(path, fillPaint)
    }

    /** Lekko falująca linia z drugą, słabszą kreską jak przy szkicowaniu. */
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, w: Float = width, color: Int = ink) {
        val p = Path()
        val segs = 3
        p.moveTo((x1 + j()) * size, (y1 + j()) * size)
        for (i in 1..segs) {
            val t = i / segs.toFloat()
            p.lineTo((x1 + (x2 - x1) * t + j()) * size, (y1 + (y2 - y1) * t + j()) * size)
        }
        strokeWith(p, w, color)
        // Druga kreska — losujemy zawsze, żeby obie fazy zużyły tyle samo liczb losowych.
        val q = Path()
        val ox = j() * 0.6f
        val oy = j() * 0.6f
        q.moveTo((x1 + ox) * size, (y1 + oy) * size)
        q.lineTo((x2 + ox + j() * 0.5f) * size, (y2 + oy + j() * 0.5f) * size)
        if (!halo) strokeWith(q, w * 0.45f, (color and 0x00FFFFFF) or 0x66000000)
    }

    /** Łamana przez punkty [xy] = x0,y0,x1,y1,... */
    fun polyline(xy: FloatArray, w: Float = width, color: Int = ink, closed: Boolean = false, fill: Int? = null) {
        val p = Path()
        p.moveTo((xy[0] + j()) * size, (xy[1] + j()) * size)
        var i = 2
        while (i < xy.size) {
            p.lineTo((xy[i] + j()) * size, (xy[i + 1] + j()) * size)
            i += 2
        }
        if (closed) p.close()
        if (fill != null) fillWith(p, fill)
        strokeWith(p, w, color)
    }

    /** Krzywa przez punkty wygładzona kwadratowo. */
    fun curve(xy: FloatArray, w: Float = width, color: Int = ink) {
        val p = Path()
        val pts = FloatArray(xy.size) { xy[it] + j() }
        p.moveTo(pts[0] * size, pts[1] * size)
        var i = 2
        while (i + 3 < pts.size) {
            val mx = (pts[i] + pts[i + 2]) / 2f
            val my = (pts[i + 1] + pts[i + 3]) / 2f
            p.quadTo(pts[i] * size, pts[i + 1] * size, mx * size, my * size)
            i += 2
        }
        p.lineTo(pts[pts.size - 2] * size, pts[pts.size - 1] * size)
        strokeWith(p, w, color)
    }

    /** Krzywe kółko/elipsa — z lekkim „niedomknięciem” jak rysowane ręką. */
    fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float, w: Float = width, color: Int = ink, fill: Int? = null) {
        val p = Path()
        val n = 14
        val start = rnd.nextFloat() * 6.28f
        val wob = FloatArray(n + 2) { 1f + j() * 4f }
        for (i in 0..n + 1) {
            val a = start + i / n.toFloat() * 6.2831855f
            val x = (cx + cos(a) * rx * wob[i]) * size
            val y = (cy + sin(a) * ry * wob[i]) * size
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        if (fill != null) {
            val f = Path(p)
            f.close()
            fillWith(f, fill)
        }
        strokeWith(p, w, color)
    }

    fun circle(cx: Float, cy: Float, r: Float, w: Float = width, color: Int = ink, fill: Int? = null) =
        ellipse(cx, cy, r, r, w, color, fill)

    /** Nieregularna plama (kleks) z [lumps] wypustkami. */
    fun blob(cx: Float, cy: Float, rx: Float, ry: Float, lumps: Int, phase: Float, fill: Int, w: Float = width, color: Int = ink) {
        val p = Path()
        val n = 28
        for (i in 0..n) {
            val a = i / n.toFloat() * 6.2831855f
            val bump = 1f + 0.16f * sin(a * lumps + phase) + 0.07f * sin(a * (lumps + 3) - phase * 1.7f) + j() * 2f
            val x = (cx + cos(a) * rx * bump) * size
            val y = (cy + sin(a) * ry * bump) * size
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        fillWith(p, fill)
        strokeWith(p, w, color)
    }

    fun dot(x: Float, y: Float, r: Float, color: Int = ink) {
        val p = Path()
        p.addCircle((x + j() * 0.3f) * size, (y + j() * 0.3f) * size, r * size, Path.Direction.CW)
        fillWith(p, color)
        if (halo) strokeWith(p, 0.01f, color)
    }

    /** Kreskowanie (cieniowanie ołówkiem) w prostokącie. */
    fun hatch(x: Float, y: Float, w: Float, h: Float, spacing: Float = 0.035f, color: Int = HATCH) {
        if (halo) return
        var t = -h
        while (t < w) {
            val x1 = x + t
            val y1 = y + h
            val x2 = x + t + h
            val y2 = y
            // przycięcie do prostokąta
            val sx1 = x1.coerceIn(x, x + w)
            val sy1 = y1 - (sx1 - x1)
            val sx2 = x2.coerceIn(x, x + w)
            val sy2 = y2 + (x2 - sx2)
            // Bez j(): kreskowanie nie może zużywać liczb losowych tylko w jednym przebiegu.
            val p = Path()
            p.moveTo(sx1 * size, sy1 * size)
            p.lineTo(sx2 * size, sy2 * size)
            strokeWith(p, 0.009f, color)
            t += spacing
        }
    }

    /** Spiralne bazgroły długopisem (np. odwłok pająka). */
    fun scribble(cx: Float, cy: Float, rx: Float, ry: Float, loops: Int, color: Int, w: Float = 0.012f) {
        val p = Path()
        val n = loops * 10
        for (i in 0..n) {
            val a = i / 10f * 6.2831855f
            val r = 0.35f + 0.65f * ((i * 7919 % 13) / 13f)
            val x = (cx + cos(a) * rx * r + j()) * size
            val y = (cy + sin(a) * ry * r + j()) * size
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        if (halo) {
            val o = Path()
            o.addOval((cx - rx) * size, (cy - ry) * size, (cx + rx) * size, (cy + ry) * size, Path.Direction.CW)
            fillWith(o, HALO)
            strokeWith(o, w, color)
        } else {
            strokeWith(p, w, color)
        }
    }

    fun text(s: String, x: Float, y: Float, textSize: Float, color: Int = ink) {
        if (halo) return
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            this.textSize = textSize * size
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create("casual", android.graphics.Typeface.BOLD)
        }
        canvas.drawText(s, x * size, y * size, tp)
    }

    companion object {
        const val INK = 0xFF2B2B33.toInt()
        const val HATCH = 0x772B2B33
        const val HALO = 0xF2FFFDF6.toInt()
        const val HALO_EXTRA = 0.024f
        const val BALLPOINT = 0xFF1E3A8A.toInt()
        const val PAPER = 0xFFFFFDF6.toInt()
    }
}
