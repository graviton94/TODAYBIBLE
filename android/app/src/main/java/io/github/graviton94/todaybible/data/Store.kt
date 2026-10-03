package io.github.graviton94.todaybible.data

import android.content.Context
import io.github.graviton94.todaybible.core.BookText
import io.github.graviton94.todaybible.core.Fill
import io.github.graviton94.todaybible.core.Milestone
import io.github.graviton94.todaybible.core.Mode
import io.github.graviton94.todaybible.core.Translation
import io.github.graviton94.todaybible.core.VerseKey
import io.github.graviton94.todaybible.design.ThemeChoice
import java.io.File

/** 판화 하나: 그 장면이 나오는 장. */
data class Plate(val id: String, val book: Int, val chapter: Int, val ko: String, val en: String, val verse: Int, val byKo: String, val byEn: String)

/**
 * 기기 안 저장. 필사 기록은 덮어쓰지 않고 한 줄씩 덧붙이기만 함 (fills.tsv). 설정은 SharedPreferences.
 */
class Store(val context: Context) {
    private val prefs = context.getSharedPreferences("today", Context.MODE_PRIVATE)
    private val fillsFile = File(context.filesDir, "fills.tsv")
    private val earnedFile = File(context.filesDir, "earned.tsv")
    private val cache = HashMap<String, BookText>()

    fun book(tr: Translation, index: Int): BookText = cache.getOrPut("${tr.id}/$index") {
        BookText.parse(index, context.assets.open("bible/${tr.id}/%02d.tsv".format(index + 1)).bufferedReader().use { it.readText() })
    }

    val plates: List<Plate> by lazy {
        context.assets.open("plates/plates.tsv").bufferedReader().readLines().filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { l ->
            val p = l.split('\t'); val b = io.github.graviton94.todaybible.core.Canon.byOsis(p[1]) ?: return@mapNotNull null
            Plate(p[0], b.index, p[2].toInt(), p[3], p[4], p.getOrNull(5)?.toIntOrNull() ?: 1, p.getOrElse(6) { "" }, p.getOrElse(7) { "" })
        }
    }
    fun plateFor(book: Int, chapter: Int): Plate? = plates.firstOrNull { it.book == book && it.chapter == chapter }

    fun loadFills(): List<Fill> = if (!fillsFile.exists()) emptyList() else fillsFile.readLines().mapNotNull { l ->
        val p = l.split('\t'); if (p.size < 5) return@mapNotNull null
        runCatching { Fill(Translation.valueOf(p[0]), VerseKey(p[1].toInt()), Mode.valueOf(p[2]), p[3].toLong(), p[4].toLong()) }.getOrNull()
    }
    fun append(fills: List<Fill>) {
        fillsFile.appendText(fills.joinToString("") { "${it.translation}\t${it.key.raw}\t${it.mode}\t${it.epochDay}\t${it.atMillis}\n" })
    }
    fun loadEarned(): Map<Milestone, Long> = if (!earnedFile.exists()) emptyMap() else earnedFile.readLines().mapNotNull { l ->
        val p = l.split('\t'); runCatching { Milestone.valueOf(p[0]) to p[1].toLong() }.getOrNull()
    }.toMap()
    fun saveEarned(m: Map<Milestone, Long>) { earnedFile.writeText(m.entries.joinToString("") { "${it.key}\t${it.value}\n" }) }
    fun reset() { fillsFile.delete(); earnedFile.delete(); prefs.edit().clear().apply() }

    var theme: ThemeChoice
        get() = runCatching { ThemeChoice.valueOf(prefs.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeChoice.SYSTEM)
        set(v) = prefs.edit().putString("theme", v.name).apply()
    var translation: Translation
        get() = runCatching { Translation.valueOf(prefs.getString("tr", defaultTranslation().name)!!) }.getOrDefault(Translation.KRV)
        set(v) = prefs.edit().putString("tr", v.name).apply()
    var textScale: Float
        get() = prefs.getFloat("scale", 1f)
        set(v) = prefs.edit().putFloat("scale", v).apply()
    var stamp: String
        get() = prefs.getString("stamp", "✠")!!
        set(v) = prefs.edit().putString("stamp", v).apply()
    /** 책갈피: 마지막으로 쓰던 권 · 장 (번역마다). */
    fun bookmark(tr: Translation): Pair<Int, Int> = prefs.getInt("bm_book_${tr.id}", 40) to prefs.getInt("bm_ch_${tr.id}", 1)
    fun setBookmark(tr: Translation, book: Int, chapter: Int) = prefs.edit().putInt("bm_book_${tr.id}", book).putInt("bm_ch_${tr.id}", chapter).apply()
    /** 매일 알림 시각 (시, -1 = 끔). */
    var reminderHour: Int
        get() = prefs.getInt("reminder", -1)
        set(v) = prefs.edit().putInt("reminder", v).apply()
    /** 오늘의 분량 (절 수, -1 = 하루 한 장). */
    var dailyGoal: Int
        get() = prefs.getInt("goal", 5)
        set(v) = prefs.edit().putInt("goal", v).apply()
    /** 처음 소개를 마쳤는지. */
    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = prefs.edit().putBoolean("onboarded", v).apply()
    /** 필사 화면을 노트로 볼지. */
    var notebook: Boolean
        get() = prefs.getBoolean("notebook", false)
        set(v) = prefs.edit().putBoolean("notebook", v).apply()
    var startDay: Long
        get() = prefs.getLong("start", -1)
        set(v) = prefs.edit().putLong("start", v).apply()

    private fun defaultTranslation() = if (java.util.Locale.getDefault().language == "ko") Translation.KRV else Translation.KJV
}
