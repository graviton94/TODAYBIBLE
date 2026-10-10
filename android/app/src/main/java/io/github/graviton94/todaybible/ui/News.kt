package io.github.graviton94.todaybible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import io.github.graviton94.todaybible.BuildConfig
import io.github.graviton94.todaybible.MainActivity
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.data.Updates
import io.github.graviton94.todaybible.design.LocalPalette
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens

/**
 * 새로 바뀐 것 (업데이트 뒤 처음 한 번 · 설정에서 다시): 판화 한 점 아래 머리글 · 바뀐 것 몇 줄 · 크림색 버튼 하나.
 * 글은 언어마다 (news_body, 한 줄에 하나).
 */
@Composable
fun NewsScreen(s: AppState) {
    CompositionLocalProvider(LocalPalette provides Tokens.dark) {
        val c = Theme.c; val k = s.korean
        fun done() { s.news = false; s.store.newsSeen = MainActivity.NEWS }
        BackHandler { done() }
        val img = rememberPlate("creation")
        val lines = stringResource(R.string.news_body).split('\n').filter { it.isNotBlank() }
        Box(Modifier.fillMaxSize().background(c.leaf).clickable(remember { MutableInteractionSource() }, null) {}) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (img != null) Image(img, null, Modifier.fillMaxWidth().aspectRatio(Tokens.Ratio.newsArt).drawWithContent {
                    drawContent()
                    drawRect(Brush.verticalGradient(0.45f to c.leaf.copy(alpha = 0f), 1f to c.leaf))
                }, contentScale = ContentScale.Crop)
                else Box(Modifier.fillMaxWidth().systemBarsPadding().height(Tokens.Space.s6))
                Column(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    Text("NOVA · ${BuildConfig.VERSION_NAME.substringBefore('-')}", style = Theme.caps(), maxLines = 1)
                    Text(stringResource(R.string.news_title), style = Theme.title(k, Tokens.Text.display), maxLines = 2)
                    Column(Modifier.fillMaxWidth().padding(top = Tokens.Space.s3)) {
                        lines.forEachIndexed { i, line ->
                            Hair()
                            Row(Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s3), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                                Text(io.github.graviton94.todaybible.core.Latin.roman(i + 1), style = Theme.caps(), modifier = Modifier.width(Tokens.Size.romanCol))
                                Text(line, style = Theme.body().copy(color = c.ink), modifier = Modifier.weight(1f))
                            }
                        }
                        Hair()
                    }
                    Column(Modifier.fillMaxWidth().padding(top = Tokens.Space.s4, bottom = Tokens.Space.s5).systemBarsPadding()) {
                        BookButton(stringResource(R.string.news_ok), Modifier.fillMaxWidth()) { done() }
                    }
                }
            }
        }
    }
}

/** 새 버전 한 줄 (화면 아래 이름표 위): 있음 → 업데이트 · 받는 중 % · 다 받음 → 다시 시작. */
@Composable
fun UpdateBar(s: AppState) {
    val u = s.updates ?: return
    if (u.state == Updates.State.NONE || (s.updateLater && u.state == Updates.State.AVAILABLE)) return
    val c = Theme.c
    Row(Modifier.fillMaxWidth().background(c.leaf).drawBehind { drawLine(c.hair, Offset.Zero, Offset(size.width, 0f), Tokens.Stroke.hair.toPx()) }
        .padding(horizontal = Tokens.Space.s5), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        val text = when (u.state) {
            Updates.State.DOWNLOADING -> stringResource(R.string.update_downloading, (u.progress * 100).toInt())
            Updates.State.READY -> stringResource(R.string.update_ready)
            else -> stringResource(R.string.update_available)
        }
        Text(text, style = Theme.small().copy(color = c.ink), maxLines = 1, modifier = Modifier.weight(1f).padding(vertical = Tokens.Space.s2))
        @Composable fun act(label: String, gilt: Boolean, go: () -> Unit) = Text(label, style = Theme.small().copy(color = if (gilt) c.giltText else c.inkSoft, textDecoration = TextDecoration.Underline), maxLines = 1,
            modifier = Modifier.heightIn(min = Tokens.Size.touch).wrapContentHeight().clickable(role = Role.Button, onClick = go))
        when (u.state) {
            Updates.State.AVAILABLE -> { act(stringResource(R.string.update_later), false) { s.updateLater = true }; act(stringResource(R.string.update_get), true) { u.start() } }
            Updates.State.READY -> act(stringResource(R.string.update_restart), true) { u.install() }
            else -> {}
        }
    }
}
