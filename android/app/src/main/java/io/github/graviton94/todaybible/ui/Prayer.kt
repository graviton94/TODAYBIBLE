package io.github.graviton94.todaybible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
        Text(stringResource(if (prayed) R.string.prayer_prayed else R.string.prayer_go), style = Theme.small().copy(color = if (prayed) c.giltText else c.rubric), maxLines = 1)
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
    Column(Modifier.fillMaxSize().background(c.leaf).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4),
        verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
        Text(stringResource(R.string.prayer_back), style = Theme.small().copy(color = c.rubric), maxLines = 1,
            modifier = Modifier.heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Button) { s.prayerOpen = null })
        Text(prayerName(p), style = Theme.title(k, Tokens.Text.title))
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
            BookButton(stringResource(if (playing) R.string.listen_stop else R.string.prayer_listen), Modifier.weight(1f)) {
                if (playing) ListenService.stop(ctx) else ListenService.startPassages(ctx, p.passages, s.aloudRate())
            }
            BookButton(stringResource(R.string.prayer_write), Modifier.weight(1f), quiet = true) { s.writePrayer(p.id) }
        }
        BookButton(stringResource(if (s.prayed(p.id)) R.string.prayer_prayed else R.string.prayer_amen), Modifier.fillMaxWidth(), quiet = s.prayed(p.id)) {
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
        Column(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.row).clickable(role = Role.Button) { s.prayerOpen = p.id }.padding(vertical = Tokens.Space.s1),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
            Text(prayerName(p) + if (s.prayed(p.id)) " · " + stringResource(R.string.prayer_prayed_short) else "", style = Theme.label().copy(color = if (s.prayed(p.id)) c.giltText else c.ink), maxLines = 1)
            Text(s.prayerRefs(p), style = Theme.small(), maxLines = 2)
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

