package io.github.graviton94.todaybible

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import io.github.graviton94.todaybible.core.Milestone
import io.github.graviton94.todaybible.core.Translation
import io.github.graviton94.todaybible.data.Store
import io.github.graviton94.todaybible.design.ThemeChoice
import io.github.graviton94.todaybible.design.TodayTheme
import io.github.graviton94.todaybible.ui.AppState
import io.github.graviton94.todaybible.ui.Root
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private var state: AppState? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        io.github.graviton94.todaybible.data.CrashLog.install(this)
        val store = Store(applicationContext)
        if (BuildConfig.DEV_TOOLS && intent.getBooleanExtra("tb.reset", false)) store.reset()
        val s = AppState(store).also { state = it }
        s.lifetime.connect()
        if (BuildConfig.DEV_TOOLS) debugSetup(s, intent)
        intent.getIntExtra("page", -1).takeIf { it >= 0 }?.let { s.page = it; s.opening = false }
        openAloud(s, intent)
        // 알림 다시 맞추기 (끈 상태면 남은 알림을 지움) · 위젯 새로 그리기 (되살리기 · 업데이트 뒤에도 맞게)
        io.github.graviton94.todaybible.data.Reminder.schedule(applicationContext, store.reminderHour)
        s.widgets(); s.warmPlan()
        setContent {
            val dark = s.night || when (s.theme) { ThemeChoice.SYSTEM -> isSystemInDarkTheme(); ThemeChoice.LIGHT -> false; ThemeChoice.DARK -> true }
            LaunchedEffect(dark) {
                val bar = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(bar, bar)
            }
            TodayTheme(s.theme, s.scale, s.night, s.contrast) { Root(s) }
            androidx.compose.runtime.LaunchedEffect(s.opening) { if (!s.opening) s.store.openedDay = s.today().toEpochDay() }
        }
    }

    /** 캡처용: 위젯 (라이트 · 다크) · 나누기 카드 셋을 앱 폴더에 그림 파일로. */
    private fun cardShots(s: AppState) {
        val dir = java.io.File(getExternalFilesDir(null), "cards").apply { mkdirs() }
        fun save(name: String, b: android.graphics.Bitmap) = java.io.File(dir, "$name.png").outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val day = s.today()
        runCatching {
            save("widget_light", io.github.graviton94.todaybible.widget.VerseWidget.bitmap(this, 360, 170, false, day))
            save("widget_dark", io.github.graviton94.todaybible.widget.VerseWidget.bitmap(this, 360, 170, true, day))
            save("widget_goal", io.github.graviton94.todaybible.widget.GoalWidget().draw(this, 110, 110, false))
            save("widget_run", io.github.graviton94.todaybible.widget.RunWidget().draw(this, 110, 110, false))
            save("widget_goal_dark", io.github.graviton94.todaybible.widget.GoalWidget().draw(this, 110, 110, true))
            save("widget_hand", io.github.graviton94.todaybible.widget.HandWidget.bitmap(this, 360, 170, false, day))
            val k = s.korean; val now = day.atTime(7, 12)
            val ref = if (k) "${s.bookName(0)} 1:1" else "${s.bookName(0)} 1:1"
            save("card_verse", io.github.graviton94.todaybible.ui.Cards.verse(this, k, ref, s.store.book(s.translation, 0).verse(1, 1), "noah", now))
            val long = s.store.book(s.translation, 18).verse(23, 4)
            save("card_verse_long", io.github.graviton94.todaybible.ui.Cards.verse(this, k, if (k) "${s.bookName(18)} 23:4" else "${s.bookName(18)} 23:4", long, "sermon", now))
            save("card_plate", io.github.graviton94.todaybible.ui.Cards.plate(this, k, "noah", if (k) "홍수" else "The Deluge", if (k) "${s.bookName(0)} 7장" else "${s.bookName(0)} 7", now))
            save("card_milestone", io.github.graviton94.todaybible.ui.Cards.milestone(this, k, Milestone.OLIVE, io.github.graviton94.todaybible.ui.milestoneName(this, Milestone.OLIVE), io.github.graviton94.todaybible.ui.milestoneRule(this, Milestone.OLIVE), now))
            // 나의 성경 PDF (창세기, 쓴 절만 날짜)
            io.github.graviton94.todaybible.ui.MyBible.make(this, s.store, s.translation, 0).copyTo(java.io.File(dir, "my_bible.pdf"), overwrite = true)
        }.onFailure { java.io.File(dir, "error.txt").writeText(it.stackTraceToString()) }
        java.io.File(dir, "done").writeText("ok")
    }

    // 알림을 눌러 다시 열 때: 필사 장으로
    override fun onResume() {
        super.onResume()
        state?.checkNight()
    }

    override fun onStop() {
        super.onStop()
        // 화면 줄을 붙잡지 않게 뒤에서
        val app = applicationContext
        Thread { runCatching { io.github.graviton94.todaybible.data.Backup.auto(app) } }.start()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getIntExtra("page", -1).takeIf { it >= 0 }?.let { state?.page = it }
        state?.let { openAloud(it, intent) }
    }

    /** 아침 알림의 ‘함께 읽기’: 그 절로 열고 낭독을 곧바로. */
    private fun openAloud(s: AppState, i: Intent) {
        if (!i.getBooleanExtra("aloud", false)) return
        i.removeExtra("aloud")
        getSystemService(android.app.NotificationManager::class.java)?.cancel(io.github.graviton94.todaybible.data.Reminder.ID)
        val b = i.getIntExtra("at_b", -1); val c = i.getIntExtra("at_c", -1); val v = i.getIntExtra("at_v", -1)
        s.opening = false
        if (b >= 0 && c > 0) { s.open(b, c); if (v > 0) s.target = v }
        s.store.copyTab = 0; s.page = io.github.graviton94.todaybible.ui.AppState.COPY; s.aloudNow = true
    }

    /**
     * 캡처 · 시험용 (debug 빌드만): tb.seed 시험 기록, tb.today 날짜, tb.tr 번역, tb.theme, tb.page,
     * tb.settings, tb.finished "권:장" (1부터), tb.award 발자취 이름, tb.open "권:장", tb.scale.
     */
    private fun debugSetup(s: AppState, i: Intent) {
        i.getStringExtra("tb.today")?.let { s.fixedToday = LocalDate.parse(it) }
        if (i.extras?.keySet()?.any { it.startsWith("tb.") } == true) { s.nightOverride = i.getBooleanExtra("tb.night", false); s.checkNight() }
        i.getStringExtra("tb.mark")?.let { r -> r.split(',').forEach { v -> s.toggleMark(io.github.graviton94.todaybible.core.VerseKey(s.book, s.chapter, v.trim().toInt())) } }
        // 첫 안내: true = 처음처럼 다시, false = 모두 본 것으로 (캡처용)
        if (i.hasExtra("tb.coach")) { if (i.getBooleanExtra("tb.coach", false)) s.coachReset() else { s.coachSeen = io.github.graviton94.todaybible.ui.Coach.steps.keys; s.store.coachSeen = s.coachSeen } }
        i.getStringExtra("tb.listen")?.split(':')?.let { s.read(it[0].toInt() - 1, it[1].toInt()) }
        if (i.hasExtra("tb.aloudMode")) s.chooseAloudMode(i.getIntExtra("tb.aloudMode", 0))
        if (i.hasExtra("tb.aloudBig")) { if (s.aloudBig != i.getBooleanExtra("tb.aloudBig", true)) s.flipAloudBig() }
        if (i.hasExtra("tb.contrast")) { if (s.contrast != i.getBooleanExtra("tb.contrast", false)) s.flipContrast() }
        if (i.hasExtra("tb.year")) s.forceYear = i.getBooleanExtra("tb.year", false)
        if (i.hasExtra("tb.pen")) s.pen = i.getIntExtra("tb.pen", 0)
        if (i.hasExtra("tb.guide")) s.handGuide = i.getBooleanExtra("tb.guide", true)
        if (i.hasExtra("tb.handBook")) s.handBook = i.getIntExtra("tb.handBook", 0)
        i.getStringExtra("tb.tr")?.let { s.chooseTranslation(Translation.valueOf(it)) }
        i.getStringExtra("tb.theme")?.let { s.setThemeChoice(ThemeChoice.valueOf(it)) }
        if (i.hasExtra("tb.scale")) s.setTextScale(i.getFloatExtra("tb.scale", 1f))
        i.getStringExtra("tb.stamp")?.let { s.setStampMark(it) }
        if (i.getBooleanExtra("tb.seed", false)) s.seedDemo()
        i.getStringExtra("tb.open")?.split(':')?.let { s.open(it[0].toInt() - 1, it[1].toInt()) }
        if (i.hasExtra("tb.page")) s.page = i.getIntExtra("tb.page", 0)
        s.settingsOpen = i.getBooleanExtra("tb.settings", false)
        i.getStringExtra("tb.finished")?.split(':')?.let { s.finished = it[0].toInt() - 1 to it[1].toInt() }
        s.award = i.getStringExtra("tb.award")?.let { Milestone.valueOf(it) }
        s.picker = i.getIntExtra("tb.picker", 0).takeIf { it > 0 }?.minus(1)
        if (i.getBooleanExtra("tb.lock", false)) s.forceLock = true
        if (i.getBooleanExtra("tb.onboard", false)) { s.onboarded = false; s.store.onboarded = false }
        s.opening = i.getBooleanExtra("tb.opening", false)
        if (i.hasExtra("tb.goal")) s.setGoal(i.getIntExtra("tb.goal", 5))
        // 필사 탭: 캡처는 따로 말하지 않으면 타자로 (예전 장면 그대로)
        if (i.extras?.keySet()?.any { it.startsWith("tb.") } == true) s.store.copyTab = i.getIntExtra("tb.copyTab", 1)
        if (i.hasExtra("tb.notebook")) { if (s.notebook != i.getBooleanExtra("tb.notebook", false)) s.toggleNotebook() }
        s.plateView = i.getStringExtra("tb.plate")?.let { id -> s.store.plates.firstOrNull { it.id == id } }
        s.peekBook = i.getIntExtra("tb.peek", 0).takeIf { it > 0 }?.minus(1)?.also { s.purchaseOpen = true }
        i.getStringExtra("tb.plan")?.let { s.choosePlan(it); s.store.planStart = s.today().toEpochDay() - 2 }
        i.getStringExtra("tb.cover")?.let { s.chooseCover(it) }
        i.getStringExtra("tb.owner")?.let { s.setOwner(it) }
        if (i.getBooleanExtra("tb.planSheet", false)) s.planOpen = true
        if (i.hasExtra("tb.voice") && s.voiceOn != i.getBooleanExtra("tb.voice", false)) s.toggleVoice()
        if (i.hasExtra("tb.welcomeStep")) s.welcomeStep = i.getIntExtra("tb.welcomeStep", 0)
        s.lifetime.debugSet(if (i.hasExtra("tb.owned")) i.getBooleanExtra("tb.owned", false) else null, i.getStringExtra("tb.price"))
        if (i.getBooleanExtra("tb.purchase", false)) s.purchaseOpen = true
        s.shareVerse = i.getIntExtra("tb.share", 0).takeIf { it > 0 }?.let { io.github.graviton94.todaybible.core.VerseKey(s.book, s.chapter, it) }
        if (i.getBooleanExtra("tb.cardShots", false)) Thread { cardShots(s) }.start()
        s.toast = i.getIntExtra("tb.toast", 0).takeIf { it > 0 }?.let { getString(R.string.filled_n, it) }
    }
}
