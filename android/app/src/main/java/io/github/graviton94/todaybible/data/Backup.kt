package io.github.graviton94.todaybible.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 기록 보관: 필사 기록 · 발자취 · 설정 · 노트 사진 · 내 목소리를 zip 하나로 (내가 고른 곳에).
 * 되살리기는 같은 파일에서. 구글 자동 백업과 따로, 손으로 간직하는 사본.
 */
object Backup {
    private val files = listOf("fills.tsv", "earned.tsv")
    private val dirs = listOf("photos", "voice")

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

    /** 되살리기: 기록 파일이 들어 있어야 함. 성공하면 true (앱을 다시 그려야 함). */
    fun read(ctx: Context, uri: Uri): Boolean = runCatching {
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
        if (!ok) return false
        files.forEach { n -> File(tmp, n).takeIf { it.exists() }?.copyTo(File(ctx.filesDir, n), overwrite = true) }
        dirs.forEach { d -> File(tmp, d).takeIf { it.exists() }?.copyRecursively(File(ctx.filesDir, d), overwrite = true) }
        File(tmp, "prefs/today.xml").takeIf { it.exists() }?.let { p ->
            // 설정은 SharedPreferences 로 다시 읽어 옮김 (파일을 직접 덮지 않음)
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(p)
            val ed = ctx.getSharedPreferences("today", Context.MODE_PRIVATE).edit()
            val nodes = doc.documentElement.childNodes
            for (i in 0 until nodes.length) {
                val n = nodes.item(i) as? org.w3c.dom.Element ?: continue
                val key = n.getAttribute("name"); val v = n.getAttribute("value")
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
        tmp.deleteRecursively(); true
    }.getOrDefault(false)
}
