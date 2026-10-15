package io.github.graviton94.todaybible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Translation
import io.github.graviton94.todaybible.core.VerseKey
import io.github.graviton94.todaybible.data.Refs
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens

/*
 * 관련 구절 · 성경 사전 (1.6). 어디로도 넘어가지 않아요: 관련 구절은 시트에서 본문 전체를 읽고 닫고,
 * 사전 풀이 · 판화 이야기 속 장절은 그 절만 작은 카드 ([VersePeek]) 로 띄워요. 닫으면 보던 자리 그대로.
 */

/** 장절 이름 (화면 언어): "히브리서 7:1", 같은 장 범위면 "히브리서 7:1–3". */
fun AppState.spanLabel(sp: Refs.Span): String {
    val a = sp.from; val b = sp.to
    val head = "${bookName(a.book)} ${a.chapter}:${a.verse}"
    return when {
        a.raw == b.raw -> head
        a.book == b.book && a.chapter == b.chapter -> "$head–${b.verse}"
        a.book == b.book -> "$head–${b.chapter}:${b.verse}"
        else -> head
    }
}

/** 그 범위의 본문 (지금 번역, 같은 장 안에서 많아야 4절). 없는 절 (번역마다 절 나눔이 다른 곳) 은 건너뜀. */
private fun AppState.spanText(sp: Refs.Span): List<Pair<Int, String>> {
    val a = sp.from; val t = runCatching { store.book(translation, a.book) }.getOrNull() ?: return emptyList()
    if (a.chapter > t.chapterCount) return emptyList()
    val last = if (sp.to.book == a.book && sp.to.chapter == a.chapter) sp.to.verse else a.verse
    return (a.verse..minOf(last, a.verse + 3)).mapNotNull { v -> runCatching { t.verse(a.chapter, v) }.getOrNull()?.takeIf { it.isNotBlank() }?.let { v to it } }
}

/** 관련 구절 시트: 「관련 구절 · 사전」 두 칸 (사전은 풀이가 있는 화면 언어에서만). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RelatedSheet(s: AppState, key: VerseKey) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    val spans = remember(key) { Refs.related(ctx, key) }
    val dict = Refs.hasDictionary(k)
    var tab by remember(key) { mutableIntStateOf(if (dict) s.relatedTab else 0) }
    BookSheet({ s.related = null }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.related_title, s.spanLabel(Refs.Span(key, key))), style = Theme.title(k), maxLines = 2, modifier = Modifier.weight(1f))
            if (spans.isNotEmpty()) Text("· ${spans.size}", style = Theme.number().copy(fontSize = Tokens.Text.small * Theme.scale), maxLines = 1)
        }
        if (dict) UnderlineTabs(listOf(stringResource(R.string.related), stringResource(R.string.dict)), tab) { tab = it }
        if (tab == 0) {
            if (spans.isEmpty()) Text(stringResource(R.string.related_none), style = Theme.small(), modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s4))
            else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = Tokens.Size.sheetMaxGrid * 1.4f)) {
                    items(spans.size) { i ->
                        val sp = spans[i]
                        Row(Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s3), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                            Text(s.spanLabel(sp), style = Theme.label().copy(color = c.giltText), modifier = Modifier.width(Tokens.Size.touch * 1.6f))
                            Text(s.spanText(sp).joinToString(" ") { (v, t) -> if (sp.from.raw == sp.to.raw) t else "$v $t" }, style = Theme.verse(s.bibleKorean).copy(fontSize = Tokens.Text.body * Theme.scale), modifier = Modifier.weight(1f))
                        }
                        Hair()
                    }
                }
                Text(stringResource(R.string.related_foot), style = Theme.small().copy(color = c.unwritten), maxLines = 1)
            }
        } else {
            val english = remember(key) { runCatching { s.store.book(Translation.WEB, key.book).verse(key.chapter, key.verse) }.getOrDefault("") }
            val found = remember(key) { Refs.inVerse(ctx, key, english) }
            if (found.isEmpty()) Text(stringResource(R.string.dict_none), style = Theme.small(), modifier = Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s4))
            else {
                Text(stringResource(R.string.dict_in_verse), style = Theme.small(), maxLines = 1, modifier = Modifier.fillMaxWidth())
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                    found.forEach { e ->
                        Text(e.name, style = Theme.small().copy(color = c.ink), maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(Tokens.Radius.pill)).clickable(role = Role.Button) { s.dictEntry = e.id }
                                .border(Tokens.Stroke.hair, c.hair, RoundedCornerShape(Tokens.Radius.pill)).padding(horizontal = Tokens.Space.s3, vertical = Tokens.Space.s2))
                    }
                }
                Column(Modifier.fillMaxWidth().heightIn(max = Tokens.Size.sheetMaxGrid).verticalScroll(rememberScrollState())) {
                    found.forEach { e -> EntryRow(s, e) }
                }
            }
        }
    }
}

/** 사전 한 줄: 갈래 · 이름 · 한 줄 풀이. 누르면 풀이 한 장. */
@Composable
private fun EntryRow(s: AppState, e: Refs.Entry) {
    val c = Theme.c
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { s.dictEntry = e.id }) {
        Row(Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s3), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
                    Text(kindLatin(e.kind), style = Theme.caps(), maxLines = 1)
                    Text(e.name, style = Theme.label().copy(color = c.giltText), maxLines = 1)
                }
                val line = e.brief.ifBlank { Refs.parts(e.body).joinToString("") { it.text } }
                if (line.isNotBlank()) Text(line, style = Theme.small().copy(color = c.ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text("›", style = Theme.small().copy(color = c.unwritten), maxLines = 1)
        }
        Hair()
    }
}

