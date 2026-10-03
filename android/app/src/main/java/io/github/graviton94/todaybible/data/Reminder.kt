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
import io.github.graviton94.todaybible.core.Canon
import io.github.graviton94.todaybible.core.Markup
import io.github.graviton94.todaybible.core.Progress
import io.github.graviton94.todaybible.core.Translation
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 매일 알림: 정한 시각에 ‘다음에 옮겨 쓸 한 절’을 그대로 보여 줌 (지어낸 문장 없이 말씀만).
 * 오늘 이미 썼으면 울리지 않음. 시각이 대략이어도 되는 알림이라 정확한 알람 권한은 쓰지 않음.
 */
object Reminder {
    private const val CHANNEL = "daily_verse"
    private const val ID = 7

    fun schedule(ctx: Context, hour: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(ctx, 0, Intent(ctx, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.cancel(pi)
        if (hour < 0) return
        var at = LocalDate.now().atTime(hour, 0)
        if (!at.isAfter(LocalDateTime.now())) at = at.plusDays(1)
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), AlarmManager.INTERVAL_DAY, pi)
    }

    fun post(ctx: Context) {
        val store = Store(ctx)
        val fills = store.loadFills()
        if (fills.any { it.epochDay == LocalDate.now().toEpochDay() }) return
        val tr = store.translation; val p = Progress(fills)
        // 길잡이가 있으면 길잡이의 다음 절, 없으면 책갈피
        val planNext = io.github.graviton94.todaybible.core.Plans.byId(store.planId)?.chapters?.firstNotNullOfOrNull { (pb, pc) ->
            p.nextVerse(tr, store.book(tr, pb), pc)?.let { Triple(pb, pc, it) } }
        val (b, ch) = planNext?.let { it.first to it.second } ?: store.bookmark(tr)
        val text = store.book(tr, b)
        val v = planNext?.third ?: p.nextVerse(tr, text, ch) ?: text.fillable(ch).first()
        val book = Canon.books[b]
        val title = "${if (tr == Translation.KRV) book.ko else book.en} $ch:$v"
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).putExtra("page", 1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val body = Markup.plain(text.verse(ch, v))
        val n = android.app.Notification.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_cross).setContentTitle(title).setContentText(body)
            .setStyle(android.app.Notification.BigTextStyle().bigText(body)).setContentIntent(open).setAutoCancel(true).build()
        runCatching { nm.notify(ID, n) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = Reminder.post(ctx)
}

/** 다시 켜지거나 앱을 새로 깔면 알림 다시 맞춤. */
class ReminderBoot : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) = Reminder.schedule(ctx, Store(ctx).reminderHour)
}
