package io.github.graviton94.todaybible.ui

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

@Composable
fun ScrollLength(s: AppState, letters: Int) {
    if (letters <= 0) return
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    val meters = letters * Tokens.Px.scrollCellCm / 100f
    val big = if (meters >= 1000f) stringResource(R.string.scroll_km, "%.1f".format(meters / 1000f)) else stringResource(R.string.scroll_m, if (meters >= 100f) "%,d".format(meters.toInt()) else "%.1f".format(meters))
    val like = when { meters >= 1000f -> R.string.scroll_like_km; meters >= 105f -> R.string.scroll_like_field; meters >= 12f -> R.string.scroll_like_bus; else -> null }
    val open = remember { Animatable(0f) }
    LaunchedEffect(Unit) { open.animateTo(1f, tween(Tokens.Motion.unrollMs, easing = FastOutSlowInEasing)) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Text(stringResource(R.string.scroll_title), style = Theme.small().copy(color = c.rubric), maxLines = 1)
        Canvas(Modifier.fillMaxWidth().height(Tokens.Size.scroll)) {
            val roll = Tokens.Size.scrollRoll.toPx(); val w = (size.width - 2 * roll) * open.value
            val y = size.height * 0.18f; val h = size.height * 0.64f
            // 펼쳐진 종이 + 원고지 칸 + 위아래 금선
            drawRect(c.paper, Offset(roll, y), Size(w, h))
            val cell = h; var x = roll + cell
            while (x < roll + w) { drawLine(c.hair, Offset(x, y), Offset(x, y + h), Tokens.Stroke.hair.toPx()); x += cell }
            drawLine(c.gilt, Offset(roll, y), Offset(roll + w, y), Tokens.Stroke.giltFine.toPx())
            drawLine(c.gilt, Offset(roll, y + h), Offset(roll + w, y + h), Tokens.Stroke.giltFine.toPx())
            // 양 끝 두루마리 축
            val cr = CornerRadius(roll / 2)
            drawRoundRect(c.leather, Offset(0f, 0f), Size(roll, size.height), cr)
            drawRoundRect(c.leather, Offset(roll + w, 0f), Size(roll, size.height), cr)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(big, style = Theme.title(k, Tokens.Text.title).copy(color = c.ink), maxLines = 1, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.year_share), style = Theme.small().copy(color = c.rubric), maxLines = 1,
                modifier = Modifier.heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Button) {
                    val lines = listOfNotNull(ctx.getString(R.string.scroll_letters, "%,d".format(letters)), like?.let { ctx.getString(it) })
                    Cards.share(s, ctx, Cards.year(ctx, k, ctx.getString(R.string.scroll_title), big, lines, s.coverPlateId()), "scroll")
                })
        }
        Text(stringResource(R.string.scroll_letters, "%,d".format(letters)) + (like?.let { " · " + stringResource(it) } ?: ""), style = Theme.small(), maxLines = 2)
    }
}
