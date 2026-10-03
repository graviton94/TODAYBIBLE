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
import java.time.format.DateTimeFormatter
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    val k = s.korean
    val t = s.text(); val p = s.progress
    val fillable = t.fillable(s.chapter)
    val next = s.target?.takeIf { it in fillable && !p.isFilled(s.translation, VerseKey(s.book, s.chapter, it)) } ?: p.nextVerse(s.translation, t, s.chapter)
    val doneCount = fillable.count { p.isFilled(s.translation, VerseKey(s.book, s.chapter, it)) }
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = Tokens.Space.s5).padding(top = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            RunningHead(if (k) "${s.bookName()} ${s.chapter}장" else "${s.bookName().uppercase()} ${s.chapter}", stringResource(R.string.verse_of, doneCount, fillable.size), k,
                Modifier.clickable(role = Role.Button) { s.picker = s.book })
            Row(verticalAlignment = Alignment.CenterVertically) {
                UnderlineTabs(listOf(stringResource(R.string.mode_type), stringResource(R.string.mode_paper), stringResource(R.string.mode_aloud)), tab, Modifier.weight(1f)) { tab = it }
                if (tab == 0) ViewToggle(s)
            }
        }
        when (tab) {
            0 -> WritePage(s, next)
            1 -> if (next != null) HandTab(s, next) else Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
                ChapterDoneNote(s)
                if (io.github.graviton94.todaybible.data.Ink.verses(LocalContext.current, s.translation.id, s.book, s.chapter).isNotEmpty())
                    BookButton(stringResource(R.string.notes_pdf), Modifier.fillMaxWidth(), quiet = true) { s.requestNotes(s.book) }
            }
            else -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
                if (next == null) { ChapterDoneNote(s); VoiceRow(s) } else AloudTab(s, next)
            }
        }
    }
}

/** 책 / 노트 보기 바꾸기 (타자일 때만). */
@Composable
private fun ViewToggle(s: AppState) {
    val c = Theme.c
    Row(Modifier.padding(start = Tokens.Space.s3).clip(RoundedCornerShape(Tokens.Radius.chip)).background(c.paper)) {
        listOf(false to R.string.view_book, true to R.string.view_note).forEach { (note, id) ->
            val on = s.notebook == note
            Box(Modifier.heightIn(min = Tokens.Size.tab).clickable(role = Role.Tab) { if (!on) s.toggleNotebook() }
                .background(if (on) c.leather else androidx.compose.ui.graphics.Color.Transparent).padding(horizontal = Tokens.Space.s3), contentAlignment = Alignment.Center) {
                Text(stringResource(id), style = Theme.small().copy(color = if (on) c.leatherInk else c.inkSoft), maxLines = 1)
            }
        }
    }
}

/**
 * 타자 필사: 입력칸 없이 페이지 위에서 그대로 씀 (A1). 숨은 입력칸 하나가 키보드를 받고,
 * 쓴 글자는 바로 먹, 틀린 글자는 붉은 밑줄, 다음 글자 밑에 붉은 펜촉. 다 쓰면 도장 · 금선 · 진동.
 * 노트 보기(A2): 위는 성경, 아래 줄 공책에 내 글씨(펜 글씨체)로 이 장에서 쓴 절이 쌓임.
 */
