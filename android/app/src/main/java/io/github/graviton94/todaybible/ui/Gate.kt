package io.github.graviton94.todaybible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.data.Plate
import io.github.graviton94.todaybible.design.LocalPalette
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import kotlinx.coroutines.launch

/** 파일 만들기 관문의 상태: 0 만드는 중 · 1 다 됨 · -1 못 만듦. */
data class ExportGate(val title: String, val state: Int = 0, val job: ExportJob? = null)

/** 관문의 한 줄: 0 하는 중 · 1 다 됨 · -1 안 됨. frac 은 알면 %, detail 은 크기 · 속도 같은 작은 글. */
data class GateCheck(val label: String, val state: Int, val frac: Float? = null, val detail: String? = null, val onRetry: (() -> Unit)? = null)

/** 오늘 걸어 둘 판화 한 점 (그 장 것이 없으면 날마다 바뀌는 한 점). */
fun AppState.dayPlate(b: Int = book, ch: Int = chapter): Plate? =
    store.plateFor(b, ch) ?: store.plates.takeIf { it.isNotEmpty() }?.let { it[Math.floorMod(today().toEpochDay(), it.size.toLong()).toInt()] }

/**
 * 넘어가는 화면 (하루의 편지 틀): 판화가 위를 채우고 어둠으로 녹아들며, 아래에 ‘…으로 이동 중’ 과 확인 줄들.
 * 다 되면 크림색 버튼 하나가 살아나고 눌러야 넘어가요. 다른 길 (건너뛰기 · 닫기) 은 밑줄 글로.
 */
@Composable
fun GateFrame(
    s: AppState, plate: Plate?, eyebrow: String, title: String, checks: List<GateCheck>,
    primary: String, onPrimary: () -> Unit, secondary: String? = null, onSecondary: () -> Unit = {}, onBack: () -> Unit,
) {
    CompositionLocalProvider(LocalPalette provides Tokens.dark) {
        val c = Theme.c; val k = s.korean
        BackHandler { onBack() }
        val ready = checks.all { it.state == 1 }
        val img = plate?.let { rememberPlate(it.id) }
        val fade = remember { Animatable(0f) }
        LaunchedEffect(img) { if (img != null) fade.animateTo(1f, tween(Tokens.Motion.fadeMs * 3)) }
        Box(Modifier.fillMaxSize().background(c.leaf).clickable(remember { MutableInteractionSource() }, null) {}) {
            if (img != null) Image(img, plate?.let { s.plateName(it) }, Modifier.fillMaxWidth().fillMaxHeight(Tokens.Ratio.gateArt).graphicsLayer { alpha = fade.value }.drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(0.35f to c.leaf.copy(alpha = 0f), 0.8f to c.leaf.copy(alpha = 0.75f), 1f to c.leaf))
            }, contentScale = ContentScale.Crop)
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Box(Modifier.weight(1f))
                Text(eyebrow, style = Theme.caps(), maxLines = 1)
                Text(title, style = Theme.title(k, Tokens.Text.display), maxLines = 2)
                if (plate != null) Text(stringResource(R.string.gate_plate, s.plateName(plate), s.plateBy(plate)), style = Theme.small().copy(color = c.unwritten), maxLines = 1)
                Column(Modifier.fillMaxWidth().padding(top = Tokens.Space.s2)) {
                    checks.forEach { GateRow(it) }
                    Hair()
                }
                Column(Modifier.fillMaxWidth().padding(top = Tokens.Space.s3, bottom = Tokens.Space.s4)) {
                    BookButton(primary, Modifier.fillMaxWidth(), enabled = ready) { onPrimary() }
                    if (secondary != null) BookButton(secondary, Modifier.fillMaxWidth(), quiet = true) { onSecondary() }
                }
            }
        }
    }
}

@Composable
private fun GateRow(g: GateCheck) {
    val c = Theme.c
    Hair()
    Column(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.touch).padding(vertical = Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(g.label, style = Theme.body().copy(color = if (g.state == 1) c.ink else c.inkSoft), modifier = Modifier.weight(1f), maxLines = 1)
            when (g.state) {
                1 -> Text("✓", style = Theme.body().copy(color = c.gilt))
                -1 -> Text(stringResource(R.string.gate_retry), style = Theme.small().copy(color = c.gilt, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                    modifier = Modifier.heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Button) { g.onRetry?.invoke() })
                else -> Text(g.frac?.let { "${(it * 100).toInt()}%" } ?: "…", style = Theme.big(Tokens.Text.gateNum), maxLines = 1)
            }
        }
        if (g.state == 0) GateBar(g.frac)
        g.detail?.let { Text(it, style = Theme.small().copy(color = c.unwritten), maxLines = 1) }
    }
}

