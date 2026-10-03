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
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    BackHandler { s.plateView = null }
    val t = s.store.book(s.translation, p.book)
    val f = s.plateFraction(p)
    val n = Pieces.revealed(f); val shown = remember(p.id) { Pieces.order(p.id.hashCode()) }.take(n).toSet()
    val img = rememberPlate(p.id)
    val ref = if (k) "${s.bookName(p.book)} ${p.chapter}:${p.verse}" else "${s.bookName(p.book)} ${p.chapter}:${p.verse}"
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
                if (img != null) Image(img, if (k) p.ko else p.en, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (n == 0) Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    StampMark(STAMP_CROSS, c.unwritten.copy(alpha = Tokens.Alpha.faint), Modifier.size(Tokens.Size.emblem))
                    Text(stringResource(R.string.plate_locked), style = Theme.small())
                }
            }
            Text(if (k) p.ko else p.en, style = Theme.title(k))
            // 그 장면의 말씀
            Column(Modifier.drawBehind { drawRect(c.rubric, Offset(0f, 0f), Size(Tokens.Stroke.rule.toPx(), size.height)) }.padding(start = Tokens.Space.s4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Text(Markup.plain(t.verse(p.chapter, p.verse)), style = Theme.body())
                Text(ref, style = Theme.small().copy(color = c.rubric))
            }
            Text(if (k) p.byKo else p.byEn, style = Theme.small())
            if (n < Pieces.COUNT) BookButton(stringResource(R.string.continue_at, s.bookName(p.book), p.chapter), Modifier.fillMaxWidth()) { s.plateView = null; s.open(p.book, p.chapter) }
            else BookButton(stringResource(R.string.share), Modifier.fillMaxWidth(), quiet = true) {
                Cards.share(ctx, Cards.plate(ctx, k, p.id, if (k) p.ko else p.en, if (k) "${s.bookName(p.book)} ${p.chapter}장" else "${s.bookName(p.book)} ${p.chapter}"), "plate")
            }
        }
    }
}
