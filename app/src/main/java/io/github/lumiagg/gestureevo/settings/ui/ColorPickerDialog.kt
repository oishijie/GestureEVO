package io.github.lumiagg.gestureevo.settings.ui

import android.graphics.Color
import android.view.ViewGroup
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.alpha
import androidx.core.graphics.toColorInt
import com.google.android.material.R as MaterialR
import com.google.android.material.color.MaterialColors
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.utils.DynamicColorUtils
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.util.Locale

/** Imperative entry point retained for existing settings callers; persistence belongs to the caller. */
fun showColorPickerDialog(
    activity: AppCompatActivity,
    @ColorInt initialColor: Int,
    enableAlpha: Boolean = true,
    onColorSelected: (color: Int) -> Unit,
    onColorPreview: ((color: Int) -> Unit)? = null
) {
    if (activity.isFinishing || activity.isDestroyed) return
    val parent = activity.window.decorView as ViewGroup
    val host = ComposeView(activity).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }
    val presetColors = buildPresetColors(DynamicColorUtils.dynamicThemedContext(activity), enableAlpha)
    // The host inherits the Activity's lifecycle/saved-state owners without replacing its content.
    // WindowDialog supplies its own window, so this host needs no screen space or touch target.
    parent.addView(host, ViewGroup.LayoutParams(0, 0))
    host.setContent {
        MiuixEvoTheme {
            ColorPickerDialogContent(
                initialColor = initialColor,
                enableAlpha = enableAlpha,
                presetColors = presetColors,
                onColorSelected = onColorSelected,
                onColorPreview = onColorPreview,
                onDismissFinished = {
                    // Dispose only after the Miuix exit animation, outside composition dispatch.
                    host.post {
                        host.disposeComposition()
                        (host.parent as? ViewGroup)?.removeView(host)
                    }
                },
            )
        }
    }
}

