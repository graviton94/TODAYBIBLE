package io.github.graviton94.todaybible.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Feasts
import io.github.graviton94.todaybible.core.Goal
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.core.Pieces
import io.github.graviton94.todaybible.core.Presence
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import java.time.temporal.ChronoUnit

/**
 * 오늘 (첫 화면, B1): 오늘의 분량 고리 · 이어 쓸 한 절 · 이번 주 도장 · 다음 판화까지 남은 절 · 다가오는 교회력.
 * 들어오자마자 할 일 하나와 받을 것이 보이게.
 */
@Composable
fun HomePage(s: AppState) {
    if (s.simple) { SimpleHome(s); return }
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    val today = s.today(); val days = s.progress.days()
    val run = Presence.streak(days, today)
    val verses = s.todayVerses(); val met = s.goalMet()
    val t = s.text(); val next = s.progress.nextVerse(s.translation, t, s.chapter)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s5)) {
        RunningHead(fmtDate(R.string.fmt_date_day, today),
            if (run > 0) stringResource(R.string.day_n, run) else "", k)
        // 부를 이름이 있으면 때에 맞는 인사 한 줄
        if (s.ownerName.isNotBlank()) {
            val h = java.time.LocalTime.now().hour
            Text(stringResource(when { h in 4..11 -> R.string.hi_morning; h in 12..17 -> R.string.hi_day; else -> R.string.hi_evening }, s.ownerName),
                style = Theme.title(k), maxLines = 1)
        }
        // 오늘의 장: 길잡이가 있으면 길잡이의 다음 장, 없으면 쓰던 장
        val pn = s.planNext()
        val (cb, cc) = if (pn != null) pn.first to pn.second else s.book to s.chapter
        val ct = s.store.book(s.translation, cb)
        val goal = s.effectiveGoal()
        val fill = ct.fillable(cc); val inCh = fill.count { s.progress.isFilled(s.translation, io.github.graviton94.todaybible.core.VerseKey(cb, cc, it)) }
        Column(Modifier.fillMaxWidth().coach("today_card").clip(RoundedCornerShape(Tokens.Radius.card)).background(c.paper).padding(Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (goal < 0) stringResource(R.string.today_chapter) else stringResource(R.string.goal_verses, goal), style = Theme.small().copy(color = c.rubric), modifier = Modifier.weight(1f), maxLines = 1)
                if (met) { StampMark(s.stamp, c.rubric, Modifier.size(Tokens.Size.iconSm)); Text(" " + stringResource(R.string.goal_done), style = Theme.small().copy(color = c.giltText), maxLines = 1) }
                else if (goal > 0) Text(stringResource(R.string.goal_left, (goal - verses).coerceAtLeast(0)), style = Theme.small(), maxLines = 1)
            }
            Text(stringResource(R.string.listen_head, s.bookName(cb), cc), style = Theme.title(k, Tokens.Text.title), maxLines = 1)
            Box(Modifier.fillMaxWidth().height(Tokens.Size.bar).clip(RoundedCornerShape(Tokens.Size.bar)).background(c.hair)) {
                Box(Modifier.fillMaxWidth(if (fill.isEmpty()) 0f else inCh / fill.size.toFloat()).height(Tokens.Size.bar).background(if (met) c.gilt else c.rubric))
            }
            Text(stringResource(R.string.ch_progress, inCh, fill.size), style = Theme.small(), maxLines = 1)
            val v = pn?.third ?: s.progress.nextVerse(s.translation, ct, cc)
            if (v != null) Text(Markup.plain(ct.verse(cc, v)), style = Theme.body().copy(color = c.inkSoft), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                BookButton(stringResource(R.string.continue_now), Modifier.weight(2f).coach("today_go")) { s.open(cb, cc) }
                BookButton(stringResource(R.string.read_short), Modifier.weight(1f).coach("today_read"), quiet = true) { s.read(cb, cc) }
            }
        }
        // 이번 주 도장 (주일부터)
        WeekStamps(s)
        // 마음에 새기는 말씀: 날마다 하나씩 돌아가며 곁에 (누르면 되뇌기)
        s.memory.filter { it.translation == s.translation }.sortedBy { it.key.raw }.takeIf { it.isNotEmpty() }?.let { list ->
            val m = list[Math.floorMod(today.toEpochDay(), list.size.toLong()).toInt()].key
            Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { s.memoryOpen = m }, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Text(stringResource(R.string.memory_title), style = Theme.small().copy(color = c.rubric), maxLines = 1)
                Text(Markup.plain(s.store.book(s.translation, m.book).verse(m.chapter, m.verse)), style = Theme.verse(k), maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.ref_verse, s.bookName(m.book), m.chapter, m.verse), style = Theme.small(), maxLines = 1)
            }
        }
        // 절기가 다가오면: 절기 읽기 계획 권하기 (한 줄)
        io.github.graviton94.todaybible.core.Plans.seasonal(today)?.takeIf { it.first != s.plan?.id }?.let { (id, start) ->
            val left = ChronoUnit.DAYS.between(today, start).toInt()
            Text(if (left > 0) stringResource(R.string.season_soon, planName(ctx, id), left) else stringResource(R.string.season_now, planName(ctx, id)),
                style = Theme.label().copy(color = c.rubric), modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Button) { s.choosePlan(id) })
        }
        MyBookCard(s)
        if (s.plan != null) PlanCard(s)
        // 다음 판화
        s.nextPlate()?.let { (pl, left) ->
            val f = s.plateFraction(pl)
            val n = Pieces.revealed(f); val order = remember(pl.id) { Pieces.order(pl.id.hashCode()) }.take(n).toSet()
            val img = rememberPlate(pl.id, small = true)
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { s.plateView = pl }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Box(Modifier.width(Tokens.Size.initialHome).aspectRatio(Tokens.Ratio.plateAspect).clip(RoundedCornerShape(Tokens.Radius.frame)).background(c.paper).drawWithContent {
                    drawContent()
                    val w = size.width / Pieces.COLS; val h = size.height / Pieces.ROWS
                    for (i in 0 until Pieces.COUNT) if (i !in order) drawRect(c.paper.copy(alpha = Tokens.Alpha.veilPiece), Offset((i % Pieces.COLS) * w, (i / Pieces.COLS) * h), Size(w + 1f, h + 1f))
                }) { if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.next_plate, s.plateName(pl), left), style = Theme.label(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.plate_where, s.chapterRef(pl.book, pl.chapter), n, Pieces.COUNT), style = Theme.small(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // 교회력: 오늘이면 발자취 안내, 아니면 가장 가까운 날
        Feasts.upcoming(today, korea = k, count = 1).firstOrNull()?.let { (f, d) ->
            val name = ctx.getString(ctx.resources.getIdentifier("feast_${f.name}", "string", ctx.packageName))
            val daysLeft = ChronoUnit.DAYS.between(today, d).toInt()
            // 한 줄 소식 (오늘이면 붉게)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Text(if (daysLeft == 0) stringResource(R.string.feast_today, name) else stringResource(R.string.feast_soon, name, daysLeft),
                    style = Theme.label().copy(color = if (daysLeft == 0) c.rubric else c.ink), maxLines = 1)
                Text(stringResource(if (daysLeft == 0) R.string.feast_reward else R.string.feast_reward_on, milestoneName(ctx, f.milestone)), style = Theme.small(), maxLines = 2)
            }
        }
    }
}

