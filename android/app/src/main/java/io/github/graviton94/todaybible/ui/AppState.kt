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
        /** 매일 알림 시각 휠을 띄울 때 prayerBell 에 넣는 값. */
        const val MORNING_BELL = "_daily"
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
    /** 필사 완성 증서를 만들 것: 권 (0..65) 또는 Certificate.OT · NT · ALL. */
    var certFor by mutableStateOf<Int?>(null)
    /** 필사본 증서의 방식 (null = 방식 상관없이). */
    var certMode: Mode? = null
    /** 평생권 화면에서 미리 보여 줄 잠긴 권. */
    var peekBook by mutableStateOf<Int?>(null)
    /** 결제 감사 카드: true = 평생권 · false = 구독 (첫 결제 때만, 복원 · 갱신은 한 줄). 잠긴 권을 열려다 샀으면 그 권. */
    var thanks by mutableStateOf<Boolean?>(null)
    var thanksBook by mutableStateOf<Int?>(null)
    /** 처음 소개의 몇째 장. */
    var welcomeStep by mutableStateOf(0)
    var planId by mutableStateOf(store.planId)
    var planOpen by mutableStateOf(false)
    /** 장을 열 때 묻는 창: 쓰던 방식 (탭 번호) · 방식마다 쓴 절 수 · 다음 절 · 장의 절. 탭 순서 (낭독 · 타자 · 손글씨). */
    data class ModeAsk(val last: Int, val counts: List<Int>, val next: List<Int?>, val total: Int)
    /** 방식을 정해 장을 열 때 (오늘의 이어 쓰기 · 순례길 이어 쓰기): 묻지 않고 그 필사본으로. */
    var openDirect: Mode? = null
    var modeAsk by mutableStateOf<ModeAsk?>(null)
    /** 이미 물어본 장 (번역:권:장). */
    var modeAsked: String? = null
    /** 마침 카드 (작정을 다 쓴 날, "id:시작날"). */
    var votumDone by mutableStateOf<String?>(null)
    // 꾸미기 (1.2)
    var coverPlate by mutableStateOf(store.coverPlate)
    fun chooseCoverPlate(id: String) { coverPlate = id; store.coverPlate = id; widgets() }
    var dedication by mutableStateOf(store.dedication)
    fun setDedicationLine(t: String) { dedication = t; store.dedication = t }
    var ink by mutableStateOf(store.ink)
    fun chooseInk(i: String) { ink = i; store.ink = i }
    var latinHeads by mutableStateOf(store.latinHeads)
    fun chooseLatinHeads(on: Boolean) { latinHeads = on; store.latinHeads = on; widgets() }
    var appIcon by mutableStateOf(store.appIcon)
    /** 앱 아이콘 바꾸기: 고른 별칭을 먼저 켜고 나머지를 꺼요 (홈 화면 아이콘이 잠깐 사라졌다 다시 생길 수 있어요). */
    fun chooseAppIcon(id: String) {
        val ctx = store.context; val pm = ctx.packageManager; val pkg = io.github.graviton94.todaybible.MainActivity::class.java.`package`!!.name
        val names = mapOf("light" to "IconLight", "tablets" to "IconTablets", "golgotha" to "IconGolgotha", "ascension" to "IconAscension")
        val on = names[id] ?: return
        runCatching {
            pm.setComponentEnabledSetting(android.content.ComponentName(ctx, "$pkg.$on"), android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED, android.content.pm.PackageManager.DONT_KILL_APP)
            names.values.filter { it != on }.forEach { pm.setComponentEnabledSetting(android.content.ComponentName(ctx, "$pkg.$it"), android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED, android.content.pm.PackageManager.DONT_KILL_APP) }
            appIcon = id; store.appIcon = id
        }
    }
    /** 화첩에 건 판화 (그 장을 다 쓴 것). */
    fun hungPlates(): List<io.github.graviton94.todaybible.data.Plate> { val st = startedBooks(); return store.plates.filter { plateFraction(it, st) >= 1f } }
    /** 표지 판화: 고른 것 → 마지막에 건 것 → 날마다 바뀌는 한 점. */
    fun coverPlateId(): String? = coverPlate.takeIf { id -> id.isNotEmpty() && store.plates.any { it.id == id } } ?: dayPlate()?.id
    /** 나누기 카드의 판화: 그 장의 그림, 없으면 표지 판화. */
    fun cardPlate(b: Int, ch: Int): String? = store.plateFor(b, ch)?.id ?: coverPlateId()
    /** 머리글: 라틴 (MARCUS · III) 또는 한글 · 영어 (마가복음 · 3). */
    fun head(b: Int, ch: Int): String = if (latinHeads) io.github.graviton94.todaybible.core.Latin.head(b, ch) else "${bookName(b)} · $ch"
    var voiceOn by mutableStateOf(store.voiceOn)
    /** 내보내는 중 (소리 · 영상 · PDF). */
    var exporting by mutableStateOf(false)
    var ownerName by mutableStateOf(store.ownerName)
    /** 이 장에서 고른 절 (없으면 다음 빈 절). */
    var target by mutableStateOf<Int?>(null)
    var reminderHour by mutableStateOf(store.reminderHour)
    var reminderMinute by mutableStateOf(store.reminderMinute)
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
    /** 기록 내보내기 창 · 가져오기 확인 (풀어 둔 파일) · 보관 한 줄을 다시 셀 때. */
    var archiveOpen by mutableStateOf(false)
    var importPreview by mutableStateOf<io.github.graviton94.todaybible.data.Backup.Preview?>(null)
    var keepRev by mutableIntStateOf(0)
    enum class KeepNudge { DUE, STOPPED }
    /**
     * 오늘 화면 보관 한 줄 (종류, 지난 보관 뒤 다 쓴 장). 드라이브 자동 보관이 잘 되고 있으면 없음.
     * 마지막 보관 뒤 30일 · 그 사이 7장 이상, 한 번도 안 했으면 처음 장을 마친 다음 날부터. 「나중에」는 14일.
     */
    fun keepNudge(): Pair<KeepNudge, Int>? {
        keepRev; fills.size
        val st = store; val day = today().toEpochDay(); val now = System.currentTimeMillis(); val dayMs = 86_400_000L
        val auto = st.backupUri.isNotEmpty()
        if (auto && st.backupFails >= io.github.graviton94.todaybible.design.Tokens.Motion.keepFails) return KeepNudge.STOPPED to 0
        if (day < st.keepNudgeUntil) return null
        if (auto && now - st.backupAt < io.github.graviton94.todaybible.design.Tokens.Motion.keepAutoDays * dayMs) return null
        val since = (st.doneChaptersNow - st.keptChapters).coerceAtLeast(0)
        if (st.backupAt == 0L) return if (st.firstChapterDay in 0 until day) KeepNudge.DUE to since else null
        return if (now - st.backupAt >= io.github.graviton94.todaybible.design.Tokens.Motion.keepDays * dayMs && since >= io.github.graviton94.todaybible.design.Tokens.Motion.keepChapters) KeepNudge.DUE to since else null
    }
    fun keepLater() { store.keepNudgeUntil = today().toEpochDay() + io.github.graviton94.todaybible.design.Tokens.Motion.keepLaterDays; keepRev++ }
    var bookmarks by mutableStateOf(store.loadBookmarks())
    /** 필사의 지금 탭 (첫 안내가 어느 화면인지 알게). */
    var copyTabNow by mutableIntStateOf(store.copyTab)
    /** 필사 집중 모드 (머리 · 탭 · 아래 이름표를 걷음). */
    var focusMode by mutableStateOf(false)
    /** 1.4 기록: 다 쓴 권 화면 · 권 다 쓰기 의식 · 66권 책장 · 묵상 노트 한 줄 · 기록 갈래 (캡처 · 알림에서 바로). */
    var liber by mutableStateOf<Int?>(null)
    var ritual by mutableStateOf<Int?>(null)
    var pendingRitual: Int? = null
    var debugRitualStep = 0
    var shelfOpen by mutableStateOf(false)
    var noteOpen by mutableStateOf<io.github.graviton94.todaybible.data.Store.Note?>(null)
    var recordTab by mutableStateOf<Int?>(null)
    /** 나의 순례길에서 누른 장 (이어 쓰기 · 새로 쓰기 · 빼기 시트). */
    var pilgrim by mutableStateOf<Paused?>(null)
    /** 캡처용: 오늘 화면을 열면 순례길 첫 줄 시트를 바로. */
    var debugPilgrim by mutableStateOf(false)
    var debugPilgrimAsk = 0
    /** 간단 모드: 오늘 화면에 오늘의 장과 큰 버튼 셋만. */
    var simple by mutableStateOf(store.simple)
    fun flipSimple() { simple = !simple; store.simple = simple }
    /** 첫 안내를 본 화면들. */
    var coachSeen by mutableStateOf(store.coachSeen)
    fun coachDone(screen: String) { coachSeen = coachSeen + screen; store.coachSeen = coachSeen }
    fun coachReset() { coachSeen = emptySet(); store.coachSeen = coachSeen }
    /** (?) : 이 화면 안내만 다시. */
    fun coachAgain(screen: String) { coachShowing = null; coachSeen = coachSeen - screen; store.coachSeen = coachSeen }
    /** 처음 소개 다시 보기 (이름 · 분량 · 알림은 묻지 않고 소개 두 장만). */
    var tour by mutableStateOf(false)
    /** 판화 이야기 속 링크로 띄운 그림 (판화 페이지 위에 잠깐). */
    var platePeek by mutableStateOf<io.github.graviton94.todaybible.data.Plate?>(null)
    /** 관련 구절 · 사전 (1.6): 시트를 연 절 · 사전 풀이 한 장 (id) · 작게 띄운 장절. */
    var related by mutableStateOf<io.github.graviton94.todaybible.core.VerseKey?>(null)
    var dictEntry by mutableStateOf<String?>(null)
    var versePeek by mutableStateOf<io.github.graviton94.todaybible.data.Refs.Span?>(null)
    /** 시트를 열 때 처음 보일 칸 (캡처용, 보통 0 = 관련 구절). */
    var relatedTab = 0
    /** 읽기 화면을 열 때 골라 둘 절 (캡처용). */
    var pickOnOpen: Int? = null
    /** 필사 화면을 열 때 ⋯ 메뉴를 펴 둠 (캡처용). */
    var copyMoreOnOpen = false
    /** 화첩 (어두운 갤러리 화면). */
    var galleryOpen by mutableStateOf(false)
    /** 장 여는 화면 (판화가 있는 장을 처음 펼칠 때 한 번). */
    var opener by mutableStateOf<Pair<Int, Int>?>(null)
    /** 새 버전 알아보기 (화면이 만들어질 때 붙여요). */
    var updates: io.github.graviton94.todaybible.data.Updates? = null
    /** 이번에 켠 동안은 새 버전 줄을 닫아 둠. */
    var updateLater by mutableStateOf(false)
    /** ‘새로 바뀐 것’ 화면 (업데이트 뒤 처음 한 번 · 설정에서 다시). */
    var news by mutableStateOf(false)
    /** 낭독 받기 관문: 낭독 탭으로 장을 펼칠 때 음원이 없으면 받는 동안 보여 줘요 (권, 장). */
    var narrGate by mutableStateOf<Pair<Int, Int>?>(null)
    /** 필사 탭을 바꿔 달라는 요청 (관문의 ‘교독 없이 필사만’). 필사 화면이 받아서 지워요. */
    var forceTab by mutableStateOf<Int?>(null)
    /** 파일 만들기 관문 (PDF · 손글씨 · 내 목소리): 만드는 중 → 다 되면 눌러서 저장 · 보내기. */
    var exportGate by mutableStateOf<ExportGate?>(null)
    /** 만들기가 끝났을 때: 관문에 ‘다 됨 · 못 만듦’ (관문을 닫았으면 아무것도). */
    /** 만드는 중 진행 (같은 관문일 때만). */
    fun gateProgress(title: String, f: Float) { val g = exportGate; if (g != null && g.title == title && g.state == 0) exportGate = g.copy(frac = f) }
    fun gateDone(title: String, job: ExportJob?) {
        val g = exportGate
        if (g == null || g.title != title) return   // 관문을 닫았으면 그만둔 것
        exportGate = g.copy(state = if (job != null) 1 else -1, job = job)
    }
    fun openerDone() { opener?.let { (b, c) -> store.openerSeen = store.openerSeen + "$b:$c" }; opener = null }
    /** 지금 펼친 첫 안내 (보여 주는 순간 본 것으로 적어 두고, 다 볼 때까지 이 이름으로 이어서). */
    var coachShowing by mutableStateOf<String?>(null)
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
        readFrom = if (page != BIBLE) page else null
        listenAt = b to ch; readVerse = v; page = BIBLE
    }
    /** 읽기 화면을 연 탭 (오늘 › 읽기 …). 뒤로 가기는 서재가 아니라 그 탭으로. 서재에서 열었으면 null. */
    var readFrom: Int? = null
    /** 읽기 화면 닫기 (뒤로 가기): 다른 탭에서 열었으면 그 탭으로 돌아가요. */
    fun closeReader() { listenAt = null; readFrom?.let { page = it }; readFrom = null }
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
    /** 외운 절 (마음에 새기기의 "외웠어요"): 지금 번역의 절 키 → 날. */
    var known by mutableStateOf(store.memoKnown)
    fun knownDays(): List<Long> = known.mapNotNull { it.split(':').takeIf { p -> p.size == 3 && p[0] == translation.id }?.get(2)?.toLongOrNull() }
    fun isKnown(k: io.github.graviton94.todaybible.core.VerseKey) = known.any { it.startsWith("${translation.id}:${k.raw}:") }
    fun toggleKnown(k: io.github.graviton94.todaybible.core.VerseKey) {
        known = if (isKnown(k)) known.filterNot { it.startsWith("${translation.id}:${k.raw}:") }.toSet() else known + "${translation.id}:${k.raw}:${today().toEpochDay()}"
        store.memoKnown = known
        checkMilestones()
    }
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
    /** 낭독 미리 받기 화면 · 받을 차례를 기다리는 권들 · 와이파이에서만. */
    var keepOpen by mutableStateOf(false)
    var keepQueue by mutableStateOf<List<Int>>(emptyList())
    var keepWifiOnly by mutableStateOf(store.keepWifiOnly)
    fun flipKeepWifiOnly() { keepWifiOnly = !keepWifiOnly; store.keepWifiOnly = keepWifiOnly }
    /** 받아 둘 권을 줄에 세워요 (잠긴 권은 평생권 화면으로). 받기는 KeepService 가 맡아 앱을 닫아도 이어가요. */
    fun keepNarration(b: Int) {
        if (locked(b)) { peekBook = b; purchaseOpen = true; return }
        if (keeping?.first == b || b in keepQueue) return
        val ctx = store.context
        val cm = ctx.getSystemService(android.net.ConnectivityManager::class.java)
        if (keepWifiOnly && cm?.isActiveNetworkMetered != false) { toast = ctx.getString(io.github.graviton94.todaybible.R.string.keep_wifi); return }
        io.github.graviton94.todaybible.data.KeepService.add(ctx, narrator, b)
    }
    fun cancelKeep(b: Int) { io.github.graviton94.todaybible.data.KeepService.cancel(b) }

    /** 기도문: 펼친 기도 · 목록 시트 · 오늘 드린 것. */
    var prayerOpen by mutableStateOf<String?>(null)
    var prayersOpen by mutableStateOf(false)
    /** 기도문마다 알림 시각 (아이디 → 하루 중 분) · 종을 누른 기도문 (시각 고르는 창). */
    var prayerTimes by mutableStateOf(store.prayerTimes)
    var prayerBell by mutableStateOf<String?>(null)   // 기도문 id, 또는 MORNING_BELL (매일 알림 시각)
    /** 정확한 알람 허락 (폰 설정에서 바뀌면 돌아올 때 다시 봐요). */
    var exactAlarm by mutableStateOf(io.github.graviton94.todaybible.data.ExactAlarm.allowed(store.context))
    fun checkExactAlarm() {
        val now = io.github.graviton94.todaybible.data.ExactAlarm.allowed(store.context)
        if (now != exactAlarm) { exactAlarm = now; io.github.graviton94.todaybible.data.Reminder.schedule(store.context, reminderHour); Thread { io.github.graviton94.todaybible.data.PrayerReminder.schedule(store.context) }.start() }
    }
    fun setPrayerTime(id: String, minutes: Int?) {
        prayerTimes = if (minutes == null) prayerTimes - id else prayerTimes + (id to minutes)
        store.prayerTimes = prayerTimes
        Thread { io.github.graviton94.todaybible.data.PrayerReminder.schedule(store.context); runCatching { io.github.graviton94.todaybible.widget.SmallWidget.refreshAll(store.context) } }.start()
    }
    private var prayedIds by mutableStateOf(store.prayedOn(java.time.LocalDate.now().toEpochDay()))
    fun prayed(id: String) = id in prayedIds && store.prayedOn(today().toEpochDay()).contains(id)
    fun markPrayed(id: String) { val d = today().toEpochDay(); prayedIds = store.prayedOn(d) + id; store.setPrayed(d, prayedIds) }

    /** 묵상 한 줄 (A4) · 설교 노트 (A2). */
    var notes by mutableStateOf(store.loadNotes())
    // 노트 저장은 한 줄로 차례대로 (자동 저장이 잦아도 앞뒤가 바뀌거나 임시 파일이 겹치지 않게)
    private val noteWriter = java.util.concurrent.Executors.newSingleThreadExecutor()
    private fun saveNotes(list: List<io.github.graviton94.todaybible.data.Store.Note>) { notes = list; noteWriter.execute { runCatching { store.saveNotes(list) } } }
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
    fun passageLabel(n: io.github.graviton94.todaybible.data.Store.Note) = Lang.passage(store.context, uiTr, bookName(n.book), n.chapter, n.from, n.to)

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

    /** 화면 말 (권 이름 · 판화 이름 · 장 표기 · 제목 글꼴): 앱 언어 (고른 언어, 없으면 폰 언어). 언어를 바꾸면 앱이 다시 열려요. */
    val korean: Boolean = Lang.uiKorean(store.context)
    /** 본문 말 (성경 글 · 글꼴 · 읽어 주는 목소리): 고른 번역. */
    val bibleKorean: Boolean get() = translation == Translation.KRV
    /** 화면 언어로 장 · 절을 적을 때 쓰는 번역 (언어만 봐요). */
    val uiTr: Translation get() = if (korean) Translation.KRV else Translation.KJV
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
    fun locked(b: Int) = b !in Canon.free && !lifetime.unlocked && (lifetime.gating || forceLock)
    fun widgets() {
        val ctx = store.context
        // 목표 계산 (길잡이 책들을 읽음) 과 위젯 그리기는 화면 줄 밖에서
        Thread { runCatching { store.lastGoal = effectiveGoal() }; io.github.graviton94.todaybible.widget.WidgetTick.refreshAll(ctx); io.github.graviton94.todaybible.widget.WidgetTick.schedule(ctx) }.start()
    }

    /**
     * 오늘의 장 (길잡이가 없을 때): 마지막으로 쓰던 자리. 기도문 따라 쓰기로 잠깐 옮긴 장이 아니라 저장된 자리를 써요.
     * 그 장을 다 채웠으면 다음 장으로.
     */
    fun resumePlace(): Pair<Int, Int> {
        // 펼쳐 보기만 한 장 (판화를 보러 7장으로 …) 이 아니라 실제로 마지막에 쓴 절의 장
        val (b, c) = lastWritten()?.let { it.book to it.chapter } ?: store.bookmark(translation)
        // 다 썼는지는 지금 쓰는 필사본 기준 (타자본으로 마친 장이면 다음 장)
        val done = progress.chapterDone(translation, store.book(translation, b), c, writingMode)
        return if (done) nextChapter(b, c) else b to c
    }

    /** 이 번역에서 마지막으로 쓴 절 (쓴 시각 순). 없으면 null. */
    fun lastWritten(): io.github.graviton94.todaybible.core.VerseKey? = fills.filter { it.translation == translation }.maxByOrNull { it.atMillis }?.key
    /** 세 필사본 (1.7): 탭 번호 ↔ 방식. 0 낭독 · 1 타자 · 2 손글씨. */
    fun modeOf(tab: Int): Mode = when (tab) { 0 -> Mode.ALOUD; 2 -> Mode.PAPER; else -> Mode.TYPE }
    fun tabOf(m: Mode): Int = when (m) { Mode.ALOUD -> 0; Mode.PAPER -> 2; Mode.TYPE -> 1 }
    /** 지금 쓰는 필사본: 마지막으로 쓴 방식 (아직 없으면 필사 탭). 오늘 · 서재 · 순례길의 진도가 이 방식으로 보여요. */
    val writingMode: Mode get() = fills.lastOrNull { it.translation == translation }?.mode ?: modeOf(store.copyTab)
    /** 이 장의 마지막 방식 (없으면 null). */
    fun chapterMode(b: Int, c: Int): Mode? = fills.lastOrNull { it.translation == translation && it.key.book == b && it.key.chapter == c }?.mode
    /** 쓰다 멈춘 장들: 가장 최근에 쓴 순서로, 다 쓰지 않은 장만 (권, 장, 다음 절, 쓴 절 수, 전체 절 수). */
    data class Paused(val book: Int, val chapter: Int, val next: Int, val done: Int, val total: Int, val mode: Mode = Mode.TYPE)
    fun pausedChapters(limit: Int = 3, except: Pair<Int, Int>? = null): List<Paused> {
        // 장마다 마지막에 쓴 방식으로 (그 필사본에서 다 못 쓴 장만)
        val seen = LinkedHashMap<Pair<Int, Int>, Mode>()
        fills.filter { it.translation == translation }.sortedByDescending { it.atMillis }.forEach { seen.putIfAbsent(it.key.book to it.key.chapter, it.mode) }
        val hidden = store.hiddenPaused
        return seen.entries.asSequence().filter { it.key != except && "${translation.id}:${it.key.first}:${it.key.second}" !in hidden }.mapNotNull { (bc, m) ->
            val (b, c) = bc
            val t = store.book(translation, b); val fill = t.fillable(c)
            val done = fill.count { progress.isFilled(translation, io.github.graviton94.todaybible.core.VerseKey(b, c, it), m) }
            if (done >= fill.size) null else Paused(b, c, progress.nextVerse(translation, t, c, m) ?: fill.first(), done, fill.size, m)
        }.take(limit).toList()
    }

    /** 듣기를 시작할 절: 이 장을 듣다 멈췄으면 그 절부터 (장 끝까지 들었으면 처음부터). */
    fun listenFrom(b: Int, ch: Int): Int {
        val first = text(b).fillable(ch).firstOrNull() ?: 1
        val p = store.lastListen.split(':').takeIf { it.size == 5 } ?: return first
        if (p[0] != translation.id || p[1].toIntOrNull() != b || p[2].toIntOrNull() != ch) return first
        val v = p[3].toIntOrNull() ?: return first
        return v.takeIf { it in text(b).fillable(ch) && it != text(b).fillable(ch).lastOrNull() } ?: first
    }
    /** 순례길 목록이 바뀐 횟수 (빼기 · 새로 쓰기). */
    var pausedRev by mutableIntStateOf(0)
    /** 나의 순례길에서 빼기: 목록에서만 사라지고 기록은 그대로. */
    fun hidePaused(b: Int, c: Int) { store.hiddenPaused = store.hiddenPaused + "${translation.id}:$b:$c"; pausedRev++ }
    /** 새로 쓰기: 이 장의 기록 (낭독 녹음 · 손글씨 포함) 을 지우고 1절부터. 되돌릴 수 없어요. */
    fun restartChapter(b: Int, c: Int) {
        val tr = translation
        val keep = fills.filterNot { it.translation == tr && it.key.book == b && it.key.chapter == c }
        store.rewrite(keep); fills.clear(); fills.addAll(keep); cachedProgress = null; cachedSize = -1
        val ctx = store.context
        Thread {
            runCatching { io.github.graviton94.todaybible.data.Voice.verses(ctx, tr.id, b, c).forEach { it.second.delete() } }
            runCatching { io.github.graviton94.todaybible.data.Ink.verses(ctx, tr.id, b, c).forEach { io.github.graviton94.todaybible.data.Ink.file(ctx, tr.id, b, c, it).delete() } }
        }.start()
        store.hiddenPaused = store.hiddenPaused - "${tr.id}:$b:$c"
        voiceRev++; pausedRev++; widgets()
    }

    fun open(b: Int, ch: Int) {
        if (locked(b)) { peekBook = b; purchaseOpen = true; return }
        prayerWrite = null
        book = b; chapter = ch; target = null; store.setBookmark(translation, b, ch); page = COPY; widgets()
        // 판화가 있는 장을 처음 펼치면: 그림으로 여는 화면 한 번 (다 쓴 장은 건너뜀)
        if (store.plateFor(b, ch) != null && "$b:$ch" !in store.openerSeen && progress.chapterFraction(translation, store.book(translation, b), ch) < 1f) opener = b to ch
        // 낭독 · 교독으로 펼치는데 음원이 없으면: 받는 관문을 거쳐 들어가요
        val N = io.github.graviton94.todaybible.data.Narration
        if (store.copyTab == 0 && aloudMode != 2 && narrator != N.DEVICE && narrationState(b, ch) != 1 && !N.has(store.context, narrator, b, ch)) { narrGate = b to ch; fetchNarration(b, ch) }
    }
    /** 기도문 따라 쓰기 (기도문 아이디, 몇 번째 말씀): 기도문 범위는 어느 책이든 무료. */
    var prayerWrite by mutableStateOf<Pair<String, Int>?>(null)
    /** 지금 펼친 장이 따라 쓰는 기도문 말씀이면 그 범위. */
    fun prayerPassHere(): io.github.graviton94.todaybible.core.Reference.Passage? = prayerWrite?.let { (id, i) -> io.github.graviton94.todaybible.core.Prayers.byId(id)?.passages?.getOrNull(i) }
        ?.takeIf { it.book == book && it.chapter == chapter }
    fun writePrayer(id: String, i: Int = 0) {
        val ps = io.github.graviton94.todaybible.core.Prayers.byId(id)?.passages?.getOrNull(i) ?: return
        prayerOpen = null; prayerWrite = id to i
        book = ps.book; chapter = ps.chapter
        target = (ps.from..ps.to).firstOrNull { !progress.isFilled(translation, io.github.graviton94.todaybible.core.VerseKey(ps.book, ps.chapter, it)) } ?: ps.from
        if (!locked(ps.book)) store.setBookmark(translation, ps.book, ps.chapter)
        page = COPY
    }
    fun chooseTranslation(t: Translation) { translation = t; store.translation = t; narrator = store.narratorFor(t); narration = emptyMap(); val bm = store.bookmark(t); book = bm.first; chapter = bm.second; widgets() }
    fun setThemeChoice(t: ThemeChoice) { theme = t; store.theme = t; checkNight(); widgets() }
    /** 언어 (시스템 · ko · en): 화면 글 · 성경 번역 · 낭독 목소리를 한 번에. 화면 글은 다시 그릴 때 (recreate) 바뀌어요. */
    var language by mutableStateOf(store.language)
    /** 캡처용: 설정의 펼친 갈래 · 언어 묻기 창. */
    var debugSection: String? = null
    var debugAskLang: String? = null
    /** 듣는 화면으로 (듣는 중 띠 · 알림): 기도문이면 그 기도문, 아니면 성경 읽기의 그 장. */
    fun goToListening() {
        val n = io.github.graviton94.todaybible.data.ListenService.now.value ?: return
        val o = io.github.graviton94.todaybible.data.ListenService.origin.value
        if (o.startsWith("prayer:")) prayerOpen = o.removePrefix("prayer:")
        else { readFrom = if (page != BIBLE) page else null; listenAt = n.book to n.chapter; page = BIBLE }
    }
    /** 하던 일 (듣기 · 소리 내어 읽기) 을 두고 뒤로 갈 때 묻는 창. */
    class LeaveAsk(val recording: Boolean, val onStop: () -> Unit, val onKeep: (() -> Unit)?)
    var leaveAsk by mutableStateOf<LeaveAsk?>(null)
    /** 타자로 쓰다 만 절 ("번역:권:장:절" to 글): 다른 화면에 다녀와도 그대로. */
    var typeDraft: Pair<String, String>? = null
    /** 덮인 창들 모두 닫기 (알림 · 위젯으로 들어올 때). */
    fun closeOverlays() {
        keepOpen = false
        settingsOpen = false; purchaseOpen = false; plateView = null; finished = null; picker = null; marksOpen = false; found = null
        memoryOpen = null; sermonOpen = null; prayersOpen = false; galleryOpen = false; prayerOpen = null; prayerBell = null; shareVerse = null; related = null; dictEntry = null; versePeek = null; handBook = null; planOpen = false; modeAsk = null; votumDone = null; pilgrim = null; liber = null; shelfOpen = false; noteOpen = null; award = null; opening = false; leaveAsk = null
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
        val tr = when (l) { "ko" -> Translation.KRV; "en" -> store.englishBible; else -> if (android.content.res.Resources.getSystem().configuration.locales[0].language == "ko") Translation.KRV else store.englishBible }
        if (tr != translation) chooseTranslation(tr)
    }
    /** 영어 성경 고르기 (WEB · KJV): 영어로 보고 있으면 바로 그 번역으로. 기록은 번역마다 따로 남아요. */
    fun chooseEnglishBible(t: Translation) { store.englishBible = t; if (translation != Translation.KRV) chooseTranslation(t) }
    fun setTextScale(s: Float) { val v = if (s > 1.01f) io.github.graviton94.todaybible.design.Tokens.Ratio.scaleLarge else 1f; scale = v; store.textScale = v }
    fun setStampMark(s: String) { stamp = s; store.stamp = s; widgets() }
    /** 나의 성경 PDF: 평생권이 필요하면 평생권 화면으로. */
    /** 노트 PDF 를 만들 권 (-1 = 전체). */
    var notesBook by mutableStateOf<Int?>(null)
    /** 내 목소리 한 권 (오디오북) 만들기. */
    var audiobookBook by mutableStateOf<Int?>(null)
    fun requestAudiobook(b: Int) { if (gated()) purchaseOpen = true else audiobookBook = b }
    /** 묵상 노트 PDF (한 해). */
    var reflectYear by mutableStateOf<Int?>(null)
    fun requestReflections(y: Int) { if (gated()) purchaseOpen = true else reflectYear = y }
    fun requestNotes(b: Int?) { if (gated()) purchaseOpen = true else notesBook = b ?: -1 }
    fun requestPdf(b: Int) { if (!lifetime.unlocked && (lifetime.gating || forceLock)) purchaseOpen = true else pdfBook = b }
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
    fun choosePlan(id: String?, note: String = "") {
        val p = io.github.graviton94.todaybible.core.Plans.byId(id)
        if (p != null && p.books.any { locked(it) }) { peekBook = p.books.first { locked(it) }; purchaseOpen = true; return }
        // 쓰던 자리 (필사 화면의 권 · 장 · 책갈피) 는 건드리지 않아요. 오늘 화면이 작정의 장을 따로 알려 줘요.
        // 향하는 날이 있는 작정은 그 창의 첫날부터 (아직 안 왔으면 미리 걸어 둠)
        val start = p?.let { io.github.graviton94.todaybible.core.Plans.window(it, today(), korean) }?.first?.toEpochDay() ?: today().toEpochDay()
        planId = id; store.planId = id; store.planStart = start; planNote = note.trim(); store.planNote = planNote; planOpen = false; warmPlan()
        widgets()
    }
    /** 작정 기도 제목. */
    var planNote by mutableStateOf(store.planNote)
    /** 작정이 아직 시작 전 (향하는 날의 창이 오기 전). */
    fun planPending(): Boolean = plan != null && store.planStart > today().toEpochDay()
    /** 작정을 다 썼는가 (마침 카드). */
    fun planComplete(): Boolean = plan != null && planCounts().let { (t, d) -> t > 0 && d >= t }
    /** 마침 카드를 보여 줄 작정 ("id:시작날"), 이미 봤으면 null. */
    fun votumToShow(): String? = plan?.let { "${it.id}:${store.planStart}" }?.takeIf { planComplete() && it != store.votumShown }
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
    /** 오늘 화면의 절기 계획 줄에서 고른 계획: 계획 창이 열리며 바로 「시작할까요?」 로. */
    var planAsk: String? = null
    /** 오늘 카드의 장: 계획을 시작한 뒤 계획 밖의 장을 마지막으로 썼으면 그 자리 (쓰던 걸 덮지 않게), 아니면 계획의 다음 장. */
    fun todayPlace(): Triple<Int, Int, Int?> {
        val pn = planNext(); val pl = plan
        val last = fills.filter { it.translation == translation }.maxByOrNull { it.atMillis }
        val off = pl != null && last != null && last.epochDay >= store.planStart && (last.key.book to last.key.chapter) !in pl.chapters
        if (pn != null && !off && !planPending()) return Triple(pn.first, pn.second, pn.third)
        val (b, c) = resumePlace(); return Triple(b, c, null)
    }
    fun planDay(): Int = io.github.graviton94.todaybible.core.Plans.day(store.planStart, today())
    /** 오늘의 분량: 길잡이가 있으면 남은 절 ÷ 남은 날 (오늘 쓴 절은 오늘 몫에 포함). */
    fun effectiveGoal(): Int {
        val pl = plan ?: return dailyGoal
        if (planPending()) return dailyGoal
        val (total, done) = planCounts(); val doneBefore = done - todayVerses()
        val left = (pl.days - planDay() + 1).coerceAtLeast(1)
        return io.github.graviton94.todaybible.core.Plans.perDay(total, doneBefore, left).coerceAtLeast(1)
    }
    /** 평생권이 있어야 하는데 없는 상태 (Play 에 닿을 때만 잠금). */
    /** 평생권으로 열리는 권인지 (보여 주기용: 결제를 쓸 수 없는 시험판에서도 표시). */
    fun premium(b: Int) = b !in Canon.free && !lifetime.unlocked
    /** 평생권 기능 표시 (붓펜 · 연필 · 표지 · PDF · 녹음 …). */
    val premiumOn: Boolean get() = !lifetime.unlocked
    fun gated() = !lifetime.unlocked && (lifetime.gating || forceLock)
    fun toggleVoice() { if (gated()) { purchaseOpen = true; return }; voiceOn = !voiceOn; store.voiceOn = voiceOn }
    fun setOwner(n: String) { ownerName = n; store.ownerName = n; widgets() }

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

    fun setReminder(h: Int, m: Int = reminderMinute) { reminderHour = h; store.reminderHour = h; reminderMinute = m; store.reminderMinute = m; if (h >= 0) { store.lastReminderHour = h; val today = java.time.LocalDate.now().toEpochDay(); store.reminderDay = if (!java.time.LocalTime.now().isBefore(java.time.LocalTime.of(h, m)) || store.postedDay == today) today else -1 }; io.github.graviton94.todaybible.data.Reminder.schedule(store.context, h) }

    /** 절(들)을 채움. 장을 다 채우면 finished, 새 발자취가 생기면 award. */
    /** 방금 마친 장의 방식 · 다른 필사본으로 이미 마친 장인지 (마침 화면을 짧게). */
    var finishedMode: Mode = Mode.TYPE
    /** 이 장을 타자로 쓰다 틀린 글자가 있었는지 ("권:장"). 오타 없이 마친 장 메달. */
    var typoIn: String? = null
    var finishedAgain = false
    fun fill(verses: List<Int>, mode: Mode) {
        val day = today().toEpochDay(); val now = System.currentTimeMillis()
        // 세 필사본: 같은 절도 방식마다 한 번씩
        val tr = translation; val already = progress.filled(tr, mode)
        val new = verses.map { VerseKey(book, chapter, it) }.filter { it.raw !in already }.map { Fill(tr, it, mode, day, now) }
        if (new.isEmpty()) return
        val t0 = text(); val otherDone = Mode.entries.any { it != mode && progress.chapterDone(tr, t0, chapter, it) }
        val bookBefore = progress.bookDone(tr, t0)
        if (store.startDay < 0) store.startDay = day
        // 위젯은 절마다 다시 그리지 않아요 (앱을 떠날 때 · 자정에 한 번)
        store.append(new); fills.addAll(new)
        // 순례길에서 뺀 장에 다시 쓰면 목록으로 돌아와요
        "${tr.id}:$book:$chapter".let { k -> if (k in store.hiddenPaused) { store.hiddenPaused = store.hiddenPaused - k; pausedRev++ } }
        val t = text()
        val wasMet = io.github.graviton94.todaybible.core.Goal.met(effectiveGoal(), todayVerses() - new.size, 0)
        val fx = io.github.graviton94.todaybible.data.Effects
        // 타자로 쓴 글자 수 (메달): 새로 쓴 절의 글자
        if (mode == Mode.TYPE && store.typedLetters >= 0) store.typedLetters = store.typedLetters + new.sumOf { f -> io.github.graviton94.todaybible.core.Markup.plain(t0.verse(f.key.chapter, f.key.verse)).count { !it.isWhitespace() } }
        // 성탄 새벽 (숨은 메달): 성탄절 아침 7시 전 누가복음 2장
        if (book == 41 && chapter == 2 && today() == io.github.graviton94.todaybible.core.ChurchYear.christmas(today().year) && java.time.LocalTime.now().hour < 7) store.christmasLuke = true
        if (progress.chapterDone(tr, t, chapter, mode)) {
            finishedMode = mode; finishedAgain = otherDone
            // 기능 메달에 드는 것들: 한밤 (0–4시) 에 마친 장 · 손글씨로 마친 장의 펜 · 오타 없이 마친 장 · 집중 모드로 마친 장
            if (java.time.LocalTime.now().hour < 4) store.nightChapter = true
            if (mode == Mode.PAPER) store.pensDone = store.pensDone + pen.toString()
            if (mode == Mode.TYPE && typoIn != "$book:$chapter") store.bump("typo_free_ch")
            if (focusMode) store.bump("focus_ch")
            typoIn = null
            // 효과음: 그 장에 판화가 있으면 화첩에 걸리는 종, 없으면 COMPLETUM 인장 (권 다 씀은 의식이 열릴 때)
            fx.play(store.context, if (store.plateFor(book, chapter) != null) io.github.graviton94.todaybible.data.Effects.Fx.PLATE else io.github.graviton94.todaybible.data.Effects.Fx.SEAL)
            finished = book to chapter; store.finishedCount = store.finishedCount + 1
            // 권의 마지막 장까지: 장 마침 다음에 권 다 쓰기 의식 (권마다 한 번)
            if (!bookBefore && progress.bookDone(tr, t) && "${tr.id}:$book" !in store.ritualSeen) pendingRitual = book
        }
        else {
            fx.play(store.context, io.github.graviton94.todaybible.data.Effects.Fx.VERSE)
            if (!wasMet && goalMet()) toast = store.context.getString(io.github.graviton94.todaybible.R.string.goal_done)
            else if (mode != Mode.TYPE) toast = store.context.getString(io.github.graviton94.todaybible.R.string.filled_n, new.size)
        }
        checkMilestones()
    }

    /** 1.7 기능 메달에 드는 기록을 모아요 (필사본별 다 쓴 장 · 권, 글자 수, 기능마다 센 수 …). */
    fun medalMore(): Milestones.More {
        val p = progress; val tr = translation
        val chBy = HashMap<Mode, MutableSet<Int>>(); val bkBy = HashMap<Mode, MutableSet<Int>>()
        Mode.entries.forEach { m ->
            p.filled(tr, m).map { VerseKey(it).book }.toSet().forEach { b ->
                val t = store.book(tr, b)
                (1..t.chapterCount).forEach { c -> if (p.chapterDone(tr, t, c, m)) chBy.getOrPut(m) { HashSet() } += (b + 1) * 1000 + c }
                if (p.bookDone(tr, t, m)) bkBy.getOrPut(m) { HashSet() } += b
            }
        }
        // 타자로 쓴 글자: 처음 한 번은 지난 기록으로 셈 (그 뒤로는 쓸 때마다 더함)
        if (store.typedLetters < 0) store.typedLetters = fills.filter { it.translation == tr && it.mode == Mode.TYPE }.sumOf { f -> io.github.graviton94.todaybible.core.Markup.plain(store.book(tr, f.key.book).verse(f.key.chapter, f.key.verse)).count { !it.isWhitespace() } }
        // 마친 작정은 기억해 둬요 (작정을 바꿔도 남게)
        plan?.let { pl -> if (planComplete() && pl.id !in store.votumsDone) store.votumsDone = store.votumsDone + pl.id }
        // 설교 노트가 이어진 주일 수 (가장 긴 것)
        // 보관 알림이 세는 다 쓴 장 (필사본마다 센 합) · 처음 장을 마친 날
        val doneNow = chBy.values.sumOf { it.size }
        if (store.doneChaptersNow != doneNow) store.doneChaptersNow = doneNow
        if (doneNow > 0 && store.firstChapterDay < 0) store.firstChapterDay = today().toEpochDay()
        val sundays = notes.filter { it.kind == io.github.graviton94.todaybible.data.Store.NoteKind.SERMON }.map { it.epochDay }.toSortedSet()
        var run = 0; var best = 0; var prev = Long.MIN_VALUE
        sundays.forEach { d -> run = if (d - prev == 7L) run + 1 else 1; best = maxOf(best, run); prev = d }
        return Milestones.More(chBy, bkBy, store.typedLetters, store.counts(), store.pensDone,
            hungPlates().count { it.set.isEmpty() }, store.plates.count { it.set.isEmpty() }.coerceAtLeast(1),
            notes.count { it.kind == io.github.graviton94.todaybible.data.Store.NoteKind.REFLECTION }, best,
            bookmarks.size, marks.size, store.votumsDone, store.nightChapter, store.christmasLuke)
    }

    /** 메달 진행을 보여 줄 때 쓰는 상태 (기록이 바뀔 때만 다시 셈). */
    private var medalMemo: Pair<List<Int>, Milestones.State>? = null
    var medalRev by mutableIntStateOf(0)
    fun medalState(): Milestones.State {
        val key = listOf(fills.size, medalRev, notes.size, bookmarks.size, marks.size)
        medalMemo?.let { (k, v) -> if (k == key) return v }
        return Milestones.State(fills.filter { it.translation == translation }, emptySet(), emptySet(), korea = korean, memorized = knownDays(), more = medalMore()).also { medalMemo = key to it }
    }
    /** 기능을 한 번 쓸 때마다 세고 메달을 살펴요. */
    fun bump(k: String) { store.bump(k); medalRev++; checkMilestones() }

    fun checkMilestones() {
        val p = progress
        val doneChapters = HashSet<Int>(); val doneBooks = HashSet<Int>()
        // 기록이 있는 권만 살펴봄 (전체 66권을 매번 열지 않게)
        p.filled(translation).map { VerseKey(it).book }.toSet().forEach { b ->
            val t = store.book(translation, b)
            (1..t.chapterCount).forEach { c -> if (p.chapterDone(translation, t, c)) doneChapters += (b + 1) * 1000 + c }
            if (p.bookDone(translation, t)) doneBooks += b
        }
        val got = Milestones.earned(Milestones.State(fills.filter { it.translation == translation }, doneChapters, doneBooks, korea = korean, memorized = knownDays(), more = medalMore()))
        val fresh = got.keys - earned.keys
        if (fresh.isNotEmpty()) {
            earned = earned + fresh.associateWith { got[it] ?: today().toEpochDay() }
            store.saveEarned(earned)
            award = fresh.first()
            // 메달 소리는 장 · 절 소리가 지나간 뒤에
            io.github.graviton94.todaybible.data.Effects.play(store.context, io.github.graviton94.todaybible.data.Effects.Fx.MEDAL, 1400)
        }
    }

    /** 캡처용 시험 기록: 지난 40여 일 동안 창세기 1–8장 · 시편 23편 · 마가복음 1–2장 등을 나눠 씀. */
    fun seedDemo() {
        val tr = translation; val end = today()
        val plan = listOf(0 to (1..8).map { it to 1f }, 18 to listOf(23 to 1f), 40 to listOf(1 to 1f, 2 to 1f, 3 to 0.35f),
            1 to listOf(14 to 0.42f), 41 to listOf(15 to 0.75f), 39 to listOf(5 to 0.17f), 7 to (1..4).map { it to 1f })
        val keys = plan.flatMap { (b, chs) -> val t = store.book(tr, b); chs.flatMap { (ch, f) -> val v = t.fillable(ch); v.take((v.size * f).toInt()).map { VerseKey(b, ch, it) } } }
        // 쓴 날: 41일 전부터, 주일 몇 번 · 평일 사흘은 쉼
        val days = (40 downTo 0).map { end.minusDays(it.toLong()) }.filterIndexed { i, d -> !(d.dayOfWeek == java.time.DayOfWeek.SUNDAY && i % 3 != 0) && i !in setOf(5, 17, 26) }
        val modes = listOf(Mode.TYPE, Mode.TYPE, Mode.ALOUD, Mode.PAPER)
        // 날마다 고르게 나눔 (마지막 날 = 오늘까지)
        val already = progress.filled(tr)
        val new = keys.mapIndexed { n, key ->
            val i = (n.toLong() * days.size / keys.size).toInt(); val d = days[i]
            val at = d.atTime(6 + i % 3, 30).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            Fill(tr, key, modes[(key.book + key.chapter) % modes.size], d.toEpochDay(), at)
        }.filter { it.key.raw !in already }
        if (new.isEmpty()) return
        if (store.startDay < 0) store.startDay = days.first().toEpochDay()
        store.append(new); fills.addAll(new)
        checkMilestones(); award = null
        // 소리 내어 읽은 시간: 지난 이레
        aloudLog = (0..6).associate { end.minusDays(it.toLong()).toEpochDay() to listOf(720, 0, 1260, 540, 960, 300, 840)[it] }.filterValues { it > 0 }; store.saveAloud(aloudLog)
        // 묵상 노트 견본 (캡처용, 내가 적은 글 자리)
        if (notes.none { it.kind == io.github.graviton94.todaybible.data.Store.NoteKind.REFLECTION }) {
            val ko = tr == io.github.graviton94.todaybible.core.Translation.KRV
            val sample = listOf(Triple(0, 1, if (ko) "빛이 있으라. 오늘도 거기서 시작해요" else "Let there be light. Starting there again today"),
                Triple(40, 1, if (ko) "곧 · 곧 · 곧. 머뭇거리지 않는 부르심" else "Straightway, again and again"),
                Triple(40, 2, if (ko) "지붕을 뜯어서라도 데려온 친구들" else "Friends who broke open a roof"))
            saveNotes(notes + sample.mapIndexed { i, (b, c, t) -> io.github.graviton94.todaybible.data.Store.Note(io.github.graviton94.todaybible.data.Store.NoteKind.REFLECTION, tr, b, c, 0, 0, end.minusDays((30 - i * 9).toLong()).toEpochDay(), t) })
        }
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
    fun chapterRef(b: Int = book, ch: Int = chapter) = Lang.chapterRef(store.context, uiTr, bookName(b), ch)
    /** 음성 인식 언어 (번역을 따라). */
    val speechTag: String get() = Lang.speechTag(store.context, translation)
}
