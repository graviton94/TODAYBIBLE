package io.github.graviton94.todaybible.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.random.Random

/**
 * 펜 소리: 종이에 긋는 사각사각 (녹음이 아니라 걸러 낸 잡음으로 그때그때 만듦).
 * 긋는 빠르기만큼 커지고, 손을 떼면 잦아들어요. 무음 · 진동 모드에서는 소리를 내지 않아요.
 */
class PenFeel(private val ctx: Context) {
    private val rate = 22050
    @Volatile private var target = 0f
    @Volatile private var running = false
    private var thread: Thread? = null

    /** 긋는 빠르기 (0..1). 0 이면 잦아듦. */
    fun speed(v: Float) {
        target = v.coerceIn(0f, 1f)
        if (target > 0f && !running && !released) start()
    }

    @Volatile private var released = false
    fun release() { released = true; running = false; thread = null }

    private fun start() {
        val am = ctx.getSystemService(AudioManager::class.java)
        if (am == null || am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        running = true
        thread = Thread {
            val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(maxOf(min, 2048)).setTransferMode(AudioTrack.MODE_STREAM).build()
            }.getOrNull() ?: run { running = false; return@Thread }
            track.play()
            val buf = ShortArray(256)
            var gain = 0f; var lp = 0f; var prev = 0f; var idle = 0
            while (running) {
                for (i in buf.indices) {
                    gain += (target * 0.22f - gain) * 0.004f
                    // 흰 잡음 → 고역 통과(긁는 결) → 살짝 저역 통과(종이에 먹힌 소리)
                    val n = Random.nextFloat() * 2f - 1f
                    val hp = n - prev; prev = n
                    lp += (hp - lp) * 0.45f
                    // 종이 섬유를 지나는 듯한 미세한 떨림
                    val grain = if (Random.nextInt(90) == 0) 1.8f else 1f
                    buf[i] = (lp * gain * grain * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                }
                track.write(buf, 0, buf.size)
                target *= 0.9f
                idle = if (gain < 0.002f) idle + 1 else 0
                if (idle > 200) break
            }
            runCatching { track.stop(); track.release() }
            running = false
        }.apply { isDaemon = true; name = "pen-feel"; start() }
    }
}
