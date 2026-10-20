package io.github.graviton94.todaybible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.core.Prayer
import io.github.graviton94.todaybible.core.Prayers
import io.github.graviton94.todaybible.data.ListenService
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens

/** 기도문 이름 (strings.json 의 prayer_아이디). */
@Composable
fun prayerName(p: Prayer): String {
    val ctx = LocalContext.current
    return stringResource(ctx.resources.getIdentifier("prayer_${p.id}", "string", ctx.packageName))
}

/** 기도문의 말씀 표기: 범위들을 · 로 이어서. */
/** 짧게: 첫 말씀 하나 (+ 외 n). */
fun AppState.prayerRefShort(p: Prayer): String {
    val f = p.passages.firstOrNull() ?: return ""
    val one = Lang.passage(store.context, uiTr, bookName(f.book), f.chapter, f.from, f.to)
    return if (p.passages.size > 1) store.context.getString(io.github.graviton94.todaybible.R.string.prayer_ref_more, one, p.passages.size - 1) else one
}
fun AppState.prayerRefs(p: Prayer): String = p.passages.joinToString(" · ") { Lang.passage(store.context, uiTr, bookName(it.book), it.chapter, it.from, it.to) }

/** 기도의 때 머리글 (라틴 성무일과 이름). */
fun prayerHead(p: Prayer): String = when (p.hour) {
    io.github.graviton94.todaybible.core.Hour.MORNING -> "LAUDES · MATUTINUM"
    io.github.graviton94.todaybible.core.Hour.NOON -> "SEXTA · MERIDIES"
    io.github.graviton94.todaybible.core.Hour.EVENING -> "VESPERAE"
    io.github.graviton94.todaybible.core.Hour.NIGHT -> "COMPLETORIUM"
    null -> "ORATIO"
}

/**
 * 기도문 한 편: 위에 짙은 리본 「기도문 목록」 (누르면 목록 시트), 제목 · 어느 말씀에서 · 드리는 방법 셋 (들으며 기도 · 교독 · 낭독), 줄마다 기도, [아멘].
 * 기도문은 그 말씀으로 드리는 기도 (assets/prayers.json). 방법 하나를 끝까지 하면 오늘 드린 것으로 남아요.
 */
