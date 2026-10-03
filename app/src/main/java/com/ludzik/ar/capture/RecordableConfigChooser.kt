package com.ludzik.ar.capture

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay

/**
 * Wybiera konfigurację EGL z flagą EGL_RECORDABLE_ANDROID, żeby ten sam kontekst
 * mógł rysować do powierzchni enkodera wideo. Gdy jej brak — zwykła konfiguracja RGBA8888.
 */
class RecordableConfigChooser : GLSurfaceView.EGLConfigChooser {
    override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
        return choose(egl, display, recordable = true)
            ?: choose(egl, display, recordable = false)
            ?: throw IllegalStateException("Brak konfiguracji EGL RGBA8888")
    }

    private fun choose(egl: EGL10, display: EGLDisplay, recordable: Boolean): EGLConfig? {
        val attribs = mutableListOf(
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_DEPTH_SIZE, 16,
            EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        )
        if (recordable) attribs += listOf(EGL_RECORDABLE_ANDROID, 1)
        attribs += EGL10.EGL_NONE
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        if (!egl.eglChooseConfig(display, attribs.toIntArray(), configs, 1, num) || num[0] == 0) return null
        return configs[0]
    }

    private companion object {
        const val EGL_OPENGL_ES2_BIT = 4
        const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}
