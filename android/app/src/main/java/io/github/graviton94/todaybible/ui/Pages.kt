package io.github.graviton94.todaybible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** 앱 뼈대: 오늘 · 필사 · 서재 · 기록 네 장을 책장처럼 넘김. 설정 · 장 마침 · 발자취 · 판화는 그 위에. 처음엔 소개, 켤 때마다 표지 넘김. */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun Root(s: AppState) {
    val c = Theme.c
    val pager = rememberPagerState(initialPage = s.page) { 4 }
    val scope = rememberCoroutineScope()
    // 이름표 · 버튼으로 옮길 땐 ‘숨 한 번’, 손으로 넘길 땐 ‘내려앉는 종이’
    var breath by remember { mutableStateOf(false) }
    fun turnTo(i: Int) { scope.launch { breath = true; try { pager.animateScrollToPage(i, animationSpec = androidx.compose.animation.core.tween(Tokens.Motion.pageMs)) } finally { breath = false } } }
    LaunchedEffect(s.page) { if (pager.currentPage != s.page && !pager.isScrollInProgress) turnTo(s.page) }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { s.page = it } }
    BackHandler(enabled = pager.currentPage != 0 && !s.settingsOpen && s.finished == null && s.award == null && s.plateView == null && !s.purchaseOpen && s.onboarded && !s.opening) { turnTo(0) }
    // 옮겨 쓰는 동안 (키보드가 떠 있으면) 옆으로 넘어가지 않게
    val typing = WindowInsets.isImeVisible && pager.currentPage == 1

    Box(Modifier.fillMaxSize().background(c.paper)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            TopBar(s)
            HorizontalPager(
                pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 0, userScrollEnabled = !typing,
                flingBehavior = PagerDefaults.flingBehavior(pager, snapPositionalThreshold = Tokens.Motion.turnSnap),
            ) { page ->
                val incoming by remember(page) { derivedStateOf { (pager.currentPage - page) + pager.currentPageOffsetFraction < 0f } }
                Box(Modifier.fillMaxSize().zIndex(if (incoming) 1f else 0f).pageTurn(pager, page) { breath }) {
                    when (page) {
                        0 -> HomePage(s)
                        1 -> CopyPage(s)
                        2 -> LibraryPage(s)
                        else -> RecordPage(s)
                    }
                }
            }
            if (!typing) PageTabs(pager.currentPage) { turnTo(it) }
        }
        if (s.settingsOpen) Box(Modifier.fillMaxSize().background(c.leaf).statusBarsPadding().navigationBarsPadding()) { SettingsPage(s) }
        s.finished?.let { (b, ch) -> FinishedPage(s, b, ch) }
        if (s.purchaseOpen) Box(Modifier.fillMaxSize().background(c.leaf).statusBarsPadding().navigationBarsPadding()) { PurchasePage(s) }
        s.shareVerse?.let { v -> ShareVerseSheet(s, v) }
        s.plateView?.let { pl -> Box(Modifier.fillMaxSize().background(c.leaf).statusBarsPadding().navigationBarsPadding()) { PlatePage(s, pl) } }
        // 나의 성경 PDF: 만들어서 나누기 창으로
        val ctx = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(s.pdfBook) {
            val b = s.pdfBook ?: return@LaunchedEffect
            val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { MyBible.make(ctx, s.store, s.translation, b) }.getOrNull() }
            s.pdfBook = null; f?.let { MyBible.share(ctx, it) }
        }
        if (s.planOpen) PlanSheet(s)
        s.picker?.let { b -> BookSheet({ s.picker = null }) { ChapterGrid(s, b) { ch -> s.picker = null; s.open(b, ch) } } }
        s.award?.takeIf { s.finished == null }?.let { AwardCard(s, it) }
        // 토스트: 이름표 위에 잠깐
        s.toast?.let { msg ->
            LaunchedEffect(msg) { kotlinx.coroutines.delay(Tokens.Motion.toastMs.toLong()); s.toast = null }
            Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = Tokens.Size.touch + Tokens.Space.s4, start = Tokens.Space.s5, end = Tokens.Space.s5), contentAlignment = Alignment.BottomCenter) { BookToast(msg) }
        }
        if (!s.onboarded) Welcome(s)
        else if (s.opening) Opening(s) { s.opening = false }
    }
}

