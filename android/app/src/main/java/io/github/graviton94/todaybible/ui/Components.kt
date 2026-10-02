package io.github.graviton94.todaybible.ui

import androidx.compose.foundation.background
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
fun RunningHead(left: String, right: String, korean: Boolean) {
    val c = Theme.c
    Row(
        Modifier.fillMaxWidth().drawBehind { drawLine(c.hair, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.hair.toPx()) }
            .padding(bottom = Tokens.Space.s2),
        verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(left, style = Theme.head(korean), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Text(right, style = Theme.small(), maxLines = 1, modifier = Modifier.padding(start = Tokens.Space.s3))
    }
}

/** 가죽 버튼 (주요 동작) 또는 조용한 버튼. 높이 56dp 이상. */
@Composable
fun BookButton(text: String, modifier: Modifier = Modifier, quiet: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Theme.c
    Box(
        modifier.heightIn(min = 56.dp).clip(RoundedCornerShape(Tokens.Radius.button))
            .background(if (quiet) Color.Transparent else if (enabled) c.leather else c.hair)
            .then(if (quiet) Modifier.drawBehind { drawRoundRect(c.hair, style = Stroke(Tokens.Stroke.hair.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(Tokens.Radius.button.toPx())) } else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = Tokens.Space.s4, vertical = Tokens.Space.s3),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Theme.label().copy(color = if (quiet) c.ink else c.leatherInk, textAlign = TextAlign.Center), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                Modifier.weight(1f).heightIn(min = 48.dp).clickable(role = Role.Tab) { onSelect(i) }.drawBehind {
                    val h = if (on) Tokens.Stroke.rule.toPx() else Tokens.Stroke.hair.toPx()
                    drawRect(if (on) c.rubric else c.hair, Offset(0f, size.height - h), androidx.compose.ui.geometry.Size(size.width, h))
                },
                contentAlignment = Alignment.Center,
            ) { Text(s, style = Theme.label().copy(color = if (on) c.ink else c.inkSoft), maxLines = 1) }
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
    "seven" to listOf("M22 24v16M28 24v16M33 24l4 16l4 -16M45 24v16"),
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
