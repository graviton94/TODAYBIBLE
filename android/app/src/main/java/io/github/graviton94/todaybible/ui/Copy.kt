package io.github.graviton94.todaybible.ui

import android.os.Build
import androidx.compose.runtime.rememberCoroutineScope
import io.github.graviton94.todaybible.data.Voice
import kotlinx.coroutines.launch
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import io.github.graviton94.todaybible.core.Recite
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.core.Mode
import io.github.graviton94.todaybible.core.ReadingPace
import io.github.graviton94.todaybible.core.TypeJudge
import io.github.graviton94.todaybible.core.VerseKey
import io.github.graviton94.todaybible.design.Fonts
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun CopyPage(s: AppState) {
    val ctx0 = LocalContext.current
    val k = s.korean
    val t = s.text(); val p = s.progress
    // 기도문 따라 쓰기: 그 범위의 절만 (잠긴 책이어도 무료)
    val pass = s.prayerPassHere()
    val fillable = t.fillable(s.chapter).let { all -> if (pass != null) all.filter { it in pass.from..pass.to } else all }
    val next = s.target?.takeIf { it in fillable && !p.isFilled(s.translation, VerseKey(s.book, s.chapter, it)) }
        ?: if (pass != null) fillable.firstOrNull { !p.isFilled(s.translation, VerseKey(s.book, s.chapter, it)) } else p.nextVerse(s.translation, t, s.chapter)
    // 기도문 밖으로 잠긴 장에 왔으면 (다른 장 고르기 등) 평생권 안내
    if (pass == null && s.locked(s.book)) { LaunchedEffect(s.book, s.chapter) { s.peekBook = s.book; s.purchaseOpen = true }; Box(Modifier.fillMaxSize().padding(Tokens.Space.s5)) { LifetimeCard(s) }; return }
    val doneCount = fillable.count { p.isFilled(s.translation, VerseKey(s.book, s.chapter, it)) }
    // 낭독 · 타자 · 손글씨 (교인 인터뷰: 낭독을 가장 많이 씀). 마지막에 고른 방식으로 열려요.
    var tab by remember { mutableIntStateOf(s.store.copyTab) }
    androidx.compose.runtime.SideEffect { s.copyTabNow = tab }
    LaunchedEffect(s.aloudNow) { if (s.aloudNow) { tab = 0; s.store.copyTab = 0; if (next == null) s.aloudNow = false } }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Tokens.Space.s5).padding(top = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                RunningHead(s.chapterRef(), stringResource(R.string.verse_of, doneCount, fillable.size), k,
                    Modifier.weight(1f).clickable(role = Role.Button) { s.picker = s.book })
                // 이 장 책갈피
                val marked = s.isBookmarked(s.book, s.chapter)
                Box(Modifier.size(Tokens.Size.tab).clickable(role = Role.Button) { s.toggleBookmark(s.book, s.chapter, next ?: 1) }
                    .semantics { contentDescription = ctx0.getString(R.string.bookmark) }, contentAlignment = Alignment.Center) {
                    Ribbon(if (marked) Theme.c.rubric else Theme.c.inkSoft, marked, Modifier.size(Tokens.Size.iconSm))
                }
                // 듣기: 성경 탭에서 이 장을 책 읽어 주듯 이어서
                Row(Modifier.heightIn(min = Tokens.Size.tab).clip(RoundedCornerShape(Tokens.Radius.chip)).background(Theme.c.paper).clickable(role = Role.Button) { if (pass != null) io.github.graviton94.todaybible.data.ListenService.startPassages(ctx0, listOf(pass), s.aloudRate()) else { s.read(s.book, s.chapter); io.github.graviton94.todaybible.data.ListenService.start(ctx0, s.book, s.chapter, 1, s.aloudRate()) } }
                    .padding(horizontal = Tokens.Space.s3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                    PlayMark(Theme.c.rubric, false, Modifier.size(Tokens.Size.iconSm))
                    Text(stringResource(R.string.listen_mode), style = Theme.small().copy(color = Theme.c.ink), maxLines = 1)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                UnderlineTabs(listOf(stringResource(R.string.mode_aloud), stringResource(R.string.mode_type), stringResource(R.string.mode_paper)), tab, Modifier.weight(1f).coach("copy_tabs")) { tab = it; s.store.copyTab = it }
            }
        }
        when (tab) {
            1 -> WritePage(s, next)
            2 -> if (next != null) HandTab(s, next) else Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
                ChapterDoneNote(s)
                if (io.github.graviton94.todaybible.data.Ink.verses(LocalContext.current, s.translation.id, s.book, s.chapter).isNotEmpty())
                    BookButton(stringResource(R.string.notes_pdf), Modifier.fillMaxWidth(), quiet = true) { s.requestNotes(s.book) }
            }
            else -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
                // 녹음을 지우고 다시 읽기를 고른 절이 있으면 그 절부터
                val again = s.reread?.takeIf { it.first == s.book && it.second == s.chapter && it.third in fillable }?.third
                val aloudVerse = again ?: next
                if (aloudVerse == null) { ChapterDoneNote(s); VoiceRow(s) } else AloudTab(s, aloudVerse)
            }
        }
    }
}

/**
 * 타자 필사 = 원고지 하나 (A1 · W1): 칸마다 쓸 글자가 옅은 밑글씨로 깔려 있고, 친 글자가 먹으로 덮어요.
 * 틀리면 붉게, 조합 중이면 흐리게. 키보드가 올라오면 지금 줄이 키보드 바로 위에 오도록 따라가요.
 * 한 절을 마치면 칸이 차례로 금빛으로 반짝인 뒤, 종이를 넘기듯 다음 절로.
 */
