// 자동 생성: scripts/generate.py (design/tokens.json). 손으로 고치지 말 것.
package io.github.graviton94.todaybible.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 한 테마의 재료 색. */
data class Palette(val paper: Color, val leaf: Color, val ink: Color, val inkSoft: Color, val unwritten: Color, val hair: Color, val rubric: Color, val wrong: Color, val gilt: Color, val giltText: Color, val leather: Color, val leatherDeep: Color, val leatherInk: Color, val shade: Color, val scrim: Color, val penInk: Color, val noteLine: Color, val mark: Color, val graphite: Color, val giltHi: Color, val giltLo: Color)

object Tokens {
    val light = Palette(paper = Color(0xFFEDE5D6), leaf = Color(0xFFF4EFE6), ink = Color(0xFF1F1A15), inkSoft = Color(0xFF6E655A), unwritten = Color(0xFFA39886), hair = Color(0x241F1A15), rubric = Color(0xFF7A5418), wrong = Color(0xFF8C2117), gilt = Color(0xFF9A6B1F), giltText = Color(0xFF7A5418), leather = Color(0xFF1F1A15), leatherDeep = Color(0xFF000000), leatherInk = Color(0xFFF4EFE6), shade = Color(0x242A2119), scrim = Color(0x661A120C), penInk = Color(0xFF1F2A44), noteLine = Color(0x291F2A44), mark = Color(0x70F2D25C), graphite = Color(0xFF5E5A55), giltHi = Color(0xFFF2DC9B), giltLo = Color(0xFF6E5020))
    val dark = Palette(paper = Color(0xFF201912), leaf = Color(0xFF17110C), ink = Color(0xFFEADFC8), inkSoft = Color(0xFFA39886), unwritten = Color(0xFF7D7262), hair = Color(0x24EADFC8), rubric = Color(0xFFD6B672), wrong = Color(0xFFD46B5C), gilt = Color(0xFFC7A35D), giltText = Color(0xFFD6B672), leather = Color(0xFFEFE6D2), leatherDeep = Color(0xFFD9CDB4), leatherInk = Color(0xFF1F1A15), shade = Color(0x66000000), scrim = Color(0xA0000000), penInk = Color(0xFFC9D3EE), noteLine = Color(0x29C9D3EE), mark = Color(0x55C9A43A), graphite = Color(0xFFA9A39A), giltHi = Color(0xFFF2DC9B), giltLo = Color(0xFF7A5C24))
    val candle = Palette(paper = Color(0xFF17100A), leaf = Color(0xFF1E150D), ink = Color(0xFFEBD9B4), inkSoft = Color(0xFFB8A07A), unwritten = Color(0xFF8E7A5A), hair = Color(0x2EEBD9B4), rubric = Color(0xFFE9C57A), wrong = Color(0xFFD9774F), gilt = Color(0xFFD9AE5F), giltText = Color(0xFFE9C57A), leather = Color(0xFFE9D6AE), leatherDeep = Color(0xFFD4BE92), leatherInk = Color(0xFF1E150D), shade = Color(0x66000000), scrim = Color(0xA0000000), penInk = Color(0xFFE9D6AE), noteLine = Color(0x26E9C57A), mark = Color(0x40E9C57A), graphite = Color(0xFFA89A86), giltHi = Color(0xFFF2DC9B), giltLo = Color(0xFF6E5020))
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
        val button = 0.dp
        val frame = 0.dp
        val card = 2.dp
        val chip = 2.dp
    }
    object Stroke {
        val hair = 1.dp
        val pen = 2.6.dp
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
        val bar = 4.dp
        val tabMark = 18.dp
        val dotDay = 11.dp
        val segment = 3.dp
        val romanCol = 30.dp
        val decoThumb = 64.dp
        val decoInk = 30.dp
        val decoIcon = 56.dp
        val bigAction = 76.dp
        val widgetPad = 16.dp
        val widgetWeekMinH = 100.dp
        val lock = 14.dp
        val coverW = 132.dp
        val cardEdge = 22.dp
        val cardBandGap = 5.dp
        val cardPad = 32.dp
        val cardMark = 22.dp
        val cardMedal = 120.dp
        val cardGap = 14.dp
        val cardRule = 32.dp
        val noteMargin = 44.dp
        val handLine = 72.dp
        val handModel = 168.dp
        val initialBox = 56.dp
        val initialHome = 44.dp
        val hiddenField = 1.dp
        val widgetRing = 64.dp
        val grain = 14.dp
        val aloudBox = 200.dp
        val aloudBars = 84.dp
        val gridCell = 34.dp
        val narrButton = 96.dp
        val bookW = 44.dp
        val bookH = 60.dp
        val spineBand = 2.dp
        val scroll = 30.dp
        val scrollRoll = 9.dp
        val shelfRow = 68.dp
        val wideMin = 600.dp
        val introEmblem = 44.dp
        val introCorner = 12.dp
        val ribbonW = 12.dp
        val ribbonH = 120.dp
        val introFrame = 16.dp
    }
    object Text {
        val verse = 21.sp
        val verseEn = 22.sp
        val body = 16.sp
        val label = 15.sp
        val tabMax = 17.sp
        val small = 13.sp
        val brand = 20.sp
        val title = 24.sp
        val display = 34.sp
        val initial = 40.sp
        val initialLg = 64.sp
        val typed = 18.sp
        val typedEn = 19.sp
        val cardVerse = 21.sp
        val cardVerseEn = 23.sp
        val cardVerseMin = 13.sp
        val cardRef = 11.5.sp
        val cardFoot = 9.5.sp
        val cardCaps = 9.5.sp
        val cardBig = 72.sp
        val cardName = 28.sp
        val pen = 23.sp
        val penLine = 34.sp
        val initialBox = 30.sp
        val gridInitial = 20.sp
        val guide = 40.sp
        val hint = 20.sp
        val aloudBig = 30.sp
        val gridChar = 20.sp
        val aloudMin = 18.sp
        val aloudFit = 26.sp
        val intro = 38.sp
        val introCover = 34.sp
        val introPlate = 30.sp
        val gateNum = 22.sp
        val introCaps = 13.sp
        val caps = 10.5.sp
        val numBig = 44.sp
        val streak = 30.sp
        val gridNum = 22.sp
    }
    /** 잉크 (꾸미기 1.2): 쓴 글자 · 손글씨 색. Light 는 종이 위, Dark 는 밤빛 위. */
    object Inks {
        val ink = Color(0xFF1F1A15)
        val navy = Color(0xFF1F2A44)
        val sepia = Color(0xFF5A3A1C)
        val gilt = Color(0xFF8A5E18)
    }
    object InksDark {
        val ink = Color(0xFFEADFC8)
        val navy = Color(0xFFB4C3E6)
        val sepia = Color(0xFFD9B48A)
        val gilt = Color(0xFFD6B672)
    }
    object Motion {
        const val sideSlop = 2.5f
        const val followPace = 0.85f
        const val voiceHoldMs = 700
        const val voiceLevel = 0.02f
        const val voiceRms = 3.0f
        const val keepMs = 5000
        const val goldMs = 900
        const val goldHoldMs = 500
        const val coachDelayMs = 700
        const val inkMs = 600
        const val veilMs = 1400
        const val pageMs = 420
        const val toastMs = 2400
        const val fadeMs = 220
        const val typeSettleMs = 350
        const val turnSnap = 0.2f
        const val turnTilt = 2.5f
        const val turnLift = 18.0f
        const val turnShade = 0.35f
        const val turnEdge = 12.0f
        const val breathDrift = 24.0f
        const val aloudNormal = 1.0f
        const val sealMs = 900
        const val sealHoldMs = 1600
        const val openDrawMs = 700
        const val openShineMs = 650
        const val openHoldMs = 900
        const val openAngle = 100.0f
        const val openCamera = 14.0f
        const val bookTilt = 14.0f
        const val turnMs = 520
        const val aloudSlow = 0.85f
        const val aloudFast = 1.3f
        const val reviewMs = 4000
        const val tickMs = 90
        const val unrollMs = 1100
        const val introFrameMs = 1300
        const val introLetterMs = 700
        const val introLetterStep = 90
        const val introGlintMs = 1100
        const val introEnterMs = 900
        const val introDriftMs = 9000
        const val introBreatheMs = 2600
        const val coverOpenMs = 1500
        const val ribbonMs = 800
        const val litStepMs = 55
    }
    object Ratio {
        const val plateAspect = 0.766f
        const val systemFontMax = 1.15f
        const val veilInset = 0.06f
        const val veilMark = 0.16f
        const val plateWidth = 0.82f
        const val stampInCell = 0.62f
        const val photoAspect = 1.3333f
        const val scaleLarge = 1.15f
        const val shineWidth = 0.18f
        const val welcomeArt = 1.25f
        const val initialFinish = 1.25f
        const val heatGap = 0.18f
        const val barMax = 0.8f
        const val spineMin = 0.62f
        const val wideVerse = 1.15f
        const val thumb = 0.89f
        const val openerArt = 0.78f
        const val gateArt = 0.62f
        const val newsArt = 1.25f
        const val purchaseArt = 1.6f
    }
    object Alpha {
        const val aloudAhead = 0.62f
        const val aloudLitBg = 0.1f
        const val faint = 0.5f
        const val goldCell = 0.55f
        const val coachScrim = 0.85f
        const val hintChar = 0.22f
        const val medalFaint = 0.55f
        const val future = 0.6f
        const val rest = 0.75f
        const val handle = 0.7f
        const val frame = 0.85f
        const val nib = 0.14f
        const val shine = 0.22f
        const val peek = 0.93f
        const val veilPiece = 0.86f
        const val handGuide = 0.35f
        const val guide = 0.22f
        const val contrastHair = 0.45f
        const val wrongCell = 0.14f
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
        const val caps = 0.2f
    }
    object Px {
        const val shareW = 1080.0f
        const val shareH = 1350.0f
        const val shareDensity = 2.75f
        const val plateFaint = 0.12f
        const val cardFill = 0.55f
        const val cardPlate = 0.62f
        const val cardArtMin = 0.48f
        const val cardArtMax = 0.74f
        const val cardTextMax = 0.4f
        const val medalTop = 0.24f
        const val scrollCellCm = 1.0f
    }
    object Sound {
        const val narrationGainDb = 7.0f
        const val voiceTargetRms = 0.1f
        const val voiceMaxGain = 8.0f
        const val penLevel = 0.075f
    }
}
