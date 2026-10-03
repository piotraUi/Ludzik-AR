package com.ludzik.ar.ar.gl

import android.opengl.GLES20
import android.util.Log
import com.google.ar.core.Frame
import com.google.ar.core.exceptions.NotYetAvailableException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Mapa głębi z ARCore (16-bit, milimetry) jako tekstura GLES 2.0.
 * ES 2.0 nie ma tekstur 16-bitowych, więc pakujemy każdy piksel w LUMINANCE_ALPHA:
 * L = młodszy bajt, A = starszy bajt. Shader składa je z powrotem (filtr NEAREST!).
 */
class DepthTexture {
    var textureId = 0
        private set
    var isValid = false
        private set

    private var width = 0
    private var height = 0
    private var lastTimestamp = -1L
    private var packed: ByteBuffer? = null

    fun create() {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        textureId = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        isValid = false
        lastTimestamp = -1L
        width = 0
        height = 0
    }

    /** Wgrywa najnowszą mapę głębi, jeśli się zmieniła. */
    fun update(frame: Frame) {
        val image = try {
            frame.acquireDepthImage16Bits()
        } catch (e: NotYetAvailableException) {
            return // pierwsze klatki — głębia jeszcze się liczy
        } catch (e: Exception) {
            Log.w("DepthTexture", "Brak mapy głębi", e)
            return
        }
        image.use { img ->
            if (img.timestamp == lastTimestamp) return
            lastTimestamp = img.timestamp
            val plane = img.planes[0]
            val w = img.width
            val h = img.height
            val rowBytes = w * 2
            val src = plane.buffer.order(ByteOrder.nativeOrder())
            // Wiersze mogą mieć dopełnienie (rowStride > szerokość) — kopiujemy bez niego.
            val data = if (plane.rowStride == rowBytes) {
                src
            } else {
                val buf = packed?.takeIf { it.capacity() >= rowBytes * h }
                    ?: ByteBuffer.allocateDirect(rowBytes * h).also { packed = it }
                buf.clear()
                for (y in 0 until h) {
                    src.limit(y * plane.rowStride + rowBytes)
                    src.position(y * plane.rowStride)
                    buf.put(src)
                }
                buf.flip()
                buf
            }
            data.position(0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 2)
            if (w != width || h != height) {
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE_ALPHA, w, h, 0, GLES20.GL_LUMINANCE_ALPHA, GLES20.GL_UNSIGNED_BYTE, data)
                width = w
                height = h
            } else {
                GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, w, h, GLES20.GL_LUMINANCE_ALPHA, GLES20.GL_UNSIGNED_BYTE, data)
            }
            GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 4)
            isValid = true
        }
    }
}