@Composable
private fun WritePage(s: AppState, verse: Int?) {
    val c = Theme.c; val k = s.korean
    val t = s.text()
    val haptic = LocalHapticFeedback.current
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    // 금빛으로 마무리하는 동안은 그 절을 그대로 보여 주고 입력은 받지 않아요
    var sealing by remember(s.book, s.chapter) { mutableStateOf<Pair<Int, String>?>(null) }
    val shown = sealing?.first ?: verse
    val source = shown?.let { t.verse(s.chapter, it) }.orEmpty()
    var value by remember(s.translation, s.book, s.chapter, verse) { mutableStateOf(TextFieldValue("")) }
    val typed = sealing?.second ?: value.text
    val marks = if (shown != null) TypeJudge.marks(source, typed) else emptyList()
    val gold = remember(s.book, s.chapter) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(value.text) {
        if (verse != null && sealing == null && TypeJudge.done(source, value.text)) {
            delay(Tokens.Motion.typeSettleMs.toLong())
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            sealing = verse to value.text
            gold.snapTo(0f); gold.animateTo(1f, tween(Tokens.Motion.goldMs, easing = androidx.compose.animation.core.LinearEasing))
            delay(Tokens.Motion.goldHoldMs.toLong())
            s.target = null; s.fill(listOf(verse), Mode.TYPE)
            sealing = null; gold.snapTo(0f)
        }
    }
    fun write() { focus.requestFocus(); keyboard?.show() }
    // 틀린 글자 뒤에 더 치려 하면: 빨간 칸을 지우라는 한 줄
    var fixHint by remember(s.book, s.chapter, verse) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (shown == null) Box(Modifier.padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) { ChapterDoneNote(s) }
        else androidx.compose.animation.AnimatedContent(shown, transitionSpec = {
            // 종이를 넘기듯: 새 절이 오른쪽에서 살짝 들어오고, 마친 절은 왼쪽으로
            (androidx.compose.animation.slideInHorizontally(tween(Tokens.Motion.turnMs)) { it / 3 } + androidx.compose.animation.fadeIn(tween(Tokens.Motion.turnMs))) togetherWith
                (androidx.compose.animation.slideOutHorizontally(tween(Tokens.Motion.turnMs)) { -it / 3 } + androidx.compose.animation.fadeOut(tween(Tokens.Motion.turnMs)))
        }, label = "verse") { v ->
            val src = if (v == shown) source else t.verse(s.chapter, v)
            Manuscript(s, v, src, if (v == shown) typed else "", if (v == shown) marks else TypeJudge.marks(src, ""), if (v == shown) gold.value else 0f, focused) { write() }
        }
        // 숨은 입력칸: 붙여넣기 · 자동완성으로 한꺼번에 들어온 글은 받지 않음 (한 자씩 옮겨 쓰기)
        BasicTextField(
            value = value,
            onValueChange = { nv ->
                if (sealing == null && nv.text.length - value.text.length <= 3) {
                    // 틀린 글자가 있으면 그 뒤로는 더 받지 않아요: 바로 그 자리에서 지우고 고치게 (지우기 · 띄어쓰기는 받음)
                    val wrongNow = TypeJudge.marks(source, value.text).any { it == TypeJudge.Mark.WRONG }
                    val adding = TypeJudge.letters(nv.text).length > TypeJudge.letters(value.text).length
                    if (wrongNow && adding) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); fixHint = true }
                    else { value = nv; if (!TypeJudge.marks(source, nv.text).any { it == TypeJudge.Mark.WRONG }) fixHint = false }
                }
            },
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
            modifier = Modifier.size(Tokens.Size.hiddenField).alpha(0f).focusRequester(focus).onFocusChanged { focused = it.isFocused },
        )
        if (fixHint && marks.any { it == TypeJudge.Mark.WRONG }) Text(stringResource(R.string.type_fix_hint),
            style = Theme.label().copy(color = c.leatherInk, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            modifier = Modifier.align(Alignment.BottomCenter).imePadding().padding(Tokens.Space.s3).clip(RoundedCornerShape(Tokens.Radius.chip)).background(c.rubric).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s2))
        // 쓰기 시작: 키보드가 닫혀 있을 때만 아래 띠로
        if (!focused && shown != null) Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.leaf)
            .drawBehind { drawLine(c.hair, Offset.Zero, Offset(size.width, 0f), Tokens.Stroke.hair.toPx()) }
            .padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            BookButton(stringResource(R.string.tap_to_write), Modifier.fillMaxWidth().coach("type_start")) { write() }
        }
    }
}

