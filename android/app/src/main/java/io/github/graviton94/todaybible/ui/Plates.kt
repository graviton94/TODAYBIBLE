package io.github.graviton94.todaybible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.withLink
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.core.Pieces
import io.github.graviton94.todaybible.data.Plate
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens

/**
 * 판화 한 장 크게 (D1): 드러난 조각 · 제목 · 그 장면의 말씀 한 절 · 화가와 책과 연도.
 * 설명은 지어내지 않고 그 장의 말씀으로.
 */
@Composable
fun PlatePage(s: AppState, p: Plate) {
    androidx.compose.runtime.CompositionLocalProvider(io.github.graviton94.todaybible.design.LocalPalette provides Tokens.dark) { PlateDark(s, p) }
}

@Composable
private fun PlateDark(s: AppState, p: Plate) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    BackHandler { s.plateView = null }
    val t = s.store.book(s.translation, p.book)
    val f = s.plateFraction(p)
    val n = Pieces.revealed(f); val shown = remember(p.id) { Pieces.order(p.id.hashCode()) }.take(n).toSet()
    val img = rememberPlate(p.id)
    val ref = "${s.bookName(p.book)} ${p.chapter}:${p.verse}"
    val backLabel = stringResource(R.string.back)
    Column(Modifier.fillMaxSize().background(c.leaf)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(Tokens.Size.touch).semantics { contentDescription = backLabel }.clickable(role = Role.Button) { s.plateView = null }, contentAlignment = Alignment.Center) { BackArrow(Modifier.size(Tokens.Size.icon)) }
            Text(stringResource(R.string.plates), style = Theme.title(k), maxLines = 1, modifier = Modifier.weight(1f))
            Text("$n/${Pieces.COUNT}", style = Theme.small(), modifier = Modifier.padding(end = Tokens.Space.s4))
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Box(Modifier.fillMaxWidth().aspectRatio(Tokens.Ratio.plateAspect).clip(RoundedCornerShape(Tokens.Radius.chip)).background(c.paper).drawWithContent {
                drawContent()
                val w = size.width / Pieces.COLS; val h = size.height / Pieces.ROWS
                for (i in 0 until Pieces.COUNT) if (i !in shown) drawRect(c.paper.copy(alpha = Tokens.Alpha.veilPiece), Offset((i % Pieces.COLS) * w, (i / Pieces.COLS) * h), Size(w + 1f, h + 1f))
                if (n in 1 until Pieces.COUNT) {
                    for (x in 1 until Pieces.COLS) drawLine(c.hair, Offset(x * w, 0f), Offset(x * w, size.height), Tokens.Stroke.hair.toPx())
                    for (y in 1 until Pieces.ROWS) drawLine(c.hair, Offset(0f, y * h), Offset(size.width, y * h), Tokens.Stroke.hair.toPx())
                }
            }, contentAlignment = Alignment.Center) {
                if (img != null) Image(img, s.plateName(p), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (n == 0) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    StampMark(STAMP_CROSS, c.unwritten.copy(alpha = Tokens.Alpha.faint), Modifier.size(Tokens.Size.emblem))
                    Text(stringResource(R.string.plate_locked), style = Theme.small())
                }
            }
            Text(s.plateName(p), style = Theme.title(k))
            // 그 장면의 말씀
            Column(Modifier.drawBehind { drawRect(c.rubric, Offset(0f, 0f), Size(Tokens.Stroke.rule.toPx(), size.height)) }.padding(start = Tokens.Space.s4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Text(Markup.plain(t.verse(p.chapter, p.verse)), style = Theme.body())
                Text(ref, style = Theme.small().copy(color = c.rubric))
            }
            Text(s.plateBy(p), style = Theme.small())
            // 역사 속의 성경 (1.5): 그림 · 장면 · 역사. 글 속 링크는 그 사료로
            StoryPanel(s, p)
            if (n < Pieces.COUNT) BookButton(stringResource(R.string.continue_at, s.bookName(p.book), p.chapter), Modifier.fillMaxWidth()) { s.plateView = null; s.open(p.book, p.chapter) }
            else BookButton(stringResource(R.string.share), Modifier.fillMaxWidth(), quiet = true) {
                Cards.share(s, ctx, Cards.verse(ctx, s.bibleKorean, s.head(p.book, p.chapter), ctx.getString(R.string.ref_verse, s.bookName(p.book), p.chapter, p.verse), s.text(p.book).verse(p.chapter, p.verse), p.id), "verse")
            }
        }
    }
}


