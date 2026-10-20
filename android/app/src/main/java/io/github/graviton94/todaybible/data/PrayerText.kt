package io.github.graviton94.todaybible.data

import android.content.Context

/**
 * 기도문 본문 (assets/prayers.json): 성경 구절을 이어 붙인 것이 아니라 그 말씀으로 드리는 기도, 한 줄에 한 호흡 · 아멘으로 마침.
 * 말은 앱 언어를 따라요 (성경 본문이 아니라서). 줄마다 낭독 음원 (assets/prayer/ko_m5/아이디_줄.m4a …), 없으면 폰 목소리.
 */
object PrayerText {
    @Volatile private var cache: Map<String, Map<String, List<String>>>? = null
    private fun all(ctx: Context): Map<String, Map<String, List<String>>> = cache ?: runCatching {
        val o = org.json.JSONObject(ctx.assets.open("prayers.json").bufferedReader().use { it.readText() })
        o.keys().asSequence().associateWith { id -> val p = o.getJSONObject(id); p.keys().asSequence().associateWith { l -> val a = p.getJSONArray(l); List(a.length()) { a.getString(it) } } }
    }.getOrDefault(emptyMap()).also { cache = it }

    fun lines(ctx: Context, id: String, korean: Boolean): List<String> = all(ctx)[id]?.get(if (korean) "ko" else "en").orEmpty()

    /** 줄마다 낭독 음원 자리 (없으면 null): 성경 낭독에서 고른 목소리의 남 · 여를 따라요 (폰 목소리면 남성). */
    fun voices(ctx: Context, id: String, korean: Boolean, male: Boolean): List<String?> {
        val dir = "prayer/${if (korean) "ko" else "en"}_${if (male) "m5" else "f5"}"
        val have = runCatching { ctx.assets.list(dir)?.toSet() }.getOrNull().orEmpty()
        return lines(ctx, id, korean).indices.map { n -> "${id}_${n + 1}.m4a".takeIf { it in have }?.let { "$dir/$it" } }
    }

    /** 내가 읽는 줄을 기다리는 시간 (교독 · 낭독): 글자 수로 어림해요. */
    fun readMs(line: String, korean: Boolean, rate: Float): Long =
        ((line.count { !it.isWhitespace() } * (if (korean) 200 else 70) + 700) / rate).toLong()
}