/** 원고지 한 장: 칸마다 밑글씨 · 친 글자 · 지금 칸 표시 · 마칠 때 금빛. */
@Composable
private fun Manuscript(s: AppState, verse: Int, source: String, typed: String, marks: List<TypeJudge.Mark>, gold: Float, typing: Boolean, onWrite: () -> Unit) {
    val c = Theme.c; val ctx = LocalContext.current; val view = androidx.compose.ui.platform.LocalView.current
    val plain = Markup.plain(source)
    val feel = remember { io.github.graviton94.todaybible.data.PenFeel(ctx.applicationContext) }
    DisposableEffect(Unit) { onDispose { feel.release() } }
    val ok = marks.count { it == TypeJudge.Mark.OK }
    LaunchedEffect(ok) {
        if (ok > 0) {
            if (s.penSound) { feel.speed(0.7f); delay(Tokens.Motion.tickMs.toLong()); feel.speed(0f) }
            if (s.paperHaptic) view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        }
    }
    val cursor = marks.indexOfFirst { it == TypeJudge.Mark.PENDING || it == TypeJudge.Mark.COMPOSING }
    val typedAt = TypeJudge.typedAt(source, typed)
    val key = VerseKey(s.book, s.chapter, verse)
    val scroll = rememberScrollState()
    val dens = LocalDensity.current
    Column(Modifier.fillMaxSize().verticalScroll(scroll).clickable(remember { MutableInteractionSource() }, null) { onWrite() }
        .padding(start = Tokens.Space.s5, end = Tokens.Space.s5, top = Tokens.Space.s3, bottom = if (typing) Tokens.Space.s3 else Tokens.Size.touch * 2),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        // 절 번호 · 형광펜
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.grid_verse, verse), style = Theme.label().copy(color = c.rubric), modifier = Modifier.weight(1f))
            val on = s.isMarked(key)
            Text(stringResource(if (on) R.string.mark_off else R.string.mark_on), style = Theme.small().copy(color = if (on) c.rubric else c.inkSoft, background = if (on) c.mark else androidx.compose.ui.graphics.Color.Unspecified), maxLines = 1,
                modifier = Modifier.heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Button) { s.toggleMark(key) })
        }
        BoxWithConstraints(Modifier.fillMaxWidth().coach("type_grid")) {
            val cols = (maxWidth / (Tokens.Size.gridCell * s.scale)).toInt().coerceAtLeast(6)
            val cell = maxWidth / cols
            val rows = plain.indices.chunked(cols)
            val n = plain.length.coerceAtLeast(1)
            // 지금 줄이 늘 보이게: 키보드가 올라와 화면이 줄면 그 줄 위 한 줄까지 보이도록
            val row = (cursor.coerceAtLeast(0) / cols)
            LaunchedEffect(row, typing) { if (typing) with(dens) { scroll.animateScrollTo(((row - 1).coerceAtLeast(0) * cell.toPx()).toInt()) } }
            Column(Modifier.drawBehind {
                drawLine(c.rubric.copy(alpha = Tokens.Alpha.faint), Offset.Zero, Offset(size.width, 0f), Tokens.Stroke.hair.toPx())
                drawLine(c.rubric.copy(alpha = Tokens.Alpha.faint), Offset.Zero, Offset(0f, size.height), Tokens.Stroke.hair.toPx())
            }) {
                rows.forEach { r ->
                    Row {
                        (0 until cols).forEach { j ->
                            val i = r.getOrNull(j)
                            val m = i?.let { marks.getOrNull(it) }
                            // 금빛: 칸 차례대로 지나가는 빛
                            val g = if (i == null || gold <= 0f) 0f else ((gold * (n + 6) - i) / 6f).coerceIn(0f, 1f).let { if (it >= 1f) 0.45f else it }
                            Box(Modifier.size(cell).drawBehind {
                                if (g > 0f) drawRect(c.gilt.copy(alpha = g * Tokens.Alpha.goldCell))
                                val w = Tokens.Stroke.hair.toPx(); val line = c.rubric.copy(alpha = Tokens.Alpha.faint)
                                drawLine(line, Offset(size.width, 0f), Offset(size.width, size.height), w)
                                drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), w)
                                if (m == TypeJudge.Mark.WRONG) drawRect(c.rubric.copy(alpha = Tokens.Alpha.wrongCell))
                                if (i != null && i == cursor && gold <= 0f) drawRect(c.rubric, Offset(Tokens.Stroke.rule.toPx() / 2, Tokens.Stroke.rule.toPx() / 2),
                                    androidx.compose.ui.geometry.Size(size.width - Tokens.Stroke.rule.toPx(), size.height - Tokens.Stroke.rule.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(Tokens.Stroke.rule.toPx()))
                            }, contentAlignment = Alignment.Center) {
                                if (i != null && plain[i] != ' ') {
                                    val done = m == TypeJudge.Mark.OK
                                    val ch = when (m) { TypeJudge.Mark.COMPOSING -> typedAt.getOrNull(i) ?: plain[i]; else -> plain[i] }   // 틀린 칸: 써야 할 글자를 붉게
                                    val color = when {
                                        gold > 0f -> c.giltText
                                        done -> c.ink
                                        m == TypeJudge.Mark.WRONG -> c.rubric
                                        m == TypeJudge.Mark.COMPOSING -> c.inkSoft
                                        else -> c.ink.copy(alpha = Tokens.Alpha.hintChar)   // 밑글씨
                                    }
                                    Text(ch.toString(), style = Theme.verse(s.korean).copy(fontSize = Tokens.Text.gridChar * s.scale, lineHeight = Tokens.Text.gridChar * s.scale, color = color, textAlign = TextAlign.Center), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (gold > 0f) Text(stringResource(R.string.grid_done, verse), style = Theme.label().copy(color = c.giltText), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        else if (!typing) Text(stringResource(R.string.grid_hint), style = Theme.small())
    }
}


/** 줄 공책 한 덩이: 왼쪽 여백에 날짜, 줄마다 가는 선. */
@Composable
private fun NoteLine(date: String, text: AnnotatedString, pen: TextStyle) {
    val c = Theme.c
    Row(Modifier.fillMaxWidth().padding(end = Tokens.Space.s5)) {
        Text(date, style = Theme.small().copy(color = c.inkSoft, textAlign = TextAlign.End), maxLines = 1,
            modifier = Modifier.width(Tokens.Size.noteMargin).padding(top = Tokens.Space.s2))
        Text(text, style = pen, modifier = Modifier.weight(1f).padding(start = Tokens.Space.s5).drawBehind {
            val lh = pen.lineHeight.toPx(); var y = lh
            while (y <= size.height + 1f) { drawLine(c.noteLine, Offset(-Tokens.Space.s5.toPx(), y), Offset(size.width, y), Tokens.Stroke.hair.toPx()); y += lh }
        })
    }
}

/** 줄 공책 한 덩이 (손글씨): 왼쪽 여백에 날짜와 절 번호. */
@Composable
private fun HandNoteLine(date: String, verse: Int, ink: io.github.graviton94.todaybible.data.Ink.Page) {
    val c = Theme.c
    Row(Modifier.fillMaxWidth().padding(end = Tokens.Space.s5)) {
        Column(Modifier.width(Tokens.Size.noteMargin).padding(top = Tokens.Space.s2), horizontalAlignment = Alignment.End) {
            Text(date, style = Theme.small().copy(color = c.inkSoft), maxLines = 1)
            Text("$verse", style = Theme.small().copy(color = c.rubric), maxLines = 1)
        }
        Box(Modifier.weight(1f).padding(start = Tokens.Space.s5)) { InkSheets(ink, c.penInk, c.noteLine, c.graphite) }
    }
}

/** 이 장을 이미 다 썼을 때. */
@Composable
private fun ChapterDoneNote(s: AppState) {
    val pw = s.prayerWrite
    if (pw != null && s.prayerPassHere() != null) { PrayerDoneNote(s, pw.first, pw.second); return }
    Column(Modifier.padding(top = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Text(stringResource(R.string.chapter_done, s.bookName(), s.chapter), style = Theme.title(s.korean))
        val (nb, nc) = s.nextChapter()
        BookButton(stringResource(R.string.continue_at, s.bookName(nb), nc), Modifier.fillMaxWidth()) { s.open(nb, nc) }
    }
}

/** 교독 한 쌍: 인도 절 (낭독 목소리) 과 회중 절 (나) 을 위아래로. 지금 읽는 쪽은 종이 바탕, 다른 쪽은 옅게. */
@Composable
private fun ResponsivePair(s: AppState, verse: Int, source: String, at: Int, guideTurn: Boolean) {
    val c = Theme.c; val t = s.text(); val all = t.fillable(s.chapter); val i = all.indexOf(verse)
    val other = if (guideTurn) all.getOrNull(i + 1) else all.getOrNull(i - 1)
    val leader = stringResource(R.string.resp_leader); val people = stringResource(R.string.resp_people)
    @Composable fun part(label: String, now: Boolean, content: @Composable () -> Unit) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.card)).background(if (now) c.paper else androidx.compose.ui.graphics.Color.Transparent).padding(Tokens.Space.s3),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
            Text(label, style = Theme.small().copy(color = if (now) c.rubric else c.inkSoft), maxLines = 1)
            content()
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        if (guideTurn) {
            part(leader, true) { VerseText(s, verse, source, lit = at) }
            other?.let { v -> part(people, false) { VerseText(s, v, t.verse(s.chapter, v), faint = true) } }
        } else {
            other?.let { v -> part(leader, false) { VerseText(s, v, t.verse(s.chapter, v)) } }
            part(people, true) { VerseText(s, verse, source, lit = at) }
        }
    }
}

/** 기도문 말씀을 다 옮겨 썼을 때: 다음 말씀 · 기도문으로 · (열린 책이면) 이 장 이어 쓰기. */
@Composable
private fun PrayerDoneNote(s: AppState, id: String, i: Int) {
    val pr = io.github.graviton94.todaybible.core.Prayers.byId(id) ?: return
    Column(Modifier.padding(top = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Text(stringResource(R.string.prayer_written), style = Theme.title(s.korean))
        pr.passages.getOrNull(i + 1)?.let { n ->
            BookButton(stringResource(R.string.prayer_next_passage, Lang.passage(s.store.context, s.translation, s.bookName(n.book), n.chapter, n.from, n.to)), Modifier.fillMaxWidth()) { s.writePrayer(id, i + 1) }
        }
        BookButton(stringResource(R.string.prayer_return), Modifier.fillMaxWidth(), quiet = pr.passages.size > i + 1) { s.prayerWrite = null; s.prayerOpen = id }
        if (!s.locked(s.book)) BookButton(stringResource(R.string.prayer_keep_chapter), Modifier.fillMaxWidth(), quiet = true) { s.open(s.book, s.chapter) }
        else Text(stringResource(R.string.prayer_free_note), style = Theme.small())
    }
}

/**
 * 말씀 한 절: 붉은 블랙레터 절 번호 + 본문. KJV 첨가어는 이탤릭, LORD 는 스몰캡.
 * lit = 밝아진 글자 수 (낭독). marks = 옮겨 쓰는 중 (맞음 먹 · 틀림 붉은 밑줄 · 다음 글자 붉은 펜촉 · 남은 글자 흐린 먹).
 * faint = 아직 안 쓴 절. sealed = 방금 마침 (끝에 도장 · 아래 금선).
 */
@Composable
fun VerseText(s: AppState, number: Int, text: String, lit: Int? = null, marks: List<TypeJudge.Mark>? = null, faint: Boolean = false, sealed: Boolean = false, marked: Boolean = false, missed: List<IntRange> = emptyList()) {
    val c = Theme.c; val k = s.korean
    val nibAt = marks?.indexOfFirst { it == TypeJudge.Mark.PENDING } ?: -1
    val body: AnnotatedString = buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = Fonts.black, color = if (faint) c.unwritten else c.rubric, fontSize = Tokens.Leading.verseNumber.em)) { append("$number ") }
        var i = 0
        Markup.spans(text).forEach { sp ->
            // 형광펜: 그은 절은 글자 뒤에 옅은 노랑
            val style = SpanStyle(fontStyle = if (sp.italic) FontStyle.Italic else FontStyle.Normal, fontFeatureSettings = if (sp.smallCaps) "smcp" else null,
                background = if (marked) c.mark else androidx.compose.ui.graphics.Color.Unspecified)
            if (marks != null) {
                sp.text.forEachIndexed { j, ch ->
                    val st = when (marks.getOrNull(i + j)) {
                        TypeJudge.Mark.OK -> style.copy(color = c.ink)
                        TypeJudge.Mark.WRONG -> style.copy(color = c.rubric, textDecoration = TextDecoration.Underline)
                        // 한글을 조합하는 중인 글자: 틀림이 아니라 쓰는 중
                        TypeJudge.Mark.COMPOSING -> style.copy(color = c.inkSoft, background = c.rubric.copy(alpha = Tokens.Alpha.nib))
                        else -> if (i + j == nibAt) style.copy(color = c.unwritten, textDecoration = TextDecoration.Underline, background = c.rubric.copy(alpha = Tokens.Alpha.nib)) else style.copy(color = c.unwritten)
                    }
                    withStyle(st) { append(ch) }
                }
            } else if (missed.isNotEmpty()) {
                // 놓친 낱말은 붉은 밑줄
                sp.text.forEachIndexed { j, ch ->
                    val miss = missed.any { (i + j) in it }
                    withStyle(style.copy(color = if (miss) c.rubric else c.ink, textDecoration = if (miss) TextDecoration.Underline else null)) { append(ch) }
                }
            } else {
                val cut = if (lit == null) sp.text.length else (lit - i).coerceIn(0, sp.text.length)
                withStyle(style.copy(color = if (faint) c.unwritten else c.ink)) { append(sp.text.substring(0, cut)) }
                if (cut < sp.text.length) withStyle(style.copy(color = c.unwritten)) { append(sp.text.substring(cut)) }
            }
            i += sp.text.length
        }
        if (sealed) { append(" "); appendInlineContent("seal", "+") }
    }
    val line = remember(sealed) { Animatable(if (sealed) 0f else 1f) }
    LaunchedEffect(sealed) { if (sealed) line.animateTo(1f, tween(Tokens.Motion.sealMs)) }
    Column {
        Text(body, style = Theme.verse(k), inlineContent = sealInline(c.rubric))
        if (sealed) Box(Modifier.fillMaxWidth(line.value).height(Tokens.Stroke.rule).background(c.gilt))
    }
}

/** ✠ 자리에 그린 도장 (글꼴에 없는 기호). */
private fun sealInline(color: androidx.compose.ui.graphics.Color): Map<String, InlineTextContent> = mapOf(
    "seal" to InlineTextContent(Placeholder(Tokens.Leading.verseNumber.em, Tokens.Leading.verseNumber.em, PlaceholderVerticalAlign.TextCenter)) {
        StampMark(STAMP_CROSS, color, Modifier.fillMaxSize())
    })

/** 재생 ▶ · 멈춤 ■ (글꼴에 기대지 않고 그려요). */
@Composable
fun PlayMark(color: androidx.compose.ui.graphics.Color, playing: Boolean, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        if (playing) drawRect(color, Offset(size.width * 0.22f, size.height * 0.22f), androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.56f))
        else drawPath(androidx.compose.ui.graphics.Path().apply { moveTo(size.width * 0.28f, size.height * 0.18f); lineTo(size.width * 0.84f, size.height * 0.5f); lineTo(size.width * 0.28f, size.height * 0.82f); close() }, color)
    }
}

