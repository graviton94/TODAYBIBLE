package io.github.graviton94.todaybible.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Milestone
import io.github.graviton94.todaybible.data.Plate
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import kotlinx.coroutines.launch

/** 장을 마친 화면: 붉은 장 번호 · 한 줄 · (판화가 있는 장이면) 가죽 덮개 아래 판화 · 다음 장. */
@Composable
fun FinishedPage(s: AppState, book: Int, chapter: Int, lifted: Boolean = false) {
    val c = Theme.c; val k = s.korean
    val plate = s.store.plateFor(book, chapter)
    val veil = remember(book, chapter) { Animatable(if (lifted) 1f else 0f) }
    val scope = rememberCoroutineScope()
    fun close() { s.finished = null }
    BackHandler { close() }
    Column(
        Modifier.fillMaxSize().background(c.leaf).systemBarsPadding().verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s5),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
    ) {
        Text("$chapter", style = Theme.number().copy(fontSize = Tokens.Text.initial * 1.6f, textAlign = TextAlign.Center))
        Text(stringResource(R.string.chapter_done, s.bookName(book), chapter), style = Theme.title(k).copy(textAlign = TextAlign.Center))
        if (plate != null) {
            PlateUnderVeil(s, plate, veil.value, Modifier.fillMaxWidth(0.82f))
            Text(if (veil.value >= 1f) (if (k) plate.ko else plate.en) else stringResource(R.string.veil_hint),
                style = Theme.small().copy(textAlign = TextAlign.Center), maxLines = 1)
        } else {
            Box(Modifier.padding(vertical = Tokens.Space.s5).size(64.dp)) { StampMark(STAMP_CROSS, c.gilt, Modifier.fillMaxSize()) }
        }
        val (nb, nc) = remember(book, chapter) { s.nextChapter() }
        if (plate != null && veil.value < 1f) {
            BookButton(stringResource(R.string.lift_veil), Modifier.fillMaxWidth(), enabled = !veil.isRunning) {
                scope.launch { veil.animateTo(1f, tween(Tokens.Motion.veilMs, easing = FastOutSlowInEasing)) }
            }
        } else if (nb != book || nc != chapter) {
            BookButton(stringResource(R.string.next_chapter, s.bookName(nb), nc), Modifier.fillMaxWidth()) { close(); s.open(nb, nc) }
        }
        BookButton(stringResource(R.string.close), Modifier.fillMaxWidth(), quiet = true) { close() }
    }
}

/**
 * 판화 위 가죽 덮개. 덮개: 무광 가죽 + 위아래 금선 두 줄 + 안쪽 금박 테 + 가운데 ✠ 하나.
 * t = 걷힌 정도 (0 = 덮임, 1 = 다 걷힘): 위로 말려 올라가며 아래 끝에 옅은 그늘.
 */
@Composable
private fun PlateUnderVeil(s: AppState, p: Plate, t: Float, modifier: Modifier) {
    val c = Theme.c
    val img = rememberPlate(p.id)
    Box(
        modifier.aspectRatio(Tokens.Ratio.plateAspect).clip(RoundedCornerShape(Tokens.Radius.button)).background(c.paper).clipToBounds()
            .drawWithContent {
                drawContent()
                if (t >= 1f) return@drawWithContent
                val w = size.width; val h = size.height
                val y = -h * t
                translate(0f, y) {
                    drawRect(c.leather, Offset.Zero, Size(w, h))
                    val g = Tokens.Stroke.gilt.toPx(); val gap = 4.dp.toPx(); val edge = 10.dp.toPx()
                    // 위 · 아래 금선 두 줄
                    for (yy in listOf(edge, edge + gap, h - edge, h - edge - gap)) drawLine(c.gilt, Offset(edge, yy), Offset(w - edge, yy), g)
                    // 안쪽 금박 테 (모서리를 살짝 둥글게)
                    val inset = w * Tokens.Ratio.veilInset + edge
                    drawRoundRect(c.gilt, Offset(inset, inset + gap), Size(w - inset * 2, h - (inset + gap) * 2), CornerRadius(3.dp.toPx()), style = Stroke(g * 0.8f))
                    // 가운데 ✠ 하나
                    val m = w * 0.16f
                    stamp(STAMP_CROSS, c.gilt, Offset(w / 2, h / 2), m)
                }
                // 걷히는 끝의 옅은 그늘
                if (t > 0f) drawRect(c.shade, Offset(0f, h + y), Size(w, 8.dp.toPx() * (1f - t)))
            },
    ) {
        if (img != null) Image(img, if (s.korean) p.ko else p.en, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** 새 발자취: 아래에서 올라오는 카드 한 장. */
@Composable
fun AwardCard(s: AppState, m: Milestone) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    val name = milestoneName(ctx, m); val rule = milestoneRule(ctx, m)
    BackHandler { s.award = null }
    Box(Modifier.fillMaxSize().background(c.scrim).clickable(remember { MutableInteractionSource() }, null) { s.award = null }, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = Tokens.Radius.page, topEnd = Tokens.Radius.page)).background(c.leaf)
                .clickable(remember { MutableInteractionSource() }, null) {}.navigationBarsPadding()
                .padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s5),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            Canvas(Modifier.size(84.dp)) { medal(m, true, c.leather, c.gilt, c.unwritten) }
            Text(stringResource(R.string.new_milestone), style = Theme.small().copy(color = c.rubric), maxLines = 1)
            Text(name, style = Theme.title(k).copy(textAlign = TextAlign.Center), maxLines = 1)
            Text(rule, style = Theme.small().copy(textAlign = TextAlign.Center), maxLines = 1)
            Row(Modifier.fillMaxWidth().padding(top = Tokens.Space.s3), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                BookButton(stringResource(R.string.close), Modifier.weight(1f), quiet = true) { s.award = null }
                BookButton(stringResource(R.string.share), Modifier.weight(1f)) {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "$name · $rule\n${ctx.getString(R.string.app_name)}")
                    ctx.startActivity(Intent.createChooser(send, null))
                }
            }
        }
    }
}