/** 이번 주 (주일부터) 도장 일곱 칸. 오늘은 가는 테. */
@Composable
fun WeekStamps(s: AppState) {
    val c = Theme.c; val today = s.today(); val days = s.progress.days()
    val sunday = today.minusDays((today.dayOfWeek.value % 7).toLong())
    val names = stringResource(R.string.weekdays)
    Row(Modifier.fillMaxWidth()) {
        for (i in 0 until 7) {
            val d = sunday.plusDays(i.toLong())
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Text(names[i].toString(), style = Theme.small().copy(color = if (i == 0) c.rubric else c.inkSoft, textAlign = TextAlign.Center), maxLines = 1)
                Box(Modifier.size(Tokens.Size.touch * Tokens.Ratio.stampInCell).then(if (d == today) Modifier.drawWithContent {
                    drawContent(); drawRoundRect(c.ink, style = Stroke(Tokens.Stroke.hair.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(Tokens.Radius.chip.toPx()))
                } else Modifier), contentAlignment = Alignment.Center) {
                    if (d.toEpochDay() in days) StampMark(s.stamp, c.rubric, Modifier.fillMaxSize(Tokens.Ratio.stampInCell))
                    else Text("${d.dayOfMonth}", style = Theme.small().copy(color = if (d.isAfter(today)) c.hair else c.unwritten), maxLines = 1)
                }
            }
        }
    }
}