/** 이 장 낭독 목소리를 받는 중이거나 못 받았을 때 한 줄. */
@Composable
private fun NarrationBanner(s: AppState, loading: Boolean, failed: Boolean) {
    if (!loading && !failed) return
    val c = Theme.c
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Text(stringResource(if (loading) R.string.narr_loading else R.string.narr_failed), style = Theme.small().copy(color = if (failed) c.rubric else c.inkSoft), modifier = Modifier.weight(1f))
        if (failed) Text(stringResource(R.string.narr_retry), style = Theme.small().copy(color = c.rubric),
            modifier = Modifier.heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Button) { s.narration = s.narration - "${s.book}:${s.chapter}"; s.fetchNarration(s.book, s.chapter) })
    }
}

/** 낭독 위: 함께 읽기 · 혼자 읽기, 빠르기 (천천히 · 보통 · 빠르게). */
@Composable
private fun AloudControls(s: AppState, guideReady: Boolean) {
    // 화면에는 읽는 방식만. 빠르기 · 큰 글씨는 설정 › 낭독으로 (처음엔 보통 · 큰 글씨)
    // 교독 · 나만 읽기 (듣기는 따로: 장 머리의 ‘듣기’)
    if (guideReady) UnderlineTabs(listOf(stringResource(R.string.resp_mode), stringResource(R.string.guide_alone)), if (s.aloudMode == 2) 1 else 0, Modifier.coach("aloud_modes")) { s.chooseAloudMode(if (it == 0) 0 else 2) }
}

/**
 * 큰 글씨 한 줄 낭독 (T1): 숨 쉴 자리로 나눈 구절 가운데 지금 읽을 한 줄만 크게. 알아들으면 다음 줄이 스르륵 올라와요.
 * 위에는 방금 읽은 줄 (작게, 먹), 아래는 다음 줄 (흐리게).
 */
