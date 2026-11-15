package com.resttimer.wear

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat

/**
 * 휴식 종료 알림: 설정에 따라 진동·비프음을 반복한다.
 * [다시] 를 누르거나 30초가 지나면 멈춘다.
 */
class AlertService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var vibrator: Vibrator? = null
    private var tone: ToneGenerator? = null

    private val beepLoop = object : Runnable {
        override fun run() {
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
            handler.postDelayed(this, 1000)
        }
    }

    private val autoStop = Runnable { stopSelf() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, Notifications.ID_ALERT, Notifications.buildAlert(this), type)

        stopFeedback()
        TimerController.init(this)
        val s = TimerController.settings.value
        if (s.vibrate) startVibration()
        if (s.sound) startBeep()
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, TimerLogic.ALERT_AUTO_STOP_MILLIS)
        return START_NOT_STICKY
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator = v
        // 0.5초 진동, 0.3초 쉼, 0.5초 진동, 0.7초 쉼 → 반복
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 300, 500, 700), 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(
                effect,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
            )
        }
    }

    private fun startBeep() {
        tone = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100) }.getOrNull()
        handler.post(beepLoop)
    }

    private fun stopFeedback() {
        handler.removeCallbacks(beepLoop)
        tone?.release()
        tone = null
        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopFeedback()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, AlertService::class.java)
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                // 백그라운드에서 포그라운드 서비스 시작이 막힌 경우: 알림을 띄우고 짧게라도 진동
                try {
                    androidx.core.app.NotificationManagerCompat.from(context)
                        .notify(Notifications.ID_ALERT, Notifications.buildAlert(context))
                } catch (_: SecurityException) {
                }
                val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.getSystemService(VibratorManager::class.java).defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Vibrator::class.java)
                }
                v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 300, 500, 300, 500), -1))
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlertService::class.java))
        }
    }
}
