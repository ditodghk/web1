package com.resttimer.wear

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status

object Notifications {

    private const val CH_RUNNING = "running"
    private const val CH_ALERT = "alert"
    const val ID_RUNNING = 1
    const val ID_ALERT = 2

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_RUNNING, "휴식 중", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALERT, "휴식 끝 알림", NotificationManager.IMPORTANCE_HIGH).apply {
                // 소리·진동은 AlertService 가 설정에 따라 직접 낸다.
                setSound(null, null)
                enableVibration(false)
            }
        )
    }

    private fun openAppIntent(context: Context) = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** 알림 권한은 API 33(Wear OS 4)부터 런타임 권한. 그 이전은 항상 허용. */
    private fun canPost(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * 카운트다운 중 표시되는 진행 중 알림 + Wear OS Ongoing Activity.
     * 워치페이스에 남은 시간이 표시되고, 탭하면 앱으로 돌아온다.
     */
    fun showRunning(context: Context, endAtMillis: Long) {
        ensureChannels(context)
        if (!canPost(context)) return
        val open = openAppIntent(context)
        val builder = NotificationCompat.Builder(context, CH_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("휴식 중")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(endAtMillis)
            .setContentIntent(open)

        val endElapsed = SystemClock.elapsedRealtime() + (endAtMillis - System.currentTimeMillis())
        val status = Status.Builder()
            .addTemplate("휴식 #time#")
            .addPart("time", Status.TimerPart(endElapsed))
            .build()
        OngoingActivity.Builder(context, ID_RUNNING, builder)
            .setStaticIcon(R.drawable.ic_timer)
            .setTouchIntent(open)
            .setStatus(status)
            .build()
            .apply(context)

        try {
            NotificationManagerCompat.from(context).notify(ID_RUNNING, builder.build())
        } catch (_: SecurityException) {
        }
    }

    /**
     * 운동 모드 대기 중 표시되는 진행 중 활동. 카운트다운 알림과 같은 ID 를 써서 서로 교체된다.
     * 이 알림이 있으면 화면이 꺼졌다 켜져도 앱으로 돌아온다.
     */
    fun showSession(context: Context) {
        ensureChannels(context)
        if (!canPost(context)) return
        val open = openAppIntent(context)
        val builder = NotificationCompat.Builder(context, CH_RUNNING)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("운동 중")
            .setContentText("하단 버튼으로 휴식 시작")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setContentIntent(open)

        val status = Status.Builder()
            .addTemplate("운동 중")
            .build()
        OngoingActivity.Builder(context, ID_RUNNING, builder)
            .setStaticIcon(R.drawable.ic_timer)
            .setTouchIntent(open)
            .setStatus(status)
            .build()
            .apply(context)

        try {
            NotificationManagerCompat.from(context).notify(ID_RUNNING, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancelRunning(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_RUNNING)
    }

    /** 0초 알림. 전체 화면 인텐트로 화면이 꺼져 있어도 알림 화면을 띄운다. */
    fun buildAlert(context: Context): Notification {
        ensureChannels(context)
        val open = openAppIntent(context)
        return NotificationCompat.Builder(context, CH_ALERT)
            .setSmallIcon(R.drawable.ic_timer)
            .setContentTitle("휴식 끝!")
            .setContentText("다음 세트를 시작하세요")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .build()
    }

    fun cancelAlert(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_ALERT)
    }
}