/** 화첩 칸 한 점: 걸린 것은 그림 그대로 + 금빛 밑줄, 걷히는 중은 조각 가림, 아직은 가는 테두리 안에 어디서 얻는지. */
@Composable
fun PlateThumb(s: AppState, p: Plate, modifier: Modifier = Modifier, index: Int? = null, bare: Boolean = false) {
    val c = Theme.c
    val f = s.plateFraction(p)
    val n = Pieces.revealed(f); val shown = remember(p.id) { Pieces.order(p.id.hashCode()) }.take(n).toSet()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
        Box(Modifier.fillMaxWidth().aspectRatio(Tokens.Ratio.thumb).then(
            if (n == 0) Modifier.drawBehind { drawRect(c.hair, style = androidx.compose.ui.graphics.drawscope.Stroke(Tokens.Stroke.hair.toPx())) } else Modifier
        ).clickable(role = Role.Button) { s.plateView = p }, contentAlignment = Alignment.Center) {
            if (n > 0) {
                val img = rememberPlate(p.id, small = true)
                if (img != null) Image(img, s.plateName(p), Modifier.fillMaxSize().drawWithContent {
                    drawContent()
                    val w = size.width / Pieces.COLS; val h = size.height / Pieces.ROWS
                    for (i in 0 until Pieces.COUNT) if (i !in shown) drawRect(c.leaf.copy(alpha = Tokens.Alpha.veilPiece), Offset((i % Pieces.COLS) * w, (i / Pieces.COLS) * h), Size(w + 1f, h + 1f))
                    if (n >= Pieces.COUNT) drawRect(c.gilt, Offset(0f, size.height - Tokens.Stroke.rule.toPx()), Size(size.width, Tokens.Stroke.rule.toPx()))
                }, contentScale = ContentScale.Crop)
            } else {
                // 아직 빈 칸: 어느 장인지만 옅게 (칸마다 같은 안내 글을 되풀이하지 않게). 화면 읽기에는 ‘이 장을 다 쓰면 ○○ ○장’
                val ref = s.chapterRef(p.book, p.chapter); val say = stringResource(R.string.plate_when) + " " + ref
                Text(ref, style = Theme.small().copy(color = c.unwritten, textAlign = androidx.compose.ui.text.style.TextAlign.Center), maxLines = 2,
                    modifier = Modifier.padding(Tokens.Space.s1).semantics { contentDescription = say })
            }
        }
        if (!bare) Text(if (n >= Pieces.COUNT) (index?.let { io.github.graviton94.todaybible.core.Latin.roman(it) } ?: s.plateName(p)) else if (n > 0) "${(f * 100).toInt()}%" else "",
            style = if (index != null && n >= Pieces.COUNT) Theme.caps().copy(color = c.unwritten) else Theme.small(), maxLines = 1)
    }
}

