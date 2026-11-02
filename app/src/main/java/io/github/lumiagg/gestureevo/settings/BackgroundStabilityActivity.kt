package io.github.lumiagg.gestureevo.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.ui.miuix.MiuixScreen
import io.github.lumiagg.gestureevo.utils.readKey
import io.github.lumiagg.gestureevo.utils.saveKey
import io.github.lumiagg.gestureevo.utils.settingsStore
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

class BackgroundStabilityActivity : AppCompatActivity() {

    companion object {
        const val HIDE_TASK = "key_hide_task"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiuixEvoTheme {
                StabilityScreen(
                    packageName = packageName,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onCopyAdb = { copyAdbCommand() },
                )
            }
        }
    }

    private fun copyAdbCommand() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(
            getString(R.string.background_adb_clipboard_label),
            getString(R.string.background_adb_command, packageName)
        )
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.background_adb_copied), Toast.LENGTH_SHORT).show()
    }

    /** 检查项状态：null = 无法自动检测。 */
    private enum class CheckState { GRANTED, PENDING, MANUAL }

    private fun openPermissionSettings(activity: AppCompatActivity, item: SetupCheckItem) {
        try {
            activity.startActivity(item.settingsIntent(activity))
        } catch (_: Exception) {
            try {
                activity.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", activity.packageName, null),
                    )
                )
            } catch (_: Exception) {
                Toast.makeText(activity, R.string.setup_check_open_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Composable
    private fun StabilityScreen(
        packageName: String,
        onBack: () -> Unit,
        onCopyAdb: () -> Unit,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var hideTask by remember { mutableStateOf(true) }
        val states = remember { mutableStateListOf<CheckState>() }
        var granted by remember { mutableIntStateOf(0) }
        var checkable by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            hideTask = context.settingsStore.readKey(HIDE_TASK, true)
        }

        // 用户可能刚在系统设置里改完权限，回到本页需重新检测。
        LaunchedEffect(Unit) {
            val items = SetupChecklist.items
            if (states.isEmpty()) {
                items.forEach { states.add(CheckState.MANUAL) }
            }
            var g = 0
            var c = 0
            items.forEachIndexed { index, item ->
                when (item.isGranted(context)) {
                    null -> states[index] = CheckState.MANUAL
                    true -> {
                        states[index] = CheckState.GRANTED
                        c++
                        g++
                    }

                    false -> {
                        states[index] = CheckState.PENDING
                        c++
                    }
                }
            }
            granted = g
            checkable = c
        }

        MiuixScreen(
            title = stringResource(R.string.background_stability_title),
            onBack = onBack,
        ) {
            item {
                MiuixCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.background_stability_permission_title),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (checkable == 0) {
                                stringResource(R.string.setup_check_progress_all_done)
                            } else if (checkable - granted == 0) {
                                stringResource(R.string.setup_check_progress_all_done)
                            } else {
                                stringResource(
                                    R.string.setup_check_progress_pending,
                                    granted,
                                    checkable - granted,
                                )
                            },
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(12.dp))
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { if (checkable == 0) 1f else granted.toFloat() / checkable },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item {
                MiuixCard {
                    SetupChecklist.items.forEachIndexed { index, item ->
                        ArrowPreference(
                            title = stringResource(item.title),
                            summary = stringResource(item.description),
                            endActions = {
                                val state = states.getOrNull(index) ?: CheckState.MANUAL
                                Text(
                                    text = stringResource(
                                        when (state) {
                                            CheckState.GRANTED -> R.string.setup_check_status_granted
                                            CheckState.PENDING -> R.string.setup_check_status_pending
                                            CheckState.MANUAL -> R.string.setup_check_status_manual
                                        }
                                    ),
                                    fontSize = 12.sp,
                                    color = when (state) {
                                        CheckState.GRANTED -> MiuixTheme.colorScheme.primary
                                        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                                    },
                                )
                            },
                            onClick = { openPermissionSettings(this@BackgroundStabilityActivity, item) },
                        )
                    }
                }
            }
            item {
                MiuixCard {
                    SwitchPreference(
                        title = stringResource(R.string.background_stability_task_hide),
                        summary = stringResource(R.string.background_stability_task_hide_desc),
                        checked = hideTask,
                        onCheckedChange = { checked ->
                            scope.launch {
                                context.settingsStore.saveKey(HIDE_TASK, checked)
                                hideTask = checked
                            }
                        },
                    )
                }
            }
            item {
                MiuixCard {
                    ArrowPreference(
                        title = stringResource(R.string.background_stability_adb_title),
                        summary = stringResource(
                            R.string.background_stability_adb_command,
                            packageName,
                        ),
                        onClick = onCopyAdb,
                    )
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = stringResource(R.string.background_stability_adb_description),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(
                                    R.string.background_stability_adb_copy
                                ),
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                }
            }
        }
    }
}
