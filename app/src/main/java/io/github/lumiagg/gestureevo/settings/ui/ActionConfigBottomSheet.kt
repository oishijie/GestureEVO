package io.github.lumiagg.gestureevo.settings.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.LifecycleCoroutineScope
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.gesture.config.GestureActionDataManager
import io.github.lumiagg.gestureevo.utils.dp2px
import io.github.lumiagg.gestureevo.utils.px2dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Retain the public Material helpers for legacy callers; the sheet itself is Compose-only.
fun createSliderLabelFormatter() =
    LabelFormatter { value -> String.format(Locale.US, "%d", value.toInt()) }

fun createSliderPercentLabelFormatter() = LabelFormatter { value -> "${(value * 100).toInt()}%" }

/** Safely sets a legacy Material slider and its accompanying value label. */
fun setSliderValueSafely(slider: Slider, value: Float, valueText: android.widget.TextView) {
    val safeValue = max(slider.valueFrom, min(slider.valueTo, value))
    slider.value = safeValue
    valueText.text = "${safeValue.toInt()}"
}

fun showActionConfigBottomSheet(
    activity: AppCompatActivity,
    tag: String,
    isSubConfig: Boolean,
    coroutineScope: LifecycleCoroutineScope,
    configUpdateCallback: () -> Unit,
    onSliderStateChanged: ((key: String, value: Float, isTouching: Boolean) -> Unit)? = null
) {
    coroutineScope.launch {
        val config = GestureActionDataManager.readActionConfig(activity, tag, isSubConfig)
        // Keep commits ordered even when a switch/reset follows a slider release immediately.
        val saveMutex = Mutex()
        fun saveAndPreview() {
            coroutineScope.launch {
                saveMutex.withLock {
                    GestureActionDataManager.saveActionConfig(activity, tag, config, isSubConfig)
                    withContext(Dispatchers.Main) { configUpdateCallback() }
                }
            }
        }

        showMiuixSheet(activity, dismissOnOutside = false) { _ ->
            // ActionConfig is the shared mutable persistence model, not a Compose state holder.
            var revision by remember { mutableIntStateOf(0) }
            @Suppress("UNUSED_VARIABLE") val observedRevision = revision
            fun changed() { revision++ }

            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(if (isSubConfig)
                        R.string.action_view_settings_sub_style_entry_title
                    else R.string.action_view_settings_main_style_entry_title),
                    fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium,
                )

                // SubActionView derives exactly these five fields from the main wheel.
                // Keep the stored custom values intact while following, so disabling it restores them.
                if (!isSubConfig || !config.subActionFollowConfig) {
                    ActionStyleSlider(R.string.action_config_item_diameter,
                        config.itemDiameter.px2dp.toFloat(), 0f..200f,
                        onChange = { config.itemDiameter = it.dp2px; changed() },
                        onStateChanged = onSliderStateChanged, onCommit = ::saveAndPreview)
                    ActionStyleSlider(R.string.action_config_init_radius,
                        config.initRadius.px2dp.toFloat(), 0f..200f,
                        onChange = { config.initRadius = it.dp2px; changed() },
                        onStateChanged = onSliderStateChanged, onCommit = ::saveAndPreview)
                    ActionStyleSlider(R.string.action_config_item_warp_offset,
                        config.itemWarpOffset.px2dp.toFloat(), 0f..50f,
                        onChange = { config.itemWarpOffset = it.dp2px; changed() },
                        onStateChanged = onSliderStateChanged, onCommit = ::saveAndPreview)
                    ActionStyleSlider(R.string.action_config_icon_padding,
                        config.iconPadding.px2dp.toFloat(), -10f..50f,
                        onChange = { config.iconPadding = it.dp2px; changed() },
                        onStateChanged = onSliderStateChanged, onCommit = ::saveAndPreview)
                    ActionStyleSlider(R.string.action_config_icon_corner_radius,
                        config.iconCornerRadius.px2dp.toFloat(), 0f..50f,
                        onChange = { config.iconCornerRadius = it.dp2px; changed() },
                        onStateChanged = onSliderStateChanged, onCommit = ::saveAndPreview)
                }

                ActionStyleSlider(R.string.action_config_icon_image_round_percent,
                    config.iconImageRoundPercent, 0f..0.5f, percent = true,
                    // A proportion is dimensionless, unlike the five size fields above.
                    onChange = { config.iconImageRoundPercent = it; changed() },
                    onStateChanged = onSliderStateChanged, onCommit = ::saveAndPreview)
                if (!isSubConfig) {
                    ActionStyleSlider(R.string.action_config_offset_x,
                        config.actionOffsetX.px2dp.toFloat(), -100f..400f,
                        onChange = { config.actionOffsetX = it.dp2px; changed() },
                        onStateChanged = onSliderStateChanged, onCommit = ::saveAndPreview)
                    ActionStyleSlider(R.string.action_config_offset_y,
                        config.actionOffsetY.px2dp.toFloat(), -100f..400f,
                        onChange = { config.actionOffsetY = it.dp2px; changed() },
                        onStateChanged = onSliderStateChanged, onCommit = ::saveAndPreview)
                }
                SwitchPreference(
                    title = stringResource(R.string.action_config_transparent_icon),
                    checked = config.transparentIconBackground,
                    onCheckedChange = {
                        config.transparentIconBackground = it
                        changed(); saveAndPreview()
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.action_config_rectangle_layout),
                    checked = config.rectangleLayoutEnabled,
                    onCheckedChange = {
                        config.rectangleLayoutEnabled = it
                        changed(); saveAndPreview()
                    },
                )
                if (config.rectangleLayoutEnabled) {
                    ActionStyleSlider(R.string.action_config_rectangle_columns,
                        config.rectangleColumns.toFloat(), 1f..10f, steps = 8,
                        onChange = { config.rectangleColumns = it.roundToInt().coerceIn(1, 10); changed() },
                        onStateChanged = onSliderStateChanged, onCommit = ::saveAndPreview)
                }
                if (isSubConfig) {
                    SwitchPreference(
                        title = stringResource(R.string.action_config_auto_size),
                        checked = config.subActionFollowConfig,
                        onCheckedChange = {
                            config.subActionFollowConfig = it
                            changed(); saveAndPreview()
                        },
                    )
                }
                TextButton(
                    text = stringResource(R.string.action_config_reset),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    onClick = {
                        config.apply {
                            itemDiameter = 55f.dp2px
                            initRadius = 130f.dp2px
                            itemWarpOffset = 10f.dp2px
                            iconPadding = 10f.dp2px
                            iconCornerRadius = 25f.dp2px
                            iconImageRoundPercent = 0f
                            actionOffsetX = 30f.dp2px
                            actionOffsetY = 30f.dp2px
                            transparentIconBackground = false
                            rectangleLayoutEnabled = false
                            rectangleColumns = 4
                            // Reset never changes subActionFollowConfig, including its visibility.
                        }
                        changed(); saveAndPreview()
                    },
                )
            }
        }
    }
}

