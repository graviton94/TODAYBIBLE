package io.github.graviton94.todaybible.ui

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import io.github.graviton94.todaybible.R
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.graviton94.todaybible.core.Milestone
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens

/** 머리줄: 왼쪽 권 · 장, 오른쪽 작은 숫자. 아래 머리줄 한 줄. */
@Composable
fun RunningHead(left: String, right: String, korean: Boolean, modifier: Modifier = Modifier) {
    val c = Theme.c
    Row(
        modifier.fillMaxWidth().drawBehind { drawLine(c.hair, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.hair.toPx()) }
            .padding(bottom = Tokens.Space.s2),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(left, style = Theme.head(korean), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Text(right, style = Theme.small(), maxLines = 1, modifier = Modifier.padding(start = Tokens.Space.s3))
    }
}

/**
 * 버튼: 무광 가죽 + 안쪽 금박 테 (위아래 변은 책등 띠처럼 두 줄). 누르면 가죽이 한 톤 깊어짐.
 * quiet = 바탕 없이 머리카락 테 한 줄. 비활성 = 옅은 바탕 · 흐린 테.
 */
@Composable
fun BookButton(text: String, modifier: Modifier = Modifier, quiet: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Theme.c
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val fill = when { quiet -> Color.Transparent; !enabled -> c.hair; pressed -> c.leatherDeep; else -> c.leather }
    val line = when { quiet || !enabled -> c.hair; else -> c.gilt.copy(alpha = Tokens.Alpha.frame) }
    Box(
        modifier.heightIn(min = Tokens.Size.touch).clip(RoundedCornerShape(Tokens.Radius.button)).background(fill)
            .drawBehind { giltFrame(line, bands = !quiet && enabled) }
            .clickable(source, null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Theme.label().copy(color = if (quiet) c.ink else if (enabled) c.leatherInk else c.inkSoft, textAlign = TextAlign.Center), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** 안쪽 금박 테 (버튼 · 토스트 공통). bands = 위아래 변에 한 줄 더 (책등 띠). */
fun DrawScope.giltFrame(color: Color, bands: Boolean) {
    val i = Tokens.Size.frameInset.toPx(); val w = Tokens.Stroke.giltFine.toPx()
    drawRoundRect(color, Offset(i, i), Size(size.width - 2 * i, size.height - 2 * i), CornerRadius(Tokens.Radius.frame.toPx()), style = Stroke(w))
    if (bands) {
        val g = i + Tokens.Size.bandGap.toPx(); val x0 = i + Tokens.Radius.frame.toPx(); val x1 = size.width - x0
        drawLine(color, Offset(x0, g), Offset(x1, g), w)
        drawLine(color, Offset(x0, size.height - g), Offset(x1, size.height - g), w)
    }
}

/**
 * 시트 (아래에서 올라오는 한 장): 장 고르기 · 발자취 · 확인 창 공통.
 * 종이 바탕 + 위쪽 금선 두 줄 (책등 띠) + 금빛 손잡이. 바깥을 누르거나 뒤로 가면 닫힘.
 */
@Composable
fun BookSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Theme.c
    val shown = remember { Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(Tokens.Motion.fadeMs)) }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = shown.value }.background(c.scrim)
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier.fillMaxWidth().graphicsLayer { translationY = (1f - shown.value) * size.height * 0.15f }
                .clip(RoundedCornerShape(topStart = Tokens.Radius.sheet, topEnd = Tokens.Radius.sheet)).background(c.leaf)
                .drawBehind {
                    // 둥근 모서리 안쪽에서 시작하는 두 줄 (가장자리에 붙으면 떠 보임)
                    val w = Tokens.Stroke.giltFine.toPx(); val x = Tokens.Space.s5.toPx(); val y = Tokens.Space.s1.toPx(); val g = Tokens.Size.bandGap.toPx()
                    drawLine(c.gilt, Offset(x, y), Offset(size.width - x, y), w)
                    drawLine(c.gilt, Offset(x, y + g), Offset(size.width - x, y + g), w)
                }
                .clickable(remember { MutableInteractionSource() }, null) {}
                .navigationBarsPadding().padding(horizontal = Tokens.Space.s5).padding(top = Tokens.Space.s3, bottom = Tokens.Space.s5),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
        ) {
            Box(Modifier.size(Tokens.Size.handleW, Tokens.Size.handleH).clip(RoundedCornerShape(Tokens.Size.handleH)).background(c.gilt.copy(alpha = Tokens.Alpha.handle)))
            content()
        }
    }
}

