package io.github.graviton94.todaybible.core

import java.time.DayOfWeek
import java.time.LocalDate

/** 출석: 한 절이라도 채운 날 = 도장. 주일은 쉬어도 끊김으로 보지 않음. 벌은 없고 누적만. */
object Presence {
    fun total(days: Set<Long>): Int = days.size
    /** 이번 주(주일 시작) 월~토 중 쓴 날이 5일 이상이면 ‘주 완성’. */
    fun weekComplete(days: Set<Long>, any: LocalDate): Boolean {
        val sunday = any.minusDays((any.dayOfWeek.value % 7).toLong())
        return (1..6).count { sunday.plusDays(it.toLong()).toEpochDay() in days } >= 5
    }
    fun weeksComplete(days: Set<Long>): Int = days.map { LocalDate.ofEpochDay(it) }.map { it.minusDays((it.dayOfWeek.value % 7).toLong()) }.toSet().count { weekComplete(days, it) }
    fun isRest(d: LocalDate) = d.dayOfWeek == DayOfWeek.SUNDAY
    /**
     * 이어 쓴 날 수: 오늘(아직 안 썼으면 어제)부터 거슬러 셈. 비어 있는 주일은 건너뛰고 끊지 않음.
     */
    /** 가장 길게 이어 쓴 날 수 (비어 있는 주일은 끊지 않음). */
    fun longestStreak(days: Set<Long>): Int {
        if (days.isEmpty()) return 0
        val sorted = days.sorted(); var best = 1; var run = 1
        for (i in 1 until sorted.size) {
            val gap = (sorted[i - 1] + 1 until sorted[i]).map { LocalDate.ofEpochDay(it) }
            run = if (gap.all { isRest(it) }) run + 1 else 1
            best = maxOf(best, run)
        }
        return best
    }
    fun streak(days: Set<Long>, today: LocalDate): Int {
        var d = if (today.toEpochDay() in days) today else today.minusDays(1)
        var n = 0
        while (true) {
            if (d.toEpochDay() in days) n++
            else if (!isRest(d)) break
            d = d.minusDays(1)
            if (n == 0 && today.toEpochDay() - d.toEpochDay() > 2) break
            if (today.toEpochDay() - d.toEpochDay() > 4000) break
        }
        return n
    }
}

/** 교회력: 부활절(그레고리력 계산법) 기준 절기. 추수감사는 한국 = 11월 셋째 주일, 영어권 = 미국 11월 넷째 목요일. */
object ChurchYear {
    fun easter(y: Int): LocalDate {
        val a = y % 19; val b = y / 100; val c = y % 100; val d = b / 4; val e = b % 4
        val f = (b + 8) / 25; val g = (b - f + 1) / 3; val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4; val k = c % 4; val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451; val month = (h + l - 7 * m + 114) / 31; val day = (h + l - 7 * m + 114) % 31 + 1
        return LocalDate.of(y, month, day)
    }
    fun palmSunday(y: Int) = easter(y).minusDays(7)
    fun ashWednesday(y: Int) = easter(y).minusDays(46)
    fun pentecost(y: Int) = easter(y).plusDays(49)
    fun christmas(y: Int): LocalDate = LocalDate.of(y, 12, 25)
    fun thanksgiving(y: Int, korea: Boolean): LocalDate {
        val first = LocalDate.of(y, 11, 1)
        return if (korea) { val sun = first.plusDays(((7 - first.dayOfWeek.value % 7) % 7).toLong()); sun.plusWeeks(2) }
        else { val thu = first.plusDays(((DayOfWeek.THURSDAY.value - first.dayOfWeek.value + 7) % 7).toLong()); thu.plusWeeks(3) }
    }
}

/** 교회력에서 앱이 챙기는 날 (그날 쓰면 받는 발자취와 짝). */
enum class Feast(val milestone: Milestone) { PALM(Milestone.HOSANNA), EASTER(Milestone.TOMB), PENTECOST(Milestone.FIRE), THANKSGIVING(Milestone.FIRSTFRUITS), CHRISTMAS(Milestone.STAR) }