@Composable
private fun WritePage(s: AppState, verse: Int?) {
    val c = Theme.c; val k = s.korean
    val t = s.text(); val p = s.progress
    val source = verse?.let { t.verse(s.chapter, it) }.orEmpty()
    var value by remember(s.translation, s.book, s.chapter, verse) { mutableStateOf(TextFieldValue("")) }
    val marks = if (verse != null) TypeJudge.marks(source, value.text) else emptyList()
    val haptic = LocalHapticFeedback.current
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    // 방금 마친 절: 도장 · 금선이 잠깐 머묾
    var sealed by remember(s.book, s.chapter) { mutableStateOf<Int?>(null) }
    LaunchedEffect(value.text) {
        if (verse != null && TypeJudge.done(source, value.text)) {
            delay(Tokens.Motion.typeSettleMs.toLong())
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            sealed = verse; s.target = null; s.fill(listOf(verse), Mode.TYPE)
        }
    }
    LaunchedEffect(sealed) { if (sealed != null) { delay(Tokens.Motion.sealHoldMs.toLong()); sealed = null } }
    fun write() { focus.requestFocus(); keyboard?.show() }

    Box(Modifier.fillMaxSize()) {
        if (s.notebook) NotebookView(s, verse, source, value.text, marks) { write() }
        else BookView(s, verse, marks, sealed) { write() }
        // 숨은 입력칸: 붙여넣기 · 자동완성으로 한꺼번에 들어온 글은 받지 않음 (한 자씩 옮겨 쓰기)
        BasicTextField(
            value = value,
            onValueChange = { nv -> if (nv.text.length - value.text.length <= 3) value = nv },
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
            modifier = Modifier.size(Tokens.Size.hiddenField).alpha(0f).focusRequester(focus).onFocusChanged { focused = it.isFocused },
        )
        // 쓰기 안내: 글 위에 겹치지 않게 아래 띠로 (키보드가 열리면 사라짐)
        if (!focused && verse != null) Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(c.leaf)
            .drawBehind { drawLine(c.hair, Offset.Zero, Offset(size.width, 0f), Tokens.Stroke.hair.toPx()) }
            .padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            BookButton(stringResource(R.string.tap_to_write), Modifier.fillMaxWidth()) { write() }
        }
    }
}

/** 책 보기: 이 장 전체가 한 페이지. 쓴 절은 먹, 쓰는 절은 표시, 남은 절은 흐린 먹. */
@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun BookView(s: AppState, current: Int?, marks: List<TypeJudge.Mark>, sealed: Int?, onWrite: () -> Unit) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current; val haptic = LocalHapticFeedback.current
    val t = s.text(); val p = s.progress
    val fillable = t.fillable(s.chapter)
    val list = rememberLazyListState()
    val chapterDone = current == null
    LaunchedEffect(current) { current?.let { v -> val i = fillable.indexOf(v); if (i > 1) list.animateScrollToItem(i - 1) } }
    LazyColumn(state = list, modifier = Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { onWrite() },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = Tokens.Space.s5, end = Tokens.Space.s5, top = Tokens.Space.s4, bottom = Tokens.Size.touch * 2),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        item { ChapterInitial(s.chapter, chapterDone, Modifier.padding(bottom = Tokens.Space.s1)) }
        items(fillable, key = { it }) { v ->
            val key = VerseKey(s.book, s.chapter, v)
            val filled = p.isFilled(s.translation, key)
            val text = t.verse(s.chapter, v)
            val on = s.isMarked(key)
            // 길게 누르면 형광펜 (긋기 · 지우기)
            Box(Modifier.combinedClickable(role = Role.Button, onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress); s.toggleMark(key)
                s.toast = ctx.getString(if (on) R.string.mark_removed else R.string.mark_added)
            }) { when { v == current -> onWrite(); filled -> s.shareVerse = v; else -> s.target = v } }) {
                when {
                    v == current -> VerseText(s, v, text, marks = marks, marked = on)
                    filled -> VerseText(s, v, text, sealed = v == sealed, marked = on)
                    else -> VerseText(s, v, text, faint = true, marked = on)
                }
            }
        }
        if (chapterDone) item { ChapterDoneNote(s) }
    }
}

