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

/** 작은 위젯 (L1) 공통: 앱과 같은 토큰으로 그림 한 장, 누르면 오늘 화면. */
abstract class SmallWidget : AppWidgetProvider() {
    abstract fun draw(ctx: Context, wDp: Int, hDp: Int, dark: Boolean): Bitmap
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
        val open = Intent(ctx, MainActivity::class.java).putExtra("page", 0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        views.setOnClickPendingIntent(R.id.widget_image, PendingIntent.getActivity(ctx, 3, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        runCatching { m.updateAppWidget(id, views) }
    }
    companion object {
        fun refreshAll(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            listOf(GoalWidget::class.java).forEach { cls ->
                val ids = m.getAppWidgetIds(ComponentName(ctx, cls)); if (ids.isNotEmpty()) ctx.sendBroadcast(Intent(ctx, cls).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE).putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids))
            }
        }
        /** 오늘 쓴 절 · 오늘 분량 (길잡이가 있으면 길잡이 몫은 앱에서 계산해 둔 값). */
        fun today(ctx: Context): Triple<Int, Int, Int> {
            val s = Store(ctx); val fills = s.loadFills(); val tr = s.translation; val d = LocalDate.now()
            val n = fills.count { it.translation == tr && it.epochDay == d.toEpochDay() }
            val goal = s.lastGoal.takeIf { it != 0 } ?: s.dailyGoal
            return Triple(n, goal, Presence.streak(Progress(fills).days(), d))
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

/** 오늘 (2×2): 오늘의 분량 고리 + 며칠째 이어 쓰는지. 다 채우면 금빛 고리 안에 도장. (예전 '이어 쓴 날' 위젯을 합쳤어요) */
class GoalWidget : SmallWidget() {
    override fun draw(ctx: Context, wDp: Int, hDp: Int, dark: Boolean): Bitmap { val (n, goal, run) = today(ctx); return paint(ctx, wDp, hDp, dark, n, goal, run) }
    /** 값을 받아 그리기 (위젯 고르는 화면의 미리보기는 보기 좋은 예시 값으로). */
    fun paint(ctx: Context, wDp: Int, hDp: Int, dark: Boolean, n: Int, goal: Int, run: Int): Bitmap = frame(ctx, wDp, hDp, dark) { m, c ->
        // 1.2 틀: 금빛 머리글 · 큰 숫자 (며칠째) · 가는 금선 고리 (오늘 분량) · 아래 한 줄
        val pad = Tokens.Size.widgetPad.toPx()
        val cap = m.measure("CONTINUA", TextStyle(fontFamily = Fonts.caps, fontWeight = FontWeight.SemiBold, fontSize = Tokens.Text.caps, letterSpacing = Tokens.Tracking.caps.em, color = c.giltText), maxLines = 1)
        drawText(cap, topLeft = Offset(pad, pad))
        val side = minOf(size.width, size.height) * 0.26f; val tl = Offset(size.width - pad - side, pad + cap.size.height + Tokens.Space.s2.toPx())
        val frac = (n.toFloat() / (if (goal < 0) Goal.LONG * Goal.chapters(goal) else goal)).coerceIn(0f, 1f)
        drawArc(c.hair, 0f, 360f, false, tl, Size(side, side), style = Stroke(Tokens.Stroke.hair.toPx() * 1.4f))
        drawArc(c.gilt, -90f, 360f * frac, false, tl, Size(side, side), style = Stroke(Tokens.Stroke.rule.toPx() * 1.6f, cap = StrokeCap.Round))
        // 큰 숫자는 위젯 높이에 맞춰 (작은 2×2 에서도 머리글과 겹치지 않게)
        val numSp = minOf(Tokens.Text.numBig.value, (size.height * 0.3f / density / fontScale))
        val big = m.measure("$run", TextStyle(fontFamily = Fonts.display, fontWeight = FontWeight.SemiBold, fontSize = numSp.sp, color = c.giltText), maxLines = 1)
        val w = (size.width - 2 * pad).toInt()
        val unit = m.measure(ctx.getString(R.string.widget_streak_unit), TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.caps, color = c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = w))
        val today = m.measure(if (goal < 0) ctx.getString(R.string.today_written, n) else ctx.getString(R.string.widget_today, n, goal), TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.caps, color = c.inkSoft), maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = w))
        drawText(today, topLeft = Offset(pad, size.height - pad - today.size.height))
        val unitY = size.height - pad - today.size.height - Tokens.Space.s1.toPx() - unit.size.height
        drawText(unit, topLeft = Offset(pad, unitY))
        drawText(big, topLeft = Offset(pad, maxOf(pad + cap.size.height, unitY - big.size.height + Tokens.Space.s1.toPx())))
    }
}
