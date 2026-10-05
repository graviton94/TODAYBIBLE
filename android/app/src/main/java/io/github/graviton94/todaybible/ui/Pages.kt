package io.github.graviton94.todaybible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
    // 옆 장이면 책장을 넘기듯 한 장, 멀리 뛰면 숨 한 번
    var turning by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var turnSeq by remember { mutableIntStateOf(0) }
    // 지나온 길 (뒤로 가기가 되짚어요)
    val trail = remember { mutableListOf<Int>() }
    var backing by remember { mutableStateOf(false) }
    fun turnTo(i: Int) {
        turning?.cancel()
        val n = ++turnSeq
        turning = scope.launch {
            val far = abs(i - pager.currentPage) > 1
            breath = far
            try { pager.animateScrollToPage(i, animationSpec = androidx.compose.animation.core.tween(if (far) Tokens.Motion.pageMs else Tokens.Motion.turnMs, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
            // 앞선 넘김이 취소될 때 새 넘김의 모양을 지우지 않게
            finally { if (n == turnSeq) breath = false }
        }
    }
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    LaunchedEffect(s.page) { if (pager.targetPage != s.page) turnTo(s.page) }
    LaunchedEffect(pager) {
        var last = pager.settledPage
        snapshotFlow { pager.settledPage }.collect {
            if (it != last) { if (!backing) { trail.remove(last); trail.add(last) }; backing = false; last = it }
            // 다른 장으로 가면 키보드는 내려요
            focus.clearFocus(); keyboard?.hide()
            s.page = it
        }
    }
    BackHandler(enabled = pager.currentPage != 0 && !s.settingsOpen && s.finished == null && s.award == null && s.plateView == null && s.handBook == null && !(s.listenAt != null && pager.currentPage == AppState.BIBLE) && !s.purchaseOpen && s.onboarded && !s.opening) { val to = trail.removeLastOrNull()?.takeIf { it != pager.currentPage } ?: 0; backing = true; s.page = to; turnTo(to) }
    // 옮겨 쓰는 동안 (키보드가 떠 있으면) 아래 이름표는 숨김
    val typing = WindowInsets.isImeVisible && pager.currentPage == AppState.COPY

    Box(Modifier.fillMaxSize().background(c.paper)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            TopBar(s)
            HorizontalPager(
                pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 0,
                // 페이지는 아래 이름표로만 옮겨요 (옆으로 밀기는 위아래 스크롤과 다퉈서 없앰)
                userScrollEnabled = false,
                flingBehavior = PagerDefaults.flingBehavior(pager, snapPositionalThreshold = Tokens.Motion.turnSnap,
                    snapAnimationSpec = androidx.compose.animation.core.tween(Tokens.Motion.turnMs, easing = androidx.compose.animation.core.FastOutSlowInEasing)),
            ) { page ->
                val incoming by remember(page) { derivedStateOf { (pager.currentPage - page) + pager.currentPageOffsetFraction < 0f } }
                Box(Modifier.fillMaxSize().zIndex(if (incoming) 1f else 0f).pageTurn(pager, page) { breath }) {
                    when (page) {
                        0 -> HomePage(s)
                        AppState.BIBLE -> LibraryPage(s)
                        AppState.COPY -> CopyPage(s)
                        else -> RecordPage(s)
                    }
                }
            }
            if (!typing) NowPlayingBar(s)
            if (!typing) PageTabs(pager.currentPage) { turnTo(it) }
        }
        if (s.settingsOpen) Box(Modifier.fillMaxSize().background(c.leaf).statusBarsPadding().navigationBarsPadding()) { SettingsPage(s) }
        s.finished?.let { (b, ch) -> FinishedPage(s, b, ch) }
        s.shareVerse?.let { v -> ShareVerseSheet(s, v) }
        s.handBook?.let { b -> HandBookView(s, b) }
        if (s.marksOpen) MarksSheet(s)
        if (s.found != null) FoundSheet(s)
        if (s.memoryOpen != null) MemorySheet(s)
        if (s.sermonOpen != null) SermonSheet(s)
        if (s.prayersOpen) PrayersSheet(s)
        s.prayerOpen?.let { id -> Box(Modifier.fillMaxSize().background(c.leaf).statusBarsPadding().navigationBarsPadding()) { PrayerPage(s, id) } }
        s.exportJob?.let { ExportSheet(s, it) }
        s.plateView?.let { pl -> Box(Modifier.fillMaxSize().background(c.leaf).statusBarsPadding().navigationBarsPadding()) { PlatePage(s, pl) } }
        // 평생권은 어느 창에서 열어도 맨 위에
        if (s.purchaseOpen) Box(Modifier.fillMaxSize().background(c.leaf).statusBarsPadding().navigationBarsPadding()) { PurchasePage(s) }
        // 하던 일을 두고 나갈지 묻는 창은 모든 창 위에
        s.leaveAsk?.let { LeaveSheet(s, it) }
        // 나의 성경 PDF: 만들어서 나누기 창으로
        val ctx = androidx.compose.ui.platform.LocalContext.current
        LaunchedEffect(s.pdfBook) {
            val b = s.pdfBook ?: return@LaunchedEffect
            val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { MyBible.make(ctx, s.store, s.translation, b) }.getOrNull() }
            s.pdfBook = null; f?.let { s.exportJob = ExportJob(listOf(it), "application/pdf", ctx.getString(R.string.export_title_pdf, s.bookName(b))) }
        }
        if (s.planOpen) PlanSheet(s)
        // 내 목소리 한 권: 장마다 절 녹음을 차례로 이어 소리 파일 하나 + 표지 카드
        LaunchedEffect(s.audiobookBook) {
            val b = s.audiobookBook ?: return@LaunchedEffect
            s.exporting = true; s.toast = ctx.getString(R.string.exporting)
            val job = try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching {
                val V = io.github.graviton94.todaybible.data.Voice
                val files = (1..s.store.book(s.translation, b).chapterCount).flatMap { ch -> V.verses(ctx, s.translation.id, b, ch).map { it.second } }
                if (files.isEmpty()) return@runCatching null
                val name = s.bookName(b)
                val audio = java.io.File(ctx.cacheDir, "share/${name.replace(' ', '_')}_${ctx.getString(R.string.audiobook_file)}.m4a")
                if (!V.exportAudio(files, audio)) return@runCatching null
                val secs = (files.sumOf { V.durationMs(it) } / 1000).toInt()
                val title = if (s.ownerName.isNotBlank()) ctx.getString(R.string.audiobook_title_named, s.ownerName, name) else ctx.getString(R.string.audiobook_title, name)
                val dur = ctx.getString(R.string.duration_hm, secs / 3600, secs / 60 % 60)
                val card = Cards.year(ctx, s.korean, title, dur, listOf(ctx.getString(R.string.audiobook_verses, files.size)))
                val img = java.io.File(ctx.cacheDir, "share/${name.replace(' ', '_')}_cover.png").also { f -> f.outputStream().use { card.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
                ExportJob(listOf(audio, img), "*/*", title)
            }.getOrNull() } } finally { s.exporting = false }
            s.audiobookBook = null
            if (job != null) s.exportJob = job else s.toast = ctx.getString(R.string.export_failed)
        }
        LaunchedEffect(s.notesBook) {
            val b = s.notesBook ?: return@LaunchedEffect
            val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { MyBible.notes(ctx, s.store, s.translation, b.takeIf { it >= 0 }) }.getOrNull() }
            s.notesBook = null; f?.let { s.exportJob = ExportJob(listOf(it), "application/pdf", ctx.getString(R.string.export_title_notes)) }
        }
        s.picker?.let { b -> BookSheet({ s.picker = null; s.pickToRead = false }) { ChapterGrid(s, b) { ch -> s.picker = null; if (s.pickToRead) s.read(b, ch) else s.open(b, ch); s.pickToRead = false } } }
        s.award?.takeIf { s.finished == null }?.let { AwardCard(s, it) }
        // 첫 안내: 덮개 (설정 · 장 마침 · 판화 …) 가 없을 때 지금 화면의 것
        val calm = s.onboarded && !s.opening && !s.settingsOpen && s.finished == null && s.award == null && s.plateView == null && s.handBook == null &&
            !s.purchaseOpen && s.picker == null && !s.marksOpen && s.found == null && s.memoryOpen == null && s.sermonOpen == null && !s.prayersOpen && s.prayerOpen == null && s.exportJob == null && !s.exporting && s.shareVerse == null && !s.planOpen && !typing && !pager.isScrollInProgress
        val screen = when (pager.currentPage) {
            AppState.TODAY -> "today"
            AppState.BIBLE -> if (s.listenAt != null) "reader" else "library"
            AppState.COPY -> "copy${s.copyTabNow}"
            else -> "record"
        }
        if (calm) CoachOverlay(s, screen)
        // 여는 순간이 먼저, 처음 설치했으면 그다음에 첫 안내
        if (s.opening) { if (s.firstOfDay) IntroCover(s) { s.opening = false } else IntroDaily(s) { s.opening = false } }
        else if (!s.onboarded) Welcome(s)
        // 낭독 음원을 못 받아 폰 목소리로 읽을 때: 한 번 알려요
        val fell by io.github.graviton94.todaybible.data.Narration.fellBack.collectAsState()
        LaunchedEffect(fell) { if (fell) { s.toast = ctx.getString(R.string.narration_fallback); io.github.graviton94.todaybible.data.Narration.fellBack.value = false } }
        // 토스트: 이름표 위에 잠깐
        s.toast?.let { msg ->
            LaunchedEffect(msg) { kotlinx.coroutines.delay(Tokens.Motion.toastMs.toLong()); s.toast = null }
            Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = Tokens.Size.touch + Tokens.Space.s4, start = Tokens.Space.s5, end = Tokens.Space.s5), contentAlignment = Alignment.BottomCenter) { BookToast(msg) }
        }
    }
}

