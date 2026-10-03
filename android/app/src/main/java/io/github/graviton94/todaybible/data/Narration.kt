package io.github.graviton94.todaybible.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * 낭독 음원 (개역한글, Supertonic 3 로 미리 만든 목소리): 권마다 내려받아 폰에 두고 써요.
 * 절마다 파일 하나 (장_절.m4a, 32kHz 모노 AAC) — 내 목소리 녹음과 같은 결이라 교독 녹음에 그대로 이어 붙어요.
 * 목소리: m5 (진중한 남성) · f5 (차분한 여성). device = 폰의 읽기 목소리.
 */
object Narration {
    const val MALE = "m5"; const val FEMALE = "f5"; const val DEVICE = "device"
    private const val BASE = "https://github.com/graviton94/TODAYBIBLE/releases/download/narration-v1"

    fun dir(ctx: Context, voice: String, book: Int) = File(ctx.filesDir, "narration/$voice/${book + 1}")
    fun file(ctx: Context, voice: String, book: Int, chapter: Int, verse: Int) = File(dir(ctx, voice, book), "${chapter}_$verse.m4a")
    fun has(ctx: Context, voice: String, book: Int) = File(dir(ctx, voice, book), ".done").exists()
    fun usage(ctx: Context): Long = File(ctx.filesDir, "narration").walkTopDown().filter { it.isFile }.sumOf { it.length() }
    fun remove(ctx: Context, voice: String, book: Int) { dir(ctx, voice, book).deleteRecursively() }

    /**
     * 한 권 내려받기 (뒤에서 부름): 큰 권은 장 묶음 여러 개 (m5_19_1.zip, m5_19_2.zip …) — 없는 묶음이 나올 때까지 차례로.
     * progress(지금까지 받은 바이트). 하나도 못 받으면 false.
     */
    fun download(ctx: Context, voice: String, book: Int, progress: (Long) -> Unit): Boolean = runCatching {
        val d = dir(ctx, voice, book); d.deleteRecursively(); d.mkdirs()
        var got = 0L; var part = 1
        while (true) {
            val conn = open(URL("$BASE/${voice}_%02d_%d.zip".format(book + 1, part))) ?: break
            val tmp = File(d, ".part")
            conn.inputStream.use { input -> tmp.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) { val n = input.read(buf); if (n < 0) break; out.write(buf, 0, n); got += n; progress(got) }
            } }
            ZipInputStream(tmp.inputStream().buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory || e.name.contains("..") || e.name.contains('/')) continue
                    File(d, e.name).outputStream().use { z.copyTo(it) }
                }
            }
            tmp.delete(); part++
        }
        if (part == 1) { d.deleteRecursively(); return false }
        File(d, ".done").writeText("ok")
        true
    }.getOrDefault(false)

    /** 릴리스 파일 열기 (다른 주소로 넘겨주면 따라감). 없으면 null. */
    private fun open(start: URL): HttpURLConnection? {
        var url = start; var hops = 0
        while (true) {
            val conn = (url.openConnection() as HttpURLConnection).apply { instanceFollowRedirects = false; connectTimeout = 15_000; readTimeout = 30_000 }
            val code = conn.responseCode
            if (code in 300..399 && hops++ < 5) { url = URL(url, conn.getHeaderField("Location")); conn.disconnect(); continue }
            if (code != 200) { conn.disconnect(); return null }
            return conn
        }
    }
}
