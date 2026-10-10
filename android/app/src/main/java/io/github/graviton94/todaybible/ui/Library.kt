package io.github.graviton94.todaybible.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Canon
import io.github.graviton94.todaybible.core.Group
import io.github.graviton94.todaybible.core.VerseKey
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import java.time.LocalDate
import kotlinx.coroutines.launch

private val groupNames = mapOf(
    Group.LAW to R.string.group_law, Group.HISTORY to R.string.group_history, Group.POETRY to R.string.group_poetry, Group.PROPHETS to R.string.group_prophets,
    Group.GOSPELS to R.string.group_gospels, Group.ACTS to R.string.group_acts, Group.EPISTLES to R.string.group_epistles, Group.REVELATION to R.string.group_revelation,
)

/** 권마다 쓴 정도 (뒤에서 셈): 다 쓴 장 · 쓴 절 · 전체 절 · 쓰다 멈춘 첫 장. */
private data class BookState(val doneChapters: Int, val verses: Int, val total: Int, val partial: Int?, val lastAt: Long) {
    val done get() = total > 0 && verses >= total
    val fraction get() = if (total == 0) 0f else verses / total.toFloat()
}

/**
 * 성경 탭: 고른 장이 있으면 읽기 화면, 없으면 서재.
 * 서재 = 이어 쓰던 책 · 내 책갈피와 형광펜 · 장절 찾기 · 구약/신약 칸 · 갈래별 권 (한 줄 소개 · 진행).
 * 권을 누르면 장 고르기 → 읽기 화면 (곧바로 필사로 가지 않아요).
 */
