package com.resttimer.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 휴식시간 종료 시각, 또는 운동 모드 자동 종료 시각에 AlarmManager 가 호출한다. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TimerController.init(context)
        if (intent.action == ACTION_SESSION_TIMEOUT) {
            TimerController.onSessionTimeout(context)
        } else {
            TimerController.onAlarmFired(context)
        }
    }

    companion object {
        const val ACTION_SESSION_TIMEOUT = "com.resttimer.wear.SESSION_TIMEOUT"
    }
}
