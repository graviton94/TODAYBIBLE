package io.github.graviton94.todaybible.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.withStyle
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
    // 오늘의 장: 길잡이가 있으면 길잡이의 다음 장, 없으면 쓰던 장
    val pn = s.planNext()
    val (cb, cc) = if (pn != null) pn.first to pn.second else s.resumePlace()
    val ct = s.store.book(s.translation, cb)
    val goal = s.effectiveGoal()
    val fill = ct.fillable(cc); val inCh = fill.count { s.progress.isFilled(s.translation, io.github.graviton94.todaybible.core.VerseKey(cb, cc, it)) }
    val v = pn?.third ?: s.progress.nextVerse(s.translation, ct, cc)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5).padding(top = Tokens.Space.s3, bottom = Tokens.Space.s5),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
        // 이어 쓰기 날수 · 요일 점 (하루의 편지 R1)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(androidx.compose.ui.text.buildAnnotatedString {
                append(stringResource(R.string.streak_pre)); append(" ")
                withStyle(Theme.big(Tokens.Text.streak).toSpanStyle()) { append("$run") }
                append(stringResource(R.string.streak_post))
            }, style = Theme.body(), maxLines = 1, modifier = Modifier.weight(1f))
            if (s.ownerName.isNotBlank()) Text(s.ownerName, style = Theme.small(), maxLines = 1)
        }
        WeekDots(s)
        Hair()
        // 오늘의 장: 라틴 머리글 · 제목 · 진행 · 이어 쓸 절 · 버튼 하나
        Column(Modifier.fillMaxWidth().coach("today_card"), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            HeadText(s, cb, cc)
            Text(stringResource(R.string.listen_head, s.bookName(cb), cc), style = Theme.title(k, Tokens.Text.display), maxLines = 1)
            Text(stringResource(R.string.ch_progress, inCh, fill.size) + if (verses > 0) " · " + stringResource(R.string.today_written, verses) else "", style = Theme.small(), maxLines = 1)
        }
        Segments(fill.size, inCh)
        if (goal > 0) Text(if (met) stringResource(R.string.goal_done) else stringResource(R.string.goal_left, (goal - verses).coerceAtLeast(0)),
            style = Theme.small().copy(color = if (met) c.giltText else c.inkSoft), maxLines = 1)
        if (v != null) Text(androidx.compose.ui.text.buildAnnotatedString {
            withStyle(androidx.compose.ui.text.SpanStyle(color = c.giltText)) { append("$v ") }; append(Markup.plain(ct.verse(cc, v)))
        }, style = Theme.verse(k), maxLines = 3, overflow = TextOverflow.Ellipsis)
        Column(Modifier.fillMaxWidth()) {
            BookButton(if (v != null) stringResource(R.string.continue_at_verse, v) else stringResource(R.string.continue_now), Modifier.fillMaxWidth().coach("today_go")) { s.open(cb, cc) }
            BookButton(stringResource(R.string.read_short), Modifier.fillMaxWidth().coach("today_read"), quiet = true) { s.read(cb, cc) }
        }
        // 쓰다 멈춘 곳: 다른 장 · 다른 권에서 쓰다 만 자리 (최근 순, 둘까지)
        val paused = remember(s.fills.size, cb, cc) { s.pausedChapters(2, cb to cc) }
        if (paused.isNotEmpty()) Column(Modifier.fillMaxWidth()) {
            Text("PAUSA · " + stringResource(R.string.paused_title), style = Theme.caps().copy(color = c.inkSoft), maxLines = 1, modifier = Modifier.padding(bottom = Tokens.Space.s1))
            paused.forEach { p ->
                Hair()
                ListRow(stringResource(R.string.paused_row, s.chapterRef(p.book, p.chapter), p.next), "${p.done} / ${p.total}") { s.open(p.book, p.chapter) }
            }
        }
        // 아래: 오늘 할 수 있는 일들을 같은 줄 모양으로
        Column(Modifier.fillMaxWidth()) {
            Hair()
            PrayerRow(s)
            ResumeListenRow(s)
            if (today.dayOfWeek == java.time.DayOfWeek.SUNDAY || s.sermonOn(today.toEpochDay()) != null)
                ListRow(stringResource(R.string.sermon_title), stringResource(if (s.sermonOn(today.toEpochDay()) != null) R.string.sermon_edit_short else R.string.sermon_write_short)) { s.sermonOpen = today.toEpochDay() }
            s.memory.filter { it.translation == s.translation }.sortedBy { it.key.raw }.takeIf { it.isNotEmpty() }?.let { list ->
                val m = list[Math.floorMod(today.toEpochDay(), list.size.toLong()).toInt()].key
                ListRow(stringResource(R.string.memory_title), stringResource(R.string.ref_verse, s.bookName(m.book), m.chapter, m.verse)) { s.memoryOpen = m }
            }
            s.plan?.let { pl -> val (total, done) = s.planCounts()
                ListRow(planName(ctx, pl.id), if (done >= total) stringResource(R.string.plan_done) else stringResource(R.string.plan_day, minOf(s.planDay(), pl.days), pl.days)) { s.planOpen = true } }
            s.nextPlate()?.let { (pl, _) ->
                val img = rememberPlate(pl.id, small = true)
                ListRow(stringResource(R.string.next_plate_short, s.plateName(pl)), "${(s.plateFraction(pl) * 100).toInt()}%",
                    leading = { if (img != null) Image(img, null, Modifier.size(Tokens.Size.iconMd).clip(androidx.compose.foundation.shape.CircleShape), contentScale = ContentScale.Crop) }) { s.plateView = pl }
            }
            io.github.graviton94.todaybible.core.Plans.seasonal(today)?.takeIf { it.first != s.plan?.id }?.let { (id, start) ->
                val left = ChronoUnit.DAYS.between(today, start).toInt()
                ListRow(if (left > 0) stringResource(R.string.season_soon, planName(ctx, id), left) else stringResource(R.string.season_now, planName(ctx, id)), "") { s.choosePlan(id) }
            }
            Feasts.upcoming(today, korea = k, count = 1).firstOrNull()?.let { (f, d) ->
                val name = ctx.getString(ctx.resources.getIdentifier("feast_${f.name}", "string", ctx.packageName))
                val daysLeft = ChronoUnit.DAYS.between(today, d).toInt()
                ListRow(if (daysLeft == 0) stringResource(R.string.feast_today, name) else stringResource(R.string.feast_soon, name, daysLeft), "") { }
            }
            if ((today.monthValue == 12 && today.dayOfMonth >= 15) || (today.monthValue == 1 && today.dayOfMonth <= 7))
                ListRow(stringResource(R.string.year_ready), "") { s.page = AppState.RECORD }
            if (!s.lifetime.unlocked) ListRow(stringResource(R.string.lifetime_row), stringResource(R.string.lifetime_row_more)) { s.purchaseOpen = true }
        }
    }
}

