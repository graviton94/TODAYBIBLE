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
class GuideVoice(ctx: Context, private val korean: Boolean, private val preferred: String, private val narrator: String = Narration.DEVICE) {
    private val app = ctx.applicationContext
    private var player: android.media.MediaPlayer? = null
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private var ticker: Runnable? = null

    /** 미리 만든 낭독 음원이 있으면 그 파일 (개역한글만). */
    fun narrated(book: Int, chapter: Int, verse: Int): java.io.File? =
        if (!korean || narrator == Narration.DEVICE) null else Narration.file(app, narrator, book, chapter, verse).takeIf { it.exists() }
    private var tts: TextToSpeech? = null
    @Volatile var ready = false; private set
    @Volatile var voices: List<Voice> = emptyList(); private set
    /** 준비를 기다리는 쪽들 (성공이든 실패든 준비가 끝나면 모두 불러요). */
    private val waiters = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    @Volatile private var settled = false
    @Volatile private var curId: String? = null
    @Volatile private var curRange: ((Int, Int) -> Unit)? = null
    @Volatile private var curDone: (() -> Unit)? = null
    private val pending = java.util.concurrent.ConcurrentHashMap<String, (Boolean) -> Unit>()

    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            val t = tts
            if (t == null || status != TextToSpeech.SUCCESS) { settle(); return@TextToSpeech }
            val lang = if (korean) "ko" else "en"
            voices = runCatching { t.voices.orEmpty().filter { it.locale.language == lang && !it.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) } }
                .getOrDefault(emptyList())
                .sortedWith(compareByDescending<Voice> { it.quality }.thenBy { it.isNetworkConnectionRequired }.thenBy { it.name })
            val pick = voices.firstOrNull { it.name == preferred } ?: voices.firstOrNull()
            if (pick != null) t.voice = pick else t.language = if (korean) Locale.KOREAN else Locale.ENGLISH
            t.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            t.setPitch(PITCH)
            t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(u: String?) {}
                override fun onRangeStart(u: String?, start: Int, end: Int, frame: Int) { if (u == curId) curRange?.invoke(start, end) }
                override fun onDone(u: String?) { if (u == curId) curDone?.invoke(); u?.let { pending.remove(it) }?.invoke(true) }
                @Deprecated("") override fun onError(u: String?) { if (u == curId) curDone?.invoke(); u?.let { pending.remove(it) }?.invoke(false) }
                override fun onStop(u: String?, interrupted: Boolean) { u?.let { pending.remove(it) }?.invoke(false) }
            })
            ready = pick != null || t.isLanguageAvailable(if (korean) Locale.KOREAN else Locale.ENGLISH) >= TextToSpeech.LANG_AVAILABLE
            settle()
        }
    }

    private fun settle() { settled = true; val w = waiters.toList(); waiters.clear(); w.forEach { it() } }
    /** 준비가 끝나면 (쓸 수 있든 없든) f. 쓸 수 있는지는 ready 로. */
    fun whenReady(f: () -> Unit) { if (settled) f() else { waiters.add(f); if (settled && waiters.remove(f)) f() } }

    /** 지금 쓰는 목소리 이름 (설정에 남김). */
    val current: String? get() = runCatching { tts?.voice?.name }.getOrNull()

    fun choose(name: String) { voices.firstOrNull { it.name == name }?.let { v -> tts?.voice = v } }

    /**
     * 한 절 읽기. rate 1 = 보통. onRange(시작, 끝) 은 지금 읽는 글자 자리, onDone 은 다 읽었을 때 (멈추면 부르지 않음).
     */
    fun speak(text: String, rate: Float, onRange: (Int, Int) -> Unit, onDone: () -> Unit, file: java.io.File? = null) {
        stopPlayer()
        // 미리 만든 음원: 재생 위치만큼 글자를 밝혀요 (빠르기는 재생 속도로, 음높이는 그대로)
        if (file != null) {
            val mp = runCatching {
                android.media.MediaPlayer().apply {
                    setDataSource(file.path); prepare()
                    if (rate != 1f) playbackParams = playbackParams.setSpeed(rate)
                }
            }.getOrNull()
            if (mp != null) {
                player = mp
                val dur = mp.duration.coerceAtLeast(1)
                val tick = object : Runnable {
                    override fun run() {
                        val p = player ?: return
                        val at = (runCatching { p.currentPosition }.getOrDefault(0).toFloat() / dur * text.length).toInt().coerceIn(0, text.length)
                        onRange(0, at); ui.postDelayed(this, 80)
                    }
                }
                ticker = tick
                mp.setOnCompletionListener { stopPlayer(); onDone() }
                mp.start(); ui.post(tick)
                return
            }
        }
        val t = tts?.takeIf { ready } ?: return onDone()
        t.setSpeechRate(rate * BASE_RATE)
        val id = "v${System.nanoTime()}"
        curId = id; curRange = onRange; curDone = onDone
        // 엔진이 받지 못하면 기다리지 않고 바로 끝난 것으로
        if (t.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), id) != TextToSpeech.SUCCESS) { curId = null; onDone() }
    }

    /** 같은 목소리 · 빠르기로 WAV 파일 만들기 (교독 녹음용). 다 되면 onDone(성공). */
    fun synthesize(text: String, rate: Float, out: java.io.File, onDone: (Boolean) -> Unit) {
        val t = tts?.takeIf { ready } ?: return onDone(false)
        t.setSpeechRate(rate * BASE_RATE)
        val id = "f${System.nanoTime()}"
        pending[id] = onDone
        out.parentFile?.mkdirs()
        if (t.synthesizeToFile(text, Bundle(), out, id) != TextToSpeech.SUCCESS) { pending.remove(id); onDone(false) }
    }

    private fun stopPlayer() { ticker?.let { ui.removeCallbacks(it) }; ticker = null; runCatching { player?.release() }; player = null }
    /** 멈춤. 교독 녹음용 파일을 만드는 중이면 그것은 마저 끝내게 둬요. */
    fun stop() { curId = null; stopPlayer(); if (pending.isEmpty()) runCatching { tts?.stop() } }
    fun release() { stopPlayer(); runCatching { tts?.stop(); tts?.shutdown() }; tts = null }

    companion object {
        /** 차분한 결: 음높이를 조금 낮추고, 보통 빠르기는 엔진 기본보다 아주 조금 느리게. */
        const val PITCH = 0.9f
        const val BASE_RATE = 0.95f
    }
}
