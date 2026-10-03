package com.ludzik.game.doodle

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.ludzik.game.characters.CharacterRegistry
import com.ludzik.game.characters.doodle.BubbleArt
import com.ludzik.game.characters.doodle.DecalArt
import com.ludzik.game.characters.doodle.SpriteAtlas
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Renderuje proceduralną grafikę do PNG (build/doodles), żeby można ją było obejrzeć
 * bez telefonu, i sprawdza, że klatki nie są puste.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DoodleRenderTest {
    private val out = File("build/doodles").apply { mkdirs() }

    private fun save(bmp: Bitmap, name: String, background: Int = Color.rgb(120, 110, 100)) {
        val bg = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        Canvas(bg).apply {
            drawColor(background)
            drawBitmap(bmp, 0f, 0f, null)
        }
        File(out, name).outputStream().use { bg.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun atlasesAreNotEmpty() {
        for (kind in CharacterRegistry.kinds) {
            val atlas = SpriteAtlas.build(kind)
            save(atlas, "atlas_${kind.id}.png")
            // każda komórka ma jakieś nieprzezroczyste piksele
            val cell = SpriteAtlas.CELL
            for (row in 0 until atlas.height / cell) for (col in 0 until atlas.width / cell) {
                var opaque = 0
                for (y in 0 until cell step 4) for (x in 0 until cell step 4) {
                    if (Color.alpha(atlas.getPixel(col * cell + x, row * cell + y)) > 128) opaque++
                }
                assertTrue("pusta klatka ${kind.id} r$row c$col", opaque > 20)
            }
        }
    }

    @Test
    fun decalsAndBubbles() {
        save(DecalArt.build(), "decals.png", Color.rgb(200, 190, 170))
        val b = BubbleArt.render("Długopisu nie da się wymazać!", 3)
        save(b.bitmap, "bubble.png")
        assertTrue(b.aspect > 1f)
    }
}
