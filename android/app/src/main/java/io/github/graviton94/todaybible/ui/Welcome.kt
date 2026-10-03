package io.github.graviton94.todaybible.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Goal
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 여는 순간 (F1): 가죽 표지에 금박 테가 그어지고 · 제목에 빛이 한 번 스친 뒤 · 표지가 왼쪽을 축으로 넘어가며
 * 속표지 (제목 · 금선 · 날짜 · 오늘 쓸 첫 절)가 드러나고 · 조용히 앱으로. 누르면 바로 끝.
 */
@Composable
fun Opening(s: AppState, onDone: () -> Unit) {
    val c = Theme.c; val k = s.korean
    val draw = remember { Animatable(0f) }     // 금박 테 그리기
    val shine = remember { Animatable(0f) }    // 제목 위 빛
    val turn = remember { Animatable(0f) }     // 표지 넘김 0 → 1
    val fade = remember { Animatable(1f) }     // 속표지에서 앱으로
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        draw.animateTo(1f, tween(Tokens.Motion.openDrawMs, easing = FastOutSlowInEasing))
        shine.animateTo(1f, tween(Tokens.Motion.openShineMs, easing = LinearEasing))
        turn.animateTo(1f, tween(Tokens.Motion.veilMs, easing = FastOutSlowInEasing))
        delay(Tokens.Motion.openHoldMs.toLong())
        fade.animateTo(0f, tween(Tokens.Motion.fadeMs * 2))
        onDone()
    }
    val t = s.text(); val v = s.progress.nextVerse(s.translation, t, s.chapter) ?: 1
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }.clickable(remember { MutableInteractionSource() }, null) {
        scope.launch { onDone() }
    }) {
        // 속표지
        Column(Modifier.fillMaxSize().background(c.leaf).systemBarsPadding().padding(Tokens.Space.s6), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4, Alignment.CenterVertically)) {
            Text(stringResource(R.string.app_name), style = Theme.title(k, Tokens.Text.display))
            Box(Modifier.width(Tokens.Size.coverW).heightIn(min = Tokens.Size.bandGap * 2).drawBehind {
                drawLine(c.gilt, Offset(0f, 0f), Offset(size.width, 0f), Tokens.Stroke.giltFine.toPx())
                drawLine(c.gilt, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.giltFine.toPx())
            })
            Text(s.today().format(DateTimeFormatter.ofPattern(if (k) "yyyy년 M월 d일 EEEE" else "EEEE, d MMMM yyyy", if (k) Locale.KOREAN else Locale.ENGLISH)), style = Theme.small())
            Text(Markup.plain(t.verse(s.chapter, v)), style = Theme.body().copy(textAlign = TextAlign.Center), maxLines = 4, overflow = TextOverflow.Ellipsis)
            Text("${s.bookName()} ${s.chapter}:$v", style = Theme.small().copy(color = c.rubric))
        }
        // 표지: 왼쪽 가장자리를 축으로 넘어감. 넘어가는 동안 오른쪽에 책장 두께
        Box(Modifier.fillMaxSize().graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0.5f); cameraDistance = Tokens.Motion.openCamera * density
            rotationY = -Tokens.Motion.openAngle * turn.value; alpha = if (turn.value > 0.85f) (1f - turn.value) / 0.15f else 1f
        }.background(c.leather).drawBehind {
            val e = Tokens.Size.veilEdge.toPx() * 1.6f; val g = Tokens.Size.veilBandGap.toPx(); val w = Tokens.Stroke.gilt.toPx()
            val p = draw.value
            // 위아래 금선 두 줄: 가운데서 양옆으로 그어짐
            val half = (size.width - 2 * e) / 2 * p; val cx = size.width / 2
            for (y in listOf(e, e + g, size.height - e, size.height - e - g)) drawLine(c.gilt, Offset(cx - half, y), Offset(cx + half, y), w)
            // 안쪽 금박 테: 나중에 나타남
            val inset = size.width * Tokens.Ratio.veilInset + e
            drawRoundRect(c.gilt.copy(alpha = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)), Offset(inset, inset + g * 2), Size(size.width - inset * 2, size.height - (inset + g * 2) * 2),
                CornerRadius(Tokens.Radius.frame.toPx()), style = Stroke(Tokens.Stroke.giltFine.toPx()))
            // 네 귀퉁이 장식 (작은 ✠)
            val m = Tokens.Size.iconSm.toPx(); val o = inset + m
            if (p > 0.6f) listOf(Offset(o, o + g * 2), Offset(size.width - o, o + g * 2), Offset(o, size.height - o - g * 2), Offset(size.width - o, size.height - o - g * 2))
                .forEach { stamp(STAMP_CROSS, c.gilt.copy(alpha = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)), it, m) }
        }.drawWithContent {
            drawContent()
            // 제목 위로 빛 한 줄이 스침 (평평한 띠, 그라데이션 없음)
            if (shine.value in 0.001f..0.999f) {
                val bw = size.width * Tokens.Ratio.shineWidth
                val x = -bw + (size.width + bw) * shine.value
                drawRect(c.leatherInk.copy(alpha = Tokens.Alpha.shine), Offset(x, size.height * 0.36f), Size(bw, size.height * 0.28f))
            }
        }, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
                StampMark(STAMP_CROSS, c.gilt.copy(alpha = draw.value), Modifier.size(Tokens.Size.emblem))
                Text(stringResource(R.string.app_name), style = Theme.title(k, Tokens.Text.display).copy(color = c.gilt.copy(alpha = draw.value)))
            }
        }
    }
}

