// 자동 생성: scripts/generate.py (design/tokens.json). 손으로 고치지 말 것.
package io.github.graviton94.todaybible.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 한 테마의 재료 색. */
data class Palette(val paper: Color, val leaf: Color, val ink: Color, val inkSoft: Color, val unwritten: Color, val hair: Color, val rubric: Color, val gilt: Color, val giltText: Color, val leather: Color, val leatherInk: Color, val shade: Color, val scrim: Color)

object Tokens {
    val light = Palette(paper = Color(0xFFECE2CC), leaf = Color(0xFFF7F1E3), ink = Color(0xFF2A2119), inkSoft = Color(0xFF5C4E3E), unwritten = Color(0xFF76674F), hair = Color(0x2E2A2119), rubric = Color(0xFF8C2117), gilt = Color(0xFFA07B36), giltText = Color(0xFF7A5C24), leather = Color(0xFF4A1913), leatherInk = Color(0xFFEBDAB1), shade = Color(0x242A2119), scrim = Color(0x661A120C))
    val dark = Palette(paper = Color(0xFF15110D), leaf = Color(0xFF201A14), ink = Color(0xFFEEE4D0), inkSoft = Color(0xFFBFB09A), unwritten = Color(0xFF9A8D78), hair = Color(0x2EEEE4D0), rubric = Color(0xFFD46B5C), gilt = Color(0xFFC7A35D), giltText = Color(0xFFD6B672), leather = Color(0xFF5A1F18), leatherInk = Color(0xFFEEDDB4), shade = Color(0x66000000), scrim = Color(0xA0000000))
    object Space {
        val s1 = 4.dp
        val s2 = 8.dp
        val s3 = 12.dp
        val s4 = 16.dp
        val s5 = 24.dp
        val s6 = 40.dp
    }
    object Radius {
        val page = 18.dp
        val button = 4.dp
        val card = 10.dp
    }
    object Stroke {
        val hair = 1.dp
        val rule = 2.dp
        val gilt = 1.5.dp
    }
    object Text {
        val verse = 21.sp
        val verseEn = 22.sp
        val body = 16.sp
        val label = 15.sp
        val small = 13.sp
        val title = 24.sp
        val display = 34.sp
        val initial = 40.sp
    }
    object Motion {
        const val inkMs = 600
        const val veilMs = 1400
        const val turnTilt = 2.5f
        const val turnLift = 18.0f
        const val turnShade = 0.35f
        const val turnEdge = 12.0f
        const val breathDrift = 24.0f
    }
    object Ratio {
        const val plateAspect = 0.766f
        const val veilInset = 0.06f
    }
}
