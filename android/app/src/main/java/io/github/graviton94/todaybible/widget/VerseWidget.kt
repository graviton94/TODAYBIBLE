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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
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

        private fun draw(ctx0: Context, m: AppWidgetManager, id: Int) {
            val ctx = io.github.graviton94.todaybible.ui.Lang.wrap(ctx0)   // 앱에서 고른 언어로
            val o = m.getAppWidgetOptions(id)
            // 세로 화면 기준 자리: 너비는 MIN_WIDTH, 높이는 MAX_HEIGHT
            val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320).coerceAtLeast(180)
            val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 150).coerceAtLeast(110)
            val views = RemoteViews(ctx.packageName, R.layout.widget_verse)
            views.setImageViewBitmap(R.id.widget_image, runCatching { bitmap(ctx, wDp, hDp, dark(ctx)) }.getOrNull())
            // 그림 위젯이라 화면 읽기 (TalkBack) 에는 글로: 다음에 옮겨 쓸 절
            runCatching { views.setContentDescription(R.id.widget_image, describe(ctx)) }
            val open = Intent(ctx, MainActivity::class.java).putExtra("page", io.github.graviton94.todaybible.ui.AppState.COPY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(R.id.widget_image, PendingIntent.getActivity(ctx, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            runCatching { m.updateAppWidget(id, views) }
        }

        /** 화면 읽기용 한 줄: ‘오늘의 한 절 · 창세기 1:3 · 말씀’. */
        fun describe(ctx: Context): String {
            val store = Store(ctx); val tr = store.translation; val (b, ch) = store.bookmark(tr)
            val text = store.book(tr, b); val v = Progress(store.loadFills()).nextVerse(tr, text, ch)
            val name = Canon.books[b].let { if (tr == Translation.KRV) it.ko else it.en }
            return ctx.getString(R.string.widget_name) + " · " + if (v == null) ctx.getString(R.string.chapter_done, name, ch) else "$name $ch:$v · " + Markup.plain(text.verse(ch, v))
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
            // 판화 바탕 (4×2 이상): 위 약 65%는 그 장(없으면 오늘)의 판화, 아래는 검은 바닥으로 서서히 → 글은 바닥에만
            if (store.widgetPlate && hDp >= Tokens.Size.widgetPlateMinH.value) {
                val pid = store.plateFor(b, ch)?.id ?: store.plates.takeIf { it.isNotEmpty() }?.let { it[Math.floorMod(today.toEpochDay(), it.size.toLong()).toInt()].id }
                val img = pid?.let { id -> runCatching { ctx.assets.open("plates/$id.jpg").use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }) } }.getOrNull() }
                if (img != null) return platedBitmap(ctx, (wDp * d).toInt(), (hDp * d).toInt(), density, img.asImageBitmap(), korean, b, ch, v, name, if (v == null) ctx.getString(R.string.chapter_done, name, ch) else Markup.plain(text.verse(ch, v)))
            }
            return Cards.render(ctx, (wDp * d).toInt(), (hDp * d).toInt(), density) { m ->
                // 1.2 틀: 종이 (어두우면 밤빛) · 금빛 라틴 머리글 · 다음 한 절 · 장절 · 마디 띠 (5절마다 한 칸)
                val pad = Tokens.Size.widgetPad.toPx(); val w = size.width - 2 * pad
                drawRoundRect(c.leaf, cornerRadius = CornerRadius(Tokens.Radius.page.toPx()))
                val headText = (if (store.latinHeads) "HODIE · " + io.github.graviton94.todaybible.core.Latin.head(b, ch) else "${ctx.getString(R.string.today_chapter)} · $name $ch")
                val head = m.measure(headText, TextStyle(fontFamily = if (store.latinHeads) Fonts.caps else Fonts.serifKr, fontWeight = FontWeight.SemiBold,
                    fontSize = Tokens.Text.caps, letterSpacing = Tokens.Tracking.caps.em, color = c.giltText), maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = (w * 0.78f).toInt()))
                drawText(head, topLeft = Offset(pad, pad))
                val count = m.measure("$done / ${fillable.size}", TextStyle(fontFamily = Fonts.display, fontWeight = FontWeight.SemiBold, fontSize = Tokens.Text.small, color = c.inkSoft))
                drawText(count, topLeft = Offset(size.width - pad - count.size.width, pad + (head.size.height - count.size.height) / 2f))
                // 아래: 마디 띠 (낮은 2×1 이면 띠는 빼고 말씀만)
                val showBar = hDp >= Tokens.Size.widgetWeekMinH.value
                val segH = Tokens.Size.segment.toPx(); val segGap = Tokens.Space.s1.toPx() * 0.6f
                val bottomY = if (showBar) size.height - pad - segH else size.height - pad
                if (showBar) {
                    val n = ((fillable.size + 4) / 5).coerceIn(1, 8); val sw = (w - segGap * (n - 1)) / n
                    for (i in 0 until n) {
                        val on = done >= (i + 1) * 5 || (i == n - 1 && done >= fillable.size && fillable.isNotEmpty())
                        val part = if (!on && done > i * 5) (done - i * 5) / 5f else 0f
                        val x = pad + i * (sw + segGap)
                        drawRect(c.hair, Offset(x, bottomY), Size(sw, segH))
                        if (on) drawRect(c.gilt, Offset(x, bottomY), Size(sw, segH)) else if (part > 0f) drawRect(c.gilt, Offset(x, bottomY), Size(sw * part, segH))
                    }
                }
                // 장절 한 줄 · 오늘 쓴 절
                val refText = if (v == null) "" else Lang.chapterRef(ctx, tr, name, ch).let { if (korean) "$name $ch:$v" else "$name $ch:$v" } + (if (todayN > 0) " · " + ctx.getString(R.string.verses_n, todayN) else "")
                val ref = m.measure(refText, TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.small, color = c.giltText), maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = w.toInt()))
                val refY = bottomY - (if (showBar) Tokens.Space.s2.toPx() else 0f) - ref.size.height
                if (refText.isNotEmpty()) drawText(ref, topLeft = Offset(pad, refY))
                // 가운데: 다음 한 절 (자리에 맞게 줄 수 · 넘치면 말줄임)
                val top = pad + head.size.height + Tokens.Space.s2.toPx(); val avail = refY - Tokens.Space.s1.toPx() - top
                val verseText = if (v == null) ctx.getString(R.string.chapter_done, name, ch) else Markup.plain(text.verse(ch, v))
                val style = TextStyle(fontFamily = if (korean) Fonts.serifKr else Fonts.garamond, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.body,
                    lineHeight = Tokens.Leading.body.em, color = c.ink, lineBreak = phrase)
                val one = m.measure("가", style).size.height.coerceAtLeast(1)
                val lines = (avail / one).toInt().coerceAtLeast(1)
                val lay = m.measure(verseText, style, maxLines = lines, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = w.toInt()))
                drawText(lay, topLeft = Offset(pad, top))
            }
        }

        /** 판화 바탕 위젯: 판화(세피아) 위 65% · 아래로 검게 이어짐 · 바닥에 말씀 두 줄과 장절. */
        private fun platedBitmap(ctx: Context, wPx: Int, hPx: Int, density: Density, img: androidx.compose.ui.graphics.ImageBitmap, korean: Boolean, b: Int, ch: Int, v: Int?, name: String, verse: String): Bitmap =
            Cards.render(ctx, wPx, hPx, density) { m ->
                val pad = Tokens.Size.widgetPad.toPx(); val w = size.width - 2 * pad
                val r = CornerRadius(Tokens.Radius.page.toPx())
                val clip = androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size), r)) }
                clipPath(clip) {
                    drawRect(androidx.compose.ui.graphics.Color.Black)
                    val ih = size.height * Tokens.Ratio.widgetPlate
                    // 가운데 위쪽을 기준으로 채워 자르기
                    val scale = maxOf(size.width / img.width, ih / img.height)
                    val sw = (size.width / scale).toInt(); val sh = (ih / scale).toInt()
                    val sx = ((img.width - sw) / 2).coerceAtLeast(0); val sy = ((img.height - sh) * 0.3f).toInt().coerceAtLeast(0)
                    drawImage(img, androidx.compose.ui.unit.IntOffset(sx, sy), androidx.compose.ui.unit.IntSize(sw, sh), dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), ih.toInt()),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(io.github.graviton94.todaybible.ui.SEPIA_MATRIX))
                    drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(0.35f to androidx.compose.ui.graphics.Color.Transparent, 1f to androidx.compose.ui.graphics.Color.Black, startY = 0f, endY = ih), size = Size(size.width, ih))
                }
                val gold = Tokens.dark.giltText; val paper = Tokens.dark.ink
                val ref = m.measure(if (v == null) "" else "$name $ch:$v", TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.small, color = gold), maxLines = 1)
                val refY = size.height - pad * 0.7f - ref.size.height
                drawText(ref, topLeft = Offset(pad, refY))
                val style = TextStyle(fontFamily = if (korean) Fonts.serifKr else Fonts.garamond, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.widgetPlateVerse,
                    lineHeight = Tokens.Leading.body.em, color = paper, lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase))
                val lay = m.measure(verse, style, maxLines = 2, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = w.toInt()))
                drawText(lay, topLeft = Offset(pad, refY - lay.size.height))
            }
    }
}
