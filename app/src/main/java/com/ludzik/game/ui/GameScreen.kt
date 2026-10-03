package com.ludzik.game.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ludzik.game.characters.World
import com.ludzik.game.game.GameHost
import com.ludzik.game.game.HudState
import com.ludzik.game.game.SpawnItem
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Ekran gry: widok 3D z Filament + sterowanie dotykiem. */
@Composable
fun GameScreen(
    host: GameHost,
    toast: String?,
    flashCounter: Int,
    onBack: () -> Unit,
    onPhoto: () -> Unit,
    onToastShown: () -> Unit,
) {
    val game = host.game
    var selected by remember { mutableStateOf(game.selected) }
    var screen by remember { mutableStateOf(IntSize(1, 1)) }
    BackHandler(onBack = onBack)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { screen = it },
    ) {
        AndroidView(factory = { host.surfaceView }, modifier = Modifier.fillMaxSize())

        // Strefy dotyku: lewa połowa = joystick, prawa = rozglądanie się i dotykanie.
        Row(Modifier.fillMaxSize()) {
            JoystickArea(
                modifier = Modifier.weight(0.42f).fillMaxHeight(),
                onMove = { x, y ->
                    game.moveX = x
                    game.moveY = y
                },
            )
            LookArea(
                modifier = Modifier.weight(0.58f).fillMaxHeight(),
                onLook = { dx, dy -> game.look(-dx * LOOK_SPEED, -dy * LOOK_SPEED) },
                onTap = { x, y, areaWidth ->
                    // współrzędne względem całego ekranu → promień przez punkt dotknięcia
                    val sx = screen.width - areaWidth + x
                    val nx = sx / screen.width * 2f - 1f
                    val ny = 1f - y / screen.height * 2f
                    game.interact(game.rayThrough(nx, ny, host.aspect))
                },
            )
        }

        GameHud(
            hud = host.hud,
            selected = selected,
            toast = toast,
            flashCounter = flashCounter,
            onBack = onBack,
            onClear = game::clearAll,
            onPhoto = onPhoto,
            onSpawn = { game.spawnAtCrosshair() },
            onThrow = { game.throwSelected() },
            onJump = game::jump,
            onSelect = {
                selected = it
                game.selected = it
            },
            onToastShown = onToastShown,
        )
    }
}

/** Nakładka bez zależności od Filament — da się ją podejrzeć w testach. */
@Composable
fun GameHud(
    hud: HudState,
    selected: SpawnItem,
    toast: String?,
    flashCounter: Int,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onPhoto: () -> Unit,
    onSpawn: () -> Unit,
    onThrow: () -> Unit,
    onJump: () -> Unit,
    onSelect: (SpawnItem) -> Unit,
    onToastShown: () -> Unit,
) {
    val flash = remember { Animatable(0f) }
    var showHelp by remember { mutableStateOf(true) }
    LaunchedEffect(flashCounter) {
        if (flashCounter > 0) {
            flash.snapTo(0.85f)
            flash.animateTo(0f, tween(450))
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2600)
            onToastShown()
        }
    }
    LaunchedEffect(hud.ready) {
        if (hud.ready) {
            delay(9000)
            showHelp = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        PlayerHands(hud.walkPhase, hud.walkAmount, Modifier.fillMaxSize())
        Crosshair(hud.aimLabel, Modifier.align(Alignment.Center))

        Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DoodleButton("←", onBack, seed = 3)
                Spacer(Modifier.width(8.dp))
                DoodleButton("Ludziki ${hud.doodles}/${World.MAX_CHARACTERS} · Rzeczy ${hud.things}", {}, seed = 5)
                Spacer(Modifier.weight(1f))
                DoodleButton("Zdjęcie", onPhoto, seed = 21)
                Spacer(Modifier.width(8.dp))
                DoodleButton("Wymaż", onClear, seed = 9, enabled = hud.doodles + hud.things > 0)
            }
            AnimatedVisibility(showHelp && hud.ready, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                StickyNote(
                    "Lewy kciuk: chodzenie · prawy: rozglądanie się\nCeluj krzyżykiem i naciśnij „Respnij”. Dotknij ludzika, żeby go zaczepić!",
                    Modifier.padding(top = 6.dp),
                    seed = 2,
                )
            }
            Spacer(Modifier.weight(1f))
            AnimatedVisibility(toast != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.CenterHorizontally)) {
                StickyNote(toast ?: "", Modifier.padding(6.dp), color = Doodle.PaperLight, seed = 4)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                SpawnPicker(selected, onSelect, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selected is SpawnItem.Thing) DoodleButton("Rzuć", onThrow, seed = 31)
                        DoodleButton("Skok", onJump, seed = 32)
                    }
                    DoodleButton("Respnij: ${selected.label}", onSpawn, seed = 33, fill = Doodle.Highlighter)
                }
            }
        }

        Box(Modifier.fillMaxSize().alpha(flash.value).background(Color.White))

        if (!hud.ready) LoadingOverlay(hud.loading, hud.loadingText)
    }
}

