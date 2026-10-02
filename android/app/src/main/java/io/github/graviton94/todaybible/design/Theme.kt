package io.github.graviton94.todaybible.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import io.github.graviton94.todaybible.R

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

object Fonts {
    val serifKr = FontFamily(Font(R.font.serif_kr_medium, FontWeight.Medium), Font(R.font.serif_kr_bold, FontWeight.Bold))
    val titleKr = FontFamily(Font(R.font.title_kr))
    val garamond = FontFamily(Font(R.font.garamond_medium, FontWeight.Medium), Font(R.font.garamond_semibold, FontWeight.SemiBold), Font(R.font.garamond_italic, FontWeight.Medium, FontStyle.Italic))
    val fell = FontFamily(Font(R.font.fell_sc))
    val black = FontFamily(Font(R.font.blackletter))
}

val LocalPalette = staticCompositionLocalOf { Tokens.light }
val LocalScale = staticCompositionLocalOf { 1f }

object Theme {
    val c: Palette @Composable get() = LocalPalette.current
    val scale: Float @Composable get() = LocalScale.current
    private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    /** 말씀 본문 (번역에 따라 글꼴). 줄 간격 넉넉히. */
    @Composable fun verse(korean: Boolean): TextStyle = TextStyle(
        fontFamily = if (korean) Fonts.serifKr else Fonts.garamond, fontWeight = FontWeight.Medium,
        fontSize = (if (korean) Tokens.Text.verse else Tokens.Text.verseEn) * scale, lineHeight = 1.8.em, lineHeightStyle = trim, color = c.ink)
    @Composable fun body(): TextStyle = TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.body * scale, lineHeight = 1.6.em, color = c.ink)
    @Composable fun label(): TextStyle = TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Bold, fontSize = Tokens.Text.label * scale, lineHeight = 1.4.em, color = c.ink)
    @Composable fun small(): TextStyle = TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.small * scale, lineHeight = 1.45.em, color = c.inkSoft)
    @Composable fun title(korean: Boolean, size: TextUnit = Tokens.Text.title): TextStyle = TextStyle(fontFamily = if (korean) Fonts.titleKr else Fonts.garamond, fontSize = size * scale, lineHeight = 1.25.em, color = c.ink)
    /** 머리줄 · 장절 표기 (영문 소문자 대문자). 한글이면 명조 굵게. */
    @Composable fun head(korean: Boolean): TextStyle = if (korean) TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Bold, fontSize = Tokens.Text.small * scale, letterSpacing = 0.06.em, color = c.ink)
        else TextStyle(fontFamily = Fonts.fell, fontSize = Tokens.Text.label * scale, letterSpacing = 0.08.em, color = c.ink)
    @Composable fun number(): TextStyle = TextStyle(fontFamily = Fonts.black, fontSize = Tokens.Text.title * scale, color = c.rubric)
}

@Composable
fun TodayTheme(choice: ThemeChoice, scale: Float, content: @Composable () -> Unit) {
    val dark = when (choice) { ThemeChoice.SYSTEM -> isSystemInDarkTheme(); ThemeChoice.LIGHT -> false; ThemeChoice.DARK -> true }
    CompositionLocalProvider(LocalPalette provides if (dark) Tokens.dark else Tokens.light, LocalScale provides scale, content = content)
}
