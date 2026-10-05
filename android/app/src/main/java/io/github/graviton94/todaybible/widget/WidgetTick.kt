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
        Thread { try { refreshAll(ctx); schedule(ctx) } finally { r.finish() } }.start()
    }

    companion object {
        fun refreshAll(ctx: Context) {
            runCatching { VerseWidget.refresh(ctx) }
            runCatching { SmallWidget.refreshAll(ctx) }
            runCatching { HandWidget.refresh(ctx) }
            runCatching { previews(ctx) }
        }

        /** 위젯 고르는 화면의 미리보기 (안드로이드 15+): 실제 그림을 그대로, 하루 한 번 (Play 가 횟수를 제한해요). */
        fun previews(ctx0: Context, force: Boolean = false) {
            if (android.os.Build.VERSION.SDK_INT < 35) return
            val ctx = io.github.graviton94.todaybible.ui.Lang.wrap(ctx0)
            val p = ctx.getSharedPreferences("today", Context.MODE_PRIVATE)
            val day = LocalDate.now().toEpochDay()
            if (!force && p.getLong("widget_preview_day", -1) == day) return
            val m = android.appwidget.AppWidgetManager.getInstance(ctx)
            val dark = VerseWidget.dark(ctx)
            fun put(cls: Class<*>, bmp: android.graphics.Bitmap) {
                val v = android.widget.RemoteViews(ctx.packageName, io.github.graviton94.todaybible.R.layout.widget_verse)
                v.setImageViewBitmap(io.github.graviton94.todaybible.R.id.widget_image, bmp)
                m.setWidgetPreview(android.content.ComponentName(ctx, cls), android.appwidget.AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, v)
            }
            runCatching { put(VerseWidget::class.java, VerseWidget.bitmap(ctx, 250, 110, dark)) }
            runCatching { put(HandWidget::class.java, HandWidget.bitmap(ctx, 250, 110, dark)) }
            runCatching { put(GoalWidget::class.java, GoalWidget().draw(ctx, 110, 110, dark)) }
            p.edit().putLong("widget_preview_day", day).apply()
        }

        /** 미리보기 그림 파일 (캡처용 · 디버그): 세 위젯을 밝은 바탕으로 그려 files/previews 에. */
        fun exportPreviews(ctx0: Context, dir: java.io.File) {
            val ctx = io.github.graviton94.todaybible.ui.Lang.wrap(ctx0)
            dir.mkdirs()
            fun save(name: String, bmp: android.graphics.Bitmap) = java.io.File(dir, "$name.png").outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            runCatching { save("widget_preview_verse", VerseWidget.bitmap(ctx, 250, 110, false)) }
            runCatching { save("widget_preview_hand", HandWidget.bitmap(ctx, 250, 110, false)) }
            runCatching { save("widget_preview_today", GoalWidget().draw(ctx, 110, 110, false)) }
            java.io.File(dir, "done").writeText("ok")
        }

        fun schedule(ctx: Context) {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(ctx, 9, Intent(ctx, WidgetTick::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val at = LocalDate.now().plusDays(1).atTime(0, 1).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            am.set(AlarmManager.RTC, at, pi)
        }
    }
}
