package io.github.graviton94.todaybible.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Canon
import io.github.graviton94.todaybible.core.Latin
import io.github.graviton94.todaybible.data.Narration
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens

/**
 * 낭독 미리 받기 (1.2): 와이파이 될 때 권마다 받아 두고 인터넷 없이 들어요.
 * 권마다 머리글 (라틴) · 이름 · 받은 장 / 전체 장 · 칩 (미리 받기 · 용량 / 받는 중 / 기다리는 중 / ✓ 받아 둠 / 평생권).
 * 여러 권을 눌러 두면 차례로 받아요. 받아 둔 권은 자동 정리에서 빠져요.
 */
@Composable
fun KeepPage(s: AppState) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    BackHandler { s.keepOpen = false }
    val voice = s.narrator
    // 권마다 받아 둔 장 수 · 이 폰에 있는 낭독 용량 (받기가 끝나거나 바뀔 때마다 다시 셈)
    val counts by produceState(IntArray(66), voice, s.keeping, s.keepQueue) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { IntArray(66) { b -> (1..Canon.books[b].chapters).count { Narration.has(ctx, voice, b, it) } } }
    }
    val usage by produceState(0L, s.keeping) { value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Narration.usage(ctx) } }
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().background(c.leaf)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s3, vertical = Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.prayer_back), style = Theme.small().copy(color = c.inkSoft), maxLines = 1,
                modifier = Modifier.heightIn(min = Tokens.Size.tab).clickable(role = Role.Button) { s.keepOpen = false }.padding(horizontal = Tokens.Space.s2))
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = Tokens.Space.s5)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    Text("VOX · ARCHIVUM", style = Theme.caps(), maxLines = 1)
                    Text(stringResource(R.string.keep_title), style = Theme.title(k, Tokens.Text.display))
                    Text(stringResource(R.string.keep_lede, "%.0f".format(usage / 1_000_000f)), style = Theme.small())
                    Hair()
                    if (voice == Narration.DEVICE) Text(stringResource(R.string.keep_device), style = Theme.body().copy(color = c.inkSoft))
                    else {
                        ChoiceRowLite(stringResource(R.string.keep_wifi_only), s.keepWifiOnly) { s.flipKeepWifiOnly() }
                        val free = Canon.free.filter { counts[it] < Canon.books[it].chapters && s.keeping?.first != it && it !in s.keepQueue }
                        if (free.isNotEmpty()) BookButton(stringResource(R.string.keep_all_free, free.sumOf { (Canon.books[it].chapters - counts[it]) * MB_TENTHS_PER_CH } / 10), Modifier.fillMaxWidth(), quiet = true) { free.forEach { s.keepNarration(it) } }
                        UnderlineTabs(listOf(stringResource(R.string.old_testament), stringResource(R.string.new_testament)), tab) { tab = it }
                    }
                }
            }
            if (voice != Narration.DEVICE) items((if (tab == 0) 0..38 else 39..65).toList()) { b -> KeepRow(s, b, counts[b]) }
            item { Box(Modifier.padding(vertical = Tokens.Space.s6)) }
        }
    }
}

/** 장마다 낭독 약 0.4MB (권 크기 어림). */
private const val MB_TENTHS_PER_CH = 4

@Composable
private fun KeepRow(s: AppState, b: Int, have: Int) {
    val c = Theme.c
    val all = Canon.books[b].chapters
    val running = s.keeping?.takeIf { it.first == b }
    val queued = b in s.keepQueue
    val kept = have >= all
    val locked = s.locked(b)
    Hair()
    Row(Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s3), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
            Text(Latin.book(b), style = Theme.caps(), maxLines = 1)
            Text(s.bookName(b), style = Theme.body(), maxLines = 1)
            Text(stringResource(R.string.keep_count, all, if (running != null) running.second else have), style = Theme.small(), maxLines = 1)
        }
        // 칩: 받아 둔 것은 금빛 테 (누르면 지우기), 받을 것은 채운 칩, 잠긴 것은 평생권
        val (label, filled) = when {
            running != null -> stringResource(R.string.keep_chip_running, running.second, running.third) to false
            queued -> stringResource(R.string.keep_chip_queued) to false
            kept -> "✓ " + stringResource(R.string.keep_chip_kept) to false
            locked -> stringResource(R.string.premium_tag) to false
            else -> stringResource(R.string.keep_chip_get, (all - have).coerceAtLeast(1) * MB_TENTHS_PER_CH / 10f) to true
        }
        val ctx = LocalContext.current
        Text(label, style = Theme.small().copy(color = if (filled) c.leatherInk else if (kept) c.giltText else c.inkSoft), maxLines = 1,
            modifier = Modifier.then(if (filled) Modifier.background(c.leather) else Modifier.border(Tokens.Stroke.hair, if (kept) c.gilt else c.hair))
                .clickable(role = Role.Button) {
                    when {
                        running != null -> {}
                        queued -> s.cancelKeep(b)
                        kept -> { Narration.release(ctx, s.narrator, b); s.toast = ctx.getString(R.string.keep_released) }
                        else -> s.keepNarration(b)
                    }
                }.padding(horizontal = Tokens.Space.s3, vertical = Tokens.Space.s2))
    }
}

/** 켜고 끄는 한 줄 (설정의 것과 같은 모양, 이 화면용). */
@Composable
private fun ChoiceRowLite(text: String, on: Boolean, onClick: () -> Unit) {
    val c = Theme.c
    Row(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.touch).clickable(role = Role.Switch, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Theme.body(), modifier = Modifier.weight(1f))
        Text(if (on) "✓" else "–", style = Theme.body().copy(color = if (on) c.gilt else c.unwritten), modifier = Modifier.width(Tokens.Size.touch).padding(start = Tokens.Space.s3))
    }
}
