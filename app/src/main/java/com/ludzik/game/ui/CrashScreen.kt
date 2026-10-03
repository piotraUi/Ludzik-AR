package com.ludzik.game.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Raport z poprzedniej awarii — do zrzutu ekranu albo skopiowania. */
@Composable
fun CrashScreen(report: String, offerSafeMode: Boolean, onSafeMode: () -> Unit, onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxSize()
            .gridPaper(margin = false)
            .systemBarsPadding()
            .padding(16.dp),
    ) {
        Text("Ups! Ludzik się potknął", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Poprzednie uruchomienie zakończyło się błędem. Zrób zrzut ekranu tej strony i wyślij go — to wystarczy, żeby to naprawić.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            report,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = Doodle.Ink,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Doodle.PaperLight.copy(alpha = 0.9f))
                .verticalScroll(rememberScrollState())
                .horizontalScroll(rememberScrollState())
                .padding(8.dp),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DoodleButton("Kopiuj", { clipboard.setText(AnnotatedString(report)) }, seed = 1)
            if (offerSafeMode) DoodleButton("Graj w trybie bezpiecznym", onSafeMode, seed = 2, fill = Doodle.Highlighter)
            DoodleButton("Zamknij", onClose, seed = 3)
        }
    }
}
