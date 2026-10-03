package io.github.graviton94.todaybible.ui

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.core.Translation
import io.github.graviton94.todaybible.core.VerseKey
import io.github.graviton94.todaybible.data.Store
import io.github.graviton94.todaybible.design.Tokens
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 나의 성경 (D2): 내가 옮겨 쓴 한 권을 PDF 로. 표지 (금선 테 · 권 이름 · 쓴 기간) 다음에
 * 본문 (장마다 붉은 장 번호 · 절마다 바깥 여백에 처음 쓴 날짜). A5, 인쇄해 간직하거나 선물.
 */
object MyBible {
    private const val W = 420; private const val H = 595 // A5 (pt)
    private const val M = 42

    fun make(ctx: Context, store: Store, tr: Translation, book: Int, owner: String = store.ownerName): File {
        val korean = tr == Translation.KRV
        val text = store.book(tr, book)
        val firstDay = HashMap<Int, Long>()
        store.loadFills().filter { it.translation == tr && it.key.book == book }.forEach { f -> firstDay.merge(f.key.raw, f.epochDay) { a, b -> minOf(a, b) } }
        val name = if (korean) io.github.graviton94.todaybible.core.Canon.books[book].ko else io.github.graviton94.todaybible.core.Canon.books[book].en
        val c = Tokens.light
        val serif = ResourcesCompat.getFont(ctx, if (korean) R.font.serif_kr_medium else R.font.garamond_medium)
        val title = ResourcesCompat.getFont(ctx, if (korean) R.font.title_kr else R.font.garamond_semibold)
        val black = ResourcesCompat.getFont(ctx, R.font.blackletter)
        val doc = PdfDocument()
        var pageNo = 0
        fun newPage(): PdfDocument.Page { pageNo++; return doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create()) }

