package com.resttimer.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 휴식시간이 끝나는 시각에 AlarmManager 가 호출한다. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TimerController.init(context)
        TimerController.onAlarmFired(context)
    }
}
