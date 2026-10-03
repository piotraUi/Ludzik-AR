package com.ludzik.ar.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import java.io.File
import java.util.EnumMap
import kotlin.random.Random

/** Odtwarza zsyntetyzowane efekty przez SoundPool (niskie opóźnienie). */
class SoundManager(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = EnumMap<Sfx, Int>(Sfx::class.java)
    private val lastPlayed = EnumMap<Sfx, Long>(Sfx::class.java)
    @Volatile
    var enabled = true

    init {
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
        Thread({
            for (sfx in Sfx.entries) {
                // wersja w nazwie: zmiana syntezy = nowe pliki
                val f = File(dir, "${sfx.name.lowercase()}_v1.wav")
                if (!f.exists()) SoundSynth.writeWav(SoundSynth.generate(sfx), f)
                val id = pool.load(f.absolutePath, 1)
                synchronized(ids) { ids[sfx] = id }
            }
        }, "sfx-synth").start()
    }

    fun play(sfx: Sfx, volume: Float = 1f) {
        if (!enabled) return
        val id = synchronized(ids) { ids[sfx] } ?: return
        val now = SystemClock.elapsedRealtime()
        val minGap = if (sfx == Sfx.TICK || sfx == Sfx.SCRIBBLE) 150L else 60L
        synchronized(lastPlayed) {
            if (now - (lastPlayed[sfx] ?: 0L) < minGap) return
            lastPlayed[sfx] = now
        }
        // Lekko losowa wysokość — każdy „plum” brzmi trochę inaczej.
        val rate = 0.9f + Random.nextFloat() * 0.22f
        val v = volume.coerceIn(0f, 1f)
        pool.play(id, v, v, 0, 0, rate)
    }

    fun release() = pool.release()
}