/** 노트 보기: 위는 성경 한 절, 아래는 줄 공책에 내 글씨. 여백에 쓴 날짜. */
@Composable
private fun NotebookView(s: AppState, current: Int?, source: String, typed: String, marks: List<TypeJudge.Mark>, onWrite: () -> Unit) {
    val c = Theme.c; val k = s.korean
    val t = s.text(); val p = s.progress
    val written = s.fills.filter { it.translation == s.translation && it.key.book == s.book && it.key.chapter == s.chapter }
        .groupBy { it.key.verse }.mapValues { (_, f) -> f.minOf { it.epochDay } }.toSortedMap()
    val list = rememberLazyListState(); val ctx = LocalContext.current
    LaunchedEffect(written.size) { if (written.isNotEmpty()) list.animateScrollToItem(written.size) }
    Column(Modifier.fillMaxSize()) {
        if (current != null) Box(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) { VerseText(s, current, source) }
        else Box(Modifier.padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) { ChapterDoneNote(s) }
        val pen = TextStyle(fontFamily = Fonts.pen, fontSize = Tokens.Text.pen * s.scale, lineHeight = Tokens.Text.penLine * s.scale, color = c.penInk, lineBreak = Theme.phrase)
        val fmt = DateTimeFormatter.ofPattern("M. d")
        LazyColumn(state = list, modifier = Modifier.fillMaxSize().background(c.leaf).drawBehind {
            // 공책 여백의 붉은 세로줄
            val x = Tokens.Size.noteMargin.toPx() + Tokens.Space.s3.toPx()
            drawLine(c.rubric.copy(alpha = Tokens.Alpha.faint), Offset(x, 0f), Offset(x, size.height), Tokens.Stroke.hair.toPx())
        }.clickable(remember { MutableInteractionSource() }, null) { onWrite() },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = Tokens.Size.touch * 2)) {
            items(written.keys.toList(), key = { it }) { v ->
                // 손으로 쓴 절은 그 손글씨 그대로
                val ink = remember(s.translation, s.book, s.chapter, v) { io.github.graviton94.todaybible.data.Ink.file(ctx, s.translation.id, s.book, s.chapter, v).takeIf { it.exists() }?.let { io.github.graviton94.todaybible.data.Ink.load(it) } }
                if (ink != null) HandNoteLine(java.time.LocalDate.ofEpochDay(written.getValue(v)).format(fmt), v, ink)
                else NoteLine(java.time.LocalDate.ofEpochDay(written.getValue(v)).format(fmt), buildAnnotatedString {
                    withStyle(SpanStyle(color = c.rubric)) { append("$v ") }; append(Markup.plain(t.verse(s.chapter, v)))
                }, pen)
            }
            if (current != null) item {
                // 지금 쓰는 글: 쓴 만큼만, 틀린 글자는 붉게, 끝에 펜촉
                val plain = Markup.plain(source)
                val line = buildAnnotatedString {
                    withStyle(SpanStyle(color = c.rubric)) { append("$current ") }
                    plain.forEachIndexed { i, ch ->
                        when (marks.getOrNull(i)) {
                            TypeJudge.Mark.OK -> append(ch)
                            TypeJudge.Mark.WRONG -> withStyle(SpanStyle(color = c.rubric, textDecoration = TextDecoration.Underline)) { append(ch) }
                            else -> {}
                        }
                    }
                    withStyle(SpanStyle(color = c.rubric)) { append("▏") }
                }
                NoteLine(s.today().format(fmt), line, pen)
            }
        }
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
    Column(Modifier.padding(top = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Text(stringResource(R.string.chapter_done, s.bookName(), s.chapter), style = Theme.title(s.korean))
        val (nb, nc) = s.nextChapter()
        BookButton(stringResource(R.string.continue_at, s.bookName(nb), nc), Modifier.fillMaxWidth()) { s.open(nb, nc) }
    }
}

/**
 * 말씀 한 절: 붉은 블랙레터 절 번호 + 본문. KJV 첨가어는 이탤릭, LORD 는 스몰캡.
 * lit = 밝아진 글자 수 (낭독). marks = 옮겨 쓰는 중 (맞음 먹 · 틀림 붉은 밑줄 · 다음 글자 붉은 펜촉 · 남은 글자 흐린 먹).
 * faint = 아직 안 쓴 절. sealed = 방금 마침 (끝에 도장 · 아래 금선).
 */
@Composable
fun VerseText(s: AppState, number: Int, text: String, lit: Int? = null, marks: List<TypeJudge.Mark>? = null, faint: Boolean = false, sealed: Boolean = false, marked: Boolean = false) {
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
                        else -> if (i + j == nibAt) style.copy(color = c.unwritten, textDecoration = TextDecoration.Underline, background = c.rubric.copy(alpha = Tokens.Alpha.nib)) else style.copy(color = c.unwritten)
                    }
                    withStyle(st) { append(ch) }
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

/** 낭독 위: 함께 읽기 · 혼자 읽기, 빠르기 (천천히 · 보통 · 빠르게). */
@Composable
private fun AloudControls(s: AppState, guideReady: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        if (guideReady) UnderlineTabs(listOf(stringResource(R.string.guide_together), stringResource(R.string.guide_alone)), if (s.aloudGuide) 0 else 1) { if ((it == 0) != s.aloudGuide) s.flipAloudGuide() }
        UnderlineTabs(listOf(stringResource(R.string.aloud_slow), stringResource(R.string.aloud_normal), stringResource(R.string.aloud_fast)), s.aloudSpeed) { s.chooseAloudSpeed(it) }
    }
}

