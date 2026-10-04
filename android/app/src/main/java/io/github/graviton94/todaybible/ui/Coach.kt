package io.github.graviton94.todaybible.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens

/**
 * 첫 안내 (게임 첫 안내처럼): 화면에 처음 들어오면 버튼마다 동그라미를 치고 말풍선으로 하나씩 알려 줘요.
 * 화면마다 한 번. 설정 ‘사용법 다시 보기’로 다시 볼 수 있어요.
 * 버튼은 Modifier.coach("이름") 으로 자리를 알려 두고, 화면마다 차례(steps)를 정해요.
 */
object Coach {
    val targets = mutableStateMapOf<String, Rect>()

    /** 화면마다 안내 차례: (버튼 이름, 말). 버튼이 화면에 없으면 그 차례는 건너뛰어요. */
    val steps: Map<String, List<Pair<String, Int>>> = mapOf(
        "today" to listOf("today_card" to R.string.coach_today_card, "today_go" to R.string.coach_today_go, "today_read" to R.string.coach_today_read,
            "tabs" to R.string.coach_tabs, "settings" to R.string.coach_settings),
        "library" to listOf("lib_marks" to R.string.coach_lib_marks, "lib_find" to R.string.coach_lib_find, "lib_testament" to R.string.coach_lib_testament, "lib_book" to R.string.coach_lib_book),
        "reader" to listOf("read_text" to R.string.coach_read_text, "read_bookmark" to R.string.coach_read_bookmark, "read_listen" to R.string.coach_read_listen, "read_copy" to R.string.coach_read_copy),
        "copy0" to listOf("copy_tabs" to R.string.coach_copy_tabs, "aloud_modes" to R.string.coach_aloud_modes, "aloud_mic" to R.string.coach_aloud_mic),
        "copy1" to listOf("type_grid" to R.string.coach_type_grid, "type_start" to R.string.coach_type_start),
        "copy2" to listOf("hand_sheet" to R.string.coach_hand_sheet, "hand_buttons" to R.string.coach_hand_buttons),
        "record" to listOf("rec_calendar" to R.string.coach_rec_calendar, "rec_plates" to R.string.coach_rec_plates, "rec_miles" to R.string.coach_rec_miles),
    )
}

/** 첫 안내가 가리킬 자리 (화면에서 사라지면 지워져요). */
fun Modifier.coach(id: String): Modifier = this then CoachElement(id)

private class CoachNode(var id: String) : Modifier.Node(), androidx.compose.ui.node.GlobalPositionAwareModifierNode {
    override fun onGloballyPositioned(coordinates: androidx.compose.ui.layout.LayoutCoordinates) { Coach.targets[id] = coordinates.boundsInRoot() }
    override fun onDetach() { Coach.targets.remove(id) }
}
private data class CoachElement(val id: String) : androidx.compose.ui.node.ModifierNodeElement<CoachNode>() {
    override fun create() = CoachNode(id)
    override fun update(node: CoachNode) { node.id = id }
}

/** 지금 화면의 첫 안내 (아직 안 봤으면). */
@Composable
fun CoachOverlay(s: AppState, screen: String) {
    if (screen in s.coachSeen) return
    val all = Coach.steps[screen] ?: return
    var ready by remember(screen) { mutableStateOf(false) }
    // 페이지가 넘어가 자리 잡을 때까지 잠깐
    LaunchedEffect(screen) { kotlinx.coroutines.delay(Tokens.Motion.coachDelayMs.toLong()); ready = true }
    if (!ready) return
    val winH = with(LocalDensity.current) { androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx() }
    // 화면 안에 보이는 버튼만
    val steps = all.filter { Coach.targets[it.first]?.let { r -> r.width > 0f && r.height > 0f && r.top >= 0f && r.bottom <= winH * 1.05f } == true }
    if (steps.isEmpty()) return
    var i by remember(screen) { mutableIntStateOf(0) }
    val (id, text) = steps[i.coerceIn(steps.indices)]
    val r = Coach.targets[id] ?: return
    val c = Theme.c; val k = s.korean
    val dens = LocalDensity.current
    fun done() { s.coachDone(screen) }
    BoxWithConstraints(Modifier.fillMaxSize()
        // 뒤 화면은 눌리지 않게
        .clickable(remember { MutableInteractionSource() }, null) { if (i < steps.lastIndex) i++ else done() }) {
        val pad = with(dens) { Tokens.Space.s2.toPx() }
        val hole = Rect(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad)
        Canvas(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
            drawRect(c.scrim.copy(alpha = Tokens.Alpha.coachScrim))
            val rad = CornerRadius(Tokens.Radius.card.toPx())
            drawRoundRect(androidx.compose.ui.graphics.Color.Black, hole.topLeft, hole.size, rad, blendMode = BlendMode.Clear)
            drawRoundRect(c.gilt, hole.topLeft, hole.size, rad, style = Stroke(Tokens.Stroke.rule.toPx() * 1.5f))
        }
        // 말풍선: 가리킨 곳이 위쪽이면 아래에, 아래쪽이면 위에
        val h = with(dens) { maxHeight.toPx() }
        val below = hole.center.y < h / 2
        val gap = with(dens) { Tokens.Space.s3.toPx() }
        var bubbleH by remember(id) { mutableIntStateOf(0) }
        val top = androidx.compose.foundation.layout.WindowInsets.statusBars.getTop(dens) + with(dens) { Tokens.Space.s2.roundToPx() }
        val bottom = androidx.compose.foundation.layout.WindowInsets.navigationBars.getBottom(dens)
        val y = if (below) hole.bottom + gap else hole.top - gap - bubbleH
        Column(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s5)
            // 상태 표시줄 · 아래 이름표에 걸리지 않게
            .offset { IntOffset(0, y.toInt().coerceIn(top, (h - bubbleH - bottom).toInt().coerceAtLeast(top))) }
            .onGloballyPositioned { bubbleH = it.size.height }
            .clip(RoundedCornerShape(Tokens.Radius.card)).background(c.leaf).padding(Tokens.Space.s4),
            verticalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
            Text(stringResource(text), style = Theme.body())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                Text(stringResource(R.string.coach_skip), style = Theme.small().copy(color = c.inkSoft), maxLines = 1,
                    modifier = Modifier.heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Button) { done() })
                Text("${i + 1} / ${steps.size}", style = Theme.small(), maxLines = 1, modifier = Modifier.weight(1f).heightIn(min = Tokens.Size.tab).wrapContentHeight(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(stringResource(if (i < steps.lastIndex) R.string.coach_next else R.string.coach_done), style = Theme.label().copy(color = c.rubric), maxLines = 1,
                    modifier = Modifier.heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Button) { if (i < steps.lastIndex) i++ else done() })
            }
        }
    }
}