/** 가는 금선: 알면 그만큼, 모르면 오가는 짧은 띠. */
@Composable
private fun GateBar(frac: Float?) {
    val c = Theme.c
    Box(Modifier.fillMaxWidth().height(Tokens.Stroke.hair).background(c.hair)) {
        if (frac != null) Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(Tokens.Stroke.hair).background(c.gilt))
        else {
            val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "gate")
            val x by t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(tween(Tokens.Motion.fadeMs * 4)), label = "x")
            Box(Modifier.fillMaxWidth().height(Tokens.Stroke.hair).drawWithContent {
                drawRect(c.gilt, androidx.compose.ui.geometry.Offset(size.width * (x * 1.3f - 0.3f), 0f), androidx.compose.ui.geometry.Size(size.width * 0.3f, size.height))
            })
        }
    }
}

/** 낭독 받기 관문: 본문 ✓ · 낭독 음원 % → 눌러서 들어가기. 아니면 교독 없이 필사만. */
@Composable
fun NarrationGate(s: AppState, b: Int, ch: Int) {
    val N = io.github.graviton94.todaybible.data.Narration
    val all by N.progress.collectAsState()
    val st = s.narrationState(b, ch)
    val p = all[N.key(s.narrator, b, ch)]
    val frac = p?.takeIf { it.total > 0 }?.let { it.bytes.toFloat() / it.total }
    val detail = if (st == 0 && p != null && p.bytes > 0) {
        val sec = ((System.currentTimeMillis() - p.startMs) / 1000f).coerceAtLeast(0.1f); val bps = p.bytes / sec
        fun mb(x: Long) = "%.1f".format(x / 1_000_000f)
        val left = if (p.total > 0 && bps > 0) ((p.total - p.bytes) / bps).toInt().coerceAtLeast(0) else null
        listOfNotNull(if (p.total > 0) "${mb(p.bytes)} / ${mb(p.total)}MB" else "${mb(p.bytes)}MB",
            if (bps >= 1_000_000) "%.1fMB/s".format(bps / 1_000_000f) else "${(bps / 1000).toInt()}KB/s",
            left?.let { stringResource(R.string.narr_left, it) }).joinToString(" · ")
    } else if (st == 0) stringResource(R.string.narr_connecting) else null
    val ref = s.chapterRef(b, ch)
    GateFrame(s, s.dayPlate(b, ch), io.github.graviton94.todaybible.core.Latin.head(b, ch),
        stringResource(if (st == 1) R.string.gate_ready else R.string.gate_going, ref),
        listOf(
            GateCheck(stringResource(R.string.gate_text), 1),
            GateCheck(stringResource(R.string.gate_voice), when (st) { 1 -> 1; -1 -> -1; else -> 0 }, frac, detail) { s.narration = s.narration - "$b:$ch"; s.fetchNarration(b, ch) },
        ),
        stringResource(R.string.gate_enter), { s.narrGate = null },
        stringResource(R.string.gate_copy_only), { s.forceTab = 1; s.narrGate = null },
        onBack = { s.narrGate = null })
}

/** 파일 만들기 관문: 기록 모으기 ✓ · 파일 만들기 → 눌러서 저장 · 보내기. */
@Composable
fun ExportGateScreen(s: AppState, g: ExportGate) {
    GateFrame(s, s.dayPlate(), "LIBER", stringResource(when (g.state) { 1 -> R.string.gate_file_ready; -1 -> R.string.gate_file_failed; else -> R.string.gate_file_making }, g.title),
        listOf(
            GateCheck(stringResource(R.string.gate_gather), 1),
            GateCheck(stringResource(R.string.gate_make), g.state),
        ),
        stringResource(R.string.gate_save), { g.job?.let { s.exportJob = it }; s.exportGate = null },
        stringResource(R.string.close), { s.exportGate = null },
        onBack = { s.exportGate = null })
}

