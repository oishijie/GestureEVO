package io.github.lumiagg.gestureevo.settings

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.db.ExternalActionPermissionModel
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.ui.miuix.MiuixScreen
import io.github.lumiagg.gestureevo.utils.ClipboardUtils
import io.github.lumiagg.gestureevo.utils.ExternalActionPermissionStore
import io.github.lumiagg.gestureevo.utils.GestureActionLink
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 授权记录子页：列出已设为免确认的外部调用，可复制 Link 或删除记录以恢复确认。
 *
 * 原先是外部功能调用页里的第二张卡，重构后从高级设置的行进入。
 */
class ExternalActionRecordsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiuixEvoTheme {
                RecordsScreen(onBack = { onBackPressedDispatcher.onBackPressed() })
            }
        }
    }

    @Composable
    private fun RecordsScreen(onBack: () -> Unit) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var records by remember { mutableStateOf<List<ExternalActionPermissionModel>>(emptyList()) }
        var pendingDelete by remember { mutableStateOf<ExternalActionPermissionModel?>(null) }

        fun copyLink(link: String) {
            ClipboardUtils.copyText(context, link)
            Toast.makeText(context, R.string.external_action_link_copied, Toast.LENGTH_SHORT).show()
        }

        LaunchedEffect(Unit) {
            records = ExternalActionPermissionStore.getAllPermissions()
        }

        MiuixScreen(
            title = stringResource(R.string.external_action_history_title),
            onBack = onBack,
        ) {
            if (records.isEmpty()) {
                item {
                    MiuixCard {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.external_action_history_empty),
                                fontSize = 14.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            } else {
                item {
                    MiuixCard {
                        records.forEach { record ->
                            RecordRow(
                                summary = buildRecordSummary(record),
                                link = record.link,
                                onCopy = { copyLink(record.link) },
                                onDelete = { pendingDelete = record },
                            )
                        }
                    }
                }
            }
        }

        pendingDelete?.let { record ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(stringResource(R.string.external_action_delete_confirm_title)) },
                text = { Text(stringResource(R.string.external_action_delete_confirm_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            ExternalActionPermissionStore.deletePermission(record.id)
                            records = ExternalActionPermissionStore.getAllPermissions()
                            pendingDelete = null
                            Toast.makeText(
                                context,
                                context.getString(R.string.external_action_deleted),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }) {
                        Text(stringResource(R.string.external_action_delete_button))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text(stringResource(R.string.data_backup_cancel))
                    }
                },
            )
        }
    }

    @Composable
    private fun RecordRow(
        summary: String,
        link: String,
        onCopy: () -> Unit,
        onDelete: () -> Unit,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = summary, fontSize = 16.sp)
                Text(
                    text = link,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.size(8.dp))
            IconButton(onClick = onCopy) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_baseline_content_copy_24),
                    contentDescription = null,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = ImageVector.vectorResource(R.drawable.ic_delete),
                    contentDescription = null,
                )
            }
        }
    }

    private fun buildRecordSummary(record: ExternalActionPermissionModel): String {
        val actionName = GestureActionLink()
            .apply { parseLink(record.link) }
            .actionType
            .showName(this)
        val scopeLabel = when (record.scopeType) {
            ExternalActionPermissionModel.ScopeType.ACTION.name -> {
                getString(R.string.external_action_permission_scope_action)
            }

            else -> {
                getString(R.string.external_action_permission_scope_link)
            }
        }
        return getString(
            R.string.external_action_permission_record_summary,
            scopeLabel,
            actionName
        )
    }
}
