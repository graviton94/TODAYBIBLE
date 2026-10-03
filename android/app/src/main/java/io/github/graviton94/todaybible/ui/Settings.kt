package io.github.graviton94.todaybible.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.graviton94.todaybible.BuildConfig
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Translation
import io.github.graviton94.todaybible.design.Theme
import io.github.graviton94.todaybible.design.ThemeChoice
import io.github.graviton94.todaybible.design.Tokens

private val SCALES = listOf(1f, Tokens.Ratio.scaleLarge, Tokens.Ratio.scaleLarger)

/** 설정: 한 장짜리. 고르는 것은 모두 밑줄 탭 · 한 줄 목록. */
@Composable
fun SettingsPage(s: AppState) {
    val c = Theme.c; val k = s.korean
    BackHandler { s.settingsOpen = false }
    Column(Modifier.fillMaxSize().background(c.leaf)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Tokens.Space.s2), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(Tokens.Size.touch).clickable(role = Role.Button) { s.settingsOpen = false }, contentAlignment = Alignment.Center) {
                BackArrow(Modifier.size(Tokens.Size.icon))
            }
            Text(stringResource(R.string.settings), style = Theme.title(k), maxLines = 1)
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Tokens.Space.s5, vertical = Tokens.Space.s3), verticalArrangement = Arrangement.spacedBy(Tokens.Space.s5)) {
            Group(stringResource(R.string.theme)) {
                UnderlineTabs(listOf(stringResource(R.string.theme_system), stringResource(R.string.theme_light), stringResource(R.string.theme_dark)), s.theme.ordinal) {
                    s.setThemeChoice(ThemeChoice.entries[it])
                }
            }
            Group(stringResource(R.string.translation)) {
                Column {
                    ChoiceRow(stringResource(R.string.tr_krv), s.translation == Translation.KRV) { s.chooseTranslation(Translation.KRV) }
                    ChoiceRow(stringResource(R.string.tr_kjv), s.translation == Translation.KJV) { s.chooseTranslation(Translation.KJV) }
                }
            }
            Group(stringResource(R.string.text_size)) {
                UnderlineTabs(listOf(stringResource(R.string.size_regular), stringResource(R.string.size_large), stringResource(R.string.size_larger)),
                    SCALES.indexOfFirst { kotlin.math.abs(it - s.scale) < 0.01f }.coerceAtLeast(0)) { s.setTextScale(SCALES[it]) }
                // 지금 고른 번역 · 크기로 창세기 1:1
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(Tokens.Radius.chip)).background(c.paper).padding(Tokens.Space.s4)) {
                    VerseText(s, 1, s.text(0).verse(1, 1))
                }
            }
            Group(stringResource(R.string.reminder)) {
                // 안드로이드 13+: 처음 켤 때 알림 허락을 물음
                var pending by remember { mutableIntStateOf(-1) }
                val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) s.setReminder(pending) }
                val hours = listOf(-1, 7, 21)
                UnderlineTabs(listOf(stringResource(R.string.reminder_off), stringResource(R.string.reminder_morning), stringResource(R.string.reminder_night)),
                    hours.indexOf(s.reminderHour).coerceAtLeast(0)) { i ->
                    val h = hours[i]
                    if (h >= 0 && android.os.Build.VERSION.SDK_INT >= 33) { pending = h; ask.launch(android.Manifest.permission.POST_NOTIFICATIONS) } else s.setReminder(h)
                }
            }
            Group(stringResource(R.string.stamp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Tokens.Space.s3)) {
                    STAMPS.forEach { m ->
                        val on = m == s.stamp
                        Box(
                            Modifier.weight(1f).heightIn(min = Tokens.Size.touch).clip(RoundedCornerShape(Tokens.Radius.chip)).background(if (on) c.paper else c.leaf)
                                .drawBehind { if (on) drawLine(c.rubric, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.rule.toPx() * 2) }
                                .clickable(role = Role.RadioButton) { s.setStampMark(m) },
                            contentAlignment = Alignment.Center,
                        ) { StampMark(m, if (on) c.rubric else c.inkSoft, Modifier.size(Tokens.Size.iconMd)) }
                    }
                }
            }
            Group(stringResource(R.string.about)) {
                Text(stringResource(R.string.about_text), style = Theme.small())
                Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = Theme.small())
            }
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Tokens.Space.s2)) {
        Text(title, style = Theme.small().copy(color = Theme.c.inkSoft), maxLines = 1)
        content()
    }
}

/** 한 줄 고르기: 고른 줄 끝에 붉은 점. */
@Composable
private fun ChoiceRow(text: String, on: Boolean, onClick: () -> Unit) {
    val c = Theme.c
    Row(
        Modifier.fillMaxWidth().heightIn(min = Tokens.Size.row).clickable(role = Role.RadioButton, onClick = onClick)
            .drawBehind { drawLine(c.hair, Offset(0f, size.height), Offset(size.width, size.height), Tokens.Stroke.hair.toPx()) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = Theme.body().copy(color = if (on) c.ink else c.inkSoft), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (on) Box(Modifier.size(Tokens.Size.dot).clip(androidx.compose.foundation.shape.CircleShape).background(c.rubric))
    }
}

/** 뒤로 (얇은 화살 한 획). */
@Composable
fun BackArrow(modifier: Modifier = Modifier) {
    val c = Theme.c
    Box(modifier.drawBehind {
        val w = Tokens.Stroke.rule.toPx(); val h = size.height; val x = size.width
        drawLine(c.ink, Offset(x * 0.9f, h / 2), Offset(x * 0.1f, h / 2), w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(c.ink, Offset(x * 0.1f, h / 2), Offset(x * 0.45f, h * 0.15f), w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(c.ink, Offset(x * 0.1f, h / 2), Offset(x * 0.45f, h * 0.85f), w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    })
}

