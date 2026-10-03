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

/** 오늘의 분량: 절 수 (CHAPTER = 하루 한 장). */
object Goal {
    const val CHAPTER = -1
    val choices = listOf(1, 5, 10, CHAPTER)
    /** 분량을 채웠는지: 절이면 오늘 쓴 절 수, 장이면 오늘 마친 장이 있는지. */
    fun met(goal: Int, todayVerses: Int, todayChapters: Int): Boolean = if (goal == CHAPTER) todayChapters > 0 else todayVerses >= goal
    /** 이 분량이면 몇 날이 걸리는지 (절 수 기준, 장이면 장 수). */
    fun days(goal: Int, verses: Int, chapters: Int): Int = if (goal == CHAPTER) chapters else (verses + goal - 1) / goal
}

