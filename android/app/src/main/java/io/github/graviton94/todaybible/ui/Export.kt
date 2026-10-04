package io.github.graviton94.todaybible.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.core.content.FileProvider
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import java.io.File

/** 내보낼 것: 파일들 (PDF · 소리 · 영상 · 그림), 종류, 제목 (메일 제목 · 저장 폴더 안내에 씀). */
data class ExportJob(val files: List<File>, val mime: String, val title: String)

/**
 * 내보내기 세 갈래: 이 폰에 저장 (다운로드 › 하루의 성경) · 메일로 보내기 · 다른 앱으로 보내기 (카카오톡 등).
 * 인터넷으로 우리 쪽에 보내는 것은 없어요. 메일도 폰의 메일 앱이 보내요.
 */
object Export {
    fun folder(ctx: Context) = ctx.getString(R.string.app_name)
    fun uri(ctx: Context, f: File): Uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.share", f)

    private fun sendIntent(ctx: Context, job: ExportJob): Intent {
        val uris = ArrayList(job.files.map { uri(ctx, it) })
        return (if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris))
            .setType(job.mime).putExtra(Intent.EXTRA_SUBJECT, job.title).putExtra(Intent.EXTRA_TEXT, job.title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** 다른 앱으로 (나누기 창). */
    fun share(ctx: Context, job: ExportJob) {
        ctx.startActivity(Intent.createChooser(sendIntent(ctx, job), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** 메일 앱으로: 받는 사람 · 제목을 채워서 (메일 앱만 고르게). */
    fun mail(ctx: Context, job: ExportJob, to: String): Boolean {
        val send = sendIntent(ctx, job).apply {
            if (to.isNotBlank()) putExtra(Intent.EXTRA_EMAIL, arrayOf(to.trim()))
            selector = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
        }
        return runCatching { ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }.getOrDefault(false)
    }

    /** 이 폰의 다운로드 › 하루의 성경 폴더에 (안드로이드 10 이상). 저장한 개수. */
    fun saveToDownloads(ctx: Context, job: ExportJob): Int {
        if (Build.VERSION.SDK_INT < 29) return 0
        var n = 0
        for (f in job.files) runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, f.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeOf(f, job.mime))
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/${folder(ctx)}")
            }
            val u = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return@runCatching
            ctx.contentResolver.openOutputStream(u)?.use { out -> f.inputStream().use { it.copyTo(out) } }
            n++
        }
        return n
    }

    /** 안드로이드 9 이하: 고른 폴더에. */
    fun saveToTree(ctx: Context, job: ExportJob, tree: Uri): Int {
        val parent = android.provider.DocumentsContract.buildDocumentUriUsingTree(tree, android.provider.DocumentsContract.getTreeDocumentId(tree))
        var n = 0
        for (f in job.files) runCatching {
            val u = android.provider.DocumentsContract.createDocument(ctx.contentResolver, parent, mimeOf(f, job.mime), f.name) ?: return@runCatching
            ctx.contentResolver.openOutputStream(u)?.use { out -> f.inputStream().use { it.copyTo(out) } }
            n++
        }
        return n
    }

    private fun mimeOf(f: File, fallback: String) = when (f.extension.lowercase()) {
        "pdf" -> "application/pdf"; "png" -> "image/png"; "m4a" -> "audio/mp4"; "mp4" -> "video/mp4"; else -> fallback
    }
}

/** 내보내기 창: 이 폰에 저장 · 메일로 보내기 (받는 사람 기억) · 다른 앱으로 보내기. */
@Composable
fun ExportSheet(s: AppState, job: ExportJob) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    var to by remember { mutableStateOf(s.store.mailTo) }
    fun close() { s.exportJob = null }
    fun saved(n: Int) { s.toast = if (n > 0) ctx.getString(R.string.export_saved, Export.folder(ctx)) else ctx.getString(R.string.export_failed); if (n > 0) close() }
    val tree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { u -> if (u != null) saved(Export.saveToTree(ctx, job, u)) }
    BookSheet({ close() }) {
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Text(job.title, style = Theme.title(k), maxLines = 2)
            Text(job.files.joinToString(" · ") { it.name } + " · " + sizeLabel(job.files.sumOf { it.length() }), style = Theme.small(), maxLines = 2)
            BookButton(stringResource(R.string.export_save), Modifier.fillMaxWidth()) {
                if (Build.VERSION.SDK_INT >= 29) saved(Export.saveToDownloads(ctx, job)) else tree.launch(null)
            }
            // 메일: 받는 사람 (이 폰에만 기억)
            Text(stringResource(R.string.export_mail_to), style = Theme.small().copy(color = c.inkSoft))
            BasicTextField(to, { to = it }, singleLine = true, textStyle = Theme.body(), cursorBrush = SolidColor(c.rubric),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.Size.row).clip(RoundedCornerShape(Tokens.Radius.chip)).background(c.paper).padding(horizontal = Tokens.Space.s4),
                decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart, modifier = Modifier.heightIn(min = Tokens.Size.row)) {
                    if (to.isEmpty()) Text(stringResource(R.string.export_mail_hint), style = Theme.body().copy(color = c.unwritten), maxLines = 1)
                    inner()
                } })
            BookButton(stringResource(R.string.export_mail), Modifier.fillMaxWidth(), quiet = true) {
                s.store.mailTo = to.trim()
                if (Export.mail(ctx, job, to)) close() else s.toast = ctx.getString(R.string.export_no_mail)
            }
            BookButton(stringResource(R.string.export_share), Modifier.fillMaxWidth(), quiet = true) { Export.share(ctx, job); close() }
        }
    }
}

private fun sizeLabel(bytes: Long): String = if (bytes >= 1_000_000) "%.1fMB".format(bytes / 1_000_000.0) else "${(bytes / 1000).coerceAtLeast(1)}KB"
