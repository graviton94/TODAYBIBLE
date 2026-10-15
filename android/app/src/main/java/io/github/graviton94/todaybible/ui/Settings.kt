package io.github.graviton94.todaybible.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import io.github.graviton94.todaybible.core.Translation
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.ThemeChoice
import io.github.graviton94.todaybible.design.Tokens

// 글자 크기는 두 단계 (보통 · 크게): 더 키우면 화면이 쓸 수 없을 만큼 무너져요
private val SCALES = listOf(1f, Tokens.Ratio.scaleLarge)

/** 설정: 한 장짜리. 고르는 것은 모두 밑줄 탭 · 한 줄 목록. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
fun SettingsPage(s: AppState) {
    val ctx0 = androidx.compose.ui.platform.LocalContext.current
    val c = Theme.c; val k = s.korean
    BackHandler { s.settingsOpen = false }
    val backLabel = stringResource(R.string.back)
    var askLang by remember { mutableStateOf(s.debugAskLang) }
    val scope0 = androidx.compose.runtime.rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(c.leaf)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(Tokens.Size.touch).semantics { contentDescription = backLabel }.clickable(role = Role.Button) { s.settingsOpen = false }, contentAlignment = Alignment.Center) {
                BackArrow(Modifier.size(Tokens.Size.icon))
            }
        }
        Column(Modifier.padding(horizontal = Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
            Text("OPTIONES", style = Theme.caps(), maxLines = 1)
            Text(stringResource(R.string.settings), style = Theme.title(k, Tokens.Text.display), maxLines = 1)
        }
        // 갈래 (접힘): 처음엔 모두 접혀 있어요. 하나를 열면 그 머리가 맨 위로 오고 (위 갈래가 접히며 밀려도),
        // 머리부터 끝까지 옅은 금빛 바탕 + 왼쪽 금선. 내려 보는 동안엔 위에 ‘갈래 이름 · 접기’가 붙어요.
        var openSec by remember { mutableStateOf<String?>(s.debugSection.also { if (!BuildConfig.DEV_TOOLS) s.debugSection = null }) }
        val spots = remember { androidx.compose.runtime.mutableStateMapOf<String, Pair<Float, Float>>() }
        val scroll = rememberScrollState()
        val dens = androidx.compose.ui.platform.LocalDensity.current
        val padTop = with(dens) { Tokens.Space.s3.toPx() }
        var settle by remember { mutableIntStateOf(0) }
        fun toggle(id: String) { openSec = if (openSec == id) null else id; settle++ }
        androidx.compose.runtime.LaunchedEffect(settle) {
            val id = openSec ?: return@LaunchedEffect
            // 위 갈래가 접혀 자리가 바뀐 다음 (두 장면 뒤) 에 그 머리로
            androidx.compose.runtime.withFrameNanos { }; androidx.compose.runtime.withFrameNanos { }
            spots[id]?.let { scroll.animateScrollTo((it.first + padTop).toInt()) }
        }
        val stuck by remember { androidx.compose.runtime.derivedStateOf {
            val id = openSec ?: return@derivedStateOf null
            val (y, h) = spots[id] ?: return@derivedStateOf null
            val off = scroll.value - y - padTop
            id.takeIf { off > with(dens) { Tokens.Size.rowTall.toPx() } && off < h - with(dens) { Tokens.Size.rowTall.toPx() } }
        } }
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val view = maxHeight
        Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Fold("set_display", stringResource(R.string.set_display), listOf(stringResource(when (s.language) { "ko" -> R.string.lang_ko_short; "en" -> R.string.lang_en_short; else -> R.string.lang_system }), stringResource(listOf(R.string.theme_system, R.string.theme_light, R.string.theme_dark, R.string.theme_candle)[s.theme.ordinal])).joinToString(" · "), openSec, spots, { toggle(it) }) {
            // 언어: 화면 글 · 성경 번역 · 낭독 목소리를 한 번에 (한국어 = 개역한글 · 한국어 낭독, English = KJV · 영어 낭독)
            Group(stringResource(R.string.language)) {
                Column {
                    val enName = stringResource(if (s.store.englishBible == io.github.graviton94.todaybible.core.Translation.KJV) R.string.tr_kjv else R.string.tr_web)
                    listOf("system" to stringResource(R.string.lang_system), "ko" to stringResource(R.string.lang_ko), "en" to stringResource(R.string.lang_en, enName)).forEach { (id, name) ->
                        ChoiceRow(name, s.language == id) { if (s.language != id) askLang = id }
                    }
                }
            }
            // 영어 성경: 오늘의 영어 (WEB, 기본) · 1611 고전 영어 (KJV). 영어로 볼 때만
            if (s.translation != io.github.graviton94.todaybible.core.Translation.KRV) Group(stringResource(R.string.english_bible)) {
                Column {
                    listOf(io.github.graviton94.todaybible.core.Translation.WEB to R.string.web_desc, io.github.graviton94.todaybible.core.Translation.KJV to R.string.kjv_desc).forEach { (t, name) ->
                        ChoiceRow(stringResource(name), s.translation == t) { if (s.translation != t) s.chooseEnglishBible(t) }
                    }
                }
            }
            Group(stringResource(R.string.theme)) {
                UnderlineTabs(listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark), stringResource(R.string.theme_candle)), s.theme.ordinal) {
                    s.setThemeChoice(ThemeChoice.entries[it])
                }
            }
            Group(stringResource(R.string.text_size)) {
                UnderlineTabs(listOf(stringResource(R.string.size_regular), stringResource(R.string.size_large)),
                    SCALES.indexOfFirst { kotlin.math.abs(it - s.scale) < 0.01f }.coerceAtLeast(0)) { s.setTextScale(SCALES[it]) }
                // 또렷하게 (나2): 흐린 글자를 진하게, 가는 줄을 또렷하게
                ChoiceRow(stringResource(R.string.contrast_setting), s.contrast) { s.flipContrast() }
                // 지금 고른 번역 · 크기로 창세기 1:1
                Box(Modifier.fillMaxWidth().background(c.paper).padding(Tokens.Space.s4)) {
                    VerseText(s, 1, s.text(0).verse(1, 1))
                }
            }
            Group(stringResource(R.string.simple_h)) {
                ChoiceRow(stringResource(R.string.simple_mode), s.simple) { s.flipSimple() }
                Text(stringResource(R.string.simple_hint), style = Theme.small())
            }
            }
            Fold("set_listen", stringResource(R.string.set_listen), stringResource(when { s.narrator == io.github.graviton94.todaybible.data.Narration.DEVICE -> R.string.narr_device; io.github.graviton94.todaybible.data.Narration.isMale(s.narrator) -> R.string.narr_male; else -> R.string.narr_female }), openSec, spots, { toggle(it) }) {
            Group(stringResource(R.string.guide_settings)) { GuideVoiceSettings(s) }
            Group(stringResource(R.string.keep_title)) { BookButton(stringResource(R.string.keep_open), Modifier.fillMaxWidth(), quiet = true) { s.settingsOpen = false; s.keepOpen = true } }
            }
            Fold("set_write", stringResource(R.string.set_write), stringResource(R.string.set_write_sum), openSec, spots, { toggle(it) }) {
            Group(stringResource(R.string.daily_goal)) {
                GoalChooser(s, title = false)
                // 읽기 계획 (오늘 화면에서 옮겨 옴)
                ChoiceRow(s.plan?.let { planName(androidx.compose.ui.platform.LocalContext.current, it.id) } ?: stringResource(R.string.plan_choose), s.plan != null) { s.planOpen = true }
            }
            Group(stringResource(R.string.owner_name)) {
                NameField(s)
                Text(stringResource(R.string.owner_note), style = Theme.small())
            }
            Group(stringResource(R.string.hand_settings)) {
                // 펜 (손글씨 화면에서 옮겨 옴)
                listOf(io.github.graviton94.todaybible.data.Ink.FOUNTAIN to R.string.pen_fountain, io.github.graviton94.todaybible.data.Ink.BRUSH to R.string.pen_brush, io.github.graviton94.todaybible.data.Ink.PENCIL to R.string.pen_pencil)
                    .forEach { (id, name) -> ChoiceRow(stringResource(name) + if (id != io.github.graviton94.todaybible.data.Ink.FOUNTAIN && s.premiumOn) " · " + stringResource(R.string.premium_tag) else "", s.pen == id) { s.choosePen(id) } }
                ChoiceRow(stringResource(R.string.guide_setting), s.handGuide) { s.flipGuide() }
                ChoiceRow(stringResource(R.string.pen_sound), s.penSound) { s.flipPenSound() }
                // 효과음 (1.5): 보상 순간에만 (무음 · 진동 모드에서는 저절로 조용)
                run { var on by remember { mutableStateOf(s.store.effects) }; ChoiceRow(stringResource(R.string.effects), on) { on = !on; s.store.effects = on; if (on) io.github.graviton94.todaybible.data.Effects.play(ctx0, io.github.graviton94.todaybible.data.Effects.Fx.VERSE) } }
                ChoiceRow(stringResource(R.string.paper_haptic), s.paperHaptic) { s.flipPaperHaptic() }
            }
            }
            // 꾸미기 (1.2): 표지 판화 · 헌사 · 잉크 · 머리글 · 도장 · 앱 아이콘
            Fold("set_deco", stringResource(R.string.set_deco), stringResource(R.string.set_deco_sum), openSec, spots, { toggle(it) }) {
            Group(stringResource(R.string.deco_cover)) { CoverPlatePicker(s) }
            Group(stringResource(R.string.deco_text)) { DedicationField(s) }
            Group(stringResource(R.string.deco_ink)) { InkPicker(s); Text(stringResource(R.string.deco_ink_note), style = Theme.small()) }
            Group(stringResource(R.string.deco_head)) { HeadPicker(s) }
            Group(stringResource(R.string.stamp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    STAMPS.forEach { m ->
                        val on = m == s.stamp
                        Box(
                            Modifier.weight(1f).heightIn(min = Tokens.Size.touch)
                                .drawBehind { if (on) drawLine(c.gilt, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.rule.toPx() * 2) }
                                .chosen(on).clickable(role = Role.RadioButton) { s.setStampMark(m) },
                            contentAlignment = Alignment.Center,
                        ) { StampMark(m, if (on) c.rubric else c.inkSoft, Modifier.size(Tokens.Size.iconMd)) }
                    }
                }
            }
            Group(stringResource(R.string.deco_icon)) { IconPicker(s) }
            Group(stringResource(R.string.deco_widget)) {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                var on by remember { mutableStateOf(s.store.widgetPlate) }
                ChoiceRow(stringResource(R.string.widget_plate), on) { on = !on; s.store.widgetPlate = on; Thread { io.github.graviton94.todaybible.widget.VerseWidget.refresh(ctx) }.start() }
            }
            }
            Fold("set_alerts", stringResource(R.string.set_alerts), if (s.reminderHour >= 0) prayerTime(s.reminderHour * 60 + s.reminderMinute) else stringResource(R.string.reminder_off), openSec, spots, { toggle(it) }) {
            Group(stringResource(R.string.reminder)) {
                // 매일 알림: 끄기 · 켜기, 켜면 정각 아무 시나 (안드로이드 13+ 는 처음 켤 때 알림 허락을 물음)
                val ctx = androidx.compose.ui.platform.LocalContext.current
                var pending by remember { mutableIntStateOf(-1) }
                val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) s.setReminder(pending) else s.toast = ctx.getString(R.string.reminder_denied) }
                fun turnOn(h: Int) { if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) { pending = h; ask.launch(android.Manifest.permission.POST_NOTIFICATIONS) } else s.setReminder(h) }
                val on = s.reminderHour >= 0
                ChoiceRow(stringResource(R.string.turn_on), on) { if (on) s.setReminder(-1) else turnOn(s.store.lastReminderHour) }
                // 시각: 누르면 휠 (오전/오후 · 시 · 분)
                if (on) ListRow(stringResource(R.string.reminder_time), prayerTime(s.reminderHour * 60 + s.reminderMinute)) { s.prayerBell = AppState.MORNING_BELL }
                // 절기 시작 (일 년에 네 번): 따로 켜고 끄지 않고 매일 알림이 그날은 절기 알림으로
                if (on) Text(stringResource(R.string.season_notice_row), style = Theme.small())
            }
            Group(stringResource(R.string.prayer_reminder)) {
                // 기도 알림은 기도문마다 (종): 여기서는 켜진 것만 한눈에
                val on = io.github.graviton94.todaybible.core.Prayers.all.mapNotNull { p -> s.prayerTimes[p.id]?.let { prayerName(p) + " " + prayerTime(it) } }
                Text(if (on.isEmpty()) stringResource(R.string.prayer_reminder_none) else on.joinToString(" · "), style = Theme.small())
                BookButton(stringResource(R.string.prayer_reminder_manage), Modifier.fillMaxWidth(), quiet = true) { s.prayersOpen = true }
            }
            ExactAlarmNote(s)
            }
            Fold("set_keep", stringResource(R.string.set_keep), stringResource(R.string.set_keep_sum), openSec, spots, { toggle(it) }) {
            Group(stringResource(R.string.voice_keep)) {
                Text(stringResource(R.string.voice_keep_hint), style = Theme.small())
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val (voice, photos) = androidx.compose.runtime.remember { io.github.graviton94.todaybible.data.Voice.usage(ctx) }
                fun mb(b: Long) = if (b < 1_000_000) "%.1fMB".format(b / 1_000_000f) else "%.0fMB".format(b / 1_000_000f)
                Text(stringResource(R.string.storage) + " · " + stringResource(R.string.storage_line, mb(voice), mb(photos)), style = Theme.small())
                // 녹음 모두 지우기: 한 번 더 눌러야 지워요
                var sure by remember { mutableStateOf(false) }
                if (voice > 0) BookButton(stringResource(if (sure) R.string.rec_clear_sure else R.string.rec_clear), Modifier.fillMaxWidth(), quiet = true) {
                    if (!sure) sure = true else { java.io.File(ctx.filesDir, "voice").deleteRecursively(); s.voiceRev++; sure = false; s.toast = ctx.getString(R.string.rec_cleared) }
                }
            }
            Group(stringResource(R.string.backup)) {
                Text(stringResource(R.string.backup_hint), style = Theme.small())
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                    if (uri != null) {
                val r = io.github.graviton94.todaybible.data.Backup.read(ctx, uri)
                if (r == null) s.toast = ctx.getString(R.string.backup_bad)
                else {
                    // 다시 그린 뒤에도 보이게 한 줄을 맡겨 둠
                    s.store.pendingToast = if (r.merged) ctx.getString(R.string.backup_merged, r.added) else ctx.getString(R.string.backup_done)
                    (ctx as? android.app.Activity)?.recreate()
                }
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
                        .let { Lang.date(ctx0, R.string.fmt_md_time, it) } else "–"
                    Text(stringResource(R.string.backup_on, last), style = Theme.small().copy(color = c.inkSoft))
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                        BookButton(stringResource(R.string.backup_now), Modifier.weight(1f), quiet = true) { keepNow(where) }
                        BookButton(stringResource(R.string.backup_off), Modifier.weight(1f), quiet = true) { where = ""; s.store.backupUri = "" }
                    }
                }
                BookButton(stringResource(R.string.backup_import), Modifier.fillMaxWidth(), quiet = true) { pick.launch(arrayOf("application/zip", "application/octet-stream")) }
            }
            }
            Fold("set_help", stringResource(R.string.set_help), stringResource(R.string.set_help_sum), openSec, spots, { toggle(it) }) {
            Group(stringResource(R.string.coach_again_h)) {
                BookButton(stringResource(R.string.coach_again), Modifier.fillMaxWidth(), quiet = true) { s.coachReset(); s.settingsOpen = false; s.toast = ctx0.getString(R.string.coach_again_done) }
                BookButton(stringResource(R.string.tour_again), Modifier.fillMaxWidth(), quiet = true) { s.welcomeStep = 0; s.tour = true; s.settingsOpen = false }
                BookButton(stringResource(R.string.news_again), Modifier.fillMaxWidth(), quiet = true) { s.news = true; s.settingsOpen = false }
                Text(stringResource(R.string.help_hint), style = Theme.small())
            }
            Group(stringResource(R.string.feedback)) { Feedback(s) }
            }
            // 평생권은 맨 아래 한 칸 (설정을 열 때마다 먼저 보이지 않게)
            LifetimeCard(s)
            // 맨 아래: 여느 앱처럼 앱 소개 · 개인정보 처리방침 · 출처와 라이선스 · 문의, 판 번호
            Footer(s)
            // 맨 아래 갈래를 열어도 그 머리가 맨 위까지 올라갈 수 있게
            if (openSec != null) androidx.compose.foundation.layout.Spacer(Modifier.height(view * Tokens.Ratio.foldTail))
        }
        // 붙는 머리: 열린 갈래 안을 내려 보는 동안
        stuck?.let { id ->
            Row(Modifier.fillMaxWidth().background(c.leaf).background(c.gilt.copy(alpha = Tokens.Alpha.foldWash))
                .drawBehind { drawLine(c.gilt, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.rule.toPx()) }
                .clickable(role = Role.Button) { val y = spots[id]?.first ?: 0f; openSec = null; scope0.launch { androidx.compose.runtime.withFrameNanos { }; scroll.scrollTo((y + padTop).toInt()) } }
                .padding(horizontal = Tokens.Space.s5).heightIn(min = Tokens.Size.touch), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(FOLD_TITLES.getValue(id)), style = Theme.title(true, Tokens.Text.label), maxLines = 1, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.fold_close) + "  −", style = Theme.small().copy(color = c.inkSoft), maxLines = 1)
            }
        }
        }
    }
    // 언어를 바꾸면 앱을 다시 열어요: 먼저 묻고, 바꾸면 처음부터 새로 (남은 글이 옛 언어로 남지 않게)
    askLang?.let { id ->
        BookSheet({ askLang = null }) {
            Text(stringResource(R.string.lang_confirm_title), style = Theme.title(k))
            Text(stringResource(R.string.lang_confirm_body), style = Theme.body())
            Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                BookButton(stringResource(R.string.cancel), Modifier.weight(1f), quiet = true) { askLang = null }
                BookButton(stringResource(R.string.lang_confirm_go), Modifier.weight(1f)) {
                    askLang = null; s.chooseLanguage(id); (ctx0 as? android.app.Activity)?.let { s.restartApp(it) }
                }
            }
        }
    }
}

/** 매일 알림 시각 고르기. */

