package io.github.lumiagg.gestureevo.inspire.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.settings.ui.showMiuixSheet
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Compose/Miuix text editor shared by Inspire content and image editor.
 * The public imperative entry point is retained so both legacy callers preserve their callbacks.
 */
fun showInspireEditBottomSheet(
    context: android.content.Context,
    title: String,
    hint: String,
    scopeText: String,
    defaultText: String,
    disableDragToDismiss: Boolean = true,
    onConfirm: (String) -> Unit,
) {
    var ownerContext = context
    val visited = java.util.Collections.newSetFromMap(
        java.util.IdentityHashMap<android.content.Context, Boolean>(),
    )
    while (ownerContext !is android.app.Activity && ownerContext is android.content.ContextWrapper) {
        if (!visited.add(ownerContext)) return
        ownerContext = ownerContext.baseContext
    }
    val activity = ownerContext as? android.app.Activity ?: return
    showMiuixSheet(activity, dismissOnOutside = !disableDragToDismiss) { dismiss ->
        InspireEditContent(
            title = title,
            hint = hint,
            scopeText = scopeText,
            defaultText = defaultText,
            onCancel = dismiss,
            onConfirm = { value ->
                onConfirm(value)
                dismiss()
            },
        )
    }
}

@Composable
private fun InspireEditContent(
    title: String,
    hint: String,
    scopeText: String,
    defaultText: String,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by androidx.compose.runtime.saveable.rememberSaveable(
        stateSaver = androidx.compose.ui.text.input.TextFieldValue.Saver,
    ) {
        mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(
            defaultText, selection = androidx.compose.ui.text.TextRange(defaultText.length),
        ))
    }
    var submitted by remember { mutableStateOf(false) }
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { windowInfo.isWindowFocused }.first { it }
        androidx.compose.runtime.withFrameNanos { }
        focusRequester.requestFocus()
        keyboard?.show()
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp),
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = scopeText,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        TextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp, max = 300.dp)
                .verticalScroll(rememberScrollState()).focusRequester(focusRequester)
                .padding(horizontal = 24.dp),
            label = hint,
            minLines = 5,
            maxLines = 10,
            singleLine = false,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = ImeAction.Default,
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            TextButton(
                text = stringResource(R.string.edit_bottomsheet_cancel),
                modifier = Modifier.weight(1f),
                minHeight = 44.dp,
                onClick = {
                    if (!submitted) {
                        submitted = true
                        keyboard?.hide()
                        onCancel()
                    }
                },
            )
            TextButton(
                text = stringResource(R.string.edit_bottomsheet_confirm),
                modifier = Modifier.weight(1f),
                minHeight = 44.dp,
                colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = {
                    if (!submitted) {
                        submitted = true
                        keyboard?.hide()
                        onConfirm(value.text)
                    }
                },
            )
        }
    }
}