@Composable
fun PrayerPage(s: AppState, id: String) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    val p = Prayers.byId(id) ?: return
    BackHandler { s.prayerOpen = null }
    val now by ListenService.now.collectAsState()
    val origin by ListenService.origin.collectAsState()
    val heard by ListenService.line.collectAsState()
    val finished by ListenService.finished.collectAsState()
    val mine = origin == "prayer:${p.id}"
    LeaveGuard(s, now != null && mine, recording = false, onStop = { ListenService.stop(ctx); s.prayerOpen = null }, onKeep = { s.prayerOpen = null })
    val lines = androidx.compose.runtime.remember(p.id, k) { io.github.graviton94.todaybible.data.PrayerText.lines(ctx, p.id, k) }
    val name = prayerName(p)
    // 드리는 방법: 0 들으며 기도 · 1 교독 · 2 낭독 (낭독은 소리 없이 줄을 밝히며 기다려요)
    var mode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var selfAt by androidx.compose.runtime.remember(p.id) { androidx.compose.runtime.mutableIntStateOf(-1) }
    val listening = now != null && mine
    val at = if (selfAt >= 0) selfAt else if (listening) heard else -1
    fun stopAll() { if (listening) ListenService.stop(ctx); selfAt = -1 }
    fun begin(m: Int) {
        stopAll(); mode = m
        if (m == 2) { selfAt = 0; return }
        val male = io.github.graviton94.todaybible.data.Narration.isMale(s.narrator) || s.narrator == io.github.graviton94.todaybible.data.Narration.DEVICE
        ListenService.startPrayer(ctx, p.id, name, lines, io.github.graviton94.todaybible.data.PrayerText.voices(ctx, p.id, k, male), alternate = m == 1, korean = k, rate = s.aloudRate())
    }
    // 낭독: 줄마다 읽을 만큼 밝혀 두고 다음 줄로, 끝까지 가면 드린 것으로
    androidx.compose.runtime.LaunchedEffect(selfAt) {
        if (selfAt < 0) return@LaunchedEffect
        if (selfAt >= lines.size) { selfAt = -1; s.markPrayed(p.id); s.toast = ctx.getString(R.string.prayer_prayed); return@LaunchedEffect }
        kotlinx.coroutines.delay(io.github.graviton94.todaybible.data.PrayerText.readMs(lines[selfAt], k, s.aloudRate()))
        selfAt += 1
    }
    // 들으며 기도 · 교독을 끝까지 마쳤으면 (화면을 떠나 있었어도 서비스가 오늘 기도로 남겨 둠)
    androidx.compose.runtime.LaunchedEffect(finished) { if (finished == "prayer:${p.id}") { s.markPrayed(p.id); s.toast = ctx.getString(R.string.prayer_prayed); ListenService.clearFinished() } }
    var choosing by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().background(c.leaf).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.prayer_back), style = Theme.small().copy(color = c.inkSoft), maxLines = 1,
                modifier = Modifier.weight(1f).heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Button) { s.prayerOpen = null })
            PrayerBell(s, p.id, Modifier.coach("prayer_bell"), big = true)
            HelpButton(s, "prayer")
        }
        // 리본: 짙은 띠 · ORATIONES · 기도문 목록 · 몇째 / 모두 ▾ (지금 기도 이름은 아래 제목에)
        val idx = Prayers.all.indexOfFirst { it.id == p.id } + 1
        val pickLabel = stringResource(R.string.prayer_ribbon)
        Row(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.touch).clip(RoundedCornerShape(Tokens.Radius.chip)).background(c.ebony)
            .clickable(role = Role.Button, onClickLabel = pickLabel) { choosing = true }.padding(horizontal = Tokens.Space.s4),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Text("ORATIONES", style = Theme.caps().copy(color = c.foil), maxLines = 1)
            Text(pickLabel, style = Theme.body().copy(color = c.ebonyInk), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("$idx / ${Prayers.all.size}", style = Theme.small().copy(color = c.ebonyInk, fontFeatureSettings = "lnum"), maxLines = 1)
            Text("▾", style = Theme.body().copy(color = c.foil))
        }
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Text(prayerHead(p), style = Theme.caps(), maxLines = 1)
            Text(name, style = Theme.title(k, Tokens.Text.display))
            Text(stringResource(R.string.prayer_from, s.prayerRefs(p)), style = Theme.small(), maxLines = 2)
        }
        // 드리는 방법 셋: 한 줄 · 고르면 바로 시작, 하는 중이면 아래에 몇째 줄 · 멈추기
        val running = at >= 0
        Row(Modifier.fillMaxWidth().coach("prayer_listen").border(Tokens.Stroke.hair, c.hair)) {
            listOf(stringResource(R.string.prayer_listen), stringResource(R.string.prayer_mode_alt), stringResource(R.string.prayer_mode_self)).forEachIndexed { i, label ->
                val on = running && mode == i
                Box(Modifier.weight(1f).heightIn(min = Tokens.Size.touch).background(if (on) c.leather else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable(role = Role.Button) { if (on) stopAll() else begin(i) }, contentAlignment = Alignment.Center) {
                    Text(label, style = Theme.small().copy(color = if (on) c.leatherInk else c.ink), maxLines = 1)
                }
            }
        }
        if (running) Text(stringResource(R.string.prayer_running, (at + 1).coerceAtMost(lines.size), lines.size) + if (mode == 1) " · " + stringResource(R.string.prayer_alt_hint) else if (mode == 2) " · " + stringResource(R.string.prayer_self_hint) else "",
            style = Theme.small().copy(color = c.rubric), modifier = Modifier.clickable(role = Role.Button) { stopAll() })
        Hair()
        // 줄마다: 지금 줄은 금빛 바탕, 교독이면 내 줄 (짝수째) 금빛 글씨
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            lines.forEachIndexed { i, line ->
                val here = i == at
                val me = mode == 1 && i % 2 == 1
                Text(line, style = Theme.body().copy(fontSize = Tokens.Text.verse, color = if (me) c.giltText else c.ink),
                    modifier = Modifier.fillMaxWidth().then(if (here) Modifier.background(c.mark) else Modifier).padding(horizontal = Tokens.Space.s1))
            }
        }
        Text(stringResource(R.string.prayer_note), style = Theme.small())
        BookButton(stringResource(if (s.prayed(p.id)) R.string.prayer_prayed else R.string.prayer_amen), Modifier.fillMaxWidth().coach("prayer_amen"), quiet = s.prayed(p.id)) {
            stopAll(); s.markPrayed(p.id); s.prayerOpen = null
        }
    }
    if (choosing) BookSheet({ choosing = false }) {
        Column(Modifier.heightIn(max = Tokens.Size.sheetMaxGrid * 2).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Text("ORATIONES", style = Theme.caps(), maxLines = 1)
            Text(stringResource(R.string.prayer_title), style = Theme.title(k, Tokens.Text.display))
            PrayerList(s, current = p.id) { choosing = false }
        }
    }
    }
}

