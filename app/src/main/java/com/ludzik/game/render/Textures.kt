package com.ludzik.game.render

import android.graphics.Bitmap
import com.google.android.filament.Engine
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max

object Textures {
    // leniwe, bo TextureSampler woła kod natywny (niedostępny w testach na JVM)
    val mipmapped by lazy {
        TextureSampler(
            TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE,
        )
    }
    val linear by lazy {
        TextureSampler(
            TextureSampler.MinFilter.LINEAR, TextureSampler.MagFilter.LINEAR, TextureSampler.WrapMode.CLAMP_TO_EDGE,
        )
    }

    fun create(engine: Engine, width: Int, height: Int, mipmaps: Boolean): Texture {
        val levels = if (mipmaps) floor(ln(max(width, height).toDouble()) / ln(2.0)).toInt() + 1 else 1
        return Texture.Builder()
            .width(width)
            .height(height)
            .levels(levels)
            .sampler(Texture.Sampler.SAMPLER_2D)
            .format(Texture.InternalFormat.SRGB8_A8)
            .usage(if (mipmaps) Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE else Texture.Usage.DEFAULT)
            .build(engine)
    }

    fun fromBitmap(engine: Engine, bitmap: Bitmap, mipmaps: Boolean): Texture {
        val t = create(engine, bitmap.width, bitmap.height, mipmaps)
        upload(engine, t, bitmap, 0, 0)
        if (mipmaps) t.generateMipmaps(engine)
        return t
    }

    /**
     * Wgrywa bitmapę w miejsce (x, y). Materiał glTF w trybie BLEND sam mnoży kolor przez alfę,
     * więc wysyłamy piksele BEZ premultiplikacji (getPixels zwraca właśnie takie) — inaczej
     * półprzezroczyste krawędzie rysunków byłyby przyciemnione.
     */
    fun upload(engine: Engine, texture: Texture, bitmap: Bitmap, x: Int, y: Int) {
        val w = bitmap.width
        val h = bitmap.height
        val px = IntArray(w * h)
        bitmap.getPixels(px, 0, w, 0, 0, w, h)
        val buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder())
        for (c in px) {
            buf.put((c shr 16 and 0xFF).toByte())
            buf.put((c shr 8 and 0xFF).toByte())
            buf.put((c and 0xFF).toByte())
            buf.put((c ushr 24).toByte())
        }
        buf.flip()
        texture.setImage(engine, 0, x, y, w, h, pixelDescriptor(buf, w))
    }

    /**
     * Deskryptor z jawnym `stride` = szerokość wgrywanego fragmentu.
     * Przy stride = 0 Filament zakłada szerokość CAŁEJ tekstury, więc wgranie małego kawałka atlasu
     * (np. dymka 256 px w atlasie 1024 px) kończyło się BufferOverflowException.
     */
    fun pixelDescriptor(buffer: ByteBuffer, regionWidth: Int) = Texture.PixelBufferDescriptor(
        buffer, Texture.Format.RGBA, Texture.Type.UBYTE, 1, 0, 0, regionWidth, null, null,
    )
}
