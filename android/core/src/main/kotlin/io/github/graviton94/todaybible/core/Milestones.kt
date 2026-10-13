package io.github.graviton94.todaybible.core

import java.time.LocalDate

/** 발자취 하나. 이름 · 조건 문구는 앱 문자열(milestone_<id>_name / _rule)에, 관련 말씀은 _verse 에. */
enum class Milestone(val icon: String) {
    BEGINNING("light"), SEVEN("seven"), DAWN("dawn"), PASTURES("crook"), FORTY("forty"), OLIVE("leaf"),
    HOSANNA("palm"), TOMB("tomb"), FIRE("flame"), FIRSTFRUITS("wheat"), STAR("star"), LAMP("lamp"),
    TABLETS("tablets"), EPISTLES("scroll"), YEAR("wreath"), OMEGA("ao"),
    // 암송 메달 (1.4): 마음에 새기기에서 "외웠어요"로 표시한 절 수
    VERSE7("verse"), VERSE30("verse"), VERSE100("verse");

    companion object {
        /** 암송 메달마다 외울 절 수. */
        val MEMORY = mapOf(VERSE7 to 7, VERSE30 to 30, VERSE100 to 100)
    }
}

/** 발자취를 얻었는지: 지금까지의 기록만 보고 판단 (순수 함수). */
object Milestones {
    data class State(
        val fills: List<Fill>,
        val doneChapters: Set<Int>,   // chapterKey (BBCCC)
        val doneBooks: Set<Int>,      // book index
        val korea: Boolean,
        /** "외웠어요"로 표시한 날들 (절마다 하나, 순서 상관없음). */
        val memorized: List<Long> = emptyList(),
    )
    private fun ck(book: Int, ch: Int) = (book + 1) * 1000 + ch
    fun earned(s: State): Map<Milestone, Long> {
        val out = linkedMapOf<Milestone, Long>()
        val days = s.fills.map { it.epochDay }.toSortedSet()
        val dates = days.map { LocalDate.ofEpochDay(it) }
        fun firstDayWhere(p: (LocalDate) -> Boolean) = dates.firstOrNull(p)?.toEpochDay()
        s.fills.minByOrNull { it.atMillis }?.let { out[Milestone.BEGINNING] = it.epochDay }
        val memo = s.memorized.sorted()
        Milestone.MEMORY.forEach { (m, n) -> if (memo.size >= n) out[m] = memo[n - 1] }
        if (days.size >= 7) out[Milestone.SEVEN] = days.elementAt(6)
        if (days.size >= 40) out[Milestone.FORTY] = days.elementAt(39)
        if (days.size >= 365) out[Milestone.YEAR] = days.elementAt(364)
        s.fills.firstOrNull { java.time.Instant.ofEpochMilli(it.atMillis).atZone(java.time.ZoneId.systemDefault()).hour < 5 }?.let { out[Milestone.DAWN] = it.epochDay }
        firstDayWhere { it == ChurchYear.palmSunday(it.year) }?.let { out[Milestone.HOSANNA] = it }
        firstDayWhere { it == ChurchYear.easter(it.year) }?.let { out[Milestone.TOMB] = it }
        firstDayWhere { it == ChurchYear.pentecost(it.year) }?.let { out[Milestone.FIRE] = it }
        firstDayWhere { it == ChurchYear.thanksgiving(it.year, s.korea) }?.let { out[Milestone.FIRSTFRUITS] = it }
        firstDayWhere { it == ChurchYear.christmas(it.year) }?.let { out[Milestone.STAR] = it }
        val today = days.lastOrNull() ?: return out
        if (ck(18, 23) in s.doneChapters) out[Milestone.PASTURES] = today
        if (ck(0, 8) in s.doneChapters) out[Milestone.OLIVE] = today
        if (ck(18, 119) in s.doneChapters) out[Milestone.LAMP] = today
        if ((0..4).all { it in s.doneBooks }) out[Milestone.TABLETS] = today
        if ((44..56).all { it in s.doneBooks }) out[Milestone.EPISTLES] = today
        if (65 in s.doneBooks) out[Milestone.OMEGA] = today
        return out
    }
}
