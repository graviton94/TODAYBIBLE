package io.github.graviton94.todaybible.core

/**
 * 한 권의 본문. TSV 한 줄 = "장\t절\t본문". 빈 본문 = 이 번역에 없는 절(이전 절에 합쳐졌거나 빠짐). 채울 대상이 아님.
 */
class BookText(val book: Int, val chapters: List<List<String>>) {
    val chapterCount: Int get() = chapters.size
    fun verseCount(chapter: Int): Int = chapters[chapter - 1].size
    fun verse(chapter: Int, verse: Int): String = chapters[chapter - 1][verse - 1]
    /** 채울 수 있는 절 (빈 절 제외). */
    fun fillable(chapter: Int): List<Int> = chapters[chapter - 1].indices.filter { chapters[chapter - 1][it].isNotBlank() }.map { it + 1 }
    val fillableTotal: Int get() = chapters.sumOf { ch -> ch.count { it.isNotBlank() } }

    companion object {
        fun parse(book: Int, tsv: String): BookText {
            val map = sortedMapOf<Int, java.util.TreeMap<Int, String>>()
            tsv.lineSequence().filter { it.isNotEmpty() }.forEach { line ->
                val p = line.split('\t', limit = 3)
                map.getOrPut(p[0].toInt()) { java.util.TreeMap() }[p[1].toInt()] = p.getOrElse(2) { "" }
            }
            return BookText(book, map.values.map { it.values.toList() })
        }
    }
}

/** 본문 한 조각: 보통 글자, KJV 첨가어(이탤릭), 하나님의 이름(스몰캡). */
data class Span(val text: String, val italic: Boolean = false, val smallCaps: Boolean = false)

object Markup {
    private val divine = Regex("\\b(LORD|GOD|JEHOVAH)\\b")
    /** KJV 의 {첨가어} · LORD 를 조각으로. ¶ 는 단락 표시로 앞에서 떼어 냄. */
    fun spans(text: String): List<Span> {
        val out = mutableListOf<Span>()
        var rest = text.removePrefix("¶ ").removePrefix("¶")
        var italic = false
        val buf = StringBuilder()
        fun flush() {
            if (buf.isEmpty()) return
            val s = buf.toString(); buf.clear()
            var last = 0
            divine.findAll(s).forEach { m ->
                if (m.range.first > last) out += Span(s.substring(last, m.range.first), italic)
                out += Span(m.value.lowercase().replaceFirstChar { it.uppercase() }, italic, smallCaps = true)
                last = m.range.last + 1
            }
            if (last < s.length) out += Span(s.substring(last), italic)
        }
        for (c in rest) when (c) { '{' -> { flush(); italic = true }; '}' -> { flush(); italic = false }; else -> buf.append(c) }
        flush()
        return out
    }
    fun paragraph(text: String): Boolean = text.startsWith("¶")
    /** 화면 · 판정에 쓰는 맨글자 (표시 기호 제거). */
    fun plain(text: String): String = text.removePrefix("¶ ").removePrefix("¶").replace("{", "").replace("}", "")
}
