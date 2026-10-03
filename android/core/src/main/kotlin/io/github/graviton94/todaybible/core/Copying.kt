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
        var ms = if (korean) (if (c == ' ') 110 else 280) else (if (c == ' ') 60 else 85)
        if (c in ",;:") ms += 450
        if (c in ".?!") ms += 650
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
            if (found >= 0) { j = found + 1; last = i; misses = 0 } else if (++misses > LOOK) break
        }
        return if (last < 0) 0 else idx[last] + 1
    }
    /** 다 읽었는지: 본문 글자의 거의 다(마지막 몇 글자 놓침 허용)를 덮었으면. */
    fun done(plain: String, heard: String): Boolean {
        val n = plain.count(::keep); if (n == 0) return true
        val covered = plain.take(lit(plain, heard)).count(::keep)
        return covered >= n - LOOK
    }
    private const val LOOK = 3
}