@Composable
fun LibraryPage(s: AppState) {
    s.listenAt?.let { (b, ch) -> androidx.compose.runtime.key(b) { BibleReader(s, b, ch) }; return }
    val c = Theme.c; val k = s.korean; val ctx = androidx.compose.ui.platform.LocalContext.current
    val p = s.progress; val filled = p.filled(s.translation)
    // 시작한 권만 본문을 읽어 셈 (뒤에서)
    val states by androidx.compose.runtime.produceState(emptyMap<Int, BookState>(), filled.size, s.translation) {
        val tr = s.translation; val fills = s.fills.toList()
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            filled.map { VerseKey(it).book }.toSet().associateWith { b ->
                val t = s.store.book(tr, b)
                val chapters = (1..t.chapterCount).map { p.chapterFraction(tr, t, it) }
                BookState(chapters.count { it >= 1f }, filled.count { VerseKey(it).book == b }, t.fillableTotal,
                    chapters.indexOfFirst { it > 0f && it < 1f }.takeIf { it >= 0 }?.plus(1),
                    fills.filter { it.translation == tr && it.key.book == b }.maxOfOrNull { it.atMillis } ?: 0L)
            }
        }
    }
    var newT by remember { mutableStateOf(s.book >= 39) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5).padding(top = Tokens.Space.s3, bottom = Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
        // 머리: 라틴 머리글 · 제목 · 쓴 절
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
            Text("BIBLIA SACRA", style = Theme.caps(), maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.page_library), style = Theme.title(k, Tokens.Text.display), maxLines = 1, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.lib_count, filled.size, "%,d".format(s.translation.total)), style = Theme.small(), maxLines = 1)
            }
        }
        FindBox(s)
        // 이어 쓰던 책 (최근 순, 셋까지): 어디서 멈췄는지 · 가는 진행선
        val going = states.filter { !it.value.done }.entries.sortedByDescending { it.value.lastAt }.take(3)
        if (going.isNotEmpty()) Column {
            Text("PERGERE · " + stringResource(R.string.lib_going), style = Theme.caps().copy(color = c.inkSoft), maxLines = 1, modifier = Modifier.padding(bottom = Tokens.Space.s1))
            going.forEach { (b, st) ->
                val at = st.partial ?: (s.store.book(s.translation, b).let { t -> (1..t.chapterCount).firstOrNull { p.chapterFraction(s.translation, t, it) < 1f } } ?: 1)
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { s.open(b, at) }.padding(vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(s.bookName(b), style = Theme.title(k), maxLines = 1, modifier = Modifier.weight(1f))
                        Text(stringResource(R.string.lib_ch_of, st.doneChapters, Canon.books[b].chapters), style = Theme.small(), maxLines = 1)
                    }
                    Text(if (st.partial != null) stringResource(R.string.lib_partial, st.partial) else stringResource(R.string.lib_next, at), style = Theme.small(), maxLines = 1)
                    Box(Modifier.padding(top = Tokens.Space.s1)) { Progress(st.fraction) }
                }
                Hair()
            }
        }
        // 할 수 있는 일: 줄 목록
        Column {
            val nb = s.bookmarks.count { it.translation == s.translation }; val nm = s.marks.count { it.translation == s.translation }
            Hair()
            ListRow(stringResource(R.string.prayer_title), stringResource(R.string.prayer_row_short)) { s.prayersOpen = true }
            Box(Modifier.coach("lib_marks")) { ListRow(stringResource(R.string.lib_marks_title), "$nb · $nm") { s.marksOpen = true } }
            ListRow(stringResource(R.string.plan), s.plan?.let { planName(ctx, it.id) } ?: stringResource(R.string.plan_none_short)) { s.planOpen = true }
        }
        // 구약 · 신약
        Row(Modifier.fillMaxWidth().coach("lib_testament").drawBehind { drawLine(c.hair, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.hair.toPx()) }) {
            listOf(false to stringResource(R.string.old_count), true to stringResource(R.string.new_count)).forEach { (nt, label) ->
                val on = newT == nt
                Box(Modifier.weight(1f).heightIn(min = Tokens.Size.touch).clickable(role = Role.Tab) { newT = nt }.drawBehind {
                    if (on) drawRect(c.gilt, Offset(0f, size.height - Tokens.Stroke.rule.toPx()), Size(size.width, Tokens.Stroke.rule.toPx()))
                }, contentAlignment = Alignment.CenterStart) { Text(label, style = Theme.body().copy(color = if (on) c.ink else c.unwritten), maxLines = 1) }
            }
        }
        Group.entries.filter { g -> Canon.inGroup(g).firstOrNull()?.let { (it.index >= 39) == newT } == true }.forEach { g ->
            Column {
                Text(stringResource(groupNames.getValue(g)).uppercase(), style = Theme.caps().copy(color = c.inkSoft), maxLines = 1, modifier = Modifier.padding(top = Tokens.Space.s2, bottom = Tokens.Space.s1))
                Canon.inGroup(g).forEach { b ->
                    val st = states[b.index]
                    val aboutId = remember(b.index) { ctx.resources.getIdentifier("about_%02d".format(b.index + 1), "string", ctx.packageName) }
                    Column(Modifier.fillMaxWidth().then(if (b.index == Canon.inGroup(g).first().index && g == Group.entries.first { gg -> Canon.inGroup(gg).firstOrNull()?.let { (it.index >= 39) == newT } == true }) Modifier.coach("lib_book") else Modifier)
                        .clickable(role = Role.Button) { if (s.locked(b.index)) { s.peekBook = b.index; s.purchaseOpen = true } else { s.pickToRead = true; s.picker = b.index } }
                        .padding(vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                            Text(if (k) b.ko else b.en, style = Theme.title(k, Tokens.Text.label).copy(color = if (st == null) c.inkSoft else c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (s.premium(b.index) && st?.done != true) PremiumTag()
                            when {
                                st?.done == true -> Text(stringResource(R.string.lib_done), style = Theme.small().copy(color = c.giltText), maxLines = 1)
                                st != null -> Text(stringResource(R.string.lib_ch_of, st.doneChapters, b.chapters), style = Theme.small(), maxLines = 1)
                                else -> Text(stringResource(R.string.lib_chapters, b.chapters), style = Theme.small().copy(color = c.unwritten), maxLines = 1)
                            }
                        }
                        if (aboutId != 0) Text(stringResource(aboutId), style = Theme.small(), maxLines = 2)
                        if (st != null && !st.done) Box(Modifier.padding(top = Tokens.Space.s1)) { Progress(st.fraction) }
                    }
                    Hair()
                }
            }
        }
    }
}

/** 가는 진행 막대. */
@Composable
private fun Progress(f: Float) {
    val c = Theme.c
    // 가는 선 (하루의 편지): 2dp, 금빛
    Box(Modifier.fillMaxWidth().height(Tokens.Stroke.rule).background(c.hair)) {
        Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).height(Tokens.Stroke.rule).background(c.gilt))
    }
}