/**
 * 여는 순간 (하루의 편지 틀): 판화 한 점이 화면을 가득 채우고 천천히 가라앉으며, 아래에 날짜 · 앱 이름 · 그림 이름.
 * 하루 첫 열기면 오늘 쓸 한 절도. 누르면 스러지며 들어가요.
 */
@Composable
fun PlateIntro(s: AppState, first: Boolean, onEnter: () -> Unit) {
    CompositionLocalProvider(LocalPalette provides Tokens.dark) {
        val c = Theme.c; val k = s.korean
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
        val plate = remember { s.dayPlate() }
        val img = plate?.let { rememberPlate(it.id) }
        val show = remember { Animatable(0f) }; val drift = remember { Animatable(0f) }; val enter = remember { Animatable(0f) }; val words = remember { Animatable(0f) }
        LaunchedEffect(img) {
            if (img == null) return@LaunchedEffect
            launch { show.animateTo(1f, tween(Tokens.Motion.introEnterMs * 2)) }
            launch { drift.animateTo(1f, tween(Tokens.Motion.introDriftMs)) }
        }
        LaunchedEffect(Unit) { kotlinx.coroutines.delay(400); words.animateTo(1f, tween(Tokens.Motion.introEnterMs * 2)) }
        fun go() {
            if (enter.value > 0f) return
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            scope.launch { enter.animateTo(1f, tween(Tokens.Motion.introEnterMs)); onEnter() }
        }
        BackHandler { go() }
        val t = s.text(); val v = s.progress.nextVerse(s.translation, t, s.chapter) ?: 1
        val verse = remember(s.book, s.chapter, v) { io.github.graviton94.todaybible.core.Markup.plain(t.verse(s.chapter, v)) }
        Box(Modifier.fillMaxSize().background(c.leaf).graphicsLayer { alpha = 1f - enter.value }
            .clickable(remember { MutableInteractionSource() }, null) { go() }) {
            if (img != null) Image(img, plate?.let { s.plateName(it) }, Modifier.fillMaxSize().graphicsLayer {
                alpha = show.value; val sc = 1.08f - 0.08f * drift.value + 0.04f * enter.value; scaleX = sc; scaleY = sc
            }.drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(0f to c.leaf.copy(alpha = 0.35f), 0.3f to c.leaf.copy(alpha = 0f), 0.55f to c.leaf.copy(alpha = 0.2f), 0.85f to c.leaf.copy(alpha = 0.92f), 1f to c.leaf))
            }, contentScale = ContentScale.Crop)
            Text(fmtDate(R.string.fmt_date_full, s.today()), style = Theme.small().copy(color = c.ink.copy(alpha = 0.8f), letterSpacing = androidx.compose.ui.unit.TextUnit(0.12f, androidx.compose.ui.unit.TextUnitType.Em)), maxLines = 1,
                modifier = Modifier.align(Alignment.TopCenter).systemBarsPadding().padding(top = Tokens.Space.s4).graphicsLayer { alpha = words.value })
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = Tokens.Space.s6).graphicsLayer { alpha = words.value; translationY = (1f - words.value) * 24f },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Box(Modifier.weight(1f))
                Text(stringResource(R.string.intro_caps), style = Theme.caps(), maxLines = 1)
                Text(stringResource(R.string.app_name), style = Theme.title(k, Tokens.Text.introPlate), maxLines = 1)
                Box(Modifier.width(Tokens.Size.coverW * 0.4f).height(Tokens.Stroke.hair).background(c.gilt))
                if (first) {
                    Text(verse, style = Theme.body().copy(color = c.ink, textAlign = TextAlign.Center), maxLines = 4)
                    Text(stringResource(R.string.ref_verse, s.bookName(), s.chapter, v), style = Theme.small().copy(color = c.gilt))
                }
                if (plate != null) Text(stringResource(R.string.gate_plate, s.plateName(plate), s.plateBy(plate)), style = Theme.small().copy(color = c.unwritten, textAlign = TextAlign.Center), maxLines = 2)
                Breathing(stringResource(R.string.intro_tap_enter), c.ink, Modifier.fillMaxWidth().padding(top = Tokens.Space.s4, bottom = Tokens.Space.s5))
            }
        }
    }
}
