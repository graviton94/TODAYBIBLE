package io.github.graviton94.todaybible.data

import android.content.Context
import android.media.MediaPlayer
import io.github.graviton94.todaybible.design.Tokens

/** 필사 집중 모드의 곁소리 (빗소리 · 촛불 · 시냇물): assets/sounds/<id>.ogg 를 조용히 되풀이. 없는 소리는 고르기에 나오지 않아요. */
object FocusSound {
    private var player: MediaPlayer? = null
    private var now: String? = null

    fun available(ctx: Context): List<String> {
        val have = runCatching { ctx.assets.list("sounds")?.toSet() }.getOrNull().orEmpty()
        return listOf("rain", "candle", "stream").filter { "$it.ogg" in have }
    }

    /** id = null 이면 멈춰요. 같은 소리면 그대로 이어가요. */
    fun play(ctx: Context, id: String?) {
        if (id == now) return
        stop()
        if (id == null) return
        player = runCatching {
            ctx.assets.openFd("sounds/$id.ogg").use { fd ->
                MediaPlayer().apply {
                    setAudioAttributes(Sound.effect)
                    setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
                    isLooping = true
                    setVolume(Tokens.Sound.focusLevel, Tokens.Sound.focusLevel)
                    prepare(); start()
                }
            }
        }.getOrNull()
        now = id.takeIf { player != null }
    }

    private fun stop() { runCatching { player?.stop(); player?.release() }; player = null; now = null }
}