/**
 * 낭독 (G1): 소리 내어 읽으면 알아들은 만큼 글자가 먹으로 (기기 안 음성 인식 · 녹음은 남기지 않음).
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
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> mic = ok; if (ok) running = true }
    // 내 목소리 남기기 (평생권): 녹음과 음성 인식이 마이크 하나를 나눠 씀 (안드로이드 13+ 는 파이프로, 그 아래는 손으로 ‘다 읽었어요’)
    val record = s.voiceOn && !s.gated()
    var pipeFailed by remember { mutableStateOf(false) }
    val recognize = canHear && (!record || (Build.VERSION.SDK_INT >= 33 && !pipeFailed))
    // 가이드 목소리 (함께 읽기): 먼저 한 절을 차분히 들려주고, 다 들으면 마이크가 열려요
    val main = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val guide = remember(k) { io.github.graviton94.todaybible.data.GuideVoice(ctx, k, s.guideVoice) }
    DisposableEffect(guide) { onDispose { guide.release() } }
    var guideReady by remember(guide) { mutableStateOf(false) }
    LaunchedEffect(guide) { guide.whenReady { main.post { guideReady = guide.ready } } }
    val useGuide = s.aloudGuide && guideReady
    var running by remember { mutableStateOf(false) }      // 읽기가 켜져 있음 (절이 바뀌어도 이어감)
    var spoken by remember(verse, s.chapter, s.book) { mutableIntStateOf(-1) }   // 가이드가 읽은 데까지 (-1 = 가이드가 읽는 중 아님)
    var replay by remember { mutableIntStateOf(0) }

    if (canHear || record) {
        val lit = if (recognize) Recite.lit(plain, heard) else plain.length
        val done = recognize && Recite.done(plain, heard)
        var finished by remember(verse, s.chapter, s.book) { mutableStateOf(false) }
        fun complete() { if (finished) return; finished = true; haptic.performHapticFeedback(HapticFeedbackType.LongPress); s.fill(listOf(verse), Mode.ALOUD) }
        LaunchedEffect(done) { if (done) { delay(Tokens.Motion.typeSettleMs.toLong()); complete() } }
        LaunchedEffect(running, verse, s.chapter, s.book, useGuide, replay) {
            if (!running) { guide.stop(); spoken = -1; on = false; return@LaunchedEffect }
            if (useGuide && !finished) {
                on = false; spoken = 0
                guide.speak(plain, s.aloudRate(), onRange = { _, e -> main.post { spoken = e } }, onDone = { main.post { spoken = -1; if (running) on = true } })
            } else on = true
        }
        DisposableEffect(on, verse, s.chapter, s.book, mic, record, recognize) {
            if (!on || !mic) return@DisposableEffect onDispose { }
            val session = if (record) Voice.Session(Voice.file(ctx, s.translation.id, s.book, s.chapter, verse)).also { it.start() } else null
            var rec: SpeechRecognizer? = null
            var alive = true
            if (recognize) {
                val r = SpeechRecognizer.createSpeechRecognizer(ctx); rec = r
                fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (k) "ko-KR" else "en-US")
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
                            else -> on = false
                        }
                    }
                    override fun onReadyForSpeech(p: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(v: Float) {}
                    override fun onBufferReceived(b: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onEvent(t: Int, p: Bundle?) {}
                })
                r.startListening(intent())
            }
            onDispose {
                alive = false; runCatching { rec?.cancel(); rec?.destroy() }
                // 다 읽은 절만 남기고, 중간에 멈춘 녹음은 버림
                if (session != null) { if (finished) session.stop() else session.discard() }
            }
        }
        AloudControls(s, guideReady)
        VerseText(s, verse, source, lit = if (spoken >= 0) spoken else lit)
        val micLabel = stringResource(R.string.aloud_listen)
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Box(Modifier.size(Tokens.Size.emblem).clip(androidx.compose.foundation.shape.CircleShape).background(if (running) c.rubric else c.leather)
                .semantics { contentDescription = micLabel }.clickable(role = Role.Button) { if (!mic) ask.launch(Manifest.permission.RECORD_AUDIO) else running = !running },
                contentAlignment = Alignment.Center) { MicMark(c.leatherInk, Modifier.size(Tokens.Size.iconMd)) }
            Text(stringResource(when { spoken >= 0 -> R.string.guide_reading; on && record -> R.string.voice_recording; on && useGuide -> R.string.guide_your_turn; on -> R.string.aloud_listening; useGuide -> R.string.guide_start; else -> R.string.aloud_listen }),
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
        if (useGuide) { guide.speak(plain, s.aloudRate(), onRange = { _, e -> main.post { lit = e } }, onDone = { main.post { lit = plain.length; playing = false } }); return@LaunchedEffect }
        while (playing && lit < plain.length) {
            delay(ReadingPace.delayMillis(plain[lit], k, s.aloudRate())); lit++
        }
        if (lit >= plain.length) playing = false
    }
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
fun ShareVerseSheet(s: AppState, v: Int) {
    val ctx = LocalContext.current; val k = s.korean
    val ref = "${s.bookName()} ${s.chapter}:$v"
    val plate = s.store.plateFor(s.book, s.chapter)?.id ?: s.store.plates.getOrNull((s.book + s.chapter) % s.store.plates.size.coerceAtLeast(1))?.id
    val text = s.text().verse(s.chapter, v)
    val bmp = remember(s.book, s.chapter, v) { Cards.verse(ctx, k, ref, text, plate) }
    BookSheet({ s.shareVerse = null }) {
        Image(bmp.asImageBitmap(), ref, Modifier.fillMaxWidth(Tokens.Ratio.plateWidth).aspectRatio(Tokens.Px.shareW / Tokens.Px.shareH).clip(RoundedCornerShape(Tokens.Radius.chip)))
        Row(Modifier.fillMaxWidth().padding(top = Tokens.Space.s3), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            val key = VerseKey(s.book, s.chapter, v)
            BookButton(stringResource(if (s.isMarked(key)) R.string.mark_off else R.string.mark_on), Modifier.weight(1f), quiet = true) { s.toggleMark(key) }
            BookButton(stringResource(R.string.share), Modifier.weight(1f)) { Cards.share(ctx, bmp, "verse"); s.shareVerse = null }
        }
    }
}

/** 이 장의 내 낭독: 길이 · 듣기 · 소리로 · 영상으로 내보내기 (평생권). */
@Composable
private fun VoiceRow(s: AppState) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    val parts = remember(s.fills.size, s.book, s.chapter, s.translation) { Voice.verses(ctx, s.translation.id, s.book, s.chapter) }
    if (parts.isEmpty()) return
    val total = remember(parts) { parts.sumOf { Voice.durationMs(it.second) } }
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
            val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                if (!video) Voice.exportAudio(parts.map { it.second }, out)
                else {
                    val t = s.text(); val plate = s.store.plateFor(s.book, s.chapter)?.id
                    val frames = parts.map { (v, f) -> Cards.verse(ctx, k, "${s.bookName()} ${s.chapter}:$v", t.verse(s.chapter, v), plate) to Voice.durationMs(f) * 1000 }
                    Voice.exportVideo(frames, parts.map { it.second }, out).also { frames.forEach { it.first.recycle() } }
                }
            }
            s.exporting = false
            if (ok) {
                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, "${ctx.packageName}.share", out)
                val send = Intent(Intent.ACTION_SEND).setType(if (video) "video/mp4" else "audio/mp4").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                ctx.startActivity(Intent.createChooser(send, null))
            }
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
                    val mp = android.media.MediaPlayer(); mp.setDataSource(parts[i++].second.path); mp.prepare()
                    mp.setOnCompletionListener { it.release(); next() }; player = mp; mp.start()
                }
                next()
            }
            BookButton(stringResource(R.string.voice_export_audio), Modifier.weight(1f), enabled = !s.exporting) { export(false) }
            BookButton(stringResource(R.string.voice_export_video), Modifier.weight(1f), enabled = !s.exporting) { export(true) }
        }
        if (s.exporting) Text(stringResource(R.string.exporting), style = Theme.small())
    }
}
