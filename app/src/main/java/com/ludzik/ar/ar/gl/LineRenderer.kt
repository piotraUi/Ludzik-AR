package com.ludzik.ar.ar.gl

import android.opengl.GLES20
import com.ludzik.ar.characters.Stroke
import com.ludzik.ar.characters.StrokeStyle
import com.ludzik.ar.characters.Vec3
import java.nio.FloatBuffer
import kotlin.math.sin

/**
 * Linie ołówka i nitki jako wstążki z trójkątów (glLineWidth bywa ograniczone do 1 px).
 * Linie na podłodze leżą płasko, mosty i nitki są zwrócone do kamery.
 */
class LineRenderer {
    private var program = 0
    private var aPos = 0
    private var aColor = 0
    private var uViewProj = 0
    private var buffer: FloatBuffer = GlUtil.floatBuffer(7 * 6 * 512)

    fun create() {
        program = GlUtil.program(VS, FS)
        aPos = GLES20.glGetAttribLocation(program, "a_Position")
        aColor = GLES20.glGetAttribLocation(program, "a_Color")
        uViewProj = GLES20.glGetUniformLocation(program, "u_ViewProj")
    }

    fun draw(strokes: List<Stroke>, viewProj: FloatArray, cameraPos: Vec3) {
        var segments = 0
        for (s in strokes) segments += maxOf(0, s.points.size - 1)
        if (segments == 0) return
        val needed = segments * 6 * 7
        if (buffer.capacity() < needed) buffer = GlUtil.floatBuffer(needed * 2)
        buffer.clear()
        var vertices = 0
        for (s in strokes) {
            val a = s.alpha
            if (a <= 0.01f || s.points.size < 2) continue
            val c = s.color
            val ca = ((c ushr 24) and 0xFF) / 255f * a
            val r = ((c shr 16) and 0xFF) / 255f * ca
            val g = ((c shr 8) and 0xFF) / 255f * ca
            val b = (c and 0xFF) / 255f * ca
            val half = s.width / 2f
            for (i in 0 until s.points.size - 1) {
                val p0 = s.points[i]
                val p1 = s.points[i + 1]
                val dir = (p1 - p0).normalized()
                val side = if (s.style == StrokeStyle.FLOOR_LINE) {
                    dir.cross(Vec3.UP).normalized()
                } else {
                    dir.cross((cameraPos - p0).normalized()).normalized()
                }
                // „grafitowa” nierówność szerokości wzdłuż linii
                val w0 = half * (0.75f + 0.25f * sin(i * 2.3f))
                val w1 = half * (0.75f + 0.25f * sin((i + 1) * 2.3f))
                val a0 = p0 + side * w0
                val b0 = p0 - side * w0
                val a1 = p1 + side * w1
                val b1 = p1 - side * w1
                put(a0, r, g, b, ca); put(b0, r, g, b, ca); put(a1, r, g, b, ca)
                put(a1, r, g, b, ca); put(b0, r, g, b, ca); put(b1, r, g, b, ca)
                vertices += 6
            }
        }
        if (vertices == 0) return
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uViewProj, 1, false, viewProj, 0)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        buffer.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 28, buffer)
        buffer.position(3)
        GLES20.glVertexAttribPointer(aColor, 4, GLES20.GL_FLOAT, false, 28, buffer)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aColor)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertices)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aColor)
        GLES20.glDepthMask(true)
    }

    private fun put(p: Vec3, r: Float, g: Float, b: Float, a: Float) {
        buffer.put(p.x).put(p.y).put(p.z).put(r).put(g).put(b).put(a)
    }

    companion object {
        private const val VS = """
            uniform mat4 u_ViewProj;
            attribute vec3 a_Position;
            attribute vec4 a_Color;
            varying vec4 v_Color;
            void main() {
                v_Color = a_Color;
                gl_Position = u_ViewProj * vec4(a_Position, 1.0);
            }
        """
        private const val FS = """
            precision mediump float;
            varying vec4 v_Color;
            void main() { gl_FragColor = v_Color; }
        """
    }
}
