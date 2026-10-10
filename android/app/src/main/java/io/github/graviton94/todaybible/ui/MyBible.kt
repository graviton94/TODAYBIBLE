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
 * 나의 성경 (D2): 내가 옮겨 쓴 한 권을 PDF 로. 표지 (판화 · 이름 · 쓴 기간 · 헌사) 다음에
 * 본문 (머리글 · 금빛 장 번호와 절 번호 · 절마다 바깥 여백에 처음 쓴 날짜). A5 인쇄 · 제본용: 표지 뒷면 비움, 묶는 쪽 여백, 쪽 번호, 맺음 쪽.
 */
object MyBible {
    private const val W = 420; private const val H = 595 // A5 (pt)
    private const val M = 42
    /** 제본 여백 (pt): 묶이는 쪽에 더 둠. */
    private const val BIND = 14

    /** 표지 (1.2): 어두운 바탕 위로 판화가 위를 채우고 어둠에 녹아들어요. 아래에 라틴 머리글 · 제목 · 몇 줄 · 금선 · 헌사. */
    private fun cover(ctx: Context, cv: android.graphics.Canvas, plateId: String?, caps: String, title: String, lines: List<String>, dedication: String, korean: Boolean) {
        val d = Tokens.dark
        cv.drawColor(d.leaf.toArgb())
        val artH = H * 0.6f
        plateId?.let { id -> runCatching { ctx.assets.open("plates/$id.jpg").use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull() }?.let { bmp ->
            val sc = maxOf(W / bmp.width.toFloat(), artH / bmp.height); val dw = bmp.width * sc; val dh = bmp.height * sc
            val sepia = android.graphics.ColorMatrix(floatArrayOf(0.30f, 0.59f, 0.11f, 0f, 0f, 0.28f, 0.55f, 0.10f, 0f, 0f, 0.24f, 0.47f, 0.09f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
            cv.save(); cv.clipRect(0f, 0f, W.toFloat(), artH)
            cv.drawBitmap(bmp, null, android.graphics.RectF((W - dw) / 2f, (artH - dh) * 0.3f, (W + dw) / 2f, (artH - dh) * 0.3f + dh), Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = android.graphics.ColorMatrixColorFilter(sepia) })
            cv.restore()
            val fade = Paint().apply { shader = android.graphics.LinearGradient(0f, artH * 0.4f, 0f, artH, 0x0017110C, d.leaf.toArgb(), android.graphics.Shader.TileMode.CLAMP) }
            cv.drawRect(0f, artH * 0.4f, W.toFloat(), artH + 1f, fade)
        }
        val x = 40f; var y = artH + 18f
        val capsTp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = ResourcesCompat.getFont(ctx, R.font.caps); textSize = 7.5f; letterSpacing = 0.2f; color = d.gilt.toArgb() }
        cv.drawText(caps, x, y, capsTp); y += 30f
        val tt = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = ResourcesCompat.getFont(ctx, if (korean) R.font.serif_kr_semibold else R.font.garamond_semibold); textSize = 24f; color = d.ink.toArgb() }
        cv.drawText(title, x, y, tt); y += 20f
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = ResourcesCompat.getFont(ctx, if (korean) R.font.serif_kr_medium else R.font.garamond_medium); textSize = 9.5f; color = d.inkSoft.toArgb() }
        lines.forEach { cv.drawText(it, x, y, sub); y += 14f }
        y += 6f; cv.drawLine(x, y, x + 30f, y, Paint().apply { color = d.gilt.toArgb(); strokeWidth = 0.7f }); y += 18f
        if (dedication.isNotBlank()) cv.drawText("“$dedication”", x, y, TextPaint(sub).apply { textSkewX = -0.18f; color = d.ink.toArgb() })
        cv.drawText(ctx.getString(R.string.app_name), x, H - 32f, TextPaint(sub).apply { textSize = 8f })
    }

    fun make(ctx: Context, store: Store, tr: Translation, book: Int, owner: String = store.ownerName): File {
        val korean = tr == Translation.KRV
        val text = store.book(tr, book)
        val firstDay = HashMap<Int, Long>()
        store.loadFills().filter { it.translation == tr && it.key.book == book }.forEach { f -> firstDay.merge(f.key.raw, f.epochDay) { a, b -> minOf(a, b) } }
        val name = if (korean) io.github.graviton94.todaybible.core.Canon.books[book].ko else io.github.graviton94.todaybible.core.Canon.books[book].en
        val c = Tokens.light
        val serif = ResourcesCompat.getFont(ctx, if (korean) R.font.serif_kr_medium else R.font.garamond_medium)
        val title = ResourcesCompat.getFont(ctx, if (korean) R.font.title_kr else R.font.garamond_semibold)
        val doc = PdfDocument()
        var pageNo = 0
        fun newPage(): PdfDocument.Page { pageNo++; return doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create()) }

        // 표지: 고른 표지 판화 (없으면 이 권의 판화 · 날마다의 한 점)
        run {
            val pg = newPage()
            val days = firstDay.values
            val fmt = DateTimeFormatter.ofPattern(Lang.content(ctx, tr).getString(R.string.fmt_ymd), Lang.locale(tr))
            val plate = store.coverPlate.takeIf { it.isNotEmpty() } ?: store.plates.firstOrNull { it.book == book }?.id ?: "creation"
            val who = if (owner.isNotBlank()) ctx.getString(R.string.mybook_named, owner) else ctx.getString(R.string.mybook)
            val chDone = (1..text.chapterCount).count { ch -> text.fillable(ch).let { f -> f.isNotEmpty() && f.all { v -> firstDay.containsKey(VerseKey(book, ch, v).raw) } } }
            val lines = listOfNotNull(ctx.getString(R.string.pdf_cover_book, name, text.chapterCount, chDone),
                days.takeIf { it.isNotEmpty() }?.let { "${LocalDate.ofEpochDay(it.min()).format(fmt)} – ${LocalDate.ofEpochDay(it.max()).format(fmt)}" })
            cover(ctx, pg.canvas, plate, "BIBLIA MANU SCRIPTA", who, lines, store.dedication, korean)
            doc.finishPage(pg)
        }

        // 표지 뒷면은 비워 두어 본문이 오른쪽 쪽에서 시작 (제본)
        run { val pg = newPage(); pg.canvas.drawColor(c.leaf.toArgb()); doc.finishPage(pg) }

        // 본문: 제본 쪽 (오른쪽 쪽은 왼쪽, 왼쪽 쪽은 오른쪽) 여백을 BIND 만큼 더 둠 · 아래 가운데 쪽 번호
        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 10.5f; color = c.ink.toArgb() }
        val display = ResourcesCompat.getFont(ctx, R.font.display)
        val num = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display; textSize = 9.5f; color = c.gilt.toArgb() }
        val margin = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display; textSize = 7f; color = c.unwritten.toArgb() }
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = if (store.latinHeads) ResourcesCompat.getFont(ctx, R.font.caps) else serif; textSize = 6.5f; color = c.giltText.toArgb(); letterSpacing = 0.2f }
        val folio = TextPaint(head).apply { textAlign = Paint.Align.CENTER }
        val chap = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display; textSize = 32f; color = c.giltText.toArgb() }
        val md = DateTimeFormatter.ofPattern(Lang.content(ctx, tr).getString(R.string.fmt_margin_md), Lang.locale(tr))
        val wb = W - BIND
        val gutter = 16f; val colW = (wb - 2 * M - gutter - 26).toInt()
        fun open(): PdfDocument.Page { val p = newPage(); p.canvas.drawColor(c.leaf.toArgb()); p.canvas.translate(if (pageNo % 2 == 1) BIND.toFloat() else 0f, 0f); return p }
        fun close(p: PdfDocument.Page) { p.canvas.drawText("${pageNo - 2}", wb / 2f, H - M / 2f, folio); doc.finishPage(p) }
        var pg = open(); var y = M.toFloat() + 14f
        // 장마다 남긴 묵상 한 줄 (A4): 그 장 끝에 금빛 기울임으로
        val lines = store.loadNotes().filter { it.kind == io.github.graviton94.todaybible.data.Store.NoteKind.REFLECTION && it.translation == tr && it.book == book }.associateBy { it.chapter }
        val reflect = TextPaint(body).apply { textSize = 9f; color = c.giltText.toArgb(); textSkewX = -0.18f }
        // 머리글: 왼쪽 권 (라틴 대문자 또는 이름) · 오른쪽 장 (로마 숫자) · 아래 가는 선
        val headRight = TextPaint(head).apply { textAlign = Paint.Align.RIGHT }
        fun header(ch: Int) {
            val left = if (store.latinHeads) io.github.graviton94.todaybible.core.Latin.book(book) else name
            val right = if (store.latinHeads) io.github.graviton94.todaybible.core.Latin.roman(ch) else "$ch"
            pg.canvas.drawText(left, M.toFloat(), M.toFloat(), head); pg.canvas.drawText(right, (wb - M).toFloat(), M.toFloat(), headRight)
            pg.canvas.drawLine(M.toFloat(), M + 5f, (wb - M).toFloat(), M + 5f, Paint().apply { color = c.hair.toArgb(); strokeWidth = 0.4f })
        }
        header(1)
        for (ch in 1..text.chapterCount) {
            if (y > H - M - 60) { close(pg); pg = open(); header(ch); y = M + 14f }
            pg.canvas.drawText(if (store.latinHeads) io.github.graviton94.todaybible.core.Latin.roman(ch) else "$ch", M.toFloat(), y + 26f, chap); y += 40f
            for (v in text.fillable(ch)) {
                val s = Markup.plain(text.verse(ch, v))
                val lay = StaticLayout.Builder.obtain(s, 0, s.length, body, colW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL).setLineSpacing(0f, 1.25f).setIncludePad(false)
                    .apply {
                        // 한글이 낱말 가운데서 끊기지 않게 (안드로이드 13+)
                        if (android.os.Build.VERSION.SDK_INT >= 33) setLineBreakConfig(android.graphics.text.LineBreakConfig.Builder()
                            .setLineBreakWordStyle(android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE).build())
                    }.build()
                if (y + lay.height > H - M) { close(pg); pg = open(); header(ch); y = M + 14f }
                val cv = pg.canvas
                cv.save(); cv.translate(M + gutter, y); lay.draw(cv); cv.restore()
                // 절 번호 · 쓴 날짜는 첫 줄과 같은 줄에
                val base = y + lay.getLineBaseline(0)
                cv.drawText("$v", M.toFloat(), base, num.apply { textAlign = Paint.Align.LEFT })
                firstDay[VerseKey(book, ch, v).raw]?.let { d -> cv.drawText(LocalDate.ofEpochDay(d).format(md), (wb - M).toFloat() - 18f, base, margin) }
                y += lay.height + 5f
            }
            lines[ch]?.let { n ->
                val t = "— " + n.text
                val lay = StaticLayout.Builder.obtain(t, 0, t.length, reflect, colW).setLineSpacing(0f, 1.2f).setIncludePad(false).build()
                if (y + lay.height > H - M) { close(pg); pg = open(); header(ch); y = M + 14f }
                pg.canvas.save(); pg.canvas.translate(M + gutter, y + 2f); lay.draw(pg.canvas); pg.canvas.restore()
                y += lay.height + 6f
            }
            y += 8f
        }
        close(pg)
        // 설교 노트 (A2): 이 권을 본문으로 한 주일 설교 노트를 날짜순으로
        val sermons = store.loadNotes().filter { it.kind == io.github.graviton94.todaybible.data.Store.NoteKind.SERMON && it.book == book && it.from > 0 }.sortedBy { it.epochDay }
        if (sermons.isNotEmpty()) {
            pg = open(); y = M.toFloat() + 14f
            val cc = Lang.content(ctx, tr)
            pg.canvas.drawText(cc.getString(R.string.sermon_title), M.toFloat(), y + 12f, TextPaint(body).apply { textSize = 15f; color = c.giltText.toArgb() }); y += 34f
            val day = DateTimeFormatter.ofPattern(cc.getString(R.string.fmt_ymd), Lang.locale(tr))
            for (n in sermons) {
                val headLine = LocalDate.ofEpochDay(n.epochDay).format(day) + "  ·  " + Lang.passage(ctx, tr, name, n.chapter, n.from, n.to)
                val lay = StaticLayout.Builder.obtain(n.text, 0, n.text.length, body, colW + gutter.toInt()).setLineSpacing(0f, 1.25f).setIncludePad(false).build()
                if (y + 16f + lay.height > H - M) { close(pg); pg = open(); y = M.toFloat() + 14f }
                pg.canvas.drawText(headLine, M.toFloat(), y + 8f, TextPaint(body).apply { textSize = 8f; color = c.giltText.toArgb() }); y += 16f
                pg.canvas.save(); pg.canvas.translate(M.toFloat(), y); lay.draw(pg.canvas); pg.canvas.restore()
                y += lay.height + 18f
            }
            close(pg)
        }
        // 맺음 쪽: 누가 언제부터 언제까지 옮겨 썼는지
        run {
            val p = open(); val cv = p.canvas
            val days = firstDay.values
            val fmt = DateTimeFormatter.ofPattern(Lang.content(ctx, tr).getString(R.string.fmt_ymd), Lang.locale(tr))
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 10f; color = c.inkSoft.toArgb(); textAlign = Paint.Align.CENTER }
            if (days.isNotEmpty()) {
                val who = if (owner.isBlank()) ctx.getString(R.string.colophon_me) else ctx.getString(R.string.colophon_named, owner)
                cv.drawText(ctx.getString(R.string.colophon, who, name), wb / 2f, H * 0.45f, tp)
                cv.drawText("${LocalDate.ofEpochDay(days.min()).format(fmt)} – ${LocalDate.ofEpochDay(days.max()).format(fmt)} · ${ctx.getString(R.string.colophon_count, days.size)}", wb / 2f, H * 0.45f + 18f, tp)
            }
            doc.finishPage(p)
        }
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
            val pg = page()
            val plate = store.coverPlate.takeIf { it.isNotEmpty() } ?: book?.let { b -> store.plates.firstOrNull { it.book == b }?.id } ?: "creation"
            cover(ctx, pg.canvas, plate, "MANU MEA", ctx.getString(R.string.notes_cover), listOfNotNull(if (book != null) name(book) else null, store.ownerName.takeIf { it.isNotBlank() }), store.dedication, korean)
            doc.finishPage(pg)
        }
        val head = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 8f; color = c.inkSoft.toArgb() }
        val num = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = ResourcesCompat.getFont(ctx, R.font.display); textSize = 10f; color = c.gilt.toArgb() }
        val rule = Paint().apply { color = c.noteLine.toArgb(); strokeWidth = 0.4f }
        val md = DateTimeFormatter.ofPattern(Lang.content(ctx, tr).getString(R.string.fmt_margin_md), Lang.locale(tr))
        val gutter = 30f; val left = M + gutter; val w = W - M - left
        var pg = page(); var y = M.toFloat()
        for ((b, ch) in chapters) {
            if (y > M + 1f) { doc.finishPage(pg); pg = page(); y = M.toFloat() }
            pg.canvas.drawText(Lang.chapterRef(ctx, tr, name(b), ch), M.toFloat(), y, head); y += 14f
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
                    io.github.graviton94.todaybible.data.Ink.draw(cv, sheet, left, y, w, (if (ink.pen == io.github.graviton94.todaybible.data.Ink.PENCIL) c.graphite else io.github.graviton94.todaybible.design.inkColor(store.ink, false)).toArgb(), ink.pen)
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
