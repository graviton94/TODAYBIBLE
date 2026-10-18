package io.github.graviton94.todaybible.core

/**
 * 한 번역의 진행: 채운 절 집합에서 장 · 권 완성을 계산.
 * 1.7 세 필사본: 낭독본 · 타자본 · 손글씨본은 진도가 따로예요 (mode 를 주면 그 필사본만). mode 없이 부르면 어느 방식이든 (판화 · 발자취 · 작정 · 날).
 */
class Progress(private val fills: List<Fill>) {
    private val byTr = fills.groupBy { it.translation }.mapValues { (_, v) -> v.map { it.key.raw }.toHashSet() }
    private val byMode = fills.groupBy { it.translation to it.mode }.mapValues { (_, v) -> v.map { it.key.raw }.toHashSet() }
    fun filled(tr: Translation): Set<Int> = byTr[tr].orEmpty()
    fun filled(tr: Translation, mode: Mode): Set<Int> = byMode[tr to mode].orEmpty()
    private fun set(tr: Translation, mode: Mode?) = if (mode == null) filled(tr) else filled(tr, mode)
    fun count(tr: Translation): Int = filled(tr).size
    fun count(tr: Translation, mode: Mode): Int = filled(tr, mode).size
    fun isFilled(tr: Translation, key: VerseKey) = key.raw in filled(tr)
    fun isFilled(tr: Translation, key: VerseKey, mode: Mode?) = key.raw in set(tr, mode)
    fun chapterDone(tr: Translation, text: BookText, chapter: Int, mode: Mode? = null): Boolean { val f = set(tr, mode); return text.fillable(chapter).all { VerseKey(text.book, chapter, it).raw in f } }
    fun chapterFraction(tr: Translation, text: BookText, chapter: Int, mode: Mode? = null): Float {
        val f = text.fillable(chapter); if (f.isEmpty()) return 1f
        val s = set(tr, mode)
        return f.count { VerseKey(text.book, chapter, it).raw in s } / f.size.toFloat()
    }
    fun bookDone(tr: Translation, text: BookText, mode: Mode? = null) = (1..text.chapterCount).all { chapterDone(tr, text, it, mode) }
    fun modeCount(tr: Translation, mode: Mode): Int = count(tr, mode)
    /** 다음에 쓸 절: 이 장에서 (그 필사본으로) 아직 안 쓴 첫 절. 다 썼으면 null. */
    fun nextVerse(tr: Translation, text: BookText, chapter: Int, mode: Mode? = null): Int? { val s = set(tr, mode); return text.fillable(chapter).firstOrNull { VerseKey(text.book, chapter, it).raw !in s } }
    fun days(): Set<Long> = fills.map { it.epochDay }.toSet()
    /** 다 쓴 장 수 (그 필사본으로). */
    fun chaptersDone(tr: Translation, mode: Mode, texts: (Int) -> BookText): Int =
        filled(tr, mode).map { VerseKey(it).book }.toSet().sumOf { b -> val t = texts(b); (1..t.chapterCount).count { chapterDone(tr, t, it, mode) } }
}

/** 판화 조각모음: 장(또는 장면 구간)을 채운 비율만큼 조각이 하나씩 드러남. 퍼즐 조작은 없음. */
object Pieces {
    const val COLS = 3
    const val ROWS = 4
    const val COUNT = COLS * ROWS
    /** 드러난 조각 수 (다 쓰면 전부). */
    fun revealed(fraction: Float): Int = if (fraction >= 1f) COUNT else (fraction * COUNT).toInt().coerceIn(0, COUNT - 1)
    /** 조각이 드러나는 순서 (판화마다 고정된 순서, 가운데부터 바깥으로 흩어지듯). */
    fun order(seed: Int): List<Int> {
        val r = java.util.Random(seed.toLong())
        val cx = (COLS - 1) / 2f; val cy = (ROWS - 1) / 2f
        return (0 until COUNT).sortedBy { i -> val x = i % COLS - cx; val y = i / COLS - cy; x * x + y * y + r.nextFloat() * 1.6f }
    }
}