private val FOLD_TITLES = mapOf("set_display" to R.string.set_display, "set_listen" to R.string.set_listen, "set_write" to R.string.set_write, "set_deco" to R.string.set_deco,
    "set_alerts" to R.string.set_alerts, "set_keep" to R.string.set_keep, "set_help" to R.string.set_help)

/** 갈래 하나: 머리 + (열렸으면) 내용. 열린 갈래는 옅은 금빛 바탕 + 왼쪽 금선으로 머리부터 끝까지 묶어요. 자리 (위 · 높이) 를 spots 에 남겨요. */
@Composable
private fun Fold(id: String, title: String, summary: String, openSec: String?, spots: MutableMap<String, Pair<Float, Float>>, onToggle: (String) -> Unit, content: @Composable () -> Unit) {
    val c = Theme.c; val open = openSec == id
    Column(Modifier.fillMaxWidth().onGloballyPositioned { spots[id] = it.positionInParent().y to it.size.height.toFloat() }
        .then(if (open) Modifier.background(c.gilt.copy(alpha = Tokens.Alpha.foldWash)).drawBehind { drawRect(c.gilt, Offset.Zero, androidx.compose.ui.geometry.Size(Tokens.Stroke.rule.toPx() * 2, size.height)) }
            .padding(start = Tokens.Space.s4, end = Tokens.Space.s3, bottom = Tokens.Space.s5) else Modifier),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s5)) {
        Section(title, summary, open) { onToggle(id) }
        if (open) content()
    }
}

