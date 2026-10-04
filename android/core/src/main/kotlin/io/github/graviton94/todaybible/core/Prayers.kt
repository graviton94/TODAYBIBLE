package io.github.graviton94.todaybible.core

/**
 * 기도문: 모두 성경 본문 그대로 (지어낸 문장 없음). 하루의 때마다 교회가 오래 드려 온 말씀으로.
 * 아침 · 점심 · 저녁 · 밤 (시 55:17 "저녁과 아침과 정오에") 과 주기도문 · 아론의 축복 · 시편 23편.
 * 절 번호는 개역한글과 KJV 가 같은 곳만 골랐어요 (시험으로 확인).
 */
enum class Hour { MORNING, NOON, EVENING, NIGHT }

data class Prayer(val id: String, val hour: Hour?, val passages: List<Reference.Passage>)

object Prayers {
    private fun p(b: Int, ch: Int, from: Int, to: Int) = Reference.Passage(b, ch, from, to)
    val all: List<Prayer> = listOf(
        Prayer("lords", null, listOf(p(39, 6, 9, 13))),                                          // 마 6:9–13
        Prayer("morning", Hour.MORNING, listOf(p(18, 5, 3, 3), p(24, 3, 22, 23), p(18, 143, 8, 8))), // 시 5:3 · 애 3:22–23 · 시 143:8
        Prayer("noon", Hour.NOON, listOf(p(18, 55, 16, 17), p(18, 121, 1, 8))),                  // 시 55:16–17 · 시 121
        Prayer("evening", Hour.EVENING, listOf(p(18, 141, 2, 2), p(41, 1, 46, 55))),             // 시 141:2 · 마리아의 노래
        Prayer("night", Hour.NIGHT, listOf(p(18, 4, 8, 8), p(18, 134, 1, 3), p(41, 2, 29, 32))), // 시 4:8 · 시 134 · 시므온의 노래
        Prayer("blessing", null, listOf(p(3, 6, 24, 26))),                                       // 민 6:24–26
        Prayer("shepherd", null, listOf(p(18, 23, 1, 6))),                                       // 시 23
    )
    fun byId(id: String) = all.firstOrNull { it.id == id }
    /** 지금 때: 새벽 4시 ~ 11시 아침, 11 ~ 15 점심, 15 ~ 20 저녁, 그 밖은 밤. */
    fun hourAt(h: Int): Hour = when (h) { in 4..10 -> Hour.MORNING; in 11..14 -> Hour.NOON; in 15..19 -> Hour.EVENING; else -> Hour.NIGHT }
    fun forHour(h: Hour): Prayer = all.first { it.hour == h }
}
