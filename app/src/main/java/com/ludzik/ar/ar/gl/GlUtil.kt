package com.ludzik.ar.ar.gl

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object GlUtil {
    private const val TAG = "LudzikGL"

    fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IllegalStateException("Błąd kompilacji shadera: $log")
        }
        return shader
    }

    fun program(vertex: String, fragment: String): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertex)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        val status = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(p)
            GLES20.glDeleteProgram(p)
            throw IllegalStateException("Błąd linkowania programu: $log")
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return p
    }

    fun floatBuffer(capacityFloats: Int): FloatBuffer =
        ByteBuffer.allocateDirect(capacityFloats * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

    fun floatBuffer(data: FloatArray): FloatBuffer = floatBuffer(data.size).apply {
        put(data)
        position(0)
    }

    /** Wgrywa bitmapę jako teksturę (premultiplied alpha). */
    fun texture(bitmap: Bitmap): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return ids[0]
    }

    fun deleteTexture(id: Int) {
        GLES20.glDeleteTextures(1, intArrayOf(id), 0)
    }

    fun checkError(where: String) {
        val e = GLES20.glGetError()
        if (e != GLES20.GL_NO_ERROR) Log.w(TAG, "GL error 0x${Integer.toHexString(e)} @ $where")
    }
}
