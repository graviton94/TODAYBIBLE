package io.github.graviton94.todaybible.data

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.graviton94.todaybible.MainActivity
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.core.Prayers
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 기도 알림: 기도문마다 켜고 끄고, 시각도 따로 (Store.prayerTimes).
 * 알림 글은 그 기도의 첫 말씀 그대로. 누르면 그 기도문이 펼쳐져요. 오늘 이미 드린 기도는 울리지 않아요.
 */
object PrayerReminder {
    private const val CHANNEL = "prayer"
    private fun intent(ctx: Context, i: Int, id: String) = PendingIntent.getBroadcast(ctx, 40 + i, Intent(ctx, PrayerReceiver::class.java).putExtra("p", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val times = Store(ctx).prayerTimes
        Prayers.all.forEachIndexed { i, p ->
            val pi = intent(ctx, i, p.id); am.cancel(pi)
            val m = times[p.id] ?: return@forEachIndexed
            var at = LocalDate.now().atTime(m / 60, m % 60)
            if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
            // 대략이어도 되는 알림이라 정확한 알람 권한 없이 10분 창으로
            am.setWindow(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), 10 * 60_000L, pi)
        }
    }

    fun post(ctx0: Context, id: String) {
        val ctx = io.github.graviton94.todaybible.ui.Lang.wrap(ctx0)   // 앱에서 고른 언어로
        val store = Store(ctx)
        if (id !in store.prayerTimes) return
        val i = Prayers.all.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
        val p = Prayers.all[i]
        if (p.id in store.prayedOn(LocalDate.now().toEpochDay())) return
        val first = p.passages.first()
        val body = Markup.plain(store.book(store.translation, first.book).verse(first.chapter, first.from))
        val title = ctx.getString(ctx.resources.getIdentifier("prayer_${p.id}", "string", ctx.packageName))
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.prayer_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 50 + i, Intent(ctx, MainActivity::class.java).setAction("prayer_${p.id}").putExtra("prayer", p.id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = android.app.Notification.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_cross).setContentTitle(title).setContentText(body)
            .setStyle(android.app.Notification.BigTextStyle().bigText(body)).setContentIntent(open).setAutoCancel(true).build()
        runCatching { nm.notify(60 + i, n) }
    }
}

class PrayerReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val r = goAsync()
        Thread {
            runCatching { intent.getStringExtra("p")?.let { PrayerReminder.post(ctx, it) } }
            PrayerReminder.schedule(ctx); r.finish()
        }.start()
    }
}
