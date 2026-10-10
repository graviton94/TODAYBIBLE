package io.github.graviton94.todaybible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.design.Fonts
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/*
 * 여는 순간: 매일 켤 때 A (금박 새김), 하루 첫 열기 C (표지 넘김). 둘 다 눌러야 들어가요 (뒤로 가기로도).
 * 가죽 결 · 대리석 면지는 그림 파일 없이 코드로 그려요.
 */

private val settle: Easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
private val sweep: Easing = CubicBezierEasing(0.6f, 0f, 0.2f, 1f)

/** 타임라인 t(ms) 에서 start 부터 dur 동안의 진행 (0..1). */
private fun seg(t: Float, start: Int, dur: Int, e: Easing = LinearEasing) = e.transform(((t - start) / dur).coerceIn(0f, 1f))

/** 가죽 결: 작은 점들을 흩뿌린 한 장을 반복해서 (한 번만 만들어 둠). */
private val grain: ImageBitmap by lazy {
    val n = 160; val r = java.util.Random(7)
    val px = IntArray(n * n) { val a = r.nextInt(48); (a shl 24) or 0x000000 }
    android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** 가죽 바탕: 가운데가 조금 밝고 가장자리로 가라앉는 결 + 가죽 결. */
private fun DrawScope.leather(cover: Color) {
    drawRect(Brush.radialGradient(listOf(lerp(cover, Color.White, 0.06f), cover, lerp(cover, Color.Black, 0.45f)),
        center = Offset(size.width / 2, size.height * 0.4f), radius = size.maxDimension * 0.75f))
    drawRect(ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)))
}

/** 금박 빛: 왼쪽에서 오른쪽으로 한 번 스치는 띠 (p = 0..1). 띠 밖은 늘 금박 그 색, 띠 가장자리만 살짝 어둡게. */
private fun goldBrush(lo: Color, mid: Color, hi: Color, p: Float, w: Float): Brush {
    val x = -w + (w * 3) * p
    return Brush.linearGradient(listOf(mid, lerp(mid, lo, 0.5f), hi, lerp(mid, lo, 0.5f), mid), start = Offset(x - w, 0f), end = Offset(x + w, w * 0.3f), tileMode = TileMode.Clamp)
}

@Composable
internal fun Breathing(text: String, color: Color, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "hint").animateFloat(0.85f, 0.35f, infiniteRepeatable(tween(Tokens.Motion.introBreatheMs / 2), RepeatMode.Reverse), label = "hint")
    Text(text, style = Theme.small().copy(color = color.copy(alpha = pulse), letterSpacing = 0.3.em, textAlign = TextAlign.Center), modifier = modifier)
}