        // 표지
        run {
            val pg = newPage(); val cv = pg.canvas
            cv.drawColor(c.leaf.toArgb())
            val gilt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.gilt.toArgb(); style = Paint.Style.STROKE; strokeWidth = 0.8f }
            cv.drawRect(24f, 24f, W - 24f, H - 24f, gilt); cv.drawRect(29f, 29f, W - 29f, H - 29f, gilt)
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = title; textSize = 30f; color = c.ink.toArgb(); textAlign = Paint.Align.CENTER }
            cv.drawText(name, W / 2f, H * 0.42f, tp)
            val days = firstDay.values
            val fmt = DateTimeFormatter.ofPattern("yyyy. M. d")
            val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 10f; color = c.inkSoft.toArgb(); textAlign = Paint.Align.CENTER }
            cv.drawText(ctx.getString(R.string.my_bible_cover), W / 2f, H * 0.42f + 28f, sub)
            if (owner.isNotBlank()) cv.drawText(owner, W / 2f, H * 0.42f + 60f, TextPaint(tp).apply { textSize = 13f; color = c.giltText.toArgb() })
            if (days.isNotEmpty()) cv.drawText("${LocalDate.ofEpochDay(days.min()).format(fmt)} – ${LocalDate.ofEpochDay(days.max()).format(fmt)}", W / 2f, H * 0.42f + 44f, sub)
            val foot = TextPaint(sub).apply { color = c.giltText.toArgb() }
            cv.drawText(ctx.getString(R.string.app_name), W / 2f, H - 48f, foot)
            doc.finishPage(pg)
        }

        // 본문
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 10.5f; color = c.ink.toArgb() }
        val num = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = black; textSize = 10.5f; color = c.rubric.toArgb() }
        val margin = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 6.5f; color = c.giltText.toArgb() }
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 7.5f; color = c.inkSoft.toArgb(); letterSpacing = 0.06f }
        val chap = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = black; textSize = 30f; color = c.rubric.toArgb() }
        val md = DateTimeFormatter.ofPattern("M.d")
        val gutter = 16f; val colW = (W - 2 * M - gutter - 26).toInt()
        var pg = newPage(); var y = M.toFloat() + 14f
        fun header(ch: Int) { pg.canvas.drawColor(c.leaf.toArgb()); pg.canvas.drawText("$name $ch", M.toFloat(), M.toFloat(), head); pg.canvas.drawLine(M.toFloat(), M + 4f, (W - M).toFloat(), M + 4f, Paint().apply { color = c.hair.toArgb(); strokeWidth = 0.4f }) }
        header(1)
        for (ch in 1..text.chapterCount) {
            if (y > H - M - 60) { doc.finishPage(pg); pg = newPage(); header(ch); y = M + 14f }
            pg.canvas.drawText("$ch", M.toFloat(), y + 26f, chap); y += 38f
            for (v in text.fillable(ch)) {
                val s = Markup.plain(text.verse(ch, v))
                val lay = StaticLayout.Builder.obtain(s, 0, s.length, body, colW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.25f).setIncludePad(false)
                    .apply {
                        // 한글이 낱말 가운데서 끊기지 않게 (안드로이드 13+)
                        if (android.os.Build.VERSION.SDK_INT >= 33) setLineBreakConfig(android.graphics.text.LineBreakConfig.Builder()
                            .setLineBreakWordStyle(android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE).build())
                    }.build()
                if (y + lay.height > H - M) { doc.finishPage(pg); pg = newPage(); header(ch); y = M + 14f }
                val cv = pg.canvas
                cv.save(); cv.translate(M + gutter, y); lay.draw(cv); cv.restore()
                // 절 번호 · 쓴 날짜는 첫 줄과 같은 줄에
                val base = y + lay.getLineBaseline(0)
                cv.drawText("$v", M.toFloat(), base, num.apply { textAlign = Paint.Align.LEFT })
                firstDay[VerseKey(book, ch, v).raw]?.let { d -> cv.drawText(LocalDate.ofEpochDay(d).format(md), (W - M).toFloat() - 18f, base, margin) }
                y += lay.height + 5f
            }
            y += 8f
        }
        doc.finishPage(pg)
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "${name.replace(' ', '_')}.pdf")
        f.outputStream().use { doc.writeTo(it) }; doc.close()
        return f
    }

    /** 손글씨 PDF: 이 권(또는 전체)에 손으로 쓴 절들을 획 그대로 (어떤 크기로 뽑아도 선명). 장마다 머리 · 절마다 여백에 번호와 날짜. */
    fun notes(ctx: Context, store: Store, tr: Translation, book: Int?): File? {
        val korean = tr == Translation.KRV
        val books = if (book != null) listOf(book) else io.github.graviton94.todaybible.core.Canon.books.indices.toList()
        val chapters = books.flatMap { b -> io.github.graviton94.todaybible.data.Ink.chapters(ctx, tr.id, b).map { b to it } }
        if (chapters.isEmpty()) return null
        val c = Tokens.light
        val serif = ResourcesCompat.getFont(ctx, if (korean) R.font.serif_kr_medium else R.font.garamond_medium)
        val title = ResourcesCompat.getFont(ctx, if (korean) R.font.title_kr else R.font.garamond_semibold)
        fun name(b: Int) = if (korean) io.github.graviton94.todaybible.core.Canon.books[b].ko else io.github.graviton94.todaybible.core.Canon.books[b].en
        val firstDay = HashMap<Int, Long>()
        store.loadFills().filter { it.translation == tr }.forEach { f -> firstDay.merge(f.key.raw, f.epochDay) { a, b -> minOf(a, b) } }
        val doc = PdfDocument(); var no = 0
        fun page(): PdfDocument.Page { no++; return doc.startPage(PdfDocument.PageInfo.Builder(W, H, no).create()).also { it.canvas.drawColor(c.leaf.toArgb()) } }
        run {
            val pg = page(); val cv = pg.canvas
            val gilt = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.gilt.toArgb(); style = Paint.Style.STROKE; strokeWidth = 0.8f }
            cv.drawRect(24f, 24f, W - 24f, H - 24f, gilt); cv.drawRect(29f, 29f, W - 29f, H - 29f, gilt)
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = title; textSize = 24f; color = c.ink.toArgb(); textAlign = Paint.Align.CENTER }
            cv.drawText(ctx.getString(R.string.notes_cover), W / 2f, H * 0.42f, tp)
            val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 11f; color = c.inkSoft.toArgb(); textAlign = Paint.Align.CENTER }
            cv.drawText(if (book != null) name(book) else ctx.getString(R.string.app_name), W / 2f, H * 0.42f + 26f, sub)
            if (store.ownerName.isNotBlank()) cv.drawText(store.ownerName, W / 2f, H * 0.42f + 44f, TextPaint(sub).apply { color = c.giltText.toArgb() })
            doc.finishPage(pg)
        }
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 8f; color = c.inkSoft.toArgb() }
        val num = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 9f; color = c.rubric.toArgb() }
        val rule = Paint().apply { color = c.noteLine.toArgb(); strokeWidth = 0.4f }
        val md = DateTimeFormatter.ofPattern("M. d")
        val gutter = 30f; val left = M + gutter; val w = W - M - left
        var pg = page(); var y = M.toFloat()
        for ((b, ch) in chapters) {
            if (y > M + 1f) { doc.finishPage(pg); pg = page(); y = M.toFloat() }
            pg.canvas.drawText("${name(b)} ${if (korean) "${ch}장" else "$ch"}", M.toFloat(), y, head); y += 14f
            for (v in io.github.graviton94.todaybible.data.Ink.verses(ctx, tr.id, b, ch)) {
                val ink = io.github.graviton94.todaybible.data.Ink.load(io.github.graviton94.todaybible.data.Ink.file(ctx, tr.id, b, ch, v)) ?: continue
                val line = ink.line * w
                ink.sheets.forEachIndexed { i, sheet ->
                    val lines = kotlin.math.ceil((ink.height(i) + line * 0.25f) / line).toInt().coerceAtLeast(1)
                    val hgt = lines * line
                    if (y + hgt > H - M) { doc.finishPage(pg); pg = page(); y = M.toFloat() }
                    val cv = pg.canvas
                    for (l in 1..lines) cv.drawLine(left, y + l * line, left + w, y + l * line, rule)
                    if (i == 0) {
                        cv.drawText("$v", M.toFloat(), y + line * 0.6f, num)
                        firstDay[VerseKey(b, ch, v).raw]?.let { d -> cv.drawText(LocalDate.ofEpochDay(d).format(md), M.toFloat(), y + line * 0.6f + 11f, head) }
                    }
                    io.github.graviton94.todaybible.data.Ink.draw(cv, sheet, left, y, w, (if (ink.pen == io.github.graviton94.todaybible.data.Ink.PENCIL) c.graphite else c.penInk).toArgb(), ink.pen)
                    y += hgt
                }
                y += 6f
            }
        }
        doc.finishPage(pg)
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val out = File(dir, "notebook${book?.let { "_${name(it).replace(' ', '_')}" } ?: ""}.pdf")
        out.outputStream().use { doc.writeTo(it) }; doc.close()
        return out
    }

    fun share(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.share", f)
        val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
