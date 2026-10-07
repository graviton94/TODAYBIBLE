package io.github.graviton94.todaybible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
fun AppState.prayerRefs(p: Prayer): String = p.passages.joinToString(" · ") { Lang.passage(store.context, translation, bookName(it.book), it.chapter, it.from, it.to) }

/** 오늘 화면의 기도 칸: 지금 때의 기도 (아침 · 낮 · 저녁 · 밤). 드렸으면 금빛 한 줄. */
@Composable
fun PrayerCard(s: AppState) {
    val c = Theme.c
    val p = Prayers.forHour(Prayers.hourAt(java.time.LocalTime.now().hour))
    val first = p.passages.first()
    val prayed = s.prayed(p.id)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.card)).background(c.paper).clickable(role = Role.Button) { s.prayerOpen = p.id }.padding(Tokens.Space.s4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
        Text(prayerName(p), style = Theme.small().copy(color = c.rubric), maxLines = 1)
        Text(Markup.plain(s.store.book(s.translation, first.book).verse(first.chapter, first.from)), style = Theme.verse(s.korean), maxLines = 3, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(if (prayed) R.string.prayer_prayed else R.string.prayer_go), style = Theme.small().copy(color = if (prayed) c.giltText else c.rubric), maxLines = 1, modifier = Modifier.weight(1f))
            // 다른 기도문들로 (주기도문 · 아론의 축복 …)
            Text(stringResource(R.string.prayer_all), style = Theme.small().copy(color = c.rubric), maxLines = 1,
                modifier = Modifier.heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Button) { s.prayersOpen = true })
        }
    }
}

/**
 * 기도문 한 편: 말씀을 크게, 범위마다 장절. [들으며 기도] [따라 쓰기] [아멘].
 * 아래에 다른 기도문들. 모두 성경 본문 그대로예요.
 */
@Composable
fun PrayerPage(s: AppState, id: String) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    val p = Prayers.byId(id) ?: return
    BackHandler { s.prayerOpen = null }
    val now by ListenService.now.collectAsState()
    val origin by ListenService.origin.collectAsState()
    LeaveGuard(s, now != null && origin == "prayer:${p.id}", recording = false, onStop = { ListenService.stop(ctx); s.prayerOpen = null }, onKeep = { s.prayerOpen = null })
    Column(Modifier.fillMaxSize().background(c.leaf).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
        Text(stringResource(R.string.prayer_back), style = Theme.small().copy(color = c.rubric), maxLines = 1,
            modifier = Modifier.heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Button) { s.prayerOpen = null })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(prayerName(p), style = Theme.title(k, Tokens.Text.title), modifier = Modifier.weight(1f))
            PrayerBell(s, p.id, Modifier.coach("prayer_bell"))
            HelpButton(s, "prayer")
        }
        p.passages.forEach { ps ->
            val t = s.store.book(s.translation, ps.book)
            Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                Text(Lang.passage(s.store.context, s.translation, s.bookName(ps.book), ps.chapter, ps.from, ps.to), style = Theme.small().copy(color = c.rubric))
                Text(buildAnnotatedString {
                    for (v in ps.from..ps.to) {
                        if (ps.to > ps.from) withStyle(SpanStyle(color = c.rubric, fontSize = Theme.small().fontSize)) { append("$v ") }
                        append(Markup.plain(t.verse(ps.chapter, v))); append(" ")
                    }
                }, style = Theme.verse(k))
            }
        }
        Text(stringResource(R.string.prayer_note), style = Theme.small())
        val playing = now != null
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            BookButton(stringResource(if (playing) R.string.listen_stop else R.string.prayer_listen), Modifier.weight(1f).coach("prayer_listen")) {
                if (playing) ListenService.stop(ctx) else ListenService.startPassages(ctx, p.passages, s.aloudRate(), "prayer:${p.id}")
            }
            BookButton(stringResource(R.string.prayer_write), Modifier.weight(1f).coach("prayer_write"), quiet = true) { s.writePrayer(p.id) }
        }
        BookButton(stringResource(if (s.prayed(p.id)) R.string.prayer_prayed else R.string.prayer_amen), Modifier.fillMaxWidth().coach("prayer_amen"), quiet = s.prayed(p.id)) {
            s.markPrayed(p.id); s.prayerOpen = null
        }
        // 다른 기도문
        Text(stringResource(R.string.prayer_title), style = Theme.title(k), modifier = Modifier.padding(top = Tokens.Space.s3))
        PrayerList(s, except = p.id)
    }
}

