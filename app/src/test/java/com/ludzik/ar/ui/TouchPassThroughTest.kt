package com.ludzik.ar.ui

import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.viewinterop.AndroidView
import com.ludzik.ar.ar.ArUiState
import com.ludzik.ar.ar.TrackingHint
import com.ludzik.ar.characters.Ludzik
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Dotknięcie środka ekranu musi przejść przez nakładkę Compose do widoku AR pod spodem. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class TouchPassThroughTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tapReachesArView() {
        val events = mutableListOf<Int>()
        compose.setContent {
            LudzikTheme {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(factory = { ctx ->
                        View(ctx).apply {
                            setOnTouchListener { _, ev -> events += ev.actionMasked; true }
                        }
                    }, modifier = Modifier.fillMaxSize())
                    ArOverlay(
                        state = ArUiState(hint = TrackingHint.TAP_ANYWAY),
                        selected = Ludzik, toast = "Komunikat", flashAlpha = 0f,
                        onBack = {}, onClear = {}, onPhoto = {}, onRecord = {}, onSelect = {},
                    )
                }
            }
        }
        compose.onRoot().performTouchInput { click(center) }
        compose.waitForIdle()
        println("zdarzenia: $events")
        assertTrue("widok AR nie dostał ACTION_UP: $events", MotionEvent.ACTION_UP in events)
    }
}
