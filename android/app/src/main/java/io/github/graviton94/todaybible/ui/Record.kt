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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
    val k = s.korean; val c = Theme.c
    val days = s.progress.days()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s5)) {
        RunningHead(stringResource(R.string.page_record), stringResource(R.string.presence_n, Presence.total(days)), k)
        yearShown(s)?.let { YearCard(s, it) }
        CalendarPanel(s, days)
        Stats(s)
        // 소리 내어 읽은 시간: 한 줄
        if (s.aloudLog.isNotEmpty()) {
            val today = s.today().toEpochDay()
            val week = (0..6).sumOf { s.aloudLog[today - it] ?: 0 }
            Text(stringResource(R.string.aloud_line, ((s.aloudLog[today] ?: 0) + 30) / 60, (week + 30) / 60), style = Theme.body().copy(color = c.inkSoft))
        }
        // 화첩: 쓰는 중 · 다 모은 것부터 여섯 점, 펼치면 모두
        var allPlates by remember { mutableStateOf(false) }
        val plates = remember(s.fills.size) { s.store.plates.sortedWith(compareByDescending<Plate> { plateFraction(s, it).let { f -> if (f > 0f && f < 1f) 2 else if (f >= 1f) 1 else 0 } }.thenByDescending { plateFraction(s, it) }) }
        Section(stringResource(R.string.plates), "${s.store.plates.count { plateFraction(s, it) >= 1f }} / ${s.store.plates.size}", k)
        PlateGallery(s, if (allPlates) plates else plates.take(PLATES_SHOWN))
        if (plates.size > PLATES_SHOWN) BookButton(stringResource(if (allPlates) R.string.fold else R.string.unfold_all, plates.size), Modifier.fillMaxWidth(), quiet = true) { allPlates = !allPlates }
        // 발자취: 받은 것 최근 넷 (없으면 처음 넷), 펼치면 모두
        var allMiles by remember { mutableStateOf(false) }
        val miles = Milestone.entries.sortedByDescending { s.earned[it] ?: Long.MIN_VALUE }
        Section(stringResource(R.string.milestones), "${s.earned.size} / ${Milestone.entries.size}", k)
        MilestoneList(s, if (allMiles) miles else miles.take(MILES_SHOWN))
        BookButton(stringResource(if (allMiles) R.string.fold else R.string.unfold_all, miles.size), Modifier.fillMaxWidth(), quiet = true) { allMiles = !allMiles }
        if (s.marks.isNotEmpty()) {
            Section(stringResource(R.string.marks), "${s.marks.count { it.translation == s.translation }}", k)
            MarkList(s)
        }
    }
}

@Composable
private fun Section(title: String, right: String, korean: Boolean) {
    Row(Modifier.fillMaxWidth().padding(top = Tokens.Space.s2), verticalAlignment = Alignment.Bottom) {
        Text(title, style = Theme.title(korean, Tokens.Text.title), maxLines = 1, modifier = Modifier.weight(1f))
        Text(right, style = Theme.small(), maxLines = 1)
    }
}

/**
 * 달력 하나 (하루의 정원처럼): 위에 [월 · 해].
 * 월: ‹ › 로 달을 넘기고, 쓴 날은 도장. 날을 누르면 그날 쓴 절.
 * 해: 작은 달력 열두 개. 누르면 그 달로.
 */
