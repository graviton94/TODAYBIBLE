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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.em
import io.github.graviton94.todaybible.MainActivity
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Canon
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.core.Season
import io.github.graviton94.todaybible.core.Seasons
import io.github.graviton94.todaybible.core.Translation
import io.github.graviton94.todaybible.data.Store
import io.github.graviton94.todaybible.design.Fonts
import io.github.graviton94.todaybible.design.Tokens
import io.github.graviton94.todaybible.ui.Cards
import java.time.LocalDate

/**
 * 절기 위젯 (1.4 · 2×2 · 4×2): 고르는 것 없이 오늘의 절기. 위는 절기 판화가 어둠으로 녹아들고, 아래 검은 자리에 이름 · 주 · 다음 절기까지 남은 날.
 * 높으면 절기 말씀까지. 위 띠는 절기색. 자정마다 (WidgetTick) 다시 그려 다음 절기로 저절로 넘어가요. 누르면 오늘 화면.
 */
class SeasonWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) { val r = goAsync(); Thread { try { ids.forEach { draw(ctx, manager, it) } } finally { r.finish() } }.start() }
    override fun onAppWidgetOptionsChanged(ctx: Context, manager: AppWidgetManager, id: Int, options: Bundle) { val r = goAsync(); Thread { try { draw(ctx, manager, id) } finally { r.finish() } }.start() }

    companion object {
        fun refresh(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            m.getAppWidgetIds(ComponentName(ctx, SeasonWidget::class.java)).forEach { draw(ctx, m, it) }
        }

        private fun draw(ctx0: Context, m: AppWidgetManager, id: Int) {
            val ctx = io.github.graviton94.todaybible.ui.Lang.wrap(ctx0)
            val o = m.getAppWidgetOptions(id)
            val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 160).coerceAtLeast(110)
            val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 160).coerceAtLeast(110)
            val views = RemoteViews(ctx.packageName, R.layout.widget_verse)
            views.setImageViewBitmap(R.id.widget_image, runCatching { bitmap(ctx, wDp, hDp) }.getOrNull())
            val open = Intent(ctx, MainActivity::class.java).putExtra("page", io.github.graviton94.todaybible.ui.AppState.TODAY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(R.id.widget_image, PendingIntent.getActivity(ctx, 3, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            runCatching { m.updateAppWidget(id, views) }
        }

        private fun color(se: Season): Color = Tokens.dark.let { c ->
            when (se) { Season.ADVENT -> c.advent; Season.LENT -> c.lent; Season.PENTECOST -> c.pentecost; Season.ORDINARY, Season.EPIPHANY -> c.ordinary; else -> c.giltText }
        }

        private fun nameOf(ctx: Context, se: Season) = ctx.getString(when (se) {
            Season.ADVENT -> R.string.season_ADVENT; Season.CHRISTMAS -> R.string.season_CHRISTMAS; Season.EPIPHANY -> R.string.season_EPIPHANY; Season.LENT -> R.string.season_LENT
            Season.EASTER -> R.string.season_EASTER; Season.PENTECOST -> R.string.season_PENTECOST; Season.ORDINARY -> R.string.season_ORDINARY
        })

        /** 위젯 그림 (dp 크기). 캡처 · 미리보기도 씀. */
        fun bitmap(ctx: Context, wDp: Int, hDp: Int, today: LocalDate = LocalDate.now()): Bitmap {
            val d = ctx.resources.displayMetrics.density
            val density = Density(d, 1f)
            val store = Store(ctx); val tr = store.translation; val korean = tr == Translation.KRV
            val now = Seasons.at(today)
            val name = nameOf(ctx, now.season)
            val sub = listOfNotNull(ctx.getString(R.string.season_week, now.week).takeIf { now.season != Season.CHRISTMAS || now.week > 1 },
                now.daysTo?.takeIf { it > 0 }?.let { n -> now.next?.let { ctx.getString(R.string.season_to, nameOf(ctx, it), n) } }).joinToString("\n")
            val (vb, vc, vv) = Seasons.verse(now.season)
            val verse = runCatching { Markup.plain(store.book(tr, vb).verse(vc, vv)) }.getOrDefault("")
            val ref = Canon.books[vb].let { if (korean) it.ko else it.en } + " $vc:$vv"
            val img = runCatching { ctx.assets.open("plates/${Seasons.plate(now.season)}.jpg").use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }) } }.getOrNull()?.asImageBitmap()
            // 4×2 (넓으면): 절기 말씀까지 · 2×2: 이름 · 주 · 남은 날만
            val tall = wDp >= Tokens.Size.seasonWideMin.value && hDp >= Tokens.Size.widgetPlateMinH.value
            return Cards.render(ctx, (wDp * d).toInt(), (hDp * d).toInt(), density) { m ->
                val pad = Tokens.Size.widgetPad.toPx(); val w = size.width - 2 * pad
                val r = CornerRadius(Tokens.Radius.page.toPx())
                val clip = androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size), r)) }
                clipPath(clip) {
                    drawRect(Color.Black)
                    val ih = size.height * (if (tall) Tokens.Ratio.widgetPlate else Tokens.Ratio.seasonWidgetPlate)
                    if (img != null) {
                        val scale = maxOf(size.width / img.width, ih / img.height)
                        val sw = (size.width / scale).toInt(); val sh = (ih / scale).toInt()
                        val sx = ((img.width - sw) / 2).coerceAtLeast(0); val sy = ((img.height - sh) * 0.3f).toInt().coerceAtLeast(0)
                        drawImage(img, androidx.compose.ui.unit.IntOffset(sx, sy), androidx.compose.ui.unit.IntSize(sw, sh), dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), ih.toInt()),
                            colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(io.github.graviton94.todaybible.ui.SEPIA_MATRIX))
                        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = ih), size = Size(size.width, ih))
                    }
                    // 절기색 띠
                    drawRect(color(now.season), size = Size(size.width, Tokens.Size.seasonBand.toPx()))
                }
                val paper = Tokens.dark.ink; val gold = Tokens.dark.giltText
                var y = size.height - pad * 0.8f
                if (tall && verse.isNotEmpty()) {
                    val refL = m.measure(ref, TextStyle(fontFamily = Fonts.serifKr, fontSize = Tokens.Text.small, color = gold), maxLines = 1)
                    y -= refL.size.height; drawText(refL, topLeft = Offset(pad, y))
                    val vs = m.measure(verse, TextStyle(fontFamily = if (korean) Fonts.serifKr else Fonts.garamond, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.widgetPlateVerse,
                        lineHeight = Tokens.Leading.body.em, color = paper, lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)), maxLines = 2, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = w.toInt()))
                    y -= vs.size.height + Tokens.Space.s1.toPx(); drawText(vs, topLeft = Offset(pad, y))
                    y -= Tokens.Space.s2.toPx()
                }
                if (sub.isNotEmpty()) {
                    // 주 · 남은 날은 줄을 나눠서 (숫자와 '일'이 갈라지지 않게), 넓으면 한 줄로
                    val text = if (tall) sub.replace("\n", " · ") else sub
                    val sl = m.measure(text, TextStyle(fontFamily = Fonts.serifKr, fontSize = Tokens.Text.small, color = gold), maxLines = 2, overflow = TextOverflow.Ellipsis, softWrap = false, constraints = Constraints(maxWidth = w.toInt()))
                    y -= sl.size.height; drawText(sl, topLeft = Offset(pad, y))
                }
                val nl = m.measure(name, TextStyle(fontFamily = if (korean) Fonts.serifKr else Fonts.garamond, fontWeight = FontWeight.SemiBold, fontSize = Tokens.Text.label, color = paper), maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = w.toInt()))
                y -= nl.size.height + Tokens.Space.s1.toPx() * 0.5f; drawText(nl, topLeft = Offset(pad, y))
            }
        }
    }
}
