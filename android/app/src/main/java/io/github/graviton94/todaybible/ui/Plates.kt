package io.github.graviton94.todaybible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
            if (n < Pieces.COUNT) BookButton(stringResource(R.string.continue_at, s.bookName(p.book), p.chapter), Modifier.fillMaxWidth()) { s.plateView = null; s.open(p.book, p.chapter) }
            else BookButton(stringResource(R.string.share), Modifier.fillMaxWidth(), quiet = true) {
                Cards.share(s, ctx, Cards.plate(ctx, k, p.id, s.plateName(p), s.chapterRef(p.book, p.chapter)), "plate")
            }
        }
    }
}


/** 화첩 칸 한 점: 걸린 것은 그림 그대로 + 금빛 밑줄, 걷히는 중은 조각 가림, 아직은 가는 테두리 안에 어디서 얻는지. */
@Composable
fun PlateThumb(s: AppState, p: Plate, modifier: Modifier = Modifier, index: Int? = null) {
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
            } else Column(Modifier.padding(Tokens.Space.s1), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Text(stringResource(R.string.plate_when), style = Theme.small().copy(color = c.unwritten, textAlign = androidx.compose.ui.text.style.TextAlign.Center), maxLines = 1)
                Text(s.chapterRef(p.book, p.chapter), style = Theme.small().copy(color = c.inkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center), maxLines = 1)
            }
        }
        Text(if (n >= Pieces.COUNT) (index?.let { io.github.graviton94.todaybible.core.Latin.roman(it) } ?: s.plateName(p)) else if (n > 0) "${(f * 100).toInt()}%" else "",
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
        Column(Modifier.fillMaxSize().background(c.leaf)) {
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
                    Column(Modifier.weight(1f).clickable(role = Role.Tab) { tab = i }.drawBehind {
                        if (i == tab) drawRect(c.gilt, Offset(0f, size.height - Tokens.Stroke.rule.toPx()), Size(size.width * 0.8f, Tokens.Stroke.rule.toPx()))
                    }.padding(bottom = Tokens.Space.s2)) {
                        Text(stringResource(name), style = Theme.body().copy(color = if (i == tab) c.ink else c.unwritten), maxLines = 1)
                        Text("${inG.count { s.plateFraction(it) >= 1f }} / ${inG.size}", style = Theme.small().copy(color = if (i == tab) c.inkSoft else c.unwritten), maxLines = 1)
                    }
                }
            }
            val list = all.filter { it.book in groups[tab].second }.sortedWith(compareBy({ it.book }, { it.chapter }))
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                list.chunked(3).forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        row.forEachIndexed { j, p -> PlateThumb(s, p, Modifier.weight(1f), index = r * 3 + j + 1) }
                        repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
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