@Composable
private fun CalendarPanel(s: AppState, days: Set<Long>) {
    val c = Theme.c; val k = s.korean
    val today = s.today()
    val first = days.minOrNull()?.let { YearMonth.from(LocalDate.ofEpochDay(it)) } ?: YearMonth.from(today)
    var yearView by remember { mutableStateOf(false) }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var picked by remember { mutableStateOf<LocalDate?>(null) }
    val loc = if (k) java.util.Locale.KOREAN else java.util.Locale.ENGLISH
    Column(Modifier.coach("rec_calendar"), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Row(Modifier.fillMaxWidth().drawBehind { drawLine(c.hair, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.hair.toPx()) }) {
            listOf(false to R.string.cal_month, true to R.string.cal_year).forEach { (y, id) ->
                val on = yearView == y
                Box(Modifier.weight(1f).heightIn(min = Tokens.Size.tab).clickable(role = Role.Tab) { yearView = y }.drawBehind {
                    if (on) drawRect(c.rubric, Offset(0f, size.height - Tokens.Stroke.rule.toPx()), androidx.compose.ui.geometry.Size(size.width, Tokens.Stroke.rule.toPx()))
                }, contentAlignment = Alignment.Center) { Text(stringResource(id), style = Theme.label().copy(color = if (on) c.ink else c.inkSoft)) }
            }
        }
        // ‹ 2026년 10월 ›  /  ‹ 2026년 ›
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val canBack = if (yearView) month.year > first.year else month > first
            val canNext = if (yearView) month.year < today.year else month < YearMonth.from(today)
            CalArrow("‹", canBack) { month = if (yearView) month.minusYears(1) else month.minusMonths(1); picked = null }
            Text(if (yearView) stringResource(R.string.cal_year_n, month.year) else fmtDate(R.string.fmt_month, month.atDay(1)),
                style = Theme.head(k), textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1)
            CalArrow("›", canNext) { month = if (yearView) month.plusYears(1).let { if (it > YearMonth.from(today)) YearMonth.from(today) else it } else month.plusMonths(1); picked = null }
        }
        if (!yearView) {
            MonthGrid(s, month, days, today, picked) { d -> picked = if (picked == d) null else d }
            Text(stringResource(R.string.weeks_n, Presence.weeksComplete(days)), style = Theme.small())
            picked?.let { d -> DayVerses(s, d) }
        } else {
            (1..12).chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    row.forEach { m ->
                        val ym = YearMonth.of(month.year, m)
                        val future = ym > YearMonth.from(today)
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(Tokens.Radius.chip)).background(c.paper).clickable(enabled = !future, role = Role.Button) { month = ym; yearView = false; picked = null }
                            .padding(Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                            val n = (1..ym.lengthOfMonth()).count { ym.atDay(it).toEpochDay() in days }
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(stringResource(R.string.cal_month_n, m), style = Theme.small().copy(color = if (future) c.hair else c.ink), modifier = Modifier.weight(1f), maxLines = 1)
                                if (n > 0) Text("$n", style = Theme.small().copy(color = c.rubric), maxLines = 1)
                            }
                            MiniMonth(ym, days, future)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalArrow(t: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Theme.c
    val label = stringResource(if (t == "‹") R.string.cal_prev else R.string.cal_next)
    Box(Modifier.size(Tokens.Size.touch).semantics(mergeDescendants = true) { contentDescription = label }.clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(t, style = Theme.title(true).copy(color = if (enabled) c.ink else c.hair))
    }
}

@Composable
private fun MonthGrid(s: AppState, month: YearMonth, days: Set<Long>, today: LocalDate, picked: LocalDate?, onPick: (LocalDate) -> Unit) {
    val c = Theme.c
    val lead = month.atDay(1).dayOfWeek.value % 7
    val cells = (0 until lead).map { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    val names = stringResource(R.string.weekdays)
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
        Row(Modifier.fillMaxWidth()) {
            names.forEachIndexed { i, ch -> Text(ch.toString(), style = Theme.small().copy(color = if (i == 0) c.rubric else c.inkSoft, textAlign = TextAlign.Center), modifier = Modifier.weight(1f)) }
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { d ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(Tokens.Size.spineGap)
                        .then(if (d != null && d.toEpochDay() in days) Modifier.clickable(role = Role.Button) { onPick(d) } else Modifier)
                        .drawBehind { if (d != null && d == picked) drawRoundRect(c.mark, cornerRadius = CornerRadius(Tokens.Radius.chip.toPx())) },
                        contentAlignment = Alignment.Center) {
                        if (d != null) DayCell(s, d, d.toEpochDay() in days, d == today, d.isAfter(today))
                    }
                }
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** 해 보기의 작은 달력: 쓴 날만 붉은 점. */
@Composable
private fun MiniMonth(ym: YearMonth, days: Set<Long>, future: Boolean) {
    val c = Theme.c
    val lead = ym.atDay(1).dayOfWeek.value % 7
    Canvas(Modifier.fillMaxWidth().aspectRatio(7f / 6f)) {
        val w = size.width / 7; val h = size.height / 6
        for (d in 1..ym.lengthOfMonth()) {
            val i = lead + d - 1
            val on = ym.atDay(d).toEpochDay() in days
            val cx = (i % 7) * w + w / 2; val cy = (i / 7) * h + h / 2
            drawCircle(if (on) c.rubric else if (future) c.hair.copy(alpha = Tokens.Alpha.future) else c.hair, (if (on) 0.36f else 0.16f) * minOf(w, h), Offset(cx, cy))
        }
    }
}

/** 고른 날 쓴 절: 장마다 한 줄. 누르면 그 장을 읽기로. */
@Composable
private fun DayVerses(s: AppState, d: LocalDate) {
    val c = Theme.c
    val e = d.toEpochDay()
    val byCh = s.fills.filter { it.translation == s.translation && it.epochDay == e }.groupBy { it.key.book to it.key.chapter }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.card)).background(c.paper).padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Text(fmtDate(R.string.fmt_md, d), style = Theme.label())
        byCh.forEach { (bc, f) ->
            val vs = f.map { it.key.verse }.sorted()
            Text(stringResource(R.string.day_line, s.bookName(bc.first), bc.second, if (vs.size > 1) "${vs.first()}–${vs.last()}" else "${vs.first()}", vs.size),
                style = Theme.body(), modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { s.read(bc.first, bc.second, vs.first()) })
        }
    }
}

private const val PLATES_SHOWN = 6
private const val MILES_SHOWN = 4

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
private fun PlateGallery(s: AppState, plates: List<Plate>) {
    Column(Modifier.coach("rec_plates"), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
        plates.chunked(3).forEach { row ->
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
    // 그림 읽기는 뒤에서 (화첩을 넘길 때 멈칫하지 않게)
    return androidx.compose.runtime.produceState<ImageBitmap?>(null, id, small) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { ctx.assets.open("plates/$id.jpg").use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = if (small) 4 else 2 })?.asImageBitmap() } }.getOrNull()
        }
    }.value
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
            if (img != null) Image(img, s.plateName(p), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (n == 0) Canvas(Modifier.size(Tokens.Size.icon)) { stamp(STAMP_CROSS, c.unwritten.copy(alpha = Tokens.Alpha.faint)) }
        }
        // 세 줄로 좁으니 제목 한 줄 · 조각 수 한 줄 (권 · 장은 누르면 크게 보는 화면에)
        Text(s.plateName(p), style = Theme.small().copy(color = c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$n/${Pieces.COUNT}", style = Theme.small().copy(color = if (n == Pieces.COUNT) c.giltText else c.inkSoft), maxLines = 1)
    }
}

