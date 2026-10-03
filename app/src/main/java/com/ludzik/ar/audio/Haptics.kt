package com.ludzik.ar.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Krótkie wibracje przy interakcjach. */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun play(h: Haptic) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val effect = when (h) {
            Haptic.LIGHT -> if (Build.VERSION.SDK_INT >= 29) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
            } else {
                VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            Haptic.MEDIUM -> VibrationEffect.createOneShot(35, VibrationEffect.DEFAULT_AMPLITUDE)
            Haptic.DOUBLE -> VibrationEffect.createWaveform(longArrayOf(0, 25, 70, 25), -1)
        }
        try {
            v.vibrate(effect)
        } catch (_: SecurityException) {
            // brak uprawnienia VIBRATE w niestandardowych ROM-ach — ignorujemy
        }
    }
}
