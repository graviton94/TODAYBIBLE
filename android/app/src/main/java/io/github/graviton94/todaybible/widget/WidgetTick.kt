package io.github.graviton94.todaybible.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate
import java.time.ZoneId

/** 자정 바로 뒤에 모든 위젯을 새로 그려요 (오늘 쓴 절 · 도장 · 날마다 바뀌는 손글씨). 그리고 다음 자정을 다시 맞춰요. */
class WidgetTick : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val r = goAsync()
        Thread { refreshAll(ctx); schedule(ctx); r.finish() }.start()
    }

    companion object {
        fun refreshAll(ctx: Context) {
            runCatching { VerseWidget.refresh(ctx) }
            runCatching { SmallWidget.refreshAll(ctx) }
            runCatching { HandWidget.refresh(ctx) }
        }

        fun schedule(ctx: Context) {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(ctx, 9, Intent(ctx, WidgetTick::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val at = LocalDate.now().plusDays(1).atTime(0, 1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            am.set(AlarmManager.RTC, at, pi)
        }
    }
}
