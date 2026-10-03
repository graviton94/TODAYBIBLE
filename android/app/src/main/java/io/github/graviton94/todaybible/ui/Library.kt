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
import java.time.format.DateTimeFormatter

private val groupNames = mapOf(
    Group.LAW to ("율법서" to "Law"), Group.HISTORY to ("역사서" to "History"), Group.POETRY to ("시가서" to "Poetry"), Group.PROPHETS to ("선지서" to "Prophets"),
    Group.GOSPELS to ("복음서" to "Gospels"), Group.ACTS to ("사도행전" to "Acts"), Group.EPISTLES to ("서신서" to "Epistles"), Group.REVELATION to ("계시록" to "Revelation"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryPage(s: AppState) {
    val c = Theme.c; val k = s.korean
    val p = s.progress; val filled = p.filled(s.translation)
    val started = filled.map { VerseKey(it).book }.toSet()
    val doneBooks = remember(filled.size, s.translation) { started.filter { p.bookDone(s.translation, s.text(it)) }.toSet() }
    var group by remember { mutableStateOf(Canon.books[s.book].group) }
    val since = if (s.store.startDay >= 0) LocalDate.ofEpochDay(s.store.startDay).format(DateTimeFormatter.ofPattern(if (k) "yyyy. M. d" else "d MMM yyyy")) else null

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
        RunningHead(stringResource(R.string.my_bible), since?.let { stringResource(R.string.since, it) } ?: "", k)
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
            Text(stringResource(R.string.verses_n, filled.size), style = Theme.title(k, Tokens.Text.display))
            Text(stringResource(R.string.of_total, "%,d".format(s.translation.total)), style = Theme.small())
            // 오늘 쓴 절 · 이어 쓴 날 (주일은 쉬어도 이어짐)
            val today = s.today().toEpochDay()
            val todayN = s.fills.count { it.translation == s.translation && it.epochDay == today }
            val run = io.github.graviton94.todaybible.core.Presence.streak(p.days(), s.today())
            if (todayN > 0 || run > 0) Text(stringResource(R.string.today_streak, todayN, run), style = Theme.small().copy(color = c.rubric), maxLines = 1)
        }
        FindBox(s)
        Shelf(stringResource(R.string.old_testament), 0 until 39, s, started, doneBooks)
        Shelf(stringResource(R.string.new_testament), 39 until 66, s, started, doneBooks)

        // 묶음 색인 → 그 묶음의 권 목록
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Group.entries.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    row.forEach { g ->
                        val on = g == group
                        Box(
                            Modifier.weight(1f).heightIn(min = Tokens.Size.tab).clip(RoundedCornerShape(Tokens.Radius.chip))
                                .background(if (on) c.leather else c.paper).clickable(role = Role.Tab) { group = g },
                            contentAlignment = Alignment.Center,
                        ) { Text(if (k) groupNames[g]!!.first else groupNames[g]!!.second, style = Theme.small().copy(color = if (on) c.leatherInk else c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
        Column {
            Canon.inGroup(group).forEach { b ->
                val n = filled.count { VerseKey(it).book == b.index }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = Tokens.Size.row).clickable { if (s.locked(b.index)) { s.peekBook = b.index; s.purchaseOpen = true } else s.picker = b.index }
                        .drawBehind { drawLine(c.hair, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.hair.toPx()) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (k) b.ko else b.en, style = Theme.body(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (s.locked(b.index)) LockMark(c.unwritten, Modifier.size(Tokens.Size.lock))
                    else if (b.index in doneBooks) StampMark(STAMP_CROSS, c.giltText, Modifier.size(Tokens.Size.iconSm))
                    else Text(if (n > 0) stringResource(R.string.verses_n, n) else if (k) "${b.chapters}장" else "${b.chapters} ch.", style = Theme.small(), maxLines = 1)
                }
            }
        }
        BookButton(stringResource(R.string.continue_at, s.bookName(), s.chapter), Modifier.fillMaxWidth()) { s.open(s.book, s.chapter) }
    }

}

/** 책장 한 칸: 권마다 책등 하나. 높이 = 장 수(로그), 다 쓴 권 = 금박 띠, 쓰는 권 = 붉은 가죽, 시작 안 한 권 = 빈 자리. */
@Composable
private fun Shelf(label: String, range: IntRange, s: AppState, started: Set<Int>, done: Set<Int>) {
    val c = Theme.c
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = Theme.head(s.korean))
            Text("${range.count { it in done }} / ${range.count()}", style = Theme.small())
        }
        Row(
            Modifier.fillMaxWidth().height(Tokens.Size.shelf).drawBehind { val b = Tokens.Size.shelfBase.toPx(); drawRect(c.inkSoft, Offset(0f, size.height - b), Size(size.width, b)) }.padding(bottom = Tokens.Size.shelfBase),
            horizontalArrangement = Arrangement.spacedBy(Tokens.Size.spineGap), verticalAlignment = Alignment.Bottom,
        ) {
            range.forEach { i ->
                val ch = Canon.books[i].chapters
                val h = (0.45f + 0.55f * (kotlin.math.ln(ch.toFloat()) / kotlin.math.ln(150f))).coerceIn(0.4f, 1f)
                val state = when { i in done -> 2; i == s.book -> 3; i in started -> 1; else -> 0 }
                Box(Modifier.weight(1f).fillMaxHeight(h).drawBehind {
                    when (state) {
                        0 -> drawRect(c.hair, style = Stroke(Tokens.Stroke.hair.toPx()))
                        else -> drawRect(if (state == 3) c.rubric else c.leather)
                    }
                    if (state == 2) { val y = size.height * 0.16f; drawRect(c.gilt, Offset(size.width * 0.18f, y), Size(size.width * 0.64f, Tokens.Stroke.gilt.toPx())); drawRect(c.gilt, Offset(size.width * 0.18f, y + (Tokens.Size.bandGap + Tokens.Stroke.rule).toPx()), Size(size.width * 0.64f, Tokens.Stroke.gilt.toPx())) }
                })
            }
        }
    }
}

/** 장 고르기: 다 쓴 장 = 먹, 쓰는 중 = 붉은 테, 판화가 있는 장 = 금빛 점. */
@Composable
fun ChapterGrid(s: AppState, b: Int, onPick: (Int) -> Unit) {
    val c = Theme.c; val t = s.text(b); val p = s.progress
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Text(s.bookName(b), style = Theme.title(s.korean))
        Text(stringResource(R.string.choose_chapter), style = Theme.small())
        if (p.bookDone(s.translation, t)) BookButton(stringResource(R.string.my_bible_pdf), Modifier.fillMaxWidth(), quiet = true) { s.requestPdf(b) }
        val ctx = androidx.compose.ui.platform.LocalContext.current
        if ((1..t.chapterCount).any { Photos.of(ctx, s.translation.id, b, it) != null }) BookButton(stringResource(R.string.notes_pdf), Modifier.fillMaxWidth(), quiet = true) { s.requestNotes(b) }
        Column(Modifier.heightIn(max = Tokens.Size.sheetMaxGrid).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            (1..t.chapterCount).chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    row.forEach { ch ->
                        val f = p.chapterFraction(s.translation, t, ch)
                        val plate = s.store.plateFor(b, ch) != null
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(Tokens.Radius.chip)).background(if (f >= 1f) c.leather else c.paper)
                                .drawBehind {
                                    if (f >= 1f) giltFrame(c.gilt.copy(alpha = Tokens.Alpha.frame), bands = false)
                                    if (f in 0.0001f..0.9999f) drawRoundRect(c.rubric, style = Stroke(Tokens.Stroke.rule.toPx()), cornerRadius = CornerRadius(Tokens.Radius.chip.toPx()))
                                    if (plate) { val d = Tokens.Size.plateDot.toPx(); drawCircle(c.gilt, d / 2, Offset(size.width - d - Tokens.Space.s1.toPx(), d + Tokens.Space.s1.toPx())) }
                                }
                                .clickable(role = Role.Button) { onPick(ch) },
                            contentAlignment = Alignment.Center,
                        ) { Text("$ch", style = if (f >= 1f) Theme.number().copy(fontSize = Tokens.Text.gridInitial, color = c.gilt) else Theme.label()) }
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
    var q by remember { mutableStateOf("") }
    fun go() {
        val r = io.github.graviton94.todaybible.core.Reference.parse(q)
        if (r == null) { s.toast = ctx.getString(R.string.find_none); return }
        val (b, ch, v) = r
        s.open(b, ch)
        if (v != null && v in s.text(b).fillable(ch)) s.target = v
        q = ""
    }
    androidx.compose.foundation.text.BasicTextField(
        value = q, onValueChange = { q = it }, singleLine = true, textStyle = Theme.body(),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.rubric),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Go),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { go() }),
        modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.Size.row).clip(RoundedCornerShape(Tokens.Radius.chip)).background(c.paper).padding(horizontal = Tokens.Space.s4),
        decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.heightIn(min = Tokens.Size.row)) {
            if (q.isEmpty()) Text(stringResource(R.string.find_hint), style = Theme.body().copy(color = c.unwritten), maxLines = 1)
            inner()
        } },
    )
}
