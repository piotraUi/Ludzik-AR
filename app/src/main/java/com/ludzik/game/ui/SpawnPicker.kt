package com.ludzik.game.ui

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
import com.ludzik.game.characters.doodle.ObjectIcons
import com.ludzik.game.characters.doodle.SpriteAtlas
import com.ludzik.game.game.SpawnItem

/** Pasek wyboru: ludziki z zeszytu i realistyczne przedmioty (ikony rysowane ołówkiem). */
@Composable
fun SpawnPicker(selected: SpawnItem, onSelect: (SpawnItem) -> Unit, modifier: Modifier = Modifier) {
    val icons = remember {
        SpawnItem.all.associateWith {
            when (it) {
                is SpawnItem.Doodle -> SpriteAtlas.icon(it.kind, 128)
                is SpawnItem.Thing -> ObjectIcons.icon(it.spec.id, 128)
            }.asImageBitmap()
        }
    }
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SpawnItem.all.forEachIndexed { i, item ->
            val isSelected = item == selected
            val scale by animateFloatAsState(if (isSelected) 1.08f else 1f, label = "pickerScale")
            Column(
                Modifier
                    .scale(scale)
                    .rotate(((i % 3) - 1) * 2f)
                    .drawBehind {
                        wobblyRect(
                            seed = i + 20,
                            color = Doodle.Ink,
                            strokeWidth = (if (isSelected) 3f else 1.5f) * density,
                            fill = if (isSelected) Doodle.Highlighter else Doodle.PaperLight.copy(alpha = 0.9f),
                        )
                    }
                    .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(item) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(icons.getValue(item), contentDescription = item.label, modifier = Modifier.size(40.dp))
                Text(item.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