/** 설정의 큰 갈래 (접힘): 제목 · 지금 고른 것 한 줄 · ›. 누르면 그 갈래만 펼쳐요. */
@Composable
private fun Section(title: String, summary: String, open: Boolean, onClick: () -> Unit) {
    val c = Theme.c
    // 상자 없이 줄 하나: 이름 · 요약 · 아래 가는 선 (열린 칸은 금빛 선)
    Row(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.rowTall).drawBehind { drawLine(if (open) c.gilt else c.hair, Offset(0f, size.height), Offset(size.width, size.height), (if (open) Tokens.Stroke.rule else Tokens.Stroke.hair).toPx()) }
        .clickable(role = Role.Button, onClick = onClick).padding(vertical = Tokens.Space.s3), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Theme.title(true, Tokens.Text.label), maxLines = 1)
            Text(summary, style = Theme.small(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(if (open) "−" else "+", style = Theme.body().copy(color = c.inkSoft))
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
        Modifier.fillMaxWidth().heightIn(min = Tokens.Size.row).chosen(on).clickable(role = Role.RadioButton, onClick = onClick)
            .drawBehind { drawLine(c.hair, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.hair.toPx()) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 긴 이름 (English · King James Version · English narration) 도 잘리지 않게 두 줄까지
        Text(text, style = Theme.body().copy(color = if (on) c.ink else c.inkSoft), maxLines = 3, modifier = Modifier.weight(1f).padding(vertical = Tokens.Space.s2).padding(end = Tokens.Space.s3))
        // 켜짐 · 꺼짐이 늘 보이게: 빈 동그라미 / 붉은 점이 든 동그라미
        Box(Modifier.size(Tokens.Size.iconSm).drawBehind {
            drawCircle(if (on) c.rubric else c.inkSoft, size.minDimension / 2 - Tokens.Stroke.rule.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(Tokens.Stroke.rule.toPx()))
            if (on) drawCircle(c.rubric, size.minDimension / 4)
        })
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
    val guide = remember(s.bibleKorean) { io.github.graviton94.todaybible.data.GuideVoice(ctx, s.bibleKorean, s.guideVoice) }
    androidx.compose.runtime.DisposableEffect(guide) { onDispose { guide.release() } }
    var list by remember(guide) { mutableStateOf<List<android.speech.tts.Voice>>(emptyList()) }
    var ready by remember(guide) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(guide) { guide.whenReady { main.post { list = guide.voices; ready = true } } }
    // 읽는 빠르기 · 큰 글씨 한 줄
    UnderlineTabs(listOf(stringResource(R.string.aloud_slow), stringResource(R.string.aloud_normal), stringResource(R.string.aloud_fast)), s.aloudSpeed) { s.chooseAloudSpeed(it) }
    ChoiceRow(stringResource(R.string.aloud_big_setting), s.aloudBig) { s.flipAloudBig() }
    // 미리 만든 낭독 목소리 (개역한글): 듣기는 받은 권이 있으면 그 음원으로
    val N = io.github.graviton94.todaybible.data.Narration
    // 번역마다 따로 (개역한글 M5 · F5, KJV 는 영어 목소리가 생기면)
    if (N.choices(s.translation).size > 1) {
        N.choices(s.translation).forEach { v ->
            val id = when { v == N.DEVICE -> R.string.narr_device; N.isMale(v) -> R.string.narr_male; else -> R.string.narr_female }
            ChoiceRow(stringResource(id), s.narrator == v) { s.chooseNarrator(v) }
        }
        var player by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
        androidx.compose.runtime.DisposableEffect(Unit) { onDispose { runCatching { player?.release() }; player = null } }
        if (s.narrator != N.DEVICE) BookButton(stringResource(if (player != null) R.string.guide_ai_stop else R.string.narr_preview), Modifier.fillMaxWidth(), quiet = true) {
            player?.let { runCatching { it.release() }; player = null; return@BookButton }
            guide.stop()
            // 견본: 앱에 든 것이 있으면 그것, 없으면 (영어 목소리 등) 시편 23편 음원을 받아서 1절
            val v = s.narrator
            fun play(src: android.media.MediaPlayer.() -> Unit) = runCatching {
                io.github.graviton94.todaybible.data.Sound.player(lift = true, src).apply { setOnCompletionListener { mp -> mp.release(); player = null }; start() }
            }.getOrNull()
            val asset = runCatching { ctx.assets.openFd("voice/${v}_ps23_1.m4a") }.getOrNull()
            if (asset != null) player = play { asset.use { setDataSource(it.fileDescriptor, it.startOffset, it.length) } }
            else Thread {
                val ok = N.fetch(ctx, v, 18, 23)
                main.post { if (ok) player = play { setDataSource(N.file(ctx, v, 18, 23, 1).path) } else s.toast = ctx.getString(R.string.narration_fallback) }
            }.start()
        }
        val used = remember(s.narration) { N.usage(ctx) }
        if (used > 0) Text(stringResource(R.string.narr_usage, "%.0fMB".format(used / 1_000_000f)), style = Theme.small())
    }
    // 폰 목소리는 낭독 목소리를 못 쓸 때만: 그때만 목록을 보여요
    if (s.narrator == N.DEVICE) {
        if (ready && list.isEmpty()) {
            Text(stringResource(R.string.guide_none), style = Theme.small())
            BookButton(stringResource(R.string.guide_install), Modifier.fillMaxWidth(), quiet = true) {
                runCatching { ctx.startActivity(android.content.Intent("com.android.settings.TTS_SETTINGS").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        } else {
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
        }
    }

}

/** 알림이 조금 늦을 수 있음 (정확한 알람 허락 없음): 한 줄 + 설정 열기. 켜진 알림이 있을 때만. */
@Composable
fun ExactAlarmNote(s: AppState) {
    if (s.exactAlarm || (s.reminderHour < 0 && s.prayerTimes.isEmpty())) return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Text(stringResource(R.string.alarm_late), style = Theme.small())
        BookButton(stringResource(R.string.alarm_late_open), Modifier.fillMaxWidth(), quiet = true) { io.github.graviton94.todaybible.data.ExactAlarm.openSettings(ctx) }
    }
}

/** 의견 보내기 (라1): 불편했던 점 · 좋았던 점을 적어 카카오톡 · 문자 등으로. 기기 · 앱 버전이 아래에 붙어요. 서버 없이 나누기 창으로. */
@Composable
private fun Feedback(s: AppState) {
    val c = Theme.c; val ctx = androidx.compose.ui.platform.LocalContext.current
    var bad by remember { mutableStateOf("") }; var good by remember { mutableStateOf("") }
    @Composable fun field(v: String, hint: Int, on: (String) -> Unit) = androidx.compose.foundation.text.BasicTextField(
        value = v, onValueChange = on, textStyle = Theme.body(), cursorBrush = androidx.compose.ui.graphics.SolidColor(c.rubric),
        modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.Size.rowTall).drawBehind { drawLine(c.hair, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.hair.toPx()) }.padding(vertical = Tokens.Space.s3),
        decorationBox = { inner -> Box { if (v.isEmpty()) Text(stringResource(hint), style = Theme.body().copy(color = c.unwritten)); inner() } })
    field(bad, R.string.feedback_bad) { bad = it }
    field(good, R.string.feedback_good) { good = it }
    // 최근 오류 기록이 있으면 함께 보낼지 (기기 안에만 있던 기록)
    val crash = remember { io.github.graviton94.todaybible.data.CrashLog.read(ctx) }
    var withCrash by remember { mutableStateOf(true) }
    if (crash != null) ChoiceRow(stringResource(R.string.feedback_crash), withCrash) { withCrash = !withCrash }
    BookButton(stringResource(R.string.feedback_send), Modifier.fillMaxWidth(), enabled = bad.isNotBlank() || good.isNotBlank() || (crash != null && withCrash)) {
        val info = "${ctx.getString(R.string.app_name)} ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · ${s.translation.name} · ${s.narrator} · ${"%.2f".format(s.scale)}"
        val text = buildString {
            if (bad.isNotBlank()) append(ctx.getString(R.string.feedback_bad_label)).append('\n').append(bad.trim()).append("\n\n")
            if (good.isNotBlank()) append(ctx.getString(R.string.feedback_good_label)).append('\n').append(good.trim()).append("\n\n")
            append(info)
            if (crash != null && withCrash) append("\n\n").append(ctx.getString(R.string.feedback_crash_label)).append('\n').append(crash)
        }
        if (crash != null && withCrash) io.github.graviton94.todaybible.data.CrashLog.clear(ctx)
        ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_SUBJECT, ctx.getString(R.string.feedback_subject)).putExtra(android.content.Intent.EXTRA_TEXT, text), null))
    }
}