/** 토스트: 짧은 한 줄. 가죽 띠 + 금박 테, 화면 아래 이름표 위에 잠깐. */
@Composable
fun BookToast(text: String) {
    val c = Theme.c
    Row(
        Modifier.heightIn(min = Tokens.Size.toastMinH).clip(RoundedCornerShape(Tokens.Radius.button)).background(c.leather)
            .drawBehind { giltFrame(c.gilt.copy(alpha = Tokens.Alpha.frame), bands = true) }
            .padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2),
    ) {
        StampMark(STAMP_CROSS, c.gilt, Modifier.size(Tokens.Size.iconSm))
        Text(text, style = Theme.label().copy(color = c.leatherInk), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 밑줄 탭: 고른 것만 붉은 밑줄. */
@Composable
fun UnderlineTabs(items: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val c = Theme.c
    Row(modifier.fillMaxWidth()) {
        items.forEachIndexed { i, s ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = Tokens.Size.tab).clickable(role = Role.Tab) { onSelect(i) }.drawBehind {
                    val h = if (on) Tokens.Stroke.rule.toPx() else Tokens.Stroke.hair.toPx()
                    drawRect(if (on) c.rubric else c.hair, Offset(0f, size.height - h), androidx.compose.ui.geometry.Size(size.width, h))
                },
                contentAlignment = Alignment.Center,
            ) { Text(s, style = Theme.label().copy(color = if (on) c.ink else c.inkSoft, textAlign = TextAlign.Center), maxLines = 2, modifier = Modifier.padding(horizontal = Tokens.Space.s1)) }
        }
    }
}

/** 세로로 쌓는 페이지 본문 (여백 토큰). */
@Composable
fun PageColumn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) { content() }
}

@Composable
fun RowScope.Spacer1() = Box(Modifier.weight(1f))

// ── 발자취 메달: 무광 가죽 원 + 금박 테 한 줄 + 금박 선 문양 하나 (SVG 경로, 64×64 기준) ──
private val ICONS: Map<String, List<String>> = mapOf(
    "light" to listOf("M26 32a6 6 0 1 0 12 0a6 6 0 1 0 -12 0", "M32 18v-4M32 50v-4M18 32h-4M50 32h-4M22 22l-3-3M45 45l-3-3M22 42l-3 3M42 22l3-3"),
    "seven" to listOf("M19 24l5 16l5-16M35 24v16M43 24v16M17 22h30M17 42h30"),
    "dawn" to listOf("M14 40h36", "M22 40a10 10 0 0 1 20 0", "M32 24v-5M22 28l-3-3M42 28l3-3"),
    "crook" to listOf("M34 48V25a6 6 0 1 0 -11 0"),
    "forty" to listOf("M14 44l11-14l7 8l6-9l12 15z"),
    "leaf" to listOf("M20 44c2-14 12-22 26-24c-2 14-12 24-26 24z", "M20 44l18-18"),
    "palm" to listOf("M32 48V22M32 22c-6-4-11-4-15 0M32 22c6-4 11-4 15 0M32 28c-5-2-9-1-12 2M32 28c5-2 9-1 12 2"),
    "tomb" to listOf("M18 46V32a12 12 0 0 1 24 0v14", "M38 40a6 6 0 1 0 12 0a6 6 0 1 0 -12 0", "M14 46h36"),
    "flame" to listOf("M32 16c6 8 9 12 9 18a9 9 0 0 1 -18 0c0-4 2-7 4-10c0 3 2 5 3 6c0-5 1-9 2-14z"),
    "wheat" to listOf("M32 48V18", "M29 22c-3 2-3 6 0 8M35 22c3 2 3 6 0 8M29 30c-3 2-3 6 0 8M35 30c3 2 3 6 0 8"),
    "star" to listOf("M32 16l3 10l10-3l-7 8l7 8l-10-3l-3 10l-3-10l-10 3l7-8l-7-8l10 3z"),
    "lamp" to listOf("M15 36c5 6 25 6 30 0z", "M45 36c3-1 5 0 6 1", "M42 30c-2 2-2 4 0 5c2-1 2-3 0-5z"),
    "tablets" to listOf("M18 46V25a5 5 0 0 1 10 0v21z", "M36 46V25a5 5 0 0 1 10 0v21z"),
    "scroll" to listOf("M20 20h22v24H22a3 3 0 0 1 -3 -3V22", "M46 14L31 35"),
    "wreath" to listOf("M26 47c-8-4-11-12-9-21M38 47c8-4 11-12 9-21", "M27 32h10M32 27v10"),
    "ao" to listOf("M20 40l5-16l5 16M22 34h6", "M36 40h3a6 7 0 1 1 6 0h3"),
)
private val parsed = HashMap<String, List<Path>>()
private fun paths(id: String): List<Path> = parsed.getOrPut(id) { ICONS[id].orEmpty().map { PathParser().parsePathString(it).toPath() } }

