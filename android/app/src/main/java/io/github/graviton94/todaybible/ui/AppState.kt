package io.github.graviton94.todaybible.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.graviton94.todaybible.core.Canon
import io.github.graviton94.todaybible.core.Fill
import io.github.graviton94.todaybible.core.Milestone
import io.github.graviton94.todaybible.core.Milestones
import io.github.graviton94.todaybible.core.Mode
import io.github.graviton94.todaybible.core.Progress
import io.github.graviton94.todaybible.core.Translation
import io.github.graviton94.todaybible.core.VerseKey
import io.github.graviton94.todaybible.data.Store
import io.github.graviton94.todaybible.design.ThemeChoice
import java.time.LocalDate

/** 화면 상태 한 곳. 기록은 Store 에 덧붙이고, 진행 · 발자취는 기록에서 다시 계산. */
class AppState(val store: Store) {
    val fills = mutableStateListOf<Fill>().apply { addAll(store.loadFills()) }
    var theme by mutableStateOf(store.theme)
    var translation by mutableStateOf(store.translation)
    var scale by mutableStateOf(store.textScale)
    var stamp by mutableStateOf(store.stamp)
    var book by mutableStateOf(store.bookmark(store.translation).first)
    var chapter by mutableStateOf(store.bookmark(store.translation).second)
    /** 0 오늘 · 1 필사 · 2 서재 · 3 기록. */
    var page by mutableStateOf(0)
    var onboarded by mutableStateOf(store.onboarded)
    /** 켤 때 표지 넘김 (처음 소개 뒤로는 매번). */
    var opening by mutableStateOf(store.onboarded && store.openedDay != java.time.LocalDate.now().toEpochDay())
    var dailyGoal by mutableStateOf(store.dailyGoal)
    var notebook by mutableStateOf(store.notebook)
    /** 크게 보는 판화. */
    var plateView by mutableStateOf<io.github.graviton94.todaybible.data.Plate?>(null)
    /** 나의 성경 PDF 를 만들 권. */
    var pdfBook by mutableStateOf<Int?>(null)
    /** 평생권 화면에서 미리 보여 줄 잠긴 권. */
    var peekBook by mutableStateOf<Int?>(null)
    /** 처음 소개의 몇째 장. */
    var welcomeStep by mutableStateOf(0)
    var planId by mutableStateOf(store.planId)
    var planOpen by mutableStateOf(false)
    var cover by mutableStateOf(store.cover)
    var voiceOn by mutableStateOf(store.voiceOn)
    /** 내보내는 중 (소리 · 영상 · PDF). */
    var exporting by mutableStateOf(false)
    var ownerName by mutableStateOf(store.ownerName)
    /** 이 장에서 고른 절 (없으면 다음 빈 절). */
    var target by mutableStateOf<Int?>(null)
    var reminderHour by mutableStateOf(store.reminderHour)
    /** 평생권 (Google Play). */
    val lifetime = io.github.graviton94.todaybible.data.Lifetime(store.context)
    var purchaseOpen by mutableStateOf(false)
    /** 나누기 시트에 띄운 절 (이 장). */
    var shareVerse by mutableStateOf<Int?>(null)
    /** 캡처용: Play 없이도 잠금 보이기. */
    var forceLock = false
    var settingsOpen by mutableStateOf(false)
    /** 방금 마친 장 (덮개 · 조각 화면). */
    var finished by mutableStateOf<Pair<Int, Int>?>(null)
    /** 방금 얻은 발자취 (아래에서 올라오는 카드). */
    var award by mutableStateOf<Milestone?>(null)
    /** 장 고르기 시트를 연 권. */
    var picker by mutableStateOf<Int?>(null)
    /** 짧은 알림 한 줄. */
    var toast by mutableStateOf<String?>(null)
    var earned by mutableStateOf(store.loadEarned())
    /** 형광펜 밑줄 (N). */
    var marks by mutableStateOf(store.loadMarks())
    /** 손글씨 펜 · 밑글씨 · 쓰는 감각 · 밤 필사. */
    var pen by mutableStateOf(store.pen)
    var handGuide by mutableStateOf(store.handGuide)
    var handPhrase by mutableStateOf(store.handPhrase)
    fun flipHandPhrase() { handPhrase = !handPhrase; store.handPhrase = handPhrase }
    var penSound by mutableStateOf(store.penSound)
    var paperHaptic by mutableStateOf(store.paperHaptic)
    var candle by mutableStateOf(store.candle)
    var contrast by mutableStateOf(store.contrast)
    fun flipContrast() { contrast = !contrast; store.contrast = contrast }
    var aloudMode by mutableStateOf(store.aloudMode)
    var aloudBig by mutableStateOf(store.aloudBig)
    /** 날마다 소리 내어 읽은 시간 (초). */
    var aloudLog by mutableStateOf(store.loadAloud())
    /** 아침 알림의 ‘함께 읽기’로 열었을 때: 낭독을 곧바로 시작. */
    var aloudNow by mutableStateOf(false)
    fun chooseAloudMode(i: Int) { aloudMode = i; store.aloudMode = i }
    fun flipAloudBig() { aloudBig = !aloudBig; store.aloudBig = aloudBig }
    fun addAloud(secs: Int) { val d = today().toEpochDay(); aloudLog = aloudLog + (d to (aloudLog[d] ?: 0) + secs); store.saveAloud(aloudLog) }
    var aloudSpeed by mutableStateOf(store.aloudSpeed)
    var guideVoice by mutableStateOf(store.guideVoice)
    var narrator by mutableStateOf(store.narrator)
    fun chooseNarrator(v: String) { narrator = v; store.narrator = v; narration = emptyMap() }
    /** 장 낭독 음원: "권:장" → 0 받는 중 · 1 있음 · -1 못 받음. */
    var narration by mutableStateOf<Map<String, Int>>(emptyMap())
    fun narrationState(book: Int, chapter: Int): Int? = narration["$book:$chapter"]
    /** 이 장 음원을 받아 둠 (있으면 바로 1). 다 받으면 다음 장도 조용히 미리. */
    fun fetchNarration(book: Int, chapter: Int, prefetchNext: Boolean = true) {
        val key = "$book:$chapter"; val v = narrator; val ctx = store.context
        if (narration[key] == 0 || narration[key] == 1) return
        narration = narration + (key to 0)
        val ui = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            val ok = io.github.graviton94.todaybible.data.Narration.fetch(ctx, v, book, chapter)
            ui.post {
                narration = narration + (key to if (ok) 1 else -1)
                if (ok && prefetchNext && chapter < io.github.graviton94.todaybible.core.Canon.books[book].chapters) fetchNarration(book, chapter + 1, prefetchNext = false)
            }
        }.apply { name = "narration" }.start()
    }
    fun chooseAloudSpeed(i: Int) { aloudSpeed = i; store.aloudSpeed = i }
    fun chooseGuideVoice(n: String) { guideVoice = n; store.guideVoice = n }
    /** 낭독 빠르기 (1 = 보통). */
    fun aloudRate(): Float = when (aloudSpeed) { 0 -> io.github.graviton94.todaybible.design.Tokens.Motion.aloudSlow; 2 -> io.github.graviton94.todaybible.design.Tokens.Motion.aloudFast; else -> io.github.graviton94.todaybible.design.Tokens.Motion.aloudNormal }
    /** 지금 촛불빛인지 (앱으로 돌아올 때마다 다시 봄). */
    var night by mutableStateOf(false)
    /** 손글씨로 넘겨 보는 권. */
    var handBook by mutableStateOf<Int?>(null)

    fun isMarked(k: io.github.graviton94.todaybible.core.VerseKey) = marks.any { it.translation == translation && it.key == k }
    fun toggleMark(k: io.github.graviton94.todaybible.core.VerseKey) {
        marks = if (isMarked(k)) marks.filterNot { it.translation == translation && it.key == k }
        else marks + io.github.graviton94.todaybible.data.Store.Mark(translation, k, today().toEpochDay())
        store.saveMarks(marks)
    }
    fun choosePen(p: Int) { if (p != io.github.graviton94.todaybible.data.Ink.FOUNTAIN && gated()) { purchaseOpen = true; return }; pen = p; store.pen = p }
    fun flipGuide() { handGuide = !handGuide; store.handGuide = handGuide }
    fun flipPenSound() { penSound = !penSound; store.penSound = penSound }
    fun flipPaperHaptic() { paperHaptic = !paperHaptic; store.paperHaptic = paperHaptic }
    fun flipCandle() { candle = !candle; store.candle = candle; checkNight() }
    /** 캡처용: 올해의 필사 카드를 언제든. */
    var forceYear = false
    /** 캡처용: 밤 필사 고정. */
    var nightOverride: Boolean? = null
    fun checkNight() { val h = java.time.LocalTime.now().hour; night = nightOverride ?: (candle && (h >= 21 || h < 5)) }
    /** 캡처용 고정 날짜 (개발자 도구). */
    var fixedToday: LocalDate? = null

    val korean: Boolean get() = translation == Translation.KRV
    // 기록은 덧붙이기만 하므로 개수가 같으면 다시 셀 필요 없음 (화면마다 수백 번 부름)
    private var cachedProgress: Progress? = null
    private var cachedSize = -1
    val progress: Progress get() {
        val n = fills.size
        cachedProgress?.takeIf { cachedSize == n }?.let { return it }
        return Progress(fills.toList()).also { cachedProgress = it; cachedSize = n }
    }
    fun today(): LocalDate = fixedToday ?: LocalDate.now()
    fun text(b: Int = book) = store.book(translation, b)
    /** 무료 네 권 밖은 평생권이 있어야 열림. Play 에 닿지 않는 곳(직접 설치 등)에서는 잠그지 않음. */
    fun locked(b: Int) = b !in Canon.free && !lifetime.owned && (lifetime.ready || lifetime.forceReady || forceLock)
    private fun widgets() {
        val ctx = store.context; runCatching { store.lastGoal = effectiveGoal() }
        Thread { runCatching { io.github.graviton94.todaybible.widget.VerseWidget.refresh(ctx) }; runCatching { io.github.graviton94.todaybible.widget.SmallWidget.refreshAll(ctx) }; runCatching { io.github.graviton94.todaybible.widget.HandWidget.refresh(ctx) } }.start()
    }

    fun open(b: Int, ch: Int) {
        if (locked(b)) { peekBook = b; purchaseOpen = true; return }
        book = b; chapter = ch; target = null; store.setBookmark(translation, b, ch); page = 1; widgets()
    }
    fun chooseTranslation(t: Translation) { translation = t; store.translation = t; val bm = store.bookmark(t); book = bm.first; chapter = bm.second }
    fun setThemeChoice(t: ThemeChoice) { theme = t; store.theme = t }
    fun setTextScale(s: Float) { scale = s; store.textScale = s }
    fun setStampMark(s: String) { stamp = s; store.stamp = s }
    /** 나의 성경 PDF: 평생권이 필요하면 평생권 화면으로. */
    /** 노트 PDF 를 만들 권 (-1 = 전체). */
    var notesBook by mutableStateOf<Int?>(null)
    fun requestNotes(b: Int?) { if (gated()) purchaseOpen = true else notesBook = b ?: -1 }
    fun requestPdf(b: Int) { if (!lifetime.owned && (lifetime.ready || lifetime.forceReady || forceLock)) purchaseOpen = true else pdfBook = b }
    fun setGoal(g: Int) { dailyGoal = g; store.dailyGoal = g }
    fun toggleNotebook() { notebook = !notebook; store.notebook = notebook; typeView = if (notebook) 1 else 0; store.typeView = typeView }
    /** 타자 보기: 0 책 · 1 노트 · 2 원고지. */
    var typeView by mutableStateOf(store.typeView)
    fun chooseTypeView(v: Int) { typeView = v; store.typeView = v; notebook = v == 1; store.notebook = notebook }
    fun finishOnboarding(startBook: Int, startChapter: Int) {
        onboarded = true; store.onboarded = true
        book = startBook; chapter = startChapter; target = null; store.setBookmark(translation, startBook, startChapter); page = 1
    }

    /** 오늘 쓴 절 (이 번역). */
    fun todayVerses(): Int { val d = today().toEpochDay(); return fills.count { it.translation == translation && it.epochDay == d } }
    /** 오늘 마친 장 수: 오늘 쓴 절이 있는 장 가운데 다 찬 장. */
    fun todayChapters(): Int {
        val d = today().toEpochDay(); val p = progress
        return fills.filter { it.translation == translation && it.epochDay == d }.map { it.key.book to it.key.chapter }.toSet()
            .count { (b, c) -> p.chapterDone(translation, store.book(translation, b), c) }
    }
    fun goalMet() = io.github.graviton94.todaybible.core.Goal.met(effectiveGoal(), todayVerses(), todayChapters())

    // ── 길잡이 ──
    val plan: io.github.graviton94.todaybible.core.Plan? get() = io.github.graviton94.todaybible.core.Plans.byId(planId)
    fun choosePlan(id: String?) {
        val p = io.github.graviton94.todaybible.core.Plans.byId(id)
        if (p != null && p.books.any { locked(it) }) { peekBook = p.books.first { locked(it) }; purchaseOpen = true; return }
        planId = id; store.planId = id; store.planStart = today().toEpochDay(); planOpen = false
        planNext()?.let { (b, c, _) -> book = b; chapter = c; target = null; store.setBookmark(translation, b, c) }
    }
    /** 길잡이 범위의 절 수 · 쓴 절 수. */
    fun planCounts(): Pair<Int, Int> {
        val pl = plan ?: return 0 to 0; val p = progress; var total = 0; var done = 0
        pl.chapters.forEach { (b, c) -> val t = store.book(translation, b); t.fillable(c).forEach { v -> total++; if (p.isFilled(translation, VerseKey(b, c, v))) done++ } }
        return total to done
    }
    /** 길잡이에서 다음에 쓸 (권, 장, 절). */
    fun planNext(): Triple<Int, Int, Int>? {
        val pl = plan ?: return null; val p = progress
        for ((b, c) in pl.chapters) { val t = store.book(translation, b); p.nextVerse(translation, t, c)?.let { return Triple(b, c, it) } }
        return null
    }
    fun planDay(): Int = io.github.graviton94.todaybible.core.Plans.day(store.planStart, today())
    /** 오늘의 분량: 길잡이가 있으면 남은 절 ÷ 남은 날 (오늘 쓴 절은 오늘 몫에 포함). */
    fun effectiveGoal(): Int {
        val pl = plan ?: return dailyGoal
        val (total, done) = planCounts(); val doneBefore = done - todayVerses()
        val left = (pl.days - planDay() + 1).coerceAtLeast(1)
        return io.github.graviton94.todaybible.core.Plans.perDay(total, doneBefore, left).coerceAtLeast(1)
    }
    /** 평생권이 있어야 하는데 없는 상태 (Play 에 닿을 때만 잠금). */
    fun gated() = !lifetime.owned && (lifetime.ready || lifetime.forceReady || forceLock)
    fun toggleVoice() { if (gated()) { purchaseOpen = true; return }; voiceOn = !voiceOn; store.voiceOn = voiceOn }
    fun chooseCover(c: String) { cover = c; store.cover = c }
    fun setOwner(n: String) { ownerName = n; store.ownerName = n }
    fun coverColor() = when (cover) { "navy" -> io.github.graviton94.todaybible.design.Tokens.Covers.navy; "olive" -> io.github.graviton94.todaybible.design.Tokens.Covers.olive; "ebony" -> io.github.graviton94.todaybible.design.Tokens.Covers.ebony; else -> io.github.graviton94.todaybible.design.Tokens.Covers.burgundy }

    /** 다음 판화: 지금 권에서 이 장 뒤로 가장 가까운 것, 없으면 가장 많이 쓴 (아직 다 안 찬) 것. 남은 절 수와 함께. */
    fun nextPlate(): Pair<io.github.graviton94.todaybible.data.Plate, Int>? {
        val p = progress
        // 시작한 권만 본문을 읽음 (나머지는 아직 한 절도 안 썼으니 남은 절 = 장 전체)
        val started = startedBooks()
        fun left(pl: io.github.graviton94.todaybible.data.Plate): Int { val t = store.book(translation, pl.book); return t.fillable(pl.chapter).count { !p.isFilled(translation, VerseKey(pl.book, pl.chapter, it)) } }
        val open = store.plates.filter { !locked(it.book) && (it.book !in started || left(it) > 0) }
        val here = open.filter { it.book == book && it.chapter >= chapter }.minByOrNull { it.chapter }
        val pick = here ?: open.filter { it.book in started }.maxByOrNull { plateFraction(it) } ?: open.firstOrNull() ?: return null
        return pick to left(pick)
    }
    fun startedBooks(): Set<Int> = progress.filled(translation).map { VerseKey(it).book }.toSet()
    /** 판화 장의 완성도 (시작 안 한 권은 0, 본문을 읽지 않음). */
    fun plateFraction(pl: io.github.graviton94.todaybible.data.Plate, started: Set<Int> = startedBooks()): Float =
        if (pl.book !in started) 0f else progress.chapterFraction(translation, store.book(translation, pl.book), pl.chapter)

    fun setReminder(h: Int) { reminderHour = h; store.reminderHour = h; io.github.graviton94.todaybible.data.Reminder.schedule(store.context, h) }

    /** 절(들)을 채움. 장을 다 채우면 finished, 새 발자취가 생기면 award. */
    fun fill(verses: List<Int>, mode: Mode) {
        val day = today().toEpochDay(); val now = System.currentTimeMillis()
        val tr = translation; val already = progress.filled(tr)
        val new = verses.map { VerseKey(book, chapter, it) }.filter { it.raw !in already }.map { Fill(tr, it, mode, day, now) }
        if (new.isEmpty()) return
        if (store.startDay < 0) store.startDay = day
        store.append(new); fills.addAll(new); widgets()
        val t = text()
        val wasMet = io.github.graviton94.todaybible.core.Goal.met(effectiveGoal(), todayVerses() - new.size, 0)
        if (progress.chapterDone(tr, t, chapter)) finished = book to chapter
        else if (!wasMet && goalMet()) toast = store.context.getString(io.github.graviton94.todaybible.R.string.goal_done)
        else if (mode != Mode.TYPE) toast = store.context.getString(io.github.graviton94.todaybible.R.string.filled_n, new.size)
        checkMilestones()
    }

    private fun checkMilestones() {
        val p = progress
        val doneChapters = HashSet<Int>(); val doneBooks = HashSet<Int>()
        // 기록이 있는 권만 살펴봄 (전체 66권을 매번 열지 않게)
        p.filled(translation).map { VerseKey(it).book }.toSet().forEach { b ->
            val t = store.book(translation, b)
            (1..t.chapterCount).forEach { c -> if (p.chapterDone(translation, t, c)) doneChapters += (b + 1) * 1000 + c }
            if (p.bookDone(translation, t)) doneBooks += b
        }
        val got = Milestones.earned(Milestones.State(fills.filter { it.translation == translation }, doneChapters, doneBooks, korea = korean))
        val fresh = got.keys - earned.keys
        if (fresh.isNotEmpty()) {
            earned = earned + fresh.associateWith { got[it] ?: today().toEpochDay() }
            store.saveEarned(earned)
            award = fresh.first()
        }
    }

    /** 캡처용 시험 기록: 지난 40여 일 동안 창세기 1–8장 · 시편 23편 · 마가복음 1–2장 등을 나눠 씀. */
    fun seedDemo() {
        val tr = translation; val end = today()
        val plan = listOf(0 to (1..8).map { it to 1f }, 18 to listOf(23 to 1f), 40 to listOf(1 to 1f, 2 to 1f, 3 to 0.35f),
            1 to listOf(14 to 0.42f), 41 to listOf(15 to 0.75f), 39 to listOf(5 to 0.17f))
        val keys = plan.flatMap { (b, chs) -> val t = store.book(tr, b); chs.flatMap { (ch, f) -> val v = t.fillable(ch); v.take((v.size * f).toInt()).map { VerseKey(b, ch, it) } } }
        // 쓴 날: 41일 전부터, 주일 몇 번 · 평일 사흘은 쉼
        val days = (40 downTo 0).map { end.minusDays(it.toLong()) }.filterIndexed { i, d -> !(d.dayOfWeek == java.time.DayOfWeek.SUNDAY && i % 3 != 0) && i !in setOf(5, 17, 26) }
        val modes = listOf(Mode.TYPE, Mode.TYPE, Mode.ALOUD, Mode.PAPER)
        // 날마다 고르게 나눔 (마지막 날 = 오늘까지)
        val already = progress.filled(tr)
        val new = keys.mapIndexed { n, key ->
            val i = (n.toLong() * days.size / keys.size).toInt(); val d = days[i]
            val at = d.atTime(6 + i % 3, 30).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            Fill(tr, key, modes[i % modes.size], d.toEpochDay(), at)
        }.filter { it.key.raw !in already }
        if (new.isEmpty()) return
        if (store.startDay < 0) store.startDay = days.first().toEpochDay()
        store.append(new); fills.addAll(new)
        checkMilestones(); award = null
        // 소리 내어 읽은 시간: 지난 이레
        aloudLog = (0..6).associate { end.minusDays(it.toLong()).toEpochDay() to listOf(720, 0, 1260, 540, 960, 300, 840)[it] }.filterValues { it > 0 }; store.saveAloud(aloudLog)
        onboarded = true; store.onboarded = true; opening = false
        open(40, 3); page = 0
    }

    fun nextChapter(b: Int = book, ch: Int = chapter): Pair<Int, Int> {
        val t = text(b)
        return if (ch < t.chapterCount) b to ch + 1 else if (b < 65) b + 1 to 1 else b to ch
    }
    fun bookName(b: Int = book) = if (korean) Canon.books[b].ko else Canon.books[b].en
}
