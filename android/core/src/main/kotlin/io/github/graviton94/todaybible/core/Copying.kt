package io.github.graviton94.todaybible.core

/** 채우는 방법. 판권면에 방법별로 따로 적는다. */
enum class Mode { TYPE, PAPER, ALOUD }

/** 한 절을 채운 기록 (덮어쓰지 않고 쌓기만 함). */
data class Fill(val translation: Translation, val key: VerseKey, val mode: Mode, val epochDay: Long, val atMillis: Long)

/** 타자 필사 판정: 띄어쓰기 · 문장부호 · 대소문자는 보지 않고, 틀린 글자만 표시. */
object TypeJudge {
    enum class Mark { OK, WRONG, PENDING }
    private fun ignorable(c: Char) = c.isWhitespace() || c in ".,:;!?'\"()[]{}¶·-—‘’“”"
    fun letters(s: String): String = Markup.plain(s).filterNot(::ignorable).lowercase()

    /** 원문 글자마다 상태 (무시하는 글자는 앞 글자 상태를 따라감). */
    fun marks(source: String, typed: String): List<Mark> {
        val src = Markup.plain(source)
        val t = letters(typed)
        var k = 0
        return src.map { c ->
            if (ignorable(c)) { if (k == 0 || k > t.length) Mark.PENDING else Mark.OK }
            else { val m = if (k >= t.length) Mark.PENDING else if (t[k] == c.lowercaseChar()) Mark.OK else Mark.WRONG; k++; m }
        }
    }
    fun done(source: String, typed: String): Boolean = letters(source) == letters(typed)
}

/** 낭독 속도: 성경 낭독보다 조금 느리게. 한국어 ≈ 3.6음절/초, 영어 ≈ 2.2단어/초. 쉼표 · 마침표에서 숨. */
object ReadingPace {
    fun delayMillis(c: Char, korean: Boolean, speed: Float = 1f): Long {
        // 소리 내어 읽는 보통 빠르기 (한국어 1초에 대여섯 음절)
        var ms = if (korean) (if (c == ' ') 70 else 175) else (if (c == ' ') 45 else 62)
        if (c in ",;:") ms += 280
        if (c in ".?!") ms += 420
        return (ms / speed).toLong()
    }
}

/**
 * 소리 내어 읽기: 알아들은 말이 본문을 어디까지 덮었는지. 띄어쓰기 · 문장부호 · 대소문자는 보지 않고,
 * 알아듣기가 한두 글자 놓치거나 더해도 따라가도록 앞을 조금 내다보며 맞춤.
 */
object Recite {
    private fun keep(c: Char) = c.isLetterOrDigit()
    /** 본문(Markup.plain) 가운데 밝힐 글자 수. */
    fun lit(plain: String, heard: String): Int {
        val h = heard.filter(::keep).lowercase()
        val idx = plain.indices.filter { keep(plain[it]) }      // 본문 글자의 원래 자리
        val t = idx.map { plain[it].lowercaseChar() }
        var j = 0; var last = -1; var misses = 0
        for (i in t.indices) {
            if (j >= h.length) break
            var found = -1
            for (d in 0..LOOK) if (j + d < h.length && h[j + d] == t[i]) { found = j + d; break }
            if (found >= 0) { j = found + 1; last = i; misses = 0 } else if (++misses > SKIP) break
        }
        return if (last < 0) 0 else idx[last] + 1
    }
    /** 다 읽었는지: 본문 글자의 거의 다(마지막 몇 글자 놓침 허용)를 덮었으면. */
    fun done(plain: String, heard: String): Boolean {
        val n = plain.count(::keep); if (n == 0) return true
        val covered = plain.take(lit(plain, heard)).count(::keep)
        return covered >= n - maxOf(LOOK, n / 12)
    }
    /**
     * 본문 글자마다 알아들었는지 (lit 과 같은 맞춤). 글자가 아닌 자리(띄어쓰기 · 문장부호)는 true.
     */
    fun matched(plain: String, heard: String): BooleanArray {
        val h = heard.filter(::keep).lowercase()
        val idx = plain.indices.filter { keep(plain[it]) }
        val t = idx.map { plain[it].lowercaseChar() }
        val ok = BooleanArray(plain.length) { !keep(plain[it]) }
        var j = 0; var misses = 0
        for (i in t.indices) {
            if (j >= h.length) break
            var found = -1
            for (d in 0..LOOK) if (j + d < h.length && h[j + d] == t[i]) { found = j + d; break }
            if (found >= 0) { j = found + 1; ok[idx[i]] = true; misses = 0 } else if (++misses > SKIP) break
        }
        return ok
    }

    /** 알아듣지 못한 낱말들 (본문 안의 [시작, 끝) 자리). 낱말 글자의 절반 넘게 놓쳤으면 놓친 낱말. */
    fun missed(plain: String, heard: String): List<IntRange> {
        val ok = matched(plain, heard)
        return Regex("\\S+").findAll(plain).map { it.range }.filter { r ->
            val letters = r.filter { keep(plain[it]) }
            letters.isNotEmpty() && letters.count { !ok[it] } * 2 > letters.size
        }.toList()
    }

    /**
     * 숨 쉴 자리로 나눈 구절들: 이어지는 말끝(~며 · ~고 · ~니 …)에서 끊고, 너무 짧은 토막은 붙임.
     * 큰 글씨 한 줄 낭독 · 구절만 손으로 · 가이드 목소리의 쉼에 같이 써요. 각 구절은 본문 안의 [시작, 끝).
     */
    fun phrases(plain: String, min: Int = 9): List<IntRange> {
        val words = Regex("\\S+").findAll(plain).map { it.range }.toList()
        val out = ArrayList<IntRange>(); var start = -1
        for ((n, w) in words.withIndex()) {
            if (start < 0) start = w.first
            val word = plain.substring(w.first, w.last + 1).trimEnd(',', '.', ';', ':', '?', '!')
            val len = plain.substring(start, w.last + 1).count(::keep)
            val brk = plain[w.last] in ",;:.?!" || (CONT.containsMatchIn(word) && len >= min)
            if (brk || n == words.lastIndex) { out.add(start..w.last); start = -1 }
        }
        // 끝 토막이 너무 짧으면 앞에 붙임
        if (out.size >= 2 && plain.substring(out.last().first, out.last().last + 1).count(::keep) < 5) {
            val last = out.removeAt(out.lastIndex); out[out.lastIndex] = out.last().first..last.last
        }
        return out
    }
    private val CONT = Regex("(며|고|니|되|나|여|서|면|매|요|라|도)$")

    private const val LOOK = 3
    /** 옛말 어미처럼 알아듣기가 자주 놓치는 글자를 몇 개까지 건너뛰어도 따라갈지. */
    private const val SKIP = 6
}

