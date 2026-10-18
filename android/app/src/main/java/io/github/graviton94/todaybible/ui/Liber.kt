package io.github.graviton94.todaybible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Canon
import io.github.graviton94.todaybible.core.Latin
import io.github.graviton94.todaybible.core.Mode
import io.github.graviton94.todaybible.core.VerseKey
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import java.time.LocalDate

/** 권마다 쓴 만큼 (0..1). 기록이 있는 권만 열어 봐요. */
@Composable
fun bookFractions(s: AppState): Map<Int, Float> {
    val filled = s.progress.filled(s.translation)
    val v by produceState(emptyMap<Int, Float>(), filled.size, s.translation) {
        val tr = s.translation
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            filled.groupingBy { VerseKey(it).book }.eachCount().mapValues { (b, n) -> (n.toFloat() / s.store.book(tr, b).fillableTotal).coerceAtMost(1f) }
        }
    }
    return v
}

/** 구약 · 신약 안에서 몇째 권 (1부터). */
fun bookOrdinal(b: Int) = if (b >= Canon.NT_START) b - Canon.NT_START + 1 else b + 1

/**
 * 책등 하나 (1.4): 위에 로마 숫자 (구약 · 신약 안에서 몇째 권), 아래에 권 이름.
 * 한국어는 글자를 세로로 바로 세우고, 영어는 옆으로 눕혀요. 이름이 길면 줄바꿈 · 말줄임 없이 글자를 줄여 책등 안에 맞춰요.
 * 다 쓴 권 = 흑단 + 금박 띠, 쓰는 중 = 아래부터 쓴 만큼 금빛, 아직 = 종이.
 */
@Composable
fun Spine(s: AppState, b: Int, f: Float, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = Theme.c; val dens = LocalDensity.current
    val done = f >= 1f
    val ink = when { done -> c.foil; f > 0f -> c.ink; else -> c.inkSoft }
    val name = s.bookName(b)
    Column(
        modifier.height(Tokens.Size.spine).drawBehind {
            if (done) {
                drawRect(c.ebony)
                val w = Tokens.Stroke.hair.toPx(); val band = Tokens.Size.spineBand.toPx()
                drawRect(c.foil, style = Stroke(w))
                drawLine(c.foil, Offset(0f, band), Offset(size.width, band), w); drawLine(c.foil, Offset(0f, size.height - band), Offset(size.width, size.height - band), w)
            } else {
                drawRect(c.paper)
                if (f > 0f) drawRect(c.gilt.copy(alpha = Tokens.Alpha.spineFill), Offset(0f, size.height * (1f - f)), Size(size.width, size.height * f))
                drawRect(c.hair, style = Stroke(Tokens.Stroke.hair.toPx()))
            }
        }.clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = name }
            .padding(top = Tokens.Space.s2, bottom = Tokens.Space.s2),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1),
    ) {
        Text(Latin.roman(bookOrdinal(b)), style = Theme.caps().copy(fontSize = Tokens.Text.spineNum, color = if (done) c.foil else c.giltText, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified),
            maxLines = 1, softWrap = false)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val availH = with(dens) { maxHeight.toPx() }; val availW = with(dens) { maxWidth.toPx() }
            if (s.korean) {
                // 세로로 한 글자씩: 글자 수에 맞춰 크기를 줄여서라도 다 들어가게
                val base = with(dens) { Tokens.Text.spineKo.toPx() }
                val px = minOf(base, availH / (name.length * Tokens.Ratio.spineLine), availW * 0.9f)
                val sp = with(dens) { px.toSp() }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    name.forEach { ch ->
                        Text(ch.toString(), style = Theme.small().copy(fontSize = sp, lineHeight = sp * Tokens.Ratio.spineLine, color = ink, fontWeight = if (done || f > 0f) FontWeight.SemiBold else FontWeight.Medium, textAlign = TextAlign.Center), maxLines = 1, softWrap = false)
                    }
                }
            } else {
                // 옆으로 눕혀서: 이름 너비를 재어 책등 높이에 맞게
                val measurer = rememberTextMeasurer()
                val style = Theme.verse(false).copy(fontSize = Tokens.Text.spineEn, color = ink, fontWeight = FontWeight.SemiBold)
                val w = remember(name, style) { measurer.measure(name, style, maxLines = 1, softWrap = false).size.width.toFloat() }
                val scale = minOf(1f, availH * 0.96f / w.coerceAtLeast(1f))
                Text(name, style = style.copy(fontSize = style.fontSize * scale), maxLines = 1, softWrap = false,
                    modifier = Modifier.layout { m, _ ->
                        val p = m.measure(Constraints())
                        layout(p.height, p.width) { p.placeWithLayer(-(p.width - p.height) / 2, (p.width - p.height) / 2) { rotationZ = 90f } }
                    })
            }
        }
    }
}