fun DrawScope.medal(m: Milestone, earned: Boolean, leather: Color, gilt: Color, faint: Color) {
    val s = size.minDimension / 64f
    val center = Offset(size.width / 2, size.height / 2)
    drawCircle(if (earned) leather else Color.Transparent, 30f * s, center)
    drawCircle(if (earned) gilt else faint, 26.5f * s, center, style = Stroke(1.2f * s))
    if (!earned) drawCircle(faint, 30f * s, center, style = Stroke(1f * s))
    val ink = if (earned) gilt else faint
    withTransform({ translate(center.x - 32f * s, center.y - 32f * s); scale(s, s, Offset.Zero) }) {
        paths(m.icon).forEach { drawPath(it, ink, style = Stroke(1.7f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)) }
    }
}

// ── 도장 표: 글꼴에 없는 기호라 직접 그림 (24×24 기준) ──
private fun crossPattee(): Path = Path().apply {
    // 가운데로 좁아지는 네 팔
    fun arm(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float) { moveTo(ax, ay); lineTo(bx, by); lineTo(cx, cy); lineTo(dx, dy); close() }
    arm(10.6f, 10.6f, 7.5f, 2f, 16.5f, 2f, 13.4f, 10.6f)
    arm(13.4f, 10.6f, 22f, 7.5f, 22f, 16.5f, 13.4f, 13.4f)
    arm(13.4f, 13.4f, 16.5f, 22f, 7.5f, 22f, 10.6f, 13.4f)
    arm(10.6f, 13.4f, 2f, 16.5f, 2f, 7.5f, 10.6f, 10.6f)
    addRect(androidx.compose.ui.geometry.Rect(10.4f, 10.4f, 13.6f, 13.6f))
}
private fun latinCross(): Path = Path().apply {
    addRect(androidx.compose.ui.geometry.Rect(10.6f, 2f, 13.4f, 22f)); addRect(androidx.compose.ui.geometry.Rect(5.5f, 6.8f, 18.5f, 9.6f))
}
private fun hedera(): Path = PathParser().parsePathString("M12 21C5 15.5 3.5 10.5 6 7.8C8 5.7 10.8 6.6 12 9C13.2 6.6 16 5.7 18 7.8C20.5 10.5 19 15.5 12 21Z").toPath()

const val STAMP_CROSS = "✠"
val STAMPS = listOf("✠", "✝", "❦")

