package io.github.graviton94.todaybible.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import io.github.graviton94.todaybible.core.Milestone
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens

/** 발자취마다 어울리는 화첩 판화 (메달 얼굴). */
val MILESTONE_PLATE = mapOf(
    Milestone.BEGINNING to "creation", Milestone.SEVEN to "eden", Milestone.DAWN to "wrestle", Milestone.PASTURES to "goliath",
    Milestone.FORTY to "juniper", Milestone.OLIVE to "dove", Milestone.HOSANNA to "entry", Milestone.TOMB to "ascension",
    Milestone.FIRE to "cornelius", Milestone.FIRSTFRUITS to "ruth", Milestone.STAR to "nativity", Milestone.LAMP to "law_read",
    Milestone.TABLETS to "sinai", Milestone.EPISTLES to "paul_crowd", Milestone.YEAR to "ladder", Milestone.OMEGA to "three_angels",
    Milestone.VERSE7 to "law_read", Milestone.VERSE30 to "sinai", Milestone.VERSE100 to "sermon",
)

/** 날 수로 얻는 발자취의 목표 (호로 진행을 보여요). */
val MILESTONE_DAYS = mapOf(Milestone.SEVEN to 7, Milestone.FORTY to 40, Milestone.YEAR to 365)

/** 아직 얻지 않은 발자취의 진행 (날 수 · 외운 절 수), 셀 수 없는 것은 null. */
fun milestoneProgress(s: io.github.graviton94.todaybible.ui.AppState, m: Milestone): Float? =
    MILESTONE_DAYS[m]?.let { s.progress.days().size.toFloat() / it } ?: Milestone.MEMORY[m]?.let { s.knownDays().size.toFloat() / it }

/** 세피아 (판화 톤): 회색으로 바꾼 뒤 따뜻하게. */
val SEPIA_MATRIX = ColorMatrix(floatArrayOf(
    0.393f * 0.9f, 0.769f * 0.9f, 0.189f * 0.9f, 0f, 0f,
    0.349f * 0.9f, 0.686f * 0.9f, 0.168f * 0.9f, 0f, 0f,
    0.272f * 0.9f, 0.534f * 0.9f, 0.131f * 0.9f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
))

/** 석고 (아직): 회색 · 밝게 · 대비 낮게 · 따뜻한 흰빛. */
private val PLASTER = ColorMatrix(floatArrayOf(
    0.12f, 0.24f, 0.05f, 0f, 150f,
    0.12f, 0.24f, 0.05f, 0f, 146f,
    0.12f, 0.24f, 0.05f, 0f, 138f,
    0f, 0f, 0f, 1f, 0f,
))

/**
 * 판화 메달 (하루의 편지 메달처럼): 얻은 것 = 세피아 판화 + 금빛 두 줄 테, 아직 = 석고 판화 + 가는 테 + 금빛 호(진행).
 */
@Composable
fun PlateMedal(m: Milestone, earned: Boolean, progress: Float?, size: Dp, modifier: Modifier = Modifier) {
    val c = Theme.c
    val img = rememberPlate(MILESTONE_PLATE.getValue(m), small = size < Tokens.Size.medalLg * 1.5f)
    val ring = Tokens.Size.medalRing
    Box(modifier.size(size).drawWithContent {
        drawContent()
        val r = this.size.minDimension / 2; val ctr = center; val g = Tokens.Stroke.gilt.toPx()
        if (earned) {
            drawCircle(c.gilt, r - g / 2, ctr, style = Stroke(g))
            drawCircle(c.gilt, r - ring.toPx() + g / 2, ctr, style = Stroke(g * 0.7f))
        } else {
            drawCircle(c.hair, r - g / 2, ctr, style = Stroke(g))
            progress?.takeIf { it > 0f }?.let { f ->
                val rr = r - g / 2
                drawArc(c.gilt, -90f, 360f * f.coerceAtMost(1f), false, Offset(ctr.x - rr, ctr.y - rr), Size(rr * 2, rr * 2), style = Stroke(g * 1.4f, cap = StrokeCap.Round))
            }
        }
    }, contentAlignment = Alignment.Center) {
        if (img != null) Image(img, null, Modifier.fillMaxSize().padding(ring + Tokens.Stroke.gilt).clip(CircleShape), contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.colorMatrix(if (earned) SEPIA_MATRIX else PLASTER), alpha = if (earned) 1f else Tokens.Alpha.plaster)
        else Box(Modifier.fillMaxSize().padding(ring).clip(CircleShape).drawBehind { drawCircle(c.hair) })
    }
}