/** 발자취: 한 줄 이름 + 한 줄 조건. 얻은 것은 가죽 메달, 아직은 빈 테. */
@Composable
private fun MilestoneList(s: AppState, list: List<Milestone>) {
    val c = Theme.c; val ctx = LocalContext.current
    val fmt = DateTimeFormatter.ofPattern(androidx.compose.ui.res.stringResource(R.string.fmt_ymd), java.util.Locale.getDefault())
    Column(Modifier.coach("rec_miles")) {
        list.forEach { m ->
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
        (hour?.let { fmtDate(R.string.fmt_hour, java.time.LocalTime.of(it, 0)) } ?: "–") to stringResource(R.string.stat_hour),
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


private const val MARKS_SHOWN = 5

/** 형광펜 그은 절 (N): 최근 것부터. 누르면 그 절로. */
@Composable
private fun MarkList(s: AppState) {
    val c = Theme.c; val k = s.korean
    var all by remember { mutableStateOf(false) }
    val list = s.marks.filter { it.translation == s.translation }.sortedByDescending { it.epochDay }
    val fmt = java.time.format.DateTimeFormatter.ofPattern(androidx.compose.ui.res.stringResource(R.string.fmt_md), java.util.Locale.getDefault())
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        (if (all) list else list.take(MARKS_SHOWN)).forEach { m ->
            val v = m.key
            Column(Modifier.fillMaxWidth().clickable(role = androidx.compose.ui.semantics.Role.Button) { s.open(v.book, v.chapter); s.target = v.verse; s.page = AppState.COPY }
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
    val fmt = java.time.format.DateTimeFormatter.ofPattern(androidx.compose.ui.res.stringResource(R.string.fmt_md), java.util.Locale.getDefault())
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
        // 가죽 위의 금박 테 버튼
        Box(Modifier.fillMaxWidth().padding(top = Tokens.Space.s2).heightIn(min = Tokens.Size.touch).clip(RoundedCornerShape(Tokens.Radius.button))
            .drawBehind { giltFrame(c.gilt, bands = false) }.clickable(role = androidx.compose.ui.semantics.Role.Button) { Cards.share(s, ctx, Cards.year(ctx, k, title, big, lines), "year_$year") },
            contentAlignment = Alignment.Center) { Text(stringResource(R.string.year_share), style = Theme.label().copy(color = c.leatherInk)) }
    }
}

