package com.ludzik.ar.ar

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import com.google.ar.core.Session

/** Przekazuje do ARCore obrót i rozmiar ekranu, gdy się zmienią. */
class DisplayRotationHelper(context: Context) : DisplayManager.DisplayListener {
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    private val display: Display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)

    @Volatile
    private var changed = false
    private var width = 0
    private var height = 0

    fun onResume() {
        displayManager.registerDisplayListener(this, null)
        changed = true
    }

    fun onPause() = displayManager.unregisterDisplayListener(this)

    fun onSurfaceChanged(w: Int, h: Int) {
        width = w
        height = h
        changed = true
    }

    fun updateSessionIfNeeded(session: Session) {
        if (changed && width > 0) {
            session.setDisplayGeometry(display.rotation, width, height)
            changed = false
        }
    }

    override fun onDisplayAdded(displayId: Int) {}
    override fun onDisplayRemoved(displayId: Int) {}
    override fun onDisplayChanged(displayId: Int) {
        changed = true
    }
}
