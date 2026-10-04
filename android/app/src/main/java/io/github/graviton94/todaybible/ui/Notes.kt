package io.github.graviton94.todaybible.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.core.Memorize
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens

/** 밑줄 한 줄 (또는 몇 줄) 적는 칸. 비어 있으면 옅은 안내. */
@Composable
fun LineField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, lines: Int = 1, max: Int = 200) {
    val c = Theme.c
    BasicTextField(
        value = value, onValueChange = { if (it.length <= max) onChange(if (lines == 1) it.replace('\n', ' ') else it) },
        singleLine = lines == 1, minLines = lines, textStyle = Theme.body(), cursorBrush = SolidColor(c.rubric),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = modifier.fillMaxWidth().heightIn(min = Tokens.Size.row)
            .drawBehind { drawLine(c.inkSoft, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.hair.toPx()) },
        decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (value.isEmpty()) Text(hint, style = Theme.body().copy(color = c.unwritten)); inner() } },
    )
}

/** 장을 마친 뒤 마음에 남은 한 줄 (A4). 적지 않아도 돼요. 나의 성경 PDF 의 그 장 끝에 들어가요. */
@Composable
fun ReflectionField(s: AppState, book: Int, chapter: Int, text: String, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
        Text(stringResource(R.string.reflect_title), style = Theme.small())
        LineField(text, onChange, stringResource(R.string.reflect_hint), max = 120)
    }
}

/**
 * 마음에 새기는 말씀 (A3): 한 절을 크게 두고, 누를 때마다 글자를 조금 더 가려 보며 되뇌어요.
 * 가려진 낱말은 누르면 살짝 보여요. 점수 · 횟수 · 다시 묻기는 없어요.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MemorySheet(s: AppState) {
    val c = Theme.c; val k = s.korean
    val key = s.memoryOpen ?: return
    BookSheet({ s.memoryOpen = null }) {
        val plain = remember(key, s.translation) { Markup.plain(s.store.book(s.translation, key.book).verse(key.chapter, key.verse)) }
        var level by remember(key) { mutableStateOf(0) }
        var peek by remember(key, level) { mutableStateOf(setOf<Int>()) }
        Text(stringResource(R.string.memory_title), style = Theme.small().copy(color = c.rubric))
        Text(stringResource(R.string.ref_verse, s.bookName(key.book), key.chapter, key.verse), style = Theme.title(k))
        FlowRow(Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s3), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            Memorize.words(plain, level).forEachIndexed { i, w ->
                val open = !w.hidden || i in peek
                Text(if (open) w.text else w.shown, style = Theme.verse(k).copy(color = if (w.hidden && !open) c.unwritten else c.ink),
                    modifier = if (w.hidden) Modifier.clip(RoundedCornerShape(Tokens.Radius.chip)).clickable(role = Role.Button) { peek = if (i in peek) peek - i else peek + i } else Modifier)
            }
        }
        Text(stringResource(if (level == 0) R.string.memory_hint else R.string.memory_peek), style = Theme.small())
        Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            BookButton(stringResource(when (level) { 0 -> R.string.memory_hide; Memorize.LEVELS - 1 -> R.string.memory_show; else -> R.string.memory_more }), Modifier.weight(1f)) {
                level = (level + 1) % Memorize.LEVELS
            }
            BookButton(stringResource(R.string.memory_read), Modifier.weight(1f), quiet = true) { s.memoryOpen = null; s.read(key.book, key.chapter, key.verse) }
        }
        Text(stringResource(R.string.memory_remove), style = Theme.small().copy(color = c.inkSoft),
            modifier = Modifier.heightIn(min = Tokens.Size.touch).clickable(role = Role.Button) { s.toggleMemory(key); s.memoryOpen = null }.padding(vertical = Tokens.Space.s2))
    }
}

/** 마음에 새기는 말씀 목록 (책갈피 · 형광펜 창 안). */
@Composable
fun MemoryList(s: AppState, onOpen: () -> Unit) {
    val c = Theme.c
    val list = s.memory.filter { it.translation == s.translation }.sortedByDescending { it.epochDay }
    Text(stringResource(R.string.memory_title), style = Theme.title(s.korean), modifier = Modifier.padding(top = Tokens.Space.s3))
    if (list.isEmpty()) Text(stringResource(R.string.memory_none), style = Theme.small())
    list.forEach { m ->
        val v = m.key
        Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(); s.memoryOpen = v }.padding(vertical = Tokens.Space.s1),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
            Text(Markup.plain(s.store.book(s.translation, v.book).verse(v.chapter, v.verse)), style = Theme.body(), maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(stringResource(R.string.ref_verse, s.bookName(v.book), v.chapter, v.verse), style = Theme.small().copy(color = c.rubric), maxLines = 1)
        }
    }
}
