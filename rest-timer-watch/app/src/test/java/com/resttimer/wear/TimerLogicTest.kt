package com.resttimer.wear

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerLogicTest {

    private val defaults = TimerSettings()
    private val running = TimerState.Running(endAtMillis = 1_000, totalMillis = 90_000)

    @Test
    fun defaultsMatchSpec() {
        assertEquals(90, defaults.restSeconds)
        assertEquals(CountdownButtonAction.RESTART, defaults.countdownButton)
        assertTrue(defaults.vibrate)
        assertTrue(defaults.sound)
    }

    @Test
    fun buttonInIdleStarts() {
        assertEquals(ButtonCommand.START, TimerLogic.commandForButton(TimerState.Idle, defaults))
    }

    @Test
    fun buttonWhileRunningFollowsSetting() {
        assertEquals(ButtonCommand.RESTART, TimerLogic.commandForButton(running, defaults))
        val ignore = defaults.copy(countdownButton = CountdownButtonAction.IGNORE)
        assertEquals(ButtonCommand.NOTHING, TimerLogic.commandForButton(running, ignore))
    }

    @Test
    fun buttonWhileAlertingDismisses() {
        assertEquals(ButtonCommand.DISMISS, TimerLogic.commandForButton(TimerState.Alerting(0), defaults))
    }

    @Test
    fun restAdjustsInTenSecondStepsWithinRange() {
        assertEquals(100, TimerLogic.adjustRestSeconds(90, 1))
        assertEquals(80, TimerLogic.adjustRestSeconds(90, -1))
        assertEquals(10, TimerLogic.adjustRestSeconds(10, -1))
        assertEquals(600, TimerLogic.adjustRestSeconds(600, 1))
    }

    @Test
    fun formatting() {
        assertEquals("1:30", TimerLogic.formatSeconds(90))
        assertEquals("0:05", TimerLogic.formatSeconds(5))
        assertEquals("10:00", TimerLogic.formatSeconds(600))
        assertEquals("0:01", TimerLogic.formatRemaining(200))
        assertEquals("1:30", TimerLogic.formatRemaining(90_000))
        assertEquals("0:00", TimerLogic.formatRemaining(-5))
    }

    @Test
    fun ignoresButtonRightAfterWake() {
        assertTrue(TimerLogic.shouldIgnoreButton(isAmbient = true, nowUptime = 10_000, lastWakeUptime = 0))
        assertTrue(TimerLogic.shouldIgnoreButton(isAmbient = false, nowUptime = 10_200, lastWakeUptime = 10_000))
        assertFalse(TimerLogic.shouldIgnoreButton(isAmbient = false, nowUptime = 10_600, lastWakeUptime = 10_000))
    }
}
