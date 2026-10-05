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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.wrapContentHeight
import io.github.graviton94.todaybible.core.Canon
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    fun close() { s.purchaseOpen = false; s.peekBook = null }
    BackHandler { close() }
    val backLabel = stringResource(R.string.back)
    Column(Modifier.fillMaxSize().background(c.leaf)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(Tokens.Size.touch).semantics { contentDescription = backLabel }.clickable(role = Role.Button) { close() }, contentAlignment = Alignment.Center) { BackArrow(Modifier.size(Tokens.Size.icon)) }
            Text(stringResource(R.string.plans_title), style = Theme.title(k), maxLines = 1)
        }
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s4),
        ) {
            val peek = s.peekBook
            if (peek != null) Peek(s, peek) else {
                Cover(Modifier.width(Tokens.Size.coverW))
                Text(stringResource(R.string.lifetime_head), style = Theme.title(k).copy(textAlign = TextAlign.Center))
            }
            when {
                life.owned -> Text(stringResource(R.string.owned), style = Theme.label().copy(color = c.giltText))
                life.ready || life.forceReady -> {
                    // 두 길을 나란히: 월 구독 · 평생권 (구독 중이면 평생권이 할인가로)
                    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                        if (life.canSubscribe || life.monthlyPrice != null || life.subscribed)
                            PlanCard(Modifier.weight(1f), stringResource(R.string.plan_month),
                                if (life.subscribed) stringResource(R.string.plan_month_on) else stringResource(R.string.plan_month_price, life.monthlyPrice ?: ""),
                                null, stringResource(R.string.plan_month_note), primary = !life.subscribed, enabled = !life.subscribed) { (ctx as? Activity)?.let { life.subscribe(it) } }
                        val member = life.subscribed && life.memberPrice != null
                        PlanCard(Modifier.weight(1f), stringResource(R.string.plan_life), life.lifetimePrice ?: "",
                            if (member) life.price else null,
                            when { member -> stringResource(R.string.plan_life_discount); life.memberPrice != null -> stringResource(R.string.plan_life_member, life.memberPrice!!); else -> stringResource(R.string.plan_life_note) },
                            primary = life.subscribed || !(life.canSubscribe || life.monthlyPrice != null)) { (ctx as? Activity)?.let { life.buy(it) } }
                    }
                    if (life.subscribed) {
                        Text(stringResource(R.string.member_note), style = Theme.small().copy(textAlign = TextAlign.Center))
                        Text(stringResource(R.string.manage_sub), style = Theme.small().copy(color = c.rubric), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                            .heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = Role.Button) {
                                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://play.google.com/store/account/subscriptions?sku=${io.github.graviton94.todaybible.data.Lifetime.MONTHLY}&package=${ctx.packageName}"))) }
                            })
                    }
                }
                else -> BookButton(stringResource(R.string.not_ready), Modifier.fillMaxWidth(), enabled = false) {}
            }
            Compare(s)
            if (!life.owned) BookButton(stringResource(R.string.restore), Modifier.fillMaxWidth(), quiet = true) { life.restore() }
            // 교회 · 소그룹에서 받은 선물 코드 (Play 프로모션 코드): Play 의 코드 쓰기 화면으로
            if (!life.owned) Text(stringResource(R.string.gift_code), style = Theme.small().copy(color = Theme.c.rubric), modifier = Modifier.fillMaxWidth()
                .heightIn(min = Tokens.Size.tab).wrapContentHeight().clickable(role = androidx.compose.ui.semantics.Role.Button) {
                    runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(io.github.graviton94.todaybible.data.Links.REDEEM))) }
                }, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(stringResource(R.string.free_books), style = Theme.small().copy(textAlign = TextAlign.Center))
            Text(stringResource(R.string.pay_note), style = Theme.small().copy(textAlign = TextAlign.Center))
        }
    }
}

