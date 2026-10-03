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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Canon
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.data.ListenService
import io.github.graviton94.todaybible.design.Fonts
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens

/**
 * 듣기 (책 읽어 주기): 장 전체를 한 쪽처럼 펼쳐 두고, 낭독 목소리가 절과 절 사이를 짧게 이어 읽어요.
 * 지금 읽는 절은 먹빛, 나머지는 옅게, 화면은 읽는 곳을 따라가요. 화면을 꺼도 계속 · 장이 끝나면 다음 장.
 * 듣기만 한 절은 채우지 않아요 (필사는 내가 읽어야).
 */
@Composable
fun ListenReader(s: AppState, book: Int, chapter: Int) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    BackHandler { s.listenAt = null }
    val now by ListenService.now.collectAsState()
    // 듣는 중이면 목소리가 있는 곳을 따라가요
    val b = now?.book ?: book; val ch = now?.chapter ?: chapter
    val t = s.text(b)
    val verses = t.fillable(ch)
    val list = rememberLazyListState()
    LaunchedEffect(now?.verse, ch) { now?.verse?.let { v -> verses.indexOf(v).takeIf { it >= 0 }?.let { list.animateScrollToItem((it - 1).coerceAtLeast(0)) } } }
    Column(Modifier.fillMaxSize().background(c.leaf).systemBarsPadding()) {
        Box(Modifier.padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3)) {
            RunningHead(stringResource(R.string.listen_head, s.bookName(b), ch), stringResource(R.string.listen_mode), k)
        }
        LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            item { ChapterInitial(ch, false, Modifier.padding(bottom = Tokens.Space.s1)) }
            items(verses, key = { it }) { v ->
                val here = now?.verse == v
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(fontFamily = Fonts.black, color = c.rubric, fontSize = Tokens.Leading.verseNumber.em)) { append("$v ") }
                    withStyle(SpanStyle(color = if (now == null || here) c.ink else c.inkSoft, background = if (here) c.mark else androidx.compose.ui.graphics.Color.Unspecified)) { append(Markup.plain(t.verse(ch, v))) }
                }, style = Theme.verse(k), modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) {
                    // 누른 절부터 듣기
                    ListenService.start(ctx, b, ch, v, s.aloudRate())
                })
            }
        }
        // 아래: 앞 장 · 듣기/멈추기 · 다음 장
        Row(Modifier.fillMaxWidth().padding(Tokens.Space.s4), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            BookButton(stringResource(R.string.listen_prev), Modifier.weight(1f), quiet = true, enabled = ch > 1) {
                if (now != null) ListenService.start(ctx, b, ch - 1, 1, s.aloudRate()) else s.listenAt = b to ch - 1
            }
            val label = stringResource(if (now != null) R.string.listen_stop else R.string.listen_start)
            Box(Modifier.size(Tokens.Size.emblem).clip(CircleShape).background(if (now != null) c.rubric else c.leather)
                .semantics { contentDescription = label }.clickable(role = Role.Button) {
                    if (now != null) ListenService.stop(ctx) else ListenService.start(ctx, b, ch, verses.firstOrNull() ?: 1, s.aloudRate())
                }, contentAlignment = Alignment.Center) { PlayMark(c.leatherInk, now != null, Modifier.size(Tokens.Size.iconMd)) }
            BookButton(stringResource(R.string.listen_next), Modifier.weight(1f), quiet = true, enabled = ch < Canon.books[b].chapters) {
                if (now != null) ListenService.start(ctx, b, ch + 1, 1, s.aloudRate()) else s.listenAt = b to ch + 1
            }
        }
        Text(stringResource(R.string.listen_hint), style = Theme.small(), modifier = Modifier.padding(start = Tokens.Space.s5, end = Tokens.Space.s5, bottom = Tokens.Space.s3).heightIn(min = Tokens.Size.hiddenField))
    }
}

