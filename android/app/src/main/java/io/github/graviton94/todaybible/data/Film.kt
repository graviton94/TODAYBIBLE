package io.github.graviton94.todaybible.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.design.Tokens
import java.io.File
import java.nio.ByteBuffer

/**
 * 내 목소리 영상 (1.2, 평생권 · 구독): 폰 세로 1080×1920 · 24fps.
 * 처음 3초 판화 위에 장 이름 → 녹음한 절마다 그 절의 글이 내 목소리를 따라 낱말씩 밝아지고 → 끝 3초 앱 이름과 날짜.
 * 판화는 영상 내내 아주 천천히 다가와요. 낱말이 밝아지는 박자는 그 절 녹음 길이를 글자 수로 나눠 맞춰요 (말이 빨라지는 자리는 조금 어긋날 수 있어요).
 */
object Film {
    class Scene(val number: Int, val text: String, val file: File)

    private const val FPS = 24
    private const val TITLE_US = 3_000_000L
    private const val END_US = 3_000_000L
    private const val FADE_US = 350_000L

    fun export(ctx: Context, korean: Boolean, plateId: String?, eyebrow: String, title: String, byline: String, endLine: String,
               scenes: List<Scene>, out: File, progress: (Float) -> Unit): Boolean = runCatching {
        val (W, H) = if (supports(1080, 1920)) 1080 to 1920 else 720 to 1280
        val k = W / 1080f
        val d = Tokens.dark
        // 절마다 시작 시각 (소리를 이어 붙이는 셈과 같게)
        val spans = scenes.map { Voice.spanUs(it.file) }
        val starts = spans.runningFold(TITLE_US) { a, b -> a + b }
        val voiceEnd = starts.last()
        val total = voiceEnd + END_US

        // 재료: 판화 (먹빛, 화면보다 조금 크게), 글꼴, 아이콘
        val art = plateId?.let { id -> runCatching { ctx.assets.open("plates/$id.jpg").use { BitmapFactory.decodeStream(it) } }.getOrNull() }
        val sepia = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(0.30f, 0.59f, 0.11f, 0f, 0f, 0.28f, 0.55f, 0.10f, 0f, 0f, 0.24f, 0.47f, 0.09f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
        val artPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { colorFilter = sepia }
        val serif = ResourcesCompat.getFont(ctx, if (korean) R.font.serif_kr_semibold else R.font.garamond_semibold)
        val serifM = ResourcesCompat.getFont(ctx, if (korean) R.font.serif_kr_medium else R.font.garamond_medium)
        val caps = ResourcesCompat.getFont(ctx, R.font.caps)
        val display = ResourcesCompat.getFont(ctx, R.font.display)
        val icon = runCatching {
            val a = BitmapFactory.decodeResource(ctx.resources, R.mipmap.ic_launcher_art); val l = BitmapFactory.decodeResource(ctx.resources, R.mipmap.ic_launcher_line)
            Bitmap.createBitmap(a.width, a.height, Bitmap.Config.ARGB_8888).also { b -> Canvas(b).apply { drawBitmap(a, 0f, 0f, null); drawBitmap(l, 0f, 0f, null) } }
        }.getOrNull()
        val gilt = d.gilt.toArgb(); val ink = d.ink.toArgb(); val soft = d.inkSoft.toArgb(); val leaf = d.leaf.toArgb(); val glow = Tokens.dark.giltHi.toArgb()

        val frame = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888); val cv = Canvas(frame)
        val capsP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = caps; textSize = 30f * k; letterSpacing = 0.2f; color = gilt }
        val titleP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 64f * k; color = ink }
        val smallP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serifM; textSize = 32f * k; color = soft }
        val numP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = display; textSize = 96f * k; color = gilt }
        val verseP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serif; textSize = 50f * k; color = ink }
        val footP = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = serifM; textSize = 26f * k; color = soft }
        val pad = 84f * k
        val layouts = HashMap<Int, StaticLayout>()

        fun drawArt(t: Long, dim: Float) {
            cv.drawColor(leaf)
            val a = art ?: return
            val zoom = 1.04f + 0.12f * (t.toFloat() / total)
            val base = maxOf(W / a.width.toFloat(), H * 0.78f / a.height)
            val sc = base * zoom; val dw = a.width * sc; val dh = a.height * sc
            val m = Matrix().apply { setScale(sc, sc); postTranslate((W - dw) / 2f, (H * 0.74f - dh) * 0.35f) }
            artPaint.alpha = (255 * dim).toInt(); cv.drawBitmap(a, m, artPaint)
            // 위는 살짝, 아래는 어둠으로 녹아들게
            cv.drawRect(0f, 0f, W.toFloat(), H * 0.3f, Paint().apply { shader = LinearGradient(0f, 0f, 0f, H * 0.3f, (0x8C shl 24) or (leaf and 0xFFFFFF), leaf and 0xFFFFFF, Shader.TileMode.CLAMP) })
            cv.drawRect(0f, H * 0.38f, W.toFloat(), H.toFloat(), Paint().apply { shader = LinearGradient(0f, H * 0.38f, 0f, H * 0.78f, leaf and 0xFFFFFF, leaf, Shader.TileMode.CLAMP) })
        }
        fun fade(t: Long, a: Long, b: Long): Float = when { t < a + FADE_US -> ((t - a).toFloat() / FADE_US).coerceIn(0f, 1f); t > b - FADE_US -> ((b - t).toFloat() / FADE_US).coerceIn(0f, 1f); else -> 1f }

        fun render(t: Long) {
            when {
                t < TITLE_US -> {
                    drawArt(t, 1f)
                    val a = fade(t, 0, TITLE_US)
                    capsP.alpha = (255 * a).toInt(); titleP.alpha = capsP.alpha; smallP.alpha = capsP.alpha
                    var y = H * 0.70f
                    cv.drawText(eyebrow, pad, y, capsP); y += 92f * k
                    cv.drawText(title, pad, y, titleP); y += 60f * k
                    cv.drawText(byline, pad, y, smallP)
                }
                t < voiceEnd -> {
                    drawArt(t, 1f)
                    val i = (starts.indexOfLast { it <= t }).coerceIn(0, scenes.lastIndex)
                    val sc = scenes[i]; val s0 = starts[i]; val s1 = starts[i + 1]
                    // 위: 머리글 · 장 이름 · 누구의 목소리
                    capsP.alpha = 255; titleP.alpha = 255; smallP.alpha = 255
                    cv.drawText(eyebrow, pad, 150f * k, capsP)
                    cv.drawText(title, pad, 222f * k, TextPaint(titleP).apply { textSize = 40f * k })
                    cv.drawText(byline, pad, 270f * k, TextPaint(smallP).apply { textSize = 26f * k })
                    // 아래: 절 번호 · 낱말씩 밝아지는 본문
                    val a = fade(t, s0, s1)
                    val words = sc.text.split(' ').filter { it.isNotEmpty() }
                    val chars = words.sumOf { it.length }.coerceAtLeast(1)
                    val lead = 150_000L; val span = (s1 - s0 - lead - 200_000L).coerceAtLeast(1)
                    val spoken = ((t - s0 - lead).toFloat() / span * chars).coerceIn(0f, chars.toFloat())
                    var acc = 0; var now = -1
                    val sp = SpannableString(words.joinToString(" "))
                    var pos = 0
                    words.forEachIndexed { wi, w ->
                        val done = acc + w.length <= spoken
                        val cur = !done && acc <= spoken
                        if (cur) now = wi
                        val col = when { done -> ink; cur -> glow; else -> (0x52 shl 24) or (ink and 0xFFFFFF) }
                        sp.setSpan(ForegroundColorSpan(col), pos, pos + w.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        acc += w.length; pos += w.length + 1
                    }
                    val lay = StaticLayout.Builder.obtain(sp, 0, sp.length, verseP, (W - 2 * pad).toInt()).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, 1.6f).setIncludePad(false).apply {
                            if (android.os.Build.VERSION.SDK_INT >= 33) setLineBreakConfig(android.graphics.text.LineBreakConfig.Builder().setLineBreakWordStyle(android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE).build())
                        }.build()
                    val bottom = H - 230f * k
                    val top = bottom - lay.height
                    cv.saveLayerAlpha(0f, 0f, W.toFloat(), H.toFloat(), (255 * a).toInt())
                    cv.drawText("${sc.number}", pad, top - 40f * k, numP)
                    cv.save(); cv.translate(pad, top); lay.draw(cv); cv.restore()
                    cv.restore()
                    // 맨 아래: 가는 금선 진행 · 몇 번째 절 · 앱 이름
                    val y = H - 150f * k
                    cv.drawRect(pad, y, W - pad, y + 2f * k, Paint().apply { color = (0x29 shl 24) or (ink and 0xFFFFFF) })
                    cv.drawRect(pad, y, pad + (W - 2 * pad) * ((t - TITLE_US).toFloat() / (voiceEnd - TITLE_US)), y + 2f * k, Paint().apply { color = gilt })
                    footP.textAlign = Paint.Align.LEFT; cv.drawText("${i + 1} / ${scenes.size}", pad, y + 50f * k, footP)
                    footP.textAlign = Paint.Align.RIGHT; cv.drawText(ctx.getString(R.string.app_name), W - pad, y + 50f * k, footP)
                }
                else -> {
                    val a = fade(t, voiceEnd, total + FADE_US)
                    drawArt(t, 1f - a.coerceAtMost(1f) * 0.85f)
                    cv.drawColor((((255 * a * 0.9f).toInt()) shl 24) or (leaf and 0xFFFFFF))
                    val cx = W / 2f
                    icon?.let { ic ->
                        val r = 84f * k; val src = ic.width * 18 / 108
                        val path = android.graphics.Path().apply { addCircle(cx, H * 0.42f, r, android.graphics.Path.Direction.CW) }
                        cv.save(); cv.clipPath(path)
                        cv.drawBitmap(ic, android.graphics.Rect(src, src, ic.width - src, ic.height - src), RectF(cx - r, H * 0.42f - r, cx + r, H * 0.42f + r), Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (255 * a).toInt() })
                        cv.restore()
                    }
                    titleP.alpha = (255 * a).toInt(); smallP.alpha = titleP.alpha
                    titleP.textAlign = Paint.Align.CENTER; smallP.textAlign = Paint.Align.CENTER
                    cv.drawText(ctx.getString(R.string.app_name), cx, H * 0.42f + 170f * k, TextPaint(titleP).apply { textSize = 48f * k })
                    cv.drawText(endLine, cx, H * 0.42f + 230f * k, smallP)
                    titleP.textAlign = Paint.Align.LEFT; smallP.textAlign = Paint.Align.LEFT
                }
            }
        }

        // 부호기 (H.264, 세로)
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, W, H).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, if (W >= 1080) 6_000_000 else 3_500_000); setInteger(MediaFormat.KEY_FRAME_RATE, FPS); setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); enc.start()
        out.parentFile?.mkdirs()
        val tmp = File(out.path + ".v.mp4")
        val vm = MediaMuxer(tmp.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var vt = -1; var started = false; val info = MediaCodec.BufferInfo()
        fun drain(end: Boolean) {
            while (true) {
                val i = enc.dequeueOutputBuffer(info, 10_000)
                if (i == MediaCodec.INFO_TRY_AGAIN_LATER) { if (!end) return else continue }
                if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { vt = vm.addTrack(enc.outputFormat); vm.start(); started = true; continue }
                if (i < 0) continue
                val b = enc.getOutputBuffer(i)!!
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                if (info.size > 0 && started) { b.position(info.offset); b.limit(info.offset + info.size); vm.writeSampleData(vt, b, info) }
                enc.releaseOutputBuffer(i, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
            }
        }
        val argb = IntArray(W * H); val yA = ByteArray(W * H); val uA = ByteArray(W * H / 4); val vA = ByteArray(W * H / 4)
        val frameUs = 1_000_000L / FPS
        val frames = (total / frameUs).toInt()
        var lastKey = ""
        for (f in 0 until frames) {
            val t = f * frameUs
            // 같은 그림이면 다시 그리지 않음: 판화 확대는 3장마다, 글 · 흐림 · 진행이 바뀔 때만
            val key = run {
                val i = starts.indexOfLast { it <= t }
                val fading = (t < TITLE_US) || t >= voiceEnd || (i in scenes.indices && (t < starts[i] + FADE_US || t > starts[i + 1] - FADE_US))
                if (fading) "f$f" else "z${f / 3}"
            }
            if (key != lastKey) {
                render(t)
                frame.getPixels(argb, 0, W, 0, 0, W, H)
                toYuv(argb, W, H, yA, uA, vA)
                lastKey = key
            }
            var ii: Int
            do { ii = enc.dequeueInputBuffer(10_000); if (ii < 0) drain(false) } while (ii < 0)
            put(enc.getInputImage(ii)!!, yA, uA, vA, W, H)
            enc.queueInputBuffer(ii, 0, W * H * 3 / 2, t, 0)
            drain(false)
            if (f % 12 == 0) progress(f.toFloat() / frames)
        }
        var ii: Int
        do { ii = enc.dequeueInputBuffer(10_000); if (ii < 0) drain(false) } while (ii < 0)
        enc.queueInputBuffer(ii, 0, 0, frames * frameUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM); drain(true)
        enc.stop(); enc.release(); vm.stop(); vm.release()
        frame.recycle()

        // 영상 + 내 목소리 (처음 3초 뒤부터)
        val mux = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val vex = MediaExtractor(); vex.setDataSource(tmp.path); vex.selectTrack(0)
        val vTrack = mux.addTrack(vex.getTrackFormat(0))
        Voice.appendAudio(mux, scenes.map { it.file }, startMux = true, offsetUs = TITLE_US)
        val buf = ByteBuffer.allocate(2 * 1024 * 1024)
        while (true) {
            val n = vex.readSampleData(buf, 0); if (n < 0) break
            info.set(0, n, vex.sampleTime, if (vex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            mux.writeSampleData(vTrack, buf, info); vex.advance()
        }
        vex.release(); mux.stop(); mux.release(); tmp.delete(); progress(1f); true
    }.getOrElse { CrashLog.note(ctx, "film failed · ${it.message}"); out.delete(); false }

    private fun supports(w: Int, h: Int): Boolean = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { ci ->
            ci.isEncoder && ci.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) } &&
                ci.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities?.isSizeSupported(w, h) == true
        }
    }.getOrDefault(false)

    /** ARGB → Y · U · V (BT.601, 4:2:0). */
    private fun toYuv(argb: IntArray, w: Int, h: Int, y: ByteArray, u: ByteArray, v: ByteArray) {
        var i = 0
        for (r in 0 until h) {
            val even = r and 1 == 0
            for (c in 0 until w) {
                val p = argb[i]; val R = (p shr 16) and 0xFF; val G = (p shr 8) and 0xFF; val B = p and 0xFF
                y[i] = (((66 * R + 129 * G + 25 * B + 128) shr 8) + 16).toByte()
                if (even && c and 1 == 0) {
                    val ci = (r shr 1) * (w shr 1) + (c shr 1)
                    u[ci] = (((-38 * R - 74 * G + 112 * B + 128) shr 8) + 128).toByte()
                    v[ci] = (((112 * R - 94 * G - 18 * B + 128) shr 8) + 128).toByte()
                }
                i++
            }
        }
    }

    /** 미리 만든 Y · U · V 를 부호기의 평면 (줄 간격 · 화소 간격 그대로) 에. */
    private fun put(img: android.media.Image, y: ByteArray, u: ByteArray, v: ByteArray, w: Int, h: Int) {
        val py = img.planes[0]; val pu = img.planes[1]; val pv = img.planes[2]
        val yb = py.buffer
        if (py.pixelStride == 1) for (r in 0 until h) { yb.position(r * py.rowStride); yb.put(y, r * w, w) }
        else for (r in 0 until h) for (c in 0 until w) yb.put(r * py.rowStride + c * py.pixelStride, y[r * w + c])
        val cw = w / 2; val chh = h / 2
        for ((pl, src) in listOf(pu to u, pv to v)) {
            val b = pl.buffer
            if (pl.pixelStride == 1) for (r in 0 until chh) { b.position(r * pl.rowStride); b.put(src, r * cw, cw) }
            else for (r in 0 until chh) { val base = r * pl.rowStride; for (c in 0 until cw) b.put(base + c * pl.pixelStride, src[r * cw + c]) }
        }
    }
}
