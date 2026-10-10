package io.github.graviton94.todaybible.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import io.github.graviton94.todaybible.MainActivity
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Canon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 낭독 미리 받기 (1.2): 줄에 세운 권들을 차례로 받아요. 앱을 닫아도 알림 하나 띄워 두고 끝까지.
 * 알림에 지금 권 · 받은 장 / 전체, 다 받으면 ‘받아 두었어요’ 한 줄. 받아 둔 권은 자동 정리에서 빠져요.
 */
class KeepService : Service() {
    companion object {
        private const val CHANNEL = "keep"; private const val ID = 21; private const val DONE_ID = 22
        /** 받는 중인 권 · 받은 장 · 전체 장 */
        private val _running = MutableStateFlow<Triple<Int, Int, Int>?>(null)
        val running: StateFlow<Triple<Int, Int, Int>?> = _running
        private val _queue = MutableStateFlow<List<Int>>(emptyList())
        val queue: StateFlow<List<Int>> = _queue
        /** 마지막으로 끝난 권 (권, 다 받았는지): 화면이 한 줄로 알려요. */
        val finished = MutableStateFlow<Pair<Int, Boolean>?>(null)
        @Volatile private var voice = Narration.FEMALE

        fun add(ctx: Context, v: String, book: Int) {
            if (_running.value?.first == book || book in _queue.value) return
            voice = v; _queue.value = _queue.value + book
            val i = Intent(ctx, KeepService::class.java)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
        fun cancel(book: Int) { _queue.value = _queue.value - book }
    }

    private var worker: Thread? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun attachBaseContext(base: Context) { super.attachBaseContext(io.github.graviton94.todaybible.ui.Lang.wrap(base)) }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notify(null)
        if (worker?.isAlive != true) worker = Thread { work() }.apply { name = "keep"; start() }
        return START_NOT_STICKY
    }

    private fun name(b: Int): String = Canon.books[b].let { if (Store(this).translation == io.github.graviton94.todaybible.core.Translation.KRV) it.ko else it.en }

    private fun work() {
        val wake = runCatching { getSystemService(android.os.PowerManager::class.java)?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "harubible:keep")?.apply { setReferenceCounted(false); acquire(2 * 60 * 60 * 1000L) } }.getOrNull()
        while (true) {
            val b = _queue.value.firstOrNull() ?: break
            _queue.value = _queue.value.drop(1)
            val n = Canon.books[b].chapters
            _running.value = Triple(b, 0, n); notify(_running.value)
            val ok = Narration.keepBook(this, voice, b, n) { done, all -> _running.value = Triple(b, done, all); notify(_running.value) }
            _running.value = null
            finished.value = b to ok
            // 다 받은 권은 따로 한 줄 (지울 수 있는 알림)
            getSystemService(NotificationManager::class.java).notify(DONE_ID + b, Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_cross)
                .setContentTitle(getString(if (ok) R.string.keep_done else R.string.keep_partial, name(b))).setAutoCancel(true).setContentIntent(open()).build())
        }
        runCatching { wake?.release() }
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    private fun open() = PendingIntent.getActivity(this, 31, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private var started = false
    private fun notify(run: Triple<Int, Int, Int>?) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.keep_title), NotificationManager.IMPORTANCE_LOW))
        val left = _queue.value.size
        val n = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_cross).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open())
            .setContentTitle(getString(R.string.keep_title))
            .setContentText(run?.let { getString(R.string.keep_notif, name(it.first), it.second, it.third) + if (left > 0) " · " + getString(R.string.keep_notif_left, left) else "" } ?: "")
            .apply { run?.let { setProgress(it.third, it.second, false) } }
            .build()
        if (!started) {
            started = true
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else startForeground(ID, n)
        } else nm.notify(ID, n)
    }
}
