package io.github.graviton94.todaybible.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 내 목소리 낭독 (평생권): 절마다 짧은 녹음을 기기 안에만 저장.
 * 32kHz 모노 · AAC-LC 48kbps (말소리에 넉넉한 음질, 1분에 약 0.36MB).
 * 음성 인식에는 같은 소리를 16kHz 로 줄여 파이프로 넘김 (안드로이드 13+, 마이크를 두 번 열지 않게).
 */
object Voice {
    const val RATE = 32000
    private const val BITRATE = 48000
    const val RECOGNIZER_RATE = 16000

    fun dir(ctx: Context, trId: String, book: Int, chapter: Int) = File(ctx.filesDir, "voice/${trId}_${book + 1}_$chapter")
    fun file(ctx: Context, trId: String, book: Int, chapter: Int, verse: Int) = File(dir(ctx, trId, book, chapter), "$verse.m4a")

    /** 이 장에 녹음된 절 (절 번호 순). */
    fun verses(ctx: Context, trId: String, book: Int, chapter: Int): List<Pair<Int, File>> =
        dir(ctx, trId, book, chapter).listFiles { f -> f.extension == "m4a" && f.length() > 0 }.orEmpty()
            .mapNotNull { f -> f.nameWithoutExtension.toIntOrNull()?.let { it to f } }.sortedBy { it.first }

    fun durationMs(f: File): Long = runCatching {
        MediaMetadataRetriever().run { setDataSource(f.path); val d = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L; release(); d }
    }.getOrDefault(0L)

    /** 녹음 · 노트 사진이 차지하는 크기 (바이트). */
    fun usage(ctx: Context): Pair<Long, Long> {
        fun size(d: File): Long = d.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return size(File(ctx.filesDir, "voice")) to size(File(ctx.filesDir, "photos"))
    }

    /**
     * 녹음 한 번 (한 절). start() 하면 별도 스레드에서 읽고 부호화. stop() 하면 파일을 닫음.
     * recognizerPipe(): 음성 인식에 넘길 16kHz PCM 파이프 (새로 부를 때마다 새 파이프로 바꿈).
     */
    class Session(private val out: File) {
        @Volatile private var running = false
        @Volatile private var pipeOut: ParcelFileDescriptor.AutoCloseOutputStream? = null
        private var thread: Thread? = null

        fun recognizerPipe(): ParcelFileDescriptor {
            val (read, write) = ParcelFileDescriptor.createPipe()
            runCatching { pipeOut?.close() }
            pipeOut = ParcelFileDescriptor.AutoCloseOutputStream(write)
            return read
        }

