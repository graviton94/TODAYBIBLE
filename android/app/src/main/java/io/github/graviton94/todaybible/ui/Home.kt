package io.github.graviton94.todaybible.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Feasts
import io.github.graviton94.todaybible.core.Goal
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.core.Pieces
import io.github.graviton94.todaybible.core.Presence
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import java.time.temporal.ChronoUnit




/**
 * 간단 모드 (F5): 자녀가 부모님 폰에 켜 드리기 좋게. 오늘의 장 하나와 큰 버튼 셋만.
 * 소리 내어 읽기 · 듣기 · 손으로 쓰기 (타자보다 쉬운 쪽). 기록 · 화첩은 아래 이름표로만.
 */
@Composable
private fun SimpleHome(s: AppState) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    val (b, ch, _) = s.todayPlace()
    val t = s.store.book(s.translation, b)
    val fill = t.fillable(ch); val done = fill.count { s.progress.isFilled(s.translation, io.github.graviton94.todaybible.core.VerseKey(b, ch, it)) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s5)) {
        HeadText(s, b, ch)
        Text(stringResource(R.string.listen_head, s.bookName(b), ch), style = Theme.title(k, Tokens.Text.display))
        Text(stringResource(R.string.ch_progress, done, fill.size) + if (s.goalMet()) " · " + stringResource(R.string.goal_done) else "", style = Theme.body().copy(color = c.inkSoft))
        BigAction(stringResource(R.string.simple_aloud)) { s.store.copyTab = 0; s.open(b, ch) }
        BigAction(stringResource(R.string.simple_listen), primary = false) { s.read(b, ch); if (s.locked(b)) return@BigAction; io.github.graviton94.todaybible.data.ListenService.start(ctx, b, ch, s.progress.nextVerse(s.translation, t, ch) ?: 1, s.aloudRate()) }
        BigAction(stringResource(R.string.simple_write), primary = false) { s.store.copyTab = 2; s.open(b, ch) }
        WeekStamps(s)
    }
}

@Composable
private fun BigAction(text: String, primary: Boolean = true, onClick: () -> Unit) {
    val c = Theme.c
    // 하나만 채우고 나머지는 가는 테 (크기는 그대로 커서 누르기 쉬움)
    Box(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.bigAction).then(if (primary) Modifier.background(c.leather) else Modifier.border(Tokens.Stroke.hair, c.ink)).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center) { Text(text, style = Theme.title(true).copy(color = if (primary) c.leatherInk else c.ink)) }
}

/** 나의 순례길 한 줄을 눌렀을 때: 이어 쓰기 · 새로 쓰기 (한 번 더 확인) · 빼기 (한 번 더 확인). */
@Composable
fun PilgrimSheet(s: AppState, p: AppState.Paused, onClose: () -> Unit) {
    val c = Theme.c; val k = s.korean
    var ask by remember { mutableStateOf(s.debugPilgrimAsk.also { s.debugPilgrimAsk = 0 }) }   // 0 고르기 · 1 새로 쓰기 확인 · 2 빼기 확인
    val ref = s.chapterRef(p.book, p.chapter)
    BookSheet(onClose) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            when (ask) {
                0 -> {
                    Text("PEREGRINATIO", style = Theme.caps(), maxLines = 1)
                    Text(ref, style = Theme.title(k), maxLines = 1)
                    @Composable fun opt(title: String, sub: String, warn: Boolean = false, onClick: () -> Unit) =
                        Column(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.rowTall).clickable(role = Role.Button, onClick = onClick)
                            .drawBehind { drawLine(c.hair, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.hair.toPx()) }
                            .padding(vertical = Tokens.Space.s3), verticalArrangement = Arrangement.Center) {
                            Text(title, style = Theme.label().copy(color = if (warn) c.wrong else c.ink, fontWeight = FontWeight.SemiBold), maxLines = 1)
                            Text(sub, style = Theme.small(), maxLines = 2)
                        }
                    opt(stringResource(R.string.pil_continue), stringResource(R.string.pil_continue_sub, p.next)) { onClose(); s.open(p.book, p.chapter) }
                    opt(stringResource(R.string.pil_restart), stringResource(R.string.pil_restart_sub)) { ask = 1 }
                    opt(stringResource(R.string.pil_remove), stringResource(R.string.pil_remove_sub)) { ask = 2 }
                }
                1 -> {
                    Text(stringResource(R.string.pil_restart_h, ref), style = Theme.title(k))
                    Text(stringResource(R.string.pil_restart_body, p.done), style = Theme.body())
                    Text(stringResource(R.string.pil_restart_warn), style = Theme.small().copy(color = c.wrong))
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                        BookButton(stringResource(R.string.pil_keep), Modifier.weight(1f), quiet = true) { onClose() }
                        BookButton(stringResource(R.string.pil_restart_go), Modifier.weight(1f)) { s.restartChapter(p.book, p.chapter); onClose(); s.open(p.book, p.chapter) }
                    }
                }
                else -> {
                    Text(stringResource(R.string.pil_remove_h), style = Theme.title(k))
                    Text(stringResource(R.string.pil_remove_body, ref, p.done, s.bookName(p.book), p.chapter, p.next), style = Theme.body())
                    Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                        BookButton(stringResource(R.string.pil_keep), Modifier.weight(1f), quiet = true) { onClose() }
                        BookButton(stringResource(R.string.pil_remove_go), Modifier.weight(1f)) { s.hidePaused(p.book, p.chapter); onClose() }
                    }
                }
            }
        }
    }
}
