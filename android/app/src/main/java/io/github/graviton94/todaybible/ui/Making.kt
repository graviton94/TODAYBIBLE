package io.github.graviton94.todaybible.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import io.github.graviton94.todaybible.MainActivity
import io.github.graviton94.todaybible.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * 오래 걸리는 파일 만들기 (내 목소리 영상 · 소리): 화면과 따로 뒤에서 끝까지 만들어요.
 * 만드는 동안 알림 표시줄에 진행 · 남은 시간, 다 되면 「눌러서 저장 · 보내기」. 관문을 닫아도 그대로 이어지고 다시 열면 그 자리.
 */
object Making {
    /** 지금 만드는 것 (없으면 null): 관문 머리 · 진행 (0..1) · 남은 초 · 다 됨 / 못 만듦 · 만든 파일. */
    data class State(val title: String, val eyebrow: String, val plate: String?, val gather: String?, val frac: Float = 0f, val leftSec: Int? = null,
                     val done: Boolean = false, val failed: Boolean = false, val job: ExportJob? = null)
    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state
    val busy: Boolean get() = _state.value?.let { !it.done && !it.failed } == true

    private const val CHANNEL = "making"; private const val ID = 31

    /** work 는 뒤 스레드에서: 진행을 알리며 파일을 만들고 (못 만들면 null) 내보낼 일감을 돌려줘요. */
    fun start(ctx0: Context, title: String, eyebrow: String, plate: String?, gather: String?, work: ((Float) -> Unit) -> ExportJob?) {
        if (busy) return
        val ctx = ctx0.applicationContext
        _state.value = State(title, eyebrow, plate, gather)
        notify(ctx)
        Thread {
            val t0 = System.currentTimeMillis(); var lastNote = 0L
            val job = runCatching {
                work { p ->
                    val now = System.currentTimeMillis()
                    // 남은 시간: 지금까지 걸린 시간으로 어림 (처음 3% 는 아직 몰라요)
                    val left = if (p >= 0.03f) (((now - t0) / p * (1 - p)) / 1000).toInt() else null
                    _state.value = _state.value?.copy(frac = p.coerceIn(0f, 1f), leftSec = left)
                    if (now - lastNote > 1000) { lastNote = now; notify(ctx) }
                }
            }.getOrNull()
            _state.value = _state.value?.copy(done = job != null, failed = job == null, frac = if (job != null) 1f else _state.value?.frac ?: 0f, leftSec = null, job = job)
            notify(ctx)
        }.apply { name = "making"; priority = Thread.NORM_PRIORITY + 1 }.start()
    }

    /** 다 된 것을 받았거나 (저장 · 보내기) 닫았을 때. */
    fun clear(ctx: Context) {
        if (busy) return
        _state.value = null
        runCatching { ctx.getSystemService(NotificationManager::class.java).cancel(ID) }
    }

    /** 남은 시간 한 줄 (약 1분 20초 남음 · 약 40초 남음). */
    fun leftText(ctx: Context, sec: Int?): String? = sec?.let { if (it >= 60) ctx.getString(R.string.making_left_min, it / 60, it % 60) else ctx.getString(R.string.making_left_sec, it.coerceAtLeast(1)) }

    private fun notify(ctx: Context) {
        val st = _state.value ?: return
        runCatching {
            val c = Lang.wrap(ctx)
            val nm = c.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, c.getString(R.string.making_channel), NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(c, 31, Intent(c, MainActivity::class.java).putExtra("making", true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val pct = (st.frac * 100).toInt()
            val text = when {
                st.done -> c.getString(R.string.making_done)
                st.failed -> c.getString(R.string.gate_file_failed, st.title)
                else -> listOfNotNull("$pct%", leftText(c, st.leftSec)).joinToString(" · ")
            }
            val b = android.app.Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_stat_cross)
                .setContentTitle(if (st.done || st.failed) st.title else c.getString(R.string.gate_file_making, st.title)).setContentText(text)
                .setContentIntent(open).setOnlyAlertOnce(true).setShowWhen(false)
            if (!st.done && !st.failed) b.setOngoing(true).setProgress(100, pct, pct == 0) else b.setAutoCancel(true)
            nm.notify(ID, b.build())
        }
    }
}