/** A · 매일: 두 줄 금테가 그어지고 ✠ · 제목이 한 자씩, 금박에 빛이 스친 뒤 ‘눌러서 들어가기’. */
@Composable
fun IntroDaily(s: AppState, onEnter: () -> Unit) {
    val c = Theme.c; val k = s.korean
    val haptic = LocalHapticFeedback.current; val scope = rememberCoroutineScope()
    val t = remember { Animatable(0f) }; val enter = remember { Animatable(0f) }
    val title = stringResource(R.string.app_name)
    val total = Tokens.Motion.introLetterStep * title.length + Tokens.Motion.introGlintMs + 1600
    LaunchedEffect(Unit) { t.animateTo(total.toFloat(), tween(total, easing = LinearEasing)) }
    fun go() { if (enter.value > 0f) return; haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); scope.launch { enter.animateTo(1f, tween(Tokens.Motion.introEnterMs, easing = sweep)); onEnter() } }
    BackHandler { go() }
    val cover = s.coverColor()
    val tv = t.value
    Box(Modifier.fillMaxSize().graphicsLayer {
        val e = enter.value; scaleX = 1f + 0.08f * e; scaleY = 1f + 0.08f * e; alpha = 1f - e
    }.clickable(remember { MutableInteractionSource() }, null) { go() }.drawBehind {
        leather(cover)
        // 두 줄 금테: 왼쪽 위에서 시작해 한 바퀴
        val m = Tokens.Size.introFrame.toPx(); val g = Tokens.Space.s2.toPx() * 0.75f
        listOf(0f to 0, g to 250).forEach { (inset, delay) ->
            val r = RoundRect(Rect(m + inset, m + inset, size.width - m - inset, size.height - m - inset), CornerRadius(Tokens.Radius.card.toPx()))
            val path = Path().apply { addRoundRect(r) }; val pm = PathMeasure().apply { setPath(path, false) }
            val out = Path(); pm.getSegment(0f, pm.length * seg(tv, delay, Tokens.Motion.introFrameMs, sweep), out, true)
            drawPath(out, c.gilt, style = Stroke(if (inset == 0f) Tokens.Stroke.gilt.toPx() else Tokens.Stroke.giltFine.toPx()))
        }
        // 네 귀퉁이 작은 ✠
        val ca = seg(tv, 1100, 600); val cs = Tokens.Size.introCorner.toPx(); val o = m + g + cs
        listOf(Offset(o, o), Offset(size.width - o, o), Offset(o, size.height - o), Offset(size.width - o, size.height - o)).forEach { stamp(STAMP_CROSS, c.gilt.copy(alpha = ca), it, cs) }
    }) {
        Column(Modifier.fillMaxSize().systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val emb = seg(tv, 350, 800, settle)
            Box(Modifier.size(Tokens.Size.introEmblem).graphicsLayer { alpha = emb; val sc = 0.92f + 0.08f * emb; scaleX = sc; scaleY = sc }.drawBehind {
                val glint = sin(seg(tv, 1350, Tokens.Motion.introGlintMs) * PI).toFloat()
                stamp(STAMP_CROSS, lerp(c.gilt, c.giltHi, glint * 0.8f))
            })
            Row(Modifier.padding(top = Tokens.Space.s4)) {
                title.forEachIndexed { i, ch ->
                    val rise = seg(tv, 550 + i * Tokens.Motion.introLetterStep, Tokens.Motion.introLetterMs, settle)
                    val gl = seg(tv, 1350 + i * 120, Tokens.Motion.introGlintMs, FastOutSlowInEasing)
                    Text(ch.toString(), style = Theme.title(k, Tokens.Text.intro).copy(letterSpacing = 0.06.em,
                        brush = goldBrush(c.giltLo, c.gilt, c.giltHi, gl, Tokens.Text.intro.value * 2f)),
                        modifier = Modifier.graphicsLayer { alpha = rise; translationY = (1f - rise) * 6.dp2px(density) })
                }
            }
            val rule = seg(tv, 1200, 900, sweep)
            Box(Modifier.padding(vertical = Tokens.Space.s3).width(Tokens.Size.coverW * rule).height(Tokens.Stroke.giltFine).background(c.gilt))
            Text(stringResource(R.string.intro_caps), style = Theme.small().copy(fontFamily = Fonts.fell, fontSize = Tokens.Text.introCaps, letterSpacing = 0.42.em, color = c.gilt.copy(alpha = seg(tv, 1500, 800))))
            Text(fmtDate(R.string.fmt_date_full, s.today()), style = Theme.small().copy(color = c.leatherInk.copy(alpha = 0.85f * seg(tv, 1700, 800)), letterSpacing = 0.12.em),
                modifier = Modifier.padding(top = Tokens.Space.s3))
        }
        if (tv > 2400) Breathing(stringResource(R.string.intro_tap_enter), c.leatherInk,
            Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(bottom = Tokens.Space.s6).fillMaxWidth())
    }
}

private fun Int.dp2px(density: Float) = this * density

/**
 * C · 하루 첫 열기: 가죽 표지 (책등 그늘 · 위아래 금띠 · 두 줄 금박 테) → 누르면 두께감 있게 넘어가며 대리석 면지,
 * 속표지에 붉은 책갈피 끈과 오늘 쓸 한 절이 한 자씩 → 한 번 더 누르면 오늘 화면.
 */
