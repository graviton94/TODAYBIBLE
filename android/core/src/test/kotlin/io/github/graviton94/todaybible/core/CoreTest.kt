package io.github.graviton94.todaybible.core

import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoreTest {
    private val assets = File(System.getProperty("assetsDir"), "bible")
    private fun book(tr: String, i: Int) = BookText.parse(i, File(assets, "$tr/%02d.tsv".format(i + 1)).readText())

    @Test fun canonHas66Books() {
        assertEquals(66, Canon.books.size)
        assertEquals("마태복음", Canon.books[39].ko)
        assertEquals(39, Canon.books.count { it.oldTestament })
    }

    @Test fun kjvIsComplete() {
        val all = (0 until 66).map { book("kjv", it) }
        assertEquals(1189, all.sumOf { it.chapterCount })
        all.forEachIndexed { i, t -> assertEquals(Canon.books[i].chapters, t.chapterCount, Canon.books[i].osis) }
        assertEquals(Translation.KJV.total, all.sumOf { it.fillableTotal })
    }

    @Test fun krvIsComplete() {
        val all = (0 until 66).map { book("krv", it) }
        assertEquals(1189, all.sumOf { it.chapterCount })
        // 개역한글 모듈: 빈 절 19개(앞 절에 합쳐졌거나 빠진 절) · 요한삼서 15절 → 채울 절 31,084
        assertEquals(Translation.KRV.total, all.sumOf { it.fillableTotal })
        all.forEachIndexed { i, t -> assertEquals(Canon.books[i].chapters, t.chapterCount, Canon.books[i].osis) }
        assertEquals("심령이 가난한 자는 복이 있나니 천국이 저희 것임이요", book("krv", 39).verse(5, 3))
    }

    @Test fun verseKeyRoundTrip() {
        val k = VerseKey(39, 5, 3)
        assertEquals(40_005_003, k.raw); assertEquals(39, k.book); assertEquals(5, k.chapter); assertEquals(3, k.verse)
    }

    @Test fun kjvMarkup() {
        val s = Markup.spans("The LORD {is} my shepherd; I shall not want.")
        assertTrue(s.any { it.smallCaps && it.text == "Lord" })
        assertTrue(s.any { it.italic && it.text == "is" })
        assertEquals("The LORD is my shepherd; I shall not want.", Markup.plain("The LORD {is} my shepherd; I shall not want."))
    }

    @Test fun typingIgnoresSpacesAndPunctuation() {
        val v = "심령이 가난한 자는 복이 있나니 천국이 저희 것임이요"
        assertTrue(TypeJudge.done(v, "심령이가난한자는 복이있나니 천국이저희것임이요"))
        assertFalse(TypeJudge.done(v, "심령이 가난한 자는"))
        val m = TypeJudge.marks("Blessed {are} the poor", "blessed arx")
        assertEquals(TypeJudge.Mark.WRONG, m[Markup.plain("Blessed {are} the poor").indexOf("are") + 2])
        assertTrue(TypeJudge.done("Blessed {are} the poor in spirit:", "blessed are the poor in spirit"))
    }

    @Test fun easterDates() {
        assertEquals(LocalDate.of(2026, 4, 5), ChurchYear.easter(2026))
        assertEquals(LocalDate.of(2027, 3, 28), ChurchYear.easter(2027))
        assertEquals(LocalDate.of(2027, 2, 10), ChurchYear.ashWednesday(2027))
        assertEquals(LocalDate.of(2026, 11, 15), ChurchYear.thanksgiving(2026, korea = true))
        assertEquals(LocalDate.of(2026, 11, 26), ChurchYear.thanksgiving(2026, korea = false))
    }

    @Test fun progressAndPieces() {
        val ps23 = book("krv", 18)
        val fills = (1..6).map { Fill(Translation.KRV, VerseKey(18, 23, it), Mode.TYPE, 20000L + it, 0L) }
        val p = Progress(fills)
        assertTrue(p.chapterDone(Translation.KRV, ps23, 23))
        assertFalse(p.chapterDone(Translation.KJV, ps23, 23))
        assertEquals(Pieces.COUNT, Pieces.revealed(1f))
        assertEquals(6, Pieces.revealed(0.5f))
        assertEquals((0 until Pieces.COUNT).toSet(), Pieces.order(7).toSet())
        val earned = Milestones.earned(Milestones.State(fills, setOf(19 * 1000 + 23), emptySet(), korea = true))
        assertTrue(Milestone.PASTURES in earned && Milestone.BEGINNING in earned)
    }

    @Test fun weekComplete() {
        val sun = LocalDate.of(2026, 10, 4)
        val days = (1..5).map { sun.plusDays(it.toLong()).toEpochDay() }.toSet()
        assertTrue(Presence.weekComplete(days, sun.plusDays(3)))
        assertFalse(Presence.weekComplete(days.drop(1).toSet(), sun))
    }

    @Test fun streakSkipsEmptySundays() {
        val sat = java.time.LocalDate.of(2026, 10, 3) // 토요일
        fun d(x: java.time.LocalDate) = x.toEpochDay()
        // 월~토 + 지난 주 금·토, 주일(9.27)은 비어 있음
        val days = (0L..5L).map { d(sat.minusDays(it)) }.toSet() + d(sat.minusDays(7)) + d(sat.minusDays(8))
        assertEquals(8, Presence.streak(days, sat))
        // 오늘 아직 안 썼으면 어제부터
        assertEquals(7, Presence.streak(days - d(sat), sat))
        // 평일 하루 빠지면 끊김
        assertEquals(2, Presence.streak(days - d(sat.minusDays(2)), sat))
        assertEquals(0, Presence.streak(emptySet(), sat))
    }

    @Test fun feastsAndGoal() {
        val up = Feasts.upcoming(LocalDate.of(2027, 3, 1), korea = true)
        assertEquals(Feast.PALM to LocalDate.of(2027, 3, 21), up[0])
        assertEquals(Feast.EASTER to LocalDate.of(2027, 3, 28), up[1])
        assertEquals(Feast.PENTECOST to LocalDate.of(2027, 5, 16), up[2])
        assertEquals(Feast.THANKSGIVING to LocalDate.of(2027, 11, 21), up[3])
        // 오늘이 성탄절이면 맨 앞
        assertEquals(Feast.CHRISTMAS, Feasts.upcoming(LocalDate.of(2026, 12, 25), true)[0].first)
        assertTrue(Goal.met(5, 5, 0)); assertFalse(Goal.met(5, 4, 0)); assertTrue(Goal.met(Goal.CHAPTER, 0, 1))
        assertEquals(136, Goal.days(5, 678, 16))
        assertTrue(Goal.met(Goal.CHAPTER, 30, 0)); assertFalse(Goal.met(Goal.TWO, 10, 1)); assertTrue(Goal.met(Goal.TWO, 3, 2))
        assertEquals(8, Goal.days(Goal.TWO, 678, 16))
        // 2026 부활절 4월 5일 → 재의 수요일 2월 18일, 종려주일 3월 29일, 대림절 첫 주일 11월 29일
        assertEquals("lent40" to java.time.LocalDate.of(2026, 2, 18), Plans.seasonal(java.time.LocalDate.of(2026, 2, 14)))
        assertEquals("holy8", Plans.seasonal(java.time.LocalDate.of(2026, 3, 27))?.first)
        assertEquals("advent24" to java.time.LocalDate.of(2026, 11, 29), Plans.seasonal(java.time.LocalDate.of(2026, 11, 25)))
        assertEquals(null, Plans.seasonal(java.time.LocalDate.of(2026, 7, 1)))
    }

    @Test fun longestStreakAndPlans() {
        val sat = LocalDate.of(2026, 10, 3)
        val days = (0L..5L).map { sat.minusDays(it).toEpochDay() }.toSet() + sat.minusDays(7).toEpochDay() + sat.minusDays(8).toEpochDay() + sat.minusDays(30).toEpochDay()
        assertEquals(8, Presence.longestStreak(days))
        assertEquals(0, Presence.longestStreak(emptySet()))
        val mark = Plans.byId("mark30")!!
        assertEquals(16, mark.chapters.size)
        assertEquals(260, Plans.byId("nt365")!!.chapters.size)
        assertEquals(23, Plans.perDay(678, 0, 30))
        assertEquals(0, Plans.perDay(678, 678, 3))
        assertEquals(1, Plans.day(sat.toEpochDay(), sat))
    }

    @Test fun reciteFollowsSpeech() {
        val v = "여호와는 나의 목자시니 내가 부족함이 없으리로다"
        assertEquals(0, Recite.lit(v, ""))
        assertEquals("여호와는 나의".length, Recite.lit(v, "여호와는 나의"))
        // 띄어쓰기 · 한 글자 잘못 알아들어도 따라감
        assertTrue(Recite.lit(v, "여호와는나의 목자 시니 내가") >= "여호와는 나의 목자시니 내가".length - 1)
        assertTrue(Recite.done(v, "여호와는 나의 목자시니 내가 부족함이 없으리로다"))
        assertTrue(Recite.done(v, "여호와는 나의 목자시니 내가 부족함이 없으리"))
        assertFalse(Recite.done(v, "여호와는 나의 목자시니"))
        assertTrue(Recite.done("And he goeth up into a mountain", "and he goes up into a mountain"))
    }

    @Test fun referenceParsing() {
        assertEquals(Triple(42, 3, 16), Reference.parse("요 3:16"))
        assertEquals(Triple(42, 3, 16), Reference.parse("요한복음 3장 16절"))
        assertEquals(Triple(42, 3, 16), Reference.parse("John 3:16"))
        assertEquals(Triple(18, 23, null), Reference.parse("시 23"))
        assertEquals(Triple(18, 23, null), Reference.parse("ps23"))
        assertEquals(Triple(45, 13, 4), Reference.parse("고전 13:4"))
        assertEquals(Triple(61, 4, 8), Reference.parse("요일 4:8"))
        assertEquals(Triple(8, 17, null), Reference.parse("삼상 17장"))
        assertEquals(Triple(0, 1, 1), Reference.parse("Genesis 1:1"))
        assertEquals(null, Reference.parse("요 30:1"))
        assertEquals(null, Reference.parse("안녕"))
    }

    @Test fun leadingPunctuationFollowsTyping() {
        val m = TypeJudge.marks("“주는", "주")
        assertEquals(TypeJudge.Mark.OK, m[0])
        assertEquals(TypeJudge.Mark.PENDING, TypeJudge.marks("“주는", "")[0])
    }

    @Test fun phrasesFitOneLine() {
        val v = "여호와는 나의 목자시니 내가 부족함이 없으리로다"
        val fit = Recite.fit(v, listOf(v.indices)) { it.last - it.first + 1 <= 10 }
        assertTrue(fit.size > 1)
        assertTrue(fit.all { it.last - it.first + 1 <= 10 })
        assertEquals(v.replace(" ", ""), fit.joinToString("") { v.substring(it.first, it.last + 1).replace(" ", "") })
    }

    @Test fun reciteMissedAndPhrases() {
        val v = "그가 나를 푸른 초장에 누이시며 쉴만한 물 가으로 인도하시는도다"
        assertTrue(Recite.missed(v, v).isEmpty())
        val miss = Recite.missed(v, "그가 나를 푸른 초장에 누이시며 쉴만한 인도하시는도다")
        assertTrue(miss.any { v.substring(it.first, it.last + 1) == "가으로" })
        val ps = Recite.phrases(v).map { v.substring(it.first, it.last + 1) }
        assertEquals(v.replace(" ", ""), ps.joinToString("").replace(" ", ""))
        assertTrue(ps.size >= 2)
        assertTrue(ps.first().endsWith("누이시며"))
    }

    @Test fun hangulComposing() {
        assertEquals(TypeJudge.Mark.COMPOSING, TypeJudge.marks("또 산에", "ㄸ")[0])
        assertEquals(TypeJudge.Mark.WRONG, TypeJudge.marks("또 산에", "도")[0])          // ㄸ 자리에 ㄷ 은 틀림
        assertEquals(TypeJudge.Mark.COMPOSING, TypeJudge.marks("가방", "갑")[0])         // 받침이 다음 글자로 넘어갈 자리
        assertEquals(TypeJudge.Mark.COMPOSING, TypeJudge.marks("왜", "오")[0])           // 겹모음 조합 중
        assertEquals(TypeJudge.Mark.COMPOSING, TypeJudge.marks("닭", "달")[0])           // 겹받침 조합 중
        assertEquals(TypeJudge.Mark.WRONG, TypeJudge.marks("가방", "나")[0])
        assertEquals(listOf(TypeJudge.Mark.OK, TypeJudge.Mark.COMPOSING), TypeJudge.marks("가방", "가바").take(2))
        assertEquals(TypeJudge.Mark.WRONG, TypeJudge.marks("가방", "갑바")[0])            // 앞 글자가 굳은 뒤에는 틀림
        // 천지인: 가 = 기 + ㆍ, 거 = ㄱㆍ + ㅣ, 구 = 그 + ㆍ, ㄸ = ㄷ 세 번
        assertEquals(TypeJudge.Mark.COMPOSING, TypeJudge.marks("가방", "기")[0])
        assertEquals(TypeJudge.Mark.COMPOSING, TypeJudge.marks("거룩", "ㄱㆍ")[0])
        assertEquals(TypeJudge.Mark.COMPOSING, TypeJudge.marks("구원", "그")[0])
        assertEquals(TypeJudge.Mark.COMPOSING, TypeJudge.marks("또 산에", "ㅌ")[0])
        assertEquals(TypeJudge.Mark.OK, TypeJudge.marks("거룩", "거ㄹ")[0])
        assertEquals(TypeJudge.Mark.WRONG, TypeJudge.marks("구원", "그워")[0])            // 굳은 뒤에는 틀림
        assertEquals(listOf('가', 'ㅂ'), TypeJudge.typedAt("가방", "가ㅂ"))
        assertTrue(TypeJudge.done("또 산에", "또산에"))
    }

    @Test fun searchFindsWordsInOrder() {
        val krv = (0 until 66).asSequence().map { book("krv", it) }
        val hits = Search.find(krv, "목자")
        assertTrue(hits.any { it.book == 18 && it.chapter == 23 && it.verse == 1 })
        assertEquals(hits.sortedWith(compareBy({ it.book }, { it.chapter }, { it.verse })), hits)
        val kjv = (0 until 66).asSequence().map { book("kjv", it) }
        assertTrue(Search.find(kjv, "lord  SHEPHERD").any { it.book == 18 && it.chapter == 23 && it.verse == 1 })
        assertTrue(Search.find(kjv, "   ").isEmpty())
        assertEquals(3, Search.find(kjv, "the", limit = 3).size)
    }
}