@Composable
private fun AloudLines(s: AppState, verse: Int, plain: String, at: Int, missed: List<IntRange>, guiding: Boolean) {
    val c = Theme.c; val k = s.korean
    val measurer = rememberTextMeasurer(); val base = Theme.verse(k)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val maxW = constraints.maxWidth
    // 큰 글씨가 줄어들지 않게: 한 줄에 안 들어가는 토막은 더 잘게 나눠요
    val floor = Tokens.Text.aloudFit.value * s.scale
    val parts = remember(plain, maxW, s.scale) {
        Recite.fit(plain, Recite.phrases(plain)) { r -> measurer.measure(plain.substring(r.first, r.last + 1), base.copy(fontSize = floor.sp), maxLines = 1, softWrap = false).size.width <= maxW }
    }
    val cur = parts.indexOfFirst { at <= it.last }.let { if (it < 0) parts.lastIndex else it }
    Column(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.aloudBox), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3, Alignment.CenterVertically)) {
        Text(stringResource(R.string.line_of, verse, cur + 1, parts.size), style = Theme.small().copy(color = c.rubric), maxLines = 1)
        // 꼭 한 줄: 화면 너비에 맞춰 글자 크기를 줄여서라도 한 줄로
        Box(Modifier.fillMaxWidth()) {
            val r = parts.getOrNull(cur)
            val line = r?.let { plain.substring(it.first, it.last + 1) }.orEmpty()
            val size = remember(line, maxW, s.scale) {
                var sp = Tokens.Text.aloudBig.value * s.scale
                while (sp > Tokens.Text.aloudMin.value && measurer.measure(line, base.copy(fontSize = sp.sp), maxLines = 1, softWrap = false).size.width > maxW) sp *= 0.93f
                sp.sp
            }
            androidx.compose.animation.AnimatedContent(cur, transitionSpec = {
                (androidx.compose.animation.slideInVertically(tween(Tokens.Motion.fadeMs)) { it / 2 } + androidx.compose.animation.fadeIn(tween(Tokens.Motion.fadeMs))) togetherWith
                    (androidx.compose.animation.slideOutVertically(tween(Tokens.Motion.fadeMs)) { -it / 2 } + androidx.compose.animation.fadeOut(tween(Tokens.Motion.fadeMs)))
            }, label = "line", modifier = Modifier.fillMaxWidth()) { i ->
                val ri = parts.getOrNull(i)
                if (ri != null) Text(buildAnnotatedString {
                    for (j in ri) {
                        val miss = missed.any { j in it }
                        val color = when { miss -> c.rubric; j < at -> if (guiding) c.giltText else c.ink; else -> c.unwritten }
                        withStyle(SpanStyle(color = color, textDecoration = if (miss) TextDecoration.Underline else null)) { append(plain[j]) }
                    }
                }, style = base.copy(fontSize = size, lineHeight = size * Tokens.Leading.title, textAlign = TextAlign.Center), maxLines = 1, softWrap = false, modifier = Modifier.fillMaxWidth())
            }
        }
        // 구절 진행: 점 하나씩
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
            parts.indices.forEach { i -> Box(Modifier.size(Tokens.Size.dot).clip(androidx.compose.foundation.shape.CircleShape).background(if (i <= cur) c.rubric else c.hair)) }
        }
    }
    }
}

/**
 * 낭독 (G1): 소리 내어 읽으면 알아들은 만큼 글자가 먹으로 (기기 안 음성 인식 · 읽은 목소리는 이 폰에만 녹음).
 * 다 읽으면 도장 · 진동과 함께 채워지고, 켜 둔 채로 다음 절로 이어짐.
 * 음성 인식이 없거나 마이크를 허락하지 않으면 읽는 빠르기로 밝아지는 방식.
 */
