package io.github.graviton94.todaybible.core

/** 머리글용 라틴어 책 이름 (불가타 전통 표기, 짧게) · 로마 숫자. 화면 꾸밈 글자라 번역과 상관없이 같아요. */
object Latin {
    private val books = listOf(
        "GENESIS", "EXODUS", "LEVITICUS", "NUMERI", "DEUTERONOMIUM", "IOSUE", "IUDICES", "RUTH", "I SAMUELIS", "II SAMUELIS",
        "I REGUM", "II REGUM", "I PARALIPOMENON", "II PARALIPOMENON", "ESDRAS", "NEHEMIAS", "ESTHER", "IOB", "PSALMI", "PROVERBIA",
        "ECCLESIASTES", "CANTICUM", "ISAIAS", "IEREMIAS", "LAMENTATIONES", "EZECHIEL", "DANIEL", "OSEE", "IOEL", "AMOS",
        "ABDIAS", "IONAS", "MICHAEAS", "NAHUM", "HABACUC", "SOPHONIAS", "AGGAEUS", "ZACHARIAS", "MALACHIAS",
        "MATTHAEUS", "MARCUS", "LUCAS", "IOANNES", "ACTUS", "AD ROMANOS", "I AD CORINTHIOS", "II AD CORINTHIOS", "AD GALATAS", "AD EPHESIOS",
        "AD PHILIPPENSES", "AD COLOSSENSES", "I AD THESSALONICENSES", "II AD THESSALONICENSES", "I AD TIMOTHEUM", "II AD TIMOTHEUM", "AD TITUM", "AD PHILEMONEM", "AD HEBRAEOS",
        "IACOBI", "I PETRI", "II PETRI", "I IOANNIS", "II IOANNIS", "III IOANNIS", "IUDAE", "APOCALYPSIS",
    )
    fun book(i: Int): String = books.getOrElse(i) { "" }
    /** 1 → I, 4 → IV, 150 → CL. */
    fun roman(n: Int): String {
        var x = n; val sb = StringBuilder()
        for ((v, s) in listOf(1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC", 50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I"))
            while (x >= v) { sb.append(s); x -= v }
        return sb.toString()
    }
    /** 머리글 한 줄: "MARCUS · III". */
    fun head(book: Int, chapter: Int) = "${book(book)} · ${roman(chapter)}"
}
