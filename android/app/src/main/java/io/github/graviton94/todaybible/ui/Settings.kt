package io.github.graviton94.todaybible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.graviton94.todaybible.BuildConfig
import io.github.graviton94.todaybible.R
import kotlinx.coroutines.launch
import io.github.graviton94.todaybible.core.Translation
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.ThemeChoice
import io.github.graviton94.todaybible.design.Tokens

private val SCALES = listOf(1f, Tokens.Ratio.scaleLarge, Tokens.Ratio.scaleLarger)

/** 설정: 한 장짜리. 고르는 것은 모두 밑줄 탭 · 한 줄 목록. */
@Composable
fun SettingsPage(s: AppState) {
    val c = Theme.c; val k = s.korean
    BackHandler { s.settingsOpen = false }
    val backLabel = stringResource(R.string.back)
    Column(Modifier.fillMaxSize().background(c.leaf)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(Tokens.Size.touch).semantics { contentDescription = backLabel }.clickable(role = Role.Button) { s.settingsOpen = false }, contentAlignment = Alignment.Center) {
                BackArrow(Modifier.size(Tokens.Size.icon))
            }
            Text(stringResource(R.string.settings), style = Theme.title(k), maxLines = 1)
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s5)) {
            Group(stringResource(R.string.theme)) {
                UnderlineTabs(listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)), s.theme.ordinal) {
                    s.setThemeChoice(ThemeChoice.entries[it])
                }
            }
            Group(stringResource(R.string.translation)) {
                Column {
                    ChoiceRow(stringResource(R.string.tr_krv), s.translation == Translation.KRV) { s.chooseTranslation(Translation.KRV) }
                    ChoiceRow(stringResource(R.string.tr_kjv), s.translation == Translation.KJV) { s.chooseTranslation(Translation.KJV) }
                }
            }
            Group(stringResource(R.string.text_size)) {
                UnderlineTabs(listOf(stringResource(R.string.size_regular), stringResource(R.string.size_large), stringResource(R.string.size_larger)),
                    SCALES.indexOfFirst { kotlin.math.abs(it - s.scale) < 0.01f }.coerceAtLeast(0)) { s.setTextScale(SCALES[it]) }
                // 지금 고른 번역 · 크기로 창세기 1:1
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.chip)).background(c.paper).padding(Tokens.Space.s4)) {
                    VerseText(s, 1, s.text(0).verse(1, 1))
                }
            }
            Group(stringResource(R.string.daily_goal)) { GoalChooser(s, title = false) }
            Group(stringResource(R.string.reminder)) {
                // 안드로이드 13+: 처음 켤 때 알림 허락을 물음
                var pending by remember { mutableIntStateOf(-1) }
                val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) s.setReminder(pending) }
                val hours = listOf(-1, 7, 21)
                UnderlineTabs(listOf(stringResource(R.string.reminder_off), stringResource(R.string.reminder_morning), stringResource(R.string.reminder_night)),
                    hours.indexOf(s.reminderHour).coerceAtLeast(0)) { i ->
                    val h = hours[i]
                    if (h >= 0 && android.os.Build.VERSION.SDK_INT >= 33) { pending = h; ask.launch(android.Manifest.permission.POST_NOTIFICATIONS) } else s.setReminder(h)
                }
            }
            Group(stringResource(R.string.stamp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    STAMPS.forEach { m ->
                        val on = m == s.stamp
                        Box(
                            Modifier.weight(1f).heightIn(min = Tokens.Size.touch).clip(RoundedCornerShape(Tokens.Radius.chip)).background(if (on) c.paper else c.leaf)
                                .drawBehind { if (on) drawLine(c.rubric, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.rule.toPx() * 2) }
                                .clickable(role = Role.RadioButton) { s.setStampMark(m) },
                            contentAlignment = Alignment.Center,
                        ) { StampMark(m, if (on) c.rubric else c.inkSoft, Modifier.size(Tokens.Size.iconMd)) }
                    }
                }
            }
            Group(stringResource(R.string.owner_name)) {
                NameField(s)
                Text(stringResource(R.string.owner_note), style = Theme.small())
            }
            Group(stringResource(R.string.my_cover)) { CoverPicker(s) }
            Group(stringResource(R.string.guide_settings)) { GuideVoiceSettings(s) }
            Group(stringResource(R.string.hand_settings)) {
                ChoiceRow(stringResource(R.string.guide_setting), s.handGuide) { s.flipGuide() }
                ChoiceRow(stringResource(R.string.pen_sound), s.penSound) { s.flipPenSound() }
                ChoiceRow(stringResource(R.string.paper_haptic), s.paperHaptic) { s.flipPaperHaptic() }
                ChoiceRow(stringResource(R.string.candle_setting), s.candle) { s.flipCandle() }
            }
            Group(stringResource(R.string.voice_keep)) {
                ChoiceRow(stringResource(R.string.voice_keep_hint), s.voiceOn) { s.toggleVoice() }
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val (voice, photos) = androidx.compose.runtime.remember { io.github.graviton94.todaybible.data.Voice.usage(ctx) }
                fun mb(b: Long) = if (b < 1_000_000) "%.1fMB".format(b / 1_000_000f) else "%.0fMB".format(b / 1_000_000f)
                Text(stringResource(R.string.storage) + " · " + stringResource(R.string.storage_line, mb(voice), mb(photos)), style = Theme.small())
                BookButton(stringResource(R.string.notes_pdf), Modifier.fillMaxWidth(), quiet = true) { s.requestNotes(null) }
            }
            Group(stringResource(R.string.lifetime)) {
                ChoiceRow(stringResource(if (s.lifetime.owned) R.string.owned else R.string.lifetime_head), s.lifetime.owned) { s.purchaseOpen = true }
            }
            Group(stringResource(R.string.backup)) {
                Text(stringResource(R.string.backup_hint), style = Theme.small())
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                        val ok = io.github.graviton94.todaybible.data.Backup.read(ctx, uri)
                        s.toast = ctx.getString(if (ok) R.string.backup_done else R.string.backup_bad)
                        if (ok) (ctx as? android.app.Activity)?.recreate()
                    }
                }
                var where by remember { mutableStateOf(s.store.backupUri) }
                var at by remember { mutableStateOf(s.store.backupAt) }
                val scope = androidx.compose.runtime.rememberCoroutineScope()
                fun keepNow(u: String) = scope.launch {
                    val ok = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { io.github.graviton94.todaybible.data.Backup.keep(ctx, android.net.Uri.parse(u)) }
                    at = s.store.backupAt; s.toast = ctx.getString(if (ok) R.string.backup_saved else R.string.backup_fail)
                }
                val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                    if (uri != null) {
                        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                        where = uri.toString(); s.store.backupUri = where; keepNow(where)
                    }
                }
                if (where.isEmpty()) {
                    BookButton(stringResource(R.string.backup_drive), Modifier.fillMaxWidth()) { create.launch("harubible_backup.zip") }
                    Text(stringResource(R.string.backup_drive_note), style = Theme.small())
                } else {
                    val last = if (at > 0) java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault())
                        .format(java.time.format.DateTimeFormatter.ofPattern(if (s.korean) "M월 d일 H:mm" else "d MMM H:mm")) else "–"
                    Text(stringResource(R.string.backup_on, last), style = Theme.small().copy(color = c.inkSoft))
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        BookButton(stringResource(R.string.backup_now), Modifier.weight(1f), quiet = true) { keepNow(where) }
                        BookButton(stringResource(R.string.backup_off), Modifier.weight(1f), quiet = true) { where = ""; s.store.backupUri = "" }
                    }
                }
                BookButton(stringResource(R.string.backup_import), Modifier.fillMaxWidth(), quiet = true) { pick.launch(arrayOf("application/zip", "application/octet-stream")) }
            }
            Group(stringResource(R.string.about)) {
                Text(stringResource(R.string.about_text), style = Theme.small())
                Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = Theme.small())
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Text(title, style = Theme.small().copy(color = Theme.c.inkSoft), maxLines = 1)
        content()
    }
}

