package com.resttimer.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactButton
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.RadioButton
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import kotlinx.coroutines.delay
import kotlin.math.abs

private val Accent = Color(0xFF66BB6A)
private val AlertRed = Color(0xFFC62828)

@Composable
fun RestTimerApp(
    isAmbient: Boolean,
    showSettings: Boolean,
    onOpenSettings: () -> Unit,
    onCloseSettings: () -> Unit,
) {
    val state by TimerController.state.collectAsStateWithLifecycle()
    val settings by TimerController.settings.collectAsStateWithLifecycle()

    MaterialTheme {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            when {
                showSettings && state is TimerState.Idle ->
                    SettingsScreen(settings, onClose = onCloseSettings)
                state is TimerState.Running ->
                    RunningScreen(state as TimerState.Running, isAmbient)
                state is TimerState.Alerting ->
                    AlertScreen(isAmbient)
                else ->
                    IdleScreen(settings, isAmbient, onOpenSettings)
            }
        }
    }
}

// ---------------- ① 대기 화면 ----------------

@Composable
private fun IdleScreen(settings: TimerSettings, isAmbient: Boolean, onOpenSettings: () -> Unit) {
    val focus = remember { FocusRequester() }
    var rotaryAccum by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            // 베젤 회전으로 10초씩 조정
            .onRotaryScrollEvent {
                rotaryAccum += it.verticalScrollPixels
                if (abs(rotaryAccum) >= ROTARY_STEP_PX) {
                    TimerController.adjustRest(if (rotaryAccum > 0) 1 else -1)
                    rotaryAccum = 0f
                }
                true
            }
            .focusRequester(focus)
            .focusable(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("휴식시간", color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isAmbient) {
                CompactButton(
                    onClick = { TimerController.adjustRest(-1) },
                    colors = ButtonDefaults.secondaryButtonColors(),
                ) { Text("−", fontSize = 20.sp) }
            }
            Text(
                TimerLogic.formatSeconds(settings.restSeconds),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            if (!isAmbient) {
                CompactButton(
                    onClick = { TimerController.adjustRest(1) },
                    colors = ButtonDefaults.secondaryButtonColors(),
                ) { Text("+", fontSize = 20.sp) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "하단 버튼으로 시작",
            color = if (isAmbient) Color.Gray else Accent,
            fontSize = 14.sp,
        )
        if (!isAmbient) {
            Spacer(Modifier.height(10.dp))
            CompactButton(
                onClick = onOpenSettings,
                colors = ButtonDefaults.secondaryButtonColors(),
            ) {
                Icon(
                    painterResource(R.drawable.ic_settings),
                    contentDescription = "설정",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private const val ROTARY_STEP_PX = 48f

// ---------------- ② 카운트다운 화면 ----------------

@Composable
private fun RunningScreen(state: TimerState.Running, isAmbient: Boolean) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(state.endAtMillis, isAmbient) {
        while (true) {
            now = System.currentTimeMillis()
            if (now >= state.endAtMillis) {
                // 알람보다 화면이 먼저 0초를 확인한 경우 (중복 호출은 무시됨)
                TimerController.onAlarmFired(context)
                break
            }
            delay(if (isAmbient) 1000 else 100)
        }
    }

    val remaining = (state.endAtMillis - now).coerceAtLeast(0)
    val progress = if (state.totalMillis > 0) remaining.toFloat() / state.totalMillis else 0f

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { TimerController.cancel(context) })
            },
        contentAlignment = Alignment.Center,
    ) {
        if (!isAmbient) {
            CircularProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxSize().padding(3.dp),
                indicatorColor = Accent,
                trackColor = Color.DarkGray,
                strokeWidth = 8.dp,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                TimerLogic.formatRemaining(remaining),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            if (!isAmbient) {
                Text("길게 눌러 취소", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

// ---------------- ③ 알림 화면 ----------------

@Composable
private fun AlertScreen(isAmbient: Boolean) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(if (isAmbient) Color.Black else AlertRed),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "휴식 끝!",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { TimerController.dismiss(context) },
            modifier = Modifier.size(width = 120.dp, height = 56.dp),
            colors = ButtonDefaults.primaryButtonColors(
                backgroundColor = Color.White,
                contentColor = AlertRed,
            ),
        ) {
            Text("다시", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text("하단 버튼 = 다시", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
    }
}

// ---------------- 설정 화면 ----------------

@Composable
private fun SettingsScreen(settings: TimerSettings, onClose: () -> Unit) {
    val listState = rememberScalingLazyListState()
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { ListHeader { Text("설정") } }
        item {
            ToggleChip(
                checked = settings.vibrate,
                onCheckedChange = { v -> TimerController.updateSettings { it.copy(vibrate = v) } },
                label = { Text("진동") },
                toggleControl = { Switch(checked = settings.vibrate) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            ToggleChip(
                checked = settings.sound,
                onCheckedChange = { v -> TimerController.updateSettings { it.copy(sound = v) } },
                label = { Text("소리") },
                toggleControl = { Switch(checked = settings.sound) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            ListHeader {
                Text(
                    "카운트다운 중\n하단 버튼",
                    textAlign = TextAlign.Center,
                )
            }
        }
        item {
            CountdownOption(
                label = "처음부터 다시",
                selected = settings.countdownButton == CountdownButtonAction.RESTART,
                value = CountdownButtonAction.RESTART,
            )
        }
        item {
            CountdownOption(
                label = "무시",
                selected = settings.countdownButton == CountdownButtonAction.IGNORE,
                value = CountdownButtonAction.IGNORE,
            )
        }
        item {
            Chip(
                onClick = onClose,
                label = { Text("완료") },
                colors = ChipDefaults.primaryChipColors(),
                modifier = Modifier.width(120.dp).padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun CountdownOption(label: String, selected: Boolean, value: CountdownButtonAction) {
    ToggleChip(
        checked = selected,
        onCheckedChange = { TimerController.updateSettings { it.copy(countdownButton = value) } },
        label = { Text(label) },
        toggleControl = { RadioButton(selected = selected) },
        modifier = Modifier.fillMaxWidth(),
    )
}
