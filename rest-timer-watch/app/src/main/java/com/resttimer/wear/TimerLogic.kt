package com.resttimer.wear

/**
 * 안드로이드 API에 의존하지 않는 순수 로직. 단위 테스트 대상.
 */

/** 카운트다운 중 하단 버튼을 눌렀을 때의 동작 (설정에서 선택). */
enum class CountdownButtonAction {
    /** (a) 무시 */
    IGNORE,

    /** (b) 처음부터 다시 시작 — 기본값 */
    RESTART,
}

data class TimerSettings(
    val restSeconds: Int = DEFAULT_REST_SECONDS,
    val countdownButton: CountdownButtonAction = CountdownButtonAction.RESTART,
    val vibrate: Boolean = true,
    val sound: Boolean = true,
) {
    companion object {
        const val DEFAULT_REST_SECONDS = 90
        const val MIN_REST_SECONDS = 10
        const val MAX_REST_SECONDS = 600
        const val STEP_SECONDS = 10
    }
}

sealed interface TimerState {
    data object Idle : TimerState

    /** [endAtMillis] 는 System.currentTimeMillis() 기준. */
    data class Running(val endAtMillis: Long, val totalMillis: Long) : TimerState

    data class Alerting(val sinceMillis: Long) : TimerState
}

/** 하단 버튼이 눌렸을 때 해야 할 일. */
enum class ButtonCommand { START, RESTART, NOTHING, DISMISS }

object TimerLogic {

    fun commandForButton(state: TimerState, settings: TimerSettings): ButtonCommand = when (state) {
        TimerState.Idle -> ButtonCommand.START
        is TimerState.Running -> when (settings.countdownButton) {
            CountdownButtonAction.RESTART -> ButtonCommand.RESTART
            CountdownButtonAction.IGNORE -> ButtonCommand.NOTHING
        }
        is TimerState.Alerting -> ButtonCommand.DISMISS
    }

    /** 휴식시간을 10초 단위로 [steps] 만큼 조정하고 범위 안으로 제한. */
    fun adjustRestSeconds(current: Int, steps: Int): Int {
        val raw = current + steps * TimerSettings.STEP_SECONDS
        return raw.coerceIn(TimerSettings.MIN_REST_SECONDS, TimerSettings.MAX_REST_SECONDS)
    }

    /** 남은 밀리초를 "m:ss" 로. 남은 시간이 0.2초여도 "0:01" 로 보이도록 올림. */
    fun formatRemaining(remainingMillis: Long): String {
        val totalSeconds = ((remainingMillis.coerceAtLeast(0) + 999) / 1000).toInt()
        return formatSeconds(totalSeconds)
    }

    fun formatSeconds(totalSeconds: Int): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return "%d:%02d".format(m, s)
    }

    /**
     * 화면이 꺼짐/어두운 상태에서 깨어난 직후의 버튼 입력은 무시한다 (기획 3-B:
     * "한 번 눌러 화면을 깨우고, 한 번 더 눌러 시작").
     */
    fun shouldIgnoreButton(isAmbient: Boolean, nowUptime: Long, lastWakeUptime: Long): Boolean =
        isAmbient || nowUptime - lastWakeUptime < WAKE_GUARD_MILLIS

    const val WAKE_GUARD_MILLIS = 500L

    /** 알림 상태가 이 시간보다 오래되면 앱을 다시 열었을 때 대기 화면으로 돌아간다. */
    const val STALE_ALERT_MILLIS = 10 * 60 * 1000L

    /** 운동 모드: 대기 상태로 이 시간이 지나면 자동 종료. */
    const val SESSION_TIMEOUT_MILLIS = 10 * 60 * 1000L

    /** [sessionEndAtMillis] 가 0 이면 예약된 종료 시각이 없는 것(=만료 아님). */
    fun isSessionExpired(sessionEndAtMillis: Long, nowMillis: Long): Boolean =
        sessionEndAtMillis in 1..nowMillis

    /** 진동·소리는 이 시간이 지나면 자동으로 멈춘다 (배터리 보호). 화면은 [다시] 를 누를 때까지 유지. */
    const val ALERT_AUTO_STOP_MILLIS = 30_000L
}
