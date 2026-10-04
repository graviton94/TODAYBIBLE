package io.github.graviton94.todaybible.ui

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Translation
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

/**
 * 말 두 겹 (코드에 언어별 글을 두지 않아요 — 모두 strings.json):
 *  · 화면 말 (단추 · 안내 · 날짜): 폰 언어 설정을 따라요 → 보통의 stringResource / [date].
 *  · 본문 말 (권 이름과 장 표기 · 음성 인식 언어 · 카드의 앱 이름): 고른 번역 (개역한글 · KJV) 을 따라요 → [content].
 */
object Lang {
    fun locale(tr: Translation): Locale = if (tr == Translation.KRV) Locale.KOREAN else Locale.ENGLISH
    private val cache = java.util.concurrent.ConcurrentHashMap<Pair<Translation, Int>, Context>()
    /** 번역의 언어로 된 문자열 묶음. */
    fun content(ctx: Context, tr: Translation): Context = cache.getOrPut(tr to System.identityHashCode(ctx.applicationContext)) {
        val c = Configuration(ctx.resources.configuration); c.setLocale(locale(tr)); ctx.createConfigurationContext(c)
    }
    fun content(ctx: Context, korean: Boolean): Context = content(ctx, if (korean) Translation.KRV else Translation.KJV)
    /** 권 · 장 (마가복음 3장 / Mark 3) — 번역의 언어로. */
    fun chapterRef(ctx: Context, tr: Translation, book: String, chapter: Int): String = content(ctx, tr).getString(R.string.ref_chapter, book, chapter)
    /** 날짜 · 시각: 형식은 strings.json 에서, 언어는 폰 설정대로. */
    fun date(ctx: Context, @StringRes pattern: Int, t: TemporalAccessor): String {
        val l = ctx.resources.configuration.locales[0] ?: Locale.getDefault()
        return DateTimeFormatter.ofPattern(ctx.getString(pattern), l).format(t)
    }
    /** 음성 인식 · 읽기 엔진 언어 (번역을 따라). */
    /** 설교 본문 같은 범위 표기: 장 전체 · 한 절 · 몇 절부터 몇 절 · 몇 절 이하. */
    fun passage(ctx: Context, tr: Translation, book: String, chapter: Int, from: Int, to: Int): String {
        val c = content(ctx, tr)
        return when {
            from <= 1 && to == 0 -> c.getString(R.string.ref_chapter, book, chapter)
            to == 0 -> c.getString(R.string.ref_onward, book, chapter, from)
            to <= from -> c.getString(R.string.ref_verse, book, chapter, from)
            else -> c.getString(R.string.ref_range, book, chapter, from, to)
        }
    }
    fun speechTag(ctx: Context, tr: Translation): String = content(ctx, tr).getString(R.string.speech_lang)
}

@Composable
fun fmtDate(@StringRes pattern: Int, t: TemporalAccessor): String = Lang.date(LocalContext.current, pattern, t)