/** 책장 한 칸 줄: 여섯 권씩, 아래에 선반 선. */
@Composable
fun ShelfRows(s: AppState, books: List<Int>, fr: Map<Int, Float>) {
    val c = Theme.c
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        books.chunked(SHELF_COLS).forEach { row ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s1), verticalAlignment = Alignment.Bottom) {
                    row.forEach { b -> Spine(s, b, fr[b] ?: 0f, Modifier.weight(1f)) { openBook(s, b, fr[b] ?: 0f) } }
                    repeat(SHELF_COLS - row.size) { Box(Modifier.weight(1f)) }
                }
                Box(Modifier.fillMaxWidth().height(Tokens.Size.shelfBase).background(c.leather.copy(alpha = Tokens.Alpha.shelfBoard)))
            }
        }
    }
}

const val SHELF_COLS = 6

/** 책등을 누르면: 다 쓴 권 = 그 권 화면, 아니면 장 고르기 (잠긴 권은 구독권 안내). */
fun openBook(s: AppState, b: Int, f: Float) {
    if (f >= 1f) { s.liber = b; return }
    if (s.locked(b)) { s.peekBook = b; s.purchaseOpen = true } else { s.pickToRead = false; s.picker = b }
}

/** 66권 책장 (시트): 구약 · 신약 머리 아래 여섯 권씩. */
@Composable
fun ShelfSheet(s: AppState) {
    val fr = bookFractions(s)
    BookSheet({ s.shelfOpen = false }) {
        Column(Modifier.fillMaxWidth().heightIn(max = Tokens.Size.sheetMaxGrid * 2).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Text(stringResource(R.string.shelf_title), style = Theme.title(s.korean))
            Text(stringResource(R.string.shelf_legend), style = Theme.small())
            Text("VETUS TESTAMENTUM · " + stringResource(R.string.shelf_ot), style = Theme.caps(), maxLines = 1)
            ShelfRows(s, (0 until Canon.NT_START).toList(), fr)
            Text("NOVUM TESTAMENTUM · " + stringResource(R.string.shelf_nt), style = Theme.caps(), maxLines = 1, modifier = Modifier.padding(top = Tokens.Space.s3))
            ShelfRows(s, (Canon.NT_START until 66).toList(), fr)
        }
    }
}

/** 한 권의 숫자: 장 · 절 · 쓴 날 · 모은 판화 · 처음과 마지막 쓴 날 · 방식 비율. */
private data class BookTally(val chapters: Int, val verses: Int, val days: Int, val plates: Int, val from: Long?, val to: Long?, val type: Int, val paper: Int, val aloud: Int)

private fun tally(s: AppState, b: Int): BookTally {
    val t = s.store.book(s.translation, b)
    val mine = s.fills.filter { it.translation == s.translation && it.key.book == b }
    val n = mine.size.coerceAtLeast(1)
    fun pct(m: Mode) = mine.count { it.mode == m } * 100 / n
    return BookTally(t.chapterCount, t.fillableTotal, mine.map { it.epochDay }.toSet().size, s.store.plates.count { it.book == b },
        mine.minOfOrNull { it.epochDay }, mine.maxOfOrNull { it.epochDay }, pct(Mode.TYPE), pct(Mode.PAPER), pct(Mode.ALOUD))
}

