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
        // 예전 ‘더 크게 · 아주 크게’ 는 ‘크게’ 로
        get() = if (prefs.getFloat("scale", 1f) > 1.01f) io.github.graviton94.todaybible.design.Tokens.Ratio.scaleLarge else 1f
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
    /** 꾸미기 (1.2): 표지 판화 (화첩에서 고른 것, 없으면 ""), 표지 헌사 한 줄, 잉크 색, 머리글 (라틴 · 한글), 앱 아이콘. */
    var coverPlate: String
        get() = prefs.getString("cover_plate", "") ?: ""
        set(v) = prefs.edit().putString("cover_plate", v).apply()
    var dedication: String
        get() = prefs.getString("dedication", "") ?: ""
        set(v) = prefs.edit().putString("dedication", v).apply()
    var ink: String
        get() = prefs.getString("ink_color", "ink") ?: "ink"
        set(v) = prefs.edit().putString("ink_color", v).apply()
    var latinHeads: Boolean
        get() = prefs.getBoolean("latin_heads", true)
        set(v) = prefs.edit().putBoolean("latin_heads", v).apply()
    var appIcon: String
        get() = prefs.getString("app_icon", "light") ?: "light"
        set(v) = prefs.edit().putString("app_icon", v).apply()
    /** 별점을 부탁한 적이 있는지 (한 번만). */
    var reviewAsked: Boolean
        get() = prefs.getBoolean("review_asked", false)
        set(v) = prefs.edit().putBoolean("review_asked", v).apply()
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
    /** 마지막으로 보여 준 ‘새로 바뀐 것’ (버전 이름, 예: "1.1"). */
    var newsSeen: String
        get() = prefs.getString("news_seen", "") ?: ""
        set(v) = prefs.edit().putString("news_seen", v).apply()
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
    /** 내보내기 메일의 받는 사람 (이 폰에만). */
    var simple: Boolean
        get() = prefs.getBoolean("simple", false)
        set(v) = prefs.edit().putBoolean("simple", v).apply()
    var mailTo: String
        get() = prefs.getString("mail_to", "").orEmpty()
        set(v) = prefs.edit().putString("mail_to", v).apply()
    /** 장 여는 화면을 이미 본 장 ("권:장"). */
    var openerSeen: Set<String>
        get() = prefs.getString("opener_seen", "").orEmpty().split(',').filter { it.isNotBlank() }.toSet()
        set(v) = prefs.edit().putString("opener_seen", v.joinToString(",")).apply()
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
    /** 다시 열 때 여는 순간을 한 번 건너뛸지 (언어를 바꾼 뒤). 읽으면 지워요. */
    var skipIntroOnce: Boolean
        get() = prefs.getBoolean("skip_intro", false)
        set(v) = prefs.edit().putBoolean("skip_intro", v).commit().let { }
    /** 바로 디스크에 (앱을 곧 닫을 때: apply 로 미뤄 둔 것까지). */
    fun flush() { prefs.edit().commit() }
    fun consumeSkipIntro(): Boolean = skipIntroOnce.also { if (it) skipIntroOnce = false }
    /** 앱 언어: system · ko · en. */
    var language: String
        get() = prefs.getString("language", "system")!!
        set(v) = prefs.edit().putString("language", v).apply()
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
    /** 영어 (KJV) 낭독 목소리. 영어 음원이 생기기 전에는 폰 목소리. */
    var narratorEn: String
        get() = prefs.getString("narrator_en", null) ?: Narration.MALE_EN.ifEmpty { Narration.DEVICE }
        set(v) = prefs.edit().putString("narrator_en", v).apply()
    /** 영어 목소리는 하나만 고르고 (남 · 여), 번역에 맞는 음원으로 바꿔 써요 (KJV en_* · WEB web_*). */
    fun narratorFor(tr: Translation) = when (tr) { Translation.KRV -> narrator; Translation.KJV -> narratorEn; Translation.WEB -> narratorEn.replace("en_", "web_") }
    fun setNarrator(tr: Translation, v: String) { if (tr == Translation.KRV) narrator = v else narratorEn = v.replace("web_", "en_") }
    /** 영어를 고르면 쓸 영어 성경: World English Bible (기본) · KJV. */
    var englishBible: Translation
        get() = if (prefs.getString("english_bible", "web") == "kjv") Translation.KJV else Translation.WEB
        set(v) = prefs.edit().putString("english_bible", v.id).apply()
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

    /** 읽기 화면 한영 대조. */
    var parallel: Boolean
        get() = prefs.getBoolean("parallel", false)
        set(v) = prefs.edit().putBoolean("parallel", v).apply()
    /** 기도 알림: 0 끄기 · 1 아침과 저녁 · 2 하루 네 번. */
    var prayerReminder: Int
        get() = prefs.getInt("prayer_reminder", 0)
        set(v) = prefs.edit().putInt("prayer_reminder", v).apply()
    /** 기도문마다 알림 시각 (아이디 → 하루 중 분). 없으면 꺼짐. 예전 '아침과 저녁 · 네 번' 설정은 처음 읽을 때 옮겨요. */
    var prayerTimes: Map<String, Int>
        get() {
            val raw = prefs.getString("prayer_times", null)
            if (raw == null) {
                val old = when (prayerReminder) { 1 -> mapOf("morning" to 7 * 60, "evening" to 18 * 60); 2 -> mapOf("morning" to 7 * 60, "noon" to 12 * 60, "evening" to 18 * 60, "night" to 21 * 60 + 30); else -> emptyMap() }
                return old
            }
            return raw.split(',').mapNotNull { e -> e.split('=').takeIf { it.size == 2 }?.let { (k, v) -> v.toIntOrNull()?.let { k to it } } }.toMap()
        }
        set(v) = prefs.edit().putString("prayer_times", v.entries.joinToString(",") { "${it.key}=${it.value}" }).apply()
    /** 오늘 드린 기도문 (날 · 아이디들). 날이 바뀌면 비어요. */
    fun prayedOn(day: Long): Set<String> = prefs.getString("prayed", "")!!.split('|').let { p -> if (p.firstOrNull() == day.toString()) p.drop(1).toSet() else emptySet() }
    fun setPrayed(day: Long, ids: Set<String>) = prefs.edit().putString("prayed", (listOf(day.toString()) + ids).joinToString("|")).apply()

    /** 마음에 새기는 말씀 (번역 · 절 · 담은 날). */
    private val memoryFile get() = java.io.File(context.filesDir, "memory.tsv")
    fun loadMemory(): List<Mark> = readMarks(memoryFile)
    fun saveMemory(list: List<Mark>) = writeMarks(memoryFile, list)

    /**
     * 내가 적은 글: 장마다 묵상 한 줄 (REFLECTION, 절 범위 없음) · 주일 설교 노트 (SERMON, 본문 절 범위).
     * 한 줄 = 종류 · 번역 · 권 · 장 · 처음 절 · 끝 절 · 날 · 글 (줄바꿈은 \n 으로).
     */
    enum class NoteKind { REFLECTION, SERMON }
    data class Note(val kind: NoteKind, val translation: Translation, val book: Int, val chapter: Int, val from: Int, val to: Int, val epochDay: Long, val text: String)
    private val notesFile get() = java.io.File(context.filesDir, "notes.tsv")
    fun loadNotes(): List<Note> = runCatching {
        notesFile.takeIf { it.exists() }?.readLines()?.mapNotNull { l ->
            val p = l.split('\t', limit = 8); if (p.size < 8) null
            else runCatching { Note(NoteKind.valueOf(p[0]), Translation.valueOf(p[1]), p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toInt(), p[6].toLong(), unescape(p[7])) }.getOrNull()
        }.orEmpty()
    }.getOrDefault(emptyList())
    fun saveNotes(list: List<Note>) {
        val tmp = java.io.File(notesFile.path + ".tmp")
        tmp.writeText(list.joinToString("") { "${it.kind.name}\t${it.translation.name}\t${it.book}\t${it.chapter}\t${it.from}\t${it.to}\t${it.epochDay}\t${escape(it.text)}\n" }); tmp.renameTo(notesFile)
    }
    private fun escape(t: String) = t.replace("\\", "\\\\").replace("\n", "\\n").replace('\t', ' ').replace("\r", "")
    private fun unescape(t: String): String {
        val b = StringBuilder(); var i = 0
        while (i < t.length) { val ch = t[i]; if (ch == '\\' && i + 1 < t.length) { b.append(if (t[i + 1] == 'n') '\n' else t[i + 1]); i += 2 } else { b.append(ch); i++ } }
        return b.toString()
    }

    private fun defaultTranslation() = if (android.content.res.Resources.getSystem().configuration.locales[0].language == "ko") Translation.KRV else englishBible
}
