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
    // 화면 줄과 PDF 만드는 줄이 함께 써요
    private val cache = java.util.concurrent.ConcurrentHashMap<String, BookText>()

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
        get() = prefs.getInt("goal", io.github.graviton94.todaybible.core.Goal.CHAPTER)
        set(v) = prefs.edit().putInt("goal", v).apply()
    /** 처음 소개를 마쳤는지. */
    var onboarded: Boolean
        get() = prefs.getBoolean("onboarded", false)
        set(v) = prefs.edit().putBoolean("onboarded", v).apply()
    /** 필사 화면을 노트로 볼지. */
    var notebook: Boolean
        get() = prefs.getBoolean("notebook", false)
        set(v) = prefs.edit().putBoolean("notebook", v).apply()
    /** 타자 보기 (0 책 · 1 노트 · 2 원고지). 처음엔 원고지, 예전에 노트를 쓰던 분은 노트. */
    var typeView: Int
        get() = prefs.getInt("type_view", if (notebook) 1 else 2)
        set(v) = prefs.edit().putInt("type_view", v).apply()
    /** 길잡이 (없으면 null) · 시작한 날. */
    var planId: String?
        get() = prefs.getString("plan", null)
        set(v) = prefs.edit().putString("plan", v).apply()
    var planStart: Long
        get() = prefs.getLong("plan_start", 0)
        set(v) = prefs.edit().putLong("plan_start", v).apply()
    /** 표지 가죽 (burgundy · navy · olive · ebony) · 금박 이름. */
    var cover: String
        get() = prefs.getString("cover", "burgundy")!!
        set(v) = prefs.edit().putString("cover", v).apply()
    var ownerName: String
        get() = prefs.getString("owner", "")!!
        set(v) = prefs.edit().putString("owner", v).apply()
    /** 자동 보관 위치 (구글 드라이브 등, 저장소 접근 프레임워크 문서) · 마지막으로 보관한 때. */
    var backupUri: String
        get() = prefs.getString("backup_uri", "")!!
        set(v) = prefs.edit().putString("backup_uri", v).apply()
    var backupAt: Long
        get() = prefs.getLong("backup_at", 0)
        set(v) = prefs.edit().putLong("backup_at", v).apply()
    /** 위젯이 쓰는 오늘의 분량 (길잡이 반영). */
    var lastGoal: Int
        get() = prefs.getInt("last_goal", 0)
        set(v) = prefs.edit().putInt("last_goal", v).apply()
    /** 내 목소리 남기기 (평생권). */
    var voiceOn: Boolean
        get() = prefs.getBoolean("voice", false)
        set(v) = prefs.edit().putBoolean("voice", v).apply()
    /** 표지 넘김을 마지막으로 보여 준 날. */
    var openedDay: Long
        get() = prefs.getLong("opened", -1)
        set(v) = prefs.edit().putLong("opened", v).apply()
    var startDay: Long
        get() = prefs.getLong("start", -1)
        set(v) = prefs.edit().putLong("start", v).apply()

    /** 알림을 다시 켤 때 쓸 시각 (끄기 전 시각, 처음엔 아침 7시). */
    var lastReminderHour: Int
        get() = prefs.getInt("reminder_last", 7)
        set(v) = prefs.edit().putInt("reminder_last", v).apply()
    /** 알림을 마지막으로 낸 날 (하루 한 번). */
    /** 알림을 실제로 띄운 날 (하루 한 번). */
    var postedDay: Long
        get() = prefs.getLong("posted_day", -1)
        set(v) = prefs.edit().putLong("posted_day", v).apply()
    var coachSeen: Set<String>
        get() = prefs.getString("coach_seen", "").orEmpty().split(',').filter { it.isNotBlank() }.toSet()
        set(v) = prefs.edit().putString("coach_seen", v.joinToString(",")).apply()
    var reminderDay: Long
        get() = prefs.getLong("reminder_day", -1)
        set(v) = prefs.edit().putLong("reminder_day", v).apply()
    /** 손글씨: 펜 (만년필 · 붓펜 · 연필) · 밑글씨 · 펜 소리 · 종이 결 진동. */
    var pen: Int
        get() = prefs.getInt("pen", 0)
        set(v) = prefs.edit().putInt("pen", v).apply()
    /** 손글씨는 마음에 닿은 구절만 (X1). */
    var handPhrase: Boolean
        get() = prefs.getBoolean("hand_phrase", true)
        set(v) = prefs.edit().putBoolean("hand_phrase", v).apply()
    var handGuide: Boolean
        get() = prefs.getBoolean("hand_guide", true)
        set(v) = prefs.edit().putBoolean("hand_guide", v).apply()
    var penSound: Boolean
        get() = prefs.getBoolean("pen_sound", true)
        set(v) = prefs.edit().putBoolean("pen_sound", v).apply()
    var paperHaptic: Boolean
        get() = prefs.getBoolean("paper_haptic", true)
        set(v) = prefs.edit().putBoolean("paper_haptic", v).apply()
    /** 또렷하게 (고대비). */
    var contrast: Boolean
        get() = prefs.getBoolean("contrast", false)
        set(v) = prefs.edit().putBoolean("contrast", v).apply()
    /** 밤 필사: 밤 9시 ~ 새벽 5시에 촛불빛 화면. */
    var candle: Boolean
        get() = prefs.getBoolean("candle", true)
        set(v) = prefs.edit().putBoolean("candle", v).apply()

    /** 필사 방식 탭 (0 낭독 · 1 타자 · 2 손글씨). */
    var copyTab: Int
        get() = prefs.getInt("copy_tab", 0)
        set(v) = prefs.edit().putInt("copy_tab", v).apply()
    /** 낭독: 가이드 목소리와 함께 읽기 · 빠르기 (0 천천히 · 1 보통 · 2 빠르게) · 고른 목소리. */
    var aloudMode: Int
        get() = prefs.getInt("aloud_mode", 0)
        set(v) = prefs.edit().putInt("aloud_mode", v).apply()
    /** 큰 글씨 한 줄 낭독. */
    var aloudBig: Boolean
        get() = prefs.getBoolean("aloud_big", true)
        set(v) = prefs.edit().putBoolean("aloud_big", v).apply()
    var aloudSpeed: Int
        get() = prefs.getInt("aloud_speed", 1)
        set(v) = prefs.edit().putInt("aloud_speed", v).apply()
    /** 낭독 목소리: m5 진중한 남성 · f5 차분한 여성 (미리 만든 음원) · device 폰 목소리. */
    var narrator: String
        get() = prefs.getString("narrator", "m5")!!
        set(v) = prefs.edit().putString("narrator", v).apply()
    var guideVoice: String
        get() = prefs.getString("guide_voice", "")!!
        set(v) = prefs.edit().putString("guide_voice", v).apply()

    /** 소리 내어 읽은 시간 (날 → 초). */
    private val aloudFile get() = java.io.File(context.filesDir, "aloud.tsv")
    fun loadAloud(): Map<Long, Int> = runCatching {
        aloudFile.takeIf { it.exists() }?.readLines()?.mapNotNull { l -> l.split('\t').takeIf { it.size >= 2 }?.let { it[0].toLong() to it[1].toInt() } }?.toMap().orEmpty()
    }.getOrDefault(emptyMap())
    fun saveAloud(m: Map<Long, Int>) {
        val tmp = java.io.File(aloudFile.path + ".tmp")
        tmp.writeText(m.entries.sortedBy { it.key }.joinToString("") { "${it.key}\t${it.value}\n" }); tmp.renameTo(aloudFile)
    }

    /** 형광펜 밑줄 (번역 · 절 · 그은 날). 파일 하나에 통째로. */
    data class Mark(val translation: Translation, val key: io.github.graviton94.todaybible.core.VerseKey, val epochDay: Long)
    private val bookmarksFile get() = java.io.File(context.filesDir, "bookmarks.tsv")
    fun loadBookmarks(): List<Mark> = readMarks(bookmarksFile)
    fun saveBookmarks(list: List<Mark>) = writeMarks(bookmarksFile, list)
    private val marksFile get() = java.io.File(context.filesDir, "marks.tsv")
    fun loadMarks(): List<Mark> = readMarks(marksFile)
    fun saveMarks(marks: List<Mark>) = writeMarks(marksFile, marks)
    private fun readMarks(f: java.io.File): List<Mark> = runCatching {
        f.takeIf { it.exists() }?.readLines()?.mapNotNull { l ->
            val p = l.split('\t'); if (p.size < 3) null
            else runCatching { Mark(Translation.valueOf(p[0]), io.github.graviton94.todaybible.core.VerseKey(p[1].toInt()), p[2].toLong()) }.getOrNull()
        }.orEmpty()
    }.getOrDefault(emptyList())
    private fun writeMarks(f: java.io.File, marks: List<Mark>) {
        val tmp = java.io.File(f.path + ".tmp")
        tmp.writeText(marks.joinToString("") { "${it.translation.name}\t${it.key.raw}\t${it.epochDay}\n" }); tmp.renameTo(f)
    }

    private fun defaultTranslation() = if (java.util.Locale.getDefault().language == "ko") Translation.KRV else Translation.KJV
}