/** 책갈피 · 형광펜 모아 보기: 누르면 그 자리를 읽기 화면에서. */
@Composable
fun MarksSheet(s: AppState) {
    val c = Theme.c; val k = s.korean
    BookSheet({ s.marksOpen = false }) {
        val bm = s.bookmarks.filter { it.translation == s.translation }.sortedByDescending { it.epochDay }
        val hl = s.marks.filter { it.translation == s.translation }.sortedByDescending { it.epochDay }
        Column(Modifier.heightIn(max = Tokens.Size.sheetMaxGrid * 2).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Text(stringResource(R.string.bookmark), style = Theme.title(k))
            if (bm.isEmpty()) Text(stringResource(R.string.marks_none_bm), style = Theme.small())
            bm.forEach { m ->
                Row(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.row).clickable(role = Role.Button) { s.marksOpen = false; s.read(m.key.book, m.key.chapter, m.key.verse) },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    Ribbon(c.rubric, true, Modifier.size(Tokens.Size.iconSm))
                    Text(stringResource(R.string.listen_head, s.bookName(m.key.book), m.key.chapter), style = Theme.body(), modifier = Modifier.weight(1f))
                }
            }
            Text(stringResource(R.string.marks_title), style = Theme.title(k), modifier = Modifier.padding(top = Tokens.Space.s3))
            if (hl.isEmpty()) Text(stringResource(R.string.marks_none_hl), style = Theme.small())
            hl.forEach { m ->
                val v = m.key
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { s.marksOpen = false; s.read(v.book, v.chapter, v.verse) }.padding(vertical = Tokens.Space.s1),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                    Text(io.github.graviton94.todaybible.core.Markup.plain(s.store.book(s.translation, v.book).verse(v.chapter, v.verse)),
                        style = Theme.body().copy(background = c.mark), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.ref_verse, s.bookName(v.book), v.chapter, v.verse), style = Theme.small(), maxLines = 1)
                }
            }
            MemoryList(s) { s.marksOpen = false }
        }
    }
}

private const val FOUND_MAX = 200
/** 낭독 받아 두기 크기 어림 (장마다 0.4MB, 10분의 1 MB 단위). */
private const val KEEP_MB_PER_CH = 4

/** 낱말로 찾은 절: 찾은 말에 형광, 누르면 읽기 화면의 그 절로. */
@Composable
fun FoundSheet(s: AppState) {
    val c = Theme.c; val (word, hits) = s.found ?: return
    BookSheet({ s.found = null }) {
        if (hits == null) { Text(stringResource(R.string.found_searching), style = Theme.small()); return@BookSheet }
        Text(stringResource(R.string.found_title, word, hits.size), style = Theme.title(s.korean), maxLines = 2)
        if (hits.size >= FOUND_MAX) Text(stringResource(R.string.found_more, FOUND_MAX), style = Theme.small())
        val words = word.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = Tokens.Size.sheetMaxGrid * 2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            items(hits.size) { i ->
                val h = hits[i]
                Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { s.found = null; s.read(h.book, h.chapter, h.verse) }.padding(vertical = Tokens.Space.s1),
                    verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                    Text(androidx.compose.ui.text.buildAnnotatedString {
                        append(h.text); val low = h.text.lowercase()
                        words.forEach { w -> var at = low.indexOf(w); while (at >= 0) { addStyle(androidx.compose.ui.text.SpanStyle(background = c.mark), at, at + w.length); at = low.indexOf(w, at + w.length) } }
                    }, style = Theme.body(), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.ref_verse, s.bookName(h.book), h.chapter, h.verse), style = Theme.small().copy(color = c.rubric), maxLines = 1)
                }
            }
        }
    }
}