/** 길잡이 (I1): 고른 길의 진행 · 없으면 고르기. */
@Composable
private fun PlanCard(s: AppState) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    val pl = s.plan
    if (pl == null) { BookButton(stringResource(R.string.plan_choose), Modifier.fillMaxWidth(), quiet = true) { s.planOpen = true }; return }
    val (total, done) = s.planCounts()
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { s.planOpen = true }, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(planName(ctx, pl.id), style = Theme.label(), maxLines = 1, modifier = Modifier.weight(1f))
            Text(if (done >= total) stringResource(R.string.plan_done) else stringResource(R.string.plan_day, minOf(s.planDay(), pl.days), pl.days), style = Theme.small(), maxLines = 1)
        }
        Box(Modifier.fillMaxWidth().height(Tokens.Size.handleH * 2).clip(RoundedCornerShape(Tokens.Size.handleH)).background(c.hair)) {
            Box(Modifier.fillMaxWidth(if (total == 0) 0f else done.toFloat() / total).height(Tokens.Size.handleH * 2).background(c.rubric))
        }
        Text(stringResource(R.string.plan_progress, "%,d".format(done), "%,d".format(total)), style = Theme.small(), maxLines = 1)
    }
}

fun planName(ctx: android.content.Context, id: String): String = ctx.getString(ctx.resources.getIdentifier("plan_$id", "string", ctx.packageName))

/** 길잡이 고르기 시트. */
@Composable
fun PlanSheet(s: AppState) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    BookSheet({ s.planOpen = false }) {
        Text(stringResource(R.string.plan), style = Theme.title(k), modifier = Modifier.fillMaxWidth())
        io.github.graviton94.todaybible.core.Plans.all.forEach { pl ->
            val verses = remember(pl.id, s.translation) { pl.chapters.sumOf { (b, ch) -> s.store.book(s.translation, b).fillable(ch).size } }
            val on = s.planId == pl.id
            Row(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.touch).clip(RoundedCornerShape(Tokens.Radius.button)).background(if (on) c.paper else c.leaf)
                .clickable(role = Role.RadioButton) { s.choosePlan(pl.id) }.padding(horizontal = Tokens.Space.s3, vertical = Tokens.Space.s2),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(planName(ctx, pl.id), style = Theme.body(), maxLines = 1)
                    Text(stringResource(R.string.plan_rate, (verses + pl.days - 1) / pl.days, pl.days), style = Theme.small(), maxLines = 1)
                }
                if (pl.books.any { s.locked(it) }) LockMark(c.unwritten, Modifier.size(Tokens.Size.lock))
                else if (on) Box(Modifier.size(Tokens.Size.dot).clip(androidx.compose.foundation.shape.CircleShape).background(c.rubric))
            }
        }
        BookButton(stringResource(R.string.plan_none), Modifier.fillMaxWidth(), quiet = true) { s.choosePlan(null) }
    }
}

