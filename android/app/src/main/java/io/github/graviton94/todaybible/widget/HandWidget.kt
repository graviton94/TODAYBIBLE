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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import io.github.graviton94.todaybible.MainActivity
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Canon
import io.github.graviton94.todaybible.core.Translation
import io.github.graviton94.todaybible.data.Ink
import io.github.graviton94.todaybible.data.Store
import io.github.graviton94.todaybible.design.Fonts
import io.github.graviton94.todaybible.design.Tokens
import io.github.graviton94.todaybible.ui.Cards
import io.github.graviton94.todaybible.ui.drawStroke
import java.time.LocalDate

/** 내 손글씨 위젯 (S1): 손으로 쓴 절 하나를 내 글씨 그대로. 하루에 한 번 다른 절로. */
class HandWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) { ids.forEach { draw(ctx, manager, it) } }
    override fun onAppWidgetOptionsChanged(ctx: Context, manager: AppWidgetManager, id: Int, options: Bundle) = draw(ctx, manager, id)

    companion object {
        fun refresh(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            m.getAppWidgetIds(ComponentName(ctx, HandWidget::class.java)).forEach { draw(ctx, m, it) }
        }

        private fun draw(ctx: Context, m: AppWidgetManager, id: Int) {
            val o = m.getAppWidgetOptions(id)
            val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 320).coerceAtLeast(180)
            val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150).coerceAtLeast(110)
            val views = RemoteViews(ctx.packageName, R.layout.widget_verse)
            views.setImageViewBitmap(R.id.widget_image, runCatching { bitmap(ctx, wDp, hDp, VerseWidget.dark(ctx)) }.getOrNull())
            val open = Intent(ctx, MainActivity::class.java).putExtra("page", 1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(R.id.widget_image, PendingIntent.getActivity(ctx, 4, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            runCatching { m.updateAppWidget(id, views) }
        }

        /** 손으로 쓴 절들 (번역 · 권 · 장 · 절). */
        private fun written(ctx: Context): List<Triple<Translation, Triple<Int, Int, Int>, java.io.File>> =
            Ink.dir(ctx).listFiles()?.mapNotNull { f ->
                val p = f.name.removeSuffix(".ink").split('_')
                if (!f.name.endsWith(".ink") || p.size != 4) null
                else Translation.entries.firstOrNull { it.id == p[0] }?.let { tr -> runCatching { Triple(tr, Triple(p[1].toInt(), p[2].toInt(), p[3].toInt()), f) }.getOrNull() }
            }?.sortedBy { it.third.name }.orEmpty()

        /** 위젯 그림 (dp 크기). 캡처용으로도 씀. */
        fun bitmap(ctx: Context, wDp: Int, hDp: Int, dark: Boolean, today: LocalDate = LocalDate.now()): Bitmap {
            val d = ctx.resources.displayMetrics.density
            val c = if (dark) Tokens.dark else Tokens.light
            val all = written(ctx)
            val pick = all.getOrNull(Math.floorMod(today.toEpochDay(), all.size.coerceAtLeast(1)).toInt())
            val page = pick?.let { Ink.load(it.third) }
            val store = Store(ctx)
            return Cards.render(ctx, (wDp * d).toInt(), (hDp * d).toInt(), Density(d, 1f)) { m ->
                val pad = Tokens.Size.widgetPad.toPx(); val w = size.width - 2 * pad
                drawRoundRect(c.leaf, cornerRadius = CornerRadius(Tokens.Radius.page.toPx()))
                if (pick == null || page == null || page.sheets.isEmpty()) {
                    val t = m.measure(ctx.getString(R.string.hand_widget_empty), TextStyle(fontFamily = Fonts.serifKr, fontSize = Tokens.Text.body, color = c.inkSoft, textAlign = TextAlign.Center),
                        constraints = Constraints(maxWidth = w.toInt()))
                    drawText(t, topLeft = Offset(pad, (size.height - t.size.height) / 2))
                    return@render
                }
                val (tr, at, _) = pick; val (b, ch, v) = at
                val name = if (tr == Translation.KRV) Canon.books[b].ko else Canon.books[b].en
                val ref = m.measure("$name $ch:$v", TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Bold, fontSize = Tokens.Text.small, color = c.rubric))
                val bottom = size.height - pad - ref.size.height
                drawText(ref, topLeft = Offset(pad, bottom))
                // 손글씨: 첫 장을 너비에 맞추고, 높이가 넘치면 줄여서 다 보이게
                val sheet = page.sheets[0]
                val inkH = (page.height(0) + page.line * 0.25f) * w
                val room = bottom - pad - Tokens.Space.s1.toPx()
                val k = if (inkH > room) room / inkH else 1f
                val ink = if (page.pen == Ink.PENCIL) c.graphite else c.penInk
                clipRect(pad, pad, size.width - pad, pad + room) {
                    val lh = page.line * w * k; var y = pad + lh
                    while (y < pad + room) { drawLine(c.noteLine, Offset(pad, y), Offset(size.width - pad, y), Tokens.Stroke.hair.toPx()); y += lh }
                    translate(pad, pad) { scale(k, k, Offset.Zero) { sheet.forEach { drawStroke(it, w, ink, page.pen) } } }
                }
                if (store.ownerName.isNotBlank()) {
                    val n = m.measure(store.ownerName, TextStyle(fontFamily = Fonts.serifKr, fontSize = Tokens.Text.small, color = c.inkSoft))
                    drawText(n, topLeft = Offset(size.width - pad - n.size.width, bottom))
                }
            }
        }
    }
}
