package com.resttimer.wear

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 타이머 상태와 설정을 관리하는 단일 객체.
 * 상태는 SharedPreferences 에 저장해서 프로세스가 종료돼도 복원한다.
 */
object TimerController {

    private const val PREFS = "rest_timer"
    private const val K_REST = "rest_seconds"
    private const val K_COUNTDOWN_BTN = "countdown_button"
    private const val K_VIBRATE = "vibrate"
    private const val K_SOUND = "sound"
    private const val K_STATE = "state"
    private const val K_END_AT = "end_at"
    private const val K_TOTAL = "total"
    private const val K_ALERT_SINCE = "alert_since"

    private const val STATE_IDLE = "idle"
    private const val STATE_RUNNING = "running"
    private const val STATE_ALERTING = "alerting"

    private const val RC_ALARM = 100
    private const val RC_SHOW = 101
    private const val RESTORE_GRACE_MILLIS = 60_000L

    private lateinit var prefs: SharedPreferences

    private val _state = MutableStateFlow<TimerState>(TimerState.Idle)
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private val _settings = MutableStateFlow(TimerSettings())
    val settings: StateFlow<TimerSettings> = _settings.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _settings.value = TimerSettings(
            restSeconds = prefs.getInt(K_REST, TimerSettings.DEFAULT_REST_SECONDS),
            countdownButton = runCatching {
                CountdownButtonAction.valueOf(
                    prefs.getString(K_COUNTDOWN_BTN, null) ?: CountdownButtonAction.RESTART.name
                )
            }.getOrDefault(CountdownButtonAction.RESTART),
            vibrate = prefs.getBoolean(K_VIBRATE, true),
            sound = prefs.getBoolean(K_SOUND, true),
        )
        val now = System.currentTimeMillis()
        _state.value = when (prefs.getString(K_STATE, STATE_IDLE)) {
            STATE_RUNNING -> {
                val endAt = prefs.getLong(K_END_AT, 0)
                // 프로세스가 종료된 뒤 AlarmReceiver 가 깨운 경우에도 알림이 울리도록,
                // 종료 시각이 막 지난 타이머는 Running 으로 복원한다 (onAlarmFired 가 처리).
                if (endAt > now - RESTORE_GRACE_MILLIS) {
                    TimerState.Running(endAt, prefs.getLong(K_TOTAL, (endAt - now).coerceAtLeast(1)))
                } else TimerState.Idle
            }
            STATE_ALERTING -> TimerState.Alerting(prefs.getLong(K_ALERT_SINCE, now))
            else -> TimerState.Idle
        }
        dropStaleAlert()
    }

    // ---------- 설정 ----------

    fun updateSettings(transform: (TimerSettings) -> TimerSettings) {
        val s = transform(_settings.value)
        _settings.value = s
        prefs.edit()
            .putInt(K_REST, s.restSeconds)
            .putString(K_COUNTDOWN_BTN, s.countdownButton.name)
            .putBoolean(K_VIBRATE, s.vibrate)
            .putBoolean(K_SOUND, s.sound)
            .apply()
    }

    fun adjustRest(steps: Int) = updateSettings {
        it.copy(restSeconds = TimerLogic.adjustRestSeconds(it.restSeconds, steps))
    }

    // ---------- 하단 버튼 ----------

    fun onHardwareButton(context: Context) {
        when (TimerLogic.commandForButton(_state.value, _settings.value)) {
            ButtonCommand.START, ButtonCommand.RESTART -> start(context)
            ButtonCommand.DISMISS -> dismiss(context)
            ButtonCommand.NOTHING -> Unit
        }
    }

    // ---------- 상태 전환 ----------

    /** 타이머 시작 (실행 중이면 처음부터 다시 시작). */
    fun start(context: Context) {
        val total = _settings.value.restSeconds * 1000L
        val endAt = System.currentTimeMillis() + total
        _state.value = TimerState.Running(endAt, total)
        prefs.edit()
            .putString(K_STATE, STATE_RUNNING)
            .putLong(K_END_AT, endAt)
            .putLong(K_TOTAL, total)
            .apply()
        scheduleAlarm(context, endAt)
        Notifications.showRunning(context, endAt)
    }

    /** 카운트다운 취소 (길게 누르기). */
    fun cancel(context: Context) {
        cancelAlarm(context)
        Notifications.cancelRunning(context)
        toIdle()
    }

    /** 0초 도달. AlarmReceiver 와 화면 양쪽에서 불릴 수 있으므로 한 번만 처리. */
    @Synchronized
    fun onAlarmFired(context: Context) {
        if (_state.value !is TimerState.Running) return
        val now = System.currentTimeMillis()
        _state.value = TimerState.Alerting(now)
        prefs.edit()
            .putString(K_STATE, STATE_ALERTING)
            .putLong(K_ALERT_SINCE, now)
            .apply()
        cancelAlarm(context)
        Notifications.cancelRunning(context)
        AlertService.start(context)
    }

    /** [다시] — 알림을 끄고 대기 화면으로. */
    fun dismiss(context: Context) {
        AlertService.stop(context)
        Notifications.cancelAlert(context)
        toIdle()
    }

    /** 오래된 알림 상태는 대기 화면으로 되돌린다. */
    fun dropStaleAlert() {
        val s = _state.value
        if (s is TimerState.Alerting &&
            System.currentTimeMillis() - s.sinceMillis > TimerLogic.STALE_ALERT_MILLIS
        ) toIdle()
    }

    private fun toIdle() {
        _state.value = TimerState.Idle
        prefs.edit().putString(K_STATE, STATE_IDLE).apply()
    }

    // ---------- AlarmManager ----------

    private fun alarmIntent(context: Context) = PendingIntent.getBroadcast(
        context, RC_ALARM, Intent(context, AlarmReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun scheduleAlarm(context: Context, endAt: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val op = alarmIntent(context)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (canExact) {
            // setAlarmClock: 절전(Doze) 중에도 정확히 울리는 가장 확실한 방법
            val show = PendingIntent.getActivity(
                context, RC_SHOW, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(endAt, show), op)
        } else {
            // 권한이 없을 때의 대비책 (수 초 늦을 수 있음)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, op)
        }
    }

    private fun cancelAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context))
    }
}
