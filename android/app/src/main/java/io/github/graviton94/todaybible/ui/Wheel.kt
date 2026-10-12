package io.github.graviton94.todaybible.ui

import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens

/**
 * 시각 휠 (알람 맞추듯): 오전/오후 · 시 · 분(5분 단위). 가운데 줄 위아래로 금빛 선 두 줄, 위아래 끝은 흐려짐.
 * minutes = 하루 중 분 (0..1439). 굴리면 곧바로 onChange.
 */
@Composable
fun TimeWheel(minutes: Int, korean: Boolean, onChange: (Int) -> Unit) {
    val c = Theme.c
    val h24 = minutes / 60; val m = (minutes % 60) / 5 * 5
    val pm = if (h24 >= 12) 1 else 0
    val h12 = (h24 % 12).let { if (it == 0) 12 else it }
    val row = Tokens.Size.wheelRow
    val cur = remember { intArrayOf(pm, h12 - 1, m / 5) }
    fun emit() { val h = (cur[1] + 1) % 12 + cur[0] * 12; onChange(h * 60 + cur[2] * 5) }
    Box(
        Modifier.fillMaxWidth().height(row * WHEEL_ROWS).drawWithContent {
            drawContent()
            val y1 = size.height / 2 - row.toPx() / 2; val y2 = y1 + row.toPx(); val w = Tokens.Stroke.hair.toPx() * 1.5f
            drawLine(c.gilt, Offset(0f, y1), Offset(size.width, y1), w)
            drawLine(c.gilt, Offset(0f, y2), Offset(size.width, y2), w)
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            val ampm = if (korean) listOf("오전", "오후") else listOf("AM", "PM")
            WheelColumn(ampm, pm, true) { cur[0] = it; emit() }
            WheelColumn((1..12).map { "$it" }, h12 - 1, false) { cur[1] = it; emit() }
            Text(":", style = Theme.big(Tokens.Text.wheel).copy(color = c.ink))
            WheelColumn((0 until 12).map { "%02d".format(it * 5) }, m / 5, false) { cur[2] = it; emit() }
        }
    }
}

private const val WHEEL_ROWS = 5

@Composable
private fun WheelColumn(items: List<String>, start: Int, word: Boolean, onPick: (Int) -> Unit) {
    val c = Theme.c; val row = Tokens.Size.wheelRow
    val state = rememberLazyListState(start)
    val half = with(androidx.compose.ui.platform.LocalDensity.current) { row.toPx() / 2 }
    val sel by remember { derivedStateOf { (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > half) 1 else 0).coerceIn(0, items.lastIndex) } }
    LaunchedEffect(state) { snapshotFlow { state.isScrollInProgress to sel }.collect { (moving, i) -> if (!moving) onPick(i) } }
    LazyColumn(
        state = state, flingBehavior = rememberSnapFlingBehavior(state),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = row * (WHEEL_ROWS / 2)),
        modifier = Modifier.width(Tokens.Size.wheelCol).height(row * WHEEL_ROWS)
            // 위아래 끝을 흐리게
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color.Black, 0.65f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(items.size) { i ->
            val on = i == sel
            Box(Modifier.height(row).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(items[i], maxLines = 1, style = (if (word) Theme.body() else Theme.big(Tokens.Text.wheel).copy(fontFeatureSettings = "lnum"))
                    .copy(color = if (on) c.ink else c.inkSoft, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal))
            }
        }
    }
}