/** 화첩 (하루의 편지 R3 갤러리): 어두운 바탕 · 갈래 칸 · 그림 격자 · 아래에 다음 판화까지. */
@Composable
fun GalleryPage(s: AppState) {
    androidx.compose.runtime.CompositionLocalProvider(io.github.graviton94.todaybible.design.LocalPalette provides Tokens.dark) {
        val c = Theme.c; val k = s.korean
        BackHandler { s.galleryOpen = false }
        val groups = listOf(R.string.gal_ot to 0..38, R.string.gal_gospels to 39..42, R.string.gal_apostles to 43..64, R.string.gal_rev to 65..65)
        var tab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
        val all = s.store.plates
        val hung = all.count { s.plateFraction(it) >= 1f }
        Column(Modifier.fillMaxSize().background(c.leaf).paperGrain(c.ink)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
                val backLabel = stringResource(R.string.back)
                Box(Modifier.size(Tokens.Size.touch).semantics { contentDescription = backLabel }.clickable(role = Role.Button) { s.galleryOpen = false }, contentAlignment = Alignment.Center) { BackArrow(Modifier.size(Tokens.Size.icon)) }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Text("COLLECTIO", style = Theme.caps(), maxLines = 1)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.plates), style = Theme.title(k, Tokens.Text.display), maxLines = 1, modifier = Modifier.weight(1f))
                    Text("$hung", style = Theme.big(Tokens.Text.title).copy(color = c.ink), maxLines = 1)
                    Text(" / ${all.size}", style = Theme.small(), maxLines = 1, modifier = Modifier.padding(bottom = Tokens.Space.s1))
                }
            }
            // 갈래 칸: 이름과 걸린 수, 지금 칸은 금빛 밑줄
            Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s5).padding(top = Tokens.Space.s4).drawBehind { drawLine(c.hair, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.hair.toPx()) }) {
                groups.forEachIndexed { i, (name, range) ->
                    val inG = all.filter { it.book in range }
                    Column(Modifier.weight(1f).chosen(tab == i).clickable(role = Role.Tab) { tab = i }.drawBehind {
                        if (i == tab) drawRect(c.gilt, Offset(0f, size.height - Tokens.Stroke.rule.toPx()), Size(size.width * 0.8f, Tokens.Stroke.rule.toPx()))
                    }.padding(bottom = Tokens.Space.s2)) {
                        Text(stringResource(name), style = Theme.body().copy(color = if (i == tab) c.ink else c.unwritten), maxLines = 1)
                        Text("${inG.count { s.plateFraction(it) >= 1f }} / ${inG.size}", style = Theme.small().copy(color = if (i == tab) c.inkSoft else c.unwritten), maxLines = 1)
                    }
                }
            }
            val list = all.filter { it.book in groups[tab].second && (it.set.isEmpty() || it.set == io.github.graviton94.todaybible.data.History.SOURCE) }.sortedWith(compareBy({ it.book }, { it.chapter }))
            // 이달의 판화 (구독 세트): 가장 최근 세트 한 줄 위에. 잠금은 결제가 열리면 (1.4.x)
            val sets = all.filter { it.set.isNotEmpty() && it.set != io.github.graviton94.todaybible.data.History.SOURCE }.groupBy { it.set }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                sets.keys.maxOrNull()?.let { key ->
                    val set = sets.getValue(key)
                    val month = key.substringAfter('-').toIntOrNull() ?: 1
                    Column(Modifier.fillMaxWidth().drawBehind { drawLine(c.gilt, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.hair.toPx()) }.padding(bottom = Tokens.Space.s4),
                        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        Text("TABULAE MENSIS · " + io.github.graviton94.todaybible.core.Latin.roman(month), style = Theme.caps(), maxLines = 1)
                        Text(stringResource(R.string.set_title, java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, androidx.compose.ui.platform.LocalConfiguration.current.locales[0])), style = Theme.title(k), maxLines = 1)
                        Text(if (k) set.first().byKo else set.first().byEn, style = Theme.small(), maxLines = 2)
                        set.chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                                row.forEach { pl -> Column(Modifier.weight(1f).clickable(role = Role.Button) { s.plateView = pl }, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                                    PlateThumb(s, pl, Modifier.fillMaxWidth())
                                    Text(s.chapterRef(pl.book, pl.chapter), style = Theme.small(), maxLines = 1)
                                } }
                                repeat(2 - row.size) { Box(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                // 권마다 도록 한 장
                var n = 0
                list.groupBy { it.book }.forEach { (b, plates) -> n++; CatalogCard(s, b, plates, n) }
            }
            // 다음 판화까지
            s.nextPlate()?.let { (pl, _) ->
                val f = s.plateFraction(pl)
                Column(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s5).padding(bottom = Tokens.Space.s4, top = Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(stringResource(R.string.next_plate_short, s.plateName(pl)) + " · " + s.chapterRef(pl.book, pl.chapter), style = Theme.small().copy(color = c.ink), maxLines = 1, modifier = Modifier.weight(1f))
                        Text("${(f * 100).toInt()}%", style = Theme.small(), maxLines = 1)
                    }
                    Box(Modifier.fillMaxWidth().size(width = Tokens.Size.touch, height = Tokens.Stroke.rule).background(c.hair)) { Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).size(width = Tokens.Size.touch, height = Tokens.Stroke.rule).background(c.gilt)) }
                }
            }
        }
    }
}


/**
 * 도록 한 장 (하루의 편지 화첩 도록): 머리글 CATALOGUS · 권 · N°, 권 이름, 판화 네 칸씩, 아래 모은 수.
 * 한 권의 판화를 다 모으면 이름이 금박 글자 + 작은 금빛 십자, 아래에 COMPLETUM.
 */
