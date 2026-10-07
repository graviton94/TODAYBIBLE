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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.text.withStyle
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
import androidx.compose.ui.unit.em
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Goal
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


/**
 * 처음 한 번: 소개 → 첫 절 소리 내어 읽기 (설치하고 두 번째 화면에서 바로) → 부를 이름 · 분량 · 아침 알림 (모두 건너뛰어도 돼요).
 */
@Composable
fun Welcome(s: AppState) {
    val c = Theme.c; val k = s.korean
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = io.github.graviton94.todaybible.data.Backup.read(ctx, uri)
            s.toast = ctx.getString(if (ok) R.string.backup_done else R.string.backup_bad)
            if (ok) (ctx as? android.app.Activity)?.recreate()
        }
    }
    // 처음은 시편 23편: 첫 절을 소리 내어 읽고 시작해요
    fun done() = s.finishOnboarding(18, 23)
    // 소개 다시 보기: 소개 · 첫 절 두 장만, 설정은 그대로
    fun endTour() { s.tour = false; s.welcomeStep = 0 }
    if (s.tour) androidx.activity.compose.BackHandler { endTour() }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) s.setReminder(7); done() }
    var read by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(c.leaf).systemBarsPadding().imePadding().padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s5),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
        AnimatedContent(s.welcomeStep, transitionSpec = { fadeIn(tween(Tokens.Motion.fadeMs)) togetherWith fadeOut(tween(Tokens.Motion.fadeMs)) }, label = "welcome", modifier = Modifier.weight(1f)) { i ->
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
                when (i) {
                    0 -> {
                        Box(Modifier.fillMaxWidth().aspectRatio(Tokens.Ratio.welcomeArt).clip(RoundedCornerShape(Tokens.Radius.card)).background(c.paper), contentAlignment = Alignment.Center) { WelcomeArt(s, 1) }
                        Text(stringResource(R.string.ob1_h), style = Theme.title(k, Tokens.Text.title))
                        Text(stringResource(R.string.ob1_p, "%,d".format(s.translation.total)), style = Theme.body().copy(color = c.inkSoft))
                        Text(stringResource(R.string.ob2_p), style = Theme.body().copy(color = c.inkSoft))
                    }
                    1 -> FirstReading(s) { read = true }
                    else -> {
                        Text(stringResource(R.string.ob_name), style = Theme.title(k, Tokens.Text.title))
                        Text(stringResource(R.string.ob_name_p), style = Theme.body().copy(color = c.inkSoft))
                        NameField(s) { }
                        Text(stringResource(R.string.ob_restore), style = Theme.small().copy(color = c.rubric),
                            modifier = Modifier.heightIn(min = Tokens.Size.touch).wrapContentHeight().clickable(role = Role.Button) { restore.launch(arrayOf("application/zip", "application/octet-stream")) })
                        // 하루 분량 (처음엔 하루 한 장)
                        GoalChooser(s, title = true)
                    }
                }
            }
        }
        // 점 셋
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2, Alignment.CenterHorizontally)) {
            for (i in 0..(if (s.tour) 1 else 2)) Box(Modifier.size(Tokens.Size.dot).clip(CircleShape).background(if (i == s.welcomeStep) c.rubric else c.hair))
        }
        when (s.welcomeStep) {
            0 -> BookButton(stringResource(R.string.next), Modifier.fillMaxWidth()) { s.welcomeStep++ }
            // 첫 절: 읽었으면 다음, 아니면 나중에
            1 -> if (s.tour) BookButton(stringResource(R.string.coach_done), Modifier.fillMaxWidth()) { endTour() }
                else BookButton(stringResource(if (read) R.string.next else R.string.ob_skip_read), Modifier.fillMaxWidth(), quiet = !read) { s.welcomeStep++ }
            // 마지막: 아침 알림을 물어보고 시작
            else -> Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Text(stringResource(R.string.ob_reminder), style = Theme.body())
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    BookButton(stringResource(R.string.not_now), Modifier.weight(1f), quiet = true) { done() }
                    BookButton(stringResource(R.string.yes_please), Modifier.weight(1f)) {
                        if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS) else { s.setReminder(7); done() }
                    }
                }
            }
        }
    }
}

/**
 * 처음 한 번 (가1 · 가2): 시편 23편 1절을 소리 내어 읽어요. 마이크를 왜 쓰는지 먼저 한 줄로 말하고, 허락을 받으면 듣기.
 * 다 읽으면 도장과 함께 첫 절이 채워져요. 마이크를 쓰지 않으면 ‘다 읽었어요’로.
 */
