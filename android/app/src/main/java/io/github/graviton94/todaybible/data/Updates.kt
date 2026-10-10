package io.github.graviton94.todaybible.data

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

/**
 * 새 버전 알아보기 (Play 앱 안 업데이트, 쓰던 화면을 막지 않는 방식).
 * 있으면 화면 아래 한 줄 → 누르면 Play 가 뒤에서 받고 → 다 받으면 ‘다시 시작’ 한 번.
 * Play 에서 설치한 앱에서만 돌아요 (직접 설치한 시험판은 늘 ‘없음’).
 */
class Updates(activity: ComponentActivity) {
    enum class State { NONE, AVAILABLE, DOWNLOADING, READY }
    var state by mutableStateOf(State.NONE)
        private set
    /** 받는 중 0..1 (모르면 0). */
    var progress by mutableFloatStateOf(0f)
        private set

    private val manager = AppUpdateManagerFactory.create(activity.applicationContext)
    private var info: AppUpdateInfo? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        // Play 창에서 ‘나중에’ 를 고르면 다시 ‘있음’ 으로
        if (r.resultCode != android.app.Activity.RESULT_OK && state == State.DOWNLOADING) state = State.AVAILABLE
    }
    private val listener = InstallStateUpdatedListener { st ->
        when (st.installStatus()) {
            InstallStatus.DOWNLOADING -> { state = State.DOWNLOADING; progress = if (st.totalBytesToDownload() > 0) st.bytesDownloaded().toFloat() / st.totalBytesToDownload() else 0f }
            InstallStatus.DOWNLOADED -> state = State.READY
            InstallStatus.FAILED, InstallStatus.CANCELED -> state = State.AVAILABLE
            else -> {}
        }
    }

    init { runCatching { manager.registerListener(listener) } }

    /** 앱으로 돌아올 때마다: 이미 받아 둔 것이 있으면 ‘다시 시작’, 새 버전이 있으면 ‘있음’. */
    fun check() {
        runCatching {
            manager.appUpdateInfo.addOnSuccessListener { i ->
                info = i
                when {
                    i.installStatus() == InstallStatus.DOWNLOADED -> state = State.READY
                    i.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE && i.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE) -> if (state == State.NONE) state = State.AVAILABLE
                    i.updateAvailability() == UpdateAvailability.UPDATE_NOT_AVAILABLE -> state = State.NONE
                }
            }
        }
    }

    fun start() {
        val i = info ?: return
        runCatching { manager.startUpdateFlowForResult(i, launcher, AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build()); state = State.DOWNLOADING; progress = 0f }
    }

    /** 받아 둔 새 버전으로 다시 시작. */
    fun install() { runCatching { manager.completeUpdate() } }

    fun close() { runCatching { manager.unregisterListener(listener) } }

    /** 캡처용 (debug 빌드만). */
    fun debugSet(s: State, p: Float = 0f) { state = s; progress = p }
}
