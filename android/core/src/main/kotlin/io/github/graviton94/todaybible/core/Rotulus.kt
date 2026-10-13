package io.github.graviton94.todaybible.core

/** 옮겨 쓴 두루마리 (1.4): 길이 (m) 를 익숙한 표지에 견줘요. 표지 이름은 앱 글에서 (rot_<id>). */
object Rotulus {
    data class Mark(val id: String, val meters: Int)
    val MARKS = listOf(Mark("bus", 12), Mark("field", 105), Mark("eiffel", 330), Mark("lotte", 555), Mark("km1", 1000), Mark("km5", 5000), Mark("km10", 10000))

    /** 지난 표지 · 다음 표지 (끝을 넘었으면 다음 = null). */
    fun around(m: Float): Pair<Mark?, Mark?> = MARKS.lastOrNull { it.meters <= m } to MARKS.firstOrNull { it.meters > m }

    /** 두루마리 눈금의 끝: 다음 표지를 조금 넘는 곳 (지금 자리가 너무 끝에 붙지 않게). */
    fun scaleEnd(m: Float): Float = (around(m).second?.meters?.toFloat() ?: m) * 1.1f
}
