package io.github.graviton94.todaybible.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens
import io.github.graviton94.todaybible.design.inkColor

/**
 * 꾸미기 (1.2): 표지 판화 · 표지 글 (헌사) · 잉크 · 머리글 · 앱 아이콘.
 * 고른 것은 나의 성경 표지 · 여는 화면 · 나누기 카드 · 위젯 · 홈 화면 아이콘에 바로 보여요.
 */
@Composable
fun CoverPlatePicker(s: AppState) {
    val c = Theme.c
    val hung = remember(s.fills.size) { s.hungPlates() }
    if (hung.isEmpty()) { Text(stringResource(R.string.deco_cover_none), style = Theme.small()); return }
    val current = s.coverPlateId()
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        hung.forEach { p ->
            val on = p.id == current
            val img = rememberPlate(p.id, small = true)
            val name = s.plateName(p)
            Box(Modifier.size(Tokens.Size.decoThumb).semantics { contentDescription = name }
                .border(if (on) Tokens.Stroke.rule * 2 else Tokens.Stroke.hair, if (on) c.gilt else c.hair)
                .padding(if (on) Tokens.Stroke.rule * 2 else Tokens.Stroke.hair)
                .background(c.paper).clickable(role = Role.RadioButton) { s.chooseCoverPlate(p.id) }) {
                if (img != null) Image(img, null, Modifier.fillMaxWidth(), contentScale = ContentScale.Crop)
            }
        }
    }
    Text(stringResource(R.string.deco_cover_note), style = Theme.small())
}

@Composable
fun DedicationField(s: AppState) {
    val c = Theme.c
    var t by remember { mutableStateOf(s.dedication) }
    androidx.compose.foundation.text.BasicTextField(
        value = t, onValueChange = { v -> if (v.length <= 30) { t = v; s.setDedicationLine(v.trim()) } },
        singleLine = true, textStyle = Theme.body().copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), cursorBrush = androidx.compose.ui.graphics.SolidColor(c.gilt),
        modifier = Modifier.fillMaxWidth().heightIn(min = Tokens.Size.row).drawBehind { drawLine(c.inkSoft, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.hair.toPx()) },
        decorationBox = { inner -> Box(contentAlignment = Alignment.CenterStart) { if (t.isEmpty()) Text(stringResource(R.string.deco_dedication_hint), style = Theme.body().copy(color = c.unwritten)); inner() } })
}

@Composable
fun InkPicker(s: AppState) {
    val c = Theme.c
    val dark = Theme.c.leaf.luminance() < 0.5f
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s4)) {
        listOf("ink" to R.string.ink_ink, "navy" to R.string.ink_navy, "sepia" to R.string.ink_sepia, "gilt" to R.string.ink_gilt).forEach { (id, name) ->
            val on = s.ink == id
            Column(Modifier.clickable(role = Role.RadioButton) { s.chooseInk(id) }.padding(vertical = Tokens.Space.s1), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Box(Modifier.size(Tokens.Size.decoInk).then(if (on) Modifier.border(Tokens.Stroke.hair, c.gilt, CircleShape).padding(Tokens.Space.s1) else Modifier.padding(Tokens.Space.s1)).clip(CircleShape).background(inkColor(id, dark)))
                Text(stringResource(name), style = Theme.small().copy(color = if (on) c.ink else c.inkSoft), maxLines = 1)
            }
        }
    }
}

@Composable
fun HeadPicker(s: AppState) {
    UnderlineTabs(listOf(stringResource(R.string.head_latin), stringResource(R.string.head_native)), if (s.latinHeads) 0 else 1) { s.chooseLatinHeads(it == 0) }
    Text(if (s.latinHeads) io.github.graviton94.todaybible.core.Latin.head(40, 3) else "${s.bookName(40)} · 3", style = if (s.latinHeads) Theme.caps() else Theme.small().copy(color = Theme.c.giltText), maxLines = 1)
}

@Composable
fun IconPicker(s: AppState) {
    val c = Theme.c
    // 적응형 아이콘 XML 은 그림으로 못 읽어서, 바탕 판화 · 십자 선 두 장을 겹쳐 가운데 (108 중 72) 만 보여요
    val icons = listOf("light" to (R.mipmap.ic_launcher_art to R.string.icon_light), "tablets" to (R.mipmap.ic_launcher_art_tablets to R.string.icon_tablets),
        "golgotha" to (R.mipmap.ic_launcher_art_golgotha to R.string.icon_golgotha), "ascension" to (R.mipmap.ic_launcher_art_ascension to R.string.icon_ascension))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        icons.forEach { (id, v) ->
            val (res, name) = v; val on = s.appIcon == id; val locked = id != "light" && s.gated()
            Column(Modifier.width(Tokens.Size.decoIcon + Tokens.Space.s3).clickable(role = Role.RadioButton) { if (locked) s.purchaseOpen = true else s.chooseAppIcon(id) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
                Box(Modifier.size(Tokens.Size.decoIcon).then(if (on) Modifier.border(Tokens.Stroke.rule * 2, c.gilt, CircleShape) else Modifier).padding(Tokens.Stroke.rule * 2).clip(CircleShape), contentAlignment = Alignment.Center) {
                    val full = Modifier.requiredSize(Tokens.Size.decoIcon * 1.5f)
                    Image(painterResource(res), null, full)
                    Image(painterResource(R.mipmap.ic_launcher_line), null, full)
                }
                Text(stringResource(name), style = Theme.small().copy(color = if (on) c.ink else c.inkSoft), maxLines = 1)
                if (locked) Text(stringResource(R.string.premium_tag), style = Theme.small().copy(color = c.giltText), maxLines = 1)
            }
        }
    }
    Text(stringResource(R.string.deco_icon_note), style = Theme.small())
}

private fun androidx.compose.ui.graphics.Color.luminance(): Float = 0.299f * red + 0.587f * green + 0.114f * blue
