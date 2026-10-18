package io.github.graviton94.todaybible.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Plan
import io.github.graviton94.todaybible.core.Plans
import io.github.graviton94.todaybible.design.LocalPalette
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import java.time.LocalDate

/**
 * 작정 (1.6, 전 「읽기 계획」): 정한 날까지 정한 말씀을 날마다 옮겨 써요.
 * 목록 (다가오는 날 · 언제든, 줄 높이 같게, 왼쪽에 판화) → 작정 카드 (기간 · 하루 몫 · 마침, 기도 제목 한 줄) → 오늘 화면의 작정 칸 → 마침 카드.
 * 무료 권만 쓰는 작정은 무료, 나머지는 평생권 (권 잠금을 그대로 따라요).
 */
private fun res(ctx: android.content.Context, name: String) = ctx.resources.getIdentifier(name, "string", ctx.packageName)
private fun md(ctx: android.content.Context, d: LocalDate) = Lang.date(ctx, R.string.fmt_md, d)
private val GRAY = ColorMatrix().apply { setToSaturation(0f) }

@Composable
fun VotaSheet(s: AppState) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current; val today = s.today()
    var open by remember { mutableStateOf(s.planAsk.also { s.planAsk = null }) }
    BookSheet({ s.planOpen = false }) {
        open?.let { id -> Plans.byId(id)?.let { VotumCard(s, it) { open = null } }; return@BookSheet }
        Column(Modifier.fillMaxWidth().heightIn(max = Tokens.Size.votumListMax).verticalScroll(rememberScrollState())) {
            Text("VOTA", style = Theme.caps(), maxLines = 1)
            Text(stringResource(R.string.plan), style = Theme.title(k, Tokens.Text.display), maxLines = 1)
            // 다가오는 날: 향하는 날의 창이 가까운 순
            val dated = remember(today) { Plans.all.filter { it.dated }.mapNotNull { p -> Plans.window(p, today, k)?.let { p to it } }.sortedBy { it.second.first } }
            SectionHead("ADVENTUS", stringResource(R.string.votum_soon), 1, Modifier.padding(top = Tokens.Space.s3))
            dated.forEach { (p, w) ->
                val right = if (w.first.isAfter(today)) stringResource(R.string.votum_starts, md(ctx, w.first)) else ""
                VotumRow(s, p, stringResource(R.string.votum_until, md(ctx, w.second), p.days), right) { open = p.id }
            }
            SectionHead("SEMPER", stringResource(R.string.votum_any), 2, Modifier.padding(top = Tokens.Space.s3))
            Plans.all.filter { !it.dated }.forEach { p ->
                val verses = remember(p.id, s.translation) { p.chapters.sumOf { (b, ch) -> s.store.book(s.translation, b).fillable(ch).size } }
                VotumRow(s, p, stringResource(R.string.votum_rate, (verses + p.days - 1) / p.days), "") { open = p.id }
            }
        }
        if (s.plan != null) BookButton(stringResource(R.string.plan_none), Modifier.fillMaxWidth(), quiet = true) { s.choosePlan(null) }
    }
}

