// 자동 생성: scripts/generate.py (design/tokens.json). 손으로 고치지 말 것.
package io.github.graviton94.todaybible.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 한 테마의 재료 색. */
data class Palette(val paper: Color, val leaf: Color, val ink: Color, val inkSoft: Color, val unwritten: Color, val hair: Color, val rubric: Color, val gilt: Color, val giltText: Color, val leather: Color, val leatherDeep: Color, val leatherInk: Color, val shade: Color, val scrim: Color, val penInk: Color, val noteLine: Color)

object Tokens {
    val light = Palette(paper = Color(0xFFECE2CC), leaf = Color(0xFFF7F1E3), ink = Color(0xFF2A2119), inkSoft = Color(0xFF5C4E3E), unwritten = Color(0xFF76674F), hair = Color(0x2E2A2119), rubric = Color(0xFF8C2117), gilt = Color(0xFFA07B36), giltText = Color(0xFF7A5C24), leather = Color(0xFF4A1913), leatherDeep = Color(0xFF3A120E), leatherInk = Color(0xFFEBDAB1), shade = Color(0x242A2119), scrim = Color(0x661A120C), penInk = Color(0xFF1F2A44), noteLine = Color(0x291F2A44))
    val dark = Palette(paper = Color(0xFF15110D), leaf = Color(0xFF201A14), ink = Color(0xFFEEE4D0), inkSoft = Color(0xFFBFB09A), unwritten = Color(0xFF9A8D78), hair = Color(0x2EEEE4D0), rubric = Color(0xFFD46B5C), gilt = Color(0xFFC7A35D), giltText = Color(0xFFD6B672), leather = Color(0xFF5A1F18), leatherDeep = Color(0xFF46170F), leatherInk = Color(0xFFEEDDB4), shade = Color(0x66000000), scrim = Color(0xA0000000), penInk = Color(0xFFC9D3EE), noteLine = Color(0x29C9D3EE))
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
        val sheet = 18.dp
        val button = 6.dp
        val frame = 3.dp
        val card = 10.dp
        val chip = 4.dp
    }
    object Stroke {
        val hair = 1.dp
        val rule = 2.dp
        val gilt = 1.5.dp
        val giltFine = 1.dp
    }
    object Size {
        val touch = 56.dp
        val row = 52.dp
        val tab = 48.dp
        val rowTall = 64.dp
        val iconSm = 18.dp
        val icon = 22.dp
        val iconMd = 26.dp
        val medal = 44.dp
        val emblem = 64.dp
        val medalLg = 84.dp
        val shelf = 76.dp
        val shelfBase = 3.dp
        val spineGap = 2.dp
        val dot = 8.dp
        val plateDot = 5.dp
        val handleW = 36.dp
        val handleH = 3.dp
        val frameInset = 4.dp
        val bandGap = 3.dp
        val veilEdge = 10.dp
        val veilBandGap = 4.dp
        val edgeShade = 8.dp
        val toastMinH = 48.dp
        val sheetMaxGrid = 420.dp
        val widgetPad = 16.dp
        val lock = 14.dp
        val coverW = 132.dp
        val cardEdge = 22.dp
        val cardBandGap = 5.dp
        val cardPad = 64.dp
        val cardMark = 22.dp
        val cardMedal = 120.dp
        val cardGap = 24.dp
        val noteMargin = 44.dp
        val initialBox = 56.dp
        val initialHome = 44.dp
        val hiddenField = 1.dp
    }
    object Text {
        val verse = 21.sp
        val verseEn = 22.sp
        val body = 16.sp
        val label = 15.sp
        val small = 13.sp
        val brand = 20.sp
        val title = 24.sp
        val display = 34.sp
        val initial = 40.sp
        val initialLg = 64.sp
        val typed = 18.sp
        val typedEn = 19.sp
        val cardVerse = 26.sp
        val cardVerseEn = 27.sp
        val cardVerseMin = 15.sp
        val cardRef = 15.sp
        val cardFoot = 12.sp
        val cardName = 28.sp
        val pen = 23.sp
        val penLine = 34.sp
        val initialBox = 30.sp
        val gridInitial = 20.sp
    }
    object Motion {
        const val inkMs = 600
        const val veilMs = 1400
        const val pageMs = 420
        const val toastMs = 2400
        const val fadeMs = 220
        const val typeSettleMs = 350
        const val turnSnap = 0.25f
        const val turnTilt = 2.5f
        const val turnLift = 18.0f
        const val turnShade = 0.35f
        const val turnEdge = 12.0f
        const val breathDrift = 24.0f
        const val aloudNormal = 1.25f
        const val sealMs = 900
        const val sealHoldMs = 1600
        const val openDrawMs = 700
        const val openShineMs = 650
        const val openHoldMs = 900
        const val openAngle = 100.0f
        const val openCamera = 14.0f
    }
    object Ratio {
        const val plateAspect = 0.766f
        const val veilInset = 0.06f
        const val veilMark = 0.16f
        const val plateWidth = 0.82f
        const val stampInCell = 0.62f
        const val photoAspect = 1.3333f
        const val scaleLarge = 1.15f
        const val scaleLarger = 1.3f
        const val shineWidth = 0.18f
        const val welcomeArt = 1.25f
        const val initialFinish = 1.25f
    }
    object Alpha {
        const val faint = 0.5f
        const val medalFaint = 0.55f
        const val future = 0.6f
        const val rest = 0.75f
        const val handle = 0.7f
        const val frame = 0.85f
        const val nib = 0.14f
        const val shine = 0.22f
        const val peek = 0.93f
    }
    object Leading {
        const val verse = 1.8f
        const val body = 1.6f
        const val label = 1.4f
        const val small = 1.45f
        const val title = 1.25f
        const val verseNumber = 1.25f
    }
    object Tracking {
        const val head = 0.06f
        const val headEn = 0.08f
    }
    object Px {
        const val shareW = 1080.0f
        const val shareH = 1350.0f
        const val shareDensity = 2.75f
        const val plateFaint = 0.12f
        const val cardFill = 0.55f
        const val cardPlate = 0.62f
        const val medalTop = 0.24f
    }
}
