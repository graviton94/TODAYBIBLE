package io.github.graviton94.todaybible.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.RemoteViews
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import io.github.graviton94.todaybible.MainActivity
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Goal
import io.github.graviton94.todaybible.core.Presence
import io.github.graviton94.todaybible.core.Progress
import io.github.graviton94.todaybible.core.Translation
import io.github.graviton94.todaybible.data.Store
import io.github.graviton94.todaybible.design.Fonts
import io.github.graviton94.todaybible.design.Tokens
import io.github.graviton94.todaybible.ui.Cards
import io.github.graviton94.todaybible.ui.stamp
import java.time.LocalDate
import java.time.LocalDateTime

/** 작은 위젯 (L1) 공통: 앱과 같은 토큰으로 그림 한 장. 누르면 open(ctx) 으로. */
abstract class SmallWidget : AppWidgetProvider() {
    abstract fun draw(ctx: Context, wDp: Int, hDp: Int, dark: Boolean): Bitmap
    /** 화면 읽기 (TalkBack) 용 한 줄. */
    abstract fun describe(ctx: Context): String
    /** 누르면 열 곳. */
    abstract fun open(ctx: Context): Intent
    // 그림 그리기는 뒤에서 (받는 쪽 시간 제한 안에)
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) { val r = goAsync(); Thread { try { ids.forEach { update(ctx, m, it) } } finally { r.finish() } }.start() }
    override fun onAppWidgetOptionsChanged(ctx: Context, m: AppWidgetManager, id: Int, o: Bundle) { val r = goAsync(); Thread { try { update(ctx, m, id) } finally { r.finish() } }.start() }
    private fun update(ctx0: Context, m: AppWidgetManager, id: Int) {
        val ctx = io.github.graviton94.todaybible.ui.Lang.wrap(ctx0)   // 앱에서 고른 언어로
        val o = m.getAppWidgetOptions(id)
        // 세로 화면 기준 자리 (가로 · 세로 중 작은 쪽으로 정사각)
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110).coerceAtLeast(60)
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110).coerceAtLeast(60)
        val views = RemoteViews(ctx.packageName, R.layout.widget_verse)
        views.setImageViewBitmap(R.id.widget_image, runCatching { draw(ctx, w, h, VerseWidget.dark(ctx)) }.getOrNull())
        runCatching { views.setContentDescription(R.id.widget_image, describe(ctx)) }
        views.setOnClickPendingIntent(R.id.widget_image, PendingIntent.getActivity(ctx, 3, open(ctx).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        runCatching { m.updateAppWidget(id, views) }
    }
    companion object {
        fun refreshAll(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            listOf(GoalWidget::class.java).forEach { cls ->
                val ids = m.getAppWidgetIds(ComponentName(ctx, cls)); if (ids.isNotEmpty()) ctx.sendBroadcast(Intent(ctx, cls).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
            }
        }
    }
}

private fun frame(ctx: Context, wDp: Int, hDp: Int, dark: Boolean, block: androidx.compose.ui.graphics.drawscope.DrawScope.(androidx.compose.ui.text.TextMeasurer, io.github.graviton94.todaybible.design.Palette) -> Unit): Bitmap {
    val d = ctx.resources.displayMetrics.density
    val c = if (dark) Tokens.dark else Tokens.light
    return Cards.render(ctx, (wDp * d).toInt(), (hDp * d).toInt(), Density(d, 1f)) { m ->
        drawRoundRect(c.leaf, cornerRadius = CornerRadius(Tokens.Radius.page.toPx()))
        block(m, c)
    }
}

/**
 * 다음 기도 (2×2, 1.5): 켜 둔 기도 알림 가운데 다음 것 ("저녁 기도 · 오후 6:00") 과 그 기도의 첫 말씀. 내 기록은 읽지 않아요.
 * 켠 기도가 없으면 매일 알림 시각, 그것도 없으면 기도 알림 켜는 안내. 누르면 그 기도문.
 * 기도 알림이 울릴 때 (PrayerReceiver) · 자정 · 앱을 떠날 때 다시 그려요.
 * (1.4 까지는 ‘오늘의 분량’: 오늘 쓴 절 고리 · 며칠째. 클래스 이름은 그대로라 홈 화면의 위젯이 이 모습으로 바뀌어요)
 */
class GoalWidget : SmallWidget() {
    /** 다음 기도: id 가 null 이면 매일 알림. */
    data class Next(val id: String?, val name: String, val time: String, val tomorrow: Boolean, val verse: String, val ref: String)

    companion object {
        /** 앱에서 고른 언어로 시각 ("오후 6:00" · "6:00 PM"). */
        private fun clock(ctx: Context, min: Int): String { val loc = ctx.resources.configuration.locales[0]
            return java.time.LocalTime.of(min / 60, min % 60).format(java.time.format.DateTimeFormatter.ofPattern(if (loc.language == "ko") "a h:mm" else "h:mm a", loc)) }
        private fun prayerNext(ctx: Context, id: String, min: Int, tomorrow: Boolean): Next {
            val st = Store(ctx); val tr = st.translation
            val p = io.github.graviton94.todaybible.core.Prayers.byId(id)!!
            val first = p.passages.first()
            val name = runCatching { ctx.getString(ctx.resources.getIdentifier("prayer_$id", "string", ctx.packageName)) }.getOrDefault(id)
            val bookName = io.github.graviton94.todaybible.core.Canon.books[first.book].let { if (io.github.graviton94.todaybible.ui.Lang.uiKorean(ctx)) it.ko else it.en }
            val verse = runCatching { io.github.graviton94.todaybible.core.Markup.plain(st.book(tr, first.book).verse(first.chapter, first.from)) }.getOrDefault("")
            return Next(id, name, clock(ctx, min), tomorrow, verse, "$bookName ${first.chapter}:${first.from}")
        }
        /** 위젯 고르는 화면 · 캡처의 예시: 저녁 기도 오후 6시. */
        fun sample(ctx: Context): Next = prayerNext(ctx, "evening", 18 * 60, false)

        fun next(ctx: Context, now: LocalDateTime = LocalDateTime.now()): Next? {
            val st = Store(ctx); val nowMin = now.hour * 60 + now.minute
            val times = st.prayerTimes.filterKeys { io.github.graviton94.todaybible.core.Prayers.byId(it) != null }
            if (times.isNotEmpty()) {
                val sorted = times.entries.sortedBy { it.value }
                val today = sorted.firstOrNull { it.value > nowMin }
                val (id, min) = (today ?: sorted.first()).let { it.key to it.value }
                return prayerNext(ctx, id, min, today == null)
            }
            if (st.reminderHour >= 0) { val min = st.reminderHour * 60 + st.reminderMinute; return Next(null, ctx.getString(R.string.reminder), clock(ctx, min), min <= nowMin, "", "") }
            return null
        }
    }

    override fun open(ctx: Context): Intent {
        val n = next(ctx)
        return if (n?.id != null) Intent(ctx, MainActivity::class.java).setAction("prayer_widget_${n.id}").putExtra("prayer", n.id)
        else Intent(ctx, MainActivity::class.java).putExtra("page", io.github.graviton94.todaybible.ui.AppState.TODAY)
    }

    override fun describe(ctx: Context): String {
        val n = next(ctx) ?: return ctx.getString(R.string.widget_goal) + " · " + ctx.getString(R.string.prayer_reminder_none)
        return listOf(ctx.getString(R.string.widget_goal), n.name, (if (n.tomorrow) ctx.getString(R.string.widget_tomorrow) + " " else "") + n.time, n.ref, n.verse).filter { it.isNotBlank() }.joinToString(" · ")
    }

    override fun draw(ctx: Context, wDp: Int, hDp: Int, dark: Boolean): Bitmap = paint(ctx, wDp, hDp, dark, next(ctx))

    /** 값을 받아 그리기 (위젯 고르는 화면의 미리보기는 보기 좋은 예시 값으로). */
    fun paint(ctx: Context, wDp: Int, hDp: Int, dark: Boolean, n: Next?): Bitmap = frame(ctx, wDp, hDp, dark) { m, c ->
        // 1.5 틀: 금빛 머리글 · 기도 이름 · 금빛 시각 · 그 기도의 첫 말씀 (칸에 맞춰) · 장절
        val pad = Tokens.Size.widgetPad.toPx(); val w = (size.width - 2 * pad).toInt()
        val latin = Store(ctx).latinHeads
        val cap = m.measure(if (latin) "ORATIO" else ctx.getString(R.string.widget_goal), TextStyle(fontFamily = if (latin) Fonts.caps else Fonts.serifKr, fontWeight = FontWeight.SemiBold, fontSize = Tokens.Text.caps, letterSpacing = Tokens.Tracking.caps.em, color = c.giltText), maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = w))
        drawText(cap, topLeft = Offset(pad, pad))
        var y = pad + cap.size.height + Tokens.Space.s1.toPx()
        if (n == null) {
            val hint = TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.small, lineHeight = Tokens.Leading.small.em, color = c.inkSoft)
            drawText(fitText(m, ctx.getString(R.string.prayer_reminder_none), hint, w, size.height - pad - y, Tokens.Text.widgetMin.value), topLeft = Offset(pad, y))
            return@frame
        }
        val title = m.measure(n.name, TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.SemiBold, fontSize = Tokens.Text.body * 1.25f, color = c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = w))
        drawText(title, topLeft = Offset(pad, y)); y += title.size.height
        val time = m.measure((if (n.tomorrow) ctx.getString(R.string.widget_tomorrow) + " " else "") + n.time, TextStyle(fontFamily = Fonts.display, fontWeight = FontWeight.SemiBold, fontSize = Tokens.Text.body, color = c.giltText), maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = w))
        drawText(time, topLeft = Offset(pad, y)); y += time.size.height + Tokens.Space.s1.toPx()
        // 아래: 장절, 그 위 남은 자리에 첫 말씀
        if (n.verse.isNotEmpty()) {
            val ref = m.measure(n.ref, TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.caps, color = c.inkSoft), maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = w))
            val refY = size.height - pad - ref.size.height
            drawText(ref, topLeft = Offset(pad, refY))
            val k = Store(ctx).translation == Translation.KRV
            val vs = TextStyle(fontFamily = if (k) Fonts.serifKr else Fonts.garamond, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.small, lineHeight = Tokens.Leading.small.em, color = c.ink)
            val room = refY - Tokens.Space.s1.toPx() - y
            if (room > 0) drawText(fitText(m, n.verse, vs, w, room, Tokens.Text.widgetMin.value), topLeft = Offset(pad, y))
        }
    }
}