/** 기도문 목록: 이름 · 장절 · 오늘 드렸으면 금빛. */
@Composable
fun PrayerList(s: AppState, except: String? = null) {
    val c = Theme.c
    Prayers.all.filter { it.id != except }.forEach { p ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).heightIn(min = Tokens.Size.row).clickable(role = Role.Button) { s.prayersOpen = false; s.prayerOpen = p.id }.padding(vertical = Tokens.Space.s1),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Text(prayerName(p) + if (s.prayed(p.id)) " · " + stringResource(R.string.prayer_prayed_short) else "", style = Theme.label().copy(color = if (s.prayed(p.id)) c.giltText else c.ink), maxLines = 2)
                Text(s.prayerRefs(p), style = Theme.small(), maxLines = 2)
            }
            PrayerBell(s, p.id)
        }
    }
}

/** 서재의 기도문 칸 (누르면 목록 시트). */
@Composable
fun PrayersSheet(s: AppState) {
    BookSheet({ s.prayersOpen = false }) {
        Column(Modifier.heightIn(max = Tokens.Size.sheetMaxGrid * 2).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Text(stringResource(R.string.prayer_title), style = Theme.title(s.korean))
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
fun PrayerBell(s: AppState, id: String, modifier: Modifier = Modifier) {
    val c = Theme.c; val ctx = LocalContext.current
    val at = s.prayerTimes[id]
    val label = stringResource(R.string.prayer_bell)
    Row(modifier.heightIn(min = Tokens.Size.touch).clip(RoundedCornerShape(Tokens.Radius.chip)).clickable(role = Role.Button) { s.prayerBell = id }
        .semantics { contentDescription = label }.padding(horizontal = Tokens.Space.s2),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
        BellMark(if (at != null) c.rubric else c.inkSoft, at != null, Modifier.size(Tokens.Size.iconSm))
        if (at != null) Text(prayerTime(at), style = Theme.small().copy(color = c.rubric), maxLines = 1)
    }
}

/** 기도 알림 정하기: 끄기 / 자주 쓰는 시각 / 다른 시각 (시계). 켤 때 알림 허락이 없으면 먼저 물어요. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PrayerBellSheet(s: AppState, id: String) {
    val c = Theme.c; val ctx = LocalContext.current
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
    BookSheet({ s.prayerBell = null }) {
        Text(stringResource(R.string.prayer_bell_h, prayerName(p)), style = Theme.title(s.korean))
        Text(stringResource(R.string.prayer_bell_note), style = Theme.small())
        ExactAlarmNote(s)
        // 이 기도의 때에 맞춘 시각을 앞에
        val presets = when (p.hour) {
            io.github.graviton94.todaybible.core.Hour.MORNING -> listOf(5 * 60, 5 * 60 + 30, 6 * 60, 6 * 60 + 30, 7 * 60, 7 * 60 + 30, 8 * 60, 9 * 60)
            io.github.graviton94.todaybible.core.Hour.NOON -> listOf(11 * 60 + 30, 12 * 60, 12 * 60 + 30, 13 * 60)
            io.github.graviton94.todaybible.core.Hour.EVENING -> listOf(17 * 60, 18 * 60, 18 * 60 + 30, 19 * 60, 20 * 60)
            io.github.graviton94.todaybible.core.Hour.NIGHT -> listOf(21 * 60, 21 * 60 + 30, 22 * 60, 22 * 60 + 30, 23 * 60)
            null -> listOf(6 * 60, 7 * 60, 12 * 60, 18 * 60, 21 * 60, 22 * 60)
        }
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            BookButton(stringResource(R.string.reminder_off), quiet = at != null) { s.setPrayerTime(id, null); s.prayerBell = null }
            (presets + listOfNotNull(at?.takeIf { it !in presets })).forEach { m ->
                BookButton(prayerTime(m), quiet = m != at) { choose(m); s.prayerBell = null }
            }
        }
        BookButton(stringResource(R.string.prayer_bell_other), Modifier.fillMaxWidth(), quiet = true) {
            val start = at ?: presets.first()
            android.app.TimePickerDialog(ctx, { _, h, mi -> choose(h * 60 + mi); s.prayerBell = null }, start / 60, start % 60, android.text.format.DateFormat.is24HourFormat(ctx)).show()
        }
    }
}
