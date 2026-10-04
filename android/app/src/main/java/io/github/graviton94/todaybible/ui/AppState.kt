package io.github.graviton94.todaybible.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
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
    companion object {
        /** 아래 이름표 차례: 오늘 · 성경 · 필사 · 기록. */
        const val TODAY = 0; const val BIBLE = 1; const val COPY = 2; const val RECORD = 3
    }

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
    /** 여는 순간: 켤 때마다 (동작 줄이기를 켠 폰은 빼고). 하루 첫 열기면 표지 넘김, 아니면 금박 새김. */
    var firstOfDay = store.openedDay != java.time.LocalDate.now().toEpochDay()
    var opening by mutableStateOf(!store.consumeSkipIntro() && android.provider.Settings.Global.getFloat(store.context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f)
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
    var shareVerse by mutableStateOf<io.github.graviton94.todaybible.core.VerseKey?>(null)
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
    /** 지금 번역의 낭독 목소리 (개역한글 · KJV 따로). */
    var narrator by mutableStateOf(store.narratorFor(store.translation))
    fun chooseNarrator(v: String) { narrator = v; store.setNarrator(translation, v); narration = emptyMap() }
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
                if (narrator != v) return@post   // 그새 목소리를 바꿨으면 이 결과는 버림
                narration = narration + (key to if (ok) 1 else -1)
                if (!ok) io.github.graviton94.todaybible.data.Narration.fellBack.value = true
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
    /** 듣기 화면 (권, 장). */
    /** 성경 탭에서 읽고 있는 (권, 장). null 이면 책 목록. */
    var listenAt by mutableStateOf<Pair<Int, Int>?>(null)
    /** 읽기 화면에서 처음 보여 줄 절 (장절 찾기 · 책갈피). */
    var readVerse by mutableStateOf<Int?>(null)
    /** 장 고르기가 읽기용인지 (성경 탭) 필사용인지. */
    var pickToRead by mutableStateOf(false)
    /** 책갈피 · 형광펜 모아 보기. */
    var marksOpen by mutableStateOf(false)
    /** 낱말로 찾은 결과 (찾은 말, 절들). 찾는 중이면 절 목록이 null. */
    var found by mutableStateOf<Pair<String, List<io.github.graviton94.todaybible.core.Hit>?>?>(null)
    /** 내보내기 창 (저장 · 메일 · 다른 앱). */
    var exportJob by mutableStateOf<ExportJob?>(null)
    var bookmarks by mutableStateOf(store.loadBookmarks())
    /** 필사의 지금 탭 (첫 안내가 어느 화면인지 알게). */
    var copyTabNow by mutableIntStateOf(store.copyTab)
    /** 간단 모드: 오늘 화면에 오늘의 장과 큰 버튼 셋만. */
    var simple by mutableStateOf(store.simple)
    fun flipSimple() { simple = !simple; store.simple = simple }
    /** 첫 안내를 본 화면들. */
    var coachSeen by mutableStateOf(store.coachSeen)
    fun coachDone(screen: String) { coachSeen = coachSeen + screen; store.coachSeen = coachSeen }
    fun coachReset() { coachSeen = emptySet(); store.coachSeen = coachSeen }
    fun isBookmarked(b: Int, ch: Int) = bookmarks.any { it.translation == translation && it.key.book == b && it.key.chapter == ch }
    /** 이 장 책갈피 꽂기 · 빼기 (한 장에 하나, 꽂은 절 기억). */
    fun toggleBookmark(b: Int, ch: Int, v: Int = 1) {
        val had = isBookmarked(b, ch)
        bookmarks = bookmarks.filterNot { it.translation == translation && it.key.book == b && it.key.chapter == ch } +
            (if (had) emptyList() else listOf(io.github.graviton94.todaybible.data.Store.Mark(translation, io.github.graviton94.todaybible.core.VerseKey(b, ch, v), today().toEpochDay())))
        val list = bookmarks; Thread { runCatching { store.saveBookmarks(list) } }.start()
        toast = store.context.getString(if (had) io.github.graviton94.todaybible.R.string.bookmark_off else io.github.graviton94.todaybible.R.string.bookmark_on, "${bookName(b)} $ch")
    }
    /** 성경 탭의 읽기 화면으로. */
    fun read(b: Int, ch: Int, v: Int? = null) {
        if (locked(b)) { peekBook = b; purchaseOpen = true; return }
        listenAt = b to ch; readVerse = v; page = BIBLE
    }
    /** 녹음을 지우고 다시 읽을 절 (권, 장, 절). 채운 절이라도 낭독에서 다시 열어요. */
    var reread by mutableStateOf<Triple<Int, Int, Int>?>(null)
    /** 녹음 파일이 바뀌면 올림 (지우기 · 모두 지우기 뒤에 목록을 다시 읽게). */
    var voiceRev by mutableIntStateOf(0)
    /** 손글씨로 넘겨 보는 권. */
    var handBook by mutableStateOf<Int?>(null)

    /** 마음에 새기는 말씀 (A3): 시험이 아니라 곁에 두고 되뇌는 구절. */
    var memory by mutableStateOf(store.loadMemory())
    var memoryOpen by mutableStateOf<io.github.graviton94.todaybible.core.VerseKey?>(null)
    /** 캡처용: 되뇌기 창을 처음부터 가린 단계로. */
    var memoryStartLevel = 0
    fun isMemory(k: io.github.graviton94.todaybible.core.VerseKey) = memory.any { it.translation == translation && it.key == k }
    fun toggleMemory(k: io.github.graviton94.todaybible.core.VerseKey) {
        memory = if (isMemory(k)) memory.filterNot { it.translation == translation && it.key == k }
        else memory + io.github.graviton94.todaybible.data.Store.Mark(translation, k, today().toEpochDay())
        val list = memory; Thread { runCatching { store.saveMemory(list) } }.start()
    }

    var parallel by mutableStateOf(store.parallel)
    fun flipParallel() { parallel = !parallel; store.parallel = parallel }
    /** 낭독 받아 두기 (I3): 받는 중인 권과 받은 장 수 / 전체. */
    var keeping by mutableStateOf<Triple<Int, Int, Int>?>(null)
    fun keepNarration(b: Int) {
        if (keeping != null) return
        val ctx = store.context
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        if (cm?.isActiveNetworkMetered != false) { toast = ctx.getString(io.github.graviton94.todaybible.R.string.keep_wifi); return }
        val voice = narrator; val n = store.book(translation, b).chapterCount
        keeping = Triple(b, 0, n)
        Thread {
            val ok = io.github.graviton94.todaybible.data.Narration.keepBook(ctx, voice, b, n) { done, all -> android.os.Handler(android.os.Looper.getMainLooper()).post { keeping = Triple(b, done, all) } }
            android.os.Handler(android.os.Looper.getMainLooper()).post { keeping = null; toast = ctx.getString(if (ok) io.github.graviton94.todaybible.R.string.keep_done else io.github.graviton94.todaybible.R.string.keep_partial, bookName(b)) }
        }.start()
    }

    /** 기도문: 펼친 기도 · 목록 시트 · 오늘 드린 것. */
    var prayerOpen by mutableStateOf<String?>(null)
    var prayersOpen by mutableStateOf(false)
    var prayerReminder by mutableIntStateOf(store.prayerReminder)
    fun choosePrayerReminder(m: Int) { prayerReminder = m; store.prayerReminder = m; Thread { io.github.graviton94.todaybible.data.PrayerReminder.schedule(store.context) }.start() }
    private var prayedIds by mutableStateOf(store.prayedOn(java.time.LocalDate.now().toEpochDay()))
    fun prayed(id: String) = id in prayedIds && store.prayedOn(today().toEpochDay()).contains(id)
    fun markPrayed(id: String) { val d = today().toEpochDay(); prayedIds = store.prayedOn(d) + id; store.setPrayed(d, prayedIds) }

    /** 묵상 한 줄 (A4) · 설교 노트 (A2). */
    var notes by mutableStateOf(store.loadNotes())
    private fun saveNotes(list: List<io.github.graviton94.todaybible.data.Store.Note>) { notes = list; Thread { runCatching { store.saveNotes(list) } }.start() }
    fun reflection(b: Int, ch: Int) = notes.firstOrNull { it.kind == io.github.graviton94.todaybible.data.Store.NoteKind.REFLECTION && it.translation == translation && it.book == b && it.chapter == ch }
    fun setReflection(b: Int, ch: Int, text: String) {
        val t = text.trim(); val old = reflection(b, ch)
        if (t == (old?.text ?: "")) return
        val rest = notes.filterNot { it === old }
        saveNotes(if (t.isEmpty()) rest else rest + io.github.graviton94.todaybible.data.Store.Note(io.github.graviton94.todaybible.data.Store.NoteKind.REFLECTION, translation, b, ch, 0, 0, today().toEpochDay(), t))
    }
    fun sermons() = notes.filter { it.kind == io.github.graviton94.todaybible.data.Store.NoteKind.SERMON }.sortedByDescending { it.epochDay }
    fun sermonOn(day: Long) = notes.firstOrNull { it.kind == io.github.graviton94.todaybible.data.Store.NoteKind.SERMON && it.epochDay == day }
    /** 그날의 설교 노트를 새로 쓰거나 바꿈 (하루 하나). 글이 비고 본문도 없으면 지움. */
    fun putSermon(n: io.github.graviton94.todaybible.data.Store.Note) {
        val rest = notes.filterNot { it.kind == io.github.graviton94.todaybible.data.Store.NoteKind.SERMON && it.epochDay == n.epochDay }
        saveNotes(if (n.text.isBlank() && n.from == 0) rest else rest + n)
    }
    var sermonOpen by mutableStateOf<Long?>(null)
    fun passageLabel(n: io.github.graviton94.todaybible.data.Store.Note) = Lang.passage(store.context, n.translation, bookName(n.book), n.chapter, n.from, n.to)

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
    fun checkNight() { night = nightOverride ?: (theme == ThemeChoice.CANDLE) }
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
    fun widgets() {
        val ctx = store.context
        // 목표 계산 (길잡이 책들을 읽음) 과 위젯 그리기는 화면 줄 밖에서
        Thread { runCatching { store.lastGoal = effectiveGoal() }; io.github.graviton94.todaybible.widget.WidgetTick.refreshAll(ctx); io.github.graviton94.todaybible.widget.WidgetTick.schedule(ctx) }.start()
    }

    fun open(b: Int, ch: Int) {
        if (locked(b)) { peekBook = b; purchaseOpen = true; return }
        book = b; chapter = ch; target = null; store.setBookmark(translation, b, ch); page = COPY; widgets()
    }
    fun chooseTranslation(t: Translation) { translation = t; store.translation = t; narrator = store.narratorFor(t); narration = emptyMap(); val bm = store.bookmark(t); book = bm.first; chapter = bm.second; widgets() }
    fun setThemeChoice(t: ThemeChoice) { theme = t; store.theme = t; checkNight(); widgets() }
    /** 언어 (시스템 · ko · en): 화면 글 · 성경 번역 · 낭독 목소리를 한 번에. 화면 글은 다시 그릴 때 (recreate) 바뀌어요. */
    var language by mutableStateOf(store.language)
    /** 캡처용: 설정의 펼친 갈래 · 언어 묻기 창. */
    var debugSection: String? = null
    var debugAskLang: String? = null
    /** 덮인 창들 모두 닫기 (알림 · 위젯으로 들어올 때). */
    fun closeOverlays() {
        settingsOpen = false; purchaseOpen = false; plateView = null; finished = null; picker = null; marksOpen = false; found = null
        memoryOpen = null; sermonOpen = null; prayersOpen = false; prayerOpen = null; shareVerse = null; handBook = null; planOpen = false; award = null; opening = false
    }
    /** 앱을 처음부터 다시 (언어처럼 화면 전체가 바뀌는 설정 뒤에). 여는 순간은 이번만 건너뛰어요. */
    fun restartApp(a: android.app.Activity) {
        store.skipIntroOnce = true; store.flush()
        io.github.graviton94.todaybible.data.ListenService.stop(a)
        // 뒤에서 쓰던 기록 (책갈피 · 노트 …) 이 마저 저장되도록 잠깐 기다린 뒤
        Thread.sleep(400)
        a.startActivity(android.content.Intent(a, io.github.graviton94.todaybible.MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK))
        a.finishAffinity(); Runtime.getRuntime().exit(0)
    }
    fun chooseLanguage(l: String) {
        language = l; store.language = l
        Lang.applyAppLocale(store.context, l)
        val tr = when (l) { "ko" -> Translation.KRV; "en" -> Translation.KJV; else -> if (android.content.res.Resources.getSystem().configuration.locales[0].language == "ko") Translation.KRV else Translation.KJV }
        if (tr != translation) chooseTranslation(tr)
    }
    fun setTextScale(s: Float) { scale = s; store.textScale = s }
    fun setStampMark(s: String) { stamp = s; store.stamp = s; widgets() }
    /** 나의 성경 PDF: 평생권이 필요하면 평생권 화면으로. */
    /** 노트 PDF 를 만들 권 (-1 = 전체). */
    var notesBook by mutableStateOf<Int?>(null)
    /** 내 목소리 한 권 (오디오북) 만들기. */
    var audiobookBook by mutableStateOf<Int?>(null)
    fun requestAudiobook(b: Int) { if (gated()) purchaseOpen = true else audiobookBook = b }
    fun requestNotes(b: Int?) { if (gated()) purchaseOpen = true else notesBook = b ?: -1 }
    fun requestPdf(b: Int) { if (!lifetime.owned && (lifetime.ready || lifetime.forceReady || forceLock)) purchaseOpen = true else pdfBook = b }
    fun setGoal(g: Int) { dailyGoal = g; store.dailyGoal = g; widgets() }
    fun toggleNotebook() { notebook = !notebook; store.notebook = notebook; typeView = if (notebook) 1 else 0; store.typeView = typeView }
    /** 타자 보기: 0 책 · 1 노트 · 2 원고지. */
    var typeView by mutableStateOf(store.typeView)
    fun chooseTypeView(v: Int) { typeView = v; store.typeView = v; notebook = v == 1; store.notebook = notebook }
    fun finishOnboarding(startBook: Int, startChapter: Int) {
        onboarded = true; store.onboarded = true
        book = startBook; chapter = startChapter; target = null; store.setBookmark(translation, startBook, startChapter); page = COPY
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
        planId = id; store.planId = id; store.planStart = today().toEpochDay(); planOpen = false; warmPlan()
        planNext()?.let { (b, c, _) -> book = b; chapter = c; target = null; store.setBookmark(translation, b, c) }
        widgets()
    }
    /** 길잡이 범위의 절 수 · 쓴 절 수. */
    @Volatile private var planMemo: Pair<List<Any?>, Pair<Int, Int>>? = null
    fun planCounts(): Pair<Int, Int> {
        val pl = plan ?: return 0 to 0
        val key = listOf(pl.id, translation, fills.size)
        planMemo?.let { (k, v) -> if (k == key) return v }
        val p = progress; var total = 0; var done = 0
        pl.chapters.forEach { (b, c) -> val t = store.book(translation, b); t.fillable(c).forEach { v -> total++; if (p.isFilled(translation, VerseKey(b, c, v))) done++ } }
        return (total to done).also { planMemo = key to it }
    }
    /** 길잡이 책들을 미리 읽어 둠 (처음 장을 고를 때 멈칫하지 않게). */
    /** 지금 권 · 길잡이 책들을 미리 읽어 둠 (처음 장을 고를 때 멈칫하지 않게). */
    fun warmPlan() {
        val pl = plan; val tr = translation; val b = book
        Thread { runCatching { store.book(tr, b); pl?.books?.forEach { store.book(tr, it) }; if (pl != null) planCounts(); store.plates } }.start()
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
    /** 평생권으로 열리는 권인지 (보여 주기용: 결제를 쓸 수 없는 시험판에서도 표시). */
    fun premium(b: Int) = b !in Canon.free && !lifetime.owned
    /** 평생권 기능 표시 (붓펜 · 연필 · 표지 · PDF · 녹음 …). */
    val premiumOn: Boolean get() = !lifetime.owned
    fun gated() = !lifetime.owned && (lifetime.ready || lifetime.forceReady || forceLock)
    fun toggleVoice() { if (gated()) { purchaseOpen = true; return }; voiceOn = !voiceOn; store.voiceOn = voiceOn }
    fun chooseCover(c: String) { cover = c; store.cover = c }
    fun setOwner(n: String) { ownerName = n; store.ownerName = n; widgets() }
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

    fun setReminder(h: Int) { reminderHour = h; store.reminderHour = h; if (h >= 0) { store.lastReminderHour = h; val today = java.time.LocalDate.now().toEpochDay(); store.reminderDay = if (java.time.LocalTime.now().hour >= h || store.postedDay == today) today else -1 }; io.github.graviton94.todaybible.data.Reminder.schedule(store.context, h) }

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
    /** 그림 이름 · 화가 (번역의 언어로; 이름은 자료에 두 말로 들어 있어요). */
    fun plateName(p: io.github.graviton94.todaybible.data.Plate) = if (korean) p.ko else p.en
    fun plateBy(p: io.github.graviton94.todaybible.data.Plate) = if (korean) p.byKo else p.byEn
    /** 권 · 장 표기 (번역의 언어로, strings.json 의 ref_chapter). */
    fun chapterRef(b: Int = book, ch: Int = chapter) = Lang.chapterRef(store.context, translation, bookName(b), ch)
    /** 음성 인식 언어 (번역을 따라). */
    val speechTag: String get() = Lang.speechTag(store.context, translation)
}
