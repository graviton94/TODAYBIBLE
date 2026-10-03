package io.github.graviton94.todaybible.data

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * 손글씨 (손가락 · 펜으로 한 절씩). 그림이 아니라 획으로 남겨서 작고, 어떤 크기로 다시 그려도 선명해요.
 * 한 절 = 파일 하나: 장(sheet) 여러 장 · 장마다 획 · 획마다 점 (x, y, 굵기).
 * 좌표는 쓰던 칸의 너비를 1로 둔 값이라 화면 크기가 달라도 같은 모양으로 그려져요.
 * 점 하나 5바이트: x · y 는 0..1 (y 는 0..4) 을 16비트로, 굵기는 너비의 1/10000 단위 1바이트.
 */
object Ink {
    private const val MAGIC = 0x494E4B31 // "INK1"
    private const val MAGIC2 = 0x494E4B32 // "INK2": 줄 간격 뒤에 펜 한 바이트
    /** 펜: 만년필 · 붓펜 · 연필. */
    const val FOUNTAIN = 0; const val BRUSH = 1; const val PENCIL = 2
    private const val Y_RANGE = 4f

    /** 획 하나: [x0, y0, w0, x1, y1, w1, …] (너비 = 1 기준). */
    class Stroke(val pts: FloatArray) { val size get() = pts.size / 3 }

    /** 한 절의 손글씨: 장마다 획들 · 줄 간격 (너비 = 1 기준). */
    class Page(val sheets: List<List<Stroke>>, val line: Float, val pen: Int = FOUNTAIN) {
        fun height(sheet: Int): Float = sheets.getOrNull(sheet)?.maxOfOrNull { s -> (0 until s.size).maxOf { s.pts[it * 3 + 1] } } ?: 0f
    }

    fun dir(ctx: Context) = File(ctx.filesDir, "ink").apply { mkdirs() }
    fun file(ctx: Context, tr: String, book: Int, chapter: Int, verse: Int) = File(dir(ctx), "${tr}_${book}_${chapter}_$verse.ink")
    fun has(ctx: Context, tr: String, book: Int, chapter: Int, verse: Int) = file(ctx, tr, book, chapter, verse).exists()

    /** 이 장에 손으로 쓴 절들. */
    fun verses(ctx: Context, tr: String, book: Int, chapter: Int): List<Int> {
        val pre = "${tr}_${book}_${chapter}_"
        return dir(ctx).list()?.filter { it.startsWith(pre) && it.endsWith(".ink") }?.mapNotNull { it.removePrefix(pre).removeSuffix(".ink").toIntOrNull() }?.sorted().orEmpty()
    }

    /** 이 책에서 손으로 쓴 장들. */
    fun chapters(ctx: Context, tr: String, book: Int): List<Int> {
        val pre = "${tr}_${book}_"
        return dir(ctx).list()?.filter { it.startsWith(pre) }?.mapNotNull { it.removePrefix(pre).substringBefore('_').toIntOrNull() }?.distinct()?.sorted().orEmpty()
    }

    fun usage(ctx: Context): Long = dir(ctx).listFiles()?.sumOf { it.length() } ?: 0L

    fun save(f: File, page: Page) {
        val tmp = File(f.path + ".tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { o ->
            o.writeInt(MAGIC2); o.writeFloat(page.line); o.writeByte(page.pen); o.writeShort(page.sheets.size)
            for (sheet in page.sheets) {
                o.writeShort(sheet.size)
                for (s in sheet) {
                    o.writeShort(s.size)
                    for (i in 0 until s.size) {
                        o.writeShort(q(s.pts[i * 3], 1f)); o.writeShort(q(s.pts[i * 3 + 1], Y_RANGE))
                        o.writeByte((s.pts[i * 3 + 2] * 10000f).toInt().coerceIn(1, 255))
                    }
                }
            }
        }
        tmp.renameTo(f)
    }

    fun load(f: File): Page? = runCatching {
        DataInputStream(f.inputStream().buffered()).use { i ->
            val magic = i.readInt()
            if (magic != MAGIC && magic != MAGIC2) return null
            val line = i.readFloat()
            val pen = if (magic == MAGIC2) i.readUnsignedByte() else FOUNTAIN
            val sheets = List(i.readUnsignedShort()) {
                List(i.readUnsignedShort()) {
                    val n = i.readUnsignedShort()
                    val pts = FloatArray(n * 3)
                    for (j in 0 until n) {
                        pts[j * 3] = i.readUnsignedShort() / 65535f
                        pts[j * 3 + 1] = i.readUnsignedShort() / 65535f * Y_RANGE
                        pts[j * 3 + 2] = i.readUnsignedByte() / 10000f
                    }
                    Stroke(pts)
                }
            }
            Page(sheets, line, pen)
        }
    }.getOrNull()

    private fun q(v: Float, range: Float) = (v / range * 65535f).toInt().coerceIn(0, 65535)

    /** 안드로이드 캔버스(PDF · 그림)에 한 장을 그려요. 너비 w 픽셀, (x, y) 에서 시작. */
    fun draw(cv: android.graphics.Canvas, sheet: List<Stroke>, x: Float, y: Float, w: Float, color: Int, pen: Int = FOUNTAIN) {
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; if (pen == PENCIL) alpha = 215; style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
        }
        for (s in sheet) {
            if (s.size == 1) { p.style = android.graphics.Paint.Style.FILL; cv.drawCircle(x + s.pts[0] * w, y + s.pts[1] * w, s.pts[2] * w / 2, p); p.style = android.graphics.Paint.Style.STROKE; continue }
            // 만년필: 획이 시작하는 곳에 잉크가 살짝 고임
            if (pen == FOUNTAIN) { p.style = android.graphics.Paint.Style.FILL; cv.drawCircle(x + s.pts[0] * w, y + s.pts[1] * w, s.pts[2] * w * 0.62f, p); p.style = android.graphics.Paint.Style.STROKE }
            for (j in 1 until s.size) {
                p.strokeWidth = (s.pts[j * 3 - 1] + s.pts[j * 3 + 2]) / 2 * w
                cv.drawLine(x + s.pts[j * 3 - 3] * w, y + s.pts[j * 3 - 2] * w, x + s.pts[j * 3] * w, y + s.pts[j * 3 + 1] * w, p)
            }
        }
    }
}
