package io.github.graviton94.todaybible.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.Tokens

/**
 * 평생권: 가죽 표지 하나 · 받는 것 세 줄 · 가격 버튼 · 구매 복원. 설명은 짧게.
 * Play 에 닿지 않으면 버튼 대신 ‘지금은 결제할 수 없어요’.
 */
@Composable
fun PurchasePage(s: AppState) {
    val c = Theme.c; val k = s.korean; val ctx = LocalContext.current
    val life = s.lifetime
    LaunchedEffect(Unit) { life.connect() }
    BackHandler { s.purchaseOpen = false }
    Column(Modifier.fillMaxSize().background(c.leaf)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(Tokens.Size.touch).clickable(role = Role.Button) { s.purchaseOpen = false }, contentAlignment = Alignment.Center) { BackArrow(Modifier.size(Tokens.Size.icon)) }
            Text(stringResource(R.string.lifetime), style = Theme.title(k), maxLines = 1)
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s4),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            Cover(Modifier.width(Tokens.Size.coverW))
            Text(stringResource(R.string.lifetime_head), style = Theme.title(k).copy(textAlign = TextAlign.Center))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
                listOf(R.string.benefit_books, R.string.benefit_plates, R.string.benefit_once).forEach { id ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                        StampMark(STAMP_CROSS, c.gilt, Modifier.size(Tokens.Size.iconSm))
                        Text(stringResource(id), style = Theme.body(), maxLines = 1)
                    }
                }
            }
            when {
                life.owned -> Text(stringResource(R.string.owned), style = Theme.label().copy(color = c.giltText))
                life.ready || life.forceReady -> BookButton(stringResource(R.string.buy_lifetime, life.price ?: ""), Modifier.fillMaxWidth()) { (ctx as? Activity)?.let { life.buy(it) } }
                else -> BookButton(stringResource(R.string.not_ready), Modifier.fillMaxWidth(), enabled = false) {}
            }
            if (!life.owned) BookButton(stringResource(R.string.restore), Modifier.fillMaxWidth(), quiet = true) { life.restore() }
            Text(stringResource(R.string.free_books), style = Theme.small().copy(textAlign = TextAlign.Center))
            Text(stringResource(R.string.pay_note), style = Theme.small().copy(textAlign = TextAlign.Center))
        }
    }
}

/** 작은 가죽 표지: 덮개와 같은 문법 (위아래 금선 두 줄 · 안쪽 금박 테 · 가운데 ✠). */
@Composable
private fun Cover(modifier: Modifier) {
    val c = Theme.c
    Box(modifier.aspectRatio(Tokens.Ratio.plateAspect).clip(RoundedCornerShape(Tokens.Radius.chip)).background(c.leather).drawBehind {
        val w = size.width; val h = size.height; val g = Tokens.Stroke.giltFine.toPx()
        val edge = Tokens.Size.frameInset.toPx() * 2; val gap = Tokens.Size.bandGap.toPx()
        for (y in listOf(edge, edge + gap, h - edge, h - edge - gap)) drawLine(c.gilt, Offset(edge, y), Offset(w - edge, y), g)
        val inset = w * Tokens.Ratio.veilInset + edge
        drawRoundRect(c.gilt, Offset(inset, inset + gap), Size(w - inset * 2, h - (inset + gap) * 2), CornerRadius(Tokens.Radius.frame.toPx()), style = Stroke(g))
        stamp(STAMP_CROSS, c.gilt, Offset(w / 2, h / 2), w * Tokens.Ratio.veilMark)
    })
}
