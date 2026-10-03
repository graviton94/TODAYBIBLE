package io.github.graviton94.todaybible.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.core.Mode
import io.github.graviton94.todaybible.data.Ink
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import kotlin.math.max
import kotlin.math.min

/**
 * 손으로 쓰기: 위에 오늘 쓸 절, 아래 줄 공책에 손가락이나 펜으로 한 절씩.
 * 펜을 쓰면 누르는 힘으로, 손가락이면 빠르기로 굵기가 달라져요. 펜이 닿은 뒤로는 손바닥 닿음은 무시.
 * 칸이 모자라면 ‘한 장 더’. ‘다 썼어요’ 하면 획으로 저장하고 그 절이 채워져요.
 */
@Composable
fun HandTab(s: AppState, verse: Int) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current; val haptic = LocalHapticFeedback.current
    val key = listOf(s.translation, s.book, s.chapter, verse)
    val strokes = remember(key) { mutableStateListOf<Ink.Stroke>() }
    val earlier = remember(key) { mutableStateListOf<List<Ink.Stroke>>() }
    var live by remember(key) { mutableStateOf<FloatArray?>(null) }
    var stylus by remember { mutableStateOf(false) }
    var padW by remember { mutableIntStateOf(1) }
    Column(Modifier.fillMaxSize().padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        // 오늘 쓸 절 (길면 이 칸 안에서 넘겨 봐요)
        Box(Modifier.fillMaxWidth().heightIn(max = Tokens.Size.handModel).verticalScroll(rememberScrollState())) {
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = c.rubric)) { append("$verse ") }; append(Markup.plain(s.text().verse(s.chapter, verse)))
            }, style = Theme.verse(k))
        }
        // 줄 공책
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.card)).background(c.paper).onSizeChanged { padW = it.width.coerceAtLeast(1) }) {
            val line = Tokens.Size.handLine
            Canvas(Modifier.fillMaxSize()) {
                val lh = line.toPx(); var y = lh
                while (y < size.height) { drawLine(c.noteLine, Offset(0f, y), Offset(size.width, y), Tokens.Stroke.hair.toPx()); y += lh }
            }
            if (strokes.isEmpty() && live == null) Text(stringResource(if (earlier.isEmpty()) R.string.hand_hint else R.string.hand_sheet, earlier.size + 1),
                style = Theme.small().copy(color = c.unwritten), modifier = Modifier.align(Alignment.Center))
            // 다 그은 획 (바뀔 때만 다시 그림)
            Canvas(Modifier.fillMaxSize()) { strokes.forEach { drawStroke(it, size.width, c.penInk) } }
            // 긋고 있는 획
            Canvas(Modifier.fillMaxSize().pointerInput(key) {
                val base = Tokens.Stroke.pen.toPx(); val vRef = 3f * density / 2.6f
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pen = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                    if (pen) stylus = true
                    if (stylus && !pen) return@awaitEachGesture
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val pts = ArrayList<Float>(600)
                    var last = down.position; var lastT = down.uptimeMillis; var width = base
                    fun add(p: Offset, t: Long, pressure: Float) {
                        val d = (p - last).getDistance()
                        if (pts.isNotEmpty() && d < 1.2f) return
                        val v = d / max(1L, t - lastT)
                        val target = if (pen) base * (0.45f + pressure.coerceIn(0f, 1f) * 0.9f) else base * (1.25f - min(v / vRef, 0.55f))
                        width += (target - width) * 0.35f
                        pts.add(p.x / w); pts.add(p.y.coerceAtLeast(0f) / w); pts.add(width / w)
                        last = p; lastT = t
                    }
                    add(down.position, down.uptimeMillis, down.pressure); down.consume(); live = pts.toFloatArray()
                    while (true) {
                        val ev = awaitPointerEvent()
                        val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                        ch.historical.forEach { add(it.position, it.uptimeMillis, ch.pressure) }
                        add(ch.position, ch.uptimeMillis, ch.pressure)
                        ch.consume(); live = pts.toFloatArray()
                        if (!ch.pressed) break
                    }
                    strokes.add(Ink.Stroke(pts.toFloatArray())); live = null
                }
            }) { live?.let { drawStroke(Ink.Stroke(it), size.width, c.penInk) } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            BookButton(stringResource(R.string.hand_undo), Modifier.weight(1f), quiet = true, enabled = strokes.isNotEmpty()) { strokes.removeAt(strokes.lastIndex) }
            BookButton(stringResource(R.string.hand_more), Modifier.weight(1f), quiet = true, enabled = strokes.isNotEmpty()) { earlier.add(strokes.toList()); strokes.clear() }
            BookButton(stringResource(R.string.hand_done), Modifier.weight(1f), enabled = strokes.isNotEmpty() || earlier.isNotEmpty()) {
                val sheets = (earlier + listOf(strokes.toList())).filter { it.isNotEmpty() }
                Ink.save(Ink.file(ctx, s.translation.id, s.book, s.chapter, verse), Ink.Page(sheets, Tokens.Size.handLine.value * ctx.resources.displayMetrics.density / padW))
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                s.fill(listOf(verse), Mode.PAPER)
            }
        }
    }
}

/** 획 하나 (너비 = 1 기준 좌표) 를 너비 w 에 맞춰 그려요. */
fun DrawScope.drawStroke(s: Ink.Stroke, w: Float, color: Color) {
    val p = s.pts
    if (s.size == 1) { drawCircle(color, p[2] * w / 2, Offset(p[0] * w, p[1] * w)); return }
    for (j in 1 until s.size) {
        drawLine(color, Offset(p[j * 3 - 3] * w, p[j * 3 - 2] * w), Offset(p[j * 3] * w, p[j * 3 + 1] * w), (p[j * 3 - 1] + p[j * 3 + 2]) / 2 * w, StrokeCap.Round)
    }
}

/** 공책 보기 · 손으로 쓴 한 절: 쓰던 줄 간격 그대로 줄을 긋고 획을 얹어요. */
@Composable
fun InkSheets(page: Ink.Page, color: Color, lineColor: Color) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wDp = maxWidth
        Column {
            page.sheets.forEachIndexed { i, sheet ->
                val lines = kotlin.math.ceil((page.height(i) + page.line * 0.25f) / page.line).toInt().coerceAtLeast(1)
                Canvas(Modifier.fillMaxWidth().height(wDp * (lines * page.line))) {
                    val w = size.width; val lh = page.line * w
                    for (l in 1..lines) drawLine(lineColor, Offset(0f, l * lh), Offset(w, l * lh), Tokens.Stroke.hair.toPx())
                    sheet.forEach { drawStroke(it, w, color) }
                }
            }
        }
    }
}
