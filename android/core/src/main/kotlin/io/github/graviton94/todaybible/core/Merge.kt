package io.github.graviton94.todaybible.core

/**
 * 합쳐서 되살리기 (1.5): 폰 · 태블릿에서 번갈아 쓴 기록을 서버 없이 하나로. 덮어쓰지 않고 합쳐요 — 어느 쪽 기록도 사라지지 않게.
 * 줄 단위 (기기 안 TSV 그대로) 로 다뤄요. 읽지 못하는 줄은 지금 기기 쪽 것을 그대로 둬요.
 */
object Merge {
    private fun cols(l: String, n: Int = -1) = if (n > 0) l.split('\t', limit = n) else l.split('\t')

    /** 필사 기록 (번역 · 절 · 방식 · 날 · 시각): 같은 (번역 · 절 · 방식) 이면 먼저 쓴 것 하나 (세 필사본은 따로). 시각 차례로. */
    fun fills(mine: List<String>, theirs: List<String>): List<String> {
        val best = LinkedHashMap<String, Pair<Long, String>>()
        for (l in mine + theirs) {
            val p = cols(l); if (p.size < 5) continue
            val at = p[4].toLongOrNull() ?: continue
            val k = p[0] + "\t" + p[1] + "\t" + p[2]
            val old = best[k]
            if (old == null || at < old.first) best[k] = at to l
        }
        return best.values.sortedBy { it.first }.map { it.second }
    }

    /** 발자취 (이름 · 얻은 날): 먼저 얻은 날. */
    fun earned(mine: List<String>, theirs: List<String>): List<String> = minBy(mine + theirs, key = { it[0] }, value = { it.getOrNull(1)?.toLongOrNull() })

    /** 형광펜 · 책갈피 · 마음에 새기기 (번역 · 절 · 날): 합집합, 같은 절이면 먼저 담은 날. */
    fun marks(mine: List<String>, theirs: List<String>): List<String> = minBy(mine + theirs, key = { it[0] + "\t" + it.getOrElse(1) { "" } }, value = { it.getOrNull(2)?.toLongOrNull() })

    /** 소리 내어 읽은 시간 (날 · 초): 날마다 큰 쪽 (두 기기에서 같은 날 읽었어도 더하지 않음: 같은 기록이 두 번 들어올 수 있어서). */
    fun aloud(mine: List<String>, theirs: List<String>): List<String> {
        val m = sortedMapOf<Long, Int>()
        for (l in mine + theirs) { val p = cols(l); if (p.size < 2) continue; val d = p[0].toLongOrNull() ?: continue; val s = p[1].toIntOrNull() ?: continue; m[d] = maxOf(m[d] ?: 0, s) }
        return m.map { "${it.key}\t${it.value}" }
    }

    /**
     * 노트 (종류 · 번역 · 권 · 장 · 처음 절 · 끝 절 · 날 · 글): 묵상은 (번역 · 권 · 장) 마다, 설교는 날마다 하나.
     * 같은 자리면 나중 날 것, 같은 날이면 긴 글.
     */
    fun notes(mine: List<String>, theirs: List<String>): List<String> {
        val best = LinkedHashMap<String, String>()
        fun key(p: List<String>) = if (p[0] == "SERMON") "S\t" + p[6] else "R\t" + p[1] + "\t" + p[2] + "\t" + p[3]
        fun better(a: List<String>, b: List<String>): Boolean {
            val da = a[6].toLongOrNull() ?: 0; val db = b[6].toLongOrNull() ?: 0
            return if (da != db) da > db else a[7].length > b[7].length
        }
        for (l in mine + theirs) {
            val p = cols(l, 8); if (p.size < 8) continue
            val k = key(p); val old = best[k]
            if (old == null || better(p, cols(old, 8))) best[k] = l
        }
        return best.values.toList()
    }

    /** 몇 절이 새로 더해졌는지 (되살리기 뒤 한 줄 알림용). */
    fun added(mine: List<String>, merged: List<String>): Int {
        val had = mine.mapNotNull { cols(it).takeIf { p -> p.size >= 3 }?.let { p -> p[0] + "\t" + p[1] + "\t" + p[2] } }.toHashSet()
        return merged.count { l -> cols(l).let { p -> p.size >= 3 && (p[0] + "\t" + p[1] + "\t" + p[2]) !in had } }
    }

    private fun minBy(lines: List<String>, key: (List<String>) -> String, value: (List<String>) -> Long?): List<String> {
        val best = LinkedHashMap<String, Pair<Long, String>>()
        for (l in lines) {
            val p = cols(l); if (p.isEmpty() || p[0].isBlank()) continue
            val v = value(p) ?: continue; val k = key(p)
            val old = best[k]
            if (old == null || v < old.first) best[k] = v to l
        }
        return best.values.map { it.second }
    }
}
