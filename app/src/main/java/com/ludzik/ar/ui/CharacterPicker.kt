package com.ludzik.ar.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.ludzik.ar.characters.CharacterKind
import com.ludzik.ar.characters.CharacterRegistry
import com.ludzik.ar.characters.doodle.SpriteAtlas

/** Pasek wyboru postaci z ikonami narysowanymi tym samym kodem co sprite'y. */
@Composable
fun CharacterPicker(selected: CharacterKind, onSelect: (CharacterKind) -> Unit, modifier: Modifier = Modifier) {
    val icons = remember { CharacterRegistry.kinds.associateWith { SpriteAtlas.icon(it, 160).asImageBitmap() } }
    Row(
        modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CharacterRegistry.kinds.forEachIndexed { i, kind ->
            val isSelected = kind == selected
            val scale by animateFloatAsState(if (isSelected) 1.1f else 1f, label = "pickerScale")
            Column(
                Modifier
                    .scale(scale)
                    .rotate(((i % 3) - 1) * 2f)
                    .drawBehind {
                        wobblyRect(
                            seed = i + 20,
                            color = Doodle.Ink,
                            strokeWidth = (if (isSelected) 3 else 1.5f).let { it.toFloat() } * density,
                            fill = if (isSelected) Doodle.Highlighter else Doodle.PaperLight.copy(alpha = 0.92f),
                        )
                    }
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(kind) }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(icons.getValue(kind), contentDescription = kind.displayName, modifier = Modifier.size(50.dp))
                Text(kind.displayName, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
