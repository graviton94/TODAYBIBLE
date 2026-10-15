package io.github.graviton94.todaybible.data

import android.content.Context
import io.github.graviton94.todaybible.core.VerseKey

/**
 * 관련 구절 · 성경 사전 (1.6). 자료는 assets/ref/ (scripts/build_refdata.py 가 만듦), 처음 쓸 때 한 번 읽어 메모리에.
 *  · related.tsv — 절 → 관련 구절 (OpenBible.info, 표 순). 장절은 VerseKey 숫자, 범위는 a-b. 번역과 상관없이 같은 자료.
 *  · names.tsv — 사람 · 땅 · 낱말 (영어 풀이). 나오는 절 목록으로 "이 절의 이름" 을 거꾸로 찾아요.
 * 한국어 풀이 (names_ko.tsv) 는 1.6.x 에: 그때까지 사전은 영어 화면에서만.
 */
object Refs {
    /** 관련 구절 하나: from..to (한 절이면 같음). */
    data class Span(val from: VerseKey, val to: VerseKey)

    /** 사전 한 항목. kind = P 사람 · L 땅 · T 낱말. body 속 장절은 [[@a-b|글]]. */
    data class Entry(val id: String, val kind: Char, val name: String, val orig: String, val brief: String, val meaning: String, val body: String, val refs: IntArray)

    @Volatile private var related: Map<Int, List<Span>>? = null
    @Volatile private var entries: List<Entry>? = null
    @Volatile private var byVerse: Map<Int, IntArray>? = null

    private fun span(t: String): Span? = runCatching {
        val p = t.split('-'); Span(VerseKey(p[0].toInt()), VerseKey(p.last().toInt()))
    }.getOrNull()

    private fun loadRelated(ctx: Context): Map<Int, List<Span>> = related ?: synchronized(this) {
        related ?: runCatching {
            val m = HashMap<Int, List<Span>>(30_000)
            ctx.assets.open("ref/related.tsv").bufferedReader().forEachLine { l ->
                if (l.isEmpty() || l[0] == '#') return@forEachLine
                val tab = l.indexOf('\t'); if (tab < 0) return@forEachLine
                m[l.substring(0, tab).toInt()] = l.substring(tab + 1).split(',').mapNotNull(::span)
            }
            m as Map<Int, List<Span>>
        }.getOrDefault(emptyMap()).also { related = it }
    }

    private fun loadEntries(ctx: Context): List<Entry> = entries ?: synchronized(this) {
        entries ?: runCatching {
            ctx.assets.open("ref/names.tsv").bufferedReader().readLines().filter { it.isNotEmpty() && it[0] != '#' }.mapNotNull { l ->
                val p = l.split('\t'); if (p.size < 8) return@mapNotNull null
                Entry(p[0], p[1].firstOrNull() ?: 'T', p[2], p[3], p[4], p[5], p[6], p[7].split(' ').mapNotNull { it.toIntOrNull() }.toIntArray())
            }
        }.getOrDefault(emptyList()).also { list ->
            val rev = HashMap<Int, MutableList<Int>>(40_000)
            list.forEachIndexed { i, e -> e.refs.forEach { rev.getOrPut(it) { ArrayList(2) }.add(i) } }
            byVerse = rev.mapValues { it.value.toIntArray() }
            entries = list
        }
    }

    /** 그 절의 관련 구절 (관련 높은 순, 많아야 8). */
    fun related(ctx: Context, key: VerseKey): List<Span> = loadRelated(ctx)[key.raw].orEmpty()

    /** 사전이 있는 화면 언어인가 (1.6.0: 영어만). */
    fun hasDictionary(korean: Boolean): Boolean = !korean

    /** 이 절에 나오는 이름 · 땅 · 낱말: 사람 · 땅을 본문에 나오는 차례로, 낱말은 그 뒤 (많아야 8). englishText 는 WEB 본문 (차례 맞추기). */
    fun inVerse(ctx: Context, key: VerseKey, englishText: String): List<Entry> {
        val all = loadEntries(ctx); val idx = byVerse?.get(key.raw) ?: return emptyList()
        val list = idx.map { all[it] }
        val low = englishText.lowercase()
        fun at(e: Entry) = low.indexOf(e.name.lowercase().substringBefore(' ')).let { if (it < 0) Int.MAX_VALUE else it }
        return (list.filter { it.kind != 'T' }.sortedBy(::at) + list.filter { it.kind == 'T' }.sortedBy(::at)).distinctBy { it.id }.take(8)
    }

    fun entry(ctx: Context, id: String): Entry? = loadEntries(ctx).firstOrNull { it.id == id }

    /** 사전 찾기: 이름이 그 말로 시작하는 것 먼저, 그다음 안에 든 것. 같은 이름은 많이 나오는 사람 먼저. */
    fun search(ctx: Context, q: String, max: Int = 60): List<Entry> {
        val w = q.trim().lowercase(); if (w.length < 2) return emptyList()
        val all = loadEntries(ctx)
        val starts = all.filter { it.name.lowercase().startsWith(w) }
        val inside = all.filter { it.name.lowercase().contains(w) && !it.name.lowercase().startsWith(w) }
        return (starts.sortedWith(compareBy({ it.name.length }, { -it.refs.size })) + inside.sortedBy { -it.refs.size }).take(max)
    }

    /** 풀이 글을 조각으로: 장절 링크 [[@a-b|글]] 은 따로. */
    data class Part(val text: String, val span: Span?)
    private val LINK = Regex("""\[\[@([0-9-]+)\|([^\]]+)]]""")
    fun parts(text: String): List<Part> {
        val out = ArrayList<Part>(); var at = 0
        for (m in LINK.findAll(text)) {
            if (m.range.first > at) out += Part(text.substring(at, m.range.first), null)
            out += Part(m.groupValues[2], span(m.groupValues[1])); at = m.range.last + 1
        }
        if (at < text.length) out += Part(text.substring(at), null)
        return out
    }

    /** 풀이 속 장절 (처음 나온 차례, 겹친 것 빼고). */
    fun spansIn(text: String): List<Span> = LINK.findAll(text).mapNotNull { span(it.groupValues[1]) }.distinctBy { it.from.raw }.toList()
}