/**
 * 처음 한 번 (소개 세 장 → 오늘의 분량 → 시작할 곳 → 아침 알림). 문장은 한두 줄.
 */
@Composable
fun Welcome(s: AppState) {
    val c = Theme.c; val k = s.korean
    var start by remember { mutableIntStateOf(0) }
    val starts = listOf(Triple(0, 1, R.string.start_gen_note), Triple(40, 1, R.string.start_mark_note), Triple(18, 23, R.string.start_ps_note))
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) s.setReminder(7); finish(s, starts[start]) }
    Column(Modifier.fillMaxSize().background(c.leaf).systemBarsPadding().padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s5),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
        AnimatedContent(s.welcomeStep, transitionSpec = { fadeIn(tween(Tokens.Motion.fadeMs)) togetherWith fadeOut(tween(Tokens.Motion.fadeMs)) }, label = "welcome", modifier = Modifier.weight(1f)) { i ->
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
                when (i) {
                    0, 1, 2 -> {
                        Box(Modifier.fillMaxWidth().aspectRatio(Tokens.Ratio.welcomeArt).clip(RoundedCornerShape(Tokens.Radius.card)).background(c.paper), contentAlignment = Alignment.Center) { WelcomeArt(s, i) }
                        Text(stringResource(listOf(R.string.ob1_h, R.string.ob2_h, R.string.ob3_h)[i]), style = Theme.title(k, Tokens.Text.title))
                        Text(if (i == 0) stringResource(R.string.ob1_p, "%,d".format(s.translation.total)) else stringResource(listOf(R.string.ob1_p, R.string.ob2_p, R.string.ob3_p)[i]), style = Theme.body().copy(color = c.inkSoft))
                    }
                    3 -> GoalChooser(s, title = true)
                    4 -> {
                        Text(stringResource(R.string.ob_start), style = Theme.title(k, Tokens.Text.title))
                        starts.forEachIndexed { j, (b, ch, note) ->
                            ChoiceCard(if (b == 18) (if (k) "${s.bookName(b)} ${ch}편" else "${s.bookName(b)} $ch") else (if (k) "${s.bookName(b)} ${ch}장" else "${s.bookName(b)} $ch"), stringResource(note), j == start) { start = j }
                        }
                    }
                    else -> {
                        Box(Modifier.fillMaxWidth().aspectRatio(Tokens.Ratio.welcomeArt).clip(RoundedCornerShape(Tokens.Radius.card)).background(c.paper), contentAlignment = Alignment.Center) { WelcomeArt(s, 5) }
                        Text(stringResource(R.string.ob_reminder), style = Theme.title(k, Tokens.Text.title))
                    }
                }
            }
        }
        // 점 여섯
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2, Alignment.CenterHorizontally)) {
            for (i in 0..5) Box(Modifier.size(Tokens.Size.dot).clip(CircleShape).background(if (i == s.welcomeStep) c.rubric else c.hair))
        }
        if (s.welcomeStep < 5) BookButton(stringResource(if (s.welcomeStep == 4) R.string.begin else R.string.next), Modifier.fillMaxWidth()) { s.welcomeStep++ }
        else Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            BookButton(stringResource(R.string.not_now), Modifier.weight(1f), quiet = true) { finish(s, starts[start]) }
            BookButton(stringResource(R.string.yes_please), Modifier.weight(1f)) {
                if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else { s.setReminder(7); finish(s, starts[start]) }
            }
        }
    }
}

private fun finish(s: AppState, start: Triple<Int, Int, Int>) = s.finishOnboarding(start.first, start.second)

/** 오늘의 분량 고르기 (처음 소개 · 설정 공통). 고른 분량이면 얼마나 걸리는지 한 줄. */
@Composable
fun GoalChooser(s: AppState, title: Boolean) {
    val c = Theme.c; val k = s.korean
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        if (title) {
            Text(stringResource(R.string.goal_title), style = Theme.title(k, Tokens.Text.title))
            Text(stringResource(R.string.goal_hint), style = Theme.small())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Goal.choices.forEach { g ->
                val on = g == s.dailyGoal
                Column(Modifier.weight(1f).heightIn(min = Tokens.Size.touch).clip(RoundedCornerShape(Tokens.Radius.button)).background(if (on) c.leather else c.paper)
                    .clickable(role = Role.RadioButton) { s.setGoal(g) }.padding(vertical = Tokens.Space.s2),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(if (g == Goal.CHAPTER) "1" else "$g", style = Theme.title(k).copy(color = if (on) c.leatherInk else c.ink), maxLines = 1)
                    Text(stringResource(if (g == Goal.CHAPTER) R.string.unit_chapter else R.string.unit_verses), style = Theme.small().copy(color = if (on) c.leatherInk else c.inkSoft), maxLines = 1)
                }
            }
        }
        val mark = s.store.book(s.translation, 40)
        Text(stringResource(R.string.goal_estimate,
            duration(s, Goal.days(s.dailyGoal, mark.fillableTotal, mark.chapterCount)),
            duration(s, Goal.days(s.dailyGoal, s.translation.total, io.github.graviton94.todaybible.core.Canon.books.sumOf { it.chapters }))), style = Theme.small().copy(color = c.inkSoft))
    }
}