/** 목록 한 줄 (높이 같게): 판화 · 이름 · 한 줄 · 오른쪽에 시작일 · ✓ · 자물쇠. */
@Composable
private fun VotumRow(s: AppState, p: Plan, meta: String, right: String, onClick: () -> Unit) {
    val c = Theme.c; val ctx = LocalContext.current
    val locked = p.books.any { s.premium(it) }; val on = s.planId == p.id
    val img = rememberPlate(p.plate, small = true)
    Row(Modifier.fillMaxWidth().height(Tokens.Size.votumRow).chosen(on).clickable(role = Role.Button, onClick = tap(onClick)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Box(Modifier.size(Tokens.Size.plateCard).clip(RoundedCornerShape(Tokens.Radius.chip)).background(c.hair)) {
            if (img != null) Image(img, null, Modifier.fillMaxSize().graphicsLayer { alpha = if (locked) Tokens.Alpha.faint else 1f }, contentScale = ContentScale.Crop,
                colorFilter = if (locked) ColorFilter.colorMatrix(GRAY) else null)
        }
        Column(Modifier.weight(1f)) {
            Text(planName(ctx, p.id), style = Theme.body().copy(fontWeight = FontWeight.SemiBold, color = if (on) c.giltText else c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = Theme.small(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            locked -> PremiumTag()
            on -> Text("✓", style = Theme.body().copy(color = c.gilt))
            right.isNotEmpty() -> Text(right, style = Theme.small().copy(color = c.giltText), maxLines = 1)
        }
    }
    Hair()
}

/** 작정 카드: 판화 · 이름 · 한 줄 · 기간 · 하루 몫 · 마침 · 기도 제목 · 작정합니다. */
@Composable
private fun VotumCard(s: AppState, p: Plan, onBack: () -> Unit) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current; val today = s.today()
    val w = remember(p.id, today) { Plans.window(p, today, k) ?: (today to today.plusDays(p.days - 1L)) }
    val img = rememberPlate(p.plate)
    var note by remember { mutableStateOf(if (s.planId == p.id) s.planNote else "") }
    Column(Modifier.fillMaxWidth().heightIn(max = Tokens.Size.votumListMax).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        if (img != null) Image(img, null, Modifier.fillMaxWidth().height(Tokens.Size.votumHero).clip(RoundedCornerShape(Tokens.Radius.card))
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent { drawContent(); drawRect(Brush.verticalGradient(0.45f to Color.Black, 1f to Color.Transparent), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn) },
            contentScale = ContentScale.Crop, alignment = androidx.compose.ui.BiasAlignment(0f, -0.3f))
        Text("VOTUM · " + p.seal, style = Theme.caps(), maxLines = 1)
        Text(planName(ctx, p.id), style = Theme.title(k, Tokens.Text.display), maxLines = 1)
        Text(stringResource(res(ctx, "votum_line_${p.id}")), style = Theme.body())
        // 짧게 세 줄: 기간 · 하루 몫 · 마침
        @Composable fun fact(label: String, value: String) = Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Text(label, style = Theme.small(), maxLines = 1, modifier = Modifier.width(Tokens.Size.romanCol * 2))
            Text(value, style = Theme.small().copy(color = c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val verses = remember(p.id, s.translation) { p.chapters.sumOf { (b, ch) -> s.store.book(s.translation, b).fillable(ch).size } }
        fact(stringResource(R.string.votum_period), stringResource(R.string.votum_span, md(ctx, w.first), md(ctx, w.second), p.days))
        fact(stringResource(R.string.votum_daily), if (p.chapters.size == p.days) stringResource(R.string.votum_daily_ch) else stringResource(R.string.votum_rate, (verses + p.days - 1) / p.days))
        fact(stringResource(R.string.votum_end), stringResource(R.string.votum_seal, stringResource(res(ctx, "votum_seal_${p.id}"))))
        // 기도 제목 (선택)
        BasicTextField(note, { note = it.take(40) }, singleLine = true, textStyle = Theme.body().copy(color = c.ink, fontStyle = FontStyle.Italic),
            cursorBrush = SolidColor(c.gilt), modifier = Modifier.fillMaxWidth().padding(top = Tokens.Space.s2),
            decorationBox = { inner ->
                Column {
                    Box(Modifier.padding(vertical = Tokens.Space.s2)) {
                        if (note.isEmpty()) Text(stringResource(R.string.votum_note_hint), style = Theme.small(), maxLines = 1)
                        inner()
                    }
                    Box(Modifier.fillMaxWidth().height(Tokens.Stroke.hair).background(c.gilt))
                }
            })
        val later = w.first.isAfter(today)
        Row(Modifier.padding(top = Tokens.Space.s2), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            BookButton(stringResource(R.string.votum_later), Modifier.weight(1f), quiet = true, onClick = onBack)
            BookButton(if (later) stringResource(R.string.votum_go_from, md(ctx, w.first)) else stringResource(R.string.votum_go), Modifier.weight(2f),
                locked = p.books.any { s.premium(it) }) { s.choosePlan(p.id, note) }
        }
        if (s.fills.isNotEmpty()) s.resumePlace().let { (b, ch) ->
            Text(stringResource(R.string.votum_keep, s.chapterRef(b, ch)), style = Theme.small().copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * 오늘 화면의 작정 칸 (2×2): 이름 | 며칠째 · 진도선 / 기도 제목 · 오늘 몫 | 이어 쓰기.
 * 작정이 없으면 한 줄 (VOTA · 작정 고르기). elsewhere = 오늘 카드가 작정 밖을 보여 줄 때 작정의 장 (이때만 버튼).
 */
@Composable
fun VotumBand(s: AppState, elsewhere: Triple<Int, Int, Int>?, glow: Boolean) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current; val p = s.plan
    if (p == null) {
        Row(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.touch).clickable(role = Role.Button, onClick = tap { s.planOpen = true }),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Text("VOTA", style = Theme.caps().copy(color = c.giltText), maxLines = 1)
            Text(stringResource(R.string.plan) + " · " + stringResource(R.string.plan_choose), style = Theme.small(), maxLines = 1, modifier = Modifier.weight(1f))
            Text("›", style = Theme.small().copy(color = c.unwritten), maxLines = 1)
        }
        return
    }
    val img = rememberPlate(p.plate, small = true)
    val shape = RoundedCornerShape(Tokens.Radius.card)
    val pending = s.planPending(); val (total, done) = s.planCounts(); val today = s.today()
    Box(Modifier.fillMaxWidth().clip(shape).border(Tokens.Stroke.hair, c.gilt, shape).background(c.leaf).clickable(role = Role.Button, onClick = tap { s.planOpen = true })) {
        // 오른쪽에 옅게 그 작정의 판화
        if (img != null) Image(img, null, Modifier.matchParentSize().graphicsLayer { alpha = Tokens.Alpha.seasonPlate; compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithContent { drawContent(); drawRect(Brush.horizontalGradient(0.35f to Color.Transparent, 1f to Color.Black), blendMode = androidx.compose.ui.graphics.BlendMode.DstIn) },
            contentScale = ContentScale.Crop, alignment = Alignment.CenterEnd)
        Column(Modifier.fillMaxWidth().padding(Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("VOTUM", style = Theme.caps(), maxLines = 1)
                    Text(planName(ctx, p.id), style = Theme.title(k), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (pending) Text(stringResource(R.string.votum_wait, (s.store.planStart - today.toEpochDay()).toInt()), style = Theme.small().copy(color = c.giltText), maxLines = 1)
                    else Text(stringResource(R.string.votum_day, minOf(s.planDay(), p.days), p.days), style = Theme.lining(Tokens.Text.label).copy(color = c.giltText), maxLines = 1)
                    Box(Modifier.padding(top = Tokens.Space.s1).width(Tokens.Size.votumBar).height(Tokens.Stroke.rule).background(c.hair)) {
                        if (total > 0) Box(Modifier.fillMaxWidth(done.toFloat() / total).height(Tokens.Stroke.rule).background(c.gilt))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Column(Modifier.weight(1f)) {
                    if (s.planNote.isNotBlank()) Text("“${s.planNote}”", style = Theme.small().copy(color = c.giltText, fontStyle = FontStyle.Italic), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val next = s.planNext()
                    Text(when {
                        pending -> stringResource(R.string.votum_starts, md(ctx, LocalDate.ofEpochDay(s.store.planStart)))
                        next == null -> stringResource(R.string.plan_done)
                        else -> stringResource(R.string.votum_today, s.chapterRef(next.first, next.second))
                    }, style = Theme.small(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (elsewhere != null && !pending) BookButton(stringResource(R.string.continue_now), glow = glow) { s.open(elsewhere.first, elsewhere.second) }
            }
        }
    }
}

/** 마침 카드: 작정을 다 쓴 뒤 처음 오늘을 열 때 한 번. 인장 · 기간 · 쓴 절 · 기도 제목 · 간직하기 / 보내기. */
@Composable
fun VotumDone(s: AppState, key: String) {
    val p = s.plan ?: return
    val ctx = LocalContext.current; val k = s.korean
    val start = LocalDate.ofEpochDay(s.store.planStart); val (total, _) = s.planCounts()
    fun close() { s.store.votumShown = key; s.votumDone = null }
    androidx.activity.compose.BackHandler { close() }
    CompositionLocalProvider(LocalPalette provides Tokens.dark) {
        val c = Theme.c
        Column(Modifier.fillMaxSize().background(c.leaf).paperGrain(c.ink).navigationBarsPadding().padding(Tokens.Space.s5),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3, Alignment.CenterVertically)) {
            Text("VOTUM COMPLETUM", style = Theme.caps(), maxLines = 1)
            CompletumSeal(p.seal)
            val name = planName(ctx, p.id)
            Text(stringResource(R.string.votum_done, name), style = Theme.title(k, Tokens.Text.title).copy(textAlign = TextAlign.Center))
            Text(stringResource(R.string.votum_done_span, md(ctx, start), md(ctx, s.today()), "%,d".format(total)), style = Theme.small().copy(textAlign = TextAlign.Center))
            if (s.planNote.isNotBlank()) Text("“${s.planNote}”", style = Theme.body().copy(color = c.giltText, fontStyle = FontStyle.Italic, textAlign = TextAlign.Center))
            Row(Modifier.padding(top = Tokens.Space.s3), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                BookButton(stringResource(R.string.votum_keep_btn), Modifier.weight(1f), quiet = true) { close() }
                BookButton(stringResource(R.string.votum_send), Modifier.weight(1f)) {
                    val text = ctx.getString(R.string.votum_done, name) + "\n" + ctx.getString(R.string.votum_done_span, md(ctx, start), md(ctx, s.today()), "%,d".format(total)) +
                        (if (s.planNote.isNotBlank()) "\n“${s.planNote}”" else "")
                    ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text), null))
                    close()
                }
            }
        }
    }
}

/** 이 장을 다른 방식으로 쓰던 중일 때: 쓰던 방식을 맨 위에, 방식마다 쓴 절 수. 고르면 그 탭으로. */
@Composable
fun ModeAskSheet(s: AppState, a: AppState.ModeAsk) {
    val c = Theme.c; val k = s.korean
    val names = listOf(stringResource(R.string.mode_aloud), stringResource(R.string.mode_type), stringResource(R.string.mode_paper))
    BookSheet({ s.modeAsk = null }) {
        Text(s.chapterRef(s.book, s.chapter), style = Theme.caps(), maxLines = 1)
        Text(stringResource(R.string.mode_ask_title), style = Theme.title(k), modifier = Modifier.fillMaxWidth())
        (listOf(a.last) + (0..2).filter { it != a.last }).forEach { i ->
            Row(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.touch).clickable(role = Role.Button, onClick = tap { s.forceTab = i; s.modeAsk = null }),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Column(Modifier.weight(1f)) {
                    Text(names[i], style = Theme.body().copy(fontWeight = if (i == a.last) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1)
                    if (i == a.last) Text(stringResource(R.string.mode_ask_last), style = Theme.small().copy(color = c.giltText), maxLines = 1)
                }
                Text(stringResource(R.string.mode_ask_count, a.counts[i]), style = Theme.small(), maxLines = 1)
                Text("›", style = Theme.small().copy(color = c.unwritten), maxLines = 1)
            }
            Hair()
        }
        Text(stringResource(R.string.mode_ask_note, a.done, a.total), style = Theme.small(), modifier = Modifier.fillMaxWidth())
    }
}