/** 지금 때의 기도 한 줄 (아침 · 낮 · 저녁 · 밤). */
@Composable
private fun PrayerRow(s: AppState) {
    val p = io.github.graviton94.todaybible.core.Prayers.forHour(io.github.graviton94.todaybible.core.Prayers.hourAt(java.time.LocalTime.now().hour))
    ListRow("${prayerName(p)} · ${s.prayerRefShort(p)}", stringResource(if (s.prayed(p.id)) R.string.prayer_prayed else R.string.prayer_go_short)) { s.prayerOpen = p.id }
}

/** 이어 듣기: 듣다 멈춘 자리 (2주 안, 지금 듣는 중이 아니면). 누르면 그 장을 펼치고 그 절부터 다시. */
@Composable
private fun ResumeListenRow(s: AppState) {
    val ctx = LocalContext.current
    val playing = io.github.graviton94.todaybible.data.ListenService.now.collectAsState().value != null
    if (playing) return
    val p = s.store.lastListen.split(':').takeIf { it.size == 5 } ?: return
    if (p[0] != s.translation.id) return
    val b = p[1].toIntOrNull() ?: return; val ch = p[2].toIntOrNull() ?: return; val v = p[3].toIntOrNull() ?: return; val day = p[4].toLongOrNull() ?: return
    if (s.today().toEpochDay() - day > 14 || s.locked(b)) return
    ListRow(stringResource(R.string.resume_listen, s.chapterRef(b, ch)), stringResource(R.string.resume_listen_from, v)) {
        s.read(b, ch, v); io.github.graviton94.todaybible.data.ListenService.start(ctx, b, ch, v, s.aloudRate())
    }
}

