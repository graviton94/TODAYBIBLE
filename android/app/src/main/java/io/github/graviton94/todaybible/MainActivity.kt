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
        val store = Store(applicationContext)
        if (BuildConfig.DEV_TOOLS && intent.getBooleanExtra("tb.reset", false)) store.reset()
        val s = AppState(store).also { state = it }
        if (BuildConfig.DEV_TOOLS) debugSetup(s, intent)
        setContent {
            val dark = when (s.theme) { ThemeChoice.SYSTEM -> isSystemInDarkTheme(); ThemeChoice.LIGHT -> false; ThemeChoice.DARK -> true }
            LaunchedEffect(dark) {
                val bar = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(bar, bar)
            }
            TodayTheme(s.theme, s.scale) { Root(s) }
        }
    }

    /**
     * 캡처 · 시험용 (debug 빌드만): tb.seed 시험 기록, tb.today 날짜, tb.tr 번역, tb.theme, tb.page,
     * tb.settings, tb.finished "권:장" (1부터), tb.award 발자취 이름, tb.open "권:장", tb.scale.
     */
    private fun debugSetup(s: AppState, i: Intent) {
        i.getStringExtra("tb.today")?.let { s.fixedToday = LocalDate.parse(it) }
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
        s.toast = i.getIntExtra("tb.toast", 0).takeIf { it > 0 }?.let { getString(R.string.filled_n, it) }
    }
}
