package com.ludzik.game.audio

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Prosty syntezator: każdy efekt to kilka linijek matematyki zamiast pliku audio.
 * Wynik zapisujemy jako WAV, bo SoundPool ładuje pliki/zasoby, a nie surowe próbki.
 */
object SoundSynth {
    const val RATE = 22050
    private const val TWO_PI = (2.0 * PI).toFloat()

    fun generate(sfx: Sfx): FloatArray = when (sfx) {
        Sfx.POP -> pop()
        Sfx.BOING -> boing()
        Sfx.PLUM -> plum()
        Sfx.SCRIBBLE -> scribble()
        Sfx.SQUEAK -> squeak()
        Sfx.TICK -> tick()
        Sfx.ZIP -> zip()
        Sfx.HEY -> hey()
        Sfx.SHUTTER -> shutter()
        Sfx.THUD -> thud()
    }

    private inline fun synth(seconds: Float, f: (t: Float, i: Int) -> Float): FloatArray {
        val n = (seconds * RATE).toInt()
        return FloatArray(n) { i -> f(i / RATE.toFloat(), i) }
    }

    /** Oscylator z ciągłą fazą przy zmiennej częstotliwości. */
    private class Osc {
        var phase = 0f
        fun sine(freq: Float): Float {
            phase += TWO_PI * freq / RATE
            if (phase > TWO_PI) phase -= TWO_PI
            return sin(phase)
        }
        fun saw(freq: Float): Float {
            phase += freq / RATE
            if (phase > 1f) phase -= 1f
            return phase * 2f - 1f
        }
    }

    private fun pop(): FloatArray {
        val o = Osc()
        return synth(0.12f) { t, _ ->
            val f = 500f + 1400f * (t / 0.12f)
            o.sine(f) * exp(-t * 35f) * 0.9f
        }
    }

    private fun boing(): FloatArray {
        val o = Osc()
        return synth(0.32f) { t, _ ->
            val f = 180f + 520f * t + 60f * sin(TWO_PI * 16f * t)
            o.sine(f) * exp(-t * 7f) * 0.7f
        }
    }

    private fun plum(): FloatArray {
        val a = Osc()
        val b = Osc()
        return synth(0.32f) { t, _ ->
            val f1 = 260f + 900f * exp(-t * 28f)
            val drop = a.sine(f1) * exp(-t * 14f)
            val t2 = (t - 0.07f).coerceAtLeast(0f)
            val bloop = if (t > 0.07f) b.sine(180f + 500f * exp(-t2 * 30f)) * exp(-t2 * 18f) * 0.6f else 0f
            (drop + bloop) * 0.75f
        }
    }

    private fun scribble(): FloatArray {
        val rnd = Random(3)
        var lp = 0f
        var hpPrev = 0f
        var hpOut = 0f
        return synth(0.42f) { t, _ ->
            val white = rnd.nextFloat() * 2f - 1f
            lp += (white - lp) * 0.35f
            // filtr górnoprzepustowy — „suchy” szelest grafitu
            hpOut = 0.92f * (hpOut + lp - hpPrev)
            hpPrev = lp
            val strokes = abs(sin(TWO_PI * 6.5f * t)).let { it * it }
            val env = (t / 0.03f).coerceAtMost(1f) * ((0.42f - t) / 0.08f).coerceIn(0f, 1f)
            hpOut * strokes * env * 0.9f
        }
    }

    private fun squeak(): FloatArray {
        val o = Osc()
        val rnd = Random(4)
        return synth(0.34f) { t, _ ->
            val f = 1250f + 250f * sin(TWO_PI * 9f * t) + 300f * t
            val bursts = 0.55f + 0.45f * sin(TWO_PI * 14f * t)
            val env = (t / 0.02f).coerceAtMost(1f) * exp(-t * 4f)
            (o.sine(f) * 0.7f + (rnd.nextFloat() - 0.5f) * 0.25f) * bursts * env * 0.6f
        }
    }

    private fun tick(): FloatArray {
        val rnd = Random(5)
        return synth(0.2f) { t, _ ->
            val local = t % 0.065f
            val n = rnd.nextFloat() * 2f - 1f
            if (t < 0.195f) n * exp(-local * 260f) * 0.5f else 0f
        }
    }

    private fun zip(): FloatArray {
        val o = Osc()
        val rnd = Random(6)
        return synth(0.36f) { t, _ ->
            val f = 1800f * exp(-t * 4f) + 300f
            val env = (t / 0.02f).coerceAtMost(1f) * exp(-t * 6f)
            (o.saw(f) * 0.35f + (rnd.nextFloat() - 0.5f) * 0.3f) * env * 0.6f
        }
    }

    private fun hey(): FloatArray {
        val o = Osc()
        val o2 = Osc()
        var lp = 0f
        return synth(0.22f) { t, _ ->
            val f = 300f + 160f * sin(PI.toFloat() * t / 0.22f)
            val raw = o.saw(f) * 0.6f + o2.sine(f * 2.5f) * 0.3f
            lp += (raw - lp) * 0.25f
            val env = sin(PI.toFloat() * t / 0.22f)
            lp * env * 0.8f
        }
    }

    private fun shutter(): FloatArray {
        val rnd = Random(7)
        return synth(0.16f) { t, _ ->
            val n = rnd.nextFloat() * 2f - 1f
            val a = exp(-t * 90f)
            val b = if (t > 0.07f) exp(-(t - 0.07f) * 70f) else 0f
            n * (a + b * 0.8f) * 0.7f
        }
    }

    /** Głuche „tup” — niski sinus z szybko opadającą wysokością i odrobiną szumu. */
    private fun thud(): FloatArray {
        val o = Osc()
        val rnd = Random(8)
        return synth(0.22f) { t, _ ->
            val f = 70f + 140f * exp(-t * 30f)
            (o.sine(f) * 0.9f + (rnd.nextFloat() - 0.5f) * 0.4f * exp(-t * 60f)) * exp(-t * 14f)
        }
    }

    fun writeWav(samples: FloatArray, file: File) {
        val pcm = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) pcm.putShort((s.coerceIn(-1f, 1f) * 32767f).toInt().toShort())
        val dataLen = pcm.capacity()
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataLen); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(dataLen)
        }
        val out = ByteArrayOutputStream(44 + dataLen)
        out.write(header.array())
        out.write(pcm.array())
        file.writeBytes(out.toByteArray())
    }
}
