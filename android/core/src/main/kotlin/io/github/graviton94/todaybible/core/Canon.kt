package io.github.graviton94.todaybible.core

/** 묶음 (서재의 묶음 색인). */
enum class Group { LAW, HISTORY, POETRY, PROPHETS, GOSPELS, ACTS, EPISTLES, REVELATION }

/** 66권 하나. index 0 = 창세기 … 65 = 요한계시록. */
data class Book(val index: Int, val osis: String, val ko: String, val en: String, val group: Group, val chapters: Int) {
    val oldTestament: Boolean get() = index < 39
}

/** 개신교 66권 정경 (개역한글 · KJV 공통 순서). */
object Canon {
    private val raw = listOf(
        "Gen" to ("창세기" to "Genesis"), "Exod" to ("출애굽기" to "Exodus"), "Lev" to ("레위기" to "Leviticus"), "Num" to ("민수기" to "Numbers"), "Deut" to ("신명기" to "Deuteronomy"),
        "Josh" to ("여호수아" to "Joshua"), "Judg" to ("사사기" to "Judges"), "Ruth" to ("룻기" to "Ruth"), "1Sam" to ("사무엘상" to "1 Samuel"), "2Sam" to ("사무엘하" to "2 Samuel"),
        "1Kgs" to ("열왕기상" to "1 Kings"), "2Kgs" to ("열왕기하" to "2 Kings"), "1Chr" to ("역대상" to "1 Chronicles"), "2Chr" to ("역대하" to "2 Chronicles"), "Ezra" to ("에스라" to "Ezra"),
        "Neh" to ("느헤미야" to "Nehemiah"), "Esth" to ("에스더" to "Esther"), "Job" to ("욥기" to "Job"), "Ps" to ("시편" to "Psalms"), "Prov" to ("잠언" to "Proverbs"),
        "Eccl" to ("전도서" to "Ecclesiastes"), "Song" to ("아가" to "Song of Solomon"), "Isa" to ("이사야" to "Isaiah"), "Jer" to ("예레미야" to "Jeremiah"), "Lam" to ("예레미야애가" to "Lamentations"),
        "Ezek" to ("에스겔" to "Ezekiel"), "Dan" to ("다니엘" to "Daniel"), "Hos" to ("호세아" to "Hosea"), "Joel" to ("요엘" to "Joel"), "Amos" to ("아모스" to "Amos"),
        "Obad" to ("오바댜" to "Obadiah"), "Jonah" to ("요나" to "Jonah"), "Mic" to ("미가" to "Micah"), "Nah" to ("나훔" to "Nahum"), "Hab" to ("하박국" to "Habakkuk"),
        "Zeph" to ("스바냐" to "Zephaniah"), "Hag" to ("학개" to "Haggai"), "Zech" to ("스가랴" to "Zechariah"), "Mal" to ("말라기" to "Malachi"),
        "Matt" to ("마태복음" to "Matthew"), "Mark" to ("마가복음" to "Mark"), "Luke" to ("누가복음" to "Luke"), "John" to ("요한복음" to "John"), "Acts" to ("사도행전" to "Acts"),
        "Rom" to ("로마서" to "Romans"), "1Cor" to ("고린도전서" to "1 Corinthians"), "2Cor" to ("고린도후서" to "2 Corinthians"), "Gal" to ("갈라디아서" to "Galatians"), "Eph" to ("에베소서" to "Ephesians"),
        "Phil" to ("빌립보서" to "Philippians"), "Col" to ("골로새서" to "Colossians"), "1Thess" to ("데살로니가전서" to "1 Thessalonians"), "2Thess" to ("데살로니가후서" to "2 Thessalonians"), "1Tim" to ("디모데전서" to "1 Timothy"),
        "2Tim" to ("디모데후서" to "2 Timothy"), "Titus" to ("디도서" to "Titus"), "Phlm" to ("빌레몬서" to "Philemon"), "Heb" to ("히브리서" to "Hebrews"), "Jas" to ("야고보서" to "James"),
        "1Pet" to ("베드로전서" to "1 Peter"), "2Pet" to ("베드로후서" to "2 Peter"), "1John" to ("요한일서" to "1 John"), "2John" to ("요한이서" to "2 John"), "3John" to ("요한삼서" to "3 John"),
        "Jude" to ("유다서" to "Jude"), "Rev" to ("요한계시록" to "Revelation"),
    )
    private fun groupOf(i: Int) = when (i) {
        in 0..4 -> Group.LAW; in 5..16 -> Group.HISTORY; in 17..21 -> Group.POETRY; in 22..38 -> Group.PROPHETS
        in 39..42 -> Group.GOSPELS; 43 -> Group.ACTS; in 44..64 -> Group.EPISTLES; else -> Group.REVELATION
    }
    /** 권마다 장 수 (두 번역 같음, CoreTest 가 본문과 대조). */
    private val chapterCounts = intArrayOf(50, 40, 27, 36, 34, 24, 21, 4, 31, 24, 22, 25, 29, 36, 10, 13, 10, 42, 150, 31, 12, 8, 66, 52, 5, 48, 12, 14, 3, 9, 1, 4, 7, 3, 3, 3, 2, 14, 4,
        28, 16, 24, 21, 28, 16, 16, 13, 6, 6, 4, 4, 5, 3, 6, 4, 3, 1, 13, 5, 5, 3, 5, 1, 1, 1, 22)
    val books: List<Book> = raw.mapIndexed { i, (osis, n) -> Book(i, osis, n.first, n.second, groupOf(i), chapterCounts[i]) }
    fun byOsis(osis: String): Book? = books.firstOrNull { it.osis == osis }
    fun inGroup(g: Group): List<Book> = books.filter { it.group == g }
    /** 무료로 열려 있는 권: 창세기 · 시편 · 잠언 · 마가복음. */
    val free: Set<Int> = setOf(0, 18, 19, 40)
}

