package io.github.graviton94.todaybible.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import io.github.graviton94.todaybible.core.Merge

/**
 * 기록 보관: 세 필사본 기록 · 발자취 · 설정 · 노트 · 손글씨 · 내 목소리를 zip 하나로 (내가 고른 곳에만). 산 것은 넣지 않아요 (Play 가 증명).
 * 되살리기는 같은 파일에서 (합치기). 구글 자동 백업과 따로, 손으로 간직하는 사본. 서버 · 로그인 없음.
 */
object Backup {
    private val files = listOf("fills.tsv", "earned.tsv", "marks.tsv", "aloud.tsv", "bookmarks.tsv", "memory.tsv", "notes.tsv")
    private val dirs = listOf("ink", "voice")
    /** 보관 파일 형식 (manifest.json). 1 = manifest 없는 예전 파일. */
    const val FORMAT = 2
    /** 파일에 넣지도, 파일에서 받지도 않는 설정: 산 것 (Play 만 증명) · 이 폰의 보관 위치 · 시험용. */
    private fun privateKey(k: String) = k.startsWith("backup_") || k.startsWith("keep_nudge") || k in setOf("lifetime", "subscribed", "shop_seen", "debug_month")

    /** 파일 이름: 오늘성경-기록-2026-10-18.zip (앱 말로). */
    fun fileName(ctx: Context) = ctx.getString(io.github.graviton94.todaybible.R.string.backup_file, java.time.LocalDate.now().toString())

    private fun lines(f: File) = if (f.exists()) f.readLines().filter { it.isNotBlank() } else emptyList()

    /** 내보내기 창에 보여 줄 것: 쓴 절 · 메달 · 노트 · 크기 (목소리 포함). */
    data class Summary(val verses: Int, val medals: Int, val notes: Int, val bytes: Long)
    fun summary(ctx: Context): Summary {
        val all = (files.map { File(ctx.filesDir, it) } + dirs.map { File(ctx.filesDir, it) }).filter { it.exists() }
        return Summary(lines(File(ctx.filesDir, "fills.tsv")).size, lines(File(ctx.filesDir, "earned.tsv")).size, lines(File(ctx.filesDir, "notes.tsv")).size,
            all.sumOf { f -> if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length() })
    }

