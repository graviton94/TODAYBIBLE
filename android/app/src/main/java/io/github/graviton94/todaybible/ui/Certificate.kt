package io.github.graviton94.todaybible.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Canon
import io.github.graviton94.todaybible.core.Latin
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.core.Mode
import io.github.graviton94.todaybible.core.Translation
import io.github.graviton94.todaybible.data.Store
import io.github.graviton94.todaybible.design.Tokens
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 필사 완성 증서 (1.5 C5, 시안 docs/mockups/certificate_*.png).
 * A 종이: 권 하나 — 그 권의 판화를 아치 창에. B 흑단 금박: 구약 · 신약 · 66권 — 판화 넷 띠 · 숫자 셋 · 헌사.
 * 한 그림을 A4 PDF (인쇄) 와 나누기 카드 (그림) 에 함께 써요. 글은 번역의 언어로 (영어 모드는 영어 증서).
 */
object Certificate {
    /** 무엇을 마쳤는지: 권 하나 (0..65) 또는 OT · NT · ALL. */
    const val OT = -1; const val NT = -2; const val ALL = -3
    private const val W = 595f; private const val H = 842f   // A4 (pt)

    /** 다 썼는지 (증서를 줄 수 있는지). */
    fun earned(store: Store, tr: Translation, which: Int): Boolean {
        val p = io.github.graviton94.todaybible.core.Progress(store.loadFills())
        return books(which).all { b -> p.bookDone(tr, store.book(tr, b)) }
    }

    private fun books(which: Int): List<Int> = when (which) { OT -> (0..38).toList(); NT -> (39..65).toList(); ALL -> (0..65).toList(); else -> listOf(which) }

    private class Data(val which: Int, val korean: Boolean, val caps: String, val title: String, val lines: List<String>, val modes: List<Pair<String, Int>>,
                       val quote: String, val dedication: String, val date: String, val seal: String, val plates: List<String>, val plateLine: String, val nums: List<Pair<String, String>>)

