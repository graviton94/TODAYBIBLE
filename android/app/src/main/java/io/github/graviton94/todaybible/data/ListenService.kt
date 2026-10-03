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
        fun stop(ctx: Context) { ctx.startService(Intent(ctx, ListenService::class.java).setAction("stop")) }
    }

    private val ui = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var guide: GuideVoice? = null
    private var book = 0; private var chapter = 1; private var verse = 1; private var rate = 1f
    private var token = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "stop") { finish(); return START_NOT_STICKY }
        book = intent?.getIntExtra("b", 0) ?: 0; chapter = intent?.getIntExtra("c", 1) ?: 1
        verse = intent?.getIntExtra("v", 1) ?: 1; rate = intent?.getFloatExtra("r", 1f) ?: 1f
        foreground()
        token++
        play(token)
        return START_NOT_STICKY
    }

    private fun foreground() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, getString(R.string.listen_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 5, Intent(this, MainActivity::class.java).putExtra("page", 1).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_IMMUTABLE)
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
        val voice = store.narrator
        Thread {
            val narrated = korean && voice != Narration.DEVICE && Narration.fetch(this, voice, book, chapter)
            ui.post {
                if (t != token) return@post
                val text = store.book(tr, book)
                val verses = text.fillable(chapter).filter { it >= verse }
                if (verses.isEmpty()) { next(t); return@post }
                fun step(i: Int) {
                    if (t != token) return
                    if (i >= verses.size) { next(t); return }
                    val v = verses[i]; verse = v
                    _now.value = Now(book, chapter, v, true)
                    val after = { ui.postDelayed({ step(i + 1) }, (900 / rate).toLong()) }
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
                        g.whenReady { ui.post { if (t == token) g.speak(Markup.plain(text.verse(chapter, v)), rate, { _, _ -> }, { ui.post { after() } }) } }
                    }
                }
                step(0)
            }
        }.start()
    }

    /** 다음 장으로 (권 끝이면 멈춤). */
    private fun next(t: Int) {
        if (t != token) return
        if (chapter >= Canon.books[book].chapters) { finish(); return }
        chapter++; verse = 1; foreground(); play(t)
    }

    private fun finish() {
        token++
        runCatching { player?.release() }; player = null
        guide?.release(); guide = null
        _now.value = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() { finish(); super.onDestroy() }
}
