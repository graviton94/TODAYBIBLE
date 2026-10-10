package io.github.graviton94.todaybible.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens

/** ‘눌러서 들어가기’ 같은 안내 한 줄: 천천히 숨 쉬듯 밝아졌다 흐려져요 (여는 화면 · 관문). */
@Composable
internal fun Breathing(text: String, color: Color, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "hint").animateFloat(0.85f, 0.35f, infiniteRepeatable(tween(Tokens.Motion.introBreatheMs / 2), RepeatMode.Reverse), label = "hint")
    Text(text, style = Theme.small().copy(color = color.copy(alpha = pulse), letterSpacing = 0.3.em, textAlign = TextAlign.Center), modifier = modifier)
}
