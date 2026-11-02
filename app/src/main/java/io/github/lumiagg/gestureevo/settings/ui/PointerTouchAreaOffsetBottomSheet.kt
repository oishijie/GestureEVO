package io.github.lumiagg.gestureevo.settings.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lumiagg.gestureevo.R
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

class PointerTouchAreaOffsetBottomSheet(
    private val activity: Activity,
    private val initialOffsetDp: Int,
    private val onValueChanged: (Int) -> Unit
) {
    fun show() {
        showMiuixSheet(activity) { dismiss ->
            var value by remember { mutableIntStateOf(initialOffsetDp.coerceIn(-100, 100)) }
            Column(
                modifier = Modifier.fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.inspire_settings_pointer_touch_area_offset),
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.inspire_settings_pointer_touch_area_offset_dp, value),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Slider(
                    modifier = Modifier.fillMaxWidth(),
                    value = value.toFloat(),
                    valueRange = -100f..100f,
                    steps = 199,
                    onValueChange = { value = it.roundToInt().coerceIn(-100, 100) },
                    // Preserve immediate persistence when the user finishes adjusting the offset.
                    onValueChangeFinished = { onValueChanged(value) },
                )
                TextButton(
                    text = stringResource(R.string.screen_search_close),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    onClick = dismiss,
                )
            }
        }
    }
}
