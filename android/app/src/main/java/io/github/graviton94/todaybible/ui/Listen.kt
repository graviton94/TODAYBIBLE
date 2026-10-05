package io.github.graviton94.todaybible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.core.VerseKey
import io.github.graviton94.todaybible.data.ListenService
import io.github.graviton94.todaybible.design.Fonts
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens

/**
 * 성경 읽기 (웹북): 장 전체를 책처럼 펼쳐 읽고, 옆으로 밀면 앞 · 다음 장.
 * 절을 누르면 아래에 [형광펜 · 책갈피 · 보내기 · 여기부터 듣기]. 아래 띠는 [듣기] [이 장 필사하기].
 * 듣는 중이면 읽는 절에 붉은 줄이 서고 화면이 따라가요 (화면을 꺼도 계속 · 장이 끝나면 다음 장).
 * 듣기 · 읽기만 한 절은 채우지 않아요 (필사는 내가 써야).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BibleReader(s: AppState, book: Int, chapter: Int) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    BackHandler { s.listenAt = null }
    val now by ListenService.now.collectAsState()
    val origin by ListenService.origin.collectAsState()
    LeaveGuard(s, now != null && origin == "read", recording = false, onStop = { ListenService.stop(ctx); s.listenAt = null }, onKeep = { s.listenAt = null })
    val count = s.text(book).chapterCount
    val pager = rememberPagerState(initialPage = (chapter - 1).coerceIn(0, count - 1)) { count }
    // 목소리가 다음 장으로 넘어가면 따라가요
    var heardBook by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(now?.book, now?.chapter) {
        now?.let { n ->
            // 권 끝에서 다음 권으로 이어지면 읽기 화면도 그 권으로
            if (n.book != book && heardBook == book) s.listenAt = n.book to n.chapter
            else if (n.book == book && n.chapter != pager.currentPage + 1) pager.animateScrollToPage(n.chapter - 1)
            heardBook = n.book
        }
    }
    LaunchedEffect(chapter) { if (pager.currentPage != chapter - 1) pager.animateScrollToPage(chapter - 1) }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { if (it + 1 != s.listenAt?.second) s.listenAt = book to it + 1 } }
    var picked by remember(book) { mutableStateOf<Int?>(null) }
    val ch = pager.currentPage + 1
    LaunchedEffect(ch) { picked = null }
    val playing = now?.let { it.book == book && it.chapter == ch } == true

    Column(Modifier.fillMaxSize().background(c.leaf)) {
        // 머리: ‹ 목록 · 권 장 (‹ ›) · 책갈피
        Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s3, vertical = Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.read_back), style = Theme.small().copy(color = c.rubric), maxLines = 1,
                modifier = Modifier.heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Button) { s.listenAt = null }.padding(horizontal = Tokens.Space.s2))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Arrow(left = true, enabled = ch > 1) { s.listenAt = book to ch - 1 }
                Text(stringResource(R.string.listen_head, s.bookName(book), ch), style = Theme.head(k), maxLines = 1,
                    modifier = Modifier.clickable(role = Role.Button) { s.pickToRead = true; s.picker = book }.padding(horizontal = Tokens.Space.s1))
                Arrow(left = false, enabled = ch < count) { s.listenAt = book to ch + 1 }
            }
            // 한영 대조 켜고 끄기
            Text(stringResource(R.string.parallel_short), style = Theme.small().copy(color = if (s.parallel) c.rubric else c.inkSoft), maxLines = 1,
                modifier = Modifier.heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Switch) { s.flipParallel() }.padding(horizontal = Tokens.Space.s2))
            val marked = s.isBookmarked(book, ch)
            Row(Modifier.coach("read_bookmark").heightIn(min = Tokens.Size.tab).clickable(role = Role.Button) { s.toggleBookmark(book, ch, picked ?: 1) }.padding(horizontal = Tokens.Space.s2),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Ribbon(if (marked) c.rubric else c.inkSoft, marked, Modifier.size(Tokens.Size.iconSm))
                Text(stringResource(R.string.bookmark), style = Theme.small().copy(color = if (marked) c.rubric else c.inkSoft), maxLines = 1)
            }
        }
        // 장 넘김: 옆으로 확실히 밀 때만 (위아래 읽기가 먼저)
        val base = LocalViewConfiguration.current
        val wide = remember(base) { object : ViewConfiguration by base { override val touchSlop: Float get() = base.touchSlop * Tokens.Motion.sideSlop } }
        CompositionLocalProvider(LocalViewConfiguration provides wide) {
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth().coach("read_text"), beyondViewportPageCount = 0) { page ->
                CompositionLocalProvider(LocalViewConfiguration provides base) {
                    ChapterText(s, book, page + 1, now?.takeIf { it.book == book && it.chapter == page + 1 }?.verse,
                        picked.takeIf { page + 1 == ch }, s.readVerse.takeIf { page + 1 == chapter }) { v -> picked = if (picked == v) null else v }
                }
            }
        }
        // 절을 골랐으면: 형광펜 · 책갈피 · 보내기 · 여기부터 듣기
        picked?.let { v ->
            val key = VerseKey(book, ch, v)
            Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s2).clip(RoundedCornerShape(Tokens.Radius.card)).background(c.leather)) {
                listOf<Pair<Int, () -> Unit>>(
                    (if (s.isMarked(key)) R.string.mark_off else R.string.mark_on) to { s.toggleMark(key) },
                    R.string.bookmark to { s.toggleBookmark(book, ch, v) },
                    R.string.share to { s.shareVerse = key },
                    (if (s.isMemory(key)) R.string.memory_off else R.string.memory_on) to {
                        val adding = !s.isMemory(key); s.toggleMemory(key)
                        s.toast = ctx.getString(if (adding) R.string.memory_added else R.string.memory_removed)
                    },
                    R.string.listen_here to { ListenService.start(ctx, book, ch, v, s.aloudRate()) },
                ).forEach { (id, go) ->
                    Box(Modifier.weight(1f).heightIn(min = Tokens.Size.touch).clickable(role = Role.Button) { go(); picked = null }, contentAlignment = Alignment.Center) {
                        Text(stringResource(id), style = Theme.small().copy(color = c.leatherInk, textAlign = androidx.compose.ui.text.style.TextAlign.Center), maxLines = 2)
                    }
                }
            }
        }
        // 듣는 중: 빠르기 · 잠들기 타이머 (누를 때마다 다음 값)
        if (now != null) {
            val timer by ListenService.timer.collectAsState()
            Row(Modifier.fillMaxWidth().background(c.paper).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s1), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                val speedName = stringResource(when (s.aloudSpeed) { 0 -> R.string.aloud_slow; 2 -> R.string.aloud_fast; else -> R.string.aloud_normal })
                val timerName = when (timer) { 0 -> stringResource(R.string.reminder_off); ListenService.TIMER_CHAPTER -> stringResource(R.string.timer_chapter); else -> stringResource(R.string.timer_min, timer) }
                ListenChip(stringResource(R.string.listen_speed, speedName), Modifier.weight(1f)) { s.chooseAloudSpeed((s.aloudSpeed + 1) % 3); ListenService.setRate(ctx, s.aloudRate()) }
                ListenChip(stringResource(R.string.listen_timer, timerName), Modifier.weight(1f), on = timer != 0) {
                    val t = ListenService.TIMERS; ListenService.setTimer(ctx, t[(t.indexOf(timer) + 1) % t.size])
                }
            }
        }
        // 아래 띠: 듣기 · 이 장 필사하기
        Row(Modifier.fillMaxWidth().background(c.paper).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            BookButton(stringResource(if (playing) R.string.listen_stop else R.string.listen_start), Modifier.weight(1f).coach("read_listen")) {
                if (playing) ListenService.stop(ctx) else ListenService.start(ctx, book, ch, s.text(book).fillable(ch).firstOrNull() ?: 1, s.aloudRate())
            }
            BookButton(stringResource(R.string.copy_this), Modifier.weight(1f).coach("read_copy"), quiet = true) { ListenService.stop(ctx); s.open(book, ch) }
        }
    }
}

@Composable
private fun ListenChip(label: String, modifier: Modifier, on: Boolean = false, onClick: () -> Unit) {
    val c = Theme.c
    Box(modifier.heightIn(min = Tokens.Size.touch).clip(RoundedCornerShape(Tokens.Radius.chip)).background(if (on) c.leather else c.leaf).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Text(label, style = Theme.small().copy(color = if (on) c.leatherInk else c.ink), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChapterText(s: AppState, book: Int, ch: Int, voiceAt: Int?, picked: Int?, jump: Int?, onTap: (Int) -> Unit) {
    val c = Theme.c; val k = s.korean
    val t = s.text(book); val verses = t.fillable(ch)
    val list = rememberLazyListState()
    // 첫 칸은 장 머리글자라 절 자리는 하나 뒤
    LaunchedEffect(voiceAt) { voiceAt?.let { v -> verses.indexOf(v).takeIf { it >= 0 }?.let { list.animateScrollToItem(it) } } }
    LaunchedEffect(jump) { jump?.let { v -> verses.indexOf(v).takeIf { it >= 0 }?.let { list.scrollToItem(it + 1); s.readVerse = null } } }
    val filled = s.progress.filled(s.translation)
    // 한영 대조 (I2): 다른 번역의 같은 절을 아래에 작게 (절 번호가 없는 곳은 비워요)
    val otherTr = if (s.translation == io.github.graviton94.todaybible.core.Translation.KRV) s.store.englishBible else io.github.graviton94.todaybible.core.Translation.KRV
    val other = if (s.parallel) remember(book, otherTr) { s.store.book(otherTr, book) } else null
    LazyColumn(state = list, modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        item { ChapterInitial(ch, false, Modifier.padding(bottom = Tokens.Space.s1)) }
        items(verses, key = { it }) { v ->
            val key = VerseKey(book, ch, v)
            val marked = s.isMarked(key)
            val dim = voiceAt != null && voiceAt != v
            Text(buildAnnotatedString {
                withStyle(SpanStyle(fontFamily = Fonts.black, color = c.rubric, fontSize = Tokens.Leading.verseNumber.em)) { append("$v ") }
                withStyle(SpanStyle(color = if (dim) c.inkSoft else c.ink, background = if (marked) c.mark else androidx.compose.ui.graphics.Color.Unspecified)) { append(Markup.plain(t.verse(ch, v))) }
            }, style = Theme.verse(k), modifier = Modifier.fillMaxWidth()
                .drawBehind {
                    // 고른 절 · 듣는 절: 왼쪽 붉은 줄 / 내가 쓴 절: 금빛 점
                    if (v == picked || v == voiceAt) drawRect(c.rubric, Offset(-Tokens.Space.s3.toPx(), 0f), Size(Tokens.Stroke.rule.toPx(), size.height))
                    else if (key.raw in filled) drawCircle(c.gilt, Tokens.Size.dot.toPx() / 2, Offset(-Tokens.Space.s3.toPx(), Tokens.Size.dot.toPx() * 1.5f))
                }
                .clickable(role = Role.Button) { onTap(v) })
            other?.takeIf { ch <= it.chapterCount && v <= it.verseCount(ch) }?.verse(ch, v)?.takeIf { it.isNotBlank() }?.let { o ->
                Text(Markup.plain(o), style = Theme.verse(!k).copy(fontSize = Theme.body().fontSize, color = c.inkSoft), modifier = Modifier.fillMaxWidth().padding(bottom = Tokens.Space.s2))
            }
        }
        item { Text(stringResource(R.string.read_hint), style = Theme.small(), modifier = Modifier.padding(top = Tokens.Space.s4)) }
    }
}

@Composable
private fun Arrow(left: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = Theme.c
    val label = stringResource(if (left) R.string.listen_prev else R.string.listen_next)
    Box(Modifier.size(Tokens.Size.tab).semantics(mergeDescendants = true) { contentDescription = label }.clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(if (left) "‹" else "›", style = Theme.title(true).copy(color = if (enabled) c.ink else c.hair))
    }
}

/** 책갈피 리본. */
@Composable
fun Ribbon(color: androidx.compose.ui.graphics.Color, filled: Boolean, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width * 0.62f; val x = (size.width - w) / 2; val h = size.height
        val p = Path().apply { moveTo(x, 0f); lineTo(x + w, 0f); lineTo(x + w, h); lineTo(x + w / 2, h * 0.72f); lineTo(x, h); close() }
        if (filled) drawPath(p, color) else drawPath(p, color, style = androidx.compose.ui.graphics.drawscope.Stroke(Tokens.Stroke.rule.toPx()))
    }
}
