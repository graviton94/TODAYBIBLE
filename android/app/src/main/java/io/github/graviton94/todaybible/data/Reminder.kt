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
    const val ID = 7

    private const val WINDOW_MS = 15 * 60_000L

    /**
     * 다음 알림 한 번을 맞춰요 (정각, 잠든 폰에서도 몇 분 안에). 알림이 울리면 받는 쪽에서 다음 날 것을 다시 맞춰요.
     * 앱을 정각 조금 뒤에 열었는데 오늘 알림이 아직이면 곧바로 한 번.
     */
    fun schedule(ctx: Context, hour: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(ctx, 0, Intent(ctx, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val backup = PendingIntent.getBroadcast(ctx, 8, Intent(ctx, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.cancel(pi); am.cancel(backup)
        if (hour < 0) return
        val now = LocalDateTime.now(); val today = LocalDate.now()
        var at = today.atTime(hour, 0)
        if (!at.isAfter(now)) {
            val missedToday = Store(ctx).reminderDay != today.toEpochDay() && now.isBefore(at.plusHours(2))
            at = if (missedToday) now.plusSeconds(30) else at.plusDays(1)
        }
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // 정확한 알람을 쓸 수 있으면 정각에. 아니면 15분 창으로 맞추고, 깊이 잠든 폰을 위해 잠결에도 울리는 것을 하나 더 (먼저 울린 쪽만 띄움)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        else { am.setWindow(AlarmManager.RTC_WAKEUP, ms, WINDOW_MS, pi); am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms + WINDOW_MS, backup) }
    }

    fun post(ctx: Context) {
        val store = Store(ctx)
        if (store.reminderHour < 0) return
        // 하루 한 번만 (늦게 울렸어도 정한 시각에서 두 시간이 지났으면 건너뜀)
        val today = LocalDate.now().toEpochDay()
        if (store.reminderDay == today) return
        store.reminderDay = today
        if (LocalDateTime.now().isAfter(LocalDate.now().atTime(store.reminderHour, 0).plusHours(2))) return
        val fills = store.loadFills()
        if (fills.any { it.epochDay == today }) return
        store.postedDay = today
        val tr = store.translation; val p = Progress(fills)
        // 길잡이가 있으면 길잡이의 다음 절, 없으면 책갈피
        val planNext = io.github.graviton94.todaybible.core.Plans.byId(store.planId)?.chapters?.firstNotNullOfOrNull { (pb, pc) ->
            p.nextVerse(tr, store.book(tr, pb), pc)?.let { Triple(pb, pc, it) } }
        val (b, ch) = planNext?.let { it.first to it.second } ?: store.bookmark(tr)
        val text = store.book(tr, b)
        val v = planNext?.third ?: p.nextVerse(tr, text, ch) ?: text.fillable(ch).first()
        val book = Canon.books[b]
        val ref = io.github.graviton94.todaybible.ui.Lang.content(ctx, tr).getString(R.string.ref_verse, if (tr == Translation.KRV) book.ko else book.en, ch, v)
        val title = if (store.ownerName.isNotBlank()) ctx.getString(R.string.reminder_named, store.ownerName, ref) else ref
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT))
        // 누르면 그 절이 펼쳐진 필사 자리로 곧바로
        val open = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java).setAction("at").putExtra("page", io.github.graviton94.todaybible.ui.AppState.COPY)
            .putExtra("at", true).putExtra("at_b", b).putExtra("at_c", ch).putExtra("at_v", v).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val body = Markup.plain(text.verse(ch, v))
        val n = android.app.Notification.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_cross).setContentTitle(title).setContentText(body)
            .setStyle(android.app.Notification.BigTextStyle().bigText(body)).setContentIntent(open).setAutoCancel(true)
            // 함께 읽기 (Y1): 누르면 앱이 그 절로 열리고 가이드 목소리가 곧바로 읽기 시작
            .addAction(android.app.Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_stat_cross), ctx.getString(R.string.reminder_aloud),
                PendingIntent.getActivity(ctx, 7, Intent(ctx, MainActivity::class.java).setAction("aloud").putExtra("page", io.github.graviton94.todaybible.ui.AppState.COPY).putExtra("aloud", true)
                    .putExtra("at_b", b).putExtra("at_c", ch).putExtra("at_v", v).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build())
            .build()
        runCatching { nm.notify(ID, n) }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val r = goAsync()
        Thread { runCatching { Reminder.post(ctx) }; Reminder.schedule(ctx, Store(ctx).reminderHour); r.finish() }.start()
    }
}

/** 다시 켜지거나, 앱을 새로 깔거나, 시간대 · 시계가 바뀌면 알림 다시 맞춤 (자정 위젯 새로 그리기도). */
class ReminderBoot : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Reminder.schedule(ctx, Store(ctx).reminderHour)
        PrayerReminder.schedule(ctx)
        io.github.graviton94.todaybible.widget.WidgetTick.schedule(ctx)
    }
}
