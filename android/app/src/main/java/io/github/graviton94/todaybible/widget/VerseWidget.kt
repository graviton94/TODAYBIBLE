package io.github.graviton94.todaybible.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.RemoteViews
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import io.github.graviton94.todaybible.MainActivity
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Canon
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.core.Progress
import io.github.graviton94.todaybible.core.Translation
import io.github.graviton94.todaybible.data.Store
import io.github.graviton94.todaybible.design.Fonts
import io.github.graviton94.todaybible.design.ThemeChoice
import io.github.graviton94.todaybible.design.Tokens
import io.github.graviton94.todaybible.ui.Cards
import io.github.graviton94.todaybible.ui.Lang
import io.github.graviton94.todaybible.ui.stamp
import java.time.LocalDate

/**
 * 오늘의 한 절 위젯 (4×2): 다음에 옮겨 쓸 절 · 이 장 진행 · 이번 주 도장 · 오늘 쓴 절 수.
 * 앱과 같은 글꼴 · 토큰으로 그림 한 장을 그려 붙임. 누르면 필사 장으로.
 */
class VerseWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) { val r = goAsync(); Thread { try { ids.forEach { draw(ctx, manager, it) } } finally { r.finish() } }.start() }
    override fun onAppWidgetOptionsChanged(ctx: Context, manager: AppWidgetManager, id: Int, options: Bundle) { val r = goAsync(); Thread { try { draw(ctx, manager, id) } finally { r.finish() } }.start() }

    companion object {
        /** 앱에서 기록이 바뀌면 부름. */
        fun refresh(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            m.getAppWidgetIds(ComponentName(ctx, VerseWidget::class.java)).forEach { draw(ctx, m, it) }
        }

        private fun draw(ctx: Context, m: AppWidgetManager, id: Int) {
            val o = m.getAppWidgetOptions(id)
            // 세로 화면 기준 자리: 너비는 MIN_WIDTH, 높이는 MAX_HEIGHT
            val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320).coerceAtLeast(180)
            val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 150).coerceAtLeast(110)
            val views = RemoteViews(ctx.packageName, R.layout.widget_verse)
            views.setImageViewBitmap(R.id.widget_image, runCatching { bitmap(ctx, wDp, hDp, dark(ctx)) }.getOrNull())
            val open = Intent(ctx, MainActivity::class.java).putExtra("page", io.github.graviton94.todaybible.ui.AppState.COPY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(R.id.widget_image, PendingIntent.getActivity(ctx, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            runCatching { m.updateAppWidget(id, views) }
        }

        fun dark(ctx: Context): Boolean = when (Store(ctx).theme) {
            ThemeChoice.DARK, ThemeChoice.CANDLE -> true; ThemeChoice.LIGHT -> false
            ThemeChoice.SYSTEM -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

        /** 위젯 그림 (dp 크기). 캡처용으로도 씀. */
        fun bitmap(ctx: Context, wDp: Int, hDp: Int, dark: Boolean, today: LocalDate = LocalDate.now()): Bitmap {
            val d = ctx.resources.displayMetrics.density
            val density = Density(d, 1f)
            val c = if (dark) Tokens.dark else Tokens.light
            val store = Store(ctx)
            val fills = store.loadFills(); val tr = store.translation; val korean = tr == Translation.KRV
            val (b, ch) = store.bookmark(tr)
            val text = store.book(tr, b); val p = Progress(fills)
            val fillable = text.fillable(ch)
            val v = p.nextVerse(tr, text, ch)
            val done = fillable.count { p.isFilled(tr, io.github.graviton94.todaybible.core.VerseKey(b, ch, it)) }
            val days = p.days(); val todayN = fills.count { it.translation == tr && it.epochDay == today.toEpochDay() }
            val name = Canon.books[b].let { if (korean) it.ko else it.en }
            val phrase = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)
            return Cards.render(ctx, (wDp * d).toInt(), (hDp * d).toInt(), density) { m ->
                val pad = Tokens.Size.widgetPad.toPx(); val w = size.width - 2 * pad
                drawRoundRect(c.leaf, cornerRadius = CornerRadius(Tokens.Radius.page.toPx()))
                // 머리줄
                val head = m.measure(Lang.chapterRef(ctx, tr, name, ch).uppercase(Lang.locale(tr)), TextStyle(fontFamily = if (korean) Fonts.serifKr else Fonts.fell, fontWeight = FontWeight.Bold,
                    fontSize = Tokens.Text.small, letterSpacing = Tokens.Tracking.head.em, color = c.ink), maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = (w * 0.7f).toInt()))
                drawText(head, topLeft = Offset(pad, pad))
                val count = m.measure("$done / ${fillable.size}", TextStyle(fontFamily = Fonts.serifKr, fontSize = Tokens.Text.small, color = c.inkSoft))
                drawText(count, topLeft = Offset(size.width - pad - count.size.width, pad))
                val ruleY = pad + head.size.height + Tokens.Space.s1.toPx()
                drawLine(c.hair, Offset(pad, ruleY), Offset(size.width - pad, ruleY), Tokens.Stroke.hair.toPx())
                // 아래: 이번 주 도장 (주일부터) · 오늘 쓴 절
                val cell = Tokens.Size.icon.toPx()
                val bottomY = size.height - pad - cell
                val sunday = today.minusDays((today.dayOfWeek.value % 7).toLong())
                val letters = ctx.getString(R.string.weekdays)
                for (i in 0 until 7) {
                    val day = sunday.plusDays(i.toLong()); val cx = pad + cell / 2 + i * (cell + Tokens.Space.s1.toPx())
                    if (day.toEpochDay() in days) stamp(store.stamp, c.rubric, Offset(cx, bottomY + cell / 2), cell)
                    else {
                        val l = m.measure(letters[i].toString(), TextStyle(fontFamily = Fonts.serifKr, fontSize = Tokens.Text.small, color = if (day == today) c.ink else c.unwritten, textAlign = TextAlign.Center), constraints = Constraints.fixedWidth(cell.toInt()))
                        drawText(l, topLeft = Offset(cx - cell / 2, bottomY + (cell - l.size.height) / 2))
                    }
                }
                if (todayN > 0) {
                    val t = m.measure(ctx.getString(R.string.verses_n, todayN), TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Bold, fontSize = Tokens.Text.small, color = c.rubric))
                    drawText(t, topLeft = Offset(size.width - pad - t.size.width, bottomY + (cell - t.size.height) / 2))
                }
                // 가운데: 다음 한 절 (자리에 맞게 줄 수 · 넘치면 말줄임)
                val top = ruleY + Tokens.Space.s2.toPx(); val avail = bottomY - Tokens.Space.s2.toPx() - top
                val verseText = if (v == null) ctx.getString(R.string.chapter_done, name, ch) else Markup.plain(text.verse(ch, v))
                val body = buildAnnotatedString {
                    if (v != null) withStyle(SpanStyle(fontFamily = Fonts.black, color = c.rubric)) { append("$v ") }
                    append(verseText)
                }
                val style = TextStyle(fontFamily = if (korean) Fonts.serifKr else Fonts.garamond, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.body,
                    lineHeight = Tokens.Leading.body.em, color = c.ink, lineBreak = phrase)
                val one = m.measure("가", style).size.height.coerceAtLeast(1)
                val lines = (avail / one).toInt().coerceAtLeast(1)
                val lay = m.measure(body, style, maxLines = lines, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = w.toInt()))
                drawText(lay, topLeft = Offset(pad, top))
            }
        }
    }
}
