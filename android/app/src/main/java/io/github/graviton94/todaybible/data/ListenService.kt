package io.github.graviton94.todaybible.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.github.graviton94.todaybible.MainActivity
import io.github.graviton94.todaybible.R
import io.github.graviton94.todaybible.core.Canon
import io.github.graviton94.todaybible.core.Markup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 듣기 (다1): 낭독 목소리로 한 장을 이어 들어요. 화면이 꺼져도 계속 (알림에서 멈춤).
 * 장이 끝나면 같은 권의 다음 장으로. 듣기만 한 절은 채우지 않아요 (필사는 내가 읽어야).
 * 낭독 음원(M5 · F5)을 장마다 받아 쓰고, 받을 수 없으면 폰 목소리로 대신.
 */
class ListenService : Service() {
    data class Now(val book: Int, val chapter: Int, val verse: Int, val playing: Boolean)

    companion object {
        private const val CHANNEL = "listen"; private const val ID = 11
        private val _now = MutableStateFlow<Now?>(null)
        val now: StateFlow<Now?> = _now

        fun start(ctx: Context, book: Int, chapter: Int, verse: Int, rate: Float) {
            val i = Intent(ctx, ListenService::class.java).putExtra("b", book).putExtra("c", chapter).putExtra("v", verse).putExtra("r", rate)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
        /** 기도문처럼 정한 절들만 차례로 (장 · 절 범위 여러 개) 듣고 멈춰요. */
        fun startPassages(ctx: Context, list: List<io.github.graviton94.todaybible.core.Reference.Passage>, rate: Float) {
            if (list.isEmpty()) return
            val q = list.flatMap { listOf(it.book, it.chapter, it.from, it.to) }.toIntArray()
            val i = Intent(ctx, ListenService::class.java).putExtra("q", q).putExtra("r", rate)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
        }
        fun stop(ctx: Context) { ctx.startService(Intent(ctx, ListenService::class.java).setAction("stop")) }

        /** 잠들기 타이머: 0 끔 · TIMER_CHAPTER 이 장 끝까지 · 그 밖은 분. */
        const val TIMER_CHAPTER = -1
        val TIMERS = intArrayOf(0, 15, 30, TIMER_CHAPTER)
        private val _timer = MutableStateFlow(0)
        val timer: StateFlow<Int> = _timer
        fun setTimer(ctx: Context, mode: Int) { if (_now.value != null) ctx.startService(Intent(ctx, ListenService::class.java).setAction("timer").putExtra("m", mode)) }
        fun setRate(ctx: Context, rate: Float) { if (_now.value != null) ctx.startService(Intent(ctx, ListenService::class.java).setAction("rate").putExtra("r", rate)) }
    }

    private val ui = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var guide: GuideVoice? = null
    private var book = 0; private var chapter = 1; private var verse = 1; private var rate = 1f
    private var token = 0
    private var stopAt = 0L
    /** 범위 듣기: 이 절까지 (0 = 장 끝까지 이어서) · 다음에 들을 범위들. */
    private var to = 0
    private val queue = ArrayDeque<IntArray>()
    // 화면이 꺼져도 절 사이 쉼 · 다음 장 받기 동안 멈추지 않게
    private var wake: android.os.PowerManager.WakeLock? = null
    private var wifi: android.net.wifi.WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { finish(); return START_NOT_STICKY }
        if (intent?.action == "timer") {
            val m = intent.getIntExtra("m", 0); _timer.value = m
            stopAt = if (m > 0) System.currentTimeMillis() + m * 60_000L else 0L
            return START_NOT_STICKY
        }
        if (intent?.action == "rate") {
            rate = intent.getFloatExtra("r", 1f)
            player?.let { p -> runCatching { if (p.isPlaying) p.playbackParams = p.playbackParams.setSpeed(rate) } }
            return START_NOT_STICKY
        }
        book = intent?.getIntExtra("b", 0) ?: 0; chapter = intent?.getIntExtra("c", 1) ?: 1
        verse = intent?.getIntExtra("v", 1) ?: 1; rate = intent?.getFloatExtra("r", 1f) ?: 1f
        queue.clear(); to = 0
        intent?.getIntArrayExtra("q")?.let { q ->
            q.toList().chunked(4).forEach { queue.addLast(it.toIntArray()) }
            queue.removeFirstOrNull()?.let { book = it[0]; chapter = it[1]; verse = it[2]; to = it[3] }
        }
        foreground()
        if (wake == null) wake = getSystemService(android.os.PowerManager::class.java)?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "harubible:listen")?.apply { setReferenceCounted(false); acquire(3 * 60 * 60 * 1000L) }
        if (wifi == null) wifi = runCatching { applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)?.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "harubible:listen")?.apply { setReferenceCounted(false); acquire() } }.getOrNull()
        token++
        play(token)
        return START_NOT_STICKY
    }

    private fun foreground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.listen_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 5, Intent(this, MainActivity::class.java).putExtra("page", io.github.graviton94.todaybible.ui.AppState.COPY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 6, Intent(this, ListenService::class.java).setAction("stop"), PendingIntent.FLAG_IMMUTABLE)
        val store = Store(this)
        val name = Canon.books[book].let { if (store.translation == io.github.graviton94.todaybible.core.Translation.KRV) it.ko else it.en }
        val n = Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_stat_cross).setContentTitle(getString(R.string.listen_title, name, chapter))
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_cross), getString(R.string.listen_stop), stop).build()).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK) else startForeground(ID, n)
    }

    /** 지금 절부터 차례로. 장 음원이 없으면 받아 보고, 끝내 없으면 폰 목소리. */
    private fun play(t: Int) {
        val store = Store(this); val tr = store.translation
        val korean = tr == io.github.graviton94.todaybible.core.Translation.KRV
        val voice = store.narratorFor(tr)
        Thread {
            val narrated = voice != Narration.DEVICE && Narration.fetch(this, voice, book, chapter)
            if (voice != Narration.DEVICE && !narrated) Narration.fellBack.value = true
            ui.post {
                if (t != token) return@post
                val text = store.book(tr, book)
                val verses = text.fillable(chapter).filter { it >= verse && (to == 0 || it <= to) }
                if (verses.isEmpty()) { next(t); return@post }
                fun step(i: Int) {
                    if (t != token) return
                    if (i >= verses.size) { next(t); return }
                    if (stopAt > 0 && System.currentTimeMillis() >= stopAt) { finish(); return }
                    val v = verses[i]; verse = v
                    _now.value = Now(book, chapter, v, true)
                    // 절 사이는 짧게 (책 읽어 주듯 이어서)
                    val after = { ui.postDelayed({ step(i + 1) }, (Narration.GAP_MS / rate).toLong()) }
                    val f = if (narrated) Narration.file(this, voice, book, chapter, v).takeIf { it.exists() } else null
                    if (f != null) {
                        runCatching { player?.release() }
                        player = runCatching {
                            MediaPlayer().apply {
                                setDataSource(f.path); prepare()
                                if (rate != 1f) playbackParams = playbackParams.setSpeed(rate)
                                setOnCompletionListener { after() }
                                start()
                            }
                        }.getOrNull() ?: run { after(); null }
                    } else {
                        val g = guide ?: GuideVoice(this, korean, store.guideVoice).also { guide = it }
                        g.whenReady { ui.post { if (t == token) { if (!g.ready) finish() else g.speak(Markup.plain(text.verse(chapter, v)), rate, { _, _ -> }, { ui.post { after() } }) } } }
                    }
                }
                step(0)
            }
        }.start()
    }

    /** 다음 장으로 (권 끝이면 다음 권 · 요한계시록 끝 · 이 장까지 타이머면 멈춤). */
    private fun next(t: Int) {
        if (t != token) return
        if (to > 0) {
            val n = queue.removeFirstOrNull() ?: run { finish(); return }
            book = n[0]; chapter = n[1]; verse = n[2]; to = n[3]; foreground(); play(t); return
        }
        if (_timer.value == TIMER_CHAPTER) { finish(); return }
        if (chapter < Canon.books[book].chapters) chapter++
        else if (book < 65 && (book + 1 in Canon.free || getSharedPreferences("today", MODE_PRIVATE).getBoolean("lifetime", false))) { book++; chapter = 1 }
        else { finish(); return }
        verse = 1; foreground(); play(t)
    }

    private fun finish() {
        token++
        runCatching { player?.release() }; player = null
        guide?.release(); guide = null
        _now.value = null; _timer.value = 0; stopAt = 0L
        runCatching { wake?.release() }; wake = null; runCatching { wifi?.release() }; wifi = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() { finish(); super.onDestroy() }
}
