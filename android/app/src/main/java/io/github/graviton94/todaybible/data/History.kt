package io.github.graviton94.todaybible.data

import android.content.Context

/**
 * 역사 속의 성경 (1.5 C6): 판화 · 사료마다 세 칸 — PICTURA 그림 · SCAENA 장면 · HISTORIA 역사. 글은 assets/history/stories.tsv (ko · en).
 * 사료 (사본 · 지도 · 유물) 는 판화와 같은 목록 (plates.tsv, set = "src") 에 있어 화첩에 섞여 걸리고, 글 속 [[id|보일 글]] 링크로도 열려요.
 */
object History {
    /** 사료 표시 (plates.tsv 의 set 칸). */
    const val SOURCE = "src"

    data class Story(val pictura: String, val scaena: String, val historia: String)
    /** 글 한 조각: link 가 있으면 그 사료 id. */
    data class Part(val text: String, val link: String?)

    @Volatile private var cache: Map<String, Story>? = null

    private fun load(ctx: Context): Map<String, Story> = cache ?: runCatching {
        ctx.assets.open("history/stories.tsv").bufferedReader().readLines().filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { l ->
            val p = l.split('\t'); if (p.size < 5) null else "${p[0]}:${p[1]}" to Story(p[2], p[3], p[4])
        }.toMap()
    }.getOrDefault(emptyMap()).also { cache = it }

    /** 그 판화 · 사료의 글 (앱이 영어면 영어, 없으면 다른 말이라도). */
    fun story(ctx: Context, id: String, korean: Boolean): Story? = load(ctx).let { m -> m["$id:${if (korean) "ko" else "en"}"] ?: m["$id:${if (korean) "en" else "ko"}"] }

    private val LINK = Regex("""\[\[([a-z0-9_]+)\|([^\]]+)]]""")

    /** 글을 조각으로: 링크 [[id|글]] 은 따로. */
    fun parts(text: String): List<Part> {
        val out = ArrayList<Part>(); var at = 0
        for (m in LINK.findAll(text)) {
            if (m.range.first > at) out += Part(text.substring(at, m.range.first), null)
            out += Part(m.groupValues[2], m.groupValues[1]); at = m.range.last + 1
        }
        if (at < text.length) out += Part(text.substring(at), null)
        return out
    }

    /** 링크 표기를 뺀 글 (화면 읽기 · 나누기). */
    fun plain(text: String): String = LINK.replace(text) { it.groupValues[2] }
}
