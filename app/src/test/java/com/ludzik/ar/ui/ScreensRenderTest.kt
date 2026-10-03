package com.ludzik.ar.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import android.graphics.Canvas
import com.ludzik.ar.ar.ArUiState
import com.ludzik.ar.ar.TrackingHint
import com.ludzik.ar.characters.Kleks
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Zrzuty ekranów Compose do build/screens — podgląd UI bez telefonu. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
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
    fun permission() = shot("permission") { PermissionScreen(false, {}, {}, {}) }

    @Test
    fun unsupported() = shot("unsupported") {
        UnsupportedScreen("Ten telefon nie obsługuje ARCore, więc ludziki nie mogą chodzić po Twoim pokoju.") {}
    }

    @Test
    fun overlay() = shot("overlay") {
        Box(Modifier.fillMaxSize().background(Color(0xFF6D6A62))) {
            ArOverlay(
                state = ArUiState(hint = TrackingHint.PLACE, characterCount = 4, recording = true, recordSeconds = 7, debugText = "śledzenie: tracking (none), OK 87% klatek\npłaszczyzny: 2 poziome, 1 pionowe, wirtualne: 0\nostatnie dotknięcie: postawiono obok kratki"),
                selected = Kleks,
                toast = "Zdjęcie zapisane w galerii (Obrazy/LudzikAR)",
                flashAlpha = 0f,
                onBack = {}, onClear = {}, onPhoto = {}, onRecord = {}, onSelect = {},
            )
        }
    }
}
