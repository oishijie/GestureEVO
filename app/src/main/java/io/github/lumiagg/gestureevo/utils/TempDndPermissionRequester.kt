package io.github.lumiagg.gestureevo.utils

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.settings.ui.showMiuixSheet
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

class TempDndPermissionRequester(
    private val activity: ComponentActivity
) {
    private val requestNotificationPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                NotificationPermissionUtils.showDisabledPromptIfNeeded(activity)
            }
        }

    private val dndSettingsLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    fun ensurePermissionWithPrompt(onContinue: () -> Unit) {
        if (hasDndPolicyAccess() && NotificationPermissionUtils.canPostNotifications(activity)) {
            onContinue()
            return
        }

        if (hasDndPolicyAccess()) {
            NotificationPermissionUtils.showDisabledPromptIfNeeded(activity)
            onContinue()
            return
        }

        showPermissionPrompt(
            title = R.string.temp_dnd_permission_notice_title,
            message = R.string.temp_dnd_permission_notice_message,
            onAuthorize = {
                requestPermissions()
                onContinue()
            },
            onContinue = onContinue,
        )
    }

    /** Notification permission only, for features that just need to post a notification. */
    fun ensureNotificationPermissionWithPrompt(onContinue: () -> Unit) {
        if (NotificationPermissionUtils.canPostNotifications(activity)) {
            onContinue()
            return
        }
        showPermissionPrompt(
            title = R.string.pause_gesture_permission_notice_title,
            message = R.string.pause_gesture_permission_notice_message,
            onAuthorize = {
                requestNotificationPermission()
                onContinue()
            },
            onContinue = onContinue,
        )
    }

    private fun showPermissionPrompt(
        @StringRes title: Int,
        @StringRes message: Int,
        onAuthorize: () -> Unit,
        onContinue: () -> Unit,
    ) {
        showMiuixSheet(activity) { dismiss ->
            var visible by remember { mutableStateOf(true) }
            fun close() {
                if (visible) {
                    visible = false
                    dismiss()
                }
            }
            Column {
                Text(
                    text = stringResource(title),
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(message),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                TextButton(
                    text = stringResource(R.string.temp_dnd_permission_notice_later),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    minHeight = 44.dp,
                    onClick = {
                        if (visible) {
                            visible = false
                            onContinue()
                            dismiss()
                        }
                    },
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.edit_bottomsheet_cancel),
                        modifier = Modifier.weight(1f),
                        minHeight = 44.dp,
                        onClick = ::close,
                    )
                    TextButton(
                        text = stringResource(R.string.temp_dnd_permission_notice_authorize),
                        modifier = Modifier.weight(1f),
                        minHeight = 44.dp,
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
                            if (visible) {
                                visible = false
                                onAuthorize()
                                dismiss()
                            }
                        },
                    )
                }
            }
        }
    }

    private fun requestPermissions() {
        if (!NotificationPermissionUtils.canPostNotifications(activity)) {
            requestNotificationPermission()
        }
        if (!hasDndPolicyAccess()) {
            dndSettingsLauncher.launch(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            NotificationPermissionUtils.showDisabledPromptIfNeeded(activity)
        }
    }

    private fun hasDndPolicyAccess(): Boolean {
        return TempDndManager.hasUsableDndPolicyAccess(activity)
    }
}