    private fun data(ctx0: Context, store: Store, tr: Translation, which: Int, mode: Mode? = null): Data {
        // 증서의 글 (이름 · 날짜 · 권 이름 · 판화) 은 앱 언어로, 인용한 말씀만 번역으로. 글꼴은 한글이 섞이면 한글 글꼴
        val ui = Lang.uiKorean(ctx0); val ut = if (ui) Translation.KRV else Translation.KJV
        val korean = tr == Translation.KRV || ui
        val ctx = Lang.content(ctx0, ut)
        val bs = books(which).toSet()
        // 필사본 증서 (1.7): 그 방식으로 쓴 기록만
        val fills = store.loadFills().filter { it.translation == tr && it.key.book in bs && (mode == null || it.mode == mode) }
        val first = fills.minOfOrNull { it.epochDay } ?: LocalDate.now().toEpochDay(); val last = fills.maxOfOrNull { it.epochDay } ?: first
        val fmt = DateTimeFormatter.ofPattern(ctx.getString(R.string.fmt_ymd), Lang.locale(ut))
        val long = DateTimeFormatter.ofPattern(ctx.getString(R.string.cert_date), Lang.locale(ut))
        val verses = fills.map { it.key.raw }.toSet().size
        val chapters = bs.sumOf { store.book(tr, it).chapterCount }
        val bookName = { b: Int -> Canon.books[b].let { if (ui) it.ko else it.en } }
        val owner = store.ownerName.trim()
        val single = which >= 0
        val bookOf = mode?.let { m -> " · " + ctx.getString(when (m) { Mode.ALOUD -> R.string.book_aloud; Mode.PAPER -> R.string.book_paper; else -> R.string.book_type }) }.orEmpty()
        val what = if (single) bookName(which) + bookOf else ctx.getString(when (which) { OT -> R.string.cert_ot; NT -> R.string.cert_nt; else -> R.string.cert_all })
        val lines = buildList {
            if (owner.isNotEmpty()) add(ctx.getString(R.string.cert_who, owner))
            add(ctx.getString(R.string.cert_what, what, chapters, "%,d".format(verses)))
            add(ctx.getString(R.string.cert_when, LocalDate.ofEpochDay(first).format(long), LocalDate.ofEpochDay(last).format(long)))
            add(ctx.getString(if (single) R.string.cert_how else R.string.cert_how_set))
            add(ctx.getString(if (single) R.string.cert_end else R.string.cert_end_set))
        }
        val byMode = fills.groupBy { it.mode }.mapValues { it.value.size }
        val modes = listOf(Mode.ALOUD to R.string.mode_aloud, Mode.TYPE to R.string.mode_type, Mode.PAPER to R.string.mode_paper)
            .map { (m, id) -> ctx.getString(id) to (byMode[m] ?: 0) }
        val (qb, qc, qv) = if (single) Triple(which, 1, store.book(tr, which).fillable(1).firstOrNull() ?: 1) else Triple(18, 119, 105)
        val quote = "“" + Markup.plain(store.book(tr, qb).verse(qc, qv)) + "”  " + bookName(qb) + " $qc:$qv"
        val plates = if (single) listOfNotNull(store.coverPlate.takeIf { id -> id.isNotEmpty() && store.plates.any { it.id == id && it.book == which } } ?: store.plates.firstOrNull { it.book == which && it.set.isEmpty() }?.id ?: "creation")
            else when (which) { OT -> listOf("creation", "noah", "moses_sea", "daniel"); NT -> listOf("nativity", "sermon", "cross", "ascension"); else -> listOf("creation", "moses_sea", "cross", "ascension") }
                .filter { id -> store.plates.any { it.id == id } }
        val plateLine = if (single) store.plates.firstOrNull { it.id == plates.firstOrNull() }?.let { "〈" + (if (ui) it.ko else it.en) + "〉 " + (if (ui) it.byKo else it.byEn) }.orEmpty() else ""
        val nums = if (single) emptyList() else {
            val p = io.github.graviton94.todaybible.core.Progress(store.loadFills())
            val hung = store.plates.count { pl -> pl.book in bs && p.chapterFraction(tr, store.book(tr, pl.book), pl.chapter) >= 1f }
            val letters = fills.distinctBy { it.key.raw }.sumOf { f -> Markup.plain(store.book(tr, f.key.book).verse(f.key.chapter, f.key.verse)).count { !it.isWhitespace() } }
            val m = letters * Tokens.Px.scrollCellCm / 100f
            listOf("%,d".format(fills.map { it.epochDay }.toSet().size) to ctx.getString(R.string.cert_days), "$hung" to ctx.getString(R.string.cert_plates),
                (if (m >= 1000f) "%.1f km".format(m / 1000f) else "%,d m".format(m.toInt())) to ctx.getString(R.string.cert_scroll))
        }
        val caps = "CERTIFICATIO · " + when (which) { OT -> "VETUS TESTAMENTUM"; NT -> "NOVUM TESTAMENTUM"; ALL -> "BIBLIA SACRA"; else -> "LIBER " + Latin.book(which) }
        val seal = when (which) { OT -> "VETUS"; NT -> "NOVUM"; ALL -> "OMNIA"; else -> Latin.book(which).let { if (it.length > 10) it.substringAfterLast(' ') else it } }
        val title = if (single) ctx.getString(R.string.cert_title) else ctx.getString(when (which) { OT -> R.string.cert_title_ot; NT -> R.string.cert_title_nt; else -> R.string.cert_title_all })
        return Data(which, korean, caps, title, lines, modes, quote, if (single) "" else store.dedication.trim(), LocalDate.ofEpochDay(last).format(fmt), seal, plates, plateLine, nums)
    }

    private fun font(ctx: Context, id: Int) = ResourcesCompat.getFont(ctx, id)

