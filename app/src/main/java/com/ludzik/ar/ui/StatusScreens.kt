package com.ludzik.ar.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ludzik.ar.characters.CharacterKind
import com.ludzik.ar.characters.Ludzik
import com.ludzik.ar.characters.Pajak
import com.ludzik.ar.characters.doodle.SpriteAtlas

@Composable
private fun PaperMessage(
    kind: CharacterKind,
    title: String,
    body: String,
    primary: Pair<String, () -> Unit>?,
    secondary: Pair<String, () -> Unit>?,
) {
    val icon = remember(kind) { SpriteAtlas.icon(kind, 256).asImageBitmap() }
    Box(
        Modifier
            .fillMaxSize()
            .gridPaper(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(start = 84.dp, end = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Image(icon, contentDescription = null, modifier = Modifier.size(120.dp).rotate(-6f))
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Start)
            Spacer(Modifier.height(28.dp))
            primary?.let { (label, action) -> DoodleButton(label, action, fill = Doodle.Highlighter) }
            secondary?.let { (label, action) ->
                Spacer(Modifier.height(14.dp))
                DoodleButton(label, action)
            }
        }
    }
}

@Composable
fun PermissionScreen(permanentlyDenied: Boolean, onRequest: () -> Unit, onOpenSettings: () -> Unit, onBack: () -> Unit) {
    PaperMessage(
        kind = Ludzik,
        title = "Ludzik potrzebuje kamery",
        body = if (permanentlyDenied) {
            "Bez dostępu do aparatu ludziki nie zobaczą Twojego pokoju. " +
                "Dostęp jest wyłączony na stałe — włącz go w ustawieniach aplikacji (Uprawnienia → Aparat)."
        } else {
            "Żeby ludziki mogły biegać po Twojej podłodze, aplikacja musi widzieć obraz z aparatu. " +
                "Nic nie jest wysyłane do internetu."
        },
        primary = if (permanentlyDenied) "Otwórz ustawienia" to onOpenSettings else "Pozwól na kamerę" to onRequest,
        secondary = "Wróć" to onBack,
    )
}

@Composable
fun UnsupportedScreen(message: String, onBack: () -> Unit) {
    PaperMessage(
        kind = Pajak,
        title = "Ups, brak ARCore",
        body = message,
        primary = null,
        secondary = "Wróć do zeszytu" to onBack,
    )
}

@Composable
fun CheckingScreen() {
    PaperMessage(
        kind = Ludzik,
        title = "Ostrzę ołówki…",
        body = "Sprawdzam, czy Twój telefon obsługuje ARCore.",
        primary = null,
        secondary = null,
    )
}