/** 맨 위 한 줄: 앱 이름 (표제 글꼴) · 오른쪽 톱니. */
@Composable
private fun TopBar(s: AppState) {
    val c = Theme.c
    Row(Modifier.fillMaxWidth().padding(start = Tokens.Space.s5, end = Tokens.Space.s1), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.app_name), style = Theme.brand(s.korean), maxLines = 1, modifier = Modifier.weight(1f))
        val label = stringResource(R.string.settings)
        Box(Modifier.size(Tokens.Size.touch).semantics { contentDescription = label }.clickable(role = Role.Button) { s.settingsOpen = true }, contentAlignment = Alignment.Center) {
            Gear(Modifier.size(Tokens.Size.icon))
        }
    }
}

/** 톱니 (가는 선): 바퀴 하나 + 이 여덟. */
@Composable
private fun Gear(modifier: Modifier) {
    val c = Theme.c
    Box(modifier.drawBehind {
        val w = Tokens.Stroke.rule.toPx() * 0.9f; val r = size.minDimension / 2
        drawCircle(c.inkSoft, r * 0.58f, style = Stroke(w))
        drawCircle(c.inkSoft, r * 0.2f, style = Stroke(w))
        for (i in 0 until 8) {
            val a = i * PI.toFloat() / 4; val dx = kotlin.math.cos(a); val dy = sin(a)
            drawLine(c.inkSoft, Offset(center.x + dx * r * 0.62f, center.y + dy * r * 0.62f), Offset(center.x + dx * r * 0.95f, center.y + dy * r * 0.95f), w * 1.6f, cap = StrokeCap.Butt)
        }
    })
}

/** 아래 이름표 세 개: 고른 장 위에 붉은 한 줄. */
@Composable
private fun PageTabs(current: Int, onSelect: (Int) -> Unit) {
    val c = Theme.c
    val names = listOf(stringResource(R.string.page_today), stringResource(R.string.page_copy), stringResource(R.string.page_library), stringResource(R.string.page_record))
    Row(Modifier.fillMaxWidth().background(c.paper).navigationBarsPadding().drawBehind {
        drawLine(c.hair, Offset.Zero, Offset(size.width, 0f), Tokens.Stroke.hair.toPx())
    }) {
        names.forEachIndexed { i, n ->
            val on = i == current
            Box(
                Modifier.weight(1f).heightIn(min = Tokens.Size.touch).clickable(role = Role.Tab) { onSelect(i) }.drawBehind {
                    if (on) { val w = size.width * 0.32f; drawRect(c.rubric, Offset((size.width - w) / 2, 0f), Size(w, Tokens.Stroke.rule.toPx())) }
                },
                contentAlignment = Alignment.Center,
            ) { Text(n, style = Theme.label().copy(color = if (on) c.ink else c.inkSoft), maxLines = 1) }
        }
    }
}

/**
 * 페이지 넘김 (하루의 정원과 같은 결). o = 이 장이 넘어간 정도 (0 = 펼쳐짐, 1 = 왼쪽으로 다 넘어감, -1 = 아직 오른쪽).
 * 손으로 넘길 때: 새 장이 살짝 기운 채 들어와 바르게 내려앉고, 아래 장은 제자리에서 조금 그늘짐.
 * 이름표로 옮길 때 (breath): 지금 장이 조금 흐르며 옅어지고, 새 장이 반대편에서 스며듦.
 */
@Composable
private fun Modifier.pageTurn(pager: PagerState, page: Int, breath: () -> Boolean): Modifier {
    val c = Theme.c
    val d = LocalDensity.current.density
    val M = Tokens.Motion
    return background(c.leaf).graphicsLayer {
        val o = (pager.currentPage - page) + pager.currentPageOffsetFraction
        val a = abs(o)
        if (breath()) {
            val t = (a * 1.8f).coerceIn(0f, 1f); val out = t * t * (3 - 2 * t)
            translationX = o * size.width - o * M.breathDrift * d
            alpha = 1f - out
            val k = 1f - 0.015f * a; scaleX = k; scaleY = k
        } else if (o < 0f) {
            transformOrigin = TransformOrigin(0f, 1f)
            rotationZ = M.turnTilt * a
            translationY = -M.turnLift * d * sin(a * PI.toFloat())
        } else if (o > 0f) {
            translationX = o * size.width
            val k = 1f - 0.02f * o; scaleX = k; scaleY = k
        }
    }.drawWithContent {
        drawContent()
        if (breath()) return@drawWithContent
        val o = (pager.currentPage - page) + pager.currentPageOffsetFraction
        if (o > 0f) drawRect(c.scrim.copy(alpha = c.scrim.alpha * (o * M.turnShade).coerceIn(0f, 1f)))
        else if (o < 0f) {
            val e = M.turnEdge * d
            drawRect(Brush.horizontalGradient(listOf(Color.Transparent, c.shade), -e, 0f), Offset(-e, 0f), Size(e, size.height))
        }
    }
}