/** 이 권을 남기는 방법: PDF · 판화 카드 · 내 목소리 오디오북 · 손글씨 노트 PDF. 의식의 셋째 장면과 권 화면이 같이 써요. */
@Composable
private fun KeepActions(s: AppState, b: Int) {
    val ctx = LocalContext.current
    val voiced by produceState(0, b, s.voiceRev) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { (1..s.store.book(s.translation, b).chapterCount).count { io.github.graviton94.todaybible.data.Voice.verses(ctx, s.translation.id, b, it).isNotEmpty() } }
    }
    val hand = remember(b) { io.github.graviton94.todaybible.data.Ink.chapters(ctx, s.translation.id, b).isNotEmpty() }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        // 필사 완성 증서 (1.5): 다 쓴 권이면 누구나
        // 필사본마다 증서 (다 쓴 필사본만). 예전처럼 여러 방식이 섞여 다 쓴 권은 방식 없는 증서 하나
        val bt = s.store.book(s.translation, b)
        val doneModes = listOf(io.github.graviton94.todaybible.core.Mode.TYPE, io.github.graviton94.todaybible.core.Mode.PAPER, io.github.graviton94.todaybible.core.Mode.ALOUD).filter { s.progress.bookDone(s.translation, bt, it) }
        doneModes.forEach { m ->
            BookButton(stringResource(R.string.cert_open) + " · " + stringResource(when (m) { io.github.graviton94.todaybible.core.Mode.ALOUD -> R.string.book_aloud; io.github.graviton94.todaybible.core.Mode.PAPER -> R.string.book_paper; else -> R.string.book_type }), Modifier.fillMaxWidth()) { s.certMode = m; s.certFor = b }
        }
        if (doneModes.isEmpty() && s.progress.bookDone(s.translation, bt)) BookButton(stringResource(R.string.cert_open), Modifier.fillMaxWidth()) { s.certFor = b }
        BookButton(stringResource(R.string.liber_pdf), Modifier.fillMaxWidth(), locked = s.premiumOn) { s.requestPdf(b) }
        BookButton(stringResource(R.string.liber_card), Modifier.fillMaxWidth(), quiet = true) {
            val t = tally(s, b)
            val title = stringResource0(ctx, R.string.liber_title, s.bookName(b))
            Cards.share(s, ctx, Cards.year(ctx, s.korean, title, ctx.getString(R.string.liber_big, t.chapters, t.verses), listOf(ctx.getString(R.string.liber_days_line, t.days, t.plates)),
                s.store.plates.firstOrNull { it.book == b }?.id ?: s.coverPlateId(), "COMPLETUM · " + Latin.book(b)), "book")
        }
        if (voiced > 0) BookButton(stringResource(R.string.liber_audio), Modifier.fillMaxWidth(), locked = s.premiumOn, quiet = true, enabled = !s.exporting) { s.requestAudiobook(b) }
        if (hand) BookButton(stringResource(R.string.liber_hand), Modifier.fillMaxWidth(), locked = s.premiumOn, quiet = true) { s.requestNotes(b) }
    }
}

private fun stringResource0(ctx: android.content.Context, id: Int, vararg a: Any) = ctx.getString(id, *a)