private fun kindLatin(k: Char) = when (k) { 'P' -> "PERSONA"; 'L' -> "LOCVS"; else -> "VERBVM" }

/** 사전 풀이 한 장: 갈래 · 이름 · 원어 · 이름 뜻 · 풀이 · 처음 나오는 곳 · 주요 장절 · 판화. 장절은 누르면 그 절만 작게. */
@Composable
fun DictPage(s: AppState, id: String) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    val e = remember(id) { Refs.entry(ctx, id) } ?: run { s.dictEntry = null; return }
    BackHandler { s.dictEntry = null }
    val link = SpanStyle(color = c.giltText, textDecoration = TextDecoration.Underline)
    var plate by remember(id) { mutableStateOf<io.github.graviton94.todaybible.data.Plate?>(null) }
    Box(Modifier.fillMaxSize().background(c.leaf).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.heightIn(min = Tokens.Size.touch).width(Tokens.Size.touch).clickable(role = Role.Button, onClickLabel = stringResource(R.string.close)) { s.dictEntry = null }, contentAlignment = Alignment.Center) {
                    Text("‹", style = Theme.title(k))
                }
                Text(stringResource(R.string.dict), style = Theme.title(k), maxLines = 1, modifier = Modifier.weight(1f))
                Text("LEXICON", style = Theme.caps(), maxLines = 1, modifier = Modifier.padding(end = Tokens.Space.s3))
            }
            Hair()
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4),
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Text(kindLatin(e.kind), style = Theme.caps(), maxLines = 1)
                Text(e.name, style = Theme.title(k, Tokens.Text.display))
                if (e.orig.isNotBlank()) Text(e.orig, style = Theme.body().copy(color = c.inkSoft, fontStyle = FontStyle.Italic), maxLines = 1)
                if (e.meaning.isNotBlank()) Text(androidx.compose.ui.text.buildAnnotatedString {
                    append(stringResource(R.string.dict_meaning)); append("  ")
                    withStyle(SpanStyle(color = c.giltText, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)) { append(e.meaning) }
                }, style = Theme.body())
                val body = e.body.ifBlank { e.brief }
                Text(androidx.compose.ui.text.buildAnnotatedString {
                    Refs.parts(body).forEach { part ->
                        val sp = part.span
                        if (sp == null) append(part.text)
                        else withLink(androidx.compose.ui.text.LinkAnnotation.Clickable(sp.from.raw.toString(), androidx.compose.ui.text.TextLinkStyles(link)) { s.versePeek = sp }) { append(part.text) }
                    }
                }, style = Theme.body())
                // 처음 나오는 곳 · 주요 장절 · 나오는 절 수
                val first = e.refs.firstOrNull()?.let { VerseKey(it) }
                val keys = Refs.spansIn(body).filter { it.from.raw != first?.raw }.take(4)
                Column(Modifier.fillMaxWidth()) {
                    Hair()
                    if (first != null) FactRow(stringResource(R.string.dict_first), listOf(Refs.Span(first, first)), s)
                    if (keys.isNotEmpty()) FactRow(stringResource(R.string.dict_key), keys, s)
                    Text(stringResource(R.string.dict_count, e.refs.size), style = Theme.small(), modifier = Modifier.padding(vertical = Tokens.Space.s3))
                    Hair()
                }
                // 이 이름이 나오는 판화 (누르면 그 그림만, 드러난 조각만)
                val plates = remember(id) { val set = e.refs.toHashSet(); s.store.plates.filter { VerseKey(it.book, it.chapter, it.verse).raw in set }.take(4) }
                if (plates.isNotEmpty()) {
                    Text(stringResource(R.string.dict_plates), style = Theme.small(), maxLines = 1)
                    plates.forEach { p ->
                        Text(androidx.compose.ui.text.buildAnnotatedString {
                            withStyle(link) { append(s.plateName(p)) }
                            append("  ·  ${s.bookName(p.book)} ${p.chapter}:${p.verse}")
                        }, style = Theme.body(), modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { plate = p }.padding(vertical = Tokens.Space.s1))
                    }
                }
                Text(stringResource(R.string.dict_source), style = Theme.small().copy(color = c.unwritten), modifier = Modifier.padding(top = Tokens.Space.s4))
            }
        }
    }
    plate?.let { PlatePeek(s, it) { plate = null } }
}

