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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import io.github.graviton94.todaybible.design.Fonts
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.core.Mode
import io.github.graviton94.todaybible.data.Ink
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import kotlin.math.max
import kotlin.math.min

/**
 * 손으로 쓰기: 줄 공책에 손가락이나 펜으로 한 절씩.
 * 밑글씨(M2): 줄마다 그 절을 큰 글씨로 흐리게 깔아 두고 따라 써요. 칸을 넘치면 ‘한 장 더’로 다음 줄들.
 * 펜(M1): 만년필(기본) · 붓펜 · 연필(평생권). 펜을 쓰면 누르는 힘으로, 손가락이면 빠르기로 굵기가 달라져요.
 * 쓰는 감각(Q1): 긋는 빠르기만큼 사각사각 · 손끝에 아주 약한 종이 결. 펜이 닿은 뒤로는 손바닥 닿음은 무시.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HandTab(s: AppState, verse: Int) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current; val haptic = LocalHapticFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
    val key = listOf(s.translation, s.book, s.chapter, verse)
    val strokes = remember(key) { mutableStateListOf<Ink.Stroke>() }
    val earlier = remember(key) { mutableStateListOf<List<Ink.Stroke>>() }
    var live by remember(key) { mutableStateOf<FloatArray?>(null) }
    var stylus by remember { mutableStateOf(false) }
    var padW by remember { mutableIntStateOf(1) }
    var padH by remember { mutableIntStateOf(1) }
    val feel = remember { io.github.graviton94.todaybible.data.PenFeel(ctx.applicationContext) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { feel.release() } }
    val full = Markup.plain(s.text().verse(s.chapter, verse))
    // 마음에 닿은 구절만 (X1): 숨 쉴 자리로 나눈 구절 가운데 고른 것만 써요 (처음엔 첫 구절). 고른 구절들은 이어진 한 덩이.
    val parts = remember(full) { io.github.graviton94.todaybible.core.Recite.phrases(full) }
    var pick by remember(key) { mutableStateOf(0..0) }
    val plain = if (s.handPhrase && parts.isNotEmpty()) full.substring(parts[pick.first.coerceIn(parts.indices)].first, parts[pick.last.coerceIn(parts.indices)].last + 1) else full
    val ink = if (s.pen == Ink.PENCIL) c.graphite else c.penInk
    val measurer = rememberTextMeasurer()
    val dens = LocalDensity.current
    val lineH = Tokens.Size.handLine
    val inset = Tokens.Space.s3
    // 밑글씨: 쓰는 칸 너비에 맞춰 줄을 나누고, 한 장에 들어가는 줄 수만큼씩
    val guide = remember(plain, padW, k) {
        if (padW <= 1) null else with(dens) {
            measurer.measure(plain, TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.guide, lineHeight = lineH.toSp(),
                lineBreak = Theme.phrase, lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Bottom, LineHeightStyle.Trim.None)),
                constraints = Constraints(maxWidth = (padW - inset.roundToPx() * 2).coerceAtLeast(1)))
        }
    }
    val perSheet = with(dens) { (padH / lineH.toPx()).toInt().coerceAtLeast(1) }
    val sheetsNeeded = guide?.let { (it.lineCount + perSheet - 1) / perSheet } ?: 1
    val sheet = earlier.size
    Column(Modifier.fillMaxSize().padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        // 펜 고르기 · 밑글씨
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            listOf(Ink.FOUNTAIN to R.string.pen_fountain, Ink.BRUSH to R.string.pen_brush, Ink.PENCIL to R.string.pen_pencil).forEach { (id, name) ->
                val on = s.pen == id
                Row(Modifier.heightIn(min = Tokens.Size.tab).clip(RoundedCornerShape(Tokens.Radius.chip)).background(if (on) c.paper else c.leaf)
                    .drawBehind { if (on) drawRect(c.rubric, Offset(0f, size.height - Tokens.Stroke.rule.toPx()), androidx.compose.ui.geometry.Size(size.width, Tokens.Stroke.rule.toPx())) }
                    .clickable(role = Role.RadioButton) { s.choosePen(id) }.padding(horizontal = Tokens.Space.s3),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                    Text(stringResource(name), style = Theme.small().copy(color = if (on) c.ink else c.inkSoft), maxLines = 1)
                    if (id != Ink.FOUNTAIN && s.gated()) LockMark(c.inkSoft, Modifier.size(Tokens.Size.lock))
                }
            }
            Box(Modifier.weight(1f))
            Text(stringResource(if (s.handGuide) R.string.guide_on else R.string.guide_off), style = Theme.small().copy(color = if (s.handGuide) c.rubric else c.inkSoft), maxLines = 1,
                modifier = Modifier.heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Switch) { s.flipGuide() })
        }
        // 밑글씨가 없으면 위에 본문, 있으면 절 번호만
        // 구절만 · 절 전체
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            listOf(true to R.string.hand_phrase, false to R.string.hand_whole).forEach { (v, id) ->
                Text(stringResource(id), style = Theme.small().copy(color = if (s.handPhrase == v) c.rubric else c.inkSoft), maxLines = 1,
                    modifier = Modifier.heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.RadioButton) { if (s.handPhrase != v) s.flipHandPhrase() })
            }
        }
        if (s.handPhrase && parts.size > 1 && strokes.isEmpty() && earlier.isEmpty()) {
            // 구절 고르기: 누르면 그 구절, 이어서 다른 구절을 누르면 그 사이까지
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = c.rubric)) { append("$verse ") }
                parts.forEachIndexed { i, r ->
                    withStyle(SpanStyle(color = if (i in pick) c.ink else c.unwritten, background = if (i in pick) c.mark else androidx.compose.ui.graphics.Color.Unspecified)) { append(full.substring(r.first, r.last + 1)) }
                    if (i < parts.lastIndex) append(" ")
                }
            }, style = Theme.verse(k), modifier = Modifier.fillMaxWidth().heightIn(max = Tokens.Size.handModel).verticalScroll(rememberScrollState()))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                parts.forEachIndexed { i, r ->
                    val on = i in pick
                    Text(full.substring(r.first, r.last + 1), style = Theme.small().copy(color = if (on) c.leatherInk else c.ink), maxLines = 1,
                        modifier = Modifier.heightIn(min = Tokens.Size.tab).clip(RoundedCornerShape(Tokens.Radius.chip)).background(if (on) c.leather else c.paper)
                            .clickable(role = Role.Button) { pick = if (i in pick && pick.first == pick.last) i..i else if (i < pick.first) i..pick.last else if (i > pick.last) pick.first..i else i..i }
                            .padding(horizontal = Tokens.Space.s3).wrapContentHeight())
                }
            }
        }
        else if (s.handGuide) Text(stringResource(R.string.hand_where, verse, sheet + 1, maxOf(sheetsNeeded, sheet + 1)), style = Theme.small().copy(color = c.rubric), maxLines = 1)
        else Box(Modifier.fillMaxWidth().heightIn(max = Tokens.Size.handModel).verticalScroll(rememberScrollState())) {
            Text(buildAnnotatedString { withStyle(SpanStyle(color = c.rubric)) { append("$verse ") }; append(plain) }, style = Theme.verse(k))
        }
        // 줄 공책
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.card)).background(c.paper)
            .onSizeChanged { padW = it.width.coerceAtLeast(1); padH = it.height.coerceAtLeast(1) }) {
            Canvas(Modifier.fillMaxSize()) {
                val lh = lineH.toPx(); var y = lh
                while (y < size.height) { drawLine(c.noteLine, Offset(0f, y), Offset(size.width, y), Tokens.Stroke.hair.toPx()); y += lh }
                // 밑글씨: 이 장에 들어갈 줄들만
                if (s.handGuide && guide != null) clipRect(0f, 0f, size.width, perSheet * lh) {
                    drawText(guide, color = c.ink.copy(alpha = Tokens.Alpha.guide), topLeft = Offset(inset.toPx(), -sheet * perSheet * lh))
                }
            }
            if (!s.handGuide && strokes.isEmpty() && live == null) Text(stringResource(if (earlier.isEmpty()) R.string.hand_hint else R.string.hand_sheet, earlier.size + 1),
                style = Theme.body().copy(fontSize = Tokens.Text.hint * s.scale, color = c.unwritten, textAlign = TextAlign.Center),
                modifier = Modifier.align(Alignment.Center).padding(Tokens.Space.s5))
            // 다 그은 획 (바뀔 때만 다시 그림)
            Canvas(Modifier.fillMaxSize()) { strokes.forEach { drawStroke(it, size.width, ink, s.pen) } }
            // 긋고 있는 획
            Canvas(Modifier.fillMaxSize().pointerInput(key, s.pen, s.penSound, s.paperHaptic) {
                val base = Tokens.Stroke.pen.toPx(); val vRef = 3f * density / 2.6f; val tick = Tokens.Size.grain.toPx()
                val brush = s.pen == Ink.BRUSH; val pencil = s.pen == Ink.PENCIL
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val pen = down.type == PointerType.Stylus || down.type == PointerType.Eraser
                    if (pen) stylus = true
                    if (stylus && !pen) return@awaitEachGesture
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val pts = ArrayList<Float>(600)
                    var last = down.position; var lastT = down.uptimeMillis; var width = base; var travel = 0f
                    fun add(p: Offset, t: Long, pressure: Float) {
                        val d = (p - last).getDistance()
                        if (pts.isNotEmpty() && d < 1.2f) return
                        val v = d / max(1L, t - lastT)
                        val target = when {
                            pencil -> base * 0.8f * (0.95f + (if (pen) pressure.coerceIn(0f, 1f) * 0.2f else 0.05f))
                            brush -> if (pen) base * (0.35f + pressure.coerceIn(0f, 1f) * 2.1f) else base * max(0.5f, 2.1f - v / vRef * 1.2f)
                            pen -> base * (0.45f + pressure.coerceIn(0f, 1f) * 0.9f)
                            else -> base * (1.25f - min(v / vRef, 0.55f))
                        }
                        // 붓펜은 첫 획이 가늘게 들어가요
                        val n = pts.size / 3
                        width += (target - width) * (if (brush) 0.22f else 0.35f)
                        val drawn = if (brush && n < 5) width * (0.35f + n * 0.13f) else width
                        pts.add(p.x / w); pts.add(p.y.coerceAtLeast(0f) / w); pts.add(drawn / w)
                        travel += d
                        if (s.paperHaptic && travel > tick) { travel = 0f; view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK) }
                        if (s.penSound) feel.speed(v / (vRef * 1.6f))
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
                    // 붓펜은 끝도 가늘게 빠져요
                    if (brush && pts.size >= 9) { val m = pts.size / 3; for (j in 1..minOf(3, m - 1)) pts[(m - j) * 3 + 2] *= 0.4f + 0.2f * (j - 1) }
                    feel.speed(0f)
                    strokes.add(Ink.Stroke(pts.toFloatArray())); live = null
                }
            }) { live?.let { drawStroke(Ink.Stroke(it), size.width, ink, s.pen) } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            BookButton(stringResource(R.string.hand_undo), Modifier.weight(1f), quiet = true, enabled = strokes.isNotEmpty()) { strokes.removeAt(strokes.lastIndex) }
            BookButton(stringResource(if (s.handGuide && sheet + 1 < sheetsNeeded) R.string.hand_next else R.string.hand_more), Modifier.weight(1f), quiet = true, enabled = strokes.isNotEmpty()) { earlier.add(strokes.toList()); strokes.clear() }
            BookButton(stringResource(R.string.hand_done), Modifier.weight(1f), enabled = strokes.isNotEmpty() || earlier.isNotEmpty()) {
                val sheets = (earlier + listOf(strokes.toList())).filter { it.isNotEmpty() }
                Ink.save(Ink.file(ctx, s.translation.id, s.book, s.chapter, verse), Ink.Page(sheets, lineH.value * ctx.resources.displayMetrics.density / padW, s.pen))
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                s.fill(listOf(verse), Mode.PAPER)
            }
        }
    }
}

/** 획 하나 (너비 = 1 기준 좌표) 를 너비 w 에 맞춰 그려요. */
fun DrawScope.drawStroke(s: Ink.Stroke, w: Float, color: Color, pen: Int = Ink.FOUNTAIN) {
    val p = s.pts
    if (s.size == 1) { drawCircle(color, p[2] * w / 2, Offset(p[0] * w, p[1] * w)); return }
    // 만년필: 획이 시작하는 곳에 잉크가 살짝 고임 · 연필: 흑연이라 조금 옅게
    if (pen == Ink.FOUNTAIN) drawCircle(color, p[2] * w * 0.62f, Offset(p[0] * w, p[1] * w))
    val col = if (pen == Ink.PENCIL) color.copy(alpha = 0.85f) else color
    for (j in 1 until s.size) {
        drawLine(col, Offset(p[j * 3 - 3] * w, p[j * 3 - 2] * w), Offset(p[j * 3] * w, p[j * 3 + 1] * w), (p[j * 3 - 1] + p[j * 3 + 2]) / 2 * w, StrokeCap.Round)
    }
}

