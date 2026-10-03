package io.github.graviton94.todaybible.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.core.Mode
import io.github.graviton94.todaybible.core.ReadingPace
import io.github.graviton94.todaybible.core.TypeJudge
import io.github.graviton94.todaybible.core.VerseKey
import io.github.graviton94.todaybible.design.Fonts
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun CopyPage(s: AppState) {
    val c = Theme.c; val k = s.korean
    val t = s.text(); val p = s.progress
    val fillable = t.fillable(s.chapter)
    val next = p.nextVerse(s.translation, t, s.chapter)
    val doneCount = fillable.count { p.isFilled(s.translation, VerseKey(s.book, s.chapter, it)) }
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
        RunningHead(if (k) "${s.bookName()} ${s.chapter}장" else "${s.bookName().uppercase()} ${s.chapter}", stringResource(R.string.verse_of, doneCount, fillable.size), k)
        UnderlineTabs(listOf(stringResource(R.string.mode_type), stringResource(R.string.mode_paper), stringResource(R.string.mode_aloud)), tab) { tab = it }
        if (next == null && tab != 1) {
            ChapterDoneNote(s)
        } else when (tab) {
            0 -> TypeTab(s, next!!)
            1 -> PaperTab(s)
            else -> AloudTab(s, next!!)
        }
    }
}

/** 이 장을 이미 다 썼을 때. */
@Composable
private fun ChapterDoneNote(s: AppState) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
        Text(stringResource(R.string.chapter_done, s.bookName(), s.chapter), style = Theme.title(s.korean))
        val (nb, nc) = s.nextChapter()
        BookButton(stringResource(R.string.continue_at, s.bookName(nb), nc), Modifier.fillMaxWidth()) { s.open(nb, nc) }
    }
}

/** 말씀 한 절: 붉은 블랙레터 절 번호 + 본문. KJV 첨가어는 이탤릭, LORD 는 스몰캡. lit = 밝아진 글자 수 (낭독), null = 전부. */
@Composable
fun VerseText(s: AppState, number: Int, text: String, lit: Int? = null, marks: List<TypeJudge.Mark>? = null) {
    val c = Theme.c; val k = s.korean
    val body: AnnotatedString = buildAnnotatedString {
        withStyle(SpanStyle(fontFamily = Fonts.black, color = c.rubric, fontSize = Tokens.Leading.verseNumber.em)) { append("$number ") }
        // lit 은 본문 글자만 셈 (절 번호 제외)
        var i = 0
        Markup.spans(text).forEach { sp ->
            val style = SpanStyle(fontStyle = if (sp.italic) FontStyle.Italic else FontStyle.Normal, fontFeatureSettings = if (sp.smallCaps) "smcp" else null)
            if (marks != null) {
                // 옮겨 쓴 만큼: 맞은 글자는 먹, 틀린 글자는 붉은 밑줄, 아직은 흐린 먹
                sp.text.forEachIndexed { j, ch ->
                    val st = when (marks.getOrNull(i + j)) {
                        TypeJudge.Mark.OK -> style.copy(color = c.ink)
                        TypeJudge.Mark.WRONG -> style.copy(color = c.rubric, textDecoration = TextDecoration.Underline)
                        else -> style.copy(color = c.unwritten)
                    }
                    withStyle(st) { append(ch) }
                }
            } else {
                val cut = if (lit == null) sp.text.length else (lit - i).coerceIn(0, sp.text.length)
                withStyle(style.copy(color = c.ink)) { append(sp.text.substring(0, cut)) }
                if (cut < sp.text.length) withStyle(style.copy(color = c.unwritten)) { append(sp.text.substring(cut)) }
            }
            i += sp.text.length
        }
    }
    Text(body, style = Theme.verse(k))
}