/** Continuous XML ranges stay continuous; only the columns control has discrete steps. */
@Composable
private fun ActionStyleSlider(
    title: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    percent: Boolean = false,
    onChange: (Float) -> Unit,
    onStateChanged: ((String, Float, Boolean) -> Unit)?,
    onCommit: () -> Unit,
) {
    val safeValue = value.coerceIn(range.start, range.endInclusive)
    val currentValue by rememberUpdatedState(safeValue)
    val stateCallback by rememberUpdatedState(onStateChanged)
    val commit by rememberUpdatedState(onCommit)
    var tracking by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    var latestValue by remember { mutableStateOf(safeValue) }
    SideEffect { if (!tracking && !dirty) latestValue = safeValue }

    fun finish() {
        if (tracking || dirty) {
            tracking = false
            dirty = false
            stateCallback?.invoke("slider", latestValue, false)
            commit()
        }
    }
    val finishGesture by rememberUpdatedState(newValue = { finish() })
    // Closing the sheet or hiding a row must never strand preview in its touching state.
    DisposableEffect(Unit) { onDispose { finishGesture() } }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(title), modifier = Modifier.weight(1f),
                fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium)
            Text(if (percent) "${(safeValue * 100).toInt()}%" else "${safeValue.toInt()}",
                fontSize = 14.sp, lineHeight = 20.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        MiuixSlider(
            value = safeValue,
            valueRange = range,
            steps = steps,
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).pointerInput(Unit) {
                // Observe without consuming: Miuix retains drag, tap and accessibility behavior.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    tracking = true
                    stateCallback?.invoke("slider", currentValue, true)
                    try {
                        // Do not treat Miuix's consumed drag events as cancellation.
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Final)
                        } while (event.changes.any { it.pressed })
                    } finally {
                        finishGesture()
                    }
                }
            },
            onValueChange = { raw ->
                val next = (if (steps > 0) raw.roundToInt().toFloat() else raw)
                    .coerceIn(range.start, range.endInclusive)
                latestValue = next
                dirty = true
                onChange(next)
                stateCallback?.invoke("slider", next, true)
            },
            // Also commits keyboard/accessibility edits, which have no pointer gesture.
            onValueChangeFinished = { finishGesture() },
        )
    }
}
