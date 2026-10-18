package io.github.graviton94.todaybible.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.data.Backup
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 사실 한 줄 (이름 · 값): 내보내기 · 가져오기 창. */
@Composable
private fun Fact(name: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Text(name, style = Theme.small().copy(color = Theme.c.inkSoft), maxLines = 1)
        Text(value, style = Theme.small().copy(color = Theme.c.ink), modifier = Modifier.weight(1f), maxLines = 2)
    }
}

/**
 * 기록 내보내기 창: 「기록을 어디에 둘까요?」 쓴 절 · 메달 · 노트 · 크기, 그리고 세 갈래.
 * 드라이브 자동 보관 (같은 파일을 쓸 때마다 새로) · 휴대폰에 저장 (고른 폴더, 기본 다운로드) · 메신저 · 메일로 보내기.
 */
@Composable
fun ArchiveSheet(s: AppState) {
    val k = s.korean; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val sum = remember { Backup.summary(ctx) }
    fun close() { s.archiveOpen = false }
    fun done(ok: Boolean) { s.toast = ctx.getString(if (ok) R.string.backup_saved else R.string.backup_fail); if (ok) { s.keepRev++; close() } }
    fun keepAt(u: android.net.Uri) = scope.launch { done(withContext(Dispatchers.IO) { Backup.keep(ctx, u) }) }
    // 드라이브 (자동): 그 위치를 기억해 두고 쓸 때마다 새로
    val drive = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            s.store.backupUri = uri.toString(); s.store.backupFails = 0; keepAt(uri)
        }
    }
    // 휴대폰에 저장 (한 번): 위치는 기억하지 않아요
    val phone = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            done(withContext(Dispatchers.IO) { runCatching {
                val f = Backup.once(ctx); ctx.contentResolver.openOutputStream(uri, "wt")!!.use { o -> f.inputStream().use { it.copyTo(o) } }; Backup.exported(ctx); true
            }.getOrDefault(false) })
        }
    }
    BookSheet({ close() }) {
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Text("ARCHIVUM", style = Theme.caps(), maxLines = 1)
            Text(stringResource(R.string.archive_title), style = Theme.title(k), maxLines = 2)
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Fact(stringResource(R.string.archive_verses), stringResource(R.string.archive_verses_v, sum.verses))
                Fact(stringResource(R.string.archive_medals), stringResource(R.string.archive_medals_v, sum.medals, sum.notes))
                Fact(stringResource(R.string.archive_size), stringResource(R.string.archive_size_v, sizeOf(sum.bytes)))
            }
            Hair()
            val on = s.store.backupUri.isNotEmpty()
            BookButton(stringResource(if (on) R.string.backup_now else R.string.backup_drive), Modifier.fillMaxWidth()) {
                if (on) keepAt(android.net.Uri.parse(s.store.backupUri)) else drive.launch(Backup.fileName(ctx))
            }
            Text(stringResource(R.string.backup_drive_note), style = Theme.small())
            BookButton(stringResource(R.string.archive_phone), Modifier.fillMaxWidth(), quiet = true) { phone.launch(Backup.fileName(ctx)) }
            BookButton(stringResource(R.string.archive_send), Modifier.fillMaxWidth(), quiet = true) {
                scope.launch {
                    val f = withContext(Dispatchers.IO) { runCatching { Backup.once(ctx) }.getOrNull() }
                    if (f == null) done(false) else { Export.share(ctx, ExportJob(listOf(f), "application/zip", ctx.getString(R.string.archive_mail_title))); Backup.exported(ctx); s.keepRev++; close() }
                }
            }
        }
    }
}

/** 가져오기 확인: 파일의 날짜 · 절 수 · 더해질 것을 보여 주고 한 번 더 물어요. 합치기만 해요 (지우는 것 없음). */
@Composable
fun ImportSheet(s: AppState, p: Backup.Preview) {
    val k = s.korean; val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    fun cancel() { s.importPreview = null; Backup.discard(ctx) }
    BookSheet({ cancel() }) {
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Text("RESTITUTIO", style = Theme.caps(), maxLines = 1)
            Text(stringResource(R.string.import_title), style = Theme.title(k), maxLines = 2)
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                p.made?.let { Fact(stringResource(R.string.import_file), stringResource(R.string.import_made, Lang.date(ctx, R.string.fmt_ymd, java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate()))) }
                Fact(stringResource(R.string.archive_verses), stringResource(R.string.import_verses_v, p.verses, p.mine))
                Fact(stringResource(R.string.import_added), stringResource(R.string.import_added_v, p.added, p.medals))
            }
            Text(stringResource(R.string.import_note), style = Theme.small())
            BookButton(stringResource(R.string.import_go), Modifier.fillMaxWidth()) {
                scope.launch {
                    val r = withContext(Dispatchers.IO) { Backup.apply(ctx) }
                    s.importPreview = null
                    if (r == null) s.toast = ctx.getString(R.string.backup_bad)
                    else {
                        // 다시 그린 뒤에도 보이게 한 줄을 맡겨 둠 (다시 켜면 Play 에서 산 것을 다시 확인해요)
                        s.store.pendingToast = if (r.merged) ctx.getString(R.string.backup_merged, r.added) else ctx.getString(R.string.backup_done)
                        (ctx as? android.app.Activity)?.recreate()
                    }
                }
            }
            BookButton(stringResource(R.string.import_cancel), Modifier.fillMaxWidth(), quiet = true) { cancel() }
        }
    }
}

/** 오늘 화면 보관 한 줄: 「지난 보관 뒤로 12장 · 기록 보관하기 › · 나중에」 또는 「자동 보관이 멈췄어요 · 위치 다시 고르기 ›」. 푸시 없음. */
@Composable
fun KeepStrip(s: AppState) {
    val n = s.keepNudge() ?: return
    val c = Theme.c
    val stopped = n.first == AppState.KeepNudge.STOPPED
    Row(Modifier.fillMaxWidth().border(Tokens.Stroke.hair, c.hair).padding(horizontal = Tokens.Space.s3),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Text(if (stopped) stringResource(R.string.keep_stopped) else if (n.second > 0) stringResource(R.string.keep_since, n.second) else stringResource(R.string.keep_never),
            style = Theme.small().copy(color = c.ink), maxLines = 2, modifier = Modifier.weight(1f).padding(vertical = Tokens.Space.s2))
        Box(Modifier.heightIn(min = Tokens.Size.touch).clickable(role = Role.Button) { if (stopped) s.store.backupUri = ""; s.archiveOpen = true }, contentAlignment = Alignment.Center) {
            Text(stringResource(if (stopped) R.string.keep_repick else R.string.keep_go), style = Theme.small().copy(color = c.giltText), maxLines = 1)
        }
        if (!stopped) Box(Modifier.heightIn(min = Tokens.Size.touch).clickable(role = Role.Button) { s.keepLater() }, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.keep_later), style = Theme.small().copy(color = c.inkSoft, textDecoration = TextDecoration.Underline), maxLines = 1)
        }
    }
}

private fun sizeOf(bytes: Long): String = if (bytes >= 1_000_000) "%.1fMB".format(bytes / 1_000_000.0) else "${(bytes / 1000).coerceAtLeast(1)}KB"
