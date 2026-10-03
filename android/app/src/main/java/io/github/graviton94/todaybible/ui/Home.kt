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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * 오늘 (첫 화면, B1): 오늘의 분량 고리 · 이어 쓸 한 절 · 이번 주 도장 · 다음 판화까지 남은 절 · 다가오는 교회력.
 * 들어오자마자 할 일 하나와 받을 것이 보이게.
 */
@Composable
fun HomePage(s: AppState) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    val today = s.today(); val days = s.progress.days()
    val run = Presence.streak(days, today)
    val verses = s.todayVerses(); val met = s.goalMet()
    val t = s.text(); val next = s.progress.nextVerse(s.translation, t, s.chapter)
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s5)) {
        RunningHead(today.format(DateTimeFormatter.ofPattern(if (k) "M월 d일 EEEE" else "EEEE, d MMMM", if (k) Locale.KOREAN else Locale.ENGLISH)),
            if (run > 0) stringResource(R.string.day_n, run) else "", k)
        // 오늘의 분량
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
            val goal = s.dailyGoal
            val frac = if (goal == Goal.CHAPTER) (if (met) 1f else 0f) else (verses.toFloat() / goal).coerceIn(0f, 1f)
            Box(Modifier.size(Tokens.Size.medalLg), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = Tokens.Stroke.rule.toPx() * 2; val inset = w / 2
                    drawArc(c.hair, 0f, 360f, false, Offset(inset, inset), Size(size.width - w, size.height - w), style = Stroke(w))
                    drawArc(if (met) c.gilt else c.rubric, -90f, 360f * frac, false, Offset(inset, inset), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                }
                if (met) StampMark(s.stamp, c.rubric, Modifier.size(Tokens.Size.iconMd))
                else Text(if (goal == Goal.CHAPTER) "–" else "$verses/$goal", style = Theme.label(), maxLines = 1)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Text(if (goal == Goal.CHAPTER) stringResource(R.string.goal_chapter) else stringResource(R.string.goal_verses, goal), style = Theme.title(k), maxLines = 1)
                Text(when {
                    met -> stringResource(R.string.goal_done)
                    goal == Goal.CHAPTER -> stringResource(R.string.goal_left_chapter)
                    else -> stringResource(R.string.goal_left, goal - verses)
                }, style = Theme.small().copy(color = if (met) c.giltText else c.inkSoft), maxLines = 2)
            }
        }
        // 이어 쓸 한 절
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.card)).background(c.paper).padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            val v = next ?: t.fillable(s.chapter).first()
            Text(stringResource(R.string.continue_ref, "${s.bookName()} ${s.chapter}:$v"), style = Theme.small().copy(color = c.rubric), maxLines = 1)
            Text(Markup.plain(t.verse(s.chapter, v)), style = Theme.body().copy(color = c.inkSoft), maxLines = 3, overflow = TextOverflow.Ellipsis)
            BookButton(stringResource(R.string.continue), Modifier.fillMaxWidth()) { s.open(s.book, s.chapter) }
        }
        // 이번 주 도장 (주일부터)
        WeekStamps(s)
        // 다음 판화
        s.nextPlate()?.let { (pl, left) ->
            val f = s.progress.chapterFraction(s.translation, s.store.book(s.translation, pl.book), pl.chapter)
            val n = Pieces.revealed(f); val order = remember(pl.id) { Pieces.order(pl.id.hashCode()) }.take(n).toSet()
            val img = rememberPlate(pl.id)
            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { s.plateView = pl }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Box(Modifier.width(Tokens.Size.initialHome).aspectRatio(Tokens.Ratio.plateAspect).clip(RoundedCornerShape(Tokens.Radius.frame)).background(c.paper).drawWithContent {
                    drawContent()
                    val w = size.width / Pieces.COLS; val h = size.height / Pieces.ROWS
                    for (i in 0 until Pieces.COUNT) if (i !in order) drawRect(c.paper, Offset((i % Pieces.COLS) * w, (i / Pieces.COLS) * h), Size(w + 1f, h + 1f))
                }) { if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.next_plate, if (k) pl.ko else pl.en, left), style = Theme.label(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.plate_where, if (k) "${s.bookName(pl.book)} ${pl.chapter}장" else "${s.bookName(pl.book)} ${pl.chapter}", n, Pieces.COUNT), style = Theme.small(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // 교회력: 오늘이면 발자취 안내, 아니면 가장 가까운 날
        Feasts.upcoming(today, korea = k, count = 1).firstOrNull()?.let { (f, d) ->
            val name = ctx.getString(ctx.resources.getIdentifier("feast_${f.name}", "string", ctx.packageName))
            val daysLeft = ChronoUnit.DAYS.between(today, d).toInt()
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.card)).background(c.leather)
                .drawWithContent { drawContent(); giltFrame(c.gilt.copy(alpha = Tokens.Alpha.frame), bands = false) }
                .padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                if (daysLeft == 0) {
                    Text(stringResource(R.string.feast_today, name), style = Theme.title(k).copy(color = c.leatherInk), maxLines = 1)
                    Text(stringResource(R.string.feast_reward, milestoneName(ctx, f.milestone)), style = Theme.small().copy(color = c.leatherInk), maxLines = 2)
                } else {
                    Text(stringResource(R.string.feast_soon, name, daysLeft), style = Theme.label().copy(color = c.leatherInk), maxLines = 1)
                    Text(stringResource(R.string.feast_reward_on, milestoneName(ctx, f.milestone)), style = Theme.small().copy(color = c.leatherInk), maxLines = 2)
                }
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