    fun write(ctx: Context, out: File): File {
        out.parentFile?.mkdirs()
        val sum = summary(ctx)
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            fun add(f: File, name: String) { z.putNextEntry(ZipEntry(name)); f.inputStream().use { it.copyTo(z) }; z.closeEntry() }
            // 형식 · 만든 때 · 수: 나중에 구조가 바뀌어도 읽고, 가져오기 전에 보여 줘요
            val ver = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
            z.putNextEntry(ZipEntry("manifest.json"))
            z.write(org.json.JSONObject().put("app", "todaybible").put("format", FORMAT).put("version", ver).put("made", System.currentTimeMillis())
                .put("verses", sum.verses).put("medals", sum.medals).put("notes", sum.notes).toString().toByteArray()); z.closeEntry()
            files.map { File(ctx.filesDir, it) }.filter { it.exists() }.forEach { add(it, it.name) }
            dirs.map { File(ctx.filesDir, it) }.filter { it.exists() }.forEach { d -> d.walkTopDown().filter { it.isFile }.forEach { add(it, it.relativeTo(ctx.filesDir).path) } }
            // 설정은 산 것 · 보관 위치를 뺀 사본으로
            val prefs = File(ctx.applicationInfo.dataDir, "shared_prefs/today.xml")
            if (prefs.exists()) runCatching {
                val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(prefs)
                val nodes = doc.documentElement.childNodes
                (nodes.length - 1 downTo 0).forEach { i -> (nodes.item(i) as? org.w3c.dom.Element)?.let { e -> if (privateKey(e.getAttribute("name"))) e.parentNode.removeChild(e) } }
                z.putNextEntry(ZipEntry("prefs/today.xml"))
                javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(javax.xml.transform.dom.DOMSource(doc), javax.xml.transform.stream.StreamResult(z))
                z.closeEntry()
            }
        }
        return out
    }

    /** 보관을 마쳤을 때: 그때의 다 쓴 장 수를 적어 두고 알림 줄을 다시 셈. */
    private fun kept(ctx: Context) { val st = Store(ctx); st.backupAt = System.currentTimeMillis(); st.backupFails = 0; st.keptChapters = st.doneChaptersNow }

    /** 정해 둔 곳(구글 드라이브 등)에 같은 파일을 새로 써요. */
    fun keep(ctx: Context, uri: Uri): Boolean = runCatching {
        val tmp = write(ctx, File(ctx.cacheDir, "backup/todaybible.zip"))
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { o -> tmp.inputStream().use { it.copyTo(o) } }
        tmp.delete(); kept(ctx); true
    }.getOrElse { Store(ctx).let { it.backupFails = it.backupFails + 1 }; false }

    /** 한 번 내보내기 (휴대폰에 저장 · 보내기): 공유 폴더에 파일을 만들어 돌려줘요. */
    fun once(ctx: Context): File = write(ctx, File(File(ctx.cacheDir, "share").apply { mkdirs() }, fileName(ctx)))
    /** 내보내기를 마쳤다고 적어요 (알림 줄 다시 셈). */
    fun exported(ctx: Context) = kept(ctx)

    /** 앱을 닫을 때: 마지막 보관 뒤로 새로 쓴 것이 있고 한 시간이 지났으면 뒤에서 조용히 보관. */
    fun auto(ctx: Context) {
        val st = Store(ctx); val u = st.backupUri.takeIf { it.isNotEmpty() } ?: return
        val changed = (files.map { File(ctx.filesDir, it) } + dirs.map { File(ctx.filesDir, it) }).filter { it.exists() }
            .flatMap { if (it.isDirectory) it.walkTopDown().toList() else listOf(it) }.maxOfOrNull { it.lastModified() } ?: 0L
        if (changed <= st.backupAt || System.currentTimeMillis() - st.backupAt < 3_600_000L) return
        val app = ctx.applicationContext
        Thread { keep(app, Uri.parse(u)) }.apply { name = "backup"; isDaemon = false }.start()
    }

    /** 가져오기 전에 보여 줄 것: 만든 때 (모르면 null) · 파일의 절 · 이 폰의 절 · 더해질 절 · 메달. */
    data class Preview(val made: Long?, val verses: Int, val mine: Int, val added: Int, val medals: Int, val format: Int)

    private fun staging(ctx: Context) = File(ctx.cacheDir, "restore")

    /** 파일을 풀어 검사해요. 오늘성경 기록이 아니거나 망가졌으면 null. 가져오기는 [apply] 로. */
    fun inspect(ctx: Context, uri: Uri): Preview? = runCatching {
        var ok = false
        val tmp = staging(ctx).apply { deleteRecursively(); mkdirs() }
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
        if (!ok) { tmp.deleteRecursively(); return null }
        val man = File(tmp, "manifest.json").takeIf { it.exists() }?.let { org.json.JSONObject(it.readText()) }
        if (man != null && man.optString("app") != "todaybible") { tmp.deleteRecursively(); return null }
        val theirs = lines(File(tmp, "fills.tsv")); val mine = lines(File(ctx.filesDir, "fills.tsv"))
        Preview(man?.optLong("made")?.takeIf { it > 0 }, theirs.size, mine.size, Merge.added(mine, Merge.fills(mine, theirs)),
            lines(File(tmp, "earned.tsv")).size, man?.optInt("format", 1) ?: 1)
    }.getOrNull()

    /** 되살린 결과: 이 기기에 기록이 있었으면 합친 것 (merged), 새로 더해진 절 수. */
    data class Restored(val merged: Boolean, val added: Int)

    /**
     * 가져오기 (합치기): [inspect] 로 풀어 둔 것을 지금 기록과 합쳐요. 덮어쓰지 않아요 (폰 ↔ 태블릿 번갈아 써도 사라지지 않게).
     * 손글씨 · 목소리는 이 기기에 없는 것만 더하고, 설정은 이 기기에 기록이 없을 때 (처음 되살리기) 만 옮겨요.
     * 기능 메달 수는 큰 쪽, 마친 작정 · 쓴 펜은 합쳐요. 산 것은 파일에 있어도 받지 않고 Play 로 다시 확인해요. 실패면 null.
     */
    fun apply(ctx: Context): Restored? = runCatching {
        val tmp = staging(ctx).takeIf { File(it, "fills.tsv").exists() } ?: return null
        val had = File(ctx.filesDir, "fills.tsv").let { it.exists() && it.length() > 0 }
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
        File(tmp, "prefs/today.xml").takeIf { it.exists() }?.let { p ->
            // 설정은 SharedPreferences 로 다시 읽어 옮김 (파일을 직접 덮지 않음)
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(p)
            val sp = ctx.getSharedPreferences("today", Context.MODE_PRIVATE); val ed = sp.edit()
            val nodes = doc.documentElement.childNodes
            for (i in 0 until nodes.length) {
                val n = nodes.item(i) as? org.w3c.dom.Element ?: continue
                val key = n.getAttribute("name"); val v = n.getAttribute("value")
                if (privateKey(key)) continue // 산 것 · 보관 위치는 이 폰의 것
                // 1.7 기능 메달에 드는 기록은 이 폰에 기록이 있어도 합쳐요
                when {
                    key.startsWith("cnt_") -> { v.toIntOrNull()?.let { ed.putInt(key, maxOf(it, sp.getInt(key, 0))) }; continue }
                    key == "pens_done" || key == "votums_done" -> { val inc = (0 until n.childNodes.length).mapNotNull { (n.childNodes.item(it) as? org.w3c.dom.Element)?.textContent }.toSet()
                        ed.putStringSet(key, sp.getStringSet(key, emptySet())!! + inc); continue }
                    key == "night_chapter" || key == "christmas_luke" -> { if (v == "true") ed.putBoolean(key, true); continue }
                    key == "typed_letters" -> { ed.putInt(key, -1); continue } // 합친 기록으로 다시 셈
                }
                if (had) continue
                when (n.tagName) {
                    "string" -> ed.putString(key, n.textContent)
                    "int" -> v.toIntOrNull()?.let { ed.putInt(key, it) }
                    "long" -> v.toLongOrNull()?.let { ed.putLong(key, it) }
                    "boolean" -> ed.putBoolean(key, v == "true")
                    "float" -> v.toFloatOrNull()?.let { ed.putFloat(key, it) }
                    "set" -> ed.putStringSet(key, (0 until n.childNodes.length).mapNotNull { (n.childNodes.item(it) as? org.w3c.dom.Element)?.textContent }.toSet())
                }
            }
            ed.putInt("typed_letters", -1)
            ed.commit()
        }
        tmp.deleteRecursively(); Restored(had, added)
    }.getOrNull()

    /** 열어 둔 가져오기를 접어요 (취소). */
    fun discard(ctx: Context) { staging(ctx).deleteRecursively() }
}