@Composable
private fun AloudTab(s: AppState, verse: Int) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    val source = s.text().verse(s.chapter, verse)
    val plain = Markup.plain(source)
    val haptic = LocalHapticFeedback.current
    val canHear = remember { SpeechRecognizer.isRecognitionAvailable(ctx) }
    var mic by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var on by remember { mutableStateOf(false) }          // 듣기 켜짐 (절이 바뀌어도 이어감)
    var heard by remember(verse, s.chapter, s.book) { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }      // 읽기가 켜져 있음 (절이 바뀌어도 이어감)
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> mic = ok; if (ok) running = true }
    // 마이크를 왜 묻는지 먼저 (가2)
    var whyMic by remember { mutableStateOf(false) }
    if (whyMic) androidx.compose.ui.window.Dialog({ whyMic = false }) {
        Column(Modifier.clip(RoundedCornerShape(Tokens.Radius.sheet)).background(c.leaf).padding(Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Text(stringResource(R.string.mic_why_h), style = Theme.title(k))
            Text(stringResource(R.string.mic_why), style = Theme.body())
            BookButton(stringResource(R.string.mic_allow), Modifier.fillMaxWidth()) { whyMic = false; ask.launch(Manifest.permission.RECORD_AUDIO) }
            BookButton(stringResource(R.string.not_now), Modifier.fillMaxWidth(), quiet = true) { whyMic = false }
        }
    }
    // 듣기와 낭독은 함께 하지 않아요: 듣기를 열면 읽기를 멈추고, 읽기를 켜면 듣기를 멈춰요 (낭독 목소리를 내 목소리로 알아듣지 않게)
    val listening = io.github.graviton94.todaybible.data.ListenService.now.collectAsState().value != null
    LaunchedEffect(listening) { if (listening) running = false }
    LaunchedEffect(running) { if (running && io.github.graviton94.todaybible.data.ListenService.now.value != null) io.github.graviton94.todaybible.data.ListenService.stop(ctx) }
    // 아침 알림 ‘함께 읽기’로 열었으면 곧바로 시작 (Y1)
    LaunchedEffect(s.aloudNow) { if (s.aloudNow) { s.aloudNow = false; if (mic) running = true else ask.launch(Manifest.permission.RECORD_AUDIO) } }
    // 내 목소리 남기기 (평생권): 녹음과 음성 인식이 마이크 하나를 나눠 씀 (안드로이드 13+ 는 파이프로, 그 아래는 손으로 ‘다 읽었어요’)
    // 읽는 목소리는 늘 녹음 (마음에 안 들면 지우기). 안드로이드 13 아래에서 음성 인식과 마이크를 나눌 수 없으면 알아듣기를 먼저.
    val record = Build.VERSION.SDK_INT >= 33 || !canHear
    var pipeFailed by remember { mutableStateOf(false) }
    val recognize = canHear && (!record || (Build.VERSION.SDK_INT >= 33 && !pipeFailed))
    // 가이드 목소리 (함께 읽기): 먼저 한 절을 차분히 들려주고, 다 들으면 마이크가 열려요
    val main = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val guide = remember(k, s.narrator, s.guideVoice) { io.github.graviton94.todaybible.data.GuideVoice(ctx, k, s.guideVoice, s.narrator) }
    DisposableEffect(guide) { onDispose { guide.release() } }
    // 낭독 목소리 (M5 · F5): 지금 장 음원만 받아 둬요 (수백 KB). 폰 목소리는 받을 수 없을 때만 대신.
    val N = io.github.graviton94.todaybible.data.Narration
    val wantNarr = s.narrator != N.DEVICE
    LaunchedEffect(wantNarr, s.narrator, s.book, s.chapter) { if (wantNarr) s.fetchNarration(s.book, s.chapter) }
    val narr = if (wantNarr) s.narrationState(s.book, s.chapter) else null
    val narrLoading = wantNarr && (narr == null || narr == 0)
    val narratedBook = narr == 1
    var ttsReady by remember(guide) { mutableStateOf(false) }
    LaunchedEffect(guide) { guide.whenReady { main.post { ttsReady = guide.ready } } }
    val guideReady = narratedBook || narrLoading || ttsReady
    val voiceFile = remember(verse, s.chapter, s.book, narratedBook) { guide.narrated(s.book, s.chapter, verse) }
    // 0 교독 · 1 듣기 · 2 나만 읽기 (낭독 목소리를 쓸 수 없으면 나만 읽기)
    val mode = if (guideReady) s.aloudMode.let { if (it == 1) 0 else it } else 2
    val useGuide = false
    // 교독: 장의 첫째 · 셋째 … 절은 인도(낭독 목소리), 둘째 · 넷째 … 는 회중(나)
    val guideTurn = mode == 0 && s.text().fillable(s.chapter).indexOf(verse) % 2 == 0
    // 읽는 동안 화면이 꺼지지 않게 (나1)
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(running) { view.keepScreenOn = running; onDispose { view.keepScreenOn = false } }
    var spoken by remember(verse, s.chapter, s.book) { mutableIntStateOf(-1) }   // 가이드가 읽은 데까지 (-1 = 가이드가 읽는 중 아님)
    var replay by remember { mutableIntStateOf(0) }
    // 방금 녹음한 절 (나만 읽기): 들어보기 · 다시 녹음 · 저장을 고르는 동안 다음 절로 가지 않아요
    var justRecorded by remember(s.book, s.chapter) { mutableStateOf<Int?>(null) }

    if (canHear || (record && mic)) {
        // 따라 읽기 (후한 판정): 소리가 들리는 동안 읽는 빠르기로 글자가 천천히 밝아지고, 알아들은 곳이 앞서면 거기까지 당겨요
        var paced by remember(verse, s.chapter, s.book) { mutableIntStateOf(0) }
        var voiceAt by remember { mutableLongStateOf(0L) }
        val lit = maxOf(paced, if (recognize) Recite.lit(plain, heard) else 0).coerceAtMost(plain.length)
        val done = lit >= plain.length || (recognize && Recite.done(plain, heard))
        var finished by remember(verse, s.chapter, s.book) { mutableStateOf(false) }
        fun complete() {
            if (finished) return; finished = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            if (record && mode == 2 && !guideTurn) justRecorded = verse
            s.fill(listOf(verse), Mode.ALOUD); if (s.reread?.third == verse) { s.reread = null; s.voiceRev++ }
        }
        LaunchedEffect(done) { if (done) { delay(Tokens.Motion.typeSettleMs.toLong()); complete() } }
        var retry by remember(verse, s.chapter, s.book) { mutableIntStateOf(0) }
        val holding = justRecorded != null
        LaunchedEffect(running, verse, s.chapter, s.book, mode, replay, narrLoading, holding) {
            if (!running || holding) { guide.stop(); spoken = -1; on = false; return@LaunchedEffect }
            if (narrLoading && mode == 0) { on = false; return@LaunchedEffect }   // 목소리 받는 동안 잠깐 기다림
            when {
                // 교독 · 인도 차례: 가이드가 읽은 절도 읽은 것으로 채우고, 녹음을 켰으면 가이드 목소리도 그 절 자리에 남겨 장 전체가 이어지게
                guideTurn && !finished -> {
                    on = false; spoken = 0
                    val atB = s.book; val atC = s.chapter
                    guide.speak(plain, s.aloudRate(), onRange = { _, e -> main.post { spoken = e } }, onDone = {
                        main.post {
                            spoken = -1
                            // 다른 장 · 절로 옮겼으면 지난 소리의 끝은 무시
                            if (!running || s.book != atB || s.chapter != atC || finished) return@post
                            if (record) {
                                val target = Voice.file(ctx, s.translation.id, s.book, s.chapter, verse)
                                // 미리 만든 음원은 내 녹음과 같은 결이라 그대로, 폰 목소리는 파일로 만들어 바꿔서
                                if (voiceFile != null) Thread { runCatching { target.parentFile?.mkdirs(); voiceFile.copyTo(target, overwrite = true) } }.start()
                                else {
                                    val wav = java.io.File(ctx.cacheDir, "guide_${s.book}_${s.chapter}_$verse.wav")
                                    guide.synthesize(plain, s.aloudRate(), wav) { ok -> if (ok) Thread { Voice.encodeWav(wav, target); wav.delete() }.start() }
                                }
                            }
                            complete()
                        }
                    }, file = voiceFile)
                }
                useGuide && !finished -> {
                    on = false; spoken = 0
                    guide.speak(plain, s.aloudRate(), onRange = { _, e -> main.post { spoken = e } }, onDone = { main.post { spoken = -1; if (running) on = true } }, file = voiceFile)
                }
                else -> on = true
            }
            // 절 · 장 · 방식이 바뀌거나 화면을 떠나면 가이드 소리도 멈춤
            try { kotlinx.coroutines.awaitCancellation() } finally { guide.stop() }
        }
        // 소리가 들린 지 얼마 안 됐으면 한 글자씩 (말을 멈추면 빛도 멈춤)
        LaunchedEffect(on, verse, s.chapter, s.book) {
            if (!on) return@LaunchedEffect
            while (paced < plain.length && !finished) {
                delay(ReadingPace.delayMillis(plain[paced], k, s.aloudRate() * Tokens.Motion.followPace))
                if (System.currentTimeMillis() - voiceAt < Tokens.Motion.voiceHoldMs) paced++
            }
        }
        // 앱을 내리면 읽기를 멈춤 (마이크 · 녹음 · 가이드)
        val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_STOP) running = false }
            owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
        }
        // 소리 내어 읽은 시간 (U2)
        DisposableEffect(running) {
            if (!running) return@DisposableEffect onDispose { }
            val t0 = System.currentTimeMillis()
            onDispose { val secs = ((System.currentTimeMillis() - t0) / 1000).toInt(); if (secs in 3..3600) s.addAloud(secs) }
        }
        DisposableEffect(on, verse, s.chapter, s.book, mic, record, retry) {
            if (!on || !mic) return@DisposableEffect onDispose { }
            val session = if (record) Voice.Session(Voice.file(ctx, s.translation.id, s.book, s.chapter, verse)).also { it.start() } else null
            // 알아듣기가 없으면 녹음 소리 크기로 말하는 중인지
            val levelJob = if (session != null && !recognize) kotlinx.coroutines.MainScope().launch { while (true) { delay(100); if (session.level > Tokens.Motion.voiceLevel) voiceAt = System.currentTimeMillis() } } else null
            var rec: SpeechRecognizer? = null
            var alive = true
            if (recognize) {
                val r = SpeechRecognizer.createSpeechRecognizer(ctx); rec = r
                fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, s.speechTag)
                    .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    .apply {
                        if (session != null && Build.VERSION.SDK_INT >= 33) {
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, session.recognizerPipe())
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, android.media.AudioFormat.ENCODING_PCM_16BIT)
                            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, Voice.RECOGNIZER_RATE)
                        }
                    }
                var base = ""
                r.setRecognitionListener(object : RecognitionListener {
                    override fun onPartialResults(b: Bundle?) { b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { heard = "$base $it" } }
                    override fun onResults(b: Bundle?) {
                        b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { heard = "$base $it" }
                        base = heard
                        if (alive && !Recite.done(plain, heard)) r.startListening(intent())
                    }
                    override fun onError(e: Int) {
                        if (!alive) return
                        when {
                            e == SpeechRecognizer.ERROR_NO_MATCH || e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> r.startListening(intent())
                            session != null -> pipeFailed = true   // 녹음은 이어가고, 손으로 마침
                            else -> { on = false; running = false }
                        }
                    }
                    override fun onReadyForSpeech(p: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(v: Float) { if (v > Tokens.Motion.voiceRms) voiceAt = System.currentTimeMillis() }
                    override fun onBufferReceived(b: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onEvent(t: Int, p: Bundle?) {}
                })
                r.startListening(intent())
            }
            onDispose {
                alive = false; levelJob?.cancel(); runCatching { rec?.cancel(); rec?.destroy() }
                // 다 읽은 절만 남기고, 중간에 멈춘 녹음은 버림
                if (session != null) { if (finished) session.stop() else session.discard() }
            }
        }
        NarrationBanner(s, narrLoading, narr == -1)
        AloudControls(s, guideReady)
        val at = if (spoken >= 0) spoken else lit
        // 교독: 인도 절과 회중 절을 한 화면에 함께
        if (mode == 0) ResponsivePair(s, verse, source, at, guideTurn)
        else if (s.aloudBig) AloudLines(s, verse, plain, at, emptyList(), guiding = spoken >= 0)
        else VerseText(s, verse, source, lit = at)
        justRecorded?.let { v -> RecordedBar(s, v, onKeep = { justRecorded = null }, onAgain = { justRecorded = null; s.reread = Triple(s.book, s.chapter, v) }) }
        val micLabel = stringResource(R.string.aloud_listen)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Box(Modifier.coach("aloud_mic").size(Tokens.Size.emblem).clip(androidx.compose.foundation.shape.CircleShape).background(if (running) c.rubric else c.leather)
                .semantics { contentDescription = micLabel }.clickable(role = Role.Button) { if (!mic) whyMic = true else running = !running },
                contentAlignment = Alignment.Center) { MicMark(c.leatherInk, Modifier.size(Tokens.Size.iconMd)) }
            Text(stringResource(when { spoken >= 0 && guideTurn -> R.string.resp_guide; spoken >= 0 -> R.string.guide_reading; on && mode == 0 -> R.string.resp_you; on && record -> R.string.voice_recording; on && useGuide -> R.string.guide_your_turn; on -> R.string.aloud_listening; mode == 0 -> R.string.resp_start; useGuide -> R.string.guide_start; else -> R.string.aloud_listen }),
                style = Theme.label().copy(color = if (running) c.rubric else c.ink))
            if (on && !recognize) BookButton(stringResource(R.string.voice_done_reading), Modifier.fillMaxWidth()) { complete() }
            if (running && useGuide && spoken < 0) BookButton(stringResource(R.string.guide_again), Modifier.fillMaxWidth(), quiet = true) { replay++ }
            Text(stringResource(if (record) R.string.voice_keep_hint else R.string.aloud_hint), style = Theme.small().copy(textAlign = TextAlign.Center))
        }
        VoiceRow(s)
        return
    }

    // 음성 인식이 없을 때: 가이드 목소리가 있으면 함께 들으며, 없으면 읽는 빠르기로 밝아짐
    var playing by remember(verse, s.chapter, s.book) { mutableStateOf(false) }
    var lit by remember(verse, s.chapter, s.book) { mutableIntStateOf(0) }
    LaunchedEffect(playing, verse) {
        if (!playing) { guide.stop(); return@LaunchedEffect }
        if (useGuide) { guide.speak(plain, s.aloudRate(), onRange = { _, e -> main.post { lit = e } }, onDone = { main.post { lit = plain.length; playing = false } }, file = voiceFile); return@LaunchedEffect }
        while (playing && lit < plain.length) {
            delay(ReadingPace.delayMillis(plain[lit], k, s.aloudRate())); lit++
        }
        if (lit >= plain.length) playing = false
    }
    NarrationBanner(s, narrLoading, narr == -1)
    AloudControls(s, guideReady)
    VerseText(s, verse, source, lit = lit)
    Text(stringResource(R.string.aloud_no_mic), style = Theme.small())
    if (lit >= plain.length) BookButton(stringResource(R.string.aloud_done), Modifier.fillMaxWidth()) { s.fill(listOf(verse), Mode.ALOUD) }
    else BookButton(stringResource(if (playing) R.string.aloud_pause else R.string.aloud_start), Modifier.fillMaxWidth()) { playing = !playing }
}

