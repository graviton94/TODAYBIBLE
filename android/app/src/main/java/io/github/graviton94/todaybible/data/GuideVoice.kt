package io.github.graviton94.todaybible.data

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * 가이드 목소리 (함께 읽기): 이 폰에 깔린 읽기 엔진 가운데 가장 자연스러운 목소리로 한 절을 먼저 읽어 줘요.
 * 고르는 순서: 사용자가 고른 목소리 → 음질이 높은 것 → 인터넷 없이 되는 것. 차분하게 들리도록 음높이를 조금 낮춰요.
 * 읽는 동안 지금 읽는 글자 자리를 알려 줘서 (onRange) 화면에서 따라 밝힐 수 있어요.
 */
class GuideVoice(ctx: Context, private val korean: Boolean, private val preferred: String) {
    private var tts: TextToSpeech? = null
    @Volatile var ready = false; private set
    @Volatile var voices: List<Voice> = emptyList(); private set
    private var onReady: (() -> Unit)? = null

    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            val t = tts ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val lang = if (korean) "ko" else "en"
            voices = runCatching { t.voices.orEmpty().filter { it.locale.language == lang && !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) } }
                .getOrDefault(emptyList())
                .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.isNetworkConnectionRequired }.thenBy { it.name })
            val pick = voices.firstOrNull { it.name == preferred } ?: voices.firstOrNull()
            if (pick != null) t.voice = pick else t.language = if (korean) Locale.KOREAN else Locale.ENGLISH
            t.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            t.setPitch(PITCH)
            ready = pick != null || t.isLanguageAvailable(if (korean) Locale.KOREAN else Locale.ENGLISH) >= TextToSpeech.LANG_AVAILABLE
            onReady?.invoke()
        }
    }

    fun whenReady(f: () -> Unit) { if (ready) f() else onReady = f }

    /** 지금 쓰는 목소리 이름 (설정에 남김). */
    val current: String? get() = runCatching { tts?.voice?.name }.getOrNull()

    fun choose(name: String) { voices.firstOrNull { it.name == name }?.let { v -> tts?.voice = v } }

    /**
     * 한 절 읽기. rate 1 = 보통. onRange(시작, 끝) 은 지금 읽는 글자 자리, onDone 은 다 읽었을 때 (멈추면 부르지 않음).
     */
    fun speak(text: String, rate: Float, onRange: (Int, Int) -> Unit, onDone: () -> Unit) {
        val t = tts ?: return
        t.setSpeechRate(rate * BASE_RATE)
        val id = "v${System.nanoTime()}"
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(u: String?) {}
            override fun onRangeStart(u: String?, start: Int, end: Int, frame: Int) { if (u == id) onRange(start, end) }
            override fun onDone(u: String?) { if (u == id) onDone() }
            @Deprecated("") override fun onError(u: String?) { if (u == id) onDone() }
            override fun onStop(u: String?, interrupted: Boolean) {}
        })
        t.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id)
    }

    fun stop() { runCatching { tts?.stop() } }
    fun release() { runCatching { tts?.stop(); tts?.shutdown() }; tts = null }

    companion object {
        /** 차분한 결: 음높이를 조금 낮추고, 보통 빠르기는 엔진 기본보다 아주 조금 느리게. */
        const val PITCH = 0.9f
        const val BASE_RATE = 0.95f
    }
}
