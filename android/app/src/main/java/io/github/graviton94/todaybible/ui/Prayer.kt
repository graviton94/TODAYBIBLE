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
 * 기도문 한 편: 위에 기도문 고르는 리본 (누르면 목록 시트), 그 아래 고른 기도. 말씀을 크게, 범위마다 장절. [들으며 기도] [따라 쓰기] [아멘].
 * 모두 성경 본문 그대로예요.
 */
@Composable
fun PrayerPage(s: AppState, id: String) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    val p = Prayers.byId(id) ?: return
    BackHandler { s.prayerOpen = null }
    val now by ListenService.now.collectAsState()
    val origin by ListenService.origin.collectAsState()
    LeaveGuard(s, now != null && origin == "prayer:${p.id}", recording = false, onStop = { ListenService.stop(ctx); s.prayerOpen = null }, onKeep = { s.prayerOpen = null })
    // 기도문 고르기: 위에 리본 한 줄 (지금 기도 ▾), 누르면 목록 시트. 목록이 화면을 차지하지 않게
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
        // 리본: 금빛 띠 + ORATIONES · 지금 기도 ▾ · 몇째 / 모두
        val idx = Prayers.all.indexOfFirst { it.id == p.id } + 1
        val pickLabel = stringResource(R.string.prayer_title)
        Row(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.touch).clip(RoundedCornerShape(Tokens.Radius.chip)).background(c.paper)
            .drawBehind { drawRect(c.gilt, size = androidx.compose.ui.geometry.Size(Tokens.Stroke.rule.toPx() * 2, size.height)) }
            .clickable(role = Role.Button, onClickLabel = pickLabel) { choosing = true }.padding(horizontal = Tokens.Space.s4),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Text("ORATIONES", style = Theme.caps(), maxLines = 1)
            Text(prayerName(p), style = Theme.body(), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("$idx / ${Prayers.all.size}", style = Theme.small().copy(fontFeatureSettings = "lnum"), maxLines = 1)
            Text("▾", style = Theme.body().copy(color = c.giltText))
        }
        Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Text(prayerHead(p), style = Theme.caps(), maxLines = 1)
            Text(prayerName(p), style = Theme.title(k, Tokens.Text.display))
            Text(s.prayerRefShort(p), style = Theme.small(), maxLines = 1)
        }
        p.passages.forEach { ps ->
            val t = s.store.book(s.translation, ps.book)
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Hair()
                Text(Lang.passage(s.store.context, s.uiTr, s.bookName(ps.book), ps.chapter, ps.from, ps.to), style = Theme.small().copy(color = c.giltText), modifier = Modifier.padding(top = Tokens.Space.s2))
                Text(buildAnnotatedString {
                    for (v in ps.from..ps.to) {
                        if (ps.to > ps.from) withStyle(SpanStyle(color = c.rubric, fontSize = Theme.small().fontSize)) { append("$v ") }
                        append(Markup.plain(t.verse(ps.chapter, v))); append(" ")
                    }
                }, style = Theme.verse(s.bibleKorean))
            }
        }
        Text(stringResource(R.string.prayer_note), style = Theme.small())
        val playing = now != null
        Hair()
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            BookButton(stringResource(if (playing) R.string.listen_stop else R.string.prayer_listen), Modifier.weight(1f).coach("prayer_listen"), quiet = true) {
                if (playing) ListenService.stop(ctx) else ListenService.startPassages(ctx, p.passages, s.aloudRate(), "prayer:${p.id}")
            }
            BookButton(stringResource(R.string.prayer_write), Modifier.weight(1f).coach("prayer_write"), quiet = true) { s.writePrayer(p.id) }
        }
        BookButton(stringResource(if (s.prayed(p.id)) R.string.prayer_prayed else R.string.prayer_amen), Modifier.fillMaxWidth().coach("prayer_amen"), quiet = s.prayed(p.id)) {
            s.markPrayed(p.id); s.prayerOpen = null
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