@Composable
private fun CatalogCard(s: AppState, b: Int, plates: List<Plate>, n: Int) {
    val c = Theme.c; val k = s.korean
    val got = plates.count { s.plateFraction(it) >= 1f }; val done = got == plates.size
    Column(Modifier.fillMaxWidth().background(c.paper).drawBehind { drawRect(c.hair, style = androidx.compose.ui.graphics.drawscope.Stroke(Tokens.Stroke.hair.toPx())) }
        .padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Row {
            Text("CATALOGUS · " + io.github.graviton94.todaybible.core.Latin.book(b), style = Theme.caps(), maxLines = 1, modifier = Modifier.weight(1f))
            Text("N° " + io.github.graviton94.todaybible.core.Latin.roman(n), style = Theme.caps(), maxLines = 1)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            val title = Theme.title(k, Tokens.Text.title)
            Text(s.bookName(b), maxLines = 1, style = if (done) title.copy(brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(c.giltLo, c.gilt, c.giltHi, c.gilt, c.giltLo)), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else title)
            if (done) StampMark(STAMP_CROSS, c.gilt, Modifier.size(Tokens.Size.iconSm))
        }
        if (done) Text("COMPLETUM", style = Theme.caps().copy(color = c.giltText), maxLines = 1)
        plates.sortedBy { it.chapter }.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                row.forEach { p -> PlateThumb(s, p, Modifier.weight(1f), index = null, bare = true) }
                repeat(4 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
        Text(stringResource(R.string.catalog_count, got, plates.size), style = Theme.small(), maxLines = 1)
    }
}

/** 장 여는 화면 (하루의 편지 C4 이달의 전시): 판화가 위를 가득 채우고 어둠으로 녹아들어요. 장면 설명 · 마디 띠 · 버튼 하나. */
@Composable
fun ChapterOpener(s: AppState, b: Int, ch: Int) {
    val p = s.store.plateFor(b, ch) ?: run { s.openerDone(); return }
    androidx.compose.runtime.CompositionLocalProvider(io.github.graviton94.todaybible.design.LocalPalette provides Tokens.dark) {
        val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
        BackHandler { s.openerDone() }
        val t = s.store.book(s.translation, b); val fill = t.fillable(ch)
        val done = fill.count { s.progress.isFilled(s.translation, io.github.graviton94.todaybible.core.VerseKey(b, ch, it)) }
        val next = s.progress.nextVerse(s.translation, t, ch)
        val img = rememberPlate(p.id)
        Box(Modifier.fillMaxSize().background(c.leaf)) {
            if (img != null) Image(img, s.plateName(p), Modifier.fillMaxWidth().aspectRatio(Tokens.Ratio.openerArt).drawWithContent {
                drawContent()
                drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(0.4f to c.leaf.copy(alpha = 0f), 0.75f to c.leaf.copy(alpha = 0.7f), 1f to c.leaf))
            }, contentScale = ContentScale.Crop)
            Column(Modifier.fillMaxSize().statusBarsPaddingCompat().padding(horizontal = Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Box(Modifier.weight(1f))
                HeadText(s, b, ch)
                Text(s.plateName(p), style = Theme.title(k, Tokens.Text.display), maxLines = 2)
                Text(stringResource(R.string.opener_line, s.chapterRef(b, ch), fill.size, p.verse), style = Theme.small(), maxLines = 2)
                Text(stringResource(R.string.opener_note, done), style = Theme.body().copy(color = c.inkSoft), maxLines = 3)
                Segments(fill.size, done)
                Column(Modifier.fillMaxWidth().padding(top = Tokens.Space.s3, bottom = Tokens.Space.s4)) {
                    BookButton(if (next != null) stringResource(R.string.write_from, next) else stringResource(R.string.continue_now), Modifier.fillMaxWidth()) { s.openerDone() }
                    BookButton(stringResource(R.string.listen_first), Modifier.fillMaxWidth(), quiet = true) { s.openerDone(); s.read(b, ch) }
                    Text(s.plateBy(p), style = Theme.small().copy(color = c.unwritten), maxLines = 1, modifier = Modifier.padding(top = Tokens.Space.s2))
                }
            }
        }
    }
}

@Composable
private fun Modifier.statusBarsPaddingCompat(): Modifier = this.then(Modifier.padding(androidx.compose.foundation.layout.WindowInsets.systemBars.asPaddingValues()))

/** 역사 속의 성경 (1.5 C6): PICTURA 그림 · SCAENA 장면 · HISTORIA 역사. 링크 [[id|글]] 를 누르면 그 사료 · 판화를 크게. */
@Composable
private fun StoryPanel(s: AppState, p: Plate) {
    val ctx = LocalContext.current; val c = Theme.c
    // 이야기 글은 화면 (앱) 언어로
    val st = remember(p.id) { io.github.graviton94.todaybible.data.History.story(ctx, p.id, s.korean) } ?: return
    val linkStyle = androidx.compose.ui.text.TextLinkStyles(androidx.compose.ui.text.SpanStyle(color = c.giltText, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline))
    listOf(Triple("PICTURA", R.string.story_pictura, st.pictura), Triple("SCAENA", R.string.story_scaena, st.scaena), Triple("HISTORIA", R.string.story_historia, st.historia)).forEachIndexed { i, (lat, label, body) ->
        Column(Modifier.fillMaxWidth().drawBehind { drawLine(if (i == 0) c.ink else c.hair, Offset.Zero, Offset(size.width, 0f), Tokens.Stroke.hair.toPx()) }.padding(top = Tokens.Space.s3),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Text(lat, style = Theme.caps().copy(color = c.ink), maxLines = 1, modifier = Modifier.clearAndSetSemantics { })
                Text(stringResource(label), style = Theme.small(), maxLines = 1, modifier = Modifier.weight(1f).semantics { heading() })
                Text(io.github.graviton94.todaybible.core.Latin.roman(i + 1), style = Theme.big(Tokens.Text.sectionNum).copy(color = c.gilt), maxLines = 1, modifier = Modifier.clearAndSetSemantics { })
            }
            val text = androidx.compose.ui.text.buildAnnotatedString {
                io.github.graviton94.todaybible.data.History.parts(body).forEach { part ->
                    val target = part.link?.let { id -> s.store.plates.firstOrNull { it.id == id } }
                    // 링크는 이 페이지를 떠나지 않고 그 그림만 띄워요
                    if (target == null) append(part.text)
                    else withLink(androidx.compose.ui.text.LinkAnnotation.Clickable(target.id, linkStyle) { s.platePeek = target }) { append(part.text) }
                }
            }
            Text(text, style = Theme.body())
        }
    }
    // 판화 페이지를 닫으면 띄운 그림도 함께
    androidx.compose.runtime.DisposableEffect(p.id) { onDispose { s.platePeek = null } }
    s.platePeek?.let { PlatePeek(s, it) { s.platePeek = null } }
}

