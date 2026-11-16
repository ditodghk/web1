package com.resttimer.wear

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.wear.ambient.AmbientLifecycleObserver

class MainActivity : ComponentActivity() {

    /** 화면이 어두운 상태(앰비언트)인지 */
    private var isAmbient by mutableStateOf(false)

    /** 현재 화면: 메인(대기/카운트다운/알림) 또는 설정 */
    private var showSettings by mutableStateOf(false)

    /** 마지막으로 화면이 깨어난 시각 (uptime). 깨어난 직후의 버튼 입력은 무시한다. */
    private var lastWakeUptime = 0L

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            isAmbient = true
        }

        override fun onExitAmbient() {
            isAmbient = false
            lastWakeUptime = SystemClock.uptimeMillis()
        }

        override fun onUpdateAmbient() {}
    }

    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            lastWakeUptime = SystemClock.uptimeMillis()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TimerController.init(this)
        Notifications.ensureChannels(this)
        lifecycle.addObserver(AmbientLifecycleObserver(this, ambientCallback))

        // 하단(뒤로 가기) 버튼을 가로채서 타이머 조작에 사용
        onBackPressedDispatcher.addCallback(this) { onBottomButton() }

        requestNotificationPermissionIfNeeded()

        setContent {
            RestTimerApp(
                isAmbient = isAmbient,
                showSettings = showSettings,
                onOpenSettings = { showSettings = true },
                onCloseSettings = { showSettings = false },
                onEndWorkout = {
                    // 운동 모드를 끝내고 앱을 닫아 워치페이스로 돌아간다
                    TimerController.endSession(this)
                    finishAndRemoveTask()
                },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop() {
        unregisterReceiver(screenOnReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        TimerController.dropStaleAlert()
    }

    /** 일부 워치는 버튼이 STEM 키로 들어오므로 함께 처리 (갤럭시워치6 은 뒤로 가기 키로 추정). */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_STEM_1 ||
            keyCode == KeyEvent.KEYCODE_STEM_2 ||
            keyCode == KeyEvent.KEYCODE_STEM_3
        ) {
            if (event.repeatCount == 0) onBottomButton()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun onBottomButton() {
        if (TimerLogic.shouldIgnoreButton(isAmbient, SystemClock.uptimeMillis(), lastWakeUptime)) return
        if (showSettings) {
            showSettings = false
            return
        }
        TimerController.onHardwareButton(this)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }
}
