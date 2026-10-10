package io.github.graviton94.todaybible.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.core.Memorize
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens

/** 밑줄 한 줄 (또는 몇 줄) 적는 칸. 비어 있으면 옅은 안내. */
@Composable
fun LineField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, lines: Int = 1, max: Int = 200) {
    val c = Theme.c
    BasicTextField(
        value = value, onValueChange = { if (it.length <= max) onChange(if (lines == 1) it.replace('\n', ' ') else it) },
        singleLine = lines == 1, minLines = lines, textStyle = Theme.body(), cursorBrush = SolidColor(c.rubric),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.fillMaxWidth().heightIn(min = Tokens.Size.row)
            .drawBehind { drawLine(c.inkSoft, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.hair.toPx()) },
        decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (value.isEmpty()) Text(hint, style = Theme.body().copy(color = c.unwritten)); inner() } },
    )
}

/** 장을 마친 뒤 마음에 남은 한 줄 (A4). 적지 않아도 돼요. 나의 성경 PDF 의 그 장 끝에 들어가요. */
@Composable
fun ReflectionField(s: AppState, book: Int, chapter: Int, text: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
        Text(stringResource(R.string.reflect_title), style = Theme.small())
        LineField(text, onChange, stringResource(R.string.reflect_hint), max = 120)
    }
}

/**
 * 마음에 새기는 말씀 (A3): 한 절을 크게 두고, 누를 때마다 글자를 조금 더 가려 보며 되뇌어요.
 * 가려진 낱말은 누르면 살짝 보여요. 점수 · 횟수 · 다시 묻기는 없어요.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MemorySheet(s: AppState) {
    val c = Theme.c; val k = s.korean
    val key = s.memoryOpen ?: return
    BookSheet({ s.memoryOpen = null }) {
        val plain = remember(key, s.translation) { Markup.plain(s.store.book(s.translation, key.book).verse(key.chapter, key.verse)) }
        var level by remember(key) { mutableStateOf(s.memoryStartLevel) }
        var peek by remember(key, level) { mutableStateOf(setOf<Int>()) }
        Text(stringResource(R.string.memory_title), style = Theme.small().copy(color = c.rubric))
        Text(stringResource(R.string.ref_verse, s.bookName(key.book), key.chapter, key.verse), style = Theme.title(k))
        FlowRow(Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s3), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Memorize.words(plain, level).forEachIndexed { i, w ->
                val open = !w.hidden || i in peek
                Text(if (open) w.text else w.shown, style = Theme.verse(k).copy(color = if (w.hidden && !open) c.unwritten else c.ink),
                    modifier = if (w.hidden) Modifier.clip(RoundedCornerShape(Tokens.Radius.chip)).clickable(role = Role.Button) { peek = if (i in peek) peek - i else peek + i } else Modifier)
            }
        }
        Text(stringResource(if (level == 0) R.string.memory_hint else R.string.memory_peek), style = Theme.small())
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            BookButton(stringResource(when (level) { 0 -> R.string.memory_hide; Memorize.LEVELS - 1 -> R.string.memory_show; else -> R.string.memory_more }), Modifier.weight(1f)) {
                level = (level + 1) % Memorize.LEVELS
            }
            BookButton(stringResource(R.string.memory_read), Modifier.weight(1f), quiet = true) { s.memoryOpen = null; s.read(key.book, key.chapter, key.verse) }
        }
        Text(stringResource(R.string.memory_remove), style = Theme.small().copy(color = c.inkSoft),
            modifier = Modifier.heightIn(min = Tokens.Size.touch).clickable(role = Role.Button) { s.toggleMemory(key); s.memoryOpen = null }.padding(vertical = Tokens.Space.s2))
    }
}

/** 마음에 새기는 말씀 목록 (책갈피 · 형광펜 창 안). */
@Composable
fun MemoryList(s: AppState, onOpen: () -> Unit) {
    val c = Theme.c
    val list = s.memory.filter { it.translation == s.translation }.sortedByDescending { it.epochDay }
    Text(stringResource(R.string.memory_title), style = Theme.title(s.korean), modifier = Modifier.padding(top = Tokens.Space.s3))
    if (list.isEmpty()) Text(stringResource(R.string.memory_none), style = Theme.small())
    list.forEach { m ->
        val v = m.key
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(); s.memoryOpen = v }.padding(vertical = Tokens.Space.s1),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
            Text(Markup.plain(s.store.book(s.translation, v.book).verse(v.chapter, v.verse)), style = Theme.body(), maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(stringResource(R.string.ref_verse, s.bookName(v.book), v.chapter, v.verse), style = Theme.small().copy(color = c.rubric), maxLines = 1)
        }
    }
}

