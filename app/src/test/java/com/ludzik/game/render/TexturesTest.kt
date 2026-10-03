package com.ludzik.game.render

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer

/** Regresja: wgrywanie fragmentu atlasu musi podawać szerokość fragmentu jako stride. */
class TexturesTest {
    @Test
    fun descriptorUsesRegionWidthAsStride() {
        val d = Textures.pixelDescriptor(ByteBuffer.allocateDirect(256 * 128 * 4), 256)
        assertEquals(256, d.stride)
        assertEquals(1, d.alignment)
    }
}
