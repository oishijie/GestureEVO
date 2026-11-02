package io.github.lumiagg.gestureevo.utils

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

class NotificationPermissionPromptActivity : AppCompatActivity() {
    private var waitingForResult = false
    private var show by mutableStateOf(true)

    private companion object {
        const val STATE_WAITING_FOR_RESULT = "waitingForResult"
        const val STATE_PROMPT_VISIBLE = "promptVisible"
    }

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (NotificationPermissionUtils.canPostNotifications(this)) {
                finish()
            } else {
                openNotificationSettings()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        waitingForResult = savedInstanceState?.getBoolean(STATE_WAITING_FOR_RESULT) ?: false
        show = savedInstanceState?.getBoolean(STATE_PROMPT_VISIBLE, true) ?: true
        // The registered launcher receives the pending result after recreation.
        if (waitingForResult) return
        if (!show || NotificationPermissionUtils.canPostNotifications(this)) {
            finish()
            return
        }
        showPrompt()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(STATE_WAITING_FOR_RESULT, waitingForResult)
        outState.putBoolean(STATE_PROMPT_VISIBLE, show)
        super.onSaveInstanceState(outState)
    }

    private fun showPrompt() {
        setContent {
            MiuixEvoTheme {
                WindowDialog(
                    show = show,
                    insideMargin = DpSize(0.dp, 0.dp),
                    onDismissRequest = { show = false },
                    onDismissFinished = {
                        // A runtime permission request must outlive the dismissed prompt.
                        if (!waitingForResult) finish()
                    },
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.notification_permission_disabled_title),
                            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
                            textAlign = TextAlign.Center,
                            fontSize = 17.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = stringResource(R.string.notification_permission_disabled_message),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 24.dp, vertical = 12.dp),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            TextButton(
                                text = stringResource(R.string.edit_bottomsheet_cancel),
                                modifier = Modifier.weight(1f),
                                minHeight = 44.dp,
                                onClick = { show = false },
                            )
                            TextButton(
                                text = stringResource(R.string.notification_permission_disabled_action),
                                modifier = Modifier.weight(1f),
                                minHeight = 44.dp,
                                colors = ButtonDefaults.textButtonColorsPrimary(),
                                onClick = {
                                    if (show) {
                                        // Keep the old button-before-dismiss ordering so the
                                        // waiting flag is set before onDismissFinished runs.
                                        requestOrOpenNotificationSettings()
                                        show = false
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestOrOpenNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            waitingForResult = true
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
            finish()
        }
    }

    private fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            })
            return
        }
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                }
            )
        }.onFailure {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
            )
        }
    }
}
