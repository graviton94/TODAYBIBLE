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

/**
 * 나의 서가 (C1): 구약 39권 · 신약 27권 책등이 두 칸에. 쓴 만큼 책등 아래부터 금박이 차오르고,
 * 다 쓴 권은 가죽 표지에 금띠. 책등 높이는 장 수를 따라 조금씩 달라요. 누르면 그 권의 장 고르기.
 */
@Composable
fun BookShelf(s: AppState) {
    val c = Theme.c; val tr = s.translation
    val keys = s.fills.filter { it.translation == tr }.map { it.key.raw }.toSet()
    // 권마다 쓴 비율 (본문을 읽어야 해서 화면 줄 밖에서)
    val frac by produceState(FloatArray(66), keys.size, tr) {
        value = withContext(Dispatchers.IO) {
            val per = keys.groupingBy { io.github.graviton94.todaybible.core.VerseKey(it).book }.eachCount()
            FloatArray(66) { b -> per[b]?.let { n -> n / s.store.book(tr, b).fillableTotal.coerceAtLeast(1).toFloat() } ?: 0f }
        }
    }
    val done = frac.count { it >= 1f }; val going = frac.count { it > 0f && it < 1f }
    val summary = stringResource(R.string.shelf_line, done, going)
    Column(Modifier.fillMaxWidth().semantics { contentDescription = summary }, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Text(stringResource(R.string.shelf_title), style = Theme.title(s.korean, Tokens.Text.title), maxLines = 1)
        Text(summary, style = Theme.small(), maxLines = 2)
        Shelf(s, 0 until 39, frac, c)
        Shelf(s, 39 until 66, frac, c)
    }
}

@Composable
private fun Shelf(s: AppState, books: IntRange, frac: FloatArray, c: Palette) {
    val n = books.count()
    val maxCh = Canon.books.maxOf { it.chapters }
    Canvas(Modifier.fillMaxWidth().height(Tokens.Size.shelfRow).pointerInput(books) {
        detectTapGestures { p -> val i = (p.x / (size.width / OT_SLOTS.toFloat())).toInt(); if (i in 0 until n) { s.pickToRead = true; s.picker = books.first + i } }
    }) {
        // 칸 너비는 두 칸이 같게 (구약 39칸 기준), 신약은 왼쪽부터
        val slot = size.width / OT_SLOTS; val gap = Tokens.Size.spineGap.toPx(); val board = Tokens.Size.shelfBase.toPx()
        val floor = size.height - board
        drawRect(c.leather, Offset(0f, floor), Size(size.width, board))
        for ((i, b) in books.withIndex()) {
            val h = floor * (Tokens.Ratio.spineMin + (1f - Tokens.Ratio.spineMin) * kotlin.math.sqrt(Canon.books[b].chapters / maxCh.toFloat()))
            spine(Offset(i * slot + gap / 2, floor - h), Size(slot - gap, h), frac[b], c)
        }
    }
}

private const val OT_SLOTS = 39

private fun DrawScope.spine(at: Offset, size: Size, f: Float, c: Palette) {
    val r = CornerRadius(Tokens.Size.spineGap.toPx())
    if (f >= 1f) {
        // 다 쓴 권: 가죽 + 위아래 금띠
        drawRoundRect(c.leather, at, size, r)
        val band = Tokens.Size.spineBand.toPx(); val inset = size.height * 0.12f
        drawRect(c.gilt, Offset(at.x, at.y + inset), Size(size.width, band))
        drawRect(c.gilt, Offset(at.x, at.y + size.height - inset - band), Size(size.width, band))
    } else {
        drawRoundRect(c.paper, at, size, r)
        if (f > 0f) { val fh = size.height * f.coerceAtLeast(0.1f); drawRect(c.gilt, Offset(at.x, at.y + size.height - fh), Size(size.width, fh)) }
        drawRoundRect(c.hair, at, size, r, style = Stroke(Tokens.Stroke.hair.toPx()))
    }
}

/**
 * 옮겨 쓴 두루마리 (C2): 쓴 글자를 원고지 칸(1cm)으로 이어 붙인 길이. 처음 보일 때 두루마리가 펼쳐져요.
 * 카드로 보낼 수 있어요.
 */
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
                    Cards.share(s, ctx, Cards.year(ctx, k, ctx.getString(R.string.scroll_title), big, lines), "scroll")
                })
        }
        Text(stringResource(R.string.scroll_letters, "%,d".format(letters)) + (like?.let { " · " + stringResource(it) } ?: ""), style = Theme.small(), maxLines = 2)
    }
}
