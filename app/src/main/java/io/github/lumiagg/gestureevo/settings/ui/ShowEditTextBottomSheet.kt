package io.github.lumiagg.gestureevo.settings.ui

import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.LifecycleCoroutineScope
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import kotlinx.coroutines.flow.first
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Suppress("UNUSED_PARAMETER")
fun showEditTextBottomSheet(
    activity: AppCompatActivity,
    coroutineScope: LifecycleCoroutineScope,
    title: String,
    hint: String,
    defaultText: String? = null,
    isMultiLine: Boolean = false,
    disableDragToDismiss: Boolean = false,
    onConfirm: (String) -> Unit,
    showDeleteButton: Boolean = false,
    deleteConfirmationMessage: String? = null,
    onDelete: (() -> Unit)? = null
) {
    // The shared host has no drag-to-dismiss gesture. Preserve the outside-touch policy;
    // Back and the explicit cancel action remain available, as in the original dialog.
    showMiuixSheet(activity, dismissOnOutside = !disableDragToDismiss) { dismiss ->
        var input by rememberSaveable(stateSaver = TextFieldValue.Saver) {
            val text = defaultText.orEmpty()
            mutableStateOf(TextFieldValue(text, selection = TextRange(text.length)))
        }
        var confirmingDelete by rememberSaveable { mutableStateOf(false) }
        var submitted by remember { mutableStateOf(false) }
        val focusRequester = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        val windowInfo = LocalWindowInfo.current

        LaunchedEffect(Unit) {
            // Compose can be created before the host window gains focus. Wait for both
            // window focus and placement before requesting the editor/IME connection.
            snapshotFlow { windowInfo.isWindowFocused }.first { it }
            withFrameNanos { }
            focusRequester.requestFocus()
            keyboard?.show()
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                modifier = Modifier.padding(top = 24.dp),
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface
            )
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                    .heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(
                    value = input,
                    onValueChange = { input = it },
                    label = hint,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .focusRequester(focusRequester),
                    singleLine = !isMultiLine,
                    minLines = if (isMultiLine) 4 else 1,
                    maxLines = if (isMultiLine) 10 else 1,
                    textStyle = MiuixTheme.textStyles.main.copy(
                        fontSize = 15.sp,
                        lineHeight = 21.sp,
                        color = MiuixTheme.colorScheme.onSurface
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = if (isMultiLine) KeyboardCapitalization.Sentences
                        else KeyboardCapitalization.None
                    )
                )
                if (showDeleteButton && onDelete != null) {
                    TextButton(
                        text = stringResource(R.string.edit_bottomsheet_delete),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        enabled = !submitted,
                        colors = ButtonDefaults.textButtonColors(
                            textColor = MiuixTheme.colorScheme.error
                        ),
                        onClick = {
                            keyboard?.hide()
                            confirmingDelete = true
                        }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                TextButton(
                    text = stringResource(R.string.edit_bottomsheet_cancel),
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    onClick = dismiss
                )
                TextButton(
                    text = stringResource(R.string.edit_bottomsheet_confirm),
                    modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                    enabled = input.text.isNotEmpty() && !submitted,
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        // Intentionally do not trim: callers own their validation and
                        // whitespace can be meaningful in links, shell commands, or text.
                        if (input.text.isNotEmpty() && !submitted) {
                            submitted = true
                            onConfirm(input.text)
                            dismiss()
                        }
                    }
                )
            }
        }

        if (confirmingDelete && showDeleteButton && onDelete != null) {
            Dialog(onDismissRequest = { confirmingDelete = false }) {
                MiuixCard {
                    Text(
                        text = stringResource(R.string.edit_delete_confirm_title),
                        modifier = Modifier.padding(
                            start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp
                        ),
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                            .heightIn(max = 500.dp).verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = deleteConfirmationMessage
                                ?: stringResource(R.string.edit_delete_confirm_message),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(
                            start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        TextButton(
                            text = stringResource(R.string.edit_delete_confirm_cancel),
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            onClick = { confirmingDelete = false }
                        )
                        TextButton(
                            text = stringResource(R.string.edit_delete_confirm_action),
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                            enabled = !submitted,
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                            onClick = {
                                if (!submitted) {
                                    submitted = true
                                    onDelete()
                                    confirmingDelete = false
                                    dismiss()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