/** 기도문 목록: 이름 · 장절 · 오늘 드렸으면 금빛. */
@Composable
fun PrayerList(s: AppState, current: String? = null, onPick: () -> Unit = {}) {
    val c = Theme.c
    Prayers.all.forEach { p ->
        Hair()
        val here = p.id == current
        Row(Modifier.fillMaxWidth().then(if (here) Modifier.drawBehind { drawRect(c.gilt, size = androidx.compose.ui.geometry.Size(Tokens.Stroke.rule.toPx() * 2, size.height)) } else Modifier)
            .chosen(here).padding(vertical = Tokens.Space.s1, horizontal = if (here) Tokens.Space.s2 else 0.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).heightIn(min = Tokens.Size.row).clickable(role = Role.Button) { s.prayersOpen = false; s.prayerOpen = p.id; onPick() }.padding(vertical = Tokens.Space.s1),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Text(prayerName(p) + if (s.prayed(p.id)) " · " + stringResource(R.string.prayer_prayed_short) else "", style = Theme.body().copy(color = if (here || s.prayed(p.id)) c.giltText else c.ink), maxLines = 2)
                Text(s.prayerRefShort(p), style = Theme.small(), maxLines = 1)
            }
            PrayerBell(s, p.id)
        }
    }
    Hair()
}

/** 서재의 기도문 칸 (누르면 목록 시트). */
@Composable
fun PrayersSheet(s: AppState) {
    BookSheet({ s.prayersOpen = false }) {
        Column(Modifier.heightIn(max = Tokens.Size.sheetMaxGrid * 2).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Text("ORATIONES", style = Theme.caps(), maxLines = 1)
            Text(stringResource(R.string.prayer_title), style = Theme.title(s.korean, Tokens.Text.display))
            Text(stringResource(R.string.prayer_list_hint), style = Theme.small())
            PrayerList(s)
        }
    }
}


/** 기도 알림 시각 글 (폰 · 앱 언어의 짧은 시각). */
@Composable
fun prayerTime(minutes: Int): String = java.time.LocalTime.of(minutes / 60, minutes % 60)
    .format(java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT).withLocale(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]))

/** 기도문 옆 종: 켜졌으면 붉은 종 + 시각, 누르면 시각 고르는 창. */
@Composable
fun PrayerBell(s: AppState, id: String, modifier: Modifier = Modifier, big: Boolean = false) {
    val c = Theme.c
    val at = s.prayerTimes[id]
    val label = stringResource(R.string.prayer_bell)
    // big: 기도문 머리의 알림 단추 (테두리 있는 넓은 칩, 꺼져 있으면 ‘알림’ 글자도)
    Row(modifier.heightIn(min = Tokens.Size.touch).clip(RoundedCornerShape(Tokens.Radius.chip))
        .then(if (big) Modifier.border(Tokens.Stroke.hair, if (at != null) c.rubric else c.hair, RoundedCornerShape(Tokens.Radius.chip)) else Modifier)
        .clickable(role = Role.Button) { s.prayerBell = id }
        .semantics { contentDescription = label }.padding(horizontal = if (big) Tokens.Space.s3 else Tokens.Space.s2),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (big) Tokens.Space.s2 else Tokens.Space.s1)) {
        BellMark(if (at != null) c.rubric else c.inkSoft, at != null, Modifier.size(if (big) Tokens.Size.icon else Tokens.Size.iconSm))
        if (at != null) Text(prayerTime(at), style = (if (big) Theme.body() else Theme.small()).copy(color = c.rubric), maxLines = 1)
        else if (big) Text(label, style = Theme.body().copy(color = c.inkSoft), maxLines = 1)
    }
}

