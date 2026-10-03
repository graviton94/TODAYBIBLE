package io.github.graviton94.todaybible.ui

import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.core.Milestone
import io.github.graviton94.todaybible.core.Pieces
import io.github.graviton94.todaybible.core.Presence
import io.github.graviton94.todaybible.data.Plate
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Composable
fun RecordPage(s: AppState) {
    val k = s.korean
    val days = s.progress.days()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s5)) {
        RunningHead(stringResource(R.string.page_record), stringResource(R.string.presence_n, Presence.total(days)), k)
        yearShown(s)?.let { YearCard(s, it) }
        Stats(s)
        if (s.aloudLog.isNotEmpty()) AloudTime(s)
        YearStamps(s)
        PresenceMonth(s, days)
        if (s.marks.isNotEmpty()) {
            Section(stringResource(R.string.marks), "${s.marks.count { it.translation == s.translation }}", k)
            MarkList(s)
        }
        Section(stringResource(R.string.plates), "${s.store.plates.count { plateFraction(s, it) >= 1f }} / ${s.store.plates.size}", k)
        PlateGallery(s)
        Section(stringResource(R.string.milestones), "${s.earned.size} / ${Milestone.entries.size}", k)
        MilestoneList(s)
    }
}

@Composable
private fun Section(title: String, right: String, korean: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = Tokens.Space.s2), verticalAlignment = Alignment.Bottom) {
        Text(title, style = Theme.title(korean, Tokens.Text.title), maxLines = 1, modifier = Modifier.weight(1f))
        Text(right, style = Theme.small(), maxLines = 1)
    }
}

/** 이 달의 출석: 쓴 날 = 도장, 주일 = 쉼 (빈칸이어도 괜찮음), 오늘 = 가는 테. */
@Composable
private fun PresenceMonth(s: AppState, days: Set<Long>) {
    val c = Theme.c; val k = s.korean
    val today = s.today(); val month = YearMonth.from(today)
    val first = month.atDay(1); val lead = first.dayOfWeek.value % 7
    val cells = (0 until lead).map { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val names = stringResource(R.string.weekdays)
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(month.atDay(1).format(DateTimeFormatter.ofPattern(if (k) "yyyy년 M월" else "MMMM yyyy", if (k) java.util.Locale.KOREAN else java.util.Locale.ENGLISH)),
                style = Theme.label(), modifier = Modifier.weight(1f), maxLines = 1)
            Text(stringResource(R.string.weeks_n, Presence.weeksComplete(days)), style = Theme.small(), maxLines = 1)
        }
        Row(Modifier.fillMaxWidth()) {
            names.forEachIndexed { i, ch ->
                Text(ch.toString(), style = Theme.small().copy(color = if (i == 0) c.rubric else c.inkSoft, textAlign = TextAlign.Center), modifier = Modifier.weight(1f))
            }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(Tokens.Size.spineGap), contentAlignment = Alignment.Center) {
                        if (d != null) DayCell(s, d, d.toEpochDay() in days, d == today, d.isAfter(today))
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun DayCell(s: AppState, d: LocalDate, present: Boolean, today: Boolean, future: Boolean) {
    val c = Theme.c
    Box(
        Modifier.fillMaxSize().drawBehind {
            if (today) drawRoundRect(c.ink, style = Stroke(Tokens.Stroke.hair.toPx()), cornerRadius = CornerRadius(Tokens.Radius.chip.toPx()))
        },
        contentAlignment = Alignment.Center,
    ) {
        if (present) StampMark(s.stamp, c.rubric, Modifier.fillMaxSize(Tokens.Ratio.stampInCell))
        else Text("${d.dayOfMonth}", style = Theme.small().copy(color = when {
            future -> c.hair.copy(alpha = Tokens.Alpha.future)
            Presence.isRest(d) -> c.rubric.copy(alpha = Tokens.Alpha.rest)
            else -> c.unwritten
        }), maxLines = 1)
    }
}

/** 판화가 있는 장의 완성도 (0..1). */
private fun plateFraction(s: AppState, p: Plate): Float = s.plateFraction(p)

/** 화첩: 판화마다 그 장을 쓴 만큼 조각이 드러남 (3×4 = 12조각). 누르면 그 장으로. */
@Composable
private fun PlateGallery(s: AppState) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
        s.store.plates.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                row.forEach { p -> PlateCard(s, p, Modifier.weight(1f)) }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** 판화 그림. small = 화첩 · 오늘 화면의 작은 그림 (4분의 1로 읽어 메모리를 아낌). */
@Composable
fun rememberPlate(id: String, small: Boolean = false): ImageBitmap? {
    val ctx = LocalContext.current
    return remember(id, small) {
        runCatching { ctx.assets.open("plates/$id.jpg").use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = if (small) 4 else 1 })?.asImageBitmap() } }.getOrNull()
    }
}