/**
 * 나의 성경 한 권: 앱이 바라는 단 하나. 표지 가죽 색 작은 책과 지금까지 옮긴 절 · 온 성경 가운데 얼마인지.
 * 누르면 서재로.
 */
@Composable
private fun MyBookCard(s: AppState) {
    val c = Theme.c; val k = s.korean
    val filled = s.progress.filled(s.translation).size
    val total = s.translation.total
    val frac = (filled.toFloat() / total).coerceIn(0f, 1f)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.card)).background(c.paper).clickable(role = Role.Button) { s.page = AppState.BIBLE }
        .padding(Tokens.Space.s4), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
        // 작은 가죽 책: 금박 테 · 채운 만큼 아래에서 금빛이 차오름
        Box(Modifier.size(Tokens.Size.bookW, Tokens.Size.bookH).clip(RoundedCornerShape(Tokens.Radius.chip)).background(s.coverColor())
            .drawBehind {
                drawRect(c.gilt.copy(alpha = Tokens.Alpha.shine), Offset(0f, size.height * (1f - frac)), Size(size.width, size.height * frac))
                giltFrame(c.gilt.copy(alpha = Tokens.Alpha.frame), bands = true)
            }, contentAlignment = Alignment.Center) { StampMark(STAMP_CROSS, c.gilt, Modifier.size(Tokens.Size.iconSm)) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
            Text(if (s.ownerName.isNotBlank()) stringResource(R.string.mybook_named, s.ownerName) else stringResource(R.string.mybook), style = Theme.title(k), maxLines = 1)
            Text(stringResource(R.string.mybook_count, "%,d".format(filled), "%.1f".format(frac * 100)), style = Theme.small().copy(color = c.inkSoft), maxLines = 1)
            Box(Modifier.fillMaxWidth().height(Tokens.Stroke.rule).background(c.hair)) { Box(Modifier.fillMaxWidth(frac.coerceAtLeast(0.01f)).height(Tokens.Stroke.rule).background(c.gilt)) }
        }
    }
}

/**
 * 간단 모드 (F5): 자녀가 부모님 폰에 켜 드리기 좋게. 오늘의 장 하나와 큰 버튼 셋만.
 * 소리 내어 읽기 · 듣기 · 손으로 쓰기 (타자보다 쉬운 쪽). 기록 · 화첩은 아래 이름표로만.
 */
@Composable
private fun SimpleHome(s: AppState) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    val pn = s.planNext()
    val (b, ch) = if (pn != null) pn.first to pn.second else s.book to s.chapter
    val t = s.store.book(s.translation, b)
    val fill = t.fillable(ch); val done = fill.count { s.progress.isFilled(s.translation, io.github.graviton94.todaybible.core.VerseKey(b, ch, it)) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s5)) {
        Text(stringResource(R.string.today_chapter), style = Theme.label().copy(color = c.rubric))
        Text(stringResource(R.string.listen_head, s.bookName(b), ch), style = Theme.title(k, Tokens.Text.display))
        Text(stringResource(R.string.ch_progress, done, fill.size) + if (s.goalMet()) " · " + stringResource(R.string.goal_done) else "", style = Theme.body().copy(color = c.inkSoft))
        BigAction(stringResource(R.string.simple_aloud)) { s.store.copyTab = 0; s.open(b, ch) }
        BigAction(stringResource(R.string.simple_listen)) { s.read(b, ch); io.github.graviton94.todaybible.data.ListenService.start(ctx, b, ch, s.progress.nextVerse(s.translation, t, ch) ?: 1, s.aloudRate()) }
        BigAction(stringResource(R.string.simple_write)) { s.store.copyTab = 2; s.open(b, ch) }
        WeekStamps(s)
    }
}

@Composable
private fun BigAction(text: String, onClick: () -> Unit) {
    val c = Theme.c
    Box(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.bigAction).clip(RoundedCornerShape(Tokens.Radius.card)).background(c.leather).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) { Text(text, style = Theme.title(true).copy(color = c.leatherInk)) }
}
