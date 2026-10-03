package com.ludzik.ar.ar.gl

import android.opengl.GLES20
import com.ludzik.ar.characters.Vec3

/**
 * Rysuje teksturowane prostokąty w 3D. Ten sam shader obsługuje billboardy postaci,
 * dymki i płaskie naklejki na podłodze — różnią się tylko osiami [right]/[up].
 */
class SpriteRenderer {
    private var program = 0
    private var aCorner = 0
    private var uViewProj = 0
    private var uOrigin = 0
    private var uRight = 0
    private var uUp = 0
    private var uSize = 0
    private var uUv = 0
    private var uFlip = 0
    private var uColor = 0
    private var uTex = 0

    // x od -0.5 do 0.5, y od 0 (dół/stopy) do 1 (góra)
    private val corners = GlUtil.floatBuffer(floatArrayOf(-0.5f, 0f, 0.5f, 0f, -0.5f, 1f, 0.5f, 1f))

    fun create() {
        program = GlUtil.program(VS, FS)
        aCorner = GLES20.glGetAttribLocation(program, "a_Corner")
        uViewProj = GLES20.glGetUniformLocation(program, "u_ViewProj")
        uOrigin = GLES20.glGetUniformLocation(program, "u_Origin")
        uRight = GLES20.glGetUniformLocation(program, "u_Right")
        uUp = GLES20.glGetUniformLocation(program, "u_Up")
        uSize = GLES20.glGetUniformLocation(program, "u_Size")
        uUv = GLES20.glGetUniformLocation(program, "u_Uv")
        uFlip = GLES20.glGetUniformLocation(program, "u_Flip")
        uColor = GLES20.glGetUniformLocation(program, "u_Color")
        uTex = GLES20.glGetUniformLocation(program, "u_Texture")
    }

    fun begin(viewProj: FloatArray) {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uViewProj, 1, false, viewProj, 0)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDepthMask(false)
        corners.position(0)
        GLES20.glVertexAttribPointer(aCorner, 2, GLES20.GL_FLOAT, false, 0, corners)
        GLES20.glEnableVertexAttribArray(aCorner)
    }

    /**
     * @param origin punkt środka dolnej krawędzi prostokąta
     * @param uv (u0, v0, u1, v1) — v0 to górna krawędź komórki w bitmapie
     */
    fun draw(
        texture: Int, origin: Vec3, right: Vec3, up: Vec3,
        width: Float, height: Float, uv: FloatArray, flip: Boolean, alpha: Float,
    ) {
        if (alpha <= 0.003f) return
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform3f(uOrigin, origin.x, origin.y, origin.z)
        GLES20.glUniform3f(uRight, right.x, right.y, right.z)
        GLES20.glUniform3f(uUp, up.x, up.y, up.z)
        GLES20.glUniform2f(uSize, width, height)
        GLES20.glUniform4f(uUv, uv[0], uv[1], uv[2], uv[3])
        GLES20.glUniform1f(uFlip, if (flip) -1f else 1f)
        GLES20.glUniform4f(uColor, alpha, alpha, alpha, alpha)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    fun end() {
        GLES20.glDisableVertexAttribArray(aCorner)
        GLES20.glDepthMask(true)
    }

    companion object {
        private const val VS = """
            uniform mat4 u_ViewProj;
            uniform vec3 u_Origin;
            uniform vec3 u_Right;
            uniform vec3 u_Up;
            uniform vec2 u_Size;
            uniform vec4 u_Uv;
            uniform float u_Flip;
            attribute vec2 a_Corner;
            varying vec2 v_Uv;
            void main() {
                vec3 p = u_Origin + u_Right * (a_Corner.x * u_Size.x) + u_Up * (a_Corner.y * u_Size.y);
                gl_Position = u_ViewProj * vec4(p, 1.0);
                float ux = a_Corner.x * u_Flip + 0.5;
                v_Uv = vec2(mix(u_Uv.x, u_Uv.z, ux), mix(u_Uv.w, u_Uv.y, a_Corner.y));
            }
        """
        private const val FS = """
            precision mediump float;
            uniform sampler2D u_Texture;
            uniform vec4 u_Color;
            varying vec2 v_Uv;
            void main() {
                gl_FragColor = texture2D(u_Texture, v_Uv) * u_Color;
            }
        """
    }
}
