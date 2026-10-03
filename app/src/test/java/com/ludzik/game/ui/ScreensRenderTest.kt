package com.ludzik.game.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.ludzik.game.game.HudState
import com.ludzik.game.game.SpawnItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Zrzuty ekranów Compose do build/screens — podgląd UI bez telefonu (orientacja pozioma). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w851dp-h393dp-land-xxhdpi")
class ScreensRenderTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { LudzikTheme { content() } }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val dir = File("build/screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun start() = shot("start") { StartScreen(onStart = {}) }

    @Test
    fun hud() {
        // tło: podgląd pokoju z przeglądarki (jeśli jest), żeby ocenić czytelność nakładki
        val bg = File("/tmp/claude-0/preview/land.png").takeIf { it.exists() }?.let { android.graphics.BitmapFactory.decodeFile(it.path) }
        val hud = HudState().apply {
            ready = true
            doodles = 4
            things = 3
            aimLabel = "Kleks"
            walkAmount = 0.6f
            walkPhase = 1f
        }
        shot("hud") {
            Box(Modifier.fillMaxSize()) {
                bg?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                GameHud(
                    hud = hud, selected = SpawnItem.all[6], toast = "Tu Kleks się nie zmieści — celuj w podłogę albo blat.",
                    flashCounter = 0, onBack = {}, onClear = {}, onPhoto = {}, onSpawn = {}, onThrow = {}, onJump = {},
                    onSelect = {}, onToastShown = {},
                )
            }
        }
    }

    @Test
    fun loading() = shot("loading") {
        GameHud(
            hud = HudState().apply { loading = 0.4f; loadingText = "Wnoszę meble" }, selected = SpawnItem.all[0], toast = null,
            flashCounter = 0, onBack = {}, onClear = {}, onPhoto = {}, onSpawn = {}, onThrow = {}, onJump = {},
            onSelect = {}, onToastShown = {},
        )
    }
}