/** 설정 맨 아래 한 줄 묶음: 앱 소개 · 개인정보 처리방침 · 출처와 라이선스 · 문의하기, 그 아래 판 · 만든 이. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Footer(s: AppState) {
    val c = Theme.c; val ctx = androidx.compose.ui.platform.LocalContext.current
    var sheet by remember { mutableStateOf(0) }   // 1 앱 소개 · 2 출처와 라이선스
    fun open(url: String) { runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    fun mail() {
        val i = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:" + io.github.graviton94.todaybible.data.Links.CONTACT_EMAIL))
            .putExtra(android.content.Intent.EXTRA_SUBJECT, ctx.getString(R.string.feedback_subject))
        if (runCatching { ctx.startActivity(i) }.isFailure) s.toast = ctx.getString(R.string.export_no_mail)
    }
    Column(Modifier.fillMaxWidth().padding(top = Tokens.Space.s5), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s1, Alignment.CenterHorizontally)) {
            @Composable fun link(text: String, onClick: () -> Unit) = Text(text, style = Theme.small().copy(color = c.ink, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline), maxLines = 1,
                modifier = Modifier.heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Button, onClick = onClick).padding(horizontal = Tokens.Space.s2))
            link(stringResource(R.string.foot_about)) { sheet = 1 }
            link(stringResource(R.string.foot_privacy)) { open(io.github.graviton94.todaybible.data.Links.PRIVACY) }
            link(stringResource(R.string.foot_credits)) { sheet = 2 }
            link(stringResource(R.string.foot_contact)) { mail() }
        }
        Text(stringResource(R.string.foot_line, BuildConfig.VERSION_NAME), style = Theme.small().copy(color = c.inkSoft, textAlign = androidx.compose.ui.text.style.TextAlign.Center))
    }
    if (sheet != 0) BookSheet({ sheet = 0 }) {
        Column(Modifier.heightIn(max = Tokens.Size.sheetMaxGrid * 2).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            if (sheet == 1) {
                Text(stringResource(R.string.foot_about), style = Theme.title(s.korean))
                Text(stringResource(R.string.about_us), style = Theme.body())
                Text(stringResource(R.string.about_contact, io.github.graviton94.todaybible.data.Links.CONTACT_EMAIL), style = Theme.body().copy(color = c.rubric),
                    modifier = Modifier.clickable(role = Role.Button) { mail() })
                Text(io.github.graviton94.todaybible.data.Links.SITE, style = Theme.small().copy(textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline),
                    modifier = Modifier.clickable(role = Role.Button) { open(io.github.graviton94.todaybible.data.Links.SITE) })
            } else {
                Text(stringResource(R.string.foot_credits), style = Theme.title(s.korean))
                listOf(R.string.credit_text to R.string.credit_text_d, R.string.credit_art to R.string.credit_art_d, R.string.credit_ref to R.string.credit_ref_d, R.string.credit_voice to R.string.credit_voice_d,
                    R.string.credit_fonts to R.string.credit_fonts_d, R.string.credit_code to R.string.credit_code_d).forEach { (h, d) ->
                    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                        Text(stringResource(h), style = Theme.label())
                        Text(stringResource(d), style = Theme.small())
                    }
                }
            }
        }
    }
}