/** 번역에 독립적이지 않은 장절 키: BBCCCVVV (권 1~66). 기록은 번역마다 따로 쌓는다. */
@JvmInline
value class VerseKey(val raw: Int) {
    constructor(book: Int, chapter: Int, verse: Int) : this((book + 1) * 1_000_000 + chapter * 1_000 + verse)
    val book: Int get() = raw / 1_000_000 - 1
    val chapter: Int get() = raw / 1_000 % 1_000
    val verse: Int get() = raw % 1_000
    val chapterKey: Int get() = raw / 1_000
    override fun toString() = "${Canon.books[book].osis} $chapter:$verse"
}

/** 번역. total = 채울 수 있는 절 수 (CoreTest 가 본문에서 확인). */
enum class Translation(val id: String, val total: Int) { KRV("krv", 31084), KJV("kjv", 31102) }

/** 장절 찾기: "요 3:16", "요한복음 3장 16절", "John 3:16", "ps23" 같은 글을 (권, 장, 절?) 로. */
object Reference {
    /** 한국 교회에서 흔히 쓰는 약칭 (권 순서대로). */
    private val koShort = listOf("창", "출", "레", "민", "신", "수", "삿", "룻", "삼상", "삼하", "왕상", "왕하", "대상", "대하", "스", "느", "에", "욥", "시", "잠", "전", "아", "사", "렘", "애", "겔", "단",
        "호", "욜", "암", "옵", "욘", "미", "나", "합", "습", "학", "슥", "말", "마", "막", "눅", "요", "행", "롬", "고전", "고후", "갈", "엡", "빌", "골", "살전", "살후", "딤전", "딤후", "딛", "몬",
        "히", "약", "벧전", "벧후", "요일", "요이", "요삼", "유", "계")
    private val enShort = mapOf("ps" to 18, "psa" to 18, "psalm" to 18, "prov" to 19, "jn" to 42, "mk" to 40, "mt" to 39, "lk" to 41, "rev" to 65, "gen" to 0, "ex" to 1, "rom" to 44)

    fun parse(raw: String): Triple<Int, Int, Int?>? {
        val q = raw.trim().lowercase().replace("장", ":").replace("편", ":").replace("절", "").replace(Regex("\\s+"), " ")
        val m = Regex("^([1-3]?\\s?[^0-9:]+?)\\s*(\\d+)(?:\\s*[:. ]\\s*(\\d+))?\\s*:?$").find(q) ?: return null
        val name = m.groupValues[1].replace(" ", "")
        val ch = m.groupValues[2].toInt(); val v = m.groupValues[3].toIntOrNull()
        val book = find(name) ?: return null
        if (ch < 1 || ch > Canon.books[book].chapters) return null
        return Triple(book, ch, v)
    }

    private fun find(name: String): Int? {
        Canon.books.firstOrNull { it.ko.replace(" ", "") == name }?.let { return it.index }
        koShort.indexOf(name).takeIf { it >= 0 }?.let { return it }
        enShort[name]?.let { return it }
        Canon.books.firstOrNull { it.osis.lowercase() == name || it.en.lowercase().replace(" ", "") == name }?.let { return it.index }
        // 앞부분만 (예: "요한복", "genes", "matth")
        if (name.length >= 2) Canon.books.firstOrNull { it.ko.replace(" ", "").startsWith(name) || it.en.lowercase().replace(" ", "").startsWith(name) }?.let { return it.index }
        return null
    }
}