/** 한 줄 고르기: 고른 줄 끝에 붉은 점. */
@Composable
private fun ChoiceRow(text: String, on: Boolean, onClick: () -> Unit) {
    val c = Theme.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = Tokens.Size.row).clickable(role = Role.RadioButton, onClick = onClick)
            .drawBehind { drawLine(c.hair, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.hair.toPx()) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Theme.body().copy(color = if (on) c.ink else c.inkSoft), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (on) Box(Modifier.size(Tokens.Size.dot).clip(androidx.compose.foundation.shape.CircleShape).background(c.rubric))
    }
}

/** 뒤로 (얇은 화살 한 획). */
@Composable
fun BackArrow(modifier: Modifier = Modifier) {
    val c = Theme.c
    Box(modifier.drawBehind {
        val w = Tokens.Stroke.rule.toPx(); val h = size.height; val x = size.width
        drawLine(c.ink, Offset(x * 0.9f, h / 2), Offset(x * 0.1f, h / 2), w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(c.ink, Offset(x * 0.1f, h / 2), Offset(x * 0.45f, h * 0.15f), w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(c.ink, Offset(x * 0.1f, h / 2), Offset(x * 0.45f, h * 0.85f), w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    })
}


/** 나의 성경 꾸미기 (H1): 표지 가죽 네 가지. 평생권이 필요하면 평생권 화면으로. */
@Composable
private fun CoverPicker(s: AppState) {
    val c = Theme.c
    val gated = !s.lifetime.owned && (s.lifetime.ready || s.lifetime.forceReady || s.forceLock)
    val covers = listOf("burgundy" to R.string.cover_burgundy, "navy" to R.string.cover_navy, "olive" to R.string.cover_olive, "ebony" to R.string.cover_ebony)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        covers.forEach { (id, name) ->
            val on = s.cover == id
            val col = when (id) { "navy" -> Tokens.Covers.navy; "olive" -> Tokens.Covers.olive; "ebony" -> Tokens.Covers.ebony; else -> Tokens.Covers.burgundy }
            Column(Modifier.weight(1f).clickable(role = Role.RadioButton) { if (gated && id != "burgundy") s.purchaseOpen = true else s.chooseCover(id) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Box(Modifier.fillMaxWidth().aspectRatio(Tokens.Ratio.plateAspect).clip(RoundedCornerShape(Tokens.Radius.chip)).background(col)
                    .drawBehind {
                        giltFrame(c.gilt.copy(alpha = Tokens.Alpha.frame), bands = true)
                        if (on) drawRect(c.rubric, Offset(0f, size.height - Tokens.Stroke.rule.toPx() * 2), androidx.compose.ui.geometry.Size(size.width, Tokens.Stroke.rule.toPx() * 2))
                    }, contentAlignment = Alignment.Center) {
                    StampMark(STAMP_CROSS, c.gilt, Modifier.size(Tokens.Size.iconSm))
                    if (gated && id != "burgundy") Box(Modifier.align(Alignment.TopEnd).padding(Tokens.Space.s2)) { LockMark(c.leatherInk, Modifier.size(Tokens.Size.lock)) }
                }
                Text(stringResource(name), style = Theme.small().copy(color = if (on) c.ink else c.inkSoft), maxLines = 1)
            }
        }
    }
}

/** 부를 이름 (처음 소개 · 설정 공통). 인사와 표지 금박에 들어가요. */
@Composable
fun NameField(s: AppState, modifier: Modifier = Modifier, onDone: (() -> Unit)? = null) {
    val c = Theme.c
    var name by remember { mutableStateOf(s.ownerName) }
    androidx.compose.foundation.text.BasicTextField(
        value = name, onValueChange = { v -> if (v.length <= 12) { name = v; s.setOwner(v.trim()) } },
        singleLine = true, textStyle = Theme.body(), cursorBrush = androidx.compose.ui.graphics.SolidColor(c.rubric),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone?.invoke() }),
        modifier = modifier.fillMaxWidth().heightIn(min = Tokens.Size.row).drawBehind { drawLine(c.inkSoft, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.hair.toPx()) },
        decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (name.isEmpty()) Text(stringResource(R.string.owner_hint), style = Theme.body().copy(color = c.unwritten)); inner() } },
    )
}

