package io.github.graviton94.todaybible.core

import java.time.LocalDate

/** 발자취 갈래 (기록 탭에서 골라 보기) · 등급 (테 색). */
enum class MedalCat { TEMPUS, SCRIPTURA, VOX, STILUS, MANUS, TRIPLEX, VERBUM, ALBUM }
enum class Tier { BRONZE, SILVER, GOLD }

/**
 * 발자취 하나. 이름 · 조건 문구는 앱 문자열(ms_<id>_name / _rule)에.
 * 1.7: 갈래 · 등급 · 숨은 메달 (받기 전엔 「?」) · 기능마다 메달 (낭독 · 타자 · 손글씨 · 말씀 곁에 · 판화 · 작정 · 나눔).
 */
enum class Milestone(val icon: String, val cat: MedalCat = MedalCat.SCRIPTURA, val tier: Tier = Tier.BRONZE, val hidden: Boolean = false) {
    BEGINNING("light", MedalCat.TEMPUS), SEVEN("seven", MedalCat.TEMPUS), DAWN("dawn", MedalCat.TEMPUS, Tier.SILVER), PASTURES("crook"), FORTY("forty", MedalCat.TEMPUS, Tier.SILVER), OLIVE("leaf"),
    HOSANNA("palm", MedalCat.TEMPUS, Tier.SILVER), TOMB("tomb", MedalCat.TEMPUS, Tier.SILVER), FIRE("flame", MedalCat.TEMPUS, Tier.SILVER), FIRSTFRUITS("wheat", MedalCat.TEMPUS, Tier.SILVER), STAR("star", MedalCat.TEMPUS, Tier.SILVER), LAMP("lamp", tier = Tier.SILVER),
    TABLETS("tablets", tier = Tier.GOLD), EPISTLES("scroll", tier = Tier.GOLD), YEAR("wreath", MedalCat.TEMPUS, Tier.GOLD), OMEGA("ao", tier = Tier.GOLD),
    // 암송 메달 (1.4): 마음에 새기기에서 "외웠어요"로 표시한 절 수
    VERSE7("verse", MedalCat.VERBUM), VERSE30("verse", MedalCat.VERBUM, Tier.SILVER), VERSE100("verse", MedalCat.VERBUM, Tier.GOLD),
    // 1.7 낭독
    VOX_PRIMA("vox", MedalCat.VOX), PSALTES("vox", MedalCat.VOX, Tier.SILVER), VOX_CLAMANTIS("vox", MedalCat.VOX, Tier.GOLD), AURORA("vox", MedalCat.VOX, Tier.SILVER), LIBER_VOCIS("vox", MedalCat.VOX, Tier.SILVER),
    // 타자
    LITTERA("type", MedalCat.STILUS), MILIA1("type", MedalCat.STILUS), MILIA2("type", MedalCat.STILUS, Tier.SILVER), MILIA3("type", MedalCat.STILUS, Tier.GOLD), SINE_MENDO("type", MedalCat.STILUS, Tier.SILVER), CUBICULUM("type", MedalCat.STILUS, Tier.SILVER),
    // 손글씨
    PRIMA_LINEA("hand", MedalCat.MANUS), TRES_CALAMI("hand", MedalCat.MANUS, Tier.SILVER), MANUS_SCRIBAE("hand", MedalCat.MANUS, Tier.GOLD), CODEX_MANU("hand", MedalCat.MANUS, Tier.SILVER),
    // 세 필사본
    FUNICULUS("triplex", MedalCat.TRIPLEX, Tier.SILVER),
    // 말씀 곁에
    MEMORIA1("note", MedalCat.VERBUM), MEMORIA2("note", MedalCat.VERBUM, Tier.SILVER), MEMORIA3("note", MedalCat.VERBUM, Tier.GOLD), AURIS("note", MedalCat.VERBUM, Tier.SILVER),
    SIGNA("note", MedalCat.VERBUM), CATENA("note", MedalCat.VERBUM), NOMINA("note", MedalCat.VERBUM, Tier.SILVER),
    // 판화 · 작정 · 나눔
    ALBUM1("plate", MedalCat.ALBUM), ALBUM2("plate", MedalCat.ALBUM, Tier.SILVER), ALBUM3("plate", MedalCat.ALBUM, Tier.GOLD), VOTUM_PRIMUM("votum", MedalCat.ALBUM), ANNUS_ECCL("votum", MedalCat.ALBUM, Tier.GOLD),
    SEMEN1("share", MedalCat.ALBUM), SEMEN2("share", MedalCat.ALBUM, Tier.SILVER),
    // 숨은 메달
    LUCERNA("night", MedalCat.TEMPUS, Tier.SILVER, hidden = true), NOX_SANCTA("night", MedalCat.TEMPUS, Tier.GOLD, hidden = true);

    companion object {
        /** 암송 메달마다 외울 절 수. */
        val MEMORY = mapOf(VERSE7 to 7, VERSE30 to 30, VERSE100 to 100)
    }
}

