package io.github.graviton94.todaybible.data

import android.content.Context
import java.io.File

/**
 * 오류 기록 (V3): 앱이 갑자기 닫히면 그 까닭을 이 폰에만 짧게 적어 둬요 (분석 도구 · 인터넷 없음).
 * ‘의견 보내기’에서 함께 보낼지 고를 수 있고, 보내면 지워요. 기록에는 기기 · 판 · 오류 줄만 (쓴 글 · 이름 없음).
 */
object CrashLog {
    private const val MAX = 6_000
    private fun file(ctx: Context) = File(ctx.filesDir, "crash.txt")

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        if (prev is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(app, prev))
    }

    fun read(ctx: Context): String? = file(ctx).takeIf { it.exists() && it.length() > 0 }?.readText()
    fun clear(ctx: Context) { file(ctx).delete() }

    private class Handler(val app: Context, val prev: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            runCatching {
                val f = file(app)
                val entry = buildString {
                    append(java.time.LocalDateTime.now().withNano(0)).append(" · ").append(t.name).append('\n')
                    append(e.stackTraceToString().lines().take(24).joinToString("\n")).append("\n\n")
                }
                // 최근 것만 (앞쪽을 잘라 MAX 안으로)
                val all = (f.takeIf { it.exists() }?.readText().orEmpty() + entry).takeLast(MAX)
                f.writeText(all)
            }
            prev?.uncaughtException(t, e)
        }
    }
}