/** 마이크 (가는 선). */
@Composable
private fun MicMark(color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Box(modifier.drawBehind {
        val w = Tokens.Stroke.rule.toPx(); val cx = size.width / 2
        val bw = size.width * 0.36f; val bh = size.height * 0.52f
        drawRoundRect(color, Offset(cx - bw / 2, 0f), androidx.compose.ui.geometry.Size(bw, bh), androidx.compose.ui.geometry.CornerRadius(bw / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
        drawArc(color, 0f, 180f, false, Offset(cx - bw * 0.85f, bh * 0.35f), androidx.compose.ui.geometry.Size(bw * 1.7f, bh * 0.95f), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
        drawLine(color, Offset(cx, bh * 1.3f), Offset(cx, size.height), w)
    })
}

/** 채운 절을 누르면: 나누기 카드 미리보기 + 나누기. */
@Composable
fun ShareVerseSheet(s: AppState, at: VerseKey) {
    val ctx = LocalContext.current; val k = s.korean
    val b = at.book; val ch = at.chapter; val v = at.verse
    val ref = "${s.bookName(b)} $ch:$v"
    val plate = s.store.plateFor(b, ch)?.id ?: s.store.plates.getOrNull((b + ch) % s.store.plates.size.coerceAtLeast(1))?.id
    val text = s.text(b).verse(ch, v)
    val bmp = remember(b, ch, v) { Cards.verse(ctx, k, ref, text, plate) }
    BookSheet({ s.shareVerse = null }) {
        Image(bmp.asImageBitmap(), ref, Modifier.fillMaxWidth(Tokens.Ratio.plateWidth).aspectRatio(Tokens.Px.shareW / Tokens.Px.shareH).clip(RoundedCornerShape(Tokens.Radius.chip)))
        Row(Modifier.fillMaxWidth().padding(top = Tokens.Space.s3), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            val key = at
            BookButton(stringResource(if (s.isMarked(key)) R.string.mark_off else R.string.mark_on), Modifier.weight(1f), quiet = true) { s.toggleMark(key) }
            BookButton(stringResource(R.string.share), Modifier.weight(1f)) { Cards.share(s, ctx, bmp, "verse"); s.shareVerse = null }
        }
    }
}

/** 방금 녹음한 절: 들어보기 · 다시 녹음 · 저장 (그대로 두면 잠시 뒤 저장하고 다음 절로). */
@Composable
private fun RecordedBar(s: AppState, v: Int, onKeep: () -> Unit, onAgain: () -> Unit) {
    val c = Theme.c; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val f = Voice.file(ctx, s.translation.id, s.book, s.chapter, v)
    var player by remember(v) { mutableStateOf<android.media.MediaPlayer?>(null) }
    var touched by remember(v) { mutableStateOf(false) }
    DisposableEffect(v) { onDispose { player?.release() } }
    LaunchedEffect(v, touched) { if (!touched) { delay(Tokens.Motion.keepMs.toLong()); onKeep() } }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.card)).background(c.paper).padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Text(stringResource(R.string.rec_just, v), style = Theme.label())
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            BookButton(stringResource(R.string.rec_listen), Modifier.weight(1f), quiet = true) {
                touched = true
                scope.launch {
                    // 녹음 줄이 파일을 마저 쓸 때까지 잠깐
                    repeat(30) { if (f.exists()) return@repeat; delay(100) }
                    player?.release()
                    player = runCatching { android.media.MediaPlayer().apply { setDataSource(f.path); prepare(); start() } }.getOrNull()
                }
            }
            BookButton(stringResource(R.string.rec_redo), Modifier.weight(1f), quiet = true) { player?.release(); player = null; f.delete(); s.voiceRev++; onAgain() }
            BookButton(stringResource(R.string.rec_keep), Modifier.weight(1f)) { player?.release(); player = null; onKeep() }
        }
        if (!touched) Text(stringResource(R.string.rec_auto), style = Theme.small())
    }
}

