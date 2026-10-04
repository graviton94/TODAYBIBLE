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
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import io.github.graviton94.todaybible.R

/** 테마: 시스템 · 밝게 · 어둡게 · 촛불빛 (밤에 눈이 편한 따뜻한 어둠). */
enum class ThemeChoice { SYSTEM, LIGHT, DARK, CANDLE }

object Fonts {
    val serifKr = FontFamily(Font(R.font.serif_kr_medium, FontWeight.Medium), Font(R.font.serif_kr_bold, FontWeight.Bold))
    val titleKr = FontFamily(Font(R.font.title_kr))
    val garamond = FontFamily(Font(R.font.garamond_medium, FontWeight.Medium), Font(R.font.garamond_semibold, FontWeight.SemiBold), Font(R.font.garamond_italic, FontWeight.Medium, FontStyle.Italic))
    val fell = FontFamily(Font(R.font.fell_sc))
    val black = FontFamily(Font(R.font.blackletter))
    val pen = FontFamily(Font(R.font.pen))
}

val LocalPalette = staticCompositionLocalOf { Tokens.light }
val LocalScale = staticCompositionLocalOf { 1f }

object Theme {
    val c: Palette @Composable get() = LocalPalette.current
    val scale: Float @Composable get() = LocalScale.current
    private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    /** 한글이 낱말 가운데서 끊기지 않게 (어절 단위 줄바꿈). */
    val phrase = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)
    /** 말씀 본문 (번역에 따라 글꼴). 줄 간격 넉넉히. */
    @Composable fun verse(korean: Boolean): TextStyle = TextStyle(lineBreak = phrase, 
        fontFamily = if (korean) Fonts.serifKr else Fonts.garamond, fontWeight = FontWeight.Medium,
        fontSize = (if (korean) Tokens.Text.verse else Tokens.Text.verseEn) * scale, lineHeight = Tokens.Leading.verse.em, lineHeightStyle = trim, color = c.ink)
    /** 옮겨 쓰는 칸 (본문보다 한 단계 작게). */
    @Composable fun typed(korean: Boolean): TextStyle = verse(korean).copy(fontSize = (if (korean) Tokens.Text.typed else Tokens.Text.typedEn) * scale)
    @Composable fun body(): TextStyle = TextStyle(lineBreak = phrase, fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.body * scale, lineHeight = Tokens.Leading.body.em, color = c.ink)
    @Composable fun label(): TextStyle = TextStyle(lineBreak = phrase, fontFamily = Fonts.serifKr, fontWeight = FontWeight.Bold, fontSize = Tokens.Text.label * scale, lineHeight = Tokens.Leading.label.em, color = c.ink)
    @Composable fun small(): TextStyle = TextStyle(lineBreak = phrase, fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.small * scale, lineHeight = Tokens.Leading.small.em, color = c.inkSoft)
    @Composable fun title(korean: Boolean, size: TextUnit = Tokens.Text.title): TextStyle = TextStyle(lineBreak = phrase, fontFamily = if (korean) Fonts.titleKr else Fonts.garamond, fontSize = size * scale, lineHeight = Tokens.Leading.title.em, color = c.ink)
    /** 머리줄 · 장절 표기 (영문 소문자 대문자). 한글이면 명조 굵게. */
    @Composable fun head(korean: Boolean): TextStyle = if (korean) TextStyle(lineBreak = phrase, fontFamily = Fonts.serifKr, fontWeight = FontWeight.Bold, fontSize = Tokens.Text.small * scale, letterSpacing = Tokens.Tracking.head.em, color = c.ink)
        else TextStyle(lineBreak = phrase, fontFamily = Fonts.fell, fontSize = Tokens.Text.label * scale, letterSpacing = Tokens.Tracking.headEn.em, color = c.ink)
    /** 맨 위 앱 이름. */
    @Composable fun brand(korean: Boolean): TextStyle = title(korean, Tokens.Text.brand).copy(color = c.inkSoft)
    /** 장 마침 화면의 큰 붉은 장 번호. */
    @Composable fun initial(): TextStyle = number().copy(fontSize = Tokens.Text.initialLg * scale)
    @Composable fun number(): TextStyle = TextStyle(lineBreak = phrase, fontFamily = Fonts.black, fontSize = Tokens.Text.title * scale, color = c.rubric)
}

@Composable
fun TodayTheme(choice: ThemeChoice, scale: Float, night: Boolean = false, contrast: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (choice) { ThemeChoice.SYSTEM -> isSystemInDarkTheme(); ThemeChoice.LIGHT -> false; ThemeChoice.DARK, ThemeChoice.CANDLE -> true }
    CompositionLocalProvider(LocalPalette provides (if (night || choice == ThemeChoice.CANDLE) Tokens.candle else if (dark) Tokens.dark else Tokens.light).let { p -> if (contrast) sharper(p) else p }, LocalScale provides scale, content = content)
}

/** 또렷하게: 흐린 글자는 한 단계씩 진하게, 가는 줄은 더 보이게 (같은 재료 색에서 끌어옴). */
fun sharper(p: Palette): Palette = p.copy(inkSoft = p.ink, unwritten = p.inkSoft, hair = p.hair.copy(alpha = Tokens.Alpha.contrastHair), noteLine = p.noteLine.copy(alpha = Tokens.Alpha.contrastHair))
