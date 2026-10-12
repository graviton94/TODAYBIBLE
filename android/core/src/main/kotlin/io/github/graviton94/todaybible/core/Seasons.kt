package io.github.graviton94.todaybible.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 교회력 절기 (오늘 탭 머리): 대림 · 성탄 · 주현 후 · 사순 · 부활 · 성령강림 · 성령강림 후. */
enum class Season { ADVENT, CHRISTMAS, EPIPHANY, LENT, EASTER, PENTECOST, ORDINARY }

object Seasons {
    /** 대림절 첫 주일: 성탄 전 넷째 주일. */
    fun adventStart(y: Int): LocalDate {
        val xmas = ChurchYear.christmas(y)
        val sunBefore = xmas.minusDays(((xmas.dayOfWeek.value % 7).takeIf { it != 0 } ?: 7).toLong())
        return sunBefore.minusWeeks(3)
    }

    /** 오늘의 절기 · 몇째 주 (1부터) · 다음 큰 날까지 남은 날 (그날이면 0, 없으면 null) · 다음 큰 날의 절기. */
    data class Now(val season: Season, val week: Int, val daysTo: Int?, val next: Season?)

    fun at(d: LocalDate): Now {
        val y = d.year
        val advent = adventStart(y); val xmas = ChurchYear.christmas(y)
        val easter = ChurchYear.easter(y); val ash = ChurchYear.ashWednesday(y); val pent = ChurchYear.pentecost(y)
        val epiphany = LocalDate.of(y, 1, 6)
        fun wk(from: LocalDate) = (ChronoUnit.DAYS.between(from, d) / 7).toInt() + 1
        fun to(t: LocalDate) = ChronoUnit.DAYS.between(d, t).toInt()
        return when {
            !d.isBefore(xmas) -> Now(Season.CHRISTMAS, 1, if (d == xmas) 0 else null, null)
            !d.isBefore(advent) -> Now(Season.ADVENT, wk(advent), to(xmas), Season.CHRISTMAS)
            d.isBefore(epiphany) -> Now(Season.CHRISTMAS, 2, null, null)
            d.isBefore(ash) -> Now(Season.EPIPHANY, wk(epiphany), to(ash), Season.LENT)
            d.isBefore(easter) -> Now(Season.LENT, wk(ash), to(easter), Season.EASTER)
            d.isBefore(pent) -> Now(Season.EASTER, wk(easter), if (d == easter) 0 else to(pent), if (d == easter) null else Season.PENTECOST)
            d.isBefore(pent.plusDays(7)) -> Now(Season.PENTECOST, 1, if (d == pent) 0 else null, null)
            else -> Now(Season.ORDINARY, wk(pent), to(advent), Season.ADVENT)
        }
    }

    /** 절기 말씀 (권 0부터, 장, 절): 본문은 성경 파일에서 그대로 읽어요. */
    fun verse(s: Season): Triple<Int, Int, Int> = when (s) {
        Season.ADVENT -> Triple(22, 9, 2)        // 이사야 9:2
        Season.CHRISTMAS -> Triple(41, 2, 11)    // 누가복음 2:11
        Season.EPIPHANY -> Triple(39, 2, 10)     // 마태복음 2:10
        Season.LENT -> Triple(22, 53, 5)         // 이사야 53:5
        Season.EASTER -> Triple(39, 28, 6)       // 마태복음 28:6
        Season.PENTECOST -> Triple(43, 2, 4)     // 사도행전 2:4
        Season.ORDINARY -> Triple(18, 1, 3)      // 시편 1:3
    }

    /** 절기 판화 (화첩 이름). */
    fun plate(s: Season): String = when (s) {
        Season.ADVENT -> "annunciation"; Season.CHRISTMAS -> "nativity"; Season.EPIPHANY -> "baptism"; Season.LENT -> "gethsemane"
        Season.EASTER -> "ascension"; Season.PENTECOST -> "cornelius"; Season.ORDINARY -> "sermon"
    }
}
