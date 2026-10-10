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
import androidx.compose.ui.graphics.drawscope.clipRect
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
import io.github.graviton94.todaybible.design.Fonts
import io.github.graviton94.todaybible.design.Palette
import io.github.graviton94.todaybible.design.Tokens
import java.io.File
import java.time.LocalDateTime

/**
 * 앱 밖으로 나가는 그림 (나누기 카드 · 위젯): 앱과 같은 토큰 · 글꼴로 비트맵에 그림.
 * 나누기 카드는 한 가지 틀 (1.2): 판화가 위를 덮고 어둠으로 녹아든 자리에 말씀.
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

    /** 판화를 카드 위쪽에 꽉 채워 (세피아 먹빛), 아래로 어둠에 녹아들게. */
    private fun DrawScope.art(ctx: Context, plateId: String?, h: Float, leaf: androidx.compose.ui.graphics.Color) {
        val id = plateId ?: return
        val bmp = runCatching { ctx.assets.open("plates/$id.jpg").use { BitmapFactory.decodeStream(it) } }.getOrNull() ?: return
        val img = bmp.asImageBitmap()
        val scale = maxOf(size.width / img.width, h / img.height)
        val dw = (img.width * scale).toInt(); val dh = (img.height * scale).toInt()
        // 판화는 흑백 → 따뜻한 먹빛 (빨강 1 · 초록 0.93 · 파랑 0.8) 으로
        val sepia = ColorMatrix(floatArrayOf(
            0.30f, 0.59f, 0.11f, 0f, 0f,
            0.28f, 0.55f, 0.10f, 0f, 0f,
            0.24f, 0.47f, 0.09f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f))
        // 판화는 h 안에서만 (아래로 삐져나오면 어둠과의 경계가 줄로 보여요)
        clipRect(0f, 0f, size.width, h) {
            drawImage(img, dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - dw) / 2).toInt(), ((h - dh) * 0.3f).toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(dw, dh), colorFilter = ColorFilter.colorMatrix(sepia))
        }
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(0f to leaf.copy(alpha = 0.35f), 0.16f to leaf.copy(alpha = 0f), 0.5f to leaf.copy(alpha = 0f), 0.8f to leaf.copy(alpha = 0.8f), 1f to leaf, startY = 0f, endY = h), size = Size(size.width, h))
    }

    /**
     * 나누기 카드 (하나의 틀): 판화가 위를 덮고 어둠으로 녹아든 아래에 머리글 · 본문 · 금선 · 장절과 그림 이름 · 바닥글.
     * 본문은 말씀이거나 (big 이 있으면) 큰 숫자 + 몇 줄.
     */
    private fun card(ctx: Context, korean: Boolean, plateId: String?, eyebrow: String, body: androidx.compose.ui.text.AnnotatedString?, ref: String?, credit: String?,
                     now: LocalDateTime, big: String? = null, lines: List<String> = emptyList()): Bitmap {
        val c = Tokens.dark
        return render(ctx, Tokens.Px.shareW.toInt(), Tokens.Px.shareH.toInt(), shareDensity()) { m ->
            drawRect(c.leaf)
            val pad = Tokens.Size.cardPad.toPx(); val width = (size.width - 2 * pad).toInt(); val gap = Tokens.Size.cardGap.toPx()
            // 아래부터 쌓을 것들을 먼저 재요
            val foot = m.measure(buildAnnotatedString { append(stamp(ctx, korean, now)) }, TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.cardFoot, color = c.inkSoft), maxLines = 1)
            val app = m.measure(Lang.content(ctx, korean).getString(R.string.app_name), TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.cardFoot, color = c.inkSoft), maxLines = 1)
            val refL = ref?.let { m.measure(it, TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.cardRef, color = c.gilt), maxLines = 1) }
            val credL = credit?.let { m.measure(it, TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.cardFoot, color = c.inkSoft), constraints = Constraints(maxWidth = (width - (refL?.size?.width ?: 0) - gap).toInt().coerceAtLeast(1)), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            val cap = m.measure(eyebrow, TextStyle(fontFamily = Fonts.caps, fontWeight = FontWeight.SemiBold, fontSize = Tokens.Text.cardCaps, letterSpacing = Tokens.Tracking.caps.em, color = c.gilt), maxLines = 1, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = width))
            val bigL = big?.let { m.measure(it, TextStyle(fontFamily = Fonts.display, fontWeight = FontWeight.SemiBold, fontSize = Tokens.Text.cardBig, color = c.giltText), maxLines = 1) }
            val lineLs = lines.map { m.measure(it, TextStyle(fontFamily = Fonts.serifKr, fontWeight = FontWeight.Medium, fontSize = Tokens.Text.cardRef, color = c.inkSoft), constraints = Constraints(maxWidth = width), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            // 본문: 길면 글자를 줄여 카드의 cardTextMax 안에
            var bodyL = body?.let {
                var fs = (if (korean) Tokens.Text.cardVerse else Tokens.Text.cardVerseEn).value
                var lay = m.measure(it, TextStyle(fontFamily = if (korean) Fonts.titleSerif else Fonts.garamond, fontWeight = if (korean) FontWeight.SemiBold else FontWeight.Medium, fontSize = fs.sp, lineHeight = Tokens.Leading.verse.em, color = c.ink, lineBreak = phrase), constraints = Constraints(maxWidth = width))
                while (lay.size.height > size.height * Tokens.Px.cardTextMax && fs > Tokens.Text.cardVerseMin.value) {
                    fs -= 1f; lay = m.measure(it, lay.layoutInput.style.copy(fontSize = fs.sp), constraints = Constraints(maxWidth = width))
                }
                lay
            }
            val ruleW = Tokens.Size.cardRule.toPx(); val hair = Tokens.Stroke.hair.toPx()
            var y = size.height - pad - foot.size.height
            val footTop = y - gap
            y = footTop - gap - (refL?.size?.height ?: 0)
            val refTop = y
            y -= gap; val ruleY = y
            val textH = (bodyL?.size?.height ?: 0) + (bigL?.size?.height ?: 0) + lineLs.sumOf { it.size.height }
            y -= gap + textH
            val textTop = y
            y -= Tokens.Space.s2.toPx() + cap.size.height
            val capTop = y
            // 판화: 글이 시작하는 자리 조금 아래까지 (짧은 글은 크게, 긴 글은 작게)
            // 판화는 글이 시작하는 자리에서 어둠으로 다 녹아요 (글 뒤로 밝은 판화가 비치지 않게)
            val artH = (capTop + gap).coerceIn(size.height * Tokens.Px.cardArtMin, size.height * Tokens.Px.cardArtMax)
            art(ctx, plateId, artH, c.leaf)
            drawText(cap, topLeft = Offset(pad, capTop))
            var ty = textTop
            bigL?.let { drawText(it, topLeft = Offset(pad, ty)); ty += it.size.height }
            bodyL?.let { drawText(it, topLeft = Offset(pad, ty)); ty += it.size.height }
            lineLs.forEach { drawText(it, topLeft = Offset(pad, ty)); ty += it.size.height }
            drawLine(c.gilt, Offset(pad, ruleY), Offset(pad + ruleW, ruleY), hair * 1.5f)
            refL?.let { drawText(it, topLeft = Offset(pad, refTop)) }
            credL?.let { drawText(it, topLeft = Offset(size.width - pad - it.size.width, refTop + ((refL?.size?.height ?: it.size.height) - it.size.height) / 2f)) }
            drawLine(c.hair, Offset(pad, footTop), Offset(size.width - pad, footTop), hair)
            drawText(foot, topLeft = Offset(pad, size.height - pad - foot.size.height))
            drawText(app, topLeft = Offset(size.width - pad - app.size.width, size.height - pad - app.size.height))
        }
    }

    /** 판화 이름 줄: 〈이름〉 화가 (화가 이름은 판화 표기의 ‘·’ 앞까지). */
    fun credit(ctx: Context, korean: Boolean, plateId: String?): String? {
        val p = io.github.graviton94.todaybible.data.Store(ctx).plates.firstOrNull { it.id == plateId } ?: return null
        val by = (if (korean) p.byKo else p.byEn).substringBefore('·').trim()
        return Lang.content(ctx, korean).getString(R.string.card_credit, if (korean) p.ko else p.en, by)
    }

    /** 말씀 한 절 카드: 머리글 (라틴 장) · 말씀 · 장절 · 그림 이름 · 나눈 날짜와 시각. */
    fun verse(ctx: Context, korean: Boolean, eyebrow: String, reference: String, text: String, plateId: String?, now: LocalDateTime = LocalDateTime.now()): Bitmap {
        val body = buildAnnotatedString {
            Markup.spans(text).forEach { sp -> withStyle(SpanStyle(fontStyle = if (sp.italic) FontStyle.Italic else FontStyle.Normal, fontFeatureSettings = if (sp.smallCaps) "smcp" else null)) { append(sp.text) } }
        }
        return card(ctx, korean, plateId, eyebrow, body, reference, credit(ctx, korean, plateId), now)
    }

    /** 같은 틀의 숫자 카드 (발자취 · 한 해 · 내 목소리): 큰 숫자 · 이름 · 몇 줄. */
    fun year(ctx: Context, korean: Boolean, title: String, big: String, lines: List<String>, plateId: String? = null, eyebrow: String = "BIBLIA MANU SCRIPTA", now: LocalDateTime = LocalDateTime.now()): Bitmap =
        card(ctx, korean, plateId, eyebrow, buildAnnotatedString { append(title) }, null, credit(ctx, korean, plateId), now, big = big, lines = lines)

    /** 그림을 저장해 나누기 창 열기 (FileProvider, 캐시 폴더). */
    fun share(s: AppState, ctx: Context, bmp: Bitmap, name: String) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "$name.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        s.exportJob = ExportJob(listOf(f), "image/png", ctx.getString(io.github.graviton94.todaybible.R.string.export_title_card))
    }

}
