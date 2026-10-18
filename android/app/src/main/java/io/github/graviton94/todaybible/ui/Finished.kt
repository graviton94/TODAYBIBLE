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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.LaunchedEffect
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
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.core.Milestone
import io.github.graviton94.todaybible.data.Plate
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import kotlinx.coroutines.launch

/** 장을 마친 화면: 붉은 장 번호 · 한 줄 · (판화가 있는 장이면) 가죽 덮개 아래 판화 · 다음 장. */
@Composable
fun FinishedPage(s: AppState, book: Int, chapter: Int, lifted: Boolean = false) {
    // 모으는 순간은 어두운 화면 (하루의 편지 R2 · U6): 앱 테마와 상관없이
    androidx.compose.runtime.CompositionLocalProvider(io.github.graviton94.todaybible.design.LocalPalette provides io.github.graviton94.todaybible.design.Tokens.dark) { FinishedDark(s, book, chapter) }
}

@Composable
private fun FinishedDark(s: AppState, book: Int, chapter: Int) {
    val c = Theme.c; val k = s.korean
    val plate = s.store.plateFor(book, chapter)
    val shown = remember(book, chapter) { Animatable(0f) }
    LaunchedEffect(book, chapter) { shown.animateTo(1f, tween(Tokens.Motion.veilMs, easing = FastOutSlowInEasing)) }
    // 장을 마친 좋은 순간에 가끔: Play 공식 별점 창만 바로 (앞에 ‘마음에 드세요?’ 같은 물음 없이 — Play 정책).
    // 세 장을 마친 뒤부터, 30일에 한 번, 모두 세 번까지. 실제로 보일지는 Play 가 정해요
    val act = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    LaunchedEffect(book, chapter) {
        val st = s.store; val today = s.today().toEpochDay()
        if (act == null || st.finishedCount < 3 || st.reviewCount >= 3 || (st.reviewDay >= 0 && today - st.reviewDay < 30) || io.github.graviton94.todaybible.BuildConfig.DEV_TOOLS) return@LaunchedEffect
        kotlinx.coroutines.delay(Tokens.Motion.veilMs.toLong() + 1200)
        st.reviewCount = st.reviewCount + 1; st.reviewDay = today
        runCatching {
            val rm = com.google.android.play.core.review.ReviewManagerFactory.create(act)
            rm.requestReviewFlow().addOnSuccessListener { info -> rm.launchReviewFlow(act, info) }
        }
    }
    // 마음에 남은 한 줄: 닫거나 다음 장으로 갈 때 남겨요
    var line by remember(book, chapter) { mutableStateOf(s.reflection(book, chapter)?.text ?: "") }
    fun close() { s.setReflection(book, chapter, line); s.finished = null }
    // 적다가 앱이 내려가도 남게: 멈추고 1.5초 뒤 저장 (같으면 그대로)
    LaunchedEffect(line) { kotlinx.coroutines.delay(1500); s.setReflection(book, chapter, line) }
    BackHandler { close() }
    // 타자로 마친 장: 떠 있던 키보드는 내려요 (마침 화면이 한눈에 보이게, 한 줄을 적을 때만 다시)
    val kb = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current; val fm = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(book, chapter) { fm.clearFocus(true); kb?.hide() }
    val ctx = LocalContext.current
    val verses = remember(book, chapter) { s.store.book(s.translation, book).fillable(chapter).size }
    val run = io.github.graviton94.todaybible.core.Presence.streak(s.progress.days(), s.today())
    val hung = s.store.plates.count { s.plateFraction(it) >= 1f }
    Column(
        Modifier.fillMaxSize().background(c.leaf).paperGrain(c.ink).systemBarsPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s5),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3),
    ) {
        HeadText(s, book, chapter)
        Text(stringResource(R.string.chapter_done, s.bookName(book), chapter), style = Theme.title(k).copy(textAlign = TextAlign.Center))
        // 세 필사본: 다른 필사본으로 이미 마친 장이면 한 줄로 알려요 (낭독본으로도 마쳤어요)
        if (s.finishedAgain) Text(stringResource(R.string.finished_again, stringResource(when (s.finishedMode) { io.github.graviton94.todaybible.core.Mode.ALOUD -> R.string.book_aloud; io.github.graviton94.todaybible.core.Mode.PAPER -> R.string.book_paper; else -> R.string.book_type })),
            style = Theme.small().copy(color = c.giltText, textAlign = TextAlign.Center))
        Text(fmtDate(R.string.fmt_date_day, s.today()), style = Theme.small(), maxLines = 1)
        if (plate != null) {
            // 판화 한 점: 가는 금빛 테 안에 그대로 (천천히 밝아져요)
            val img = rememberPlate(plate.id)
            Box(Modifier.fillMaxWidth().padding(top = Tokens.Space.s3).drawBehind { drawRect(c.gilt.copy(alpha = Tokens.Alpha.frame), style = Stroke(Tokens.Stroke.hair.toPx())) }.padding(Tokens.Space.s2)) {
                if (img != null) Image(img, s.plateName(plate), Modifier.fillMaxWidth().height(Tokens.Size.finishedPlate).graphicsLayer { alpha = shown.value }, contentScale = ContentScale.Crop, alignment = androidx.compose.ui.BiasAlignment(0f, -0.3f))
                // 그림 오른쪽 위 구석에 작은 인장 (글은 가리지 않게)
                CompletumSeal(io.github.graviton94.todaybible.core.Latin.roman(chapter) + " · " + io.github.graviton94.todaybible.core.Latin.roman(s.today().year), Modifier.align(Alignment.TopEnd).padding(Tokens.Space.s2))
            }
            Text("〈" + s.plateName(plate) + "〉 " + s.plateBy(plate) + " · " + stringResource(R.string.ref_verse, s.bookName(plate.book), plate.chapter, plate.verse),
                style = Theme.small().copy(textAlign = TextAlign.Center, color = c.unwritten), maxLines = 2)
        } else {
            // 판화 없는 장: 큰 로마 숫자 하나
            Text(io.github.graviton94.todaybible.core.Latin.roman(chapter), style = Theme.big(Tokens.Text.initialLg).copy(color = c.gilt), maxLines = 1, modifier = Modifier.padding(vertical = Tokens.Space.s4).graphicsLayer { alpha = shown.value })
        }
        // 쓴 것 목록 (하루의 편지 R2)
        Column(Modifier.fillMaxWidth().padding(top = Tokens.Space.s2)) {
            @Composable fun row(a: String, b: String) {
                Row(Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s3), verticalAlignment = Alignment.Bottom) {
                    Text(a, style = Theme.body(), modifier = Modifier.weight(1f), maxLines = 1); Text(b, style = Theme.body().copy(color = c.inkSoft), maxLines = 1)
                }
                Hair()
            }
            row(stringResource(R.string.done_verses), "+$verses")
            row(stringResource(R.string.done_streak, run), "")
            Row(Modifier.fillMaxWidth().padding(top = Tokens.Space.s3), verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.plates), style = Theme.body(), modifier = Modifier.weight(1f).padding(bottom = Tokens.Space.s2), maxLines = 1)
                Text("$hung / ${s.store.plates.size}", style = Theme.big(Tokens.Text.display).copy(color = c.gilt), maxLines = 1)
            }
        }
        ReflectionField(s, book, chapter, line) { line = it }
        val (nb, nc) = remember(book, chapter) { s.nextChapter(book, chapter) }
        if (nb != book || nc != chapter) BookButton(stringResource(R.string.next_chapter, s.bookName(nb), nc), Modifier.fillMaxWidth().padding(top = Tokens.Space.s2)) { close(); s.open(nb, nc) }
        if (plate != null) BookButton(stringResource(R.string.share), Modifier.fillMaxWidth(), quiet = true) {
            // 판화 장면의 절 + 그 판화 (하나의 카드 틀)
            Cards.share(s, ctx, Cards.verse(ctx, s.bibleKorean, s.head(book, chapter), ctx.getString(R.string.ref_verse, s.bookName(book), chapter, plate.verse), s.text(book).verse(chapter, plate.verse), plate.id), "verse")
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
        modifier.aspectRatio(Tokens.Ratio.plateAspect).clip(RoundedCornerShape(Tokens.Radius.chip)).background(c.paper).clipToBounds()
            .drawWithContent {
                drawContent()
                if (t >= 1f) return@drawWithContent
                val w = size.width; val h = size.height
                val y = -h * t
                translate(0f, y) {
                    drawRect(c.leather, Offset.Zero, Size(w, h))
                    val g = Tokens.Stroke.gilt.toPx(); val gap = Tokens.Size.veilBandGap.toPx(); val edge = Tokens.Size.veilEdge.toPx()
                    // 위 · 아래 금선 두 줄
                    for (yy in listOf(edge, edge + gap, h - edge, h - edge - gap)) drawLine(c.gilt, Offset(edge, yy), Offset(w - edge, yy), g)
                    // 안쪽 금박 테 (모서리를 살짝 둥글게)
                    val inset = w * Tokens.Ratio.veilInset + edge
                    drawRoundRect(c.gilt, Offset(inset, inset + gap), Size(w - inset * 2, h - (inset + gap) * 2), CornerRadius(Tokens.Radius.frame.toPx()), style = Stroke(Tokens.Stroke.giltFine.toPx()))
                    // 가운데 ✠ 하나
                    val m = w * Tokens.Ratio.veilMark
                    stamp(STAMP_CROSS, c.gilt, Offset(w / 2, h / 2), m)
                }
                // 걷히는 끝의 옅은 그늘
                if (t > 0f) drawRect(c.shade, Offset(0f, h + y), Size(w, Tokens.Size.edgeShade.toPx() * (1f - t)))
            },
    ) {
        if (img != null) Image(img, s.plateName(p), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** 발자취 한 장: 큰 판화 메달 · 이름 · 조건 · 얻은 날 (방금 얻었으면 "새 발자취"). 얻은 것만 나누기. */
@Composable
fun AwardCard(s: AppState, m: Milestone) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    val day = s.earned[m]; val fresh = day != null && day == s.today().toEpochDay()
    // 숨은 메달은 받기 전엔 이름 · 조건을 가려요
    val veiled = m.hidden && day == null
    val name = if (veiled) stringResource(R.string.medal_hidden_name) else milestoneName(ctx, m); val rule = if (veiled) stringResource(R.string.medal_hidden_rule) else milestoneRule(ctx, m)
    BookSheet({ s.award = null }) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Text(if (fresh) stringResource(R.string.new_milestone).uppercase() else "VESTIGIUM · " + io.github.graviton94.todaybible.core.Latin.roman(m.ordinal + 1), style = Theme.caps(), maxLines = 1)
            PlateMedal(m, day != null, milestoneProgress(s, m), Tokens.Size.medalBig, Modifier.padding(vertical = Tokens.Space.s2))
            Text(name, style = Theme.title(k).copy(textAlign = TextAlign.Center), maxLines = 1)
            Text(rule, style = Theme.small().copy(textAlign = TextAlign.Center), maxLines = 1)
            Box(Modifier.size(Tokens.Size.cardRule, Tokens.Stroke.hair).background(c.gilt))
            Text(when {
                day != null -> stringResource(R.string.medal_got, fmtDate(R.string.fmt_ymd, java.time.LocalDate.ofEpochDay(day)))
                MILESTONE_DAYS[m] != null -> stringResource(R.string.medal_left, (MILESTONE_DAYS.getValue(m) - s.progress.days().size).coerceAtLeast(0))
                Milestone.MEMORY[m] != null -> stringResource(R.string.medal_left_verses, (Milestone.MEMORY.getValue(m) - s.knownDays().size).coerceAtLeast(0))
                !veiled && milestoneCount(s, m) != null -> milestoneCount(s, m)!!.let { (n, g) -> stringResource(R.string.medal_count, "%,d".format(minOf(n, g)), "%,d".format(g)) }
                else -> stringResource(R.string.medal_not_yet)
            }, style = Theme.small().copy(color = if (day != null) c.giltText else c.inkSoft, textAlign = TextAlign.Center), maxLines = 2)
        }
        Row(Modifier.fillMaxWidth().padding(top = Tokens.Space.s3), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            BookButton(stringResource(R.string.close), Modifier.weight(1f), quiet = true) { s.award = null }
            if (day != null) BookButton(stringResource(R.string.share), Modifier.weight(1f)) {
                Cards.share(s, ctx, Cards.year(ctx, k, name, io.github.graviton94.todaybible.core.Latin.roman(m.ordinal + 1), listOf(rule), MILESTONE_PLATE[m] ?: s.coverPlateId(), "VESTIGIUM"), "milestone")
            }
        }
    }
}