/**
 * 주일 설교 노트 (A2): 본문 장절 · 설교에서 남은 말. 본문은 곧바로 필사하거나 읽을 수 있어요.
 * 하루에 하나, 창을 닫을 때 남겨요. 나의 성경 PDF 끝에 그 권의 설교 노트가 모여요.
 */
@Composable
fun SermonSheet(s: AppState) {
    val c = Theme.c; val k = s.korean
    val day = s.sermonOpen ?: return
    val old = remember(day) { s.sermonOn(day) }
    var ref by remember(day) { mutableStateOf(old?.takeIf { it.from > 0 }?.let { s.passageLabel(it) } ?: "") }
    var text by remember(day) { mutableStateOf(old?.text ?: "") }
    val p = remember(ref) { io.github.graviton94.todaybible.core.Reference.passage(ref) }
    fun save() {
        s.putSermon(io.github.graviton94.todaybible.data.Store.Note(io.github.graviton94.todaybible.data.Store.NoteKind.SERMON, s.translation,
            p?.book ?: 0, p?.chapter ?: 1, p?.from ?: 0, p?.to ?: 0, day, text.trim()))
    }
    fun close() { save(); s.sermonOpen = null }
    BookSheet({ close() }) {
        Column(Modifier.heightIn(max = Tokens.Size.sheetMaxGrid * 2).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Text(stringResource(R.string.sermon_title), style = Theme.title(k))
            Text(fmtDate(R.string.fmt_date_full, java.time.LocalDate.ofEpochDay(day)), style = Theme.small())
            Text(stringResource(R.string.sermon_passage), style = Theme.small().copy(color = c.rubric))
            LineField(ref, { ref = it }, stringResource(R.string.sermon_passage_hint), max = 40)
            if (p != null) {
                val t = s.store.book(s.translation, p.book)
                val verses = t.fillable(p.chapter).filter { it >= p.from && (p.to == 0 || it <= p.to) }
                Text(Lang.passage(s.store.context, s.translation, s.bookName(p.book), p.chapter, p.from, p.to), style = Theme.label())
                verses.take(3).forEach { v -> Text("$v  " + Markup.plain(t.verse(p.chapter, v)), style = Theme.body().copy(color = c.inkSoft), maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    BookButton(stringResource(R.string.sermon_copy), Modifier.weight(1f)) { close(); s.open(p.book, p.chapter); if (!s.locked(p.book)) s.target = verses.firstOrNull { !s.progress.isFilled(s.translation, io.github.graviton94.todaybible.core.VerseKey(p.book, p.chapter, it)) } ?: p.from }
                    BookButton(stringResource(R.string.read_short), Modifier.weight(1f), quiet = true) { close(); s.read(p.book, p.chapter, p.from) }
                }
            } else if (ref.isNotBlank()) Text(stringResource(R.string.sermon_passage_bad), style = Theme.small())
            Text(stringResource(R.string.sermon_note), style = Theme.small().copy(color = c.rubric), modifier = Modifier.padding(top = Tokens.Space.s2))
            LineField(text, { text = it }, stringResource(R.string.sermon_note_hint), lines = 5, max = 2000)
            BookButton(stringResource(R.string.close), Modifier.fillMaxWidth(), quiet = true) { close() }
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.ime))
        }
    }
}

/** 오늘 화면의 주일 칸: 설교 본문 적기 · 적은 노트 한 줄. */
@Composable
fun SermonCard(s: AppState, day: Long) {
    val c = Theme.c
    val n = s.sermonOn(day)
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { s.sermonOpen = day }.padding(vertical = Tokens.Space.s3),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
        Text(stringResource(R.string.sermon_title), style = Theme.small().copy(color = c.rubric), maxLines = 1)
        if (n == null) Text(stringResource(R.string.sermon_invite), style = Theme.body())
        else {
            if (n.from > 0) Text(s.passageLabel(n), style = Theme.label(), maxLines = 1)
            if (n.text.isNotBlank()) Text(n.text.lineSequence().first(), style = Theme.body().copy(color = c.inkSoft), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Text(stringResource(if (n == null) R.string.sermon_write else R.string.sermon_continue), style = Theme.small().copy(color = c.rubric), maxLines = 1)
    }
}
