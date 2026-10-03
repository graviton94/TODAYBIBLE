package io.github.graviton94.todaybible.ui

import android.graphics.BitmapFactory
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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
            1 -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) { PaperTab(s) }
            else -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
                if (next == null) ChapterDoneNote(s) else AloudTab(s, next)
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
        if (!focused && verse != null) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = Tokens.Space.s4)) {
            Row(Modifier.clip(RoundedCornerShape(Tokens.Radius.button)).background(c.leather).clickable(role = Role.Button) { write() }
                .drawBehind { giltFrame(c.gilt.copy(alpha = Tokens.Alpha.frame), bands = false) }
                .padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Text(stringResource(R.string.tap_to_write), style = Theme.label().copy(color = c.leatherInk), maxLines = 1)
            }
        }
    }
}

/** 책 보기: 이 장 전체가 한 페이지. 쓴 절은 먹, 쓰는 절은 표시, 남은 절은 흐린 먹. */
@Composable
private fun BookView(s: AppState, current: Int?, marks: List<TypeJudge.Mark>, sealed: Int?, onWrite: () -> Unit) {
    val c = Theme.c; val k = s.korean
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
            val filled = p.isFilled(s.translation, VerseKey(s.book, s.chapter, v))
            val text = t.verse(s.chapter, v)
            when {
                v == current -> VerseText(s, v, text, marks = marks)
                filled -> Column(Modifier.clickable(role = Role.Button) { s.shareVerse = v }) {
                    VerseText(s, v, text, sealed = v == sealed)
                }
                else -> Box(Modifier.clickable(role = Role.Button) { s.target = v }) { VerseText(s, v, text, faint = true) }
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
    val list = rememberLazyListState()
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
                NoteLine(java.time.LocalDate.ofEpochDay(written.getValue(v)).format(fmt), buildAnnotatedString {
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
fun VerseText(s: AppState, number: Int, text: String, lit: Int? = null, marks: List<TypeJudge.Mark>? = null, faint: Boolean = false, sealed: Boolean = false) {
    val c = Theme.c; val k = s.korean
    val nibAt = marks?.indexOfFirst { it == TypeJudge.Mark.PENDING } ?: -1
    val body: AnnotatedString = buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = Fonts.black, color = if (faint) c.unwritten else c.rubric, fontSize = Tokens.Leading.verseNumber.em)) { append("$number ") }
        var i = 0
        Markup.spans(text).forEach { sp ->
            val style = SpanStyle(fontStyle = if (sp.italic) FontStyle.Italic else FontStyle.Normal, fontFeatureSettings = if (sp.smallCaps) "smcp" else null)
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

@Composable
private fun PaperTab(s: AppState) {
    val c = Theme.c; val ctx = LocalContext.current
    val file = File(ctx.filesDir, "photos/${s.translation.id}_${s.book + 1}_${s.chapter}.jpg")
    var stamp by remember(s.book, s.chapter) { mutableIntStateOf(if (file.exists()) 1 else 0) }
    var declared by remember(s.book, s.chapter) { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) runCatching {
            file.parentFile?.mkdirs()
            ctx.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            stamp++
        }
    }
    val bitmap = remember(stamp) { if (file.exists()) runCatching { BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() }.getOrNull() else null }
    Box(
        Modifier.fillMaxWidth().aspectRatio(Tokens.Ratio.photoAspect).clip(RoundedCornerShape(Tokens.Radius.card)).background(c.paper)
            .clickable(role = Role.Button) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxWidth(), contentScale = ContentScale.Crop)
        else Text(stringResource(R.string.paper_photo), style = Theme.body().copy(color = c.inkSoft))
    }
    Row(Modifier.fillMaxWidth().clickable { declared = !declared }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(declared, { declared = it }, colors = CheckboxDefaults.colors(checkedColor = c.leather, uncheckedColor = c.inkSoft, checkmarkColor = c.leatherInk))
        Text(stringResource(R.string.paper_declare, s.chapter), style = Theme.body(), modifier = Modifier.weight(1f))
    }
    BookButton(stringResource(R.string.paper_add), Modifier.fillMaxWidth(), enabled = declared) {
        s.fill(s.text().fillable(s.chapter), Mode.PAPER)
    }
}

@Composable
private fun AloudTab(s: AppState, verse: Int) {
    val k = s.korean
    val source = s.text().verse(s.chapter, verse)
    val plain = Markup.plain(source)
    var speed by remember { mutableIntStateOf(0) }
    var playing by remember(verse, s.chapter, s.book) { mutableStateOf(false) }
    var lit by remember(verse, s.chapter, s.book) { mutableIntStateOf(0) }
    LaunchedEffect(playing, verse) {
        while (playing && lit < plain.length) {
            delay(ReadingPace.delayMillis(plain[lit], k, if (speed == 0) 1f else Tokens.Motion.aloudNormal)); lit++
        }
        if (lit >= plain.length) playing = false
    }
    VerseText(s, verse, source, lit = lit)
    UnderlineTabs(listOf(stringResource(R.string.aloud_slow), stringResource(R.string.aloud_normal)), speed, Modifier.padding(horizontal = Tokens.Space.s6)) { speed = it }
    if (lit >= plain.length) BookButton(stringResource(R.string.aloud_done), Modifier.fillMaxWidth()) { s.fill(listOf(verse), Mode.ALOUD) }
    else BookButton(stringResource(if (playing) R.string.aloud_pause else R.string.aloud_start), Modifier.fillMaxWidth()) { playing = !playing }
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
            BookButton(stringResource(R.string.close), Modifier.weight(1f), quiet = true) { s.shareVerse = null }
            BookButton(stringResource(R.string.share), Modifier.weight(1f)) { Cards.share(ctx, bmp, "verse"); s.shareVerse = null }
        }
    }
}