/** 도장 표 하나 (✠ ✝ ❦). */
fun DrawScope.stamp(mark: String, color: Color, at: Offset = center, side: Float = size.minDimension) {
    val s = side / 24f
    withTransform({ translate(at.x - 12f * s, at.y - 12f * s); scale(s, s, Offset.Zero) }) {
        when (mark) {
            "✝" -> drawPath(latinCross(), color)
            "❦" -> { drawPath(hedera(), color); drawLine(color, Offset(12f, 9f), Offset(15.5f, 2.5f), 1.6f, cap = androidx.compose.ui.graphics.StrokeCap.Round) }
            else -> drawPath(crossPattee(), color)
        }
    }
}

@Composable
fun StampMark(mark: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.drawBehind { stamp(mark, color) })
}

/** 평생권 안내 칸 (오늘 화면 아래 · 설정 맨 위): 가죽 바탕에 금박, 무엇이 열리는지 한 줄 · 누르면 평생권 화면. */
@Composable
fun LifetimeCard(s: AppState, modifier: Modifier = Modifier) {
    if (s.lifetime.owned) return
    val c = Theme.c
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.card)).background(c.leather).drawBehind { giltFrame(c.gilt.copy(alpha = Tokens.Alpha.frame), bands = true) }
        .clickable(role = Role.Button) { s.purchaseOpen = true }.padding(Tokens.Space.s5), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            LockMark(c.gilt, Modifier.size(Tokens.Size.lock))
            Text(stringResource(R.string.lifetime), style = Theme.title(s.korean).copy(color = c.gilt), maxLines = 1)
        }
        Text(stringResource(R.string.lifetime_card_line), style = Theme.small().copy(color = c.leatherInk))
        Text(stringResource(R.string.lifetime_card_go, s.lifetime.price ?: stringResource(R.string.lifetime_price_hint)), style = Theme.label().copy(color = c.leatherInk), maxLines = 1)
    }
}

/** 평생권 표시: 금빛 자물쇠 + ‘평생권’. 누르면 평생권 화면 (onClick 이 있으면). */
@Composable
fun PremiumTag(modifier: Modifier = Modifier) {
    val c = Theme.c
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
        LockMark(c.giltText, Modifier.size(Tokens.Size.lock))
        Text(stringResource(R.string.premium_tag), style = Theme.small().copy(color = c.giltText), maxLines = 1)
    }
}

@Composable
fun LockMark(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.drawBehind {
        val w = Tokens.Stroke.giltFine.toPx() * 1.5f; val bw = size.width * 0.8f; val bh = size.height * 0.5f
        val x = (size.width - bw) / 2; val y = size.height - bh
        drawRoundRect(color, Offset(x, y), Size(bw, bh), CornerRadius(w))
        val r = bw * 0.32f
        drawArc(color, 180f, 180f, false, Offset(size.width / 2 - r, y - r), Size(r * 2, r * 2), style = Stroke(w))
        drawLine(color, Offset(size.width / 2 - r, y - 0.5f), Offset(size.width / 2 - r, y - r * 0.2f), w)
        drawLine(color, Offset(size.width / 2 + r, y - 0.5f), Offset(size.width / 2 + r, y - r * 0.2f), w)
    })
}

/**
 * 장 첫머리 글자: 아직이면 붉은 블랙레터 장 번호, 마친 장이면 금박 머리글자
 * (가죽 네모 · 안쪽 금박 테 · 금빛 숫자). 중세 필사본처럼 마칠 때마다 하나씩 생김.
 */
@Composable
fun ChapterInitial(chapter: Int, done: Boolean, modifier: Modifier = Modifier, box: androidx.compose.ui.unit.Dp = Tokens.Size.initialBox) {
    val c = Theme.c
    if (!done) {
        Text("$chapter", style = Theme.number().copy(fontSize = Tokens.Text.initial), modifier = modifier)
        return
    }
    Box(modifier.size(box).clip(RoundedCornerShape(Tokens.Radius.chip)).background(c.leather).drawBehind { giltFrame(c.gilt.copy(alpha = Tokens.Alpha.frame), bands = false) },
        contentAlignment = Alignment.Center) {
        Text("$chapter", style = Theme.number().copy(fontSize = Tokens.Text.initialBox * (box / Tokens.Size.initialBox), color = c.gilt), maxLines = 1)
    }
}
