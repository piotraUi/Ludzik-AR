package com.ludzik.ar.ar.gl

import android.opengl.GLES20
import com.ludzik.ar.characters.WalkSurface
import java.nio.FloatBuffer

/**
 * Wykryte płaszczyzny jako „kartka w kratkę”: niebieskie linie co 5 cm,
 * znikające ku krawędziom.
 */
class PlaneRenderer {
    private var program = 0
    private var aPos = 0
    private var aGrid = 0
    private var aAlpha = 0
    private var uViewProj = 0
    private var uColor = 0
    private var uCell = 0
    private var buffer: FloatBuffer = GlUtil.floatBuffer(6 * 256)

    fun create() {
        program = GlUtil.program(VS, FS)
        aPos = GLES20.glGetAttribLocation(program, "a_Position")
        aGrid = GLES20.glGetAttribLocation(program, "a_Grid")
        aAlpha = GLES20.glGetAttribLocation(program, "a_Alpha")
        uViewProj = GLES20.glGetUniformLocation(program, "u_ViewProj")
        uColor = GLES20.glGetUniformLocation(program, "u_LineColor")
        uCell = GLES20.glGetUniformLocation(program, "u_Cell")
    }

    fun draw(surfaces: List<WalkSurface>, viewProj: FloatArray, visibility: Float) {
        if (visibility <= 0.01f) return
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uViewProj, 1, false, viewProj, 0)
        GLES20.glUniform1f(uCell, 0.05f)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aGrid)
        GLES20.glEnableVertexAttribArray(aAlpha)
        for (s in surfaces) {
            if (!s.alive || s.boundary.size < 3) continue
            val n = s.boundary.size
            val floats = (n + 2) * 6
            if (buffer.capacity() < floats) buffer = GlUtil.floatBuffer(floats * 2)
            buffer.clear()
            val a = 0.9f * visibility
            put(s, s.center, a)
            for (i in 0..n) put(s, s.boundary[i % n], 0f)
            buffer.position(0)
            val alpha = if (s.isVertical) 0.5f else 1f
            GLES20.glUniform4f(uColor, 0.32f * alpha, 0.52f * alpha, 0.86f * alpha, alpha)
            buffer.position(0)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 24, buffer)
            buffer.position(3)
            GLES20.glVertexAttribPointer(aGrid, 2, GLES20.GL_FLOAT, false, 24, buffer)
            buffer.position(5)
            GLES20.glVertexAttribPointer(aAlpha, 1, GLES20.GL_FLOAT, false, 24, buffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, n + 2)
        }
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aGrid)
        GLES20.glDisableVertexAttribArray(aAlpha)
        GLES20.glDepthMask(true)
    }

    private fun put(s: WalkSurface, p: com.ludzik.ar.characters.Vec3, alpha: Float) {
        buffer.put(p.x).put(p.y).put(p.z)
        if (s.isVertical) {
            buffer.put((p - s.center).dot(s.wallAxis)).put(p.y)
        } else {
            buffer.put(p.x).put(p.z)
        }
        buffer.put(alpha)
    }

    companion object {
        private const val VS = """
            uniform mat4 u_ViewProj;
            attribute vec3 a_Position;
            attribute vec2 a_Grid;
            attribute float a_Alpha;
            varying vec2 v_Grid;
            varying float v_Alpha;
            void main() {
                v_Grid = a_Grid;
                v_Alpha = a_Alpha;
                gl_Position = u_ViewProj * vec4(a_Position, 1.0);
            }
        """
        private const val FS = """
            precision mediump float;
            uniform vec4 u_LineColor;
            uniform float u_Cell;
            varying vec2 v_Grid;
            varying float v_Alpha;
            void main() {
                vec2 g = v_Grid / u_Cell;
                vec2 d = abs(fract(g) - 0.5);
                float line = smoothstep(0.42, 0.48, max(d.x, d.y));
                float a = clamp(v_Alpha * 1.6, 0.0, 1.0);
                // lekka biel papieru + niebieskie kratki (premultiplied alpha)
                vec4 paper = vec4(0.10, 0.10, 0.09, 0.10) * a;
                gl_FragColor = paper + u_LineColor * line * a * 0.6;
            }
        """
    }
}
