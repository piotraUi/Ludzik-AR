package com.ludzik.ar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ludzik.ar.ar.ArController
import com.ludzik.ar.ar.ArUiState
import com.ludzik.ar.characters.CharacterKind
import androidx.compose.ui.graphics.RectangleShape
import com.ludzik.ar.ar.TrackingHint
import com.ludzik.ar.characters.CharacterRegistry
import com.ludzik.ar.characters.World
import kotlinx.coroutines.delay

@Composable
fun ArScreen(
    controller: ArController,
    onBack: () -> Unit,
    onPhoto: () -> Unit,
    onRecord: () -> Unit,
) {
    val state by controller.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable { mutableStateOf(CharacterRegistry.kinds.first().id) }
    val selected = CharacterRegistry.byId(selectedId) ?: CharacterRegistry.kinds.first()
    var toast by remember { mutableStateOf<String?>(null) }
    val flash = remember { Animatable(0f) }

    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { controller.selectKind(selected) }
    LaunchedEffect(controller) {
        controller.messages.collect { msg ->
            toast = msg
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2600)
            toast = null
        }
    }
    LaunchedEffect(state.flashCounter) {
        if (state.flashCounter > 0) {
            flash.snapTo(0.85f)
            flash.animateTo(0f, tween(450))
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { controller.glView }, modifier = Modifier.fillMaxSize())
        ArOverlay(
            state = state,
            selected = selected,
            toast = toast,
            flashAlpha = flash.value,
            onBack = onBack,
            onClear = controller::clearAll,
            onCounter = controller::toggleDebug,
            onPhoto = onPhoto,
            onRecord = onRecord,
            onSelect = {
                selectedId = it.id
                controller.selectKind(it)
            },
        )
    }
}

/** Interfejs nad obrazem z kamery — bez zależności od ARCore (da się go podglądać i testować). */
@Composable
fun ArOverlay(
    state: ArUiState,
    selected: CharacterKind,
    toast: String?,
    flashAlpha: Float,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onCounter: () -> Unit = {},
    onPhoto: () -> Unit,
    onRecord: () -> Unit,
    onSelect: (CharacterKind) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().alpha(flashAlpha).background(Color.White))

        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // górny pasek
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DoodleButton("←", onBack, seed = 3)
                Spacer(Modifier.weight(1f))
                DoodleButton("${state.characterCount}/${World.MAX_CHARACTERS}", onCounter, seed = 5)
                Spacer(Modifier.width(8.dp))
                DoodleButton("Wymaż", onClear, seed = 9, enabled = state.characterCount > 0)
            }
            val hint = hintText(state.hint, selected.displayName)
            AnimatedVisibility(hint != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                StickyNote(hint ?: "", Modifier.padding(horizontal = 24.dp, vertical = 4.dp), seed = state.hint.ordinal)
            }

            state.debugText?.let {
                Text(
                    it,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(8.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(toast != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                StickyNote(toast ?: "", Modifier.padding(16.dp), color = Doodle.PaperLight, seed = 4)
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DoodleButton(
                    if (state.busySaving) "Zapisuję…" else "Zrób zdjęcie",
                    onPhoto,
                    seed = 21,
                    enabled = !state.busySaving,
                )
                DoodleButton(
                    if (state.recording) "Stop %d:%02d".format(state.recordSeconds / 60, state.recordSeconds % 60) else "Nagraj",
                    onRecord,
                    seed = 22,
                    fill = if (state.recording) Color(0xFFFFCDD2) else Doodle.PaperLight,
                    leading = {
                        Box(
                            Modifier
                                .size(12.dp)
                                .background(Doodle.RecordRed, if (state.recording) RectangleShape else CircleShape),
                        )
                    },
                )
            }
            CharacterPicker(
                selected = selected,
                onSelect = onSelect,
                modifier = Modifier.padding(bottom = 12.dp, top = 4.dp),
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

private fun hintText(hint: TrackingHint, kindName: String): String? = when (hint) {
    TrackingHint.NONE -> null
    TrackingHint.STARTING -> "Poruszaj powoli telefonem, żeby rozejrzeć się po pokoju"
    TrackingHint.SEARCHING_PLANE -> "Szukam podłogi… celuj w podłogę i powoli poruszaj telefonem"
    TrackingHint.ONLY_WALLS -> "Widzę ścianę, ale nie podłogę — skieruj telefon bardziej w dół"
    TrackingHint.TAP_ANYWAY -> "Podłoga jest trudna do rozpoznania. Dotknij ekranu w miejscu podłogi, a postawię $kindName na oko"
    TrackingHint.PLACE -> "Dotknij kratkowanej podłogi, żeby postawić: $kindName"
    TrackingHint.LOST_MOTION -> "Za szybko! Poruszaj telefonem wolniej"
    TrackingHint.LOST_DARK -> "Za ciemno — zapal światło, ludziki się boją"
    TrackingHint.LOST_FEATURES -> "Ludziki się zgubiły — poruszaj telefonem i celuj w przedmioty z wzorem"
    TrackingHint.LOST_OTHER -> "Zgubiłem trop — poruszaj telefonem"
}
