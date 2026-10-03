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
    override fun onUpdate(ctx: Context, m: AppWidgetManager, ids: IntArray) { ids.forEach { update(ctx, m, it) } }
    override fun onAppWidgetOptionsChanged(ctx: Context, m: AppWidgetManager, id: Int, o: Bundle) = update(ctx, m, id)
    private fun update(ctx: Context, m: AppWidgetManager, id: Int) {
        val o = m.getAppWidgetOptions(id)
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110).coerceAtLeast(60)
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110).coerceAtLeast(60)
        val views = RemoteViews(ctx.packageName, R.layout.widget_verse)
        views.setImageViewBitmap(R.id.widget_image, runCatching { draw(ctx, w, h, VerseWidget.dark(ctx)) }.getOrNull())
        val open = Intent(ctx, MainActivity::class.java).putExtra("page", 0).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        views.setOnClickPendingIntent(R.id.widget_image, PendingIntent.getActivity(ctx, 2, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        runCatching { m.updateAppWidget(id, views) }
    }
    companion object {
        fun refreshAll(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            listOf(GoalWidget::class.java, RunWidget::class.java).forEach { cls ->
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

/** 오늘의 분량 고리 (1×1). 다 채우면 금빛 고리 안에 도장. */
class GoalWidget : SmallWidget() {
    override fun draw(ctx: Context, wDp: Int, hDp: Int, dark: Boolean): Bitmap = frame(ctx, wDp, hDp, dark) { m, c ->
        val (n, goal, _) = today(ctx)
        val met = if (goal == Goal.CHAPTER) false else n >= goal
        val side = minOf(size.width, size.height) * 0.62f; val w = Tokens.Stroke.rule.toPx() * 2.5f
        val tl = Offset((size.width - side) / 2, size.height * 0.1f)
        drawArc(c.hair, 0f, 360f, false, tl, Size(side, side), style = Stroke(w))
        val frac = if (goal <= 0) 0f else (n.toFloat() / goal).coerceIn(0f, 1f)
        drawArc(if (met) c.gilt else c.rubric, -90f, 360f * frac, false, tl, Size(side, side), style = Stroke(w, cap = StrokeCap.Round))
        val center = Offset(size.width / 2, tl.y + side / 2)
        if (met) stamp(io.github.graviton94.todaybible.ui.STAMP_CROSS, c.rubric, center, side * 0.4f)
        else {
            val t = m.measure(if (goal == Goal.CHAPTER) "$n" else "$n/$goal", TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Bold, fontSize = Tokens.Text.label, color = c.ink, textAlign = TextAlign.Center), constraints = Constraints.fixedWidth(side.toInt()))
            drawText(t, topLeft = Offset(tl.x, center.y - t.size.height / 2))
        }
        val l = m.measure(ctx.getString(R.string.widget_goal), TextStyle(fontFamily = Fonts.serifKr, fontSize = Tokens.Text.small, color = c.inkSoft, textAlign = TextAlign.Center), maxLines = 1, constraints = Constraints.fixedWidth(size.width.toInt()))
        drawText(l, topLeft = Offset(0f, size.height * 0.9f - l.size.height))
    }
}

/** 이어 쓴 날 (1×1): 큰 숫자 하나. */
class RunWidget : SmallWidget() {
    override fun draw(ctx: Context, wDp: Int, hDp: Int, dark: Boolean): Bitmap = frame(ctx, wDp, hDp, dark) { m, c ->
        val (_, _, run) = today(ctx)
        val n = m.measure("$run", TextStyle(fontFamily = Fonts.titleKr, fontSize = Tokens.Text.display * 1.2f, color = c.ink, textAlign = TextAlign.Center), constraints = Constraints.fixedWidth(size.width.toInt()))
        drawText(n, topLeft = Offset(0f, size.height * 0.46f - n.size.height / 2))
        val l = m.measure(ctx.getString(R.string.run_label), TextStyle(fontFamily = Fonts.serifKr, fontSize = Tokens.Text.small, color = c.inkSoft, textAlign = TextAlign.Center), maxLines = 1, constraints = Constraints.fixedWidth(size.width.toInt()))
        drawText(l, topLeft = Offset(0f, size.height * 0.9f - l.size.height))
        if (run > 0) stamp(io.github.graviton94.todaybible.ui.STAMP_CROSS, c.rubric, Offset(size.width / 2, size.height * 0.16f), Tokens.Size.iconSm.toPx())
    }
}


