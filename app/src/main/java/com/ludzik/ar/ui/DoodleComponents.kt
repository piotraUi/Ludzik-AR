package com.ludzik.ar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** Tło kartki w kratkę z czerwonym marginesem. */
fun Modifier.gridPaper(margin: Boolean = true): Modifier = this
    .background(Doodle.Paper)
    .drawBehind {
        val step = 22.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawLine(Doodle.GridBlue, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
            x += step
        }
        var y = 0f
        while (y < size.height) {
            drawLine(Doodle.GridBlue, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            y += step
        }
        if (margin) {
            val mx = step * 3
            drawLine(Doodle.MarginRed, Offset(mx, 0f), Offset(mx, size.height), 2.dp.toPx())
        }
    }

/** Krzywy prostokąt narysowany „ręcznie” — dwie kreski z innym ziarnem. */
fun DrawScope.wobblyRect(seed: Int, color: Color, strokeWidth: Float, fill: Color? = null, inset: Float = strokeWidth) {
    for (pass in 0..1) {
        val rnd = Random(seed * 13 + pass)
        val j = { (rnd.nextFloat() - 0.5f) * 2f * 2.2f * density }
        val l = inset
        val t = inset
        val r = size.width - inset
        val b = size.height - inset
        val path = Path().apply {
            moveTo(l + j(), t + j())
            quadraticTo((l + r) / 2 + j(), t + j() * 1.5f, r + j(), t + j())
            quadraticTo(r + j() * 1.5f, (t + b) / 2 + j(), r + j(), b + j())
            quadraticTo((l + r) / 2 + j(), b + j() * 1.5f, l + j(), b + j())
            quadraticTo(l + j() * 1.5f, (t + b) / 2 + j(), l + j() * 0.5f, t + j() * 0.5f)
        }
        if (pass == 0 && fill != null) drawPath(path, fill)
        drawPath(
            path,
            if (pass == 0) color else color.copy(alpha = 0.45f),
            style = Stroke(if (pass == 0) strokeWidth else strokeWidth * 0.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
fun DoodleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    seed: Int = text.hashCode(),
    fill: Color = Doodle.PaperLight,
    textColor: Color = Doodle.Ink,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier = modifier
            .scale(if (pressed) 0.94f else 1f)
            .rotate(((seed % 5) - 2) * 0.6f)
            .drawBehind { wobblyRect(seed, Doodle.Ink, 2.5.dp.toPx(), if (enabled) fill else fill.copy(alpha = 0.5f)) }
            .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) textColor else textColor.copy(alpha = 0.5f))
        }
    }
}

/** Żółta karteczka samoprzylepna z tekstem. */
@Composable
fun StickyNote(text: String, modifier: Modifier = Modifier, color: Color = Doodle.Highlighter, seed: Int = 7) {
    Box(
        modifier = modifier
            .rotate(((seed % 3) - 1) * 1.2f)
            .drawBehind { wobblyRect(seed, Doodle.Ink.copy(alpha = 0.7f), 1.5.dp.toPx(), color.copy(alpha = 0.95f)) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}