/** 고를 수 있는 길 하나 (월 구독 · 평생권): 이름 · 가격 (할인 전 가격은 줄 그어) · 한 줄 · 고르기. primary = 가죽 바탕으로 먼저 눈에. */
@Composable
private fun PlanCard(modifier: Modifier, name: String, price: String, was: String?, note: String, primary: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Theme.c
    val bg = if (primary) c.leather else c.paper
    val fg = if (primary) c.leatherInk else c.ink
    Column(modifier.fillMaxHeight().clip(RoundedCornerShape(Tokens.Radius.card)).background(bg)
        .drawBehind { giltFrame(if (primary) c.gilt.copy(alpha = Tokens.Alpha.frame) else c.hair, bands = primary) }
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(Tokens.Space.s4),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Tokens.Space.s1)) {
        Text(name, style = Theme.small().copy(color = if (primary) c.gilt else c.rubric, textAlign = TextAlign.Center), maxLines = 1)
        was?.let { Text(it, style = Theme.small().copy(color = fg.copy(alpha = Tokens.Alpha.faint), textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough, textAlign = TextAlign.Center), maxLines = 1) }
        Text(price, style = Theme.title(true).copy(color = fg, textAlign = TextAlign.Center), maxLines = 2)
        Text(note, style = Theme.small().copy(color = fg, textAlign = TextAlign.Center), maxLines = 3)
    }
}

/** 잠긴 권 미리 보기 (E3): 첫 두 절 위로 종이가 덮이고 그 위에 한 줄. */
@Composable
private fun Peek(s: AppState, b: Int) {
    val c = Theme.c; val k = s.korean
    val t = s.store.book(s.translation, b)
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.card)).background(c.paper)) {
        Column(Modifier.padding(Tokens.Space.s4), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
            RunningHead(s.chapterRef(b, 1), "", k)
            t.fillable(1).take(3).forEach { v -> VerseText(s, v, t.verse(1, v)) }
        }
        Column(Modifier.matchParentSize().padding(top = Tokens.Size.coverW).background(c.leaf.copy(alpha = Tokens.Alpha.peek)),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            LockMark(c.inkSoft, Modifier.size(Tokens.Size.iconMd))
            Text(stringResource(R.string.peek_head, s.bookName(b)), style = Theme.title(k).copy(textAlign = TextAlign.Center), modifier = Modifier.padding(Tokens.Space.s3))
        }
    }
}

/** 무료와 평생권 비교 (E2). */
@Composable
private fun Compare(s: AppState) {
    val c = Theme.c
    val rows = listOf(
        Triple(stringResource(R.string.cmp_books), stringResource(R.string.books_n, Canon.free.size), stringResource(R.string.books_n, Canon.books.size)),
        Triple(stringResource(R.string.cmp_plates), stringResource(R.string.cmp_some), stringResource(R.string.cmp_all)),
        Triple(stringResource(R.string.my_bible_pdf), "–", "✓"),
        Triple(stringResource(R.string.cmp_voice), "–", "✓"),
        Triple(stringResource(R.string.cmp_notes), "–", "✓"),
        Triple(stringResource(R.string.cmp_pens), stringResource(R.string.pen_fountain), stringResource(R.string.cmp_pens_all)),
        Triple(stringResource(R.string.cmp_ads), stringResource(R.string.cmp_none), stringResource(R.string.cmp_none)),
    )
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = Tokens.Space.s2)) {
            Box(Modifier.weight(1.4f)); Text(stringResource(R.string.cmp_free), style = Theme.small().copy(textAlign = TextAlign.Center), modifier = Modifier.weight(1f))
            Text(stringResource(R.string.cmp_paid), style = Theme.small().copy(color = c.rubric, textAlign = TextAlign.Center), modifier = Modifier.weight(1f))
        }
        rows.forEach { (name, free, life) ->
            Row(Modifier.fillMaxWidth().heightIn(min = Tokens.Size.row).drawBehind { drawLine(c.hair, Offset(0f, 0f), Offset(size.width, 0f), Tokens.Stroke.hair.toPx()) },
                verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = Theme.body(), maxLines = 1, modifier = Modifier.weight(1.4f))
                Text(free, style = Theme.body().copy(color = c.inkSoft, textAlign = TextAlign.Center), maxLines = 1, modifier = Modifier.weight(1f))
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (life == "✓") StampMark(STAMP_CROSS, c.rubric, Modifier.size(Tokens.Size.iconSm))
                    else Text(life, style = Theme.label().copy(color = c.rubric, textAlign = TextAlign.Center), maxLines = 1)
                }
            }
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