@Composable
private fun duration(s: AppState, days: Int): String = when {
    days < 60 -> stringResource(R.string.dur_days, days)
    days < 730 -> stringResource(R.string.dur_months, (days + 15) / 30)
    else -> stringResource(R.string.dur_years, (days + 182) / 365)
}

@Composable
private fun ChoiceCard(title: String, note: String, on: Boolean, onClick: () -> Unit) {
    val c = Theme.c
    Row(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.touch).clip(RoundedCornerShape(Tokens.Radius.button)).background(if (on) c.paper else c.leaf)
        .drawBehind {
            drawRoundRect(if (on) c.rubric else c.hair, style = Stroke(Tokens.Stroke.hair.toPx()), cornerRadius = CornerRadius(Tokens.Radius.button.toPx()))
            if (on) drawRect(c.rubric, Offset.Zero, Size(Tokens.Stroke.rule.toPx() * 2, size.height))
        }.clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = Tokens.Space.s4),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = Theme.body(), maxLines = 1, modifier = Modifier.weight(1f))
        Text(note, style = Theme.small(), maxLines = 1)
    }
}

/** 소개 그림: 0 책장 · 1 흐린 글자 위에 먹 · 2 조각 판화 · 5 아침 알림. */
@Composable
private fun WelcomeArt(s: AppState, i: Int) {
    val c = Theme.c; val k = s.korean
    when (i) {
        0 -> Canvas(Modifier.fillMaxSize().padding(Tokens.Space.s5)) {
            val n = 14; val gap = Tokens.Size.spineGap.toPx(); val w = (size.width - gap * (n - 1)) / n
            for (j in 0 until n) {
                val h = size.height * (0.55f + 0.4f * ((j * 37) % 11) / 10f); val x = j * (w + gap)
                if (j < 4) {
                    drawRect(c.leather, Offset(x, size.height - h), Size(w, h))
                    drawRect(c.gilt, Offset(x + w * 0.18f, size.height - h + h * 0.12f), Size(w * 0.64f, Tokens.Stroke.gilt.toPx()))
                } else drawRect(c.hair, Offset(x, size.height - h), Size(w, h), style = Stroke(Tokens.Stroke.hair.toPx()))
            }
        }
        1 -> {
            val t = s.store.book(s.translation, 0).verse(1, 1)
            val plain = Markup.plain(t); val cut = plain.length / 2
            Text(androidx.compose.ui.text.buildAnnotatedString {
                pushStyle(androidx.compose.ui.text.SpanStyle(color = c.ink)); append(plain.substring(0, cut)); pop()
                pushStyle(androidx.compose.ui.text.SpanStyle(color = c.unwritten, textDecoration = androidx.compose.ui.text.style.TextDecoration.None)); append(plain.substring(cut)); pop()
            }, style = Theme.verse(k), modifier = Modifier.padding(Tokens.Space.s5))
        }
        2 -> {
            val img = rememberPlate("noah"); val shown = remember { io.github.graviton94.todaybible.core.Pieces.order("noah".hashCode()).take(7).toSet() }
            Box(Modifier.fillMaxSize().padding(Tokens.Space.s4).aspectRatio(Tokens.Ratio.plateAspect).drawWithContent {
                drawContent()
                val cols = io.github.graviton94.todaybible.core.Pieces.COLS; val rows = io.github.graviton94.todaybible.core.Pieces.ROWS
                val w = size.width / cols; val h = size.height / rows
                for (j in 0 until cols * rows) if (j !in shown) drawRect(c.paper, Offset((j % cols) * w, (j / cols) * h), Size(w + 1f, h + 1f))
            }) { if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        else -> Column(Modifier.padding(Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Text("7:00", style = Theme.title(k, Tokens.Text.display))
            Row(Modifier.clip(RoundedCornerShape(Tokens.Radius.card)).background(c.leaf).padding(Tokens.Space.s3), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Box(Modifier.size(Tokens.Size.iconMd).clip(CircleShape).background(c.leather), contentAlignment = Alignment.Center) { StampMark(STAMP_CROSS, c.gilt, Modifier.size(Tokens.Size.lock)) }
                val t = s.store.book(s.translation, 18)
                Column { Text(if (k) "${s.bookName(18)} 23:1" else "${s.bookName(18)} 23:1", style = Theme.small().copy(color = c.ink)); Text(Markup.plain(t.verse(23, 1)), style = Theme.small(), maxLines = 2) }
            }
        }
    }
}
