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

    /** 이어 붙일 때 한 녹음이 차지하는 길이 (µs): 마지막 소리 조각 시각 + AAC 한 조각. appendAudio 와 같은 셈. */
    fun spanUs(f: File): Long = runCatching {
        val ex = MediaExtractor(); ex.setDataSource(f.path)
        val t = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("audio/") }
        ex.selectTrack(t); var last = 0L
        while (ex.sampleTime >= 0) { last = ex.sampleTime; if (!ex.advance()) break }
        ex.release(); last + 1024L * 1_000_000L / RATE
    }.getOrDefault(0L)

    fun durationMs(f: File): Long = runCatching {
        MediaMetadataRetriever().run { setDataSource(f.path); val d = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L; release(); d }
    }.getOrDefault(0L)

    /** 녹음 · 노트 사진이 차지하는 크기 (바이트). */
    fun usage(ctx: Context): Pair<Long, Long> {
        fun size(d: File): Long = d.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return size(File(ctx.filesDir, "voice")) to size(File(ctx.filesDir, "ink"))
    }

    /**
     * 녹음 한 번 (한 절). start() 하면 별도 스레드에서 읽고 부호화. stop() 하면 파일을 닫음.
     * recognizerPipe(): 음성 인식에 넘길 16kHz PCM 파이프 (새로 부를 때마다 새 파이프로 바꿈).
     */
    class Session(private val out: File) {
        @Volatile private var running = false
        @Volatile private var pipeOut: ParcelFileDescriptor.AutoCloseOutputStream? = null
        private var thread: Thread? = null
        // 음성 인식으로 가는 소리는 따로 흘려보냄: 인식이 잠시 안 읽어도 녹음이 멈추지 않게 (넘치면 버림)
        private val toRecognizer = java.util.concurrent.ArrayBlockingQueue<ByteArray>(64)
        private val feeder = Thread {
            while (true) {
                val chunk = runCatching { toRecognizer.take() }.getOrNull() ?: break
                if (chunk.isEmpty()) break
                val p = pipeOut ?: continue
                if (runCatching { p.write(chunk) }.isFailure) pipeOut = null
            }
        }.apply { isDaemon = true; name = "voice-feed" }

        fun recognizerPipe(): ParcelFileDescriptor {
            val (read, write) = ParcelFileDescriptor.createPipe()
            runCatching { pipeOut?.close() }
            pipeOut = ParcelFileDescriptor.AutoCloseOutputStream(write)
            if (!feeder.isAlive) runCatching { feeder.start() }
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
                val leveler = Sound.Leveler()
                fun drain(end: Boolean) {
                    var waits = 0
                    while (true) {
                        val i = enc.dequeueOutputBuffer(info, if (end) 10_000 else 0)
                        // 끝맺을 때도 1초 넘게 기다리지 않음 (부호기가 멈춰도 녹음 줄이 묶이지 않게)
                        if (i == MediaCodec.INFO_TRY_AGAIN_LATER) { if (!end || ++waits > 100) return else continue }
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
                    var fails = 0
                    while (running) {
                        val n = rec.read(pcm, 0, pcm.size)
                        // 마이크를 못 잡으면 빈 손으로 돌지 않게 잠깐 쉬고, 계속 안 되면 그만
                        if (n <= 0) { if (++fails > 200) break; Thread.sleep(10); continue }
                        fails = 0
                        // 소리 크기 (말하는 중인지 보려고): 표본 몇 개만 훑어요
                        run { var sum = 0.0; var m = 0; var j = 0; while (j + 1 < n) { val x = ((pcm[j + 1].toInt() shl 8) or (pcm[j].toInt() and 0xff)).toShort() / 32768.0; sum += x * x; m++; j += 16 }; if (m > 0) level = kotlin.math.sqrt(sum / m).toFloat() }
                        // 음성 인식에 16kHz 로 (두 표본 평균)
                        if (pipeOut != null) {
                            val sb = ByteBuffer.wrap(pcm, 0, n).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer(); val ob = ByteBuffer.wrap(half).order(ByteOrder.LITTLE_ENDIAN)
                            var m = 0
                            while (sb.remaining() >= 2) { val a = sb.get().toInt(); val b = sb.get().toInt(); ob.putShort(((a + b) / 2).toShort()); m += 2 }
                            toRecognizer.offer(half.copyOf(m))
                        }
                        // 저장할 녹음만 말소리 크기로 (음성 인식에는 원래 소리)
                        leveler.apply(pcm, n)
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
                toRecognizer.clear(); toRecognizer.offer(ByteArray(0)); runCatching { pipeOut?.close() }; pipeOut = null
                val ok = keep && started && samples > RATE / 2
                if (ok) tmp.renameTo(out) else { tmp.delete(); if (!keep) out.delete() }
                onSaved?.invoke(ok)
            }.apply { name = "voice"; start() }
        }

        @Volatile private var keep = true
        /** 지금 소리 크기 (0..1). */
        @Volatile var level = 0f
        /** 녹음 줄이 파일을 다 쓰고 나면 (남겼는지). 화면 줄이 아니라 녹음 줄에서 불려요. */
        @Volatile var onSaved: ((Boolean) -> Unit)? = null
        /** 멈춤: 기다리지 않아요 (마무리는 녹음 줄이 해요). */
        fun stop() { running = false; thread = null }
        /** 다시 읽기: 녹음을 버림. */
        fun discard() { keep = false; stop() }
    }

    /** 이 장의 절 녹음을 이어 붙여 소리 파일 하나로 (다시 부호화하지 않음). */
    /**
     * 가이드 목소리가 읽은 절 (교독): 읽기 엔진이 만든 WAV 를 내 녹음과 같은 결 (32kHz 모노 AAC) 로 바꿔 그 절 자리에 둬요.
     * 그래야 장 전체를 이어 붙였을 때 가이드 · 내 목소리가 차례대로 이어져요.
     */
    fun encodeWav(wav: File, out: File): Boolean = runCatching {
        val b = ByteBuffer.wrap(wav.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        var rate = 0; var ch = 1; var bits = 16; var data: ShortArray? = null
        b.position(12)
        while (b.remaining() >= 8) {
            val id = ByteArray(4).also { b.get(it) }.toString(Charsets.US_ASCII); val len = b.int
            val at = b.position()
            when (id) {
                "fmt " -> { b.short; ch = b.short.toInt(); rate = b.int; b.int; b.short; bits = b.short.toInt() }
                "data" -> { val n = minOf(len, b.remaining()) / 2; data = ShortArray(n).also { b.asShortBuffer().get(it) } }
            }
            b.position(minOf(b.limit(), at + len + (len and 1)))
        }
        val src = data ?: return false
        if (bits != 16 || rate <= 0) return false
        // 모노로 · 32kHz 로 (선형 보간)
        val mono = if (ch == 1) src else ShortArray(src.size / ch) { i -> (0 until ch).sumOf { src[i * ch + it].toInt() }.div(ch).toShort() }
        val n = (mono.size.toLong() * RATE / rate).toInt()
        val pcm = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) {
            val x = i.toDouble() * rate / RATE; val k = x.toInt().coerceAtMost(mono.size - 1); val f = x - k
            val a = mono[k].toInt(); val c = mono[minOf(k + 1, mono.size - 1)].toInt()
            pcm.putShort((a + (c - a) * f).toInt().toShort())
        }
        encodePcm(pcm.array(), out)
    }.getOrDefault(false)

    /**
     * 낭독 음원 (조용하게 만든 -25 LUFS) 을 내 녹음 자리에 둘 때: 풀어서 낭독 재생과 같은 만큼 키우고 다시 묶어요.
     * 그래야 녹음을 이어 들을 때 인도 목소리와 내 목소리 크기가 비슷해요. 실패하면 그대로 복사.
     */
    fun copyLifted(src: File, out: File): Boolean = runCatching {
        val ex = android.media.MediaExtractor(); ex.setDataSource(src.path)
        val ti = (0 until ex.trackCount).first { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
        ex.selectTrack(ti); val inFmt = ex.getTrackFormat(ti)
        val rate = inFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); val ch = inFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val dec = MediaCodec.createDecoderByType(inFmt.getString(MediaFormat.KEY_MIME)!!)
        dec.configure(inFmt, null, null, 0); dec.start()
        val o = java.io.ByteArrayOutputStream(); val info = MediaCodec.BufferInfo(); var inDone = false
        while (true) {
            if (!inDone) { val ii = dec.dequeueInputBuffer(10_000); if (ii >= 0) { val n = ex.readSampleData(dec.getInputBuffer(ii)!!, 0); if (n < 0) { dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true } else { dec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0); ex.advance() } } }
            val i = dec.dequeueOutputBuffer(info, 10_000)
            if (i >= 0) { val bb = dec.getOutputBuffer(i)!!; val arr = ByteArray(info.size); bb.position(info.offset); bb.get(arr); o.write(arr); dec.releaseOutputBuffer(i, false); if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break }
        }
        runCatching { dec.stop() }; dec.release(); ex.release()
        val sh = ByteBuffer.wrap(o.toByteArray()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val all = ShortArray(sh.remaining()).also { sh.get(it) }
        val mono = if (ch == 1) all else ShortArray(all.size / ch) { i -> (0 until ch).sumOf { all[i * ch + it].toInt() }.div(ch).toShort() }
        val g = Math.pow(10.0, io.github.graviton94.todaybible.design.Tokens.Sound.narrationGainDb / 20.0).toFloat()
        val n = (mono.size.toLong() * RATE / rate).toInt()
        val pcm = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until n) {
            val k = (i.toLong() * rate / RATE).toInt().coerceAtMost(mono.size - 1)
            val x = mono[k] / 32768f * g
            val y = if (kotlin.math.abs(x) <= 0.7f) x else kotlin.math.sign(x) * (0.7f + 0.3f * kotlin.math.tanh((kotlin.math.abs(x) - 0.7f) / 0.3f))
            pcm.putShort((y * 32767f).toInt().coerceIn(-32768, 32767).toShort())
        }
        encodePcm(pcm.array(), out)
    }.getOrElse { runCatching { src.copyTo(out, overwrite = true); true }.getOrDefault(false) }

    /** 32kHz 모노 16비트 PCM → 내 녹음과 같은 결의 AAC (m4a). */
    internal fun encodePcm(bytes: ByteArray, out: File): Boolean = runCatching {
        out.parentFile?.mkdirs()
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BITRATE); setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        enc.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE); enc.start()
        val tmp = File(out.path + ".part")
        val mux = MediaMuxer(tmp.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var track = -1; var started = false; val info = MediaCodec.BufferInfo()
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
        var off = 0
        while (off < bytes.size) {
            val ii = enc.dequeueInputBuffer(10_000); if (ii < 0) { drain(false); continue }
            val ib = enc.getInputBuffer(ii)!!; ib.clear()
            val len = minOf(ib.remaining(), bytes.size - off); ib.put(bytes, off, len)
            enc.queueInputBuffer(ii, 0, len, (off / 2).toLong() * 1_000_000L / RATE, 0); off += len
            drain(false)
        }
        val ii = enc.dequeueInputBuffer(10_000)
        if (ii >= 0) enc.queueInputBuffer(ii, 0, 0, (bytes.size / 2).toLong() * 1_000_000L / RATE, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        drain(true)
        runCatching { enc.stop() }; enc.release(); runCatching { if (started) mux.stop() }; runCatching { mux.release() }
        if (started) tmp.renameTo(out) else { tmp.delete(); false }
    }.getOrDefault(false)

    fun exportAudio(parts: List<File>, out: File): Boolean = runCatching {
        out.parentFile?.mkdirs()
        val mux = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        appendAudio(mux, parts, startMux = true); mux.stop(); mux.release(); true
    }.getOrElse { out.delete(); false }

    /** 절 녹음들을 한 트랙으로 이어 씀. 돌려주는 값: 전체 길이(µs). */
    internal fun appendAudio(mux: MediaMuxer, parts: List<File>, startMux: Boolean, offsetUs: Long = 0L): Long {
        var track = -1; var base = offsetUs
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


}