    /** 증서 한 장을 그려요 (W × H pt 좌표). */
    private fun draw(ctx: Context, cv: Canvas, d: Data) {
        val single = d.which >= 0
        val pal = if (single) Tokens.light else Tokens.dark
        val paper = pal.leaf.toArgb(); val ink = pal.ink.toArgb(); val soft = pal.inkSoft.toArgb(); val gilt = pal.gilt.toArgb(); val giltText = pal.giltText.toArgb()
        cv.drawColor(paper)
        // 종이 결
        val grain = Paint().apply { color = ink; alpha = if (single) 10 else 14; strokeWidth = 0.5f }
        var gy = 0f; while (gy < H) { cv.drawLine(0f, gy, W, gy, grain); gy += 3f }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gilt; style = Paint.Style.STROKE }
        // 금빛 두 줄 테
        val m = if (single) 34f else 29f
        cv.drawRect(m, m, W - m, H - m, line.apply { strokeWidth = if (single) 2f else 1.5f })
        cv.drawRect(m + 8f, m + 8f, W - m - 8f, H - m - 8f, Paint(line).apply { strokeWidth = 0.6f })
        val serif = font(ctx, if (d.korean) R.font.serif_kr_medium else R.font.garamond_medium)
        val serifBold = font(ctx, if (d.korean) R.font.serif_kr_semibold else R.font.garamond_semibold)
        fun tp(face: android.graphics.Typeface?, size: Float, color: Int, track: Float = 0f) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = size; this.color = color; letterSpacing = track; textAlign = Paint.Align.CENTER }
        fun plate(id: String, box: RectF, dim: Float = 1f) {
            val bmp = runCatching { io.github.graviton94.todaybible.data.RemoteSets.open(ctx, id).use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull() ?: return
            val sc = maxOf(box.width() / bmp.width, box.height() / bmp.height); val sw = box.width() / sc; val sh = box.height() / sc
            val src = android.graphics.Rect(((bmp.width - sw) / 2).toInt(), ((bmp.height - sh) * 0.35f).toInt(), ((bmp.width + sw) / 2).toInt(), ((bmp.height - sh) * 0.35f + sh).toInt())
            val k = dim
            val sepia = android.graphics.ColorMatrix(floatArrayOf(0.30f * k, 0.59f * k, 0.11f * k, 0f, 0f, 0.28f * k, 0.55f * k, 0.10f * k, 0f, 0f, 0.24f * k, 0.47f * k, 0.09f * k, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
            cv.drawBitmap(bmp, src, box, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = android.graphics.ColorMatrixColorFilter(sepia) })
        }
        var y: Float
        if (single) {
            // 판화 아치 창
            val box = RectF(120f, 82f, W - 120f, 346f)
            cv.save()
            val arch = android.graphics.Path().apply { addOval(RectF(box.left, box.top, box.right, box.top + box.width()), android.graphics.Path.Direction.CW); addRect(box.left, box.top + box.width() / 2, box.right, box.bottom, android.graphics.Path.Direction.CW) }
            cv.clipPath(arch); d.plates.firstOrNull()?.let { plate(it, box) }; cv.restore()
            val frame = android.graphics.Path().apply {
                moveTo(box.left - 5f, box.bottom + 5f); lineTo(box.left - 5f, box.top + box.width() / 2)
                arcTo(RectF(box.left - 5f, box.top - 5f, box.right + 5f, box.top + box.width() + 5f), 180f, 180f); lineTo(box.right + 5f, box.bottom + 5f); close()
            }
            cv.drawPath(frame, Paint(line).apply { strokeWidth = 1.4f })
            cv.drawText(d.plateLine, W / 2, 365f, tp(serif, 9.5f, soft))
            y = 400f
        } else {
            // 판화 넷 띠
            val top = 62f; val h = 158f; val x0 = 62f; val x1 = W - 62f; val gap = 7f; val cw = (x1 - x0 - gap * 3) / 4
            d.plates.take(4).forEachIndexed { i, id -> val box = RectF(x0 + i * (cw + gap), top, x0 + i * (cw + gap) + cw, top + h); plate(id, box, 0.88f); cv.drawRect(box, Paint(line).apply { strokeWidth = 1f }) }
            y = 262f
        }
        cv.drawText(d.caps, W / 2, y, tp(font(ctx, R.font.caps), 13f, giltText, 0.18f)); y += 40f
        cv.drawText(d.title, W / 2, y, tp(serifBold, if (single) 34f else 32f, ink)); y += 18f
        cv.drawLine(W / 2 - 60f, y, W / 2 + 60f, y, Paint(line).apply { strokeWidth = 1f }); y += 32f
        val body = tp(serif, 16.5f, ink)
        d.lines.forEach { l -> cv.drawText(l, W / 2, y, body); y += 29f }
        // 쓴 방식 비율 띠
        y += 10f
        val total = d.modes.sumOf { it.second }.coerceAtLeast(1); var x = 126f; val bw = W - 252f
        val cols = listOf(gilt, ink, soft)
        val lab = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 10f; color = soft }
        d.modes.forEachIndexed { i, (name, n) ->
            if (n == 0) return@forEachIndexed
            val w = bw * n / total
            cv.drawRect(x, y, x + w - 3f, y + 4.5f, Paint().apply { color = cols[i] })
            cv.drawText("$name ${Math.round(n * 100f / total)}%", x, y + 18f, lab); x += w
        }
        y += 52f
        if (d.nums.isNotEmpty()) {
            d.nums.forEachIndexed { i, (n, label) ->
                val cx = W / 2 + (i - 1) * 172f
                cv.drawText(n, cx, y, tp(font(ctx, R.font.display), 29f, gilt)); cv.drawText(label, cx, y + 20f, tp(serif, 10.5f, soft))
            }
            y += 56f
        }
        // 말씀 한 줄 (넘치면 글자를 줄여요)
        val q = tp(serif, 12.5f, soft); while (q.measureText(d.quote) > W - 150f && q.textSize > 8f) q.textSize -= 0.5f
        cv.drawText(d.quote, W / 2, y, q); y += 22f
        if (d.dedication.isNotEmpty()) cv.drawText(d.dedication, W / 2, y, tp(serif, 12f, soft))
        // 아래: 날 · 앱 이름 · COMPLETUM 인장
        val left = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font(ctx, R.font.display); textSize = 19f; color = ink }
        cv.drawText(d.date, 80f, H - 92f, left)
        cv.drawText(ctx.getString(R.string.app_name) + " · BIBLE BY HAND", 80f, H - 68f, TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 10f; color = soft })
        seal(ctx, cv, W - 118f, H - 102f, if (single) 38f else 44f, gilt, d.seal)
    }

    /** COMPLETUM 인장: 금빛 두 줄 테 · 두 줄 글, 비스듬히. (10/14: 시안보다 조금 작게) */
    private fun seal(ctx: Context, cv: Canvas, cx: Float, cy: Float, r: Float, color: Int, second: String) {
        cv.save(); cv.rotate(-12f, cx, cy)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 2.2f }
        cv.drawCircle(cx, cy, r, p); cv.drawCircle(cx, cy, r - 5.5f, Paint(p).apply { strokeWidth = 0.9f })
        val t = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = ResourcesCompat.getFont(ctx, R.font.caps); textSize = r * 0.19f; this.color = color; textAlign = Paint.Align.CENTER }
        cv.drawText("COMPLETUM", cx, cy - r * 0.02f, t)
        t.textSize = r * 0.155f; while (t.measureText(second) > r * 1.5f && t.textSize > 4f) t.textSize -= 0.3f
        cv.drawText(second, cx, cy + r * 0.30f, t)
        cv.restore()
    }

    /** PDF (A4) 와 카드 그림을 만들어요. 돌려주는 것: [pdf, png]. */
    fun make(ctx: Context, store: Store, tr: Translation, which: Int, mode: Mode? = null): List<File> {
        val d = data(ctx, store, tr, which, mode)
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val name = if (which >= 0) Canon.books[which].let { if (d.korean) it.ko else it.en }.replace(' ', '_') else when (which) { OT -> "OT"; NT -> "NT"; else -> "Bible" }
        val pdf = File(dir, "${name}_${ctx.getString(R.string.cert_file)}.pdf")
        val doc = PdfDocument()
        val pg = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), 1).create())
        draw(ctx, pg.canvas, d); doc.finishPage(pg)
        pdf.outputStream().use { doc.writeTo(it) }; doc.close()
        // 카드: 같은 그림을 1080 너비로
        val sc = 1080f / W
        val bmp = Bitmap.createBitmap(1080, (H * sc).toInt(), Bitmap.Config.ARGB_8888)
        Canvas(bmp).apply { scale(sc, sc); draw(ctx, this, d) }
        val png = File(dir, "${name}_${ctx.getString(R.string.cert_file)}.png").also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        return listOf(pdf, png)
    }
}
