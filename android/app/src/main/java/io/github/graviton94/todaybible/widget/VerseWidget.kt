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
 * 오늘의 말씀 위젯 (1.5): 날마다 바뀌는 말씀 한 절 + 그 장면의 판화. 내 기록은 읽지 않아요 (말씀 · 판화 · 때만).
 * 화첩의 도레 판화 가운데 날마다 한 점, 그 판화가 그린 절. 앱과 같은 글꼴 · 토큰으로 그림 한 장. 누르면 그 장을 그 절에서 읽기.
 * (1.4 까지는 ‘오늘의 한 절’: 다음에 옮겨 쓸 절 · 진행. 홈 화면의 위젯은 그대로 이 모습으로 바뀌어요)
 */
class VerseWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) { val r = goAsync(); Thread { try { ids.forEach { draw(ctx, manager, it) } } finally { r.finish() } }.start() }
    override fun onAppWidgetOptionsChanged(ctx: Context, manager: AppWidgetManager, id: Int, options: Bundle) { val r = goAsync(); Thread { try { draw(ctx, manager, id) } finally { r.finish() } }.start() }

    companion object {
        fun refresh(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            m.getAppWidgetIds(ComponentName(ctx, VerseWidget::class.java)).forEach { draw(ctx, m, it) }
        }

        /** 오늘의 판화 (도레 기본 판화에서, 이웃한 장이 이어 나오지 않게 37칸씩 건너뛰며). */
        fun plateOf(store: Store, day: LocalDate): io.github.graviton94.todaybible.data.Plate? {
            val list = store.plates.filter { it.set.isEmpty() }.ifEmpty { store.plates }
            if (list.isEmpty()) return null
            return list[Math.floorMod(day.toEpochDay() * 37, list.size.toLong()).toInt()]
        }

        private fun draw(ctx0: Context, m: AppWidgetManager, id: Int) {
            val ctx = io.github.graviton94.todaybible.ui.Lang.wrap(ctx0)   // 앱에서 고른 언어로
            val o = m.getAppWidgetOptions(id)
            // 세로 화면 기준 자리: 너비는 MIN_WIDTH, 높이는 MAX_HEIGHT
            val wDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 320).coerceAtLeast(110)
            val hDp = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 150).coerceAtLeast(110)
            val views = RemoteViews(ctx.packageName, R.layout.widget_verse)
            views.setImageViewBitmap(R.id.widget_image, runCatching { bitmap(ctx, wDp, hDp, dark(ctx)) }.getOrNull())
            // 그림 위젯이라 화면 읽기 (TalkBack) 에는 글로
            runCatching { views.setContentDescription(R.id.widget_image, describe(ctx)) }
            val p = plateOf(Store(ctx), LocalDate.now())
            val open = Intent(ctx, MainActivity::class.java).setAction("word_read").putExtra("season_read", true)
                .putExtra("at_b", p?.book ?: 18).putExtra("at_c", p?.chapter ?: 23).putExtra("at_v", p?.verse ?: 1)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(R.id.widget_image, PendingIntent.getActivity(ctx, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            runCatching { m.updateAppWidget(id, views) }
        }

        /** 오늘의 말씀: (권, 장, 절, 말씀, 장절, 판화). 판화가 없으면 시편 23:1. */
        private data class Word(val book: Int, val chapter: Int, val verse: Int, val text: String, val ref: String, val plate: io.github.graviton94.todaybible.data.Plate?)
        private fun word(ctx: Context, day: LocalDate): Word {
            val store = Store(ctx); val tr = store.translation; val p = plateOf(store, day)
            val b = p?.book ?: 18; val ch = p?.chapter ?: 23
            val t = store.book(tr, b); val v = (p?.verse ?: 1).takeIf { it in t.fillable(ch) } ?: t.fillable(ch).firstOrNull() ?: 1
            val name = Canon.books[b].let { if (tr == Translation.KRV) it.ko else it.en }
            return Word(b, ch, v, Markup.plain(t.verse(ch, v)), "$name $ch:$v", p)
        }

        /** 화면 읽기용 한 줄: ‘오늘의 말씀 · 창세기 1:3 · 말씀 · 판화 이름’. */
        fun describe(ctx: Context): String {
            val w = word(ctx, LocalDate.now()); val k = Store(ctx).translation == Translation.KRV
            return listOfNotNull(ctx.getString(R.string.widget_name), w.ref, w.text, w.plate?.let { if (k) it.ko else it.en }).joinToString(" · ")
        }

        fun dark(ctx: Context): Boolean = when (Store(ctx).theme) {
            ThemeChoice.DARK, ThemeChoice.CANDLE -> true; ThemeChoice.LIGHT -> false
            ThemeChoice.SYSTEM -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }

        /** 위젯 그림 (dp 크기). 캡처 · 미리보기도 씀. 높으면 판화 바탕, 낮거나 판화 바탕을 끄면 종이. */
        fun bitmap(ctx: Context, wDp: Int, hDp: Int, dark: Boolean, today: LocalDate = LocalDate.now()): Bitmap {
            val d = ctx.resources.displayMetrics.density
            val density = Density(d, 1f)
            val c = if (dark) Tokens.dark else Tokens.light
            val store = Store(ctx); val korean = store.translation == Translation.KRV
            val w = word(ctx, today)
            val by = w.plate?.let { (if (korean) it.ko else it.en) + " · " + (if (korean) it.byKo else it.byEn) }.orEmpty()
            val phrase = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)
            if (store.widgetPlate && hDp >= Tokens.Size.widgetPlateMinH.value && w.plate != null) {
                val img = runCatching { ctx.assets.open("plates/${w.plate.id}.jpg").use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }) } }.getOrNull()
                if (img != null) return platedBitmap(ctx, (wDp * d).toInt(), (hDp * d).toInt(), density, img.asImageBitmap(), korean, w.ref, w.text, by)
            }
            return Cards.render(ctx, (wDp * d).toInt(), (hDp * d).toInt(), density) { m ->
                // 종이 (어두우면 밤빛) · 금빛 머리글 · 말씀 (칸에 맞춰 글자 크기) · 장절 · 판화 이름
                val pad = Tokens.Size.widgetPad.toPx(); val width = size.width - 2 * pad
                drawRoundRect(c.leaf, cornerRadius = CornerRadius(Tokens.Radius.page.toPx()))
                val headText = if (store.latinHeads) "VERBUM DIEI" else ctx.getString(R.string.widget_name)
                val head = m.measure(headText, TextStyle(fontFamily = if (store.latinHeads) Fonts.caps else Fonts.serifKr, fontWeight = FontWeight.SemiBold,
                    fontSize = Tokens.Text.caps, letterSpacing = Tokens.Tracking.caps.em, color = c.giltText), maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = width.toInt()))
                drawText(head, topLeft = Offset(pad, pad))
                // 아래: 판화 이름 (넓을 때) · 장절
                val small = TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.small, color = c.giltText)
                val showBy = by.isNotEmpty() && hDp >= Tokens.Size.widgetWeekMinH.value
                val byLay = if (showBy) m.measure(by, small.copy(color = c.inkSoft), maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = width.toInt())) else null
                val bottom = size.height - pad - (byLay?.size?.height ?: 0)
                val ref = m.measure(w.ref, small, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = width.toInt()))
                val refY = bottom - ref.size.height
                drawText(ref, topLeft = Offset(pad, refY))
                byLay?.let { drawText(it, topLeft = Offset(pad, size.height - pad - it.size.height)) }
                // 가운데: 말씀 (자리에 맞게 글자를 줄여 다 보이게)
                val top = pad + head.size.height + Tokens.Space.s2.toPx(); val avail = refY - Tokens.Space.s1.toPx() - top
                val style = TextStyle(fontFamily = if (korean) Fonts.serifKr else Fonts.garamond, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.body,
                    lineHeight = Tokens.Leading.body.em, color = c.ink, lineBreak = phrase)
                drawText(fitText(m, w.text, style, width.toInt(), avail, Tokens.Text.widgetMin.value), topLeft = Offset(pad, top))
            }
        }

        /** 판화 바탕: 판화(세피아) 위 65% · 아래로 검게 이어짐 · 바닥에 말씀 (칸에 맞춰) · 장절 · 판화 이름. */
        private fun platedBitmap(ctx: Context, wPx: Int, hPx: Int, density: Density, img: androidx.compose.ui.graphics.ImageBitmap, korean: Boolean, refText: String, verse: String, by: String): Bitmap =
            Cards.render(ctx, wPx, hPx, density) { m ->
                val pad = Tokens.Size.widgetPad.toPx(); val w = size.width - 2 * pad
                val r = CornerRadius(Tokens.Radius.page.toPx())
                val clip = androidx.compose.ui.graphics.Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size), r)) }
                val ih = size.height * Tokens.Ratio.widgetPlate
                clipPath(clip) {
                    drawRect(androidx.compose.ui.graphics.Color.Black)
                    // 가운데 위쪽을 기준으로 채워 자르기
                    val scale = maxOf(size.width / img.width, ih / img.height)
                    val sw = (size.width / scale).toInt(); val sh = (ih / scale).toInt()
                    val sx = ((img.width - sw) / 2).coerceAtLeast(0); val sy = ((img.height - sh) * 0.3f).toInt().coerceAtLeast(0)
                    drawImage(img, androidx.compose.ui.unit.IntOffset(sx, sy), androidx.compose.ui.unit.IntSize(sw, sh), dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), ih.toInt()),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(io.github.graviton94.todaybible.ui.SEPIA_MATRIX))
                    drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(0.35f to androidx.compose.ui.graphics.Color.Transparent, 1f to androidx.compose.ui.graphics.Color.Black, startY = 0f, endY = ih), size = Size(size.width, ih))
                }
                val gold = Tokens.dark.giltText; val paper = Tokens.dark.ink
                val small = TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.small, color = gold)
                val line = if (by.isEmpty()) refText else "$refText · $by"
                val ref = m.measure(line, small, maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = w.toInt()))
                val refY = size.height - pad * 0.7f - ref.size.height
                drawText(ref, topLeft = Offset(pad, refY))
                // 말씀은 판화가 어둠으로 녹는 자리부터 장절 위까지 (넘치면 글자를 줄여요)
                val style = TextStyle(fontFamily = if (korean) Fonts.serifKr else Fonts.garamond, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.widgetPlateVerse,
                    lineHeight = Tokens.Leading.body.em, color = paper, lineBreak = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase))
                val room = refY - Tokens.Space.s1.toPx() - ih * 0.55f
                val lay = fitText(m, verse, style, w.toInt(), room, Tokens.Text.widgetMin.value)
                drawText(lay, topLeft = Offset(pad, refY - Tokens.Space.s1.toPx() - lay.size.height))
            }
    }
}
