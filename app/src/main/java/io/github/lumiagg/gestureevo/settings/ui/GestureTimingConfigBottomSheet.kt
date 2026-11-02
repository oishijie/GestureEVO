package io.github.lumiagg.gestureevo.settings.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.gesture.config.FreeTriggerConfig
import io.github.lumiagg.gestureevo.gesture.config.GestureTimingConfig
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

interface GestureTimingConfigCallback {
    fun onTimingConfigChanged()
}

class GestureTimingConfigBottomSheet(
    private val activity: Activity,
    private val triggerConfig: FreeTriggerConfig,
    private val callback: GestureTimingConfigCallback
) {
    fun show() {
        // Each opening starts a new draft. Neither sliding nor resetting mutates the trigger.
        val initial = triggerConfig.gestureTimingConfig.copy()
        showMiuixSheet(activity, dismissOnOutside = false) { dismiss ->
            var draft by remember {
                mutableStateOf(
                    GestureTimingConfig(
                        longPressTimeoutMs = initial.resolvedLongPressTimeoutMs().toInt(),
                        doubleTapTimeoutMs = initial.resolvedDoubleTapTimeoutMs().toInt(),
                        pauseTimeoutMs = initial.resolvedPauseTimeoutMs().toInt(),
                        swipeThresholdDp = (initial.swipeThresholdDp.takeIf { it > 0 }
                            ?: GestureTimingConfig.DEFAULT_SWIPE_THRESHOLD_DP).coerceIn(
                            GestureTimingConfig.MIN_SWIPE_THRESHOLD_DP,
                            GestureTimingConfig.MAX_SWIPE_THRESHOLD_DP
                        ),
                        backThresholdDp = (initial.backThresholdDp.takeIf { it > 0 }
                            ?: GestureTimingConfig.DEFAULT_BACK_THRESHOLD_DP).coerceIn(
                            GestureTimingConfig.MIN_BACK_THRESHOLD_DP,
                            GestureTimingConfig.MAX_BACK_THRESHOLD_DP
                        )
                    )
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.gesture_timing_config_title),
                    modifier = Modifier.padding(top = 24.dp),
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Column(
                    modifier = Modifier.weight(1f, fill = false)
                        .heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.gesture_timing_section_title),
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.gesture_timing_section_desc),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    TimingSlider(
                        title = R.string.gesture_timing_long_press_title,
                        description = R.string.gesture_timing_long_press_desc,
                        value = draft.longPressTimeoutMs,
                        minimum = GestureTimingConfig.MIN_LONG_PRESS_TIMEOUT_MS,
                        maximum = GestureTimingConfig.MAX_LONG_PRESS_TIMEOUT_MS,
                        step = 10,
                        unit = "ms"
                    ) { draft = draft.copy(longPressTimeoutMs = it) }
                    TimingSlider(
                        title = R.string.gesture_timing_double_tap_title,
                        description = R.string.gesture_timing_double_tap_desc,
                        value = draft.doubleTapTimeoutMs,
                        minimum = GestureTimingConfig.MIN_DOUBLE_TAP_TIMEOUT_MS,
                        maximum = GestureTimingConfig.MAX_DOUBLE_TAP_TIMEOUT_MS,
                        step = 10,
                        unit = "ms"
                    ) { draft = draft.copy(doubleTapTimeoutMs = it) }
                    TimingSlider(
                        title = R.string.gesture_timing_pause_title,
                        description = R.string.gesture_timing_pause_desc,
                        value = draft.pauseTimeoutMs,
                        minimum = GestureTimingConfig.MIN_PAUSE_TIMEOUT_MS,
                        maximum = GestureTimingConfig.MAX_PAUSE_TIMEOUT_MS,
                        step = 10,
                        unit = "ms"
                    ) { draft = draft.copy(pauseTimeoutMs = it) }
                    TimingSlider(
                        title = R.string.gesture_timing_swipe_threshold_title,
                        description = R.string.gesture_timing_swipe_threshold_desc,
                        value = draft.swipeThresholdDp,
                        minimum = GestureTimingConfig.MIN_SWIPE_THRESHOLD_DP,
                        maximum = GestureTimingConfig.MAX_SWIPE_THRESHOLD_DP,
                        step = 1,
                        unit = "dp"
                    ) { draft = draft.copy(swipeThresholdDp = it) }
                    TimingSlider(
                        title = R.string.gesture_timing_back_threshold_title,
                        description = R.string.gesture_timing_back_threshold_desc,
                        value = draft.backThresholdDp,
                        minimum = GestureTimingConfig.MIN_BACK_THRESHOLD_DP,
                        maximum = GestureTimingConfig.MAX_BACK_THRESHOLD_DP,
                        step = 1,
                        unit = "dp"
                    ) { draft = draft.copy(backThresholdDp = it) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    TextButton(
                        text = stringResource(R.string.gesture_timing_config_reset),
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        onClick = {
                            draft = GestureTimingConfig(
                                longPressTimeoutMs = GestureTimingConfig.defaultLongPressTimeoutMs(),
                                doubleTapTimeoutMs = GestureTimingConfig.defaultDoubleTapTimeoutMs(),
                                pauseTimeoutMs = GestureTimingConfig.DEFAULT_PAUSE_TIMEOUT_MS,
                                swipeThresholdDp = GestureTimingConfig.DEFAULT_SWIPE_THRESHOLD_DP,
                                backThresholdDp = GestureTimingConfig.DEFAULT_BACK_THRESHOLD_DP
                            )
                        }
                    )
                    TextButton(
                        text = stringResource(R.string.gesture_timing_config_save),
                        modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            // Zero is the persisted sentinel for system/default timing.
                            triggerConfig.gestureTimingConfig = GestureTimingConfig(
                                longPressTimeoutMs = draft.longPressTimeoutMs.takeUnless {
                                    it == GestureTimingConfig.defaultLongPressTimeoutMs()
                                } ?: 0,
                                doubleTapTimeoutMs = draft.doubleTapTimeoutMs.takeUnless {
                                    it == GestureTimingConfig.defaultDoubleTapTimeoutMs()
                                } ?: 0,
                                pauseTimeoutMs = draft.pauseTimeoutMs.takeUnless {
                                    it == GestureTimingConfig.DEFAULT_PAUSE_TIMEOUT_MS
                                } ?: 0,
                                swipeThresholdDp = draft.swipeThresholdDp.takeUnless {
                                    it == GestureTimingConfig.DEFAULT_SWIPE_THRESHOLD_DP
                                } ?: 0,
                                backThresholdDp = draft.backThresholdDp.takeUnless {
                                    it == GestureTimingConfig.DEFAULT_BACK_THRESHOLD_DP
                                } ?: 0
                            )
                            callback.onTimingConfigChanged()
                            dismiss()
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun TimingSlider(
        title: Int,
        description: Int,
        value: Int,
        minimum: Int,
        maximum: Int,
        step: Int,
        unit: String,
        onChange: (Int) -> Unit
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(title),
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = "$value $unit",
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Text(
                text = stringResource(description),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Slider(
                value = value.toFloat(),
                valueRange = minimum.toFloat()..maximum.toFloat(),
                steps = (maximum - minimum) / step - 1,
                modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                onValueChange = { raw ->
                    onChange((minimum + ((raw - minimum) / step).roundToInt() * step)
                        .coerceIn(minimum, maximum))
                }
            )
        }
    }
}
