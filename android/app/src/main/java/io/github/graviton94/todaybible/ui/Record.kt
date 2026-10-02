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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
        PresenceMonth(s, days)
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
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
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
            if (today) drawRoundRect(c.ink, style = Stroke(Tokens.Stroke.hair.toPx()), cornerRadius = CornerRadius(Tokens.Radius.button.toPx()))
        },
        contentAlignment = Alignment.Center,
    ) {
        if (present) StampMark(s.stamp, c.rubric, Modifier.fillMaxSize(0.62f))
        else Text("${d.dayOfMonth}", style = Theme.small().copy(color = when {
            future -> c.hair.copy(alpha = 0.6f)
            Presence.isRest(d) -> c.rubric.copy(alpha = 0.75f)
            else -> c.unwritten
        }), maxLines = 1)
    }
}

/** 판화가 있는 장의 완성도 (0..1). */
private fun plateFraction(s: AppState, p: Plate): Float = s.progress.chapterFraction(s.translation, s.text(p.book), p.chapter)

/** 화첩: 판화마다 그 장을 쓴 만큼 조각이 드러남 (3×4 = 12조각). 누르면 그 장으로. */
@Composable
private fun PlateGallery(s: AppState) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
        s.store.plates.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
                row.forEach { p -> PlateCard(s, p, Modifier.weight(1f)) }
                if (row.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun rememberPlate(id: String): ImageBitmap? {
    val ctx = LocalContext.current
    return remember(id) { runCatching { ctx.assets.open("plates/$id.jpg").use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull() }
}

@Composable
private fun PlateCard(s: AppState, p: Plate, modifier: Modifier) {
    val c = Theme.c; val k = s.korean
    val f = plateFraction(s, p); val n = Pieces.revealed(f)
    val order = remember(p.id) { Pieces.order(p.id.hashCode()) }
    val shown = order.take(n).toSet()
    val img = rememberPlate(p.id)
    Column(modifier.clickable(role = Role.Button) { s.open(p.book, p.chapter) }, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(Tokens.Ratio.plateAspect).clip(RoundedCornerShape(Tokens.Radius.button)).background(c.paper)
                .drawWithContent {
                    drawContent()
                    // 아직 드러나지 않은 조각은 종이로 덮고, 조각 사이엔 머리카락 같은 줄
                    val w = size.width / Pieces.COLS; val h = size.height / Pieces.ROWS
                    for (i in 0 until Pieces.COUNT) if (i !in shown) drawRect(c.paper, Offset((i % Pieces.COLS) * w, (i / Pieces.COLS) * h), Size(w + 0.5f, h + 0.5f))
                    if (n in 1 until Pieces.COUNT) {
                        for (x in 1 until Pieces.COLS) drawLine(c.hair, Offset(x * w, 0f), Offset(x * w, size.height), Tokens.Stroke.hair.toPx())
                        for (y in 1 until Pieces.ROWS) drawLine(c.hair, Offset(0f, y * h), Offset(size.width, y * h), Tokens.Stroke.hair.toPx())
                    }
                    if (n == 0) drawRoundRect(c.hair, style = Stroke(Tokens.Stroke.hair.toPx()), cornerRadius = CornerRadius(Tokens.Radius.button.toPx()))
                },
            contentAlignment = Alignment.Center,
        ) {
            if (img != null) Image(img, if (k) p.ko else p.en, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (n == 0) Canvas(Modifier.size(22.dp)) { stamp(STAMP_CROSS, c.unwritten.copy(alpha = 0.5f)) }
        }
        Text(if (k) p.ko else p.en, style = Theme.label(), minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (k) "${s.bookName(p.book)} ${p.chapter}장" else "${s.bookName(p.book)} ${p.chapter}", style = Theme.small(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("$n/${Pieces.COUNT}", style = Theme.small().copy(color = if (n == Pieces.COUNT) c.giltText else c.inkSoft), maxLines = 1)
        }
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
                Modifier.fillMaxWidth().heightIn(min = 64.dp).drawBehind { drawLine(c.hair, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.hair.toPx()) }
                    .padding(vertical = Tokens.Space.s2),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
            ) {
                Canvas(Modifier.size(44.dp)) { medal(m, day != null, c.leather, c.gilt, c.unwritten.copy(alpha = 0.55f)) }
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