@Composable
private fun ColorPickerDialogContent(
    @ColorInt initialColor: Int,
    enableAlpha: Boolean,
    presetColors: List<Int>,
    onColorSelected: (Int) -> Unit,
    onColorPreview: ((Int) -> Unit)?,
    onDismissFinished: () -> Unit,
) {
    var show by remember { mutableStateOf(true) }
    var selectedColor by remember {
        mutableIntStateOf(if (enableAlpha) initialColor else initialColor or -0x1000000)
    }
    var hexInput by remember { mutableStateOf(colorToHex(selectedColor, enableAlpha)) }
    var inputFocused by remember { mutableStateOf(false) }
    var inputInvalid by remember { mutableStateOf(false) }
    val invalidMessage = stringResource(R.string.color_picker_hex_invalid)

    fun applyColor(@ColorInt color: Int, emitPreview: Boolean) {
        selectedColor = if (enableAlpha) color else color or -0x1000000
        if (!inputFocused) hexInput = colorToHex(selectedColor, enableAlpha)
        if (emitPreview && show) onColorPreview?.invoke(selectedColor)
    }

    fun commitInput() {
        if (!show) return
        val parsed = parsePickerHexColor(hexInput)
        inputInvalid = parsed == null
        if (parsed == null) {
            if (!inputFocused) hexInput = colorToHex(selectedColor, enableAlpha)
        } else {
            applyColor(parsed, emitPreview = true)
        }
    }

    WindowDialog(
        show = show,
        insideMargin = DpSize(0.dp, 0.dp),
        onDismissRequest = { show = false },
        onDismissFinished = onDismissFinished,
    ) {
        Column {
            Text(
                text = stringResource(R.string.color_picker_title),
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
                textAlign = TextAlign.Center,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val colorHex = colorToHex(selectedColor, enableAlpha)
                Text(
                    text = colorHex,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MiuixTheme.colorScheme.secondaryContainer)
                        .semantics { contentDescription = colorHex },
                ) {
                    Box(Modifier.fillMaxWidth().height(80.dp).background(ComposeColor(selectedColor)))
                }
                if (enableAlpha) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(R.string.color_picker_alpha), fontSize = 16.sp)
                        Text(
                            text = String.format(Locale.US, "%d%%", (selectedColor.alpha / 255f * 100f).toInt()),
                            fontSize = 14.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Slider(
                        value = selectedColor.alpha.toFloat(),
                        valueRange = 0f..255f,
                        steps = 254,
                        onValueChange = { value ->
                            applyColor(
                                Color.argb(value.toInt(), Color.red(selectedColor), Color.green(selectedColor), Color.blue(selectedColor)),
                                emitPreview = true,
                            )
                        },
                    )
                }
                TextField(
                    value = hexInput,
                    onValueChange = { hexInput = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .onFocusChanged { focus ->
                            val lostFocus = inputFocused && !focus.isFocused
                            inputFocused = focus.isFocused
                            if (lostFocus) commitInput()
                        }
                        .semantics { if (inputInvalid) error(invalidMessage) },
                    label = stringResource(R.string.color_picker_hex_input_hint),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Ascii,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { commitInput() }),
                    colors = TextFieldDefaults.textFieldColors(
                        borderColor = if (inputInvalid) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.primary,
                    ),
                )
                if (inputInvalid) {
                    Text(text = invalidMessage, color = MiuixTheme.colorScheme.error, fontSize = 12.sp)
                }
                Text(stringResource(R.string.color_picker_presets), fontSize = 16.sp)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presetColors.forEach { color ->
                        TextButton(
                            text = colorToHex(color, enableAlpha),
                            onClick = { applyColor(color, emitPreview = true) },
                            minHeight = 44.dp,
                            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            colors = ButtonDefaults.textButtonColors(
                                color = ComposeColor(color),
                                textColor = ComposeColor(ensureContrastTextColor(color)),
                            ),
                            textStyle = MiuixTheme.textStyles.button.copy(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.color_picker_cancel),
                    modifier = Modifier.weight(1f),
                    minHeight = 44.dp,
                    onClick = { show = false },
                )
                TextButton(
                    text = stringResource(R.string.color_picker_confirm),
                    modifier = Modifier.weight(1f),
                    minHeight = 44.dp,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        if (show) {
                            // Match the old dialog: invalid text confirms the last valid selection.
                            parsePickerHexColor(hexInput)?.let { applyColor(it, emitPreview = false) }
                            show = false
                            onColorSelected(selectedColor)
                        }
                    },
                )
            }
        }
    }
}

private fun buildPresetColors(context: android.content.Context, enableAlpha: Boolean): List<Int> {
    val base = listOf(
        DynamicColorUtils.primaryColor.takeIf { it != 0 } ?: "#6750A4".toColorInt(),
        DynamicColorUtils.secondaryContainerColor.takeIf { it != 0 }
            ?: MaterialColors.getColor(
                context,
                MaterialR.attr.colorSecondaryContainer,
                "#625B71".toColorInt()
            ),
        MaterialColors.getColor(context, MaterialR.attr.colorTertiary, "#7D5260".toColorInt()),
        "#F44336".toColorInt(),
        "#FF9800".toColorInt(),
        "#4CAF50".toColorInt(),
        "#03A9F4".toColorInt(),
        "#3F51B5".toColorInt(),
        "#9C27B0".toColorInt(),
        "#000000".toColorInt(),
        "#FFFFFF".toColorInt()
    )
    if (!enableAlpha) return base
    return base + listOf(
        Color.argb(180, 255, 255, 255),
        Color.argb(180, 0, 0, 0)
    )
}

private fun colorToHex(@ColorInt color: Int, withAlpha: Boolean): String {
    return if (withAlpha) {
        String.format(Locale.US, "#%08X", color)
    } else {
        String.format(Locale.US, "#%06X", 0xFFFFFF and color)
    }
}

@ColorInt
private fun ensureContrastTextColor(@ColorInt background: Int): Int {
    return if (ColorUtils.calculateLuminance(background) >= 0.5) Color.BLACK else Color.WHITE
}