@Composable
fun IntroCover(s: AppState, onEnter: () -> Unit) {
    val c = Theme.c; val k = s.korean
    val haptic = LocalHapticFeedback.current; val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    val shine = remember { Animatable(0f) }; val open = remember { Animatable(0f) }; val ribbon = remember { Animatable(0f) }
    val enter = remember { Animatable(0f) }; var lit by remember { mutableIntStateOf(0) }
    val t = s.text(); val v = s.progress.nextVerse(s.translation, t, s.chapter) ?: 1
    val verse = remember(s.book, s.chapter, v) { Markup.plain(t.verse(s.chapter, v)) }
    LaunchedEffect(Unit) { shine.animateTo(1f, tween(1800, 400, FastOutSlowInEasing)) }
    LaunchedEffect(step) {
        if (step != 1) return@LaunchedEffect
        launch { open.animateTo(1f, tween(Tokens.Motion.coverOpenMs, easing = CubicBezierEasing(0.55f, 0f, 0.25f, 1f))) }
        launch { kotlinx.coroutines.delay(1000); ribbon.animateTo(1f, tween(Tokens.Motion.ribbonMs, easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1.2f))) }
        kotlinx.coroutines.delay(1400)
        while (lit < verse.length) { lit++; kotlinx.coroutines.delay(Tokens.Motion.litStepMs.toLong()) }
    }
    fun tap() {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        when (step) {
            0 -> step = 1
            1 -> { step = 2; scope.launch { enter.animateTo(1f, tween(Tokens.Motion.introEnterMs, easing = sweep)); onEnter() } }
        }
    }
    BackHandler { if (step < 2) { step = 2; scope.launch { enter.animateTo(1f, tween(Tokens.Motion.introEnterMs, easing = sweep)); onEnter() } } }
    val cover = s.coverColor()
    Box(Modifier.fillMaxSize().graphicsLayer { val e = enter.value; scaleX = 1f + 0.06f * e; scaleY = 1f + 0.06f * e; alpha = 1f - e }
        .clickable(remember { MutableInteractionSource() }, null) { tap() }) {
        // 속표지
        Box(Modifier.fillMaxSize().background(c.leaf)) {
            Box(Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(end = Tokens.Space.s6).width(Tokens.Size.ribbonW).height(Tokens.Size.ribbonH * ribbon.value).drawBehind {
                val p = Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width, size.height); lineTo(size.width / 2, size.height * 0.92f); lineTo(0f, size.height); close() }
                drawPath(p, c.rubric); drawLine(lerp(c.rubric, Color.Black, 0.3f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height * 0.9f), size.width * 0.12f)
            })
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = Tokens.Space.s6), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3, Alignment.CenterVertically)) {
                Text(stringResource(R.string.app_name), style = Theme.title(k, Tokens.Text.title).copy(letterSpacing = 0.04.em))
                Box(Modifier.width(Tokens.Size.coverW * 0.6f).height(Tokens.Size.bandGap * 2).drawBehind {
                    drawLine(c.gilt, Offset(0f, 0f), Offset(size.width, 0f), Tokens.Stroke.giltFine.toPx()); drawLine(c.gilt, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.giltFine.toPx())
                })
                Text((if (s.ownerName.isNotBlank()) stringResource(R.string.mybook_named, s.ownerName) else stringResource(R.string.mybook)) + " · " + fmtDate(R.string.fmt_md, s.today()),
                    style = Theme.small().copy(letterSpacing = 0.12.em))
                Text(buildAnnotatedString {
                    withStyle(SpanStyle(color = c.ink)) { append(verse.take(lit)) }
                    withStyle(SpanStyle(color = c.ink.copy(alpha = 0.18f))) { append(verse.drop(lit)) }
                }, style = Theme.body().copy(textAlign = TextAlign.Center), modifier = Modifier.padding(top = Tokens.Space.s3))
                Text(stringResource(R.string.ref_verse, s.bookName(), s.chapter, v), style = Theme.small().copy(color = c.rubric))
            }
            if (step >= 1 && lit >= verse.length) Breathing(stringResource(R.string.intro_tap_start), c.inkSoft,
                Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(bottom = Tokens.Space.s6).fillMaxWidth())
        }
        // 표지: 왼쪽 가장자리를 축으로 두께감 있게 넘어감. 반을 넘으면 안쪽 면 (대리석 면지)
        val o = open.value
        if (o < 1f) Box(Modifier.fillMaxSize().graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0.5f); cameraDistance = Tokens.Motion.openCamera * density; rotationY = -178f * o
        }.drawBehind {
            if (o < 0.5f) {
                leather(cover)
                // 책등 그늘
                drawRect(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), endX = Tokens.Space.s5.toPx()))
                // 위아래 금띠 두 줄
                val gl = c.gilt; val e = Tokens.Size.veilEdge.toPx(); val g = Tokens.Size.veilBandGap.toPx(); val w = Tokens.Stroke.gilt.toPx()
                for (y in listOf(e, e + g, size.height - e, size.height - e - g)) drawLine(gl, Offset(size.width * 0.08f, y), Offset(size.width * 0.92f, y), w)
                // 두 줄 금박 테
                val inset = size.width * Tokens.Ratio.veilInset + e
                val r1 = Rect(inset, inset + g * 2, size.width - inset, size.height - inset - g * 2)
                drawRoundRect(gl, r1.topLeft, r1.size, CornerRadius(Tokens.Radius.frame.toPx()), style = Stroke(Tokens.Stroke.giltFine.toPx()))
                val d = Tokens.Space.s2.toPx() * 0.6f
                drawRoundRect(gl.copy(alpha = 0.7f), Offset(r1.left + d, r1.top + d), Size(r1.width - 2 * d, r1.height - 2 * d), CornerRadius(Tokens.Radius.frame.toPx()), style = Stroke(Tokens.Stroke.giltFine.toPx() * 0.6f))
            } else marble(c)
            // 넘어가는 동안 그늘
            drawRect(Color.Black.copy(alpha = 0.35f * sin(o * PI).toFloat()))
        }) {
            if (o < 0.5f) {
                Column(Modifier.fillMaxSize().systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4, Alignment.CenterVertically)) {
                    Box(Modifier.size(Tokens.Size.introEmblem).drawBehind { stamp(STAMP_CROSS, lerp(c.gilt, c.giltHi, sin(shine.value * PI).toFloat() * 0.7f)) })
                    Text(stringResource(R.string.app_name), style = Theme.title(k, Tokens.Text.introCover).copy(letterSpacing = 0.06.em,
                        brush = goldBrush(c.giltLo, c.gilt, c.giltHi, shine.value, Tokens.Text.introCover.value * 4f)))
                    Text(stringResource(R.string.intro_cover_caps), style = Theme.small().copy(fontFamily = Fonts.fell, fontSize = Tokens.Text.introCaps, letterSpacing = 0.42.em, color = c.gilt))
                }
                if (shine.value >= 1f) Breathing(stringResource(R.string.intro_tap_open), c.leatherInk,
                    Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(bottom = Tokens.Space.s6 * 2).fillMaxWidth())
            }
        }
    }
}

/** 대리석 면지: 쪽빛 · 붉은빛 · 금빛 · 미색 줄을 물결로 흘려 그림 (표지 안쪽, 뒤집힌 면이라 좌우를 되돌려 그려요). */
private fun DrawScope.marble(c: io.github.graviton94.todaybible.design.Palette) {
    drawRect(Color(0xFFE8DCC2))
    val inset = Tokens.Space.s3.toPx()
    val colors = listOf(Color(0xFF2E3B5C), c.rubric, c.gilt, Color(0xFFEBDAB1), Color(0xFF2E3B5C), Color(0xFF1F2A44))
    val stripe = Tokens.Space.s3.toPx()
    var x = inset; var i = 0
    while (x < size.width - inset) {
        val p = Path(); var y = inset; p.moveTo(x, y)
        while (y <= size.height - inset) {
            val dx = sin(y / 37f + i * 0.7f) * stripe * 1.6f + sin(y / 11f + i) * stripe * 0.3f
            p.lineTo((x + dx).coerceIn(inset, size.width - inset), y); y += 6f
        }
        drawPath(p, colors[i % colors.size], style = Stroke(stripe * (0.5f + (i % 3) * 0.35f)))
        x += stripe * 0.9f; i++
    }
}
