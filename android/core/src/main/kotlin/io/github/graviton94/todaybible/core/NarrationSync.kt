package io.github.graviton94.todaybible.core

/**
 * 미리 만든 낭독 음원과 글자 맞추기. 음원은 scripts/narrate.py 가 절을 구절로 나눠 읽고,
 * 구절 사이에 숨 (gap) 을, 끝에 0.12초 쉼을 둬요. 같은 방식으로 나눠서 재생 위치를 글자 자리로 바꿔요.
 * (재생 시간을 글자 수로 고르게 나누면 숨 쉬는 동안에도 글자가 앞서 가서 어긋나요.)
 */
object NarrationSync {
    /** narrate.py 의 TUNE (낮춤 · 숨 길이 초). 낮추면 소리가 늘어나 숨도 그만큼 길어져요. */
    private class Tune(val deepen: Double, val gap: Double)
    private val TUNE = mapOf("m5" to Tune(0.96, 0.42), "f5" to Tune(1.0, 0.35), "en_m5" to Tune(0.97, 0.36), "en_f5" to Tune(1.0, 0.32))
    private const val TAIL = 0.12
    private val CONT = Regex("(며|고|니|되|나|여|서|면|매|요|라|은|는|도)$")

    /** narrate.py 의 phrases 와 같게 나눈 구절들 (띄어쓰기 하나로 이으면 본문 그대로). */
    fun phrases(v: String, english: Boolean): List<String> {
        if (english) {
            val res = mutableListOf<String>()
            for (p in v.split(Regex("(?<=[,;:.?!])\\s+"))) {
                if (res.isNotEmpty() && (p.length < 12 || res.last().length < 12)) res[res.size - 1] = res.last() + " " + p else res.add(p)
            }
            return res.filter { it.isNotBlank() }
        }
        val res = mutableListOf<String>(); val cur = mutableListOf<String>()
        for (w in v.split(' ').filter { it.isNotEmpty() }) {
            cur.add(w)
            if ((CONT.containsMatchIn(w.trimEnd(',', '.', ';', ':', '?', '!')) && cur.joinToString("").length >= 9) || w.last() in ",;:.?!") { res.add(cur.joinToString(" ")); cur.clear() }
        }
        if (cur.isNotEmpty()) {
            if (res.isNotEmpty() && cur.joinToString("").length < 5) res[res.size - 1] = res.last() + " " + cur.joinToString(" ") else res.add(cur.joinToString(" "))
        }
        return res
    }

    private fun weight(p: String) = p.count { it.isLetterOrDigit() }.coerceAtLeast(1)

    /** 재생 위치 (ms) → 밝힐 글자 수. plain = Markup.plain 본문, durMs = 음원 길이. 모르는 목소리면 고르게. */
    fun lit(plain: String, english: Boolean, voice: String, posMs: Int, durMs: Int): Int {
        if (durMs <= 0) return 0
        val tune = TUNE[voice] ?: return (posMs.toLong() * plain.length / durMs).toInt().coerceIn(0, plain.length)
        val ps = phrases(plain, english)
        if (ps.isEmpty()) return plain.length
        val gapMs = tune.gap * 1000 / tune.deepen; val tailMs = TAIL * 1000 / tune.deepen
        val speech = (durMs - gapMs * (ps.size - 1) - tailMs).coerceAtLeast(1.0)
        val total = ps.sumOf { weight(it) }.toDouble()
        var t = posMs.toDouble(); var at = 0
        ps.forEachIndexed { i, p ->
            val d = speech * weight(p) / total
            if (t < d) return (at + charAt(p, t / d)).coerceAtMost(plain.length)
            t -= d; at += p.length
            if (i < ps.size - 1) { if (t < gapMs) return at.coerceAtMost(plain.length); t -= gapMs; at += 1 }
        }
        return plain.length
    }

    /** 구절 안에서 읽은 몫 (0..1) 만큼의 글자 수 (띄어쓰기 · 부호는 그냥 지나가요). */
    private fun charAt(p: String, frac: Double): Int {
        val want = frac * weight(p); var n = 0
        p.forEachIndexed { i, c -> if (c.isLetterOrDigit()) { if (n >= want) return i; n++ } }
        return p.length
    }
}
