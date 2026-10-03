package com.ludzik.ar.capture

import android.content.Context
import android.media.MediaRecorder
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Nagrywanie sceny AR: MediaRecorder z wejściem typu Surface, do którego renderer
 * rysuje każdą klatkę drugi raz (przez dodatkową powierzchnię EGL).
 * Wszystkie metody poza konstruktorem wołamy z wątku GL.
 */
class VideoRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var file: File? = null
    private var lastFrameNs = 0L
    private var frames = 0

    var width = 0
        private set
    var height = 0
        private set
    var startedAtNs = 0L
        private set
    val isRecording get() = recorder != null

    /** Rozmiar wideo w proporcjach ekranu, wielokrotność 16, dłuższy bok ≤ 1280. */
    fun start(screenW: Int, screenH: Int): Boolean {
        if (isRecording) return true
        val scale = minOf(1f, 1280f / maxOf(screenW, screenH))
        width = ((screenW * scale).toInt() / 16) * 16
        height = ((screenH * scale).toInt() / 16) * 16
        val out = File(context.cacheDir, "ludzik_${System.currentTimeMillis()}.mp4")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            r.setVideoSize(width, height)
            r.setVideoFrameRate(30)
            r.setVideoEncodingBitRate(6_000_000)
            r.setOutputFile(out.absolutePath)
            r.prepare()
            eglSurface = createEglSurface(r) ?: throw IllegalStateException("Nie udało się utworzyć powierzchni EGL")
            r.start()
        } catch (e: Exception) {
            Log.e(TAG, "Start nagrywania nie powiódł się", e)
            releaseSurface()
            r.release()
            out.delete()
            return false
        }
        recorder = r
        file = out
        startedAtNs = System.nanoTime()
        lastFrameNs = 0L
        frames = 0
        return true
    }

    private fun createEglSurface(r: MediaRecorder): EGLSurface? {
        val display = EGL14.eglGetCurrentDisplay()
        val ctx = EGL14.eglGetCurrentContext()
        val id = IntArray(1)
        EGL14.eglQueryContext(display, ctx, EGL14.EGL_CONFIG_ID, id, 0)
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        EGL14.eglChooseConfig(display, intArrayOf(EGL14.EGL_CONFIG_ID, id[0], EGL14.EGL_NONE), 0, configs, 0, 1, num, 0)
        if (num[0] == 0) return null
        val s = EGL14.eglCreateWindowSurface(display, configs[0], r.surface, intArrayOf(EGL14.EGL_NONE), 0)
        return if (s == EGL14.EGL_NO_SURFACE) null else s
    }

    /** Rysuje klatkę do enkodera (maks. 30 kl./s), potem przywraca ekran jako cel. */
    fun captureFrame(nowNs: Long, render: (w: Int, h: Int) -> Unit) {
        if (!isRecording || eglSurface == EGL14.EGL_NO_SURFACE) return
        if (lastFrameNs != 0L && nowNs - lastFrameNs < 30_000_000L) return
        lastFrameNs = nowNs
        val display = EGL14.eglGetCurrentDisplay()
        val ctx = EGL14.eglGetCurrentContext()
        val draw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        val read = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
        if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, ctx)) return
        render(width, height)
        EGLExt.eglPresentationTimeANDROID(display, eglSurface, nowNs)
        EGL14.eglSwapBuffers(display, eglSurface)
        frames++
        EGL14.eglMakeCurrent(display, draw, read, ctx)
    }

    /** Zatrzymuje nagrywanie; zwraca plik albo null, gdy nic się nie nagrało. */
    fun stop(): File? {
        val r = recorder ?: return null
        recorder = null
        releaseSurface()
        val ok = try {
            r.stop()
            frames > 0
        } catch (e: RuntimeException) {
            // MediaRecorder rzuca, gdy nagranie jest puste/za krótkie.
            Log.w(TAG, "stop() bez klatek", e)
            false
        } finally {
            r.release()
        }
        val f = file
        file = null
        if (!ok) {
            f?.delete()
            return null
        }
        return f
    }

    private fun releaseSurface() {
        if (eglSurface != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(EGL14.eglGetCurrentDisplay(), eglSurface)
            eglSurface = EGL14.EGL_NO_SURFACE
        }
    }

    private companion object {
        const val TAG = "VideoRecorder"
    }
}