/** 이 장의 내 낭독: 길이 · 듣기 · 소리로 · 영상으로 내보내기 (평생권). */
@Composable
private fun VoiceRow(s: AppState) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    val parts = remember(s.fills.size, s.book, s.chapter, s.translation, s.voiceRev) { Voice.verses(ctx, s.translation.id, s.book, s.chapter) }
    if (parts.isEmpty()) return
    val lengths = androidx.compose.runtime.produceState(emptyMap<Int, Long>(), parts) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { parts.associate { it.first to Voice.durationMs(it.second) } }
    }.value
    val total = lengths.values.sum()
    val scope = rememberCoroutineScope()
    var player by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    fun export(video: Boolean) {
        if (s.gated()) { s.purchaseOpen = true; return }
        if (s.exporting) return
        s.exporting = true
        scope.launch {
            val name = "${s.bookName().replace(' ', '_')}_${s.chapter}"
            val out = java.io.File(ctx.cacheDir, "share/$name.${if (video) "mp4" else "m4a"}")
            val ok = try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (!video) Voice.exportAudio(parts.map { it.second }, out)
                else {
                    val t = s.text(); val plate = s.store.plateFor(s.book, s.chapter)?.id
                    val frames = parts.map { (v, f) -> Cards.verse(ctx, k, "${s.bookName()} ${s.chapter}:$v", t.verse(s.chapter, v), plate) to Voice.durationMs(f) * 1000 }
                    Voice.exportVideo(frames, parts.map { it.second }, out).also { frames.forEach { it.first.recycle() } }
                }
            } } finally { s.exporting = false }
            if (ok) s.exportJob = ExportJob(listOf(out), if (video) "video/mp4" else "audio/mp4", ctx.getString(R.string.export_title_voice, s.bookName(), s.chapter))
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = Tokens.Space.s4).clip(RoundedCornerShape(Tokens.Radius.card)).background(c.paper).padding(Tokens.Space.s4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        val sec = (total / 1000).toInt()
        Text(stringResource(R.string.voice_chapter, parts.size, stringResource(R.string.duration_ms, sec / 60, sec % 60)), style = Theme.label(), maxLines = 1)
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            BookButton(stringResource(if (player != null) R.string.voice_stop else R.string.voice_play), Modifier.weight(1f), quiet = true) {
                player?.let { it.release(); player = null; return@BookButton }
                // 절 녹음을 차례로
                var i = 0
                fun next() {
                    if (i >= parts.size) { player?.release(); player = null; return }
                    val mp = runCatching { android.media.MediaPlayer().apply { setDataSource(parts[i++].second.path); prepare() } }.getOrNull() ?: return next()
                    mp.setOnCompletionListener { it.release(); next() }; player = mp; mp.start()
                }
                next()
            }
            BookButton(stringResource(R.string.voice_export_audio), Modifier.weight(1f), locked = s.premiumOn, enabled = !s.exporting) { export(false) }
            BookButton(stringResource(R.string.voice_export_video), Modifier.weight(1f), locked = s.premiumOn, enabled = !s.exporting) { export(true) }
        }
        // 가족에게 보내기 (U1): 표지 카드 한 장 + 이 장 낭독을 함께
        BookButton(stringResource(R.string.gift_send), Modifier.fillMaxWidth(), locked = s.premiumOn, enabled = !s.exporting) {
            if (s.gated()) { s.purchaseOpen = true; return@BookButton }
            s.exporting = true
            scope.launch {
                val name = "${s.bookName().replace(' ', '_')}_${s.chapter}"
                val audio = java.io.File(ctx.cacheDir, "share/$name.m4a")
                val title = if (s.ownerName.isNotBlank()) ctx.getString(R.string.gift_title_named, s.ownerName, s.bookName(), s.chapter) else ctx.getString(R.string.gift_title, s.bookName(), s.chapter)
                val sec = (total / 1000).toInt()
                val card = try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    if (!Voice.exportAudio(parts.map { it.second }, audio)) null
                    else Cards.year(ctx, k, title, stringResource0(ctx, R.string.duration_ms, sec / 60, sec % 60), listOf(ctx.getString(R.string.voice_chapter, parts.size, ctx.getString(R.string.duration_ms, sec / 60, sec % 60))))
                } } finally { s.exporting = false }
                if (card == null) return@launch
                val img = java.io.File(ctx.cacheDir, "share/$name.png").also { f -> f.outputStream().use { card.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
                s.exportJob = ExportJob(listOf(img, audio), "*/*", title)
            }
        }
        if (s.exporting) Text(stringResource(R.string.exporting), style = Theme.small())
        if (s.aloudMode != 2) Text(stringResource(R.string.rec_guide_note), style = Theme.small())
        // 절마다 녹음: 듣기 · 지우기 (마음에 안 들면 지우고 다시 읽어요)
        var open by remember { mutableStateOf(false) }
        Text(stringResource(if (open) R.string.rec_hide else R.string.rec_show), style = Theme.small().copy(color = c.rubric),
            modifier = Modifier.heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Button) { open = !open })
        if (open) parts.forEach { (v, f) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                val sec = ((lengths[v] ?: 0L) / 1000).toInt()
                Text(stringResource(R.string.rec_row, v, sec / 60, sec % 60), style = Theme.body(), modifier = Modifier.weight(1f))
                Text(stringResource(R.string.voice_play), style = Theme.small().copy(color = c.ink), modifier = Modifier.heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Button) {
                    player?.release(); player = runCatching { android.media.MediaPlayer().apply { setDataSource(f.path); prepare(); setOnCompletionListener { it.release(); player = null }; start() } }.getOrNull()
                })
                Text(stringResource(R.string.rec_delete), style = Theme.small().copy(color = c.rubric), modifier = Modifier.heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Button) {
                    player?.release(); player = null; f.delete(); s.voiceRev++; s.toast = ctx.getString(R.string.rec_deleted, v)
                })
                // 지우고 그 절을 다시 소리 내어 읽기
                Text(stringResource(R.string.rec_again), style = Theme.small().copy(color = c.ink), modifier = Modifier.heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Button) {
                    player?.release(); player = null; f.delete(); s.voiceRev++; s.reread = Triple(s.book, s.chapter, v); s.store.copyTab = 0
                })
            }
        }
    }
}

private fun stringResource0(ctx: android.content.Context, id: Int, vararg a: Any) = ctx.getString(id, *a)