/** 필사본 단계 (1.7): 방식마다 다 쓴 장 수로 I–VI. 한 절에 드는 품이 달라 (낭독 < 타자 < 손글씨) 문턱을 다르게. */
object Levels {
    val NAMES = listOf("INITIUM", "ROTULUS", "CODEX", "SCRIBA", "LIBRARIUS", "BIBLIA TOTA")
    fun steps(m: Mode): List<Int> = when (m) {
        Mode.ALOUD -> listOf(1, 20, 100, 300, 600, 1189)
        Mode.TYPE -> listOf(1, 10, 40, 120, 300, 1189)
        Mode.PAPER -> listOf(1, 5, 20, 60, 150, 1189)
    }
    /** 이룬 단계 (0 = 아직, 1..6). */
    fun level(m: Mode, chapters: Int): Int = steps(m).count { chapters >= it }
    /** 다음 단계까지 남은 장 (다 이뤘으면 0). */
    fun left(m: Mode, chapters: Int): Int = steps(m).firstOrNull { chapters < it }?.let { it - chapters } ?: 0
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
        /** 1.7 기능 메달에 필요한 기록 (앱이 모아 줌). */
        val more: More = More(),
    )
    /**
     * 필사본별 다 쓴 장 · 권, 타자로 쓴 글자 수, 기능마다 센 수 (counts: audiobook · notes_pdf · focus_ch · typo_free_ch · related · dict · share),
     * 손글씨로 다 쓴 장의 펜들, 걸린 판화 · 모든 판화, 마음에 남은 한 줄 수, 설교 노트 연속 주일, 책갈피 · 형광펜, 마친 작정 id, 한밤 (0–4시) 에 마친 장이 있었는지, 성탄 아침 누가 2장.
     */
    data class More(
        val chaptersBy: Map<Mode, Set<Int>> = emptyMap(), val booksBy: Map<Mode, Set<Int>> = emptyMap(),
        val typedLetters: Int = 0, val counts: Map<String, Int> = emptyMap(), val pens: Set<String> = emptySet(),
        val platesHung: Int = 0, val platesAll: Int = Int.MAX_VALUE, val reflections: Int = 0, val sermonRun: Int = 0,
        val bookmarks: Int = 0, val highlights: Int = 0, val votums: Set<String> = emptySet(),
        val nightChapter: Boolean = false, val christmasLuke: Boolean = false,
    )
    /** 셀 수 있는 메달의 (지금, 목표). 날 수 · 암송은 앱이 따로 셈. */
    fun progress(m: Milestone, s: State): Pair<Int, Int>? {
        val x = s.more; fun ch(md: Mode) = x.chaptersBy[md].orEmpty()
        fun cnt(k: String) = x.counts[k] ?: 0
        return when (m) {
            Milestone.PSALTES -> ch(Mode.ALOUD).count { it / 1000 == 19 } to 10
            Milestone.AURORA -> s.fills.filter { it.mode == Mode.ALOUD && java.time.Instant.ofEpochMilli(it.atMillis).atZone(java.time.ZoneId.systemDefault()).hour < 6 }.map { it.epochDay }.toSet().size to 7
            Milestone.MILIA1 -> x.typedLetters to 10_000
            Milestone.MILIA2 -> x.typedLetters to 100_000
            Milestone.MILIA3 -> x.typedLetters to 1_000_000
            Milestone.CUBICULUM -> cnt("focus_ch") to 10
            Milestone.TRES_CALAMI -> x.pens.size to 3
            Milestone.MEMORIA1 -> x.reflections to 10
            Milestone.MEMORIA2 -> x.reflections to 50
            Milestone.MEMORIA3 -> x.reflections to 200
            Milestone.AURIS -> x.sermonRun to 4
            Milestone.SIGNA -> minOf(x.bookmarks, 10) + minOf(x.highlights, 30) to 40
            Milestone.CATENA -> cnt("related") to 30
            Milestone.NOMINA -> cnt("dict") to 50
            Milestone.ALBUM1 -> x.platesHung to 10
            Milestone.ALBUM2 -> x.platesHung to 50
            Milestone.ALBUM3 -> x.platesHung to x.platesAll
            Milestone.ANNUS_ECCL -> x.votums.count { it in ECCL } to ECCL.size
            Milestone.SEMEN1 -> cnt("share") to 1
            Milestone.SEMEN2 -> cnt("share") to 10
            else -> null
        }
    }
    /** 교회력 한 바퀴에 드는 작정. */
    val ECCL = setOf("thanks21", "advent24", "lent40", "pente50")
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
        // 1.7 기능 메달: 방식마다 첫 절은 그 절을 쓴 날, 나머지는 이룬 것을 처음 본 날 (오늘)
        val x = s.more
        Mode.entries.forEach { md -> s.fills.filter { it.mode == md }.minByOrNull { it.atMillis }?.let { f ->
            out[when (md) { Mode.ALOUD -> Milestone.VOX_PRIMA; Mode.TYPE -> Milestone.LITTERA; Mode.PAPER -> Milestone.PRIMA_LINEA }] = f.epochDay } }
        if (x.booksBy[Mode.ALOUD].orEmpty().isNotEmpty()) out[Milestone.VOX_CLAMANTIS] = today
        if (x.booksBy[Mode.PAPER].orEmpty().isNotEmpty()) out[Milestone.MANUS_SCRIBAE] = today
        if ((x.counts["audiobook"] ?: 0) > 0) out[Milestone.LIBER_VOCIS] = today
        if ((x.counts["notes_pdf"] ?: 0) > 0) out[Milestone.CODEX_MANU] = today
        if ((x.counts["typo_free_ch"] ?: 0) > 0) out[Milestone.SINE_MENDO] = today
        val all = Mode.entries.map { x.chaptersBy[it].orEmpty() }
        if (all[0].any { c -> all.all { c in it } }) out[Milestone.FUNICULUS] = today
        if (x.votums.isNotEmpty()) out[Milestone.VOTUM_PRIMUM] = today
        if (x.nightChapter) out[Milestone.LUCERNA] = today
        if (x.christmasLuke) out[Milestone.NOX_SANCTA] = today
        Milestone.entries.forEach { m -> progress(m, s)?.let { (n, goal) -> if (goal in 1..n) out[m] = today } }
        return out
    }
}
