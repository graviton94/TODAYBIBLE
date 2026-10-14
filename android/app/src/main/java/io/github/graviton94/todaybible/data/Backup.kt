package io.github.graviton94.todaybible.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import io.github.graviton94.todaybible.core.Merge

/**
 * 기록 보관: 필사 기록 · 발자취 · 설정 · 노트 사진 · 내 목소리를 zip 하나로 (내가 고른 곳에).
 * 되살리기는 같은 파일에서. 구글 자동 백업과 따로, 손으로 간직하는 사본.
 */
object Backup {
    private val files = listOf("fills.tsv", "earned.tsv", "marks.tsv", "aloud.tsv", "bookmarks.tsv", "memory.tsv", "notes.tsv")
    private val dirs = listOf("ink", "voice")

    fun write(ctx: Context, out: File): File {
        out.parentFile?.mkdirs()
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            fun add(f: File, name: String) { z.putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
            files.map { File(ctx.filesDir, it) }.filter { it.exists() }.forEach { add(it, it.name) }
            dirs.map { File(ctx.filesDir, it) }.filter { it.exists() }.forEach { d -> d.walkTopDown().filter { it.isFile }.forEach { add(it, it.relativeTo(ctx.filesDir).path) } }
            val prefs = File(ctx.applicationInfo.dataDir, "shared_prefs/today.xml")
            if (prefs.exists()) add(prefs, "prefs/today.xml")
        }
        return out
    }

    /** 정해 둔 곳(구글 드라이브 등)에 같은 파일을 새로 써요. */
    fun keep(ctx: Context, uri: Uri): Boolean = runCatching {
        val tmp = write(ctx, File(ctx.cacheDir, "backup/harubible.zip"))
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { o -> tmp.inputStream().use { it.copyTo(o) } }
        tmp.delete(); Store(ctx).backupAt = System.currentTimeMillis(); true
    }.getOrDefault(false)

    /** 앱을 닫을 때: 마지막 보관 뒤로 새로 쓴 것이 있고 한 시간이 지났으면 뒤에서 조용히 보관. */
    fun auto(ctx: Context) {
        val st = Store(ctx); val u = st.backupUri.takeIf { it.isNotEmpty() } ?: return
        val changed = (files.map { File(ctx.filesDir, it) } + dirs.map { File(ctx.filesDir, it) }).filter { it.exists() }
            .flatMap { if (it.isDirectory) it.walkTopDown().toList() else listOf(it) }.maxOfOrNull { it.lastModified() } ?: 0L
        if (changed <= st.backupAt || System.currentTimeMillis() - st.backupAt < 3_600_000L) return
        val app = ctx.applicationContext
        Thread { keep(app, Uri.parse(u)) }.apply { name = "backup"; isDaemon = false }.start()
    }

    /** 되살린 결과: 이 기기에 기록이 있었으면 합친 것 (merged), 새로 더해진 절 수. */
    data class Restored(val merged: Boolean, val added: Int)

    /**
     * 되살리기 (1.5 합치기): 기록 파일이 들어 있어야 함. 덮어쓰지 않고 지금 기록과 합쳐요 (폰 ↔ 태블릿 번갈아 써도 사라지지 않게).
     * 손글씨 · 목소리는 이 기기에 없는 것만 더하고, 설정은 이 기기에 기록이 없을 때 (처음 되살리기) 만 옮겨요.
     * 실패면 null (앱을 다시 그려야 함).
     */
    fun read(ctx: Context, uri: Uri): Restored? = runCatching {
        var ok = false
        val tmp = File(ctx.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        ctx.contentResolver.openInputStream(uri)!!.use { input ->
            ZipInputStream(input.buffered()).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    val name = e.name
                    if (name.contains("..") || e.isDirectory) continue
                    val dst = File(tmp, name); dst.parentFile?.mkdirs()
                    dst.outputStream().use { z.copyTo(it) }
                    if (name == "fills.tsv") ok = true
                }
            }
        }
        if (!ok) return null
        val had = File(ctx.filesDir, "fills.tsv").let { it.exists() && it.length() > 0 }
        fun lines(f: File) = if (f.exists()) f.readLines().filter { it.isNotBlank() } else emptyList()
        val rule: Map<String, (List<String>, List<String>) -> List<String>> = mapOf(
            "fills.tsv" to Merge::fills, "earned.tsv" to Merge::earned, "aloud.tsv" to Merge::aloud, "notes.tsv" to Merge::notes,
            "marks.tsv" to Merge::marks, "bookmarks.tsv" to Merge::marks, "memory.tsv" to Merge::marks)
        var added = 0
        files.forEach { n ->
            val inc = File(tmp, n).takeIf { it.exists() } ?: return@forEach
            val dst = File(ctx.filesDir, n); val mine = lines(dst)
            val merged = rule[n]?.invoke(mine, lines(inc)) ?: lines(inc)
            if (n == "fills.tsv") added = Merge.added(mine, merged)
            // 임시 파일에 다 쓴 뒤 바꿔 끼움 (도중에 꺼져도 반쪽 파일이 남지 않게)
            val part = File(ctx.filesDir, "$n.restore"); part.writeText(merged.joinToString("") { it + "\n" })
            if (!part.renameTo(dst)) { dst.writeText(part.readText()); part.delete() }
        }
        dirs.forEach { d ->
            val src = File(tmp, d).takeIf { it.isDirectory } ?: return@forEach
            src.walkTopDown().filter { it.isFile }.forEach { f -> val to = File(File(ctx.filesDir, d), f.relativeTo(src).path); if (!to.exists()) { to.parentFile?.mkdirs(); f.copyTo(to) } }
        }
        if (!had) File(tmp, "prefs/today.xml").takeIf { it.exists() }?.let { p ->
            // 설정은 SharedPreferences 로 다시 읽어 옮김 (파일을 직접 덮지 않음)
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(p)
            val ed = ctx.getSharedPreferences("today", Context.MODE_PRIVATE).edit()
            val nodes = doc.documentElement.childNodes
            for (i in 0 until nodes.length) {
                val n = nodes.item(i) as? org.w3c.dom.Element ?: continue
                val key = n.getAttribute("name"); val v = n.getAttribute("value")
                if (key.startsWith("backup_")) continue // 보관 위치 권한은 이 폰의 것
                when (n.tagName) {
                    "string" -> ed.putString(key, n.textContent)
                    "int" -> v.toIntOrNull()?.let { ed.putInt(key, it) }
                    "long" -> v.toLongOrNull()?.let { ed.putLong(key, it) }
                    "boolean" -> ed.putBoolean(key, v == "true")
                    "float" -> v.toFloatOrNull()?.let { ed.putFloat(key, it) }
                }
            }
            ed.commit()
        }
        tmp.deleteRecursively(); Restored(had, added)
    }.getOrNull()
}