/** 공책 보기 · 손으로 쓴 한 절: 쓰던 줄 간격 그대로 줄을 긋고 획을 얹어요. */
@Composable
fun InkSheets(page: Ink.Page, color: Color, lineColor: Color, graphite: Color = color) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wDp = maxWidth
        Column {
            page.sheets.forEachIndexed { i, sheet ->
                val lines = kotlin.math.ceil((page.height(i) + page.line * 0.25f) / page.line).toInt().coerceAtLeast(1)
                Canvas(Modifier.fillMaxWidth().height(wDp * (lines * page.line))) {
                    val w = size.width; val lh = page.line * w
                    for (l in 1..lines) drawLine(lineColor, Offset(0f, l * lh), Offset(w, l * lh), Tokens.Stroke.hair.toPx())
                    sheet.forEach { drawStroke(it, w, if (page.pen == Ink.PENCIL) graphite else color, page.pen) }
                }
            }
        }
    }
}

/** 손글씨 한 권 (M3): 손으로 쓴 장을 한 쪽씩, 책장을 넘기듯. */
@Composable
fun HandBookView(s: AppState, book: Int) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    androidx.activity.compose.BackHandler { s.handBook = null }
    val chapters = remember(book, s.translation) { Ink.chapters(ctx, s.translation.id, book) }
    val pager = androidx.compose.foundation.pager.rememberPagerState { chapters.size.coerceAtLeast(1) }
    Column(Modifier.fillMaxSize().background(c.leaf).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null) {}.systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalAlignment = Alignment.CenterVertically) {
            RunningHead(stringResource(R.string.hand_book, s.bookName(book)), if (chapters.isEmpty()) "" else "${pager.currentPage + 1} / ${chapters.size}", k, Modifier.weight(1f))
        }
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1,
            flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(pager, snapPositionalThreshold = Tokens.Motion.turnSnap,
                snapAnimationSpec = androidx.compose.animation.core.tween(Tokens.Motion.turnMs, easing = androidx.compose.animation.core.FastOutSlowInEasing))) { i ->
            val ch = chapters.getOrNull(i) ?: return@HorizontalPager
            val verses = remember(ch) { Ink.verses(ctx, s.translation.id, book, ch).mapNotNull { v -> Ink.load(Ink.file(ctx, s.translation.id, book, ch, v))?.let { v to it } } }
            // 넘길 때: 메인 장들과 같은 결 (새 장이 살짝 기운 채 들어와 내려앉음)
            Column(Modifier.fillMaxSize().zIndex(if ((pager.currentPage - i) + pager.currentPageOffsetFraction < 0f) 1f else 0f).pageTurn(pager, i).padding(horizontal = Tokens.Space.s5).clip(RoundedCornerShape(Tokens.Radius.card)).background(c.paper)
                .verticalScroll(rememberScrollState()).padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                ChapterInitial(ch, true)
                verses.forEach { (v, page) ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        Text("$v", style = Theme.small().copy(color = c.rubric), modifier = Modifier.padding(top = Tokens.Space.s1))
                        Box(Modifier.weight(1f)) { InkSheets(page, c.penInk, c.noteLine, c.graphite) }
                    }
                }
            }
        }
        BookButton(stringResource(R.string.close), Modifier.fillMaxWidth().padding(Tokens.Space.s5), quiet = true) { s.handBook = null }
    }
}