/** 장 고르기: 다 쓴 장 = 먹, 쓰는 중 = 붉은 테, 판화가 있는 장 = 금빛 점. */
@Composable
fun ChapterGrid(s: AppState, b: Int, onPick: (Int) -> Unit) {
    val c = Theme.c; val t = s.text(b); val p = s.progress
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Text(io.github.graviton94.todaybible.core.Latin.book(b), style = Theme.caps(), maxLines = 1)
        Text(s.bookName(b), style = Theme.title(s.korean, Tokens.Text.display))
        Text(stringResource(R.string.choose_chapter), style = Theme.small())
        Text(stringResource(R.string.grid_legend), style = Theme.small().copy(color = c.inkSoft))
        if (p.bookDone(s.translation, t)) BookButton(stringResource(R.string.my_bible_pdf), Modifier.fillMaxWidth(), locked = s.premiumOn, quiet = true) { s.requestPdf(b) }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        // 내 목소리로 읽은 장이 있으면: 한 권 오디오북
        val voiced by androidx.compose.runtime.produceState(0, b, s.voiceRev) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { (1..t.chapterCount).count { io.github.graviton94.todaybible.data.Voice.verses(ctx, s.translation.id, b, it).isNotEmpty() } }
        }
        // 낭독 받아 두기: 인터넷 없는 곳에서도 이 권을 들어요 (폰 목소리면 필요 없음)
        if (s.narrator != io.github.graviton94.todaybible.data.Narration.DEVICE) {
            val keepRun = s.keeping?.takeIf { it.first == b }
            val kept = remember(b, s.keeping, s.narrator) { io.github.graviton94.todaybible.data.Narration.kept(ctx, s.narrator, b) }
            when {
                keepRun != null -> Text(stringResource(R.string.keep_running, keepRun.second, keepRun.third), style = Theme.small().copy(color = c.rubric))
                kept -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.keep_kept), style = Theme.small(), modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.keep_release), style = Theme.small().copy(color = c.rubric), modifier = Modifier.heightIn(min = Tokens.Size.tab).wrapContentHeight()
                        .clickable(role = Role.Button) { io.github.graviton94.todaybible.data.Narration.release(ctx, s.narrator, b); s.toast = ctx.getString(R.string.keep_released) })
                }
                else -> BookButton(stringResource(R.string.keep_book, t.chapterCount * KEEP_MB_PER_CH / 10), Modifier.fillMaxWidth(), quiet = true, enabled = s.keeping == null) { s.keepNarration(b) }
            }
        }
        if (voiced > 0) BookButton(stringResource(R.string.audiobook_make, voiced), Modifier.fillMaxWidth(), locked = s.premiumOn, quiet = true, enabled = !s.exporting) { s.picker = null; s.requestAudiobook(b) }
        if (io.github.graviton94.todaybible.data.Ink.chapters(ctx, s.translation.id, b).isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            BookButton(stringResource(R.string.hand_book_open), Modifier.weight(1f), quiet = true) { s.picker = null; s.handBook = b }
            BookButton(stringResource(R.string.notes_pdf), Modifier.weight(1f), locked = s.premiumOn, quiet = true) { s.requestNotes(b) }
        }
        Column(Modifier.heightIn(max = Tokens.Size.sheetMaxGrid).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            (1..t.chapterCount).chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    row.forEach { ch ->
                        val f = p.chapterFraction(s.translation, t, ch)
                        val plate = s.store.plateFor(b, ch) != null
                        // 숫자만 (상자 없음): 다 쓴 장은 금빛 밑줄, 쓰는 중은 굵게 + 짧은 선, 판화가 있으면 위에 작은 금빛 점
                        Box(
                            Modifier.weight(1f).aspectRatio(1f)
                                .drawBehind {
                                    val u = Tokens.Stroke.rule.toPx(); val w = size.width * 0.42f; val y = size.height * 0.78f
                                    if (f >= 1f) drawLine(c.gilt, Offset((size.width - w) / 2, y), Offset((size.width + w) / 2, y), u)
                                    else if (f > 0f) drawLine(c.gilt, Offset((size.width - w) / 2, y), Offset((size.width - w) / 2 + w * f, y), u)
                                    if (plate) { val d = Tokens.Size.plateDot.toPx(); drawCircle(c.gilt, d / 2, Offset(size.width / 2, size.height * 0.16f)) }
                                }
                                .clickable(role = Role.Button) { onPick(ch) },
                            contentAlignment = Alignment.Center,
                        ) { Text("$ch", style = Theme.big(Tokens.Text.gridNum).copy(color = when { f >= 1f -> c.giltText; f > 0f -> c.ink; else -> c.inkSoft }), maxLines = 1) }
                    }
                    repeat(6 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 장절 찾기: "요 3:16" 처럼 쓰고 들어가면 그 절로. */
@Composable
private fun FindBox(s: AppState) {
    val c = Theme.c; val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    // 장절이면 그 자리로, 아니면 낱말로 찾기
    fun go() {
        val r = io.github.graviton94.todaybible.core.Reference.parse(q)
        if (r != null) {
            val (b, ch, v) = r
            s.read(b, ch, v?.takeIf { it in s.text(b).fillable(ch) }); q = ""; return
        }
        val word = q.trim()
        if (word.length < 2) { s.toast = ctx.getString(if (word.isEmpty()) R.string.find_none else R.string.find_short); return }
        s.found = word to null
        val tr = s.translation
        scope.launch {
            val hits = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                io.github.graviton94.todaybible.core.Search.find((0 until 66).asSequence().map { s.store.book(tr, it) }, word, FOUND_MAX)
            }
            if (s.found?.first == word) { if (hits.isEmpty()) { s.found = null; s.toast = ctx.getString(R.string.find_none) } else s.found = word to hits }
        }
    }
    androidx.compose.foundation.text.BasicTextField(
        value = q, onValueChange = { q = it }, singleLine = true, textStyle = Theme.body(),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.rubric),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Go),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { go() }),
        // 상자 없이 밑줄 한 줄 (하루의 편지)
        modifier = Modifier.fillMaxWidth().coach("lib_find").heightIn(min = Tokens.Size.row).drawBehind { drawLine(c.inkSoft.copy(alpha = 0.5f), Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.hair.toPx()) },
        decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.heightIn(min = Tokens.Size.row)) {
            if (q.isEmpty()) Text(stringResource(R.string.find_hint), style = Theme.body().copy(color = c.unwritten), maxLines = 1)
            inner()
        } },
    )
}
