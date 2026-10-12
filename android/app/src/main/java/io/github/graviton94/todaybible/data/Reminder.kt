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
     * 다음 알림 한 번을 맞춰요 (정한 시각, 잠든 폰에서도 몇 분 안에). 알림이 울리면 받는 쪽에서 다음 날 것을 다시 맞춰요.
     * 앱을 정각 조금 뒤에 열었는데 오늘 알림이 아직이면 곧바로 한 번.
     */
    fun schedule(ctx: Context, hour: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(ctx, 0, Intent(ctx, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val backup = PendingIntent.getBroadcast(ctx, 8, Intent(ctx, ReminderReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.cancel(pi); am.cancel(backup)
        if (hour < 0) return
        val now = LocalDateTime.now(); val today = LocalDate.now()
        var at = today.atTime(hour, Store(ctx).reminderMinute)
        if (!at.isAfter(now)) {
            val missedToday = Store(ctx).reminderDay != today.toEpochDay() && now.isBefore(at.plusHours(2))
            at = if (missedToday) now.plusSeconds(30) else at.plusDays(1)
        }
        val ms = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // 정확한 알람을 쓸 수 있으면 정각에. 아니면 15분 창으로 맞추고, 깊이 잠든 폰을 위해 잠결에도 울리는 것을 하나 더 (먼저 울린 쪽만 띄움)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        else { am.setWindow(AlarmManager.RTC_WAKEUP, ms, WINDOW_MS, pi); am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms + WINDOW_MS, backup) }
    }

    const val SEASON_ID = 41
    private val NOTICE = setOf(io.github.graviton94.todaybible.core.Season.ADVENT, io.github.graviton94.todaybible.core.Season.LENT, io.github.graviton94.todaybible.core.Season.EASTER, io.github.graviton94.todaybible.core.Season.PENTECOST)

    /** 절기 시작 알림: 대림 · 사순 · 부활 · 성령강림 첫날에만. 절기 판화 · 절기 말씀 · ‘계획 시작’ · ‘오늘 말씀’. 설정 › 알림에서 끌 수 있어요. */
    fun seasonNotice(ctx0: Context, store: Store, day: LocalDate, force: Boolean = false) {
        val S = io.github.graviton94.todaybible.core.Seasons
        val now = S.at(day).season
        if (!force && (!store.seasonNotice || now !in NOTICE || S.at(day.minusDays(1)).season == now)) return
        val ctx = io.github.graviton94.todaybible.ui.Lang.wrap(ctx0)
        val tr = store.translation
        val (b, c, v) = S.verse(now)
        val book = Canon.books[b]
        val cc = io.github.graviton94.todaybible.ui.Lang.content(ctx, tr)
        val ref = cc.getString(R.string.ref_verse, if (tr == Translation.KRV) book.ko else book.en, c, v)
        val name = ctx.getString(when (now) {
            io.github.graviton94.todaybible.core.Season.ADVENT -> R.string.season_ADVENT; io.github.graviton94.todaybible.core.Season.LENT -> R.string.season_LENT
            io.github.graviton94.todaybible.core.Season.EASTER -> R.string.season_EASTER; io.github.graviton94.todaybible.core.Season.PENTECOST -> R.string.season_PENTECOST
            io.github.graviton94.todaybible.core.Season.CHRISTMAS -> R.string.season_CHRISTMAS; io.github.graviton94.todaybible.core.Season.EPIPHANY -> R.string.season_EPIPHANY
            io.github.graviton94.todaybible.core.Season.ORDINARY -> R.string.season_ORDINARY
        })
        val body = Markup.plain(store.book(tr, b).verse(c, v)) + " (" + ref + ")"
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT))
        fun act(code: Int, extra: String) = PendingIntent.getActivity(ctx, code, Intent(ctx, MainActivity::class.java).setAction(extra).putExtra(extra, true)
            .putExtra("at_b", b).putExtra("at_c", c).putExtra("at_v", v).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val icon = android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_stat_cross)
        val n = android.app.Notification.Builder(ctx, CHANNEL).setSmallIcon(R.drawable.ic_stat_cross)
            .setContentTitle(ctx.getString(R.string.season_begins, name)).setContentText(body)
            .setStyle(plateBitmap(ctx, S.plate(now))?.let { android.app.Notification.BigPictureStyle().bigPicture(it).setSummaryText(body) } ?: android.app.Notification.BigTextStyle().bigText(body))
            .setContentIntent(act(42, "season_read")).setAutoCancel(true)
            .addAction(android.app.Notification.Action.Builder(icon, ctx.getString(R.string.season_plan_start), act(43, "season_plan")).build())
            .addAction(android.app.Notification.Action.Builder(icon, ctx.getString(R.string.season_word), act(44, "season_read")).build())
            .build()
        runCatching { nm.notify(SEASON_ID, n) }
    }

    fun post(ctx0: Context) {
        val ctx = io.github.graviton94.todaybible.ui.Lang.wrap(ctx0)   // 앱에서 고른 언어로
        val store = Store(ctx)
        if (store.reminderHour < 0) return
        // 하루 한 번만 (늦게 울렸어도 정한 시각에서 두 시간이 지났으면 건너뜀)
        val today = LocalDate.now().toEpochDay()
        if (store.reminderDay == today) return
        store.reminderDay = today
        if (LocalDateTime.now().isAfter(LocalDate.now().atTime(store.reminderHour, store.reminderMinute).plusHours(2))) return
        // 절기가 시작되는 날 (일 년에 네 번): 오늘 썼어도 따로 한 번
        seasonNotice(ctx, store, LocalDate.now())
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
            .setStyle(plateStyle(ctx, store, b, ch, body) ?: android.app.Notification.BigTextStyle().bigText(body)).setContentIntent(open).setAutoCancel(true)
            // 함께 읽기 (Y1): 누르면 앱이 그 절로 열리고 가이드 목소리가 곧바로 읽기 시작
            .addAction(android.app.Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_stat_cross), ctx.getString(R.string.reminder_aloud),
                PendingIntent.getActivity(ctx, 7, Intent(ctx, MainActivity::class.java).setAction("aloud").putExtra("page", io.github.graviton94.todaybible.ui.AppState.COPY).putExtra("aloud", true)
                    .putExtra("at_b", b).putExtra("at_c", ch).putExtra("at_v", v).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build())
            .build()
        runCatching { nm.notify(ID, n) }
    }
}

/** 판화 한 점 (알림용, 반으로 줄여 읽음). */
private fun plateBitmap(ctx: Context, id: String) = runCatching { ctx.assets.open("plates/$id.jpg").use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }) } }.getOrNull()

/** 펼치면 그 장(없으면 오늘)의 판화가 크게. 그림을 못 읽으면 글만. */
private fun plateStyle(ctx: Context, store: Store, b: Int, ch: Int, body: String): android.app.Notification.Style? {
    val plates = store.plates
    val id = store.plateFor(b, ch)?.id ?: plates.takeIf { it.isNotEmpty() }?.let { it[Math.floorMod(LocalDate.now().toEpochDay(), it.size.toLong()).toInt()].id } ?: return null
    val bmp = runCatching { ctx.assets.open("plates/$id.jpg").use { android.graphics.BitmapFactory.decodeStream(it, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 2 }) } }.getOrNull() ?: return null
    return android.app.Notification.BigPictureStyle().bigPicture(bmp).setSummaryText(body)
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

/** 정확한 알람: 안드로이드 12+ 는 사용자가 허락해야 해요 (14+ 새 설치는 기본 꺼짐). */
object ExactAlarm {
    fun allowed(ctx: Context): Boolean = Build.VERSION.SDK_INT < 31 || ctx.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()
    /** 폰 설정의 ‘알람 및 리마인더’ 화면 (이 앱). */
    fun openSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT < 31) return
        runCatching { ctx.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
}
