package io.github.graviton94.todaybible.data

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * 낭독 음원 (개역한글, Supertonic 3 로 미리 만든 목소리): 장마다 내려받아 폰에 두고 써요 (Cloudflare R2, 안 되면 GitHub 릴리스).
 * 절마다 파일 하나 (장_절.m4a, 32kHz 모노 AAC) — 내 목소리 녹음과 같은 결이라 교독 녹음에 그대로 이어 붙어요.
 * 목소리: m5 (진중한 남성) · f5 (차분한 여성). device = 폰의 읽기 목소리.
 */
object Narration {
    const val MALE = "m5"; const val FEMALE = "f5"; const val DEVICE = "device"
    /** 듣기에서 절과 절 사이 쉼 (ms). */
    const val GAP_MS = 350
    private const val RELEASE = "https://github.com/graviton94/TODAYBIBLE/releases/download/narration-v1"
    /** 받을 곳 차례: Cloudflare R2 (있으면) → GitHub 릴리스. */
    private val bases = listOfNotNull(io.github.graviton94.todaybible.BuildConfig.NARRATION_URL.takeIf { it.isNotBlank() }?.let { "$it/narration" }, RELEASE)

    fun dir(ctx: Context, voice: String, book: Int) = File(ctx.filesDir, "narration/$voice/${book + 1}")
    fun file(ctx: Context, voice: String, book: Int, chapter: Int, verse: Int) = File(dir(ctx, voice, book), "${chapter}_$verse.m4a")
    private fun mark(ctx: Context, voice: String, book: Int, chapter: Int) = File(dir(ctx, voice, book), ".c$chapter")
    /** 이 장의 음원이 폰에 있는지. 쓸 때마다 표시를 새로 해 두어 오래 안 쓴 장부터 지워요. */
    fun has(ctx: Context, voice: String, book: Int, chapter: Int) = mark(ctx, voice, book, chapter).let { m -> m.exists().also { if (it) m.setLastModified(System.currentTimeMillis()) } }
    fun usage(ctx: Context): Long = File(ctx.filesDir, "narration").walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** 한 장 받기 (뒤에서 부름, 수백 KB). 받은 뒤 전체가 CAP 을 넘으면 오래 안 쓴 장부터 지워요. */
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()
    fun fetch(ctx: Context, voice: String, book: Int, chapter: Int): Boolean =
        // 같은 장을 앱과 듣기가 동시에 받지 않게
        synchronized(locks.getOrPut("$voice/$book/$chapter") { Any() }) { fetchOnce(ctx, voice, book, chapter) }

    private fun fetchOnce(ctx: Context, voice: String, book: Int, chapter: Int): Boolean = runCatching {
        if (has(ctx, voice, book, chapter)) return true
        val d = dir(ctx, voice, book); d.mkdirs()
        val name = "${voice}_%02d_c%03d.zip".format(book + 1, chapter)
        val conn = bases.firstNotNullOfOrNull { open(URL("$it/$name")) } ?: return false
        var n = 0
        ZipInputStream(conn.inputStream.buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                n++
                if (e.isDirectory || e.name.contains("..") || e.name.contains('/')) continue
                val tmp = File(d, e.name + ".part"); tmp.outputStream().use { z.copyTo(it) }; tmp.renameTo(File(d, e.name))
            }
        }
        conn.disconnect()
        if (n == 0) return false
        mark(ctx, voice, book, chapter).writeText("ok")
        trim(ctx, keep = mark(ctx, voice, book, chapter))
        true
    }.getOrDefault(false)

    private const val CAP = 50L * 1024 * 1024
    private fun trim(ctx: Context, keep: File) {
        var total = usage(ctx); if (total <= CAP) return
        val marks = File(ctx.filesDir, "narration").walkTopDown().filter { it.isFile && it.name.startsWith(".c") && it != keep }.sortedBy { it.lastModified() }
        for (m in marks) {
            if (total <= CAP) break
            val ch = m.name.removePrefix(".c")
            m.parentFile?.listFiles { f -> f.name.startsWith("${ch}_") }?.forEach { total -= it.length(); it.delete() }
            m.delete()
        }
    }

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
