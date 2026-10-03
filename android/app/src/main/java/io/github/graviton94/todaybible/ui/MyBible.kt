package io.github.graviton94.todaybible.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
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
            // 이 장을 종이에 쓴 노트 사진이 있으면 한 쪽에 붙임
            Photos.of(ctx, tr.id, book, ch)?.let { f ->
                BitmapFactory.decodeFile(f.path)?.let { bmp ->
                    doc.finishPage(pg); pg = newPage(); header(ch)
                    val box = android.graphics.RectF(M.toFloat(), M + 16f, (W - M).toFloat(), (H - M).toFloat())
                    val k = minOf(box.width() / bmp.width, box.height() / bmp.height)
                    val dw = bmp.width * k; val dh = bmp.height * k
                    pg.canvas.drawBitmap(bmp, null, android.graphics.RectF(box.centerX() - dw / 2, box.top, box.centerX() + dw / 2, box.top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
                    bmp.recycle()
                    doc.finishPage(pg); pg = newPage(); header(minOf(ch + 1, text.chapterCount)); y = M + 14f
                }
            }
        }
        doc.finishPage(pg)
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, "${name.replace(' ', '_')}.pdf")
        f.outputStream().use { doc.writeTo(it) }; doc.close()
        return f
    }

    /** 노트 PDF: 이 권(또는 전체)의 노트 사진만, 한 쪽에 한 장 · 머리에 권 · 장과 찍은 날. */
    fun notes(ctx: Context, store: Store, tr: Translation, book: Int?): File? {
        val korean = tr == Translation.KRV
        val books = if (book != null) listOf(book) else io.github.graviton94.todaybible.core.Canon.books.indices.toList()
        val photos = books.flatMap { b -> (1..io.github.graviton94.todaybible.core.Canon.books[b].chapters).mapNotNull { ch -> Photos.of(ctx, tr.id, b, ch)?.let { Triple(b, ch, it) } } }
        if (photos.isEmpty()) return null
        val c = Tokens.light
        val serif = ResourcesCompat.getFont(ctx, if (korean) R.font.serif_kr_medium else R.font.garamond_medium)
        val title = ResourcesCompat.getFont(ctx, if (korean) R.font.title_kr else R.font.garamond_semibold)
        fun name(b: Int) = if (korean) io.github.graviton94.todaybible.core.Canon.books[b].ko else io.github.graviton94.todaybible.core.Canon.books[b].en
        val doc = PdfDocument(); var no = 0
        fun page(): PdfDocument.Page { no++; return doc.startPage(PdfDocument.PageInfo.Builder(W, H, no).create()) }
        run {
            val pg = page(); val cv = pg.canvas; cv.drawColor(c.leaf.toArgb())
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
        val fmt = DateTimeFormatter.ofPattern("yyyy. M. d")
        for ((b, ch, f) in photos) {
            val bmp = BitmapFactory.decodeFile(f.path) ?: continue
            val pg = page(); val cv = pg.canvas; cv.drawColor(c.leaf.toArgb())
            val day = java.time.Instant.ofEpochMilli(f.lastModified()).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            cv.drawText("${name(b)} ${if (korean) "${ch}장" else "$ch"}", M.toFloat(), M.toFloat(), head)
            cv.drawText(day.format(fmt), (W - M).toFloat(), M.toFloat(), TextPaint(head).apply { textAlign = Paint.Align.RIGHT })
            val box = android.graphics.RectF(M.toFloat(), M + 12f, (W - M).toFloat(), (H - M).toFloat())
            val k = minOf(box.width() / bmp.width, box.height() / bmp.height)
            val dw = bmp.width * k; val dh = bmp.height * k
            cv.drawBitmap(bmp, null, android.graphics.RectF(box.centerX() - dw / 2, box.top, box.centerX() + dw / 2, box.top + dh), Paint(Paint.FILTER_BITMAP_FLAG))
            bmp.recycle(); doc.finishPage(pg)
        }
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
