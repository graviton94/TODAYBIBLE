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

/** 낱말로 찾은 절 하나. */
data class Hit(val book: Int, val chapter: Int, val verse: Int, val text: String)

object Search {
    /** 낱말(들)이 모두 든 절을 성경 차례대로. 맨글자 기준, 대소문자 · 띄어쓰기 차이는 무시. */
    fun find(books: Sequence<BookText>, query: String, limit: Int = 200): List<Hit> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val out = ArrayList<Hit>()
        for (b in books) for ((ci, ch) in b.chapters.withIndex()) for ((vi, raw) in ch.withIndex()) {
            if (raw.isBlank()) continue
            val plain = Markup.plain(raw); val low = plain.lowercase()
            if (words.all { low.contains(it) }) { out += Hit(b.book, ci + 1, vi + 1, plain); if (out.size >= limit) return out }
        }
        return out
    }
}

/** 마음에 새기기 (A3): 낱말을 조금씩 가려 보며 되뇌어요. 맞고 틀림은 없어요. */
object Memorize {
    data class Word(val text: String, val shown: String) { val hidden get() = text != shown }
    const val LEVELS = 3
    /** level 0 = 다 보임 · 1 = 첫 글자만 · 2 = 자리만. 문장부호는 늘 보여요. 한글은 ○, 그 밖은 _ 로 가려요. */
    fun words(plain: String, level: Int): List<Word> = plain.split(' ').filter { it.isNotEmpty() }.map { w ->
        if (level <= 0) Word(w, w) else {
            var seen = 0
            Word(w, buildString {
                for (ch in w) {
                    if (!ch.isLetterOrDigit()) { append(ch); continue }
                    seen++
                    append(if (level == 1 && seen == 1) ch else if (ch in '가'..'힣') '○' else '_')
                }
            })
        }
    }
}
