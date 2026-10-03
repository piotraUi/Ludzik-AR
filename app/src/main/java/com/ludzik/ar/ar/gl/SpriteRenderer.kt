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
    private var uView = 0
    private var uDepth = 0
    private var uUseDepth = 0
    private var uViewport = 0
    private var uDepthUvA = 0
    private var uDepthUvB = 0

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
        uView = GLES20.glGetUniformLocation(program, "u_View")
        uDepth = GLES20.glGetUniformLocation(program, "u_Depth")
        uUseDepth = GLES20.glGetUniformLocation(program, "u_UseDepth")
        uViewport = GLES20.glGetUniformLocation(program, "u_Viewport")
        uDepthUvA = GLES20.glGetUniformLocation(program, "u_DepthUvA")
        uDepthUvB = GLES20.glGetUniformLocation(program, "u_DepthUvB")
    }

    /**
     * @param occlusion gdy nie-null: postacie chowają się za realnymi przedmiotami.
     */
    fun begin(viewProj: FloatArray, occlusion: Occlusion? = null) {
        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uViewProj, 1, false, viewProj, 0)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glUniform1i(uDepth, 1)
        if (occlusion != null) {
            GLES20.glUniform1f(uUseDepth, 1f)
            GLES20.glUniformMatrix4fv(uView, 1, false, occlusion.view, 0)
            GLES20.glUniform2f(uViewport, occlusion.viewportW.toFloat(), occlusion.viewportH.toFloat())
            val t = occlusion.depthUv
            GLES20.glUniform4f(uDepthUvA, t[0], t[1], t[2], t[3])
            GLES20.glUniform4f(uDepthUvB, t[4], t[5], t[6], t[7])
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, occlusion.depthTexture)
        } else {
            GLES20.glUniform1f(uUseDepth, 0f)
        }
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

    /** Dane do zasłaniania: mapa głębi i jej współrzędne w rogach ekranu (jak obraz kamery). */
    class Occlusion(
        val depthTexture: Int,
        val view: FloatArray,
        /** UV rogów ekranu: (-1,-1), (1,-1), (-1,1), (1,1) — po 2 liczby. */
        val depthUv: FloatArray,
        val viewportW: Int,
        val viewportH: Int,
    )

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
            uniform mat4 u_View;
            attribute vec2 a_Corner;
            varying vec2 v_Uv;
            varying float v_ViewDepth;
            void main() {
                vec3 p = u_Origin + u_Right * (a_Corner.x * u_Size.x) + u_Up * (a_Corner.y * u_Size.y);
                gl_Position = u_ViewProj * vec4(p, 1.0);
                v_ViewDepth = -(u_View * vec4(p, 1.0)).z;
                float ux = a_Corner.x * u_Flip + 0.5;
                v_Uv = vec2(mix(u_Uv.x, u_Uv.z, ux), mix(u_Uv.w, u_Uv.y, a_Corner.y));
            }
        """
        private const val FS = """
            #ifdef GL_FRAGMENT_PRECISION_HIGH
            precision highp float;
            #else
            precision mediump float;
            #endif
            uniform sampler2D u_Texture;
            uniform sampler2D u_Depth;
            uniform vec4 u_Color;
            uniform float u_UseDepth;
            uniform vec2 u_Viewport;
            uniform vec4 u_DepthUvA;
            uniform vec4 u_DepthUvB;
            varying vec2 v_Uv;
            varying float v_ViewDepth;
            void main() {
                vec4 c = texture2D(u_Texture, v_Uv) * u_Color;
                if (u_UseDepth > 0.5) {
                    vec2 s = gl_FragCoord.xy / u_Viewport;
                    vec2 uv = mix(mix(u_DepthUvA.xy, u_DepthUvA.zw, s.x), mix(u_DepthUvB.xy, u_DepthUvB.zw, s.x), s.y);
                    vec4 d = texture2D(u_Depth, uv);
                    // milimetry z dwóch bajtów (LUMINANCE = młodszy, ALPHA = starszy)
                    float meters = (d.r * 255.0 + d.a * 255.0 * 256.0) * 0.001;
                    if (meters > 0.0) {
                        // realny przedmiot bliżej niż postać → postać za nim (miękka krawędź)
                        c *= smoothstep(-0.12, -0.04, meters - v_ViewDepth);
                    }
                }
                gl_FragColor = c;
            }
        """
    }
}