@Composable
private fun TypeTab(s: AppState, verse: Int) {
    val c = Theme.c; val k = s.korean
    val source = s.text().verse(s.chapter, verse)
    var value by remember(s.translation, s.book, s.chapter, verse) { mutableStateOf(TextFieldValue("")) }
    val marks = TypeJudge.marks(source, value.text)
    LaunchedEffect(value.text) {
        if (TypeJudge.done(source, value.text)) { delay(Tokens.Motion.typeSettleMs.toLong()); s.fill(listOf(verse), Mode.TYPE) }
    }
    if (verse > 1) {
        val prev = s.text().verse(s.chapter, verse - 1)
        if (prev.isNotBlank()) Text(Markup.plain(prev), style = Theme.small(), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    VerseText(s, verse, source, marks = marks)
    BasicTextField(
        value = value,
        onValueChange = { nv ->
            // 붙여넣기 · 자동완성으로 한꺼번에 들어온 글은 받지 않음 (한 자씩 옮겨 쓰기)
            if (nv.text.length - value.text.length <= 3) value = nv
        },
        textStyle = Theme.typed(k),
        cursorBrush = SolidColor(c.rubric),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, capitalization = KeyboardCapitalization.None),
        modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.Size.touch).drawBehind {
            drawLine(c.inkSoft, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.rule.toPx())
        }.padding(vertical = Tokens.Space.s2),
        decorationBox = { inner ->
            Box { if (value.text.isEmpty()) Text(stringResource(R.string.type_hint), style = Theme.body().copy(color = c.unwritten)); inner() }
        },
    )
}

@Composable
private fun PaperTab(s: AppState) {
    val c = Theme.c; val ctx = LocalContext.current
    val file = File(ctx.filesDir, "photos/${s.translation.id}_${s.book + 1}_${s.chapter}.jpg")
    var stamp by remember(s.book, s.chapter) { mutableIntStateOf(if (file.exists()) 1 else 0) }
    var declared by remember(s.book, s.chapter) { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) runCatching {
            file.parentFile?.mkdirs()
            ctx.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            stamp++
        }
    }
    val bitmap = remember(stamp) { if (file.exists()) runCatching { BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap() }.getOrNull() else null }
    Box(
        Modifier.fillMaxWidth().aspectRatio(Tokens.Ratio.photoAspect).clip(RoundedCornerShape(Tokens.Radius.card)).background(c.paper)
            .clickable(role = Role.Button) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxWidth(), contentScale = ContentScale.Crop)
        else Text(stringResource(R.string.paper_photo), style = Theme.body().copy(color = c.inkSoft))
    }
    Row(Modifier.fillMaxWidth().clickable { declared = !declared }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(declared, { declared = it }, colors = CheckboxDefaults.colors(checkedColor = c.leather, uncheckedColor = c.inkSoft, checkmarkColor = c.leatherInk))
        Text(stringResource(R.string.paper_declare, s.chapter), style = Theme.body(), modifier = Modifier.weight(1f))
    }
    BookButton(stringResource(R.string.paper_add), Modifier.fillMaxWidth(), enabled = declared) {
        s.fill(s.text().fillable(s.chapter), Mode.PAPER)
    }
}

@Composable
private fun AloudTab(s: AppState, verse: Int) {
    val k = s.korean
    val source = s.text().verse(s.chapter, verse)
    val plain = Markup.plain(source)
    var speed by remember { mutableIntStateOf(0) }
    var playing by remember(verse, s.chapter, s.book) { mutableStateOf(false) }
    var lit by remember(verse, s.chapter, s.book) { mutableIntStateOf(0) }
    LaunchedEffect(playing, verse) {
        while (playing && lit < plain.length) {
            delay(ReadingPace.delayMillis(plain[lit], k, if (speed == 0) 1f else Tokens.Motion.aloudNormal)); lit++
        }
        if (lit >= plain.length) playing = false
    }
    VerseText(s, verse, source, lit = lit)
    UnderlineTabs(listOf(stringResource(R.string.aloud_slow), stringResource(R.string.aloud_normal)), speed, Modifier.padding(horizontal = Tokens.Space.s6)) { speed = it }
    if (lit >= plain.length) BookButton(stringResource(R.string.aloud_done), Modifier.fillMaxWidth()) { s.fill(listOf(verse), Mode.ALOUD) }
    else BookButton(stringResource(if (playing) R.string.aloud_pause else R.string.aloud_start), Modifier.fillMaxWidth()) { playing = !playing }
}
