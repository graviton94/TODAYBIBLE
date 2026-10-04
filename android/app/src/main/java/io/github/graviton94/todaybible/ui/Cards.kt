package io.github.graviton94.todaybible.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.core.Milestone
import io.github.graviton94.todaybible.design.Fonts
import io.github.graviton94.todaybible.design.Palette
import io.github.graviton94.todaybible.design.Tokens
import java.io.File
import java.time.LocalDateTime

/**
 * 앱 밖으로 나가는 그림 (나누기 카드 · 위젯): 앱과 같은 토큰 · 글꼴로 비트맵에 그림.
 * 나누기 카드는 늘 종이(라이트) 위에: 말씀 · 흐린 판화 · 나눈 날짜와 시각 · 금선 테.
 */
object Cards {
    /** DrawScope 로 비트맵 하나 그리기. 글자는 TextMeasurer 로 (앱과 같은 글꼴 · 어절 줄바꿈). */
    fun render(ctx: Context, w: Int, h: Int, density: Density, block: DrawScope.(TextMeasurer) -> Unit): Bitmap {
        val img = ImageBitmap(w, h)
        val measurer = TextMeasurer(createFontFamilyResolver(ctx), density, LayoutDirection.Ltr)
        CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(img), Size(w.toFloat(), h.toFloat())) { block(measurer) }
        return img.asAndroidBitmap()
    }

    private val phrase = LineBreak.Paragraph.copy(wordBreak = LineBreak.WordBreak.Phrase)
    private fun shareDensity() = Density(Tokens.Px.shareDensity, 1f)
    /** 카드의 날짜 · 앱 이름은 본문(번역)의 언어로. */
    private fun stamp(ctx: Context, korean: Boolean, now: LocalDateTime): String =
        Lang.date(Lang.content(ctx, korean), io.github.graviton94.todaybible.R.string.fmt_card_time, now)

    /** 종이 + 바깥 금선 두 줄 테 (덮개와 같은 문법). */
    private fun DrawScope.paperFrame(c: Palette) {
        drawRect(c.leaf)
        val e = Tokens.Size.cardEdge.toPx(); val g = Tokens.Size.cardBandGap.toPx(); val w = Tokens.Stroke.giltFine.toPx()
        drawRect(c.gilt, Offset(e, e), Size(size.width - 2 * e, size.height - 2 * e), style = Stroke(w))
        drawRect(c.gilt, Offset(e + g, e + g), Size(size.width - 2 * (e + g), size.height - 2 * (e + g)), style = Stroke(w))
    }

    private fun DrawScope.faintPlate(ctx: Context, plateId: String?, alpha: Float) {
        val id = plateId ?: return
        val bmp = runCatching { ctx.assets.open("plates/$id.jpg").use { BitmapFactory.decodeStream(it) } }.getOrNull() ?: return
        val img = bmp.asImageBitmap()
        // 화면을 덮도록 (가운데 기준 잘라내기)
        val scale = maxOf(size.width / img.width, size.height / img.height)
        val dw = (img.width * scale).toInt(); val dh = (img.height * scale).toInt()
        drawImage(img, dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - dw) / 2).toInt(), ((size.height - dh) / 2).toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(dw, dh), alpha = alpha, colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }))
    }

    /** 말씀 한 절 카드: 본문 · 장절 · 나눈 날짜와 시각 · 흐린 판화. */
    fun verse(ctx: Context, korean: Boolean, reference: String, text: String, plateId: String?, now: LocalDateTime = LocalDateTime.now()): Bitmap {
        val c = Tokens.light
        return render(ctx, Tokens.Px.shareW.toInt(), Tokens.Px.shareH.toInt(), shareDensity()) { m ->
            paperFrame(c)
            faintPlate(ctx, plateId, Tokens.Px.plateFaint)
            val pad = Tokens.Size.cardPad.toPx(); val width = (size.width - 2 * pad).toInt()
            val body = buildAnnotatedString {
                Markup.spans(text).forEach { sp -> withStyle(SpanStyle(fontStyle = if (sp.italic) FontStyle.Italic else FontStyle.Normal, fontFeatureSettings = if (sp.smallCaps) "smcp" else null)) { append(sp.text) } }
            }
            // 긴 절은 글자를 줄여 테 안에 들게
            var fs = (if (korean) Tokens.Text.cardVerse else Tokens.Text.cardVerseEn).value
            var lay = m.measure(body, TextStyle(fontFamily = if (korean) Fonts.serifKr else Fonts.garamond, fontWeight = FontWeight.Medium, fontSize = fs.sp, lineHeight = Tokens.Leading.verse.em, color = c.ink, textAlign = TextAlign.Center, lineBreak = phrase), constraints = Constraints(maxWidth = width))
            while (lay.size.height > size.height * Tokens.Px.cardFill && fs > Tokens.Text.cardVerseMin.value) {
                fs -= 1f
                lay = m.measure(body, lay.layoutInput.style.copy(fontSize = fs.sp), constraints = Constraints(maxWidth = width))
            }
            val top = (size.height - lay.size.height) / 2 - Tokens.Size.cardGap.toPx()
            stamp(STAMP_CROSS, c.rubric, Offset(size.width / 2, top - Tokens.Size.cardGap.toPx() - Tokens.Size.cardMark.toPx() / 2), Tokens.Size.cardMark.toPx())
            drawText(lay, topLeft = Offset(pad, top))
            val ref = m.measure(reference, TextStyle(fontFamily = if (korean) Fonts.serifKr else Fonts.fell, fontWeight = FontWeight.Bold, fontSize = Tokens.Text.cardRef, letterSpacing = Tokens.Tracking.head.em, color = c.rubric, textAlign = TextAlign.Center), constraints = Constraints.fixedWidth(width))
            drawText(ref, topLeft = Offset(pad, top + lay.size.height + Tokens.Size.cardGap.toPx()))
            footer(ctx, m, c, korean, now)
        }
    }

    /** 맨 아래: 나눈 날짜와 시각 · 앱 이름. */
    private fun DrawScope.footer(ctx: Context, m: TextMeasurer, c: Palette, korean: Boolean, now: LocalDateTime) {
        val pad = Tokens.Size.cardPad.toPx(); val width = (size.width - 2 * pad).toInt()
        val t = m.measure(buildAnnotatedString {
            withStyle(SpanStyle(color = c.inkSoft)) { append(stamp(ctx, korean, now)) }
            withStyle(SpanStyle(color = c.giltText)) { append("  ·  " + Lang.content(ctx, korean).getString(io.github.graviton94.todaybible.R.string.app_name)) }
        }, TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.cardFoot, textAlign = TextAlign.Center), constraints = Constraints.fixedWidth(width), maxLines = 1, overflow = TextOverflow.Ellipsis)
        drawText(t, topLeft = Offset(pad, size.height - Tokens.Size.cardPad.toPx() - t.size.height))
    }

    /** 장을 마치고 걷은 판화 카드: 판화 · 제목 · 장 · 날짜. */
    fun plate(ctx: Context, korean: Boolean, plateId: String, title: String, reference: String, now: LocalDateTime = LocalDateTime.now()): Bitmap {
        val c = Tokens.light
        return render(ctx, Tokens.Px.shareW.toInt(), Tokens.Px.shareH.toInt(), shareDensity()) { m ->
            paperFrame(c)
            val bmp = runCatching { ctx.assets.open("plates/$plateId.jpg").use { BitmapFactory.decodeStream(it) } }.getOrNull()?.asImageBitmap()
            val pad = Tokens.Size.cardPad.toPx(); val width = size.width - 2 * pad
            if (bmp != null) {
                // 제목 · 장 · 바닥글 자리를 먼저 재고 남는 높이에 판화를 맞춤 (겹치지 않게)
                val gap = Tokens.Size.cardGap.toPx()
                val t = m.measure(title, TextStyle(fontFamily = if (korean) Fonts.titleKr else Fonts.garamond, fontSize = Tokens.Text.title, color = c.ink, textAlign = TextAlign.Center), constraints = Constraints.fixedWidth(width.toInt()), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val r = m.measure(reference, TextStyle(fontFamily = if (korean) Fonts.serifKr else Fonts.fell, fontWeight = FontWeight.Bold, fontSize = Tokens.Text.cardRef, color = c.rubric, textAlign = TextAlign.Center), constraints = Constraints.fixedWidth(width.toInt()))
                val foot = Tokens.Size.cardPad.toPx() + Tokens.Text.cardFoot.toPx() * 2
                val avail = size.height - pad - foot - gap * 2 - t.size.height - r.size.height - Tokens.Space.s2.toPx()
                val dh = minOf(width / Tokens.Ratio.plateAspect, avail); val dw = dh * Tokens.Ratio.plateAspect
                drawImage(bmp, dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - dw) / 2).toInt(), pad.toInt()), dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt()))
                val y = pad + dh + gap
                drawText(t, topLeft = Offset(pad, y))
                drawText(r, topLeft = Offset(pad, y + t.size.height + Tokens.Space.s2.toPx()))
            }
            footer(ctx, m, c, korean, now)
        }
    }

    /** 발자취 카드: 메달 · 이름 · 조건 · 날짜. */
    fun milestone(ctx: Context, korean: Boolean, m: Milestone, name: String, rule: String, now: LocalDateTime = LocalDateTime.now()): Bitmap {
        val c = Tokens.light
        return render(ctx, Tokens.Px.shareW.toInt(), (Tokens.Px.shareW).toInt(), shareDensity()) { tm ->
            paperFrame(c)
            val d = Tokens.Size.cardMedal.toPx(); val cx = size.width / 2; val top = size.height * Tokens.Px.medalTop
            drawContext.canvas.save(); drawContext.canvas.translate(cx - d / 2, top)
            val old = drawContext.size; drawContext.size = Size(d, d); medal(m, true, c.leather, c.gilt, c.unwritten); drawContext.size = old
            drawContext.canvas.restore()
            val pad = Tokens.Size.cardPad.toPx(); val width = (size.width - 2 * pad).toInt()
            val n = tm.measure(name, TextStyle(fontFamily = if (korean) Fonts.titleKr else Fonts.garamond, fontSize = Tokens.Text.cardName, color = c.ink, textAlign = TextAlign.Center), constraints = Constraints.fixedWidth(width), maxLines = 1, overflow = TextOverflow.Ellipsis)
            drawText(n, topLeft = Offset(pad, top + d + Tokens.Size.cardGap.toPx()))
            val r = tm.measure(rule, TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.cardRef, color = c.inkSoft, textAlign = TextAlign.Center), constraints = Constraints.fixedWidth(width), maxLines = 1, overflow = TextOverflow.Ellipsis)
            drawText(r, topLeft = Offset(pad, top + d + Tokens.Size.cardGap.toPx() + n.size.height + Tokens.Space.s2.toPx()))
            footer(ctx, tm, c, korean, now)
        }
    }

    /** 올해의 필사 (R1): 이름 · 큰 숫자 · 몇 줄. */
    fun year(ctx: Context, korean: Boolean, title: String, big: String, lines: List<String>, now: LocalDateTime = LocalDateTime.now()): Bitmap {
        val c = Tokens.light
        return render(ctx, Tokens.Px.shareW.toInt(), (Tokens.Px.shareW).toInt(), shareDensity()) { tm ->
            paperFrame(c)
            val pad = Tokens.Size.cardPad.toPx(); val width = (size.width - 2 * pad).toInt()
            var y = size.height * Tokens.Px.medalTop
            val t = tm.measure(title, TextStyle(fontFamily = if (korean) Fonts.titleKr else Fonts.garamond, fontSize = Tokens.Text.cardName, color = c.giltText, textAlign = TextAlign.Center), constraints = Constraints.fixedWidth(width), maxLines = 1, overflow = TextOverflow.Ellipsis)
            drawText(t, topLeft = Offset(pad, y)); y += t.size.height + Tokens.Size.cardGap.toPx()
            val b = tm.measure(big, TextStyle(fontFamily = if (korean) Fonts.titleKr else Fonts.garamond, fontSize = Tokens.Text.display * 2, color = c.rubric, textAlign = TextAlign.Center), constraints = Constraints.fixedWidth(width), maxLines = 1)
            drawText(b, topLeft = Offset(pad, y)); y += b.size.height + Tokens.Size.cardGap.toPx()
            for (l in lines) {
                val r = tm.measure(l, TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.cardRef, color = c.inkSoft, textAlign = TextAlign.Center), constraints = Constraints.fixedWidth(width), maxLines = 1, overflow = TextOverflow.Ellipsis)
                drawText(r, topLeft = Offset(pad, y)); y += r.size.height + Tokens.Space.s2.toPx()
            }
            footer(ctx, tm, c, korean, now)
        }
    }

    /** 그림을 저장해 나누기 창 열기 (FileProvider, 캐시 폴더). */
    fun share(s: AppState, ctx: Context, bmp: Bitmap, name: String) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        s.exportJob = ExportJob(listOf(f), "image/png", ctx.getString(io.github.graviton94.todaybible.R.string.export_title_card))
    }

}
