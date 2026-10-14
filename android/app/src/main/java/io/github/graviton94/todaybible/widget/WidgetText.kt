package io.github.graviton94.todaybible.widget

import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp

/**
 * 위젯 말씀 맞추기 (1.5): 칸에 다 들어갈 때까지 글자를 한 단계씩 (0.5sp) 줄여요. 가장 작은 크기 (minSp) 에서도 넘치면 그때만 말줄임.
 * 성경 말씀이 중간에서 끊기지 않게.
 */
internal fun fitText(m: TextMeasurer, text: String, style: TextStyle, maxW: Int, maxH: Float, minSp: Float): TextLayoutResult {
    var sp = style.fontSize.value
    while (true) {
        val st = style.copy(fontSize = sp.sp)
        val lay = m.measure(text, st, constraints = Constraints(maxWidth = maxW.coerceAtLeast(1)))
        if (lay.size.height <= maxH || sp <= minSp) {
            if (lay.size.height <= maxH) return lay
            // 가장 작은 크기에서도 넘침: 들어가는 줄까지만, 끝은 말줄임
            val one = m.measure("가", st).size.height.coerceAtLeast(1)
            val lines = (maxH / one).toInt().coerceAtLeast(1)
            return m.measure(text, st, maxLines = lines, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = maxW.coerceAtLeast(1)))
        }
        sp -= 0.5f
    }
}
