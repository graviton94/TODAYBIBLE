package io.github.graviton94.todaybible.core

/** 한 번역의 진행: 채운 절 집합에서 장 · 권 완성을 계산. */
class Progress(private val fills: List<Fill>) {
    private val byTr = fills.groupBy { it.translation }.mapValues { (_, v) -> v.map { it.key.raw }.toHashSet() }
    fun filled(tr: Translation): Set<Int> = byTr[tr].orEmpty()
    fun count(tr: Translation): Int = filled(tr).size
    fun isFilled(tr: Translation, key: VerseKey) = key.raw in filled(tr)
    fun chapterDone(tr: Translation, text: BookText, chapter: Int): Boolean =
        text.fillable(chapter).all { VerseKey(text.book, chapter, it).raw in filled(tr) }
    fun chapterFraction(tr: Translation, text: BookText, chapter: Int): Float {
        val f = text.fillable(chapter); if (f.isEmpty()) return 1f
        return f.count { VerseKey(text.book, chapter, it).raw in filled(tr) } / f.size.toFloat()
    }
    fun bookDone(tr: Translation, text: BookText) = (1..text.chapterCount).all { chapterDone(tr, text, it) }
    fun modeCount(tr: Translation, mode: Mode): Int = fills.filter { it.translation == tr && it.mode == mode }.map { it.key.raw }.toSet().size
    /** 다음에 쓸 절: 이 장에서 아직 안 쓴 첫 절. 다 썼으면 null. */
    fun nextVerse(tr: Translation, text: BookText, chapter: Int): Int? = text.fillable(chapter).firstOrNull { VerseKey(text.book, chapter, it).raw !in filled(tr) }
    fun days(): Set<Long> = fills.map { it.epochDay }.toSet()
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
