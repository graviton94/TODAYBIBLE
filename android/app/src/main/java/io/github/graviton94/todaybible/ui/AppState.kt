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
    var page by mutableStateOf(0)
    var settingsOpen by mutableStateOf(false)
    /** 방금 마친 장 (덮개 · 조각 화면). */
    var finished by mutableStateOf<Pair<Int, Int>?>(null)
    /** 방금 얻은 발자취 (아래에서 올라오는 카드). */
    var award by mutableStateOf<Milestone?>(null)
    var earned by mutableStateOf(store.loadEarned())
    /** 캡처용 고정 날짜 (개발자 도구). */
    var fixedToday: LocalDate? = null

    val korean: Boolean get() = translation == Translation.KRV
    val progress: Progress get() = Progress(fills.toList())
    fun today(): LocalDate = fixedToday ?: LocalDate.now()
    fun text(b: Int = book) = store.book(translation, b)
    fun locked(b: Int) = false // 평생권 연결 전까지 모두 열림 (Canon.free 로 나눌 예정)

    fun open(b: Int, ch: Int) { book = b; chapter = ch; store.setBookmark(translation, b, ch); page = 1 }
    fun chooseTranslation(t: Translation) { translation = t; store.translation = t; val bm = store.bookmark(t); book = bm.first; chapter = bm.second }
    fun setThemeChoice(t: ThemeChoice) { theme = t; store.theme = t }
    fun setTextScale(s: Float) { scale = s; store.textScale = s }
    fun setStampMark(s: String) { stamp = s; store.stamp = s }

    /** 절(들)을 채움. 장을 다 채우면 finished, 새 발자취가 생기면 award. */
    fun fill(verses: List<Int>, mode: Mode) {
        val day = today().toEpochDay(); val now = System.currentTimeMillis()
        val tr = translation; val already = progress.filled(tr)
        val new = verses.map { VerseKey(book, chapter, it) }.filter { it.raw !in already }.map { Fill(tr, it, mode, day, now) }
        if (new.isEmpty()) return
        if (store.startDay < 0) store.startDay = day
        store.append(new); fills.addAll(new)
        val t = text()
        if (progress.chapterDone(tr, t, chapter)) finished = book to chapter
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
        open(40, 3); page = 0
    }

    fun nextChapter(b: Int = book, ch: Int = chapter): Pair<Int, Int> {
        val t = text(b)
        return if (ch < t.chapterCount) b to ch + 1 else if (b < 65) b + 1 to 1 else b to ch
    }
    fun bookName(b: Int = book) = if (korean) Canon.books[b].ko else Canon.books[b].en
}
