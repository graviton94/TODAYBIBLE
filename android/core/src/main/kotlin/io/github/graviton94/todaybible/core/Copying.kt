package io.github.graviton94.todaybible.core

/** 채우는 방법. 판권면에 방법별로 따로 적는다. */
enum class Mode { TYPE, PAPER, ALOUD }

/** 한 절을 채운 기록 (덮어쓰지 않고 쌓기만 함). */
data class Fill(val translation: Translation, val key: VerseKey, val mode: Mode, val epochDay: Long, val atMillis: Long)

/**
 * 타자 필사 판정: 띄어쓰기 · 문장부호 · 대소문자는 보지 않고, 틀린 글자만 표시.
 * 한글은 자모를 하나씩 조합하며 쓰므로, 마지막 글자가 아직 조합 중이면 (두벌식 · 천지인 · 나랏글 어느 자판이든) 틀림이 아니라 COMPOSING.
 * 받침이 다음 글자로 넘어가는 경우 (가 + ㅂ → ‘갑’ → 가방) 도 다음 글자까지 이어 봐요.
 */
object TypeJudge {
    enum class Mark { OK, WRONG, PENDING, COMPOSING }
    // 천지인 자판이 조합 중에 잠깐 보이는 아래아 (ㆍ ᆢ ‥) 도 글자로 치지 않음
    private fun ignorable(c: Char) = c.isWhitespace() || c in ".,:;!?'\"()[]{}¶·-—‘’“”ㆍᆢᆞ‥"
    fun letters(s: String): String = Markup.plain(s).filterNot(::ignorable).lowercase()

    /** 원문 글자마다 상태 (무시하는 글자는 앞 글자 상태를 따라감). */
    fun marks(source: String, typed: String): List<Mark> {
        val src = Markup.plain(source)
        val t = letters(typed)
        val targets = src.filterNot(::ignorable).lowercase()
        var k = 0
        return src.map { c ->
            if (ignorable(c)) { if (t.isEmpty() || k > t.length) Mark.PENDING else Mark.OK }
            else {
                val m = when {
                    k >= t.length -> Mark.PENDING
                    t[k] == c.lowercaseChar() -> Mark.OK
                    // 마지막 글자가 조합 중이면 틀림으로 보지 않음
                    k == t.length - 1 && Hangul.composing(t[k], targets, k) -> Mark.COMPOSING
                    else -> Mark.WRONG
                }
                k++; m
            }
        }
    }

    /** 원문 글자 자리마다 실제로 친 글자 (원고지 칸에 그대로 보여 주기). 무시하는 글자 · 아직 안 친 자리는 null. */
    fun typedAt(source: String, typed: String): List<Char?> {
        val src = Markup.plain(source); val t = letters(typed)
        var k = 0
        return src.map { c -> if (ignorable(c)) null else t.getOrNull(k++) }
    }

    fun done(source: String, typed: String): Boolean = letters(source) == letters(typed)
}

/** 한글 자모: 음절을 치는 순서대로의 자모(겹모음 · 겹받침은 나눠서)로 풀기. */
object Hangul {
    private const val BASE = 0xAC00
    private const val INITIALS = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private const val MEDIALS = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    private val FINALS = listOf("", "ㄱ", "ㄲ", "ㄳ", "ㄴ", "ㄵ", "ㄶ", "ㄷ", "ㄹ", "ㄺ", "ㄻ", "ㄼ", "ㄽ", "ㄾ", "ㄿ", "ㅀ", "ㅁ", "ㅂ", "ㅄ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ")
    private val SPLIT = mapOf(
        'ㅘ' to "ㅗㅏ", 'ㅙ' to "ㅗㅐ", 'ㅚ' to "ㅗㅣ", 'ㅝ' to "ㅜㅓ", 'ㅞ' to "ㅜㅔ", 'ㅟ' to "ㅜㅣ", 'ㅢ' to "ㅡㅣ",
        'ㄳ' to "ㄱㅅ", 'ㄵ' to "ㄴㅈ", 'ㄶ' to "ㄴㅎ", 'ㄺ' to "ㄹㄱ", 'ㄻ' to "ㄹㅁ", 'ㄼ' to "ㄹㅂ", 'ㄽ' to "ㄹㅅ", 'ㄾ' to "ㄹㅌ", 'ㄿ' to "ㄹㅍ", 'ㅀ' to "ㄹㅎ", 'ㅄ' to "ㅂㅅ",
    )
    private fun split(s: String) = s.map { SPLIT[it] ?: it.toString() }.joinToString("")

    /** 치는 순서대로의 자모. 한글이 아니면 그 글자 그대로. */
    fun keys(c: Char): String {
        val code = c.code - BASE
        if (code !in 0 until 11172) return split(c.toString())
        return INITIALS[code / 588].toString() + split(MEDIALS[(code % 588) / 28].toString()) + split(FINALS[code % 28])
    }

    /**
     * 마지막 글자가 아직 조합 중으로 볼 만한지 (자판마다 거치는 모습이 달라요).
     * 두벌식: isPrefix. 천지인 · 나랏글: 홀자음을 돌려 고르는 중 (ㄷ → ㄸ), 첫소리는 같고 모음 · 받침을 고르는 중 (기 → 가, 그 → 구, 갇 → 같).
     */
    fun composing(c: Char, targets: String, k: Int): Boolean {
        if (isPrefix(c, targets, k)) return true
        val tc = targets[k]
        val hangul = (tc.code - BASE) in 0 until 11172
        if (c in '\u3131'..'\u318E') return hangul
        val a = c.code - BASE; val b = tc.code - BASE
        return a in 0 until 11172 && hangul && a / 588 == b / 588 && c != tc
    }

    /** 친 글자 c 가 원문 targets[k] (와 다음 글자) 를 치는 도중의 모습인지. */
    fun isPrefix(c: Char, targets: String, k: Int): Boolean {
        val t = keys(c); val a = keys(targets[k]); val b = targets.getOrNull(k + 1)?.let { keys(it) } ?: ""
        if (t.isEmpty() || t == a) return false
        // 아직 덜 친 글자 (ㄸ → 또, 오 → 왜, 달 → 닭)
        if (t.length < a.length && a.startsWith(t)) return true
        // 다음 글자의 첫소리가 받침처럼 잠깐 붙은 모습 (갑 → 가방)
        return b.isNotEmpty() && t == a + b[0]
    }
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

    /** 큰 글씨 한 줄에 들어가지 않는 토막은 가운데에 가까운 띄어쓰기에서 둘로 (들어갈 때까지). */
    fun fit(plain: String, parts: List<IntRange>, fits: (IntRange) -> Boolean): List<IntRange> = parts.flatMap { split(plain, it, fits) }
    private fun split(plain: String, r: IntRange, fits: (IntRange) -> Boolean): List<IntRange> {
        if (fits(r)) return listOf(r)
        val mid = (r.first + r.last) / 2
        val gap = (r.first + 1..r.last - 1).filter { plain[it] == ' ' }.minByOrNull { kotlin.math.abs(it - mid) } ?: return listOf(r)
        return split(plain, r.first..gap - 1, fits) + split(plain, gap + 1..r.last, fits)
    }

    private const val LOOK = 3
    /** 옛말 어미처럼 알아듣기가 자주 놓치는 글자를 몇 개까지 건너뛰어도 따라갈지. */
    private const val SKIP = 6
}