/** 이번 주 요일 점 (하루의 편지): 쓴 날은 금빛 점, 오늘은 금빛 테, 남은 날은 옅은 테. */
@Composable
fun WeekDots(s: AppState) {
    val c = Theme.c; val today = s.today(); val days = s.progress.days()
    val sunday = today.minusDays((today.dayOfWeek.value % 7).toLong())
    val names = stringResource(R.string.weekdays)
    Row(Modifier.fillMaxWidth()) {
        for (i in 0 until 7) {
            val d = sunday.plusDays(i.toLong()); val done = d.toEpochDay() in days
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Text(names[i].toString(), style = Theme.small().copy(color = if (d.isAfter(today)) c.unwritten else c.ink, textAlign = TextAlign.Center), maxLines = 1)
                Box(Modifier.size(Tokens.Size.dotDay).drawBehind {
                    when {
                        done -> drawCircle(c.gilt)
                        d == today -> drawCircle(c.gilt, style = Stroke(Tokens.Stroke.gilt.toPx()))
                        else -> drawCircle(c.hair, style = Stroke(Tokens.Stroke.hair.toPx()))
                    }
                })
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
                Text(names[i].toString(), style = Theme.small().copy(color = if (i == 0) c.giltText else c.inkSoft, textAlign = TextAlign.Center), maxLines = 1)
                Box(Modifier.size(Tokens.Size.touch * Tokens.Ratio.stampInCell).then(if (d == today) Modifier.drawWithContent {
                    drawContent(); drawLine(c.gilt, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.rule.toPx())
                } else Modifier), contentAlignment = Alignment.Center) {
                    if (d.toEpochDay() in days) StampMark(s.stamp, c.rubric, Modifier.fillMaxSize(Tokens.Ratio.stampInCell))
                    else Text("${d.dayOfMonth}", style = Theme.small().copy(color = if (d.isAfter(today)) c.hair else c.unwritten), maxLines = 1)
                }
            }
        }
    }
}

fun planName(ctx: android.content.Context, id: String): String = ctx.getString(ctx.resources.getIdentifier("plan_$id", "string", ctx.packageName))

/** 길잡이 고르기 시트. */
@Composable
fun PlanSheet(s: AppState) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    BookSheet({ s.planOpen = false }) {
        Text("ORDO LECTIONIS", style = Theme.caps(), maxLines = 1)
        Text(stringResource(R.string.plan), style = Theme.title(k, Tokens.Text.display), modifier = Modifier.fillMaxWidth())
        io.github.graviton94.todaybible.core.Plans.all.forEach { pl ->
            val verses = remember(pl.id, s.translation) { pl.chapters.sumOf { (b, ch) -> s.store.book(s.translation, b).fillable(ch).size } }
            val on = s.planId == pl.id
            Hair()
            Row(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.touch)
                .clickable(role = Role.RadioButton) { s.choosePlan(pl.id) }.padding(vertical = Tokens.Space.s2),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(planName(ctx, pl.id), style = Theme.body().copy(color = if (on) c.giltText else c.ink), maxLines = 1)
                    Text(stringResource(R.string.plan_rate, (verses + pl.days - 1) / pl.days, pl.days), style = Theme.small(), maxLines = 1)
                }
                if (pl.books.any { s.premium(it) }) PremiumTag()
                else if (on) Text("✓", style = Theme.body().copy(color = c.gilt))
            }
        }
        BookButton(stringResource(R.string.plan_none), Modifier.fillMaxWidth(), quiet = true) { s.choosePlan(null) }
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
        HeadText(s, b, ch)
        Text(stringResource(R.string.listen_head, s.bookName(b), ch), style = Theme.title(k, Tokens.Text.display))
        Text(stringResource(R.string.ch_progress, done, fill.size) + if (s.goalMet()) " · " + stringResource(R.string.goal_done) else "", style = Theme.body().copy(color = c.inkSoft))
        BigAction(stringResource(R.string.simple_aloud)) { s.store.copyTab = 0; s.open(b, ch) }
        BigAction(stringResource(R.string.simple_listen), primary = false) { s.read(b, ch); if (s.locked(b)) return@BigAction; io.github.graviton94.todaybible.data.ListenService.start(ctx, b, ch, s.progress.nextVerse(s.translation, t, ch) ?: 1, s.aloudRate()) }
        BigAction(stringResource(R.string.simple_write), primary = false) { s.store.copyTab = 2; s.open(b, ch) }
        WeekStamps(s)
    }
}

@Composable
private fun BigAction(text: String, primary: Boolean = true, onClick: () -> Unit) {
    val c = Theme.c
    // 하나만 채우고 나머지는 가는 테 (크기는 그대로 커서 누르기 쉬움)
    Box(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.bigAction).then(if (primary) Modifier.background(c.leather) else Modifier.border(Tokens.Stroke.hair, c.ink)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) { Text(text, style = Theme.title(true).copy(color = if (primary) c.leatherInk else c.ink)) }
}
