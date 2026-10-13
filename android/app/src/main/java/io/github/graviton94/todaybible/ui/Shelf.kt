package io.github.graviton94.todaybible.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Canon
import io.github.graviton94.todaybible.design.Palette
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val ROT_NAMES = mapOf("bus" to R.string.rot_bus, "field" to R.string.rot_field, "eiffel" to R.string.rot_eiffel, "lotte" to R.string.rot_lotte,
    "km1" to R.string.rot_km1, "km5" to R.string.rot_km5, "km10" to R.string.rot_km10)

/** 옮겨 쓴 두루마리 (1.4): 길이를 버스 · 축구장 · 에펠탑 … 표지에 견줘 보여 줘요. */
@Composable
fun ScrollLength(s: AppState, letters: Int) {
    if (letters <= 0) return
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    val meters = letters * Tokens.Px.scrollCellCm / 100f
    val big = if (meters >= 1000f) stringResource(R.string.scroll_km, "%.1f".format(meters / 1000f)) else stringResource(R.string.scroll_m, if (meters >= 100f) "%,d".format(meters.toInt()) else "%.1f".format(meters))
    val R_ = io.github.graviton94.todaybible.core.Rotulus
    val (prev, next) = R_.around(meters)
    val end = R_.scaleEnd(meters)
    fun label(m: io.github.graviton94.todaybible.core.Rotulus.Mark) = ctx.getString(ROT_NAMES.getValue(m.id))
    val open = remember { Animatable(0f) }
    LaunchedEffect(Unit) { open.animateTo(1f, tween(Tokens.Motion.unrollMs, easing = FastOutSlowInEasing)) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Text("ROTULUS", style = Theme.caps().copy(color = c.ink), maxLines = 1)
            Text(stringResource(R.string.scroll_title), style = Theme.small(), maxLines = 1, modifier = Modifier.weight(1f))
            Text(big, style = Theme.title(k, Tokens.Text.title).copy(color = c.ink), maxLines = 1)
        }
        // 두루마리: 양 끝 축 · 쓴 만큼 금빛으로 펼쳐진 원고지 · 지난 표지와 다음 표지 · 지금 자리 ▲
        BoxWithConstraints(Modifier.fillMaxWidth().height(Tokens.Size.scrollTrack)) {
            val w = maxWidth
            val roll = Tokens.Size.scrollRoll
            fun x(mm: Float) = roll + (w - roll * 2) * (mm / end).coerceIn(0f, 1f)
            Canvas(Modifier.fillMaxSize()) {
                val r = roll.toPx(); val y = size.height * 0.36f; val h = size.height * 0.3f
                val inner = size.width - 2 * r
                drawRect(c.paper, Offset(r, y), Size(inner, h))
                val cell = h; var cx = r + cell
                while (cx < r + inner) { drawLine(c.hair, Offset(cx, y), Offset(cx, y + h), Tokens.Stroke.hair.toPx()); cx += cell }
                val fw = inner * (meters / end).coerceIn(0f, 1f) * open.value
                drawRect(c.gilt.copy(alpha = Tokens.Alpha.spineFill), Offset(r, y), Size(fw, h))
                drawLine(c.gilt, Offset(r, y), Offset(r + fw, y), Tokens.Stroke.giltFine.toPx()); drawLine(c.gilt, Offset(r, y + h), Offset(r + fw, y + h), Tokens.Stroke.giltFine.toPx())
                val cr = CornerRadius(r / 2)
                drawRoundRect(c.leather, Offset(0f, y - h * 0.4f), Size(r, h * 1.8f), cr)
                drawRoundRect(c.leather, Offset(size.width - r, y - h * 0.5f), Size(r, h * 2f), cr)
                // 표지 눈금
                listOfNotNull(prev, next).forEach { m -> val mx = r + inner * (m.meters / end); drawLine(if (m == prev) c.giltText else c.unwritten, Offset(mx, y - h * 0.3f), Offset(mx, y + h), Tokens.Stroke.rule.toPx()) }
            }
            val dens = LocalDensity.current
            listOfNotNull(prev, next).forEach { m ->
                val passed = m == prev
                Text(label(m), style = Theme.small().copy(fontSize = Tokens.Text.scrollMark, color = if (passed) c.giltText else c.unwritten), maxLines = 1, softWrap = false,
                    modifier = Modifier.align(Alignment.TopStart).offset { androidx.compose.ui.unit.IntOffset(with(dens) { (x(m.meters.toFloat()) - Tokens.Space.s4).roundToPx() }.coerceAtMost(with(dens) { (w - Tokens.Size.scrollLabel).roundToPx() }).coerceAtLeast(0), 0) })
            }
            Text("▲ " + stringResource(R.string.rot_now), style = Theme.small().copy(fontSize = Tokens.Text.scrollMark, color = c.ink, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1, softWrap = false,
                modifier = Modifier.align(Alignment.BottomStart).offset { androidx.compose.ui.unit.IntOffset(with(dens) { (x(meters) - Tokens.Space.s2).roundToPx() }.coerceAtMost(with(dens) { (w - Tokens.Size.scrollLabel).roundToPx() }).coerceAtLeast(0), 0) })
        }
        val line = when {
            prev != null && next != null -> stringResource(R.string.rot_line, label(prev), label(next), stringResource(R.string.scroll_m, "%,d".format((next.meters - meters).toInt().coerceAtLeast(1))))
            next != null -> stringResource(R.string.rot_first, label(next), stringResource(R.string.scroll_m, "%,d".format((next.meters - meters).toInt().coerceAtLeast(1))))
            else -> stringResource(R.string.rot_past, label(prev!!))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(line, style = Theme.small(), maxLines = 2, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.year_share), style = Theme.small().copy(color = c.rubric), maxLines = 1,
                modifier = Modifier.heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Button) {
                    Cards.share(s, ctx, Cards.year(ctx, k, ctx.getString(R.string.scroll_title), big, listOf(ctx.getString(R.string.scroll_letters, "%,d".format(letters)), line), s.coverPlateId()), "scroll")
                }.padding(start = Tokens.Space.s2))
        }
    }
}