/** 낭독 가이드 목소리: 함께 읽기 켜기 · 이 폰의 목소리 가운데 고르기 · 미리 듣기. 목소리가 없으면 받는 곳으로. */
@Composable
private fun GuideVoiceSettings(s: AppState) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val main = remember { android.os.Handler(android.os.Looper.getMainLooper()) }
    val guide = remember(s.korean) { io.github.graviton94.todaybible.data.GuideVoice(ctx, s.korean, s.guideVoice) }
    androidx.compose.runtime.DisposableEffect(guide) { onDispose { guide.release() } }
    var list by remember(guide) { mutableStateOf<List<android.speech.tts.Voice>>(emptyList()) }
    var ready by remember(guide) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(guide) { guide.whenReady { main.post { list = guide.voices; ready = true } } }
    Text(stringResource(R.string.guide_toggle), style = Theme.small())
    if (ready && list.isEmpty()) {
        Text(stringResource(R.string.guide_none), style = Theme.small())
        BookButton(stringResource(R.string.guide_install), Modifier.fillMaxWidth(), quiet = true) {
            runCatching { ctx.startActivity(android.content.Intent("com.android.settings.TTS_SETTINGS").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        return
    }
    val chosen = s.guideVoice.takeIf { n -> list.any { it.name == n } } ?: list.firstOrNull()?.name
    list.take(4).forEachIndexed { i, v ->
        val q = stringResource(if (v.quality >= android.speech.tts.Voice.QUALITY_HIGH) R.string.guide_q_high else R.string.guide_q_normal) +
            (if (v.isNetworkConnectionRequired) " · " + stringResource(R.string.guide_net) else "")
        ChoiceRow(stringResource(R.string.guide_voice_n, i + 1, q), v.name == chosen) { s.chooseGuideVoice(v.name); guide.choose(v.name) }
    }
    if (list.isNotEmpty()) BookButton(stringResource(R.string.guide_preview), Modifier.fillMaxWidth(), quiet = true) {
        val sample = io.github.graviton94.todaybible.core.Markup.plain(s.store.book(s.translation, 18).verse(23, 1))
        guide.speak(sample, s.aloudRate(), { _, _ -> }, { })
    }
    // 미리 만든 낭독 목소리 (개역한글): 듣기는 받은 권이 있으면 그 음원으로
    if (s.korean) {
        val N = io.github.graviton94.todaybible.data.Narration
        listOf(N.MALE to R.string.narr_male, N.FEMALE to R.string.narr_female, N.DEVICE to R.string.narr_device).forEach { (v, id) ->
            ChoiceRow(stringResource(id), s.narrator == v) { s.chooseNarrator(v) }
        }
        var player by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
        androidx.compose.runtime.DisposableEffect(Unit) { onDispose { runCatching { player?.release() }; player = null } }
        if (s.narrator != N.DEVICE) BookButton(stringResource(if (player != null) R.string.guide_ai_stop else R.string.narr_preview), Modifier.fillMaxWidth(), quiet = true) {
            player?.let { runCatching { it.release() }; player = null; return@BookButton }
            guide.stop()
            player = runCatching {
                android.media.MediaPlayer().apply {
                    ctx.assets.openFd("voice/${s.narrator}_ps23_1.m4a").use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                    setOnCompletionListener { mp -> mp.release(); player = null }
                    prepare(); start()
                }
            }.getOrNull()
        }
        val used = remember(s.narration) { N.usage(ctx) }
        if (used > 0) Text(stringResource(R.string.narr_usage, "%.0fMB".format(used / 1_000_000f)), style = Theme.small())
    }
}