/** 이야기 속 링크: 그 판화 · 사료 그림만 크게 (드러난 조각만), 제목 · 장절 · 출처 한 줄. 아무 데나 누르거나 뒤로 가면 닫혀요. */
@Composable
private fun PlatePeek(s: AppState, p: Plate, close: () -> Unit) {
    val c = Theme.c; val k = s.korean
    val n = Pieces.revealed(s.plateFraction(p)); val shown = remember(p.id) { Pieces.order(p.id.hashCode()) }.take(n).toSet()
    val img = rememberPlate(p.id)
    val closeLabel = stringResource(R.string.close)
    androidx.compose.ui.window.Dialog(close, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(c.scrim).clickable(role = Role.Button, onClickLabel = closeLabel, onClick = close)
            .padding(horizontal = Tokens.Space.s4), verticalArrangement = Arrangement.Center) {
          // 그림과 제목을 불투명한 카드에 (뒤 페이지 글자가 비치지 않게)
          Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.chip)).background(c.leaf).padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Box(Modifier.fillMaxWidth().aspectRatio(Tokens.Ratio.plateAspect).clip(RoundedCornerShape(Tokens.Radius.chip)).background(c.paper).drawWithContent {
                drawContent()
                val w = size.width / Pieces.COLS; val h = size.height / Pieces.ROWS
                for (i in 0 until Pieces.COUNT) if (i !in shown) drawRect(c.paper.copy(alpha = Tokens.Alpha.veilPiece), Offset((i % Pieces.COLS) * w, (i / Pieces.COLS) * h), Size(w + 1f, h + 1f))
            }, contentAlignment = Alignment.Center) {
                if (img != null) Image(img, s.plateName(p), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (n == 0) Text(stringResource(R.string.plate_locked), style = Theme.small())
            }
            Text(s.plateName(p), style = Theme.title(k))
            Text("${s.bookName(p.book)} ${p.chapter}:${p.verse} · ${s.plateBy(p)}", style = Theme.small())
          }
        }
    }
}