/** 맨 위 한 줄: 앱 이름 (표제 글꼴) · 오른쪽 톱니. */
@Composable
private fun TopBar(s: AppState) {
    val c = Theme.c
    Row(Modifier.fillMaxWidth().padding(start = Tokens.Space.s5, end = Tokens.Space.s1), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.app_name), style = Theme.brand(s.korean), maxLines = 1, modifier = Modifier.weight(1f))
        val label = stringResource(R.string.settings)
        // 톱니만으로는 알기 어려워서 ‘설정’ 글자도 함께
        Row(Modifier.coach("settings").heightIn(min = Tokens.Size.touch).semantics { contentDescription = label }.clickable(role = Role.Button) { s.settingsOpen = true }
            .padding(horizontal = Tokens.Space.s3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
            Gear(Modifier.size(Tokens.Size.icon))
            Text(label, style = Theme.small().copy(color = c.inkSoft), maxLines = 1)
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
/** 듣는 중 띠: 다른 화면에 있어도 지금 듣는 것 · 누르면 그 화면으로 · 멈추기. 듣는 화면에서는 숨겨요. */
@Composable
private fun NowPlayingBar(s: AppState) {
    val c = Theme.c; val ctx = androidx.compose.ui.platform.LocalContext.current
    val now = io.github.graviton94.todaybible.data.ListenService.now.collectAsState().value ?: return
    val origin = io.github.graviton94.todaybible.data.ListenService.origin.collectAsState().value
    val prayer = origin.removePrefix("prayer:").takeIf { origin.startsWith("prayer:") }?.let { io.github.graviton94.todaybible.core.Prayers.byId(it) }
    if (prayer == null && s.listenAt != null && s.page == AppState.BIBLE) return
    val what = if (prayer != null) prayerName(prayer) else s.chapterRef(now.book, now.chapter)
    Row(Modifier.fillMaxWidth().background(c.leaf).clickable(role = Role.Button) { s.goToListening() }
        .drawBehind { drawLine(c.hair, Offset.Zero, Offset(size.width, 0f), Tokens.Stroke.hair.toPx()) }
        .padding(horizontal = Tokens.Space.s5).heightIn(min = Tokens.Size.touch),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        PlayMark(c.rubric, true, Modifier.size(Tokens.Size.iconSm))
        Text(stringResource(R.string.now_playing, what), style = Theme.label().copy(color = c.ink), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.listen_stop), style = Theme.label().copy(color = c.rubric), maxLines = 1,
            modifier = Modifier.heightIn(min = Tokens.Size.touch).wrapContentHeight().clickable(role = Role.Button) { io.github.graviton94.todaybible.data.ListenService.stop(ctx) })
    }
}

@Composable
private fun PageTabs(current: Int, onSelect: (Int) -> Unit) {
    val c = Theme.c
    val names = listOf(stringResource(R.string.page_today), stringResource(R.string.page_library), stringResource(R.string.page_copy), stringResource(R.string.page_record))
    Row(Modifier.fillMaxWidth().background(c.paper).navigationBarsPadding().coach("tabs").drawBehind {
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
fun Modifier.pageTurn(pager: PagerState, page: Int, breath: () -> Boolean = { false }): Modifier {
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