        @SuppressLint("MissingPermission")
        fun start() {
            if (running) return
            running = true
            out.parentFile?.mkdirs()
            thread = Thread {
                val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, 8192))
                val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 1).apply {
                    setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                    setInteger(MediaFormat.KEY_BIT_RATE, BITRATE)
                    setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
                }
                val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); enc.start()
                val tmp = File(out.path + ".part")
                val mux = MediaMuxer(tmp.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
                var track = -1; var started = false
                val info = MediaCodec.BufferInfo()
                val pcm = ByteArray(4096); var samples = 0L
                fun drain(end: Boolean) {
                    while (true) {
                        val i = enc.dequeueOutputBuffer(info, if (end) 10_000 else 0)
                        if (i == MediaCodec.INFO_TRY_AGAIN_LATER) { if (!end) return else continue }
                        if (i == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) { track = mux.addTrack(enc.outputFormat); mux.start(); started = true; continue }
                        if (i < 0) continue
                        val buf = enc.getOutputBuffer(i)!!
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                        if (info.size > 0 && started) { buf.position(info.offset); buf.limit(info.offset + info.size); mux.writeSampleData(track, buf, info) }
                        enc.releaseOutputBuffer(i, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
                runCatching {
                    rec.startRecording()
                    val half = ByteArray(pcm.size / 2)
                    while (running) {
                        val n = rec.read(pcm, 0, pcm.size); if (n <= 0) continue
                        // 음성 인식에 16kHz 로 (두 표본 평균)
                        pipeOut?.let { p ->
                            val sb = ByteBuffer.wrap(pcm, 0, n).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer(); val ob = ByteBuffer.wrap(half).order(ByteOrder.LITTLE_ENDIAN)
                            var m = 0
                            while (sb.remaining() >= 2) { val a = sb.get().toInt(); val b = sb.get().toInt(); ob.putShort(((a + b) / 2).toShort()); m += 2 }
                            if (runCatching { p.write(half, 0, m) }.isFailure) pipeOut = null
                        }
                        var off = 0
                        while (off < n) {
                            val ii = enc.dequeueInputBuffer(10_000); if (ii < 0) { drain(false); continue }
                            val ib = enc.getInputBuffer(ii)!!; ib.clear()
                            val len = minOf(ib.remaining(), n - off); ib.put(pcm, off, len)
                            enc.queueInputBuffer(ii, 0, len, samples * 1_000_000L / RATE, 0)
                            samples += len / 2; off += len
                        }
                        drain(false)
                    }
                    val ii = enc.dequeueInputBuffer(10_000)
                    if (ii >= 0) enc.queueInputBuffer(ii, 0, 0, samples * 1_000_000L / RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    drain(true)
                }
                runCatching { rec.stop() }; rec.release()
                runCatching { enc.stop() }; enc.release()
                runCatching { if (started) mux.stop() }; runCatching { mux.release() }
                runCatching { pipeOut?.close() }
                if (started && samples > RATE / 2) tmp.renameTo(out) else tmp.delete()
            }.apply { name = "voice"; start() }
        }

        fun stop() { running = false; thread?.join(3000); thread = null }
        /** 다시 읽기: 녹음을 버림. */
        fun discard() { stop(); out.delete() }
    }

    /** 이 장의 절 녹음을 이어 붙여 소리 파일 하나로 (다시 부호화하지 않음). */
    fun exportAudio(parts: List<File>, out: File): Boolean = runCatching {
        out.parentFile?.mkdirs()
        val mux = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        appendAudio(mux, parts, startMux = true); mux.stop(); mux.release(); true
    }.getOrElse { out.delete(); false }

    /** 절 녹음들을 한 트랙으로 이어 씀. 돌려주는 값: 전체 길이(µs). */
    private fun appendAudio(mux: MediaMuxer, parts: List<File>, startMux: Boolean): Long {
        var track = -1; var base = 0L
        val buf = ByteBuffer.allocate(256 * 1024); val info = MediaCodec.BufferInfo()
        for (f in parts) {
            val ex = MediaExtractor(); ex.setDataSource(f.path)
            val t = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("audio/") }
            ex.selectTrack(t)
            if (track < 0) { track = mux.addTrack(ex.getTrackFormat(t)); if (startMux) mux.start() }
            var last = 0L
            while (true) {
                val n = ex.readSampleData(buf, 0); if (n < 0) break
                info.set(0, n, base + ex.sampleTime, if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                mux.writeSampleData(track, buf, info); last = ex.sampleTime; ex.advance()
            }
            ex.release(); base += last + 1024L * 1_000_000L / RATE // 마지막 AAC 프레임 길이만큼
        }
        return base
    }

    /**
     * 영상으로: 절마다 그 절 카드 한 장이 소리 길이만큼 (초당 2장), 소리는 녹음 그대로.
     * H.264 720×896 · 정지 화면이라 작게 나옴. frames = (그림, 길이 µs).
     */
    fun exportVideo(frames: List<Pair<Bitmap, Long>>, parts: List<File>, out: File): Boolean = runCatching {
        val w = 720; val h = 896; val fps = 2
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 600_000); setInteger(MediaFormat.KEY_FRAME_RATE, fps); setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); enc.start()
        out.parentFile?.mkdirs()
        // 영상 먼저 임시 파일로, 그다음 소리와 합침
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
        var pts = 0L
        val frameUs = 1_000_000L / fps
        for ((bmp, dur) in frames) {
            val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
            val argb = IntArray(w * h); scaled.getPixels(argb, 0, w, 0, 0, w, h)
            val n = maxOf(1, (dur / frameUs).toInt())
            repeat(n) {
                var ii: Int
                do { ii = enc.dequeueInputBuffer(10_000); if (ii < 0) drain(false) } while (ii < 0)
                val img = enc.getInputImage(ii)!!
                fillYuv(img, argb, w, h)
                enc.queueInputBuffer(ii, 0, w * h * 3 / 2, pts, 0); pts += frameUs
                drain(false)
            }
            if (scaled != bmp) scaled.recycle()
        }
        var ii: Int
        do { ii = enc.dequeueInputBuffer(10_000); if (ii < 0) drain(false) } while (ii < 0)
        enc.queueInputBuffer(ii, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM); drain(true)
        enc.stop(); enc.release(); vm.stop(); vm.release()

        // 영상 + 소리 합치기
        val mux = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val vex = MediaExtractor(); vex.setDataSource(tmp.path); vex.selectTrack(0)
        val vTrack = mux.addTrack(vex.getTrackFormat(0))
        appendAudio(mux, parts, startMux = true)
        val buf = ByteBuffer.allocate(1024 * 1024)
        while (true) {
            val n = vex.readSampleData(buf, 0); if (n < 0) break
            info.set(0, n, vex.sampleTime, if (vex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            mux.writeSampleData(vTrack, buf, info); vex.advance()
        }
        vex.release(); mux.stop(); mux.release(); tmp.delete(); true
    }.getOrElse { out.delete(); false }

    /** ARGB → YUV420 (부호기가 준 평면 · 간격 그대로). */
    private fun fillYuv(img: android.media.Image, argb: IntArray, w: Int, h: Int) {
        val y = img.planes[0]; val u = img.planes[1]; val v = img.planes[2]
        val yb = y.buffer; val ub = u.buffer; val vb = v.buffer
        for (r in 0 until h) for (c in 0 until w) {
            val p = argb[r * w + c]; val R = Color.red(p); val G = Color.green(p); val B = Color.blue(p)
            yb.put(r * y.rowStride + c * y.pixelStride, (((66 * R + 129 * G + 25 * B + 128) shr 8) + 16).coerceIn(0, 255).toByte())
            if (r % 2 == 0 && c % 2 == 0) {
                val cr = r / 2; val cc = c / 2
                ub.put(cr * u.rowStride + cc * u.pixelStride, (((-38 * R - 74 * G + 112 * B + 128) shr 8) + 128).coerceIn(0, 255).toByte())
                vb.put(cr * v.rowStride + cc * v.pixelStride, (((112 * R - 94 * G - 18 * B + 128) shr 8) + 128).coerceIn(0, 255).toByte())
            }
        }
    }

}