object Feasts {
    fun dateOf(f: Feast, y: Int, korea: Boolean): LocalDate = when (f) {
        Feast.PALM -> ChurchYear.palmSunday(y); Feast.EASTER -> ChurchYear.easter(y); Feast.PENTECOST -> ChurchYear.pentecost(y)
        Feast.THANKSGIVING -> ChurchYear.thanksgiving(y, korea); Feast.CHRISTMAS -> ChurchYear.christmas(y)
    }
    /** 오늘부터 다가오는 날들 (오늘 포함, 가까운 순). */
    fun upcoming(today: LocalDate, korea: Boolean, count: Int = 4): List<Pair<Feast, LocalDate>> =
        (today.year..today.year + 1).flatMap { y -> Feast.entries.map { it to dateOf(it, y, korea) } }
            .filter { !it.second.isBefore(today) }.sortedBy { it.second }.take(count)
}

/**
 * 오늘의 분량: 절 수, 또는 장 (CHAPTER = 하루 한 장, TWO = 하루 두 장). 기본은 하루 한 장.
 * 장이 아주 길면 (시편 119편 등) 한 장 몫의 절 (LONG) 을 써도 채운 것으로 쳐요.
 */
object Goal {
    const val CHAPTER = -1
    const val TWO = -2
    const val LONG = 25
    val choices = listOf(5, CHAPTER, TWO)
    /** 장 수 (절 목표면 0). */
    fun chapters(goal: Int): Int = if (goal < 0) -goal else 0
    /** 분량을 채웠는지: 절이면 오늘 쓴 절 수, 장이면 오늘 마친 장 수 (또는 장마다 LONG 절). */
    fun met(goal: Int, todayVerses: Int, todayChapters: Int): Boolean =
        if (goal < 0) todayChapters >= chapters(goal) || todayVerses >= LONG * chapters(goal) else todayVerses >= goal
    /** 이 분량이면 몇 날이 걸리는지 (절 수 기준, 장이면 장 수). */
    fun days(goal: Int, verses: Int, chapters: Int): Int = if (goal < 0) (chapters + chapters(goal) - 1) / chapters(goal) else (verses + goal - 1) / goal
}

/** 필사 길잡이: 정한 권 · 장을 정한 날 수 안에. 하루 분량은 남은 절을 남은 날로 나눔. */
data class Plan(val id: String, val chapters: List<Pair<Int, Int>>, val days: Int) {
    val books: Set<Int> get() = chapters.map { it.first }.toSet()
}

object Plans {
    private fun whole(book: Int) = (1..Canon.books[book].chapters).map { book to it }
    val all = listOf(
        Plan("mark30", whole(40), 30),
        Plan("prov31", whole(19), 31),
        Plan("ps365", whole(18), 365),
        Plan("nt365", (39..65).flatMap { whole(it) }, 365),
        Plan("bible365", (0..65).flatMap { whole(it) }, 365),
        // 교회력 계획: 사순절 40일 (마가복음 · 누가복음 22–24 · 요한복음 12–21), 고난주간 8일, 대림절 24일
        Plan("lent40", whole(40) + (22..24).map { 41 to it } + (12..21).map { 42 to it }, 40),
        Plan("holy8", listOf(39 to 21) + (13..17).map { 42 to it } + (26..28).map { 39 to it }, 8),
        Plan("advent24", listOf(9, 11, 40, 53, 60, 61).map { 22 to it } + listOf(39 to 1, 39 to 2, 41 to 1, 41 to 2, 42 to 1), 24),
    )
    /** 절기 계획을 권할 때: 시작 이레 전부터 시작 사흘 뒤까지. (계획 id, 시작 날) */
    fun seasonal(today: LocalDate): Pair<String, LocalDate>? {
        for (y in listOf(today.year, today.year + 1)) {
            val easter = ChurchYear.easter(y)
            val christmas = ChurchYear.christmas(y)
            val advent = christmas.minusDays((christmas.dayOfWeek.value % 7).toLong() + 21)
            for ((id, start) in listOf("lent40" to easter.minusDays(46), "holy8" to easter.minusDays(7), "advent24" to advent)) {
                if (!today.isBefore(start.minusDays(7)) && !today.isAfter(start.plusDays(3))) return id to start
            }
        }
        return null
    }
    fun byId(id: String?) = all.firstOrNull { it.id == id }
    /** 오늘 몇째 날인지 (1부터, 날 수를 넘으면 마지막 날). */
    fun day(start: Long, today: LocalDate): Int = (today.toEpochDay() - start + 1).toInt().coerceIn(1, Int.MAX_VALUE)
    /** 오늘 분량: 남은 절 ÷ 남은 날 (올림, 하루 1절 이상). */
    fun perDay(total: Int, done: Int, daysLeft: Int): Int = if (total <= done) 0 else maxOf(1, (total - done + daysLeft - 1) / maxOf(1, daysLeft))
}

