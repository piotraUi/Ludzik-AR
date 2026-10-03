package com.ludzik.game.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import com.ludzik.game.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Doodle {
    val Paper = Color(0xFFFBF6E9)
    val PaperLight = Color(0xFFFFFDF6)
    val Ink = Color(0xFF2B2B33)
    val GridBlue = Color(0xFFB9D3EE)
    val MarginRed = Color(0xFFE57373)
    val Cover = Color(0xFF2D4A7A)
    val CoverDark = Color(0xFF1F3559)
    val Highlighter = Color(0xFFFFF176)
    val RecordRed = Color(0xFFD84343)

    /**
     * Odręczna czcionka Caveat (SIL OFL) dołączona do aplikacji — systemowa „cursive”
     * nie ma polskich znaków (ą, ę, ś, ż…).
     */
    @OptIn(ExperimentalTextApi::class)
    val Hand = FontFamily(
        Font(R.font.caveat, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.caveat, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )
    val Casual = FontFamily.SansSerif
}

private val typography = Typography(
    displayLarge = TextStyle(fontFamily = Doodle.Hand, fontWeight = FontWeight.Bold, fontSize = 64.sp, color = Doodle.Ink),
    headlineMedium = TextStyle(fontFamily = Doodle.Hand, fontWeight = FontWeight.Bold, fontSize = 34.sp, color = Doodle.Ink),
    titleMedium = TextStyle(fontFamily = Doodle.Hand, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = Doodle.Ink),
    bodyLarge = TextStyle(fontFamily = Doodle.Casual, fontSize = 17.sp, color = Doodle.Ink, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Doodle.Casual, fontSize = 15.sp, color = Doodle.Ink),
    labelLarge = TextStyle(fontFamily = Doodle.Hand, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Doodle.Ink),
    labelSmall = TextStyle(fontFamily = Doodle.Hand, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Doodle.Ink),
)

@Composable
fun LudzikTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Doodle.Cover,
            background = Doodle.Paper,
            surface = Doodle.PaperLight,
            onSurface = Doodle.Ink,
            onBackground = Doodle.Ink,
        ),
        typography = typography,
        content = content,
    )
}