@Composable
private fun LoadingOverlay(progress: Float, text: String) {
    Box(Modifier.fillMaxSize().gridPaper(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ludzik 3D", style = MaterialTheme.typography.displayLarge)
            Spacer(Modifier.height(10.dp))
            Text("$text…", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.width(280.dp).height(10.dp),
                color = Doodle.Cover,
                trackColor = Doodle.GridBlue,
            )
        }
    }
}

/** Krzyżyk rysowany ołówkiem + podpis, na co celujemy. */
@Composable
private fun Crosshair(label: String?, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(34.dp)) {
            val c = center
            val r = size.minDimension / 2
            val w = 2.5.dp.toPx()
            for (col in listOf(Color.White.copy(alpha = 0.85f) to w * 2.2f, Doodle.Ink to w)) {
                drawLine(col.first, Offset(c.x - r, c.y + 1f), Offset(c.x - r * 0.35f, c.y - 1f), col.second, StrokeCap.Round)
                drawLine(col.first, Offset(c.x + r * 0.35f, c.y + 1f), Offset(c.x + r, c.y), col.second, StrokeCap.Round)
                drawLine(col.first, Offset(c.x - 1f, c.y - r), Offset(c.x + 1f, c.y - r * 0.35f), col.second, StrokeCap.Round)
                drawLine(col.first, Offset(c.x, c.y + r * 0.35f), Offset(c.x - 1f, c.y + r), col.second, StrokeCap.Round)
            }
        }
        if (label != null) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = Doodle.Ink,
                modifier = Modifier
                    .offset(y = 34.dp)
                    .background(Doodle.PaperLight.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp),
            )
        }
    }
}

/**
 * Ręce gracza narysowane ołówkiem (jesteś ludzikiem z zeszytu!). Bujają się w rytm kroków.
 */
@Composable
private fun PlayerHands(phase: Float, amount: Float, modifier: Modifier) {
    Canvas(modifier) {
        val bobX = sin(phase) * 14.dp.toPx() * amount
        val bobY = abs(cos(phase)) * 10.dp.toPx() * amount
        drawArm(right = true, dx = bobX, dy = bobY)
    }
}

