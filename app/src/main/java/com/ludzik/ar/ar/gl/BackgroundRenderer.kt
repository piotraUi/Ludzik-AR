package com.ludzik.ar.ar.gl

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame

/** Rysuje obraz z kamery na pełnym ekranie (z lekkim „papierowym” ociepleniem). */
class BackgroundRenderer {
    var textureId = -1
        private set
    private var program = 0
    private var aPos = 0
    private var aTex = 0
    private var uTex = 0

    private val quad = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
    private val quadBuf = GlUtil.floatBuffer(quad)
    /** UV obrazu kamery w rogach ekranu — te same współrzędne służą do mapy głębi. */
    val texCoords = FloatArray(8)
    private val texBuf = GlUtil.floatBuffer(8)

    fun create() {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        textureId = ids[0]
        val target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        GLES20.glBindTexture(target, textureId)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        program = GlUtil.program(VS, FS)
        aPos = GLES20.glGetAttribLocation(program, "a_Position")
        aTex = GLES20.glGetAttribLocation(program, "a_TexCoord")
        uTex = GLES20.glGetUniformLocation(program, "u_Texture")
    }

    /** Aktualizuje współrzędne tekstury po zmianie geometrii ekranu. */
    fun updateGeometry(frame: Frame) {
        if (frame.hasDisplayGeometryChanged()) {
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quad,
                Coordinates2d.TEXTURE_NORMALIZED, texCoords,
            )
            texBuf.position(0)
            texBuf.put(texCoords)
            texBuf.position(0)
        }
    }

    fun draw(frame: Frame) {
        if (frame.timestamp == 0L) return
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUniform1i(uTex, 0)
        quadBuf.position(0)
        texBuf.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quadBuf)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, texBuf)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    companion object {
        private const val VS = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """
        private const val FS = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES u_Texture;
            void main() {
                vec3 c = texture2D(u_Texture, v_TexCoord).rgb;
                // delikatnie w stronę koloru papieru
                gl_FragColor = vec4(mix(c, vec3(0.98, 0.96, 0.90), 0.07), 1.0);
            }
        """
    }
}
