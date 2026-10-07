package io.github.graviton94.todaybible.data

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import io.github.graviton94.todaybible.design.Tokens
import java.util.WeakHashMap

/**
 * 소리 균형: 앱의 모든 소리 (낭독 · 안내 목소리 · 내 녹음 · 펜 · 타자) 가 한 볼륨 (미디어) 을 따르고, 서로 크기가 맞게.
 * 낭독 음원은 -25 LUFS 로 조용하게 만들어져 있어 재생할 때 Tokens.Sound.narrationGainDb 만큼 키워요 (제한기가 있어 찢어지지 않음).
 */
object Sound {
    val speech: AudioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    val effect: AudioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    // 효과기는 플레이어가 살아 있는 동안 붙잡아 둬야 (GC 되면 꺼져요)
    private val lifts = WeakHashMap<MediaPlayer, LoudnessEnhancer>()

    /** 말소리 플레이어: 미디어 볼륨 · 말 유형. lift = 낭독처럼 키울 때. src 안에서 setDataSource. prepare 까지 해서 돌려줘요. */
    fun player(lift: Boolean = false, src: MediaPlayer.() -> Unit): MediaPlayer = MediaPlayer().apply {
        setAudioAttributes(speech); src(); prepare()
        if (lift) runCatching {
            lifts[this] = LoudnessEnhancer(audioSessionId).apply { setTargetGain((Tokens.Sound.narrationGainDb * 100).toInt()); enabled = true }
        }
    }

    /** 내 녹음: 마이크 소리를 말소리 크기로 (조용한 마이크도 낭독과 비슷하게). 블록마다 이득을 천천히 맞추고 넘치면 부드럽게 눌러요. */
    class Leveler {
        private var gain = 1f
        fun apply(pcm: ByteArray, n: Int) {
            var sum = 0.0; var m = 0; var j = 0
            while (j + 1 < n) { val x = ((pcm[j + 1].toInt() shl 8) or (pcm[j].toInt() and 0xff)).toShort() / 32768.0; sum += x * x; m++; j += 2 }
            if (m == 0) return
            val rms = kotlin.math.sqrt(sum / m).toFloat()
            // 말하는 중일 때만 이득을 옮겨요 (조용한 틈에 잡음을 키우지 않게)
            if (rms > 0.004f) {
                val want = (Tokens.Sound.voiceTargetRms / rms).coerceIn(1f, Tokens.Sound.voiceMaxGain)
                gain += (want - gain) * (if (want < gain) 0.5f else 0.08f)
            }
            if (gain <= 1.01f) return
            j = 0
            while (j + 1 < n) {
                val x = ((pcm[j + 1].toInt() shl 8) or (pcm[j].toInt() and 0xff)).toShort() / 32768f * gain
                // 부드러운 눌림 (0.7 위로는 천천히 1 에 다가가요)
                val y = if (kotlin.math.abs(x) <= 0.7f) x else kotlin.math.sign(x) * (0.7f + 0.3f * kotlin.math.tanh((kotlin.math.abs(x) - 0.7f) / 0.3f))
                val v = (y * 32767f).toInt().coerceIn(-32768, 32767)
                pcm[j] = (v and 0xff).toByte(); pcm[j + 1] = (v shr 8).toByte(); j += 2
            }
        }
    }
}
