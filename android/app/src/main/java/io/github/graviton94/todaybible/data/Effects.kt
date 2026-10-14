package io.github.graviton94.todaybible.data

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import io.github.graviton94.todaybible.R

/**
 * 효과음 (1.5): 보상 순간에만, 한 번에 하나. 소리는 직접 합성 (scripts/mockups/mock_sounds.py --app → res/raw/fx_*).
 * 한 절 마침 (작은 종) · 장 마침 (COMPLETUM 인장) · 판화가 걸림 (종 두 번) · 메달 (세 음) · 권 다 씀 (낮은 교회 종).
 * 설정 › 손글씨 · 소리의 ‘효과음’ 으로 끄고, 폰이 무음 · 진동이면 내지 않아요.
 */
object Effects {
    enum class Fx(val res: Int) { VERSE(R.raw.fx_verse), SEAL(R.raw.fx_seal), PLATE(R.raw.fx_plate), MEDAL(R.raw.fx_medal), BOOK(R.raw.fx_book) }

    private var pool: SoundPool? = null
    private val ids = HashMap<Fx, Int>()
    private val ready = HashSet<Int>()
    private val main = Handler(Looper.getMainLooper())

    /** 앱을 열 때 미리 읽어 둠 (처음 울릴 때 늦지 않게). */
    fun warm(ctx: Context) {
        if (pool != null) return
        val p = SoundPool.Builder().setMaxStreams(2).setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
        p.setOnLoadCompleteListener { _, id, status -> if (status == 0) main.post { ready += id } }
        pool = p
        Fx.entries.forEach { fx -> runCatching { ids[fx] = p.load(ctx.applicationContext, fx.res, 1) } }
    }

    fun play(ctx: Context, fx: Fx, delayMs: Long = 0) {
        if (!Store(ctx).effects) return
        val am = ctx.getSystemService(AudioManager::class.java)
        if (am != null && am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        warm(ctx)
        val run = Runnable { val id = ids[fx] ?: return@Runnable; if (id in ready) pool?.play(id, 1f, 1f, 1, 0, 1f) }
        if (delayMs > 0) main.postDelayed(run, delayMs) else main.post(run)
    }
}
