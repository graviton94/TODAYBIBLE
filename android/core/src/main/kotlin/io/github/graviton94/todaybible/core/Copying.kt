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