@Composable
private fun FirstReading(s: AppState, onRead: () -> Unit) {
    val c = Theme.c; val k = s.korean; val ctx = androidx.compose.ui.platform.LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val plain = io.github.graviton94.todaybible.core.Markup.plain(s.store.book(s.translation, 18).verse(23, 1))
    var heard by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val canHear = remember { android.speech.SpeechRecognizer.isRecognitionAvailable(ctx) }
    fun complete() {
        if (done) return; done = true; listening = false
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        s.book = 18; s.chapter = 23; s.fill(listOf(1), io.github.graviton94.todaybible.core.Mode.ALOUD); onRead()
    }
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok && canHear) listening = true }
    val lit = io.github.graviton94.todaybible.core.Recite.lit(plain, heard)
    LaunchedEffect(heard) { if (io.github.graviton94.todaybible.core.Recite.done(plain, heard)) { kotlinx.coroutines.delay(Tokens.Motion.typeSettleMs.toLong()); complete() } }
    androidx.compose.runtime.DisposableEffect(listening) {
        if (!listening) return@DisposableEffect onDispose { }
        val r = android.speech.SpeechRecognizer.createSpeechRecognizer(ctx); var alive = true; var base = ""
        fun intent() = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, s.speechTag)
            .putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true).putExtra(android.speech.RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        r.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onPartialResults(b: android.os.Bundle?) { b?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { heard = "$base $it" } }
            override fun onResults(b: android.os.Bundle?) { b?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { heard = "$base $it" }; base = heard; if (alive && !done) r.startListening(intent()) }
            override fun onError(e: Int) {
                if (!alive || done) return
                // 말이 없었을 때만 다시 듣기 (권한 · 네트워크 · 바쁨 같은 오류는 멈추고 ‘다 읽었어요’로)
                if (e == android.speech.SpeechRecognizer.ERROR_NO_MATCH || e == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT) r.startListening(intent()) else listening = false
            }
            override fun onReadyForSpeech(p: android.os.Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: android.os.Bundle?) {}
        })
        r.startListening(intent())
        onDispose { alive = false; runCatching { r.cancel(); r.destroy() } }
    }
    Text(stringResource(R.string.ob_read_h), style = Theme.title(k, Tokens.Text.title))
    Text(androidx.compose.ui.text.buildAnnotatedString {
        withStyle(androidx.compose.ui.text.SpanStyle(color = c.ink)) { append(plain.substring(0, lit)) }
        withStyle(androidx.compose.ui.text.SpanStyle(color = c.unwritten)) { append(plain.substring(lit)) }
    }, style = Theme.verse(k).copy(fontSize = Tokens.Text.aloudBig * s.scale, lineHeight = Tokens.Text.aloudBig * s.scale * Tokens.Leading.title))
    Text("${s.bookName(18)} 23:1", style = Theme.small().copy(color = c.rubric))
    if (done) Text(stringResource(R.string.ob_read_done), style = Theme.body().copy(color = c.giltText))
    else {
        // 마이크를 왜 묻는지 (가2)
        Text(stringResource(R.string.mic_why), style = Theme.small())
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (canHear) BookButton(stringResource(if (listening) R.string.aloud_listening else R.string.ob_read_start), Modifier.fillMaxWidth()) {
            if (granted) listening = !listening else mic.launch(Manifest.permission.RECORD_AUDIO)
        }
        BookButton(stringResource(R.string.voice_done_reading), Modifier.fillMaxWidth(), quiet = true) { complete() }
    }
}


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
                    Text(if (g < 0) "${-g}" else "$g", style = Theme.title(k).copy(color = if (on) c.leatherInk else c.ink), maxLines = 1)
                    Text(stringResource(if (g < 0) R.string.unit_chapter else R.string.unit_verses), style = Theme.small().copy(color = if (on) c.leatherInk else c.inkSoft), maxLines = 1)
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
                for (j in 0 until cols * rows) if (j !in shown) drawRect(c.paper.copy(alpha = Tokens.Alpha.veilPiece), Offset((j % cols) * w, (j / cols) * h), Size(w + 1f, h + 1f))
            }) { if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        else -> Column(Modifier.padding(Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Text("7:00", style = Theme.title(k, Tokens.Text.display))
            Row(Modifier.clip(RoundedCornerShape(Tokens.Radius.card)).background(c.leaf).padding(Tokens.Space.s3), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Box(Modifier.size(Tokens.Size.iconMd).clip(CircleShape).background(c.leather), contentAlignment = Alignment.Center) { StampMark(STAMP_CROSS, c.gilt, Modifier.size(Tokens.Size.lock)) }
                val t = s.store.book(s.translation, 18)
                Column { Text("${s.bookName(18)} 23:1", style = Theme.small().copy(color = c.ink)); Text(Markup.plain(t.verse(23, 1)), style = Theme.small(), maxLines = 2) }
            }
        }
    }
}
