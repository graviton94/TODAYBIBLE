package io.github.graviton94.todaybible.data

import android.content.Context
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 이달의 판화 원격 받기 (1.5 C8): 12월 세트부터는 앱을 고치지 않고 R2 (낭독과 같은 공개 주소) 에서 받아요.
 *  · `plates/sets.tsv` — plates.tsv 와 같은 칸 (id · 권 · 장 · 이름 · 영어 이름 · 절 · 화가 · 영어 화가 · 세트 해-달)
 *  · `plates/stories.tsv` — 역사 속의 성경 글 (history/stories.tsv 와 같은 칸)
 *  · `plates/<id>.webp` (또는 .jpg) — 흑백 그림 (긴 변 820px, WebP 품질 50)
 * 받은 것은 filesDir/plates 에. 그림까지 다 받은 줄만 목록에 남겨요 (못 받으면 그 세트는 안 보임).
 * 다음 달 세트를 미리 받아 두어도 화첩에는 그 달이 되어야 걸려요 ([Store.plates]).
 * 올리는 쪽: .github/workflows/plates-r2-sync.yml (저장소의 remote/plates/).
 */
object RemoteSets {
    private val base = io.github.graviton94.todaybible.BuildConfig.NARRATION_URL.takeIf { it.isNotBlank() }?.let { "${it.trimEnd('/')}/plates" }
    /** 하루 두 번까지만 확인 (작은 목록 하나). */
    private const val EVERY_MS = 12L * 60 * 60 * 1000

    fun dir(ctx: Context): File = File(ctx.filesDir, "plates").apply { mkdirs() }

    /** 판화 그림: 받은 것이 있으면 그것, 없으면 앱 안. WebP (새 판화, 같은 화질에 더 작게) 를 먼저, 없으면 JPEG. */
    fun open(ctx: Context, id: String): InputStream {
        for (ext in EXTS) File(dir(ctx), "$id.$ext").takeIf { it.isFile && it.length() > 0 }?.let { return it.inputStream() }
        for (ext in EXTS) runCatching { return ctx.assets.open("plates/$id.$ext") }
        throw java.io.FileNotFoundException("plates/$id")
    }
    private val EXTS = listOf("webp", "jpg")

    /** 받아 둔 목록 줄 (sets.tsv · stories.tsv). */
    fun lines(ctx: Context, name: String): List<String> =
        runCatching { File(dir(ctx), name).takeIf { it.isFile }?.readLines() }.getOrNull().orEmpty()

    /** 뒤에서 (네트워크): 목록 · 글 · 아직 없는 그림을 받아요. */
    fun sync(ctx: Context, force: Boolean = false) {
        val url = base ?: return
        val prefs = ctx.getSharedPreferences("today", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (!force && now - prefs.getLong("sets_checked", 0L) < EVERY_MS) return
        val list = fetch("$url/sets.tsv")?.toString(Charsets.UTF_8) ?: return
        val d = dir(ctx)
        val rows = list.lines().filter { it.isNotBlank() && !it.startsWith("#") && it.split('\t').size >= 9 }
        val ok = rows.filter { l ->
            val id = l.substringBefore('\t')
            if (!id.matches(Regex("[a-z0-9_]+"))) return@filter false
            EXTS.any { File(d, "$id.$it").let { f -> f.isFile && f.length() > 0 } } ||
                EXTS.any { ext -> fetch("$url/$id.$ext")?.let { bytes -> save(File(d, "$id.$ext"), bytes); true } == true }
        }
        save(File(d, "sets.tsv"), (list.lines().filter { it.startsWith("#") } + ok).joinToString("\n", postfix = "\n").toByteArray())
        fetch("$url/stories.tsv")?.let { save(File(d, "stories.tsv"), it) }
        prefs.edit().putLong("sets_checked", now).apply()
    }

    private fun save(f: File, bytes: ByteArray) {
        val tmp = File(f.parentFile, f.name + ".part")
        tmp.writeBytes(bytes); tmp.renameTo(f)
    }

    private fun fetch(u: String): ByteArray? = runCatching {
        (URL(u).openConnection() as HttpURLConnection).run {
            connectTimeout = 15_000; readTimeout = 30_000
            try { if (responseCode == 200) inputStream.use { it.readBytes() } else null } finally { disconnect() }
        }
    }.getOrNull()
}