@Composable
private fun PlateCard(s: AppState, p: Plate, modifier: Modifier) {
    val c = Theme.c; val k = s.korean
    val f = plateFraction(s, p); val n = Pieces.revealed(f)
    val order = remember(p.id) { Pieces.order(p.id.hashCode()) }
    val shown = order.take(n).toSet()
    val img = rememberPlate(p.id, small = true)
    Column(modifier.clickable(role = Role.Button) { s.plateView = p }, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(Tokens.Ratio.plateAspect).clip(RoundedCornerShape(Tokens.Radius.chip)).background(c.paper)
                .drawWithContent {
                    drawContent()
                    // 아직 드러나지 않은 조각은 종이로 덮고, 조각 사이엔 머리카락 같은 줄
                    val w = size.width / Pieces.COLS; val h = size.height / Pieces.ROWS
                    for (i in 0 until Pieces.COUNT) if (i !in shown) drawRect(c.paper.copy(alpha = Tokens.Alpha.veilPiece), Offset((i % Pieces.COLS) * w, (i / Pieces.COLS) * h), Size(w + 0.5f, h + 0.5f))
                    if (n in 1 until Pieces.COUNT) {
                        for (x in 1 until Pieces.COLS) drawLine(c.hair, Offset(x * w, 0f), Offset(x * w, size.height), Tokens.Stroke.hair.toPx())
                        for (y in 1 until Pieces.ROWS) drawLine(c.hair, Offset(0f, y * h), Offset(size.width, y * h), Tokens.Stroke.hair.toPx())
                    }
                    if (n == 0) drawRoundRect(c.hair, style = Stroke(Tokens.Stroke.hair.toPx()), cornerRadius = CornerRadius(Tokens.Radius.chip.toPx()))
                },
            contentAlignment = Alignment.Center,
        ) {
            if (img != null) Image(img, if (k) p.ko else p.en, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (n == 0) Canvas(Modifier.size(Tokens.Size.icon)) { stamp(STAMP_CROSS, c.unwritten.copy(alpha = Tokens.Alpha.faint)) }
        }
        // 세 줄로 좁으니 제목 한 줄 · 조각 수 한 줄 (권 · 장은 누르면 크게 보는 화면에)
        Text(if (k) p.ko else p.en, style = Theme.small().copy(color = c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$n/${Pieces.COUNT}", style = Theme.small().copy(color = if (n == Pieces.COUNT) c.giltText else c.inkSoft), maxLines = 1)
    }
}

/** 발자취: 한 줄 이름 + 한 줄 조건. 얻은 것은 가죽 메달, 아직은 빈 테. */
@Composable
private fun MilestoneList(s: AppState) {
    val c = Theme.c; val ctx = LocalContext.current
    val fmt = DateTimeFormatter.ofPattern(if (s.korean) "yyyy. M. d" else "d MMM yyyy", if (s.korean) java.util.Locale.KOREAN else java.util.Locale.ENGLISH)
    Column {
        Milestone.entries.forEach { m ->
            val day = s.earned[m]
            Row(
                Modifier.fillMaxWidth().heightIn(min = Tokens.Size.rowTall).drawBehind { drawLine(c.hair, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.hair.toPx()) }
                    .padding(vertical = Tokens.Space.s2),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
            ) {
                Canvas(Modifier.size(Tokens.Size.medal)) { medal(m, day != null, c.leather, c.gilt, c.unwritten.copy(alpha = Tokens.Alpha.medalFaint)) }
                Column(Modifier.weight(1f)) {
                    Text(milestoneName(ctx, m), style = Theme.label().copy(color = if (day != null) c.ink else c.inkSoft), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(milestoneRule(ctx, m), style = Theme.small(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (day != null) Text(LocalDate.ofEpochDay(day).format(fmt), style = Theme.small().copy(color = c.giltText), maxLines = 1)
            }
        }
    }
}

fun milestoneName(ctx: android.content.Context, m: Milestone): String =
    ctx.getString(ctx.resources.getIdentifier("ms_${m.name}_name", "string", ctx.packageName))
fun milestoneRule(ctx: android.content.Context, m: Milestone): String =
    ctx.getString(ctx.resources.getIdentifier("ms_${m.name}_rule", "string", ctx.packageName))

/** 숫자로 본 나의 성경 (J2): 지어낸 말 없이 숫자만. */
@Composable
private fun Stats(s: AppState) {
    val c = Theme.c; val k = s.korean
    val tr = s.translation
    val mine = s.fills.filter { it.translation == tr }
    val keys = mine.map { it.key.raw }.toSet()
    val letters = remember(keys.size, tr) {
        keys.groupBy { io.github.graviton94.todaybible.core.VerseKey(it).book }.entries.sumOf { (b, ks) ->
            val t = s.store.book(tr, b); ks.sumOf { r -> val v = io.github.graviton94.todaybible.core.VerseKey(r); Markup.plain(t.verse(v.chapter, v.verse)).count { !it.isWhitespace() } }
        }
    }
    val chapters = remember(keys.size, tr) {
        keys.map { io.github.graviton94.todaybible.core.VerseKey(it).book }.toSet().sumOf { b -> val t = s.store.book(tr, b); (1..t.chapterCount).count { s.progress.chapterDone(tr, t, it) } }
    }
    val longest = Presence.longestStreak(s.progress.days())
    val hour = mine.groupBy { java.time.Instant.ofEpochMilli(it.atMillis).atZone(java.time.ZoneId.systemDefault()).hour }.maxByOrNull { it.value.size }?.key
    val byMode = mine.groupBy { it.mode }.mapValues { it.value.size }
    val all = mine.size.coerceAtLeast(1)
    fun pct(m: io.github.graviton94.todaybible.core.Mode) = (byMode[m] ?: 0) * 100 / all
    val cells = listOf(
        stringResource(R.string.verses_n, keys.size) to stringResource(R.string.stat_verses),
        stringResource(R.string.letters_n, "%,d".format(letters)) to stringResource(R.string.stat_letters),
        stringResource(R.string.chapters_n, chapters) to stringResource(R.string.stat_chapters),
        stringResource(R.string.days_n, longest) to stringResource(R.string.stat_longest),
        (hour?.let { java.time.LocalTime.of(it, 0).format(DateTimeFormatter.ofPattern(if (k) "a h시" else "h a", if (k) java.util.Locale.KOREAN else java.util.Locale.ENGLISH)) } ?: "–") to stringResource(R.string.stat_hour),
        stringResource(R.string.stat_modes, pct(io.github.graviton94.todaybible.core.Mode.TYPE), pct(io.github.graviton94.todaybible.core.Mode.PAPER), pct(io.github.graviton94.todaybible.core.Mode.ALOUD)) to "",
    )
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        cells.take(4).chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                row.forEach { (big, small) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(Tokens.Radius.card)).background(c.paper).padding(Tokens.Space.s3)) {
                        Text(big, style = Theme.title(k), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(small, style = Theme.small(), maxLines = 1)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${cells[4].second} · ${cells[4].first}", style = Theme.small(), maxLines = 1, modifier = Modifier.weight(1f))
        }
        Text(cells[5].first, style = Theme.small(), maxLines = 1)
    }
}

/** 한 해 도장첩 (J1): 주마다 한 줄, 붉을수록 많이 쓴 날. */
@Composable
private fun YearStamps(s: AppState) {
    val c = Theme.c; val k = s.korean
    val today = s.today(); val year = today.year
    val counts = s.fills.filter { it.translation == s.translation }.groupBy { it.epochDay }.mapValues { it.value.size }
    val jan1 = java.time.LocalDate.of(year, 1, 1); val start = jan1.minusDays((jan1.dayOfWeek.value % 7).toLong())
    val stamped = counts.keys.count { java.time.LocalDate.ofEpochDay(it).year == year }
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(if (k) "${year}년" else "$year", style = Theme.label(), modifier = Modifier.weight(1f))
            Text(stringResource(R.string.year_stamps, stamped), style = Theme.small())
        }
        Canvas(Modifier.fillMaxWidth().aspectRatio(53f / 7f)) {
            val cell = size.width / 53f; val gap = cell * Tokens.Ratio.heatGap
            for (w in 0 until 53) for (d in 0 until 7) {
                val day = start.plusDays((w * 7 + d).toLong())
                if (day.year != year) continue
                val n = counts[day.toEpochDay()] ?: 0
                val col = when { day.isAfter(today) -> c.hair.copy(alpha = Tokens.Alpha.faint); n == 0 -> c.hair; n < 5 -> c.rubric.copy(alpha = Tokens.Alpha.faint); else -> c.rubric }
                drawRect(col, Offset(w * cell, d * cell), Size(cell - gap, cell - gap))
            }
        }
    }
}

private const val MARKS_SHOWN = 5

/** 형광펜 그은 절 (N): 최근 것부터. 누르면 그 절로. */
@Composable
private fun MarkList(s: AppState) {
    val c = Theme.c; val k = s.korean
    var all by remember { mutableStateOf(false) }
    val list = s.marks.filter { it.translation == s.translation }.sortedByDescending { it.epochDay }
    val fmt = java.time.format.DateTimeFormatter.ofPattern(if (k) "M월 d일" else "d MMM")
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        (if (all) list else list.take(MARKS_SHOWN)).forEach { m ->
            val v = m.key
            Column(Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { s.open(v.book, v.chapter); s.target = v.verse; s.page = 1 }
                .padding(vertical = Tokens.Space.s1), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Text(io.github.graviton94.todaybible.core.Markup.plain(s.store.book(s.translation, v.book).verse(v.chapter, v.verse)),
                    style = Theme.body().copy(background = c.mark), maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text("${s.bookName(v.book)} ${v.chapter}:${v.verse} · ${java.time.LocalDate.ofEpochDay(m.epochDay).format(fmt)}", style = Theme.small(), maxLines = 1)
            }
        }
        if (!all && list.size > MARKS_SHOWN) BookButton(stringResource(R.string.see_all, list.size), Modifier.fillMaxWidth(), quiet = true) { all = true }
    }
}

/** 올해의 필사 (R1)를 보여 줄 해: 12월 15일부터 이듬해 1월 7일까지. */
private fun yearShown(s: AppState): Int? {
    val d = s.today()
    return when {
        s.forceYear -> d.year
        d.monthValue == 12 && d.dayOfMonth >= 15 -> d.year
        d.monthValue == 1 && d.dayOfMonth <= 7 -> d.year - 1
        else -> null
    }
}

/** 올해의 필사 (R1): 가죽 카드 한 장 · 카드로 보내기. */
@Composable
private fun YearCard(s: AppState, year: Int) {
    val c = Theme.c; val k = s.korean; val ctx = androidx.compose.ui.platform.LocalContext.current
    val from = java.time.LocalDate.of(year, 1, 1).toEpochDay(); val to = java.time.LocalDate.of(year, 12, 31).toEpochDay()
    val inYear = s.fills.filter { it.epochDay in from..to }
    if (inYear.isEmpty()) return
    val verses = inYear.distinctBy { it.translation to it.key }.size
    val days = inYear.map { it.epochDay }.toSet()
    val topBook = inYear.groupingBy { it.key.book }.eachCount().maxByOrNull { it.value }?.key ?: 0
    val first = inYear.minBy { it.atMillis }
    val fmt = java.time.format.DateTimeFormatter.ofPattern(if (k) "M월 d일" else "d MMM")
    val plates = s.store.plates.count { s.plateFraction(it) >= 1f }
    val title = if (s.ownerName.isNotBlank()) stringResource(R.string.year_title_named, s.ownerName, year) else stringResource(R.string.year_title, year)
    val big = stringResource(R.string.year_big, "%,d".format(verses))
    val lines = listOf(
        stringResource(R.string.year_top, s.bookName(topBook)),
        stringResource(R.string.year_first, "${s.bookName(first.key.book)} ${first.key.chapter}:${first.key.verse}", java.time.LocalDate.ofEpochDay(first.epochDay).format(fmt)),
        stringResource(R.string.year_days, days.size, Presence.longestStreak(days)),
        stringResource(R.string.year_plates, plates),
    )
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.card)).background(c.leather).drawBehind { giltFrame(c.gilt.copy(alpha = Tokens.Alpha.frame), bands = false) }
        .padding(Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Text(title, style = Theme.title(k).copy(color = c.gilt), maxLines = 1)
        Text(big, style = Theme.title(k, Tokens.Text.display).copy(color = c.leatherInk), maxLines = 1)
        lines.forEach { Text(it, style = Theme.small().copy(color = c.leatherInk), maxLines = 1) }
        BookButton(stringResource(R.string.year_share), Modifier.fillMaxWidth().padding(top = Tokens.Space.s2), quiet = true) {
            Cards.share(ctx, Cards.year(ctx, k, title, big, lines), "year_$year")
        }
    }
}

/** 소리 내어 읽은 시간 (U2): 오늘 몇 분 · 이번 이레 막대. */
@Composable
private fun AloudTime(s: AppState) {
    val c = Theme.c; val k = s.korean
    val today = s.today().toEpochDay()
    val week = (6 downTo 0).map { today - it }.map { s.aloudLog[it] ?: 0 }
    val max = week.maxOrNull()?.coerceAtLeast(60) ?: 60
    val names = stringResource(R.string.weekdays)
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Text(stringResource(R.string.minutes_n, (week.last() + 30) / 60), style = Theme.title(k, Tokens.Text.display), maxLines = 1)
            Text(stringResource(R.string.aloud_today), style = Theme.small(), maxLines = 1, modifier = Modifier.padding(bottom = Tokens.Space.s2))
        }
        Row(Modifier.fillMaxWidth().height(Tokens.Size.aloudBars), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2), verticalAlignment = Alignment.Bottom) {
            week.forEachIndexed { i, secs ->
                Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight((secs.toFloat() / max).coerceIn(0.03f, 1f) * Tokens.Ratio.barMax).clip(RoundedCornerShape(Tokens.Radius.chip))
                        .background(if (i == 6) c.rubric else c.rubric.copy(alpha = Tokens.Alpha.rest)))
                    val d = java.time.LocalDate.ofEpochDay(today - 6 + i)
                    Text(names[d.dayOfWeek.value % 7].toString(), style = Theme.small(), maxLines = 1)
                }
            }
        }
        Text(stringResource(R.string.aloud_week, (week.sum() + 30) / 60), style = Theme.small())
    }
}