/** 권 머리: 그 권 판화 · COMPLETUM 인장 · 나의 ○○ · 날짜 · 숫자 넷 · 방식 비율 (어두운 종이). */
@Composable
private fun LiberHead(s: AppState, b: Int) {
    val d = Tokens.dark; val ctx = LocalContext.current
    val t = remember(b, s.fills.size) { tally(s, b) }
    val plate = s.store.plates.lastOrNull { it.book == b }?.id ?: s.coverPlateId()
    val img = plate?.let { rememberPlate(it) }
    Box(Modifier.fillMaxWidth()) {
        if (img != null) Image(img, null, Modifier.fillMaxWidth().height(Tokens.Size.liberArt), contentScale = ContentScale.Crop,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(SEPIA_MATRIX))
        CompletumSeal(Latin.book(b) + " · " + Latin.roman((t.to?.let { LocalDate.ofEpochDay(it) } ?: s.today()).year), Modifier.align(Alignment.BottomEnd).padding(Tokens.Space.s3))
    }
    Column(Modifier.padding(horizontal = Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Text(Latin.book(b) + " · I–" + Latin.roman(t.chapters), style = Theme.caps().copy(color = d.giltText), maxLines = 1)
        Text(stringResource(R.string.liber_title, s.bookName(b)), style = Theme.title(s.korean, Tokens.Text.display).copy(color = d.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (t.from != null && t.to != null) Text(stringResource(R.string.liber_range, fmtDay(ctx, t.from), fmtDay(ctx, t.to)) + if (s.ownerName.isNotBlank()) " · " + s.ownerName else "", style = Theme.small().copy(color = d.inkSoft), maxLines = 1)
        Row(Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s2)) {
            listOf("${t.chapters}" to R.string.liber_ch, "%,d".format(t.verses) to R.string.liber_v, "${t.days}" to R.string.liber_d, "${t.plates}" to R.string.liber_p).forEach { (n, l) ->
                Column(Modifier.weight(1f)) {
                    Text(n, style = Theme.lining(Tokens.Text.streak).copy(color = d.giltText), maxLines = 1)
                    Text(stringResource(l), style = Theme.small().copy(color = d.inkSoft), maxLines = 1)
                }
            }
        }
        Text(stringResource(R.string.stat_modes, t.type, t.paper, t.aloud), style = Theme.small().copy(color = d.inkSoft), maxLines = 1)
    }
}

private fun fmtDay(ctx: android.content.Context, day: Long) = Lang.date(ctx, R.string.fmt_ymd, LocalDate.ofEpochDay(day))

/** 다 쓴 권 화면 (책장에서): 머리 · 남기기 · 책 덮던 장면 다시 보기. 언제든 다시 만들 수 있어요. */
@Composable
fun LiberPage(s: AppState, b: Int) {
    val d = Tokens.dark
    BackHandler { s.liber = null }
    Box(Modifier.fillMaxSize().background(d.leaf).statusBarsPadding().navigationBarsPadding()) {
        androidx.compose.runtime.CompositionLocalProvider(io.github.graviton94.todaybible.design.LocalPalette provides d) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(Tokens.Size.touch).clickable(role = Role.Button) { s.liber = null }, contentAlignment = Alignment.Center) { BackArrow(Modifier.size(Tokens.Size.icon)) }
                }
                LiberHead(s, b)
                Column(Modifier.padding(horizontal = Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    KeepActions(s, b)
                    Text(stringResource(R.string.liber_replay), style = Theme.small().copy(color = d.giltText, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), maxLines = 1,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.Size.touch).wrapContentHeight().clickable(role = Role.Button) { s.ritual = b }, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/**
 * 권 다 쓰기 의식 (1.4): 권의 마지막 장을 마치면 장 마침 다음에 한 번.
 * 1 이 권에서 모은 판화가 한 장씩 지나가요 · 2 책을 덮으며 인장 · 숫자 · 3 남기기와 다음 권.
 */
@Composable
fun BookRitual(s: AppState, b: Int) {
    val d = Tokens.dark; val k = s.korean
    var step by remember(b) { mutableIntStateOf(s.debugRitualStep.also { s.debugRitualStep = 0 }) }
    fun close() { s.ritual = null; s.store.ritualSeen = s.store.ritualSeen + "${s.translation.id}:$b" }
    BackHandler { if (step > 0) step-- else close() }
    val plates = remember(b) { s.store.plates.filter { it.book == b }.sortedBy { it.chapter } }
    Box(Modifier.fillMaxSize().background(d.leaf).statusBarsPadding().navigationBarsPadding()) {
        androidx.compose.runtime.CompositionLocalProvider(io.github.graviton94.todaybible.design.LocalPalette provides d) {
            when (step) {
                0 -> Column(Modifier.fillMaxSize().padding(Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
                    Text("LIBER COMPLETUS", style = Theme.caps(), maxLines = 1)
                    Text(stringResource(R.string.rit_title, s.bookName(b)), style = Theme.title(k, Tokens.Text.display).copy(color = d.ink))
                    // 판화가 한 장씩 천천히 (누르면 다음)
                    var i by remember(b) { mutableIntStateOf(0) }
                    LaunchedEffect(b, plates.size) { while (plates.isNotEmpty()) { kotlinx.coroutines.delay(Tokens.Motion.ritualPlateMs.toLong()); i = (i + 1) % plates.size } }
                    plates.getOrNull(i)?.let { pl ->
                        androidx.compose.animation.Crossfade(pl, Modifier.weight(1f).fillMaxWidth().clickable(role = Role.Button) { i = (i + 1) % plates.size }, label = "plate") { p ->
                            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                                rememberPlate(p.id)?.let { Image(it, null, Modifier.weight(1f).fillMaxWidth().border(Tokens.Stroke.hair, d.gilt), contentScale = ContentScale.Crop, colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(SEPIA_MATRIX)) }
                                Text(Latin.roman(p.chapter) + " · " + s.plateName(p), style = Theme.small().copy(color = d.inkSoft), maxLines = 1)
                            }
                        }
                    } ?: Box(Modifier.weight(1f))
                    Text(stringResource(R.string.rit_plates, plates.size), style = Theme.small().copy(color = d.inkSoft))
                    BookButton(stringResource(R.string.rit_close), Modifier.fillMaxWidth()) { step = 1 }
                }
                1 -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
                    LiberHead(s, b)
                    Box(Modifier.padding(horizontal = Tokens.Space.s5)) { BookButton(stringResource(R.string.rit_keep_go), Modifier.fillMaxWidth()) { step = 2 } }
                }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    Text("POST LIBRUM", style = Theme.caps(), maxLines = 1)
                    Text(stringResource(R.string.rit_keep), style = Theme.title(k).copy(color = d.ink))
                    KeepActions(s, b)
                    // 다음 권: 아직 다 쓰지 않은 바로 다음 권
                    val nb = remember(b, s.fills.size) { ((b + 1) until 66).firstOrNull { !s.progress.bookDone(s.translation, s.store.book(s.translation, it)) } }
                    if (nb != null) Column(Modifier.fillMaxWidth().drawBehind { drawLine(d.hair, Offset.Zero, Offset(size.width, 0f), Tokens.Stroke.hair.toPx()) }.padding(top = Tokens.Space.s4),
                        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        Text(stringResource(R.string.rit_next_h), style = Theme.small().copy(color = d.inkSoft), maxLines = 1)
                        Text(stringResource(R.string.rit_next, s.bookName(nb)), style = Theme.title(k).copy(color = d.ink), maxLines = 1)
                        BookButton(stringResource(R.string.rit_next_go, s.bookName(nb)), Modifier.fillMaxWidth()) { close(); s.liber = null; s.open(nb, 1) }
                    }
                    Text(stringResource(R.string.rit_later), style = Theme.small().copy(color = d.inkSoft, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), maxLines = 1,
                        modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.Size.touch).wrapContentHeight().clickable(role = Role.Button) { close() }, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** 노트 한 줄 (묵상 · 설교 · 형광펜): 머리 = 날짜 (금빛 숫자) + 어디, 아래 = 한 줄 (말줄임). 누르면 전체. */
@Composable
fun NoteRow(date: String, where: String, line: String, onClick: () -> Unit) {
    val c = Theme.c
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
        .drawBehind { drawLine(c.hair, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.hair.toPx()) }
        .padding(vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Text(date, style = Theme.lining(Tokens.Text.noteDate).copy(color = c.giltText), maxLines = 1)
            Text(where, style = Theme.small(), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("›", style = Theme.small().copy(color = c.unwritten), maxLines = 1)
        }
        Text(line, style = Theme.body(), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 묵상 노트 한 줄 전체 (노트 탭에서 누르면). */
@Composable
fun ReflectionSheet(s: AppState, n: io.github.graviton94.todaybible.data.Store.Note) {
    val c = Theme.c; val ctx = LocalContext.current
    BookSheet({ s.noteOpen = null }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Text("NOTAE CORDIS · " + fmtDay(ctx, n.epochDay), style = Theme.caps(), maxLines = 1)
            Text(s.chapterRef(n.book, n.chapter), style = Theme.title(s.korean), maxLines = 1)
            Text(n.text, style = Theme.verse(s.bibleKorean))
            BookButton(stringResource(R.string.note_read), Modifier.fillMaxWidth(), quiet = true) { s.noteOpen = null; s.read(n.book, n.chapter) }
        }
    }
}
