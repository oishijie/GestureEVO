package io.github.lumiagg.gestureevo.settings

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.databinding.ActivityAdvancedSettingsBinding
import io.github.lumiagg.gestureevo.db.APPModel
import io.github.lumiagg.gestureevo.db.getAPPDao
import io.github.lumiagg.gestureevo.db.updateDB
import io.github.lumiagg.gestureevo.search.ContactSearchHelper
import io.github.lumiagg.gestureevo.search.SettingsShortcutSearchHelper
import io.github.lumiagg.gestureevo.settings.ui.DefaultSelectAPPBottomSheet
import io.github.lumiagg.gestureevo.settings.ui.SelectAPPCallBack
import io.github.lumiagg.gestureevo.settings.ui.installPageHeader
import io.github.lumiagg.gestureevo.settings.ui.showEditTextBottomSheet
import io.github.lumiagg.gestureevo.ui.demo.HomeDemoComposeActivity
import io.github.lumiagg.gestureevo.ui.demo.HomeDemoViewActivity
import io.github.lumiagg.gestureevo.utils.ClipboardUtils
import io.github.lumiagg.gestureevo.utils.ExternalActionPermissionStore
import io.github.lumiagg.gestureevo.utils.app.syncAppCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 高级设置：收纳低频但需保留的进阶能力，三个分组
 * ① 外部功能调用 ② 授权记录（只留一行带数量，明细进 [ExternalActionRecordsActivity]）
 * ③ 维护与日志（应用别名 / 索引重建 / 运行日志，原属数据页）。
 */
class AdvancedSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdvancedSettingsBinding

    /** 外部调用编辑器返回后直接复制生成的 Link。 */
    private val editorLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult

        val link = result.data?.getStringExtra(GestureAddActionActivity.EXTRA_EXTERNAL_ACTION_LINK)
            ?.takeIf { it.isNotBlank() }
            ?: return@registerForActivityResult

        ClipboardUtils.copyText(this, link)
        Toast.makeText(
            this,
            getString(R.string.external_action_link_copied),
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAdvancedSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar()
        setupButtons()
        SettingsSearchNavigator.focusAnchorIfNeeded(binding.root, intent)
    }

    override fun onResume() {
        super.onResume()
        refreshRecordsCount()
    }

    private fun setupToolbar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        installPageHeader(
            this,
            binding.toolbar,
            binding.toolbarLayout,
            R.string.advanced_settings_title
        )
    }

    private fun setupButtons() {
        binding.newRecordButton.setOnClickListener { launchEditor() }

        binding.openExternalWheelButton.setOnClickListener {
            startActivity(
                Intent(this, ActionViewSettingsActivity::class.java).apply {
                    putExtra(ActionViewSettingsActivity.EXTRA_SCROLL_TO_EXTERNAL_CALL, true)
                }
            )
        }

        binding.recordsEntryButton.setOnClickListener {
            startActivity(Intent(this, ExternalActionRecordsActivity::class.java))
        }

        binding.appAliasButton.setOnClickListener { showAppAliasSelector() }

        binding.rebuildDBButton.setOnClickListener { rebuildDB() }

        binding.logsEntryButton.setOnClickListener {
            startActivity(Intent(this, LogManagerActivity::class.java))
        }

        binding.demoViewButton.setOnClickListener {
            startActivity(Intent(this, HomeDemoViewActivity::class.java))
        }

        binding.demoComposeButton.setOnClickListener {
            startActivity(Intent(this, HomeDemoComposeActivity::class.java))
        }
    }

    private fun launchEditor() {
        val intent = Intent(this, GestureAddActionActivity::class.java).apply {
            putExtra(GestureAddActionActivity.EXTRA_EXTERNAL_ACTION_MODE, true)
        }
        editorLauncher.launch(intent)
    }

    /** 授权记录条数直接显示在行尾，明细进子页看。 */
    private fun refreshRecordsCount() {
        lifecycleScope.launch {
            val count = ExternalActionPermissionStore.getAllPermissions().size
            binding.recordsCountValue.text = getString(R.string.advanced_records_count, count)
        }
    }

    // ------------------------------------------------------------ 维护与日志

    private fun rebuildDB() = lifecycleScope.launch(Dispatchers.IO) {
        withContext(Dispatchers.Main) {
            binding.rebuildDBProgress.visibility = View.VISIBLE
        }

        val dao = getAPPDao()
        val appAliases = AppAliasEntry.collect(this@AdvancedSettingsActivity, dao.getAll())
        val appAliasMap = appAliases.associate { it.pkgName to it.alias }
        // Retrieve package names for gestureBlack and recentBlack
        val gestureBlackPackages =
            dao.getAll(state = APPModel.FLAG_GESTURE_DISABLE).map { it.pkgName }
        val recentBlackPackages =
            dao.getAll(state = APPModel.FLAG_RECENT_APPS_DISABLE).map { it.pkgName }
        val notificationIconBlackPackages =
            dao.getAll(state = APPModel.FLAG_NOTIFICATION_ICON_DISABLE).map { it.pkgName }
        val notificationListenerWhitePackages =
            dao.getAll(state = APPModel.FLAG_NOTIFICATION_APPS_ENABLE).map { it.pkgName }

        dao.deleteAll()
        syncAppCatalog(applicationContext)

        dao.getAll().forEach { appModel ->
            appAliasMap[appModel.pkgName]?.let { alias ->
                appModel.name = alias
            }
            // Set status if package name is present in gestureBlackPackages or recentBlackPackages
            when {
                gestureBlackPackages.contains(appModel.pkgName) -> {
                    appModel.setState(APPModel.FLAG_GESTURE_DISABLE, true)
                }

                recentBlackPackages.contains(appModel.pkgName) -> {
                    appModel.setState(APPModel.FLAG_RECENT_APPS_DISABLE, true)
                }

                notificationIconBlackPackages.contains(appModel.pkgName) -> {
                    appModel.setState(APPModel.FLAG_NOTIFICATION_ICON_DISABLE, true)
                }

                notificationListenerWhitePackages.contains(appModel.pkgName) -> {
                    appModel.setState(APPModel.FLAG_NOTIFICATION_APPS_ENABLE, true)
                }
            }
            // Update database
            updateDB(appModel)
        }

        ContactSearchHelper.syncContactsToDatabase(applicationContext, force = true)
        SettingsShortcutSearchHelper.syncShortcutsToDatabase(applicationContext, force = true)

        withContext(Dispatchers.Main) {
            binding.rebuildDBProgress.visibility = View.INVISIBLE
            Toast.makeText(
                applicationContext,
                getString(R.string.data_backup_rebuild_done),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showAppAliasSelector() {
        DefaultSelectAPPBottomSheet(this, lifecycleScope, object : SelectAPPCallBack {
            override fun selectCompleted(model: APPModel) {
                showAppAliasEditor(model)
            }
        }).show()
    }

    private fun showAppAliasEditor(model: APPModel) {
        showEditTextBottomSheet(
            activity = this,
            coroutineScope = lifecycleScope,
            title = getString(R.string.data_app_alias_edit_title),
            hint = getString(R.string.data_app_alias_edit_hint),
            defaultText = model.name,
            onConfirm = { inputText ->
                val alias = inputText.trim()
                if (alias.isEmpty()) {
                    return@showEditTextBottomSheet
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    model.name = alias
                    updateDB(model)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AdvancedSettingsActivity,
                            getString(R.string.data_app_alias_saved, alias),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }
}
