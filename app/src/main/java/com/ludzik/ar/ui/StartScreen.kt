package com.ludzik.ar.ui

import androidx.compose.foundation.Image
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ludzik.ar.characters.CharacterRegistry
import com.ludzik.ar.characters.doodle.SpriteAtlas
import kotlin.random.Random

/** Ekran startowy: okładka szkolnego zeszytu. */
@Composable
fun StartScreen(onStart: () -> Unit) {
    val icons = remember {
        CharacterRegistry.kinds.map { SpriteAtlas.icon(it, 192).asImageBitmap() }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Doodle.Cover)
            .drawBehind { coverTexture() },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(start = 44.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(
                "ZESZYT  DO  RYSOWANIA",
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 13.sp,
                letterSpacing = 4.sp,
                fontWeight = FontWeight.Bold,
            )
            // naklejka z tytułem
            Column(
                Modifier
                    .rotate(-2f)
                    .fillMaxWidth(0.92f)
                    .drawBehind { wobblyRect(3, Doodle.Ink, 2.dp.toPx(), Doodle.PaperLight) }
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Ludzik AR", style = MaterialTheme.typography.displayLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                LabelLine("Imię:", "Ludzik")
                LabelLine("Klasa:", "3b")
                LabelLine("Przedmiot:", "rozszerzona rzeczywistość")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                icons.forEachIndexed { i, img ->
                    Image(
                        img,
                        contentDescription = CharacterRegistry.kinds[i].displayName,
                        modifier = Modifier
                            .size(58.dp)
                            .rotate(((i % 3) - 1) * 6f),
                    )
                }
            }
            DoodleButton(
                "Start",
                onClick = onStart,
                modifier = Modifier.width(180.dp),
                fill = Doodle.Highlighter,
                seed = 11,
            )
            Text(
                "Postaw ludziki z zeszytu w swoim pokoju.\nDotknij ich, a coś powiedzą!",
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LabelLine(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .drawBehind {
                drawLine(Doodle.GridBlue, Offset(0f, size.height), Offset(size.width, size.height), 1.5.dp.toPx())
            },
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Doodle.Ink.copy(alpha = 0.6f))
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, color = Color(0xFF1E3A8A))
    }
}

/** Faktura tektury, grzbiet z zszywkami. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.coverTexture() {
    val rnd = Random(42)
    repeat(900) {
        val x = rnd.nextFloat() * size.width
        val y = rnd.nextFloat() * size.height
        val c = if (rnd.nextBoolean()) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.07f)
        drawCircle(c, radius = 1f + rnd.nextFloat() * 2.5f, center = Offset(x, y))
    }
    val spine = 28.dp.toPx()
    drawRect(Doodle.CoverDark, size = androidx.compose.ui.geometry.Size(spine, size.height))
    drawLine(Color.Black.copy(alpha = 0.25f), Offset(spine, 0f), Offset(spine, size.height), 3.dp.toPx())
    for (frac in floatArrayOf(0.25f, 0.75f)) {
        val y = size.height * frac
        drawLine(Color(0xFFCFD8DC), Offset(spine / 2, y - 22.dp.toPx()), Offset(spine / 2, y + 22.dp.toPx()), 3.dp.toPx())
    }
}