/** 기도 알림 정하기: 시각 휠 하나 (오전/오후 · 시 · 분) + 끄기 / 저장. 켤 때 알림 허락이 없으면 먼저 물어요. */
@Composable
fun PrayerBellSheet(s: AppState, id: String) {
    val ctx = LocalContext.current
    val p = Prayers.byId(id) ?: return
    val at = s.prayerTimes[id]
    var want by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) want?.let { s.setPrayerTime(id, it) } else s.toast = ctx.getString(R.string.reminder_denied)
    }
    fun choose(m: Int) {
        if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) { want = m; ask.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
        else s.setPrayerTime(id, m)
    }
    // 처음엔 이 기도의 때에 맞춘 시각
    val first = at ?: when (p.hour) {
        io.github.graviton94.todaybible.core.Hour.MORNING -> 6 * 60
        io.github.graviton94.todaybible.core.Hour.NOON -> 12 * 60
        io.github.graviton94.todaybible.core.Hour.EVENING -> 18 * 60
        io.github.graviton94.todaybible.core.Hour.NIGHT -> 22 * 60
        null -> 7 * 60
    }
    var pick by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(first) }
    BookSheet({ s.prayerBell = null }) {
        Text("HORA ORANDI", style = Theme.caps(), maxLines = 1)
        Text(stringResource(R.string.prayer_bell_h, prayerName(p)), style = Theme.title(s.korean))
        TimeWheel(first, s.korean) { pick = it }
        Text(stringResource(R.string.wheel_say, prayerTime(pick)), style = Theme.small(), modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(stringResource(R.string.prayer_bell_note), style = Theme.small())
        ExactAlarmNote(s)
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            BookButton(stringResource(R.string.reminder_off), Modifier.weight(1f), quiet = true) { s.setPrayerTime(id, null); s.prayerBell = null }
            BookButton(stringResource(R.string.wheel_save), Modifier.weight(1f)) { choose(pick); s.prayerBell = null }
        }
    }
}

/** 매일 알림 시각: 기도 알림과 같은 휠. */
@Composable
fun MorningBellSheet(s: AppState) {
    val ctx = LocalContext.current
    var want by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Int?>(null) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) want?.let { s.setReminder(it / 60, it % 60) } else s.toast = ctx.getString(R.string.reminder_denied)
    }
    val first = (if (s.reminderHour >= 0) s.reminderHour else s.store.lastReminderHour) * 60 + s.reminderMinute
    var pick by androidx.compose.runtime.remember { androidx.compose.runtime.mutableIntStateOf(first) }
    BookSheet({ s.prayerBell = null }) {
        Text("HORA MATUTINA", style = Theme.caps(), maxLines = 1)
        Text(stringResource(R.string.reminder_on), style = Theme.title(s.korean))
        TimeWheel(first, s.korean) { pick = it }
        Text(stringResource(R.string.wheel_say, prayerTime(pick)), style = Theme.small(), modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            BookButton(stringResource(R.string.reminder_off), Modifier.weight(1f), quiet = true) { s.setReminder(-1); s.prayerBell = null }
            BookButton(stringResource(R.string.wheel_save), Modifier.weight(1f)) {
                if (android.os.Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) { want = pick; ask.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                else s.setReminder(pick / 60, pick % 60)
                s.prayerBell = null
            }
        }
    }
}