private fun DrawScope.drawArm(right: Boolean, dx: Float, dy: Float) {
    val s = size.minDimension
    val sign = if (right) 1f else -1f
    // Ręka wystaje zza przycisków w prawym dolnym rogu i trzyma ołówek nad nimi.
    val baseX = if (right) size.width * 0.80f else size.width * 0.2f
    val start = Offset(baseX + sign * s * 0.16f + dx, size.height + s * 0.02f + dy)
    val elbow = Offset(baseX + sign * s * 0.06f + dx, size.height - s * 0.22f + dy)
    val hand = Offset(baseX - sign * s * 0.03f + dx, size.height - s * 0.40f + dy)
    val arm = Path().apply {
        moveTo(start.x, start.y)
        quadraticTo(elbow.x + sign * 6f, elbow.y + 8f, elbow.x, elbow.y)
        lineTo(hand.x, hand.y)
    }
    val halo = Color.White.copy(alpha = 0.9f)
    drawPath(arm, halo, style = Stroke(s * 0.035f, cap = StrokeCap.Round))
    drawPath(arm, Doodle.Ink, style = Stroke(s * 0.014f, cap = StrokeCap.Round))
    // dłoń: kółko z „palcami”
    drawCircle(halo, s * 0.045f, hand)
    drawCircle(Doodle.Ink, s * 0.035f, hand, style = Stroke(s * 0.011f))
    for (i in 0..2) {
        val a = (-100f - i * 25f) * (Math.PI / 180f).toFloat()
        val f = Offset(hand.x + cos(a) * s * 0.035f * sign, hand.y + sin(a) * s * 0.035f)
        val e = Offset(hand.x + cos(a) * s * 0.065f * sign, hand.y + sin(a) * s * 0.065f)
        drawLine(Doodle.Ink, f, e, s * 0.01f, StrokeCap.Round)
    }
    if (right) {
        // ołówek w prawej ręce
        val tip = Offset(hand.x - s * 0.05f, hand.y - s * 0.12f)
        val end = Offset(hand.x + s * 0.035f, hand.y + s * 0.05f)
        drawLine(halo, end, tip, s * 0.04f, StrokeCap.Round)
        drawLine(Color(0xFFFFD54F), end, tip, s * 0.024f, StrokeCap.Butt)
        drawLine(Doodle.Ink, Offset(tip.x + (end.x - tip.x) * 0.12f, tip.y + (end.y - tip.y) * 0.12f), tip, s * 0.012f, StrokeCap.Round)
    }
}

/** Joystick pojawia się tam, gdzie położysz kciuk. */
@Composable
private fun JoystickArea(modifier: Modifier, onMove: (Float, Float) -> Unit) {
    var center by remember { mutableStateOf<Offset?>(null) }
    var knob by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier.pointerInput(Unit) {
            val radius = 60.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                center = down.position
                knob = Offset.Zero
                val id = down.id
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == id } ?: break
                    if (!change.pressed) break
                    var d = change.position - down.position
                    val len = hypot(d.x, d.y)
                    if (len > radius) d = d * (radius / len)
                    knob = d
                    onMove(d.x / radius, -d.y / radius)
                    change.consume()
                }
                center = null
                knob = Offset.Zero
                onMove(0f, 0f)
            }
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center ?: Offset(70.dp.toPx() + 30.dp.toPx(), size.height - 150.dp.toPx())
            val r = 60.dp.toPx()
            val a = if (center == null) 0.35f else 0.8f
            drawCircle(Doodle.PaperLight.copy(alpha = a * 0.5f), r, c)
            drawCircle(Doodle.Ink.copy(alpha = a), r, c, style = Stroke(2.5.dp.toPx()))
            drawCircle(Doodle.PaperLight.copy(alpha = a), r * 0.42f, c + knob)
            drawCircle(Doodle.Ink.copy(alpha = a), r * 0.42f, c + knob, style = Stroke(2.5.dp.toPx()))
        }
    }
}

/** Przeciąganie obraca kamerę, krótkie dotknięcie = „zaczep” to, co pod palcem. */
@Composable
private fun LookArea(modifier: Modifier, onLook: (Float, Float) -> Unit, onTap: (Float, Float, Float) -> Unit) {
    Box(
        modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val id = down.id
                var moved = 0f
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == id } ?: break
                    if (!change.pressed) break
                    val d = change.positionChange()
                    moved += abs(d.x) + abs(d.y)
                    if (moved > viewConfiguration.touchSlop) onLook(d.x, d.y)
                    change.consume()
                }
                if (moved <= viewConfiguration.touchSlop) onTap(down.position.x, down.position.y, size.width.toFloat())
            }
        },
    )
}

private const val LOOK_SPEED = 0.0045f
