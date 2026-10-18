package io.github.lumiagg.gestureevo.settings

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.databinding.ActivityExternalActionRecordsBinding
import io.github.lumiagg.gestureevo.db.ExternalActionPermissionModel
import io.github.lumiagg.gestureevo.settings.ui.installPageHeader
import io.github.lumiagg.gestureevo.utils.ClipboardUtils
import io.github.lumiagg.gestureevo.utils.ExternalActionPermissionStore
import io.github.lumiagg.gestureevo.utils.GestureActionLink
import kotlinx.coroutines.launch

/**
 * 授权记录子页：列出已设为免确认的外部调用，可复制 Link 或删除记录以恢复确认。
 *
 * 原先是外部功能调用页里的第二张卡，重构后从高级设置的行进入。
 */
class ExternalActionRecordsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityExternalActionRecordsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityExternalActionRecordsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        installPageHeader(
            this,
            binding.toolbar,
            binding.toolbarLayout,
            R.string.external_action_history_title
        )
    }

    override fun onResume() {
        super.onResume()
        refreshRecords()
    }

    private fun refreshRecords() {
        lifecycleScope.launch {
            val records = ExternalActionPermissionStore.getAllPermissions()
            binding.recordsContainer.removeAllViews()
            binding.emptyHistoryText.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE

            records.forEach { record ->
                val itemView = layoutInflater.inflate(
                    R.layout.item_external_permission_record,
                    binding.recordsContainer,
                    false
                )

                itemView.findViewById<TextView>(R.id.recordTitle).isGone = true
                itemView.findViewById<TextView>(R.id.recordId).text = buildRecordSummary(record)
                itemView.findViewById<TextView>(R.id.recordLink).text = record.link

                itemView.findViewById<MaterialButton>(R.id.copyRecordButton).apply {
                    setOnClickListener { copyLink(record.link) }
                }
                itemView.findViewById<View>(R.id.recordLink).setOnClickListener {
                    copyLink(record.link)
                }
                itemView.findViewById<View>(R.id.deleteRecordButton).setOnClickListener {
                    confirmDeleteRecord(record)
                }
                binding.recordsContainer.addView(itemView)
            }
        }
    }

    private fun copyLink(link: String) {
        ClipboardUtils.copyText(this, link)
        Toast.makeText(this, getString(R.string.external_action_link_copied), Toast.LENGTH_SHORT)
            .show()
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

    private fun confirmDeleteRecord(record: ExternalActionPermissionModel) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.external_action_delete_confirm_title)
            .setMessage(R.string.external_action_delete_confirm_message)
            .setPositiveButton(R.string.external_action_delete_button) { _, _ ->
                lifecycleScope.launch {
                    ExternalActionPermissionStore.deletePermission(record.id)
                    refreshRecords()
                    Toast.makeText(
                        this@ExternalActionRecordsActivity,
                        getString(R.string.external_action_deleted),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.data_backup_cancel, null)
            .show()
    }
}