@Composable
private fun FactRow(label: String, spans: List<Refs.Span>, s: AppState) {
    val c = Theme.c
    Row(Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s3), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Text(label, style = Theme.small(), modifier = Modifier.width(Tokens.Size.touch * 2))
        androidx.compose.foundation.layout.FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            spans.forEach { sp ->
                Text(s.spanLabel(sp), style = Theme.small().copy(color = c.giltText, textDecoration = TextDecoration.Underline), maxLines = 1,
                    modifier = Modifier.clickable(role = Role.Button) { s.versePeek = sp })
            }
        }
    }
}

/** 장절 하나만 작은 카드로: 아무 데나 누르거나 뒤로 가면 닫혀요. 보던 화면은 그대로. */
@Composable
fun VersePeek(s: AppState, sp: Refs.Span) {
    val c = Theme.c; val k = s.korean
    val lines = remember(sp, s.translation) { s.spanText(sp) }
    val closeLabel = stringResource(R.string.close)
    val close = { s.versePeek = null }
    androidx.compose.ui.window.Dialog(close, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(c.scrim).clickable(role = Role.Button, onClickLabel = closeLabel, onClick = close).padding(horizontal = Tokens.Space.s5),
            verticalArrangement = Arrangement.Center) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.chip)).background(c.leaf).padding(Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Text(s.spanLabel(sp), style = Theme.label().copy(color = c.giltText), maxLines = 1)
                if (lines.isEmpty()) Text(stringResource(R.string.related_none), style = Theme.small())
                lines.forEach { (v, t) -> Text(if (lines.size > 1) "$v  $t" else t, style = Theme.verse(s.bibleKorean)) }
                Text(closeLabel, style = Theme.small().copy(textDecoration = TextDecoration.Underline), modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

/** 판화 이야기 끝: 「이 장면의 관련 구절」 상위 3개 (누르면 그 절만 작게). */
@Composable
fun SceneRelated(s: AppState, p: io.github.graviton94.todaybible.data.Plate) {
    val c = Theme.c; val ctx = LocalContext.current
    val key = VerseKey(p.book, p.chapter, p.verse)
    val spans = remember(p.id) { Refs.related(ctx, key).take(3) }
    if (spans.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = Tokens.Space.s2), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
        Hair()
        Text("${stringResource(R.string.related_scene)} · ${s.spanLabel(Refs.Span(key, key))}", style = Theme.small(), modifier = Modifier.padding(top = Tokens.Space.s2))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
            spans.forEach { sp ->
                Text(s.spanLabel(sp), style = Theme.body().copy(color = c.giltText, textDecoration = TextDecoration.Underline), maxLines = 1,
                    modifier = Modifier.heightIn(min = Tokens.Size.touch * 0.8f).clickable(role = Role.Button) { s.versePeek = sp })
            }
        }
    }
}

/** 찾기의 「사전」 칸: 이름이 그 말로 시작하는 항목. */
@Composable
fun DictResults(s: AppState, list: List<Refs.Entry>) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = Tokens.Size.sheetMaxGrid * 2)) {
        items(list.size) { i -> EntryRow(s, list[i]) }
    }
}

