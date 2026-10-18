package io.github.lumiagg.gestureevo.settings

import io.github.lumiagg.gestureevo.settings.ui.installPageHeader

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.drake.brv.utils.bindingAdapter
import com.drake.brv.utils.linear
import com.drake.brv.utils.models
import com.drake.brv.utils.setup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.databinding.ActivitySearchEngineSettingsBinding
import io.github.lumiagg.gestureevo.databinding.SearchEngineManageItemBinding
import io.github.lumiagg.gestureevo.search.ContactSearchHelper
import io.github.lumiagg.gestureevo.search.QuickSearchActivity
import io.github.lumiagg.gestureevo.search.config.EngineType
import io.github.lumiagg.gestureevo.search.config.SearchEngine
import io.github.lumiagg.gestureevo.search.config.SearchEngineDataManager
import io.github.lumiagg.gestureevo.search.setEngineIcon
import io.github.lumiagg.gestureevo.utils.readKey
import io.github.lumiagg.gestureevo.utils.saveKey
import io.github.lumiagg.gestureevo.utils.settingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 原版「搜索」设置页：引擎列表 + 显示 / 功能 / 启动 / 导入导出 / 隐藏结果分区卡。
 *
 * 一级菜单的「设置」「导入导出」入口通过 [SettingsSearchNavigator.EXTRA_TARGET_VIEW_IDS]
 * 深链进入本页并自动展开对应分区卡；直接进入时仅引擎列表默认展开。
 */
class SearchEngineSettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySearchEngineSettingsBinding
    private val searchEngines = mutableListOf<SearchEngine>()
    private var defaultEngineTag: String? = null
    private var showImageBadge = true

    private var rowCount = 1
    private var columnCount = 5

    /**
     * 引擎列表是否需要呈现。非深链进入，或深链目标含引擎列表卡时为 true；
     * 深链只指向其它分区时为 false，此时不加载引擎、不显示添加按钮。
     */
    private var showEngineList = true

    /** 通讯录权限结果：授予则落库并同步，拒绝则回退开关。 */
    private val contactPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            lifecycleScope.launch {
                settingsStore.saveKey(KEY_SEARCH_CONTACT, true)
                ContactSearchHelper.syncContactsToDatabase(applicationContext, force = true)
            }
        } else {
            binding.contactSwitch.isChecked = false
            Toast.makeText(
                this,
                R.string.search_engine_settings_search_contacts,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    companion object {
        const val KEY_ENGINE_SHOW_LABEL = "engine_show_label"
        const val KEY_ROW_COUNT = "engine_row_count"
        const val KEY_COLUMN_COUNT = "engine_column_count"
        const val KEY_BOTTOM_INPUT_LAYOUT = "search_bottom_input_layout"
        const val KEY_RESULT_DOUBLE_COLUMN = "search_result_double_column"
        const val KEY_KEYBOARD_FOLLOW_ANIMATION = "search_keyboard_follow_animation"
        const val KEY_SHOW_IMAGE_BADGE = "search_show_image_badge"
        const val KEY_SHOW_CLIPBOARD_CONTENT = "search_show_clipboard_content"
        const val KEY_SEARCH_HISTORY = "search_history"
        const val KEY_SEARCH_CONTACT = "search_contact"
        const val KEY_SEARCH_SETTINGS_SHORTCUTS = "search_settings_shortcuts"
        const val KEY_ENTER_FIRST_RESULT = "search_enter_first_result"
        const val KEY_KEEP_LAST_SEARCH_RESULT = "search_keep_last_search_result"
        const val KEY_DEFAULT_SEARCH_PAGE = "default_search_page"
        const val MIN_ROW_COUNT = 1
        const val MAX_ROW_COUNT = 5
        const val MIN_COLUMN_COUNT = 2
        const val MAX_COLUMN_COUNT = 8
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchEngineSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()

        // 深链收敛必须最先执行：此刻视图树已可查，不需等任何读库。若排在协程里，
        // 首帧会先按布局默认状态渲染出全部卡片与引擎列表转圈，等IO 跑完才收敛，
        // 用户会看到「全部展示 + 转圈 → 突然消失成目标分区」。
        val deepLinkTargets =
            SettingsSearchNavigator.focusAnchorIfNeeded(binding.root, intent)
        showEngineList = deepLinkTargets.isEmpty() || R.id.engineListCard in deepLinkTargets
        if (!showEngineList) {
            // 引擎列表不呈现时，加载与添加按钮都无意义。
            binding.fabAdd.isVisible = false
            binding.fabPreviewSort.isVisible = false
        }

        setupButtons()
        setupRecyclerView()
        setupTransferSection()
        if (showEngineList) {
            loadSearchEngines()
        }

        // 开关初值需要读库，放协程里；此时视图已收敛完成，不再影响首帧。
        lifecycleScope.launch {
            setupDisplaySection()
            setupFunctionSection()
            setupStartupSection()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh list when returning from add/edit page
        if (showEngineList) {
            loadSearchEngines()
        }
    }

    private fun setupToolbar() {
        installPageHeader(this, binding.toolbar, binding.toolbarLayout, R.string.search_engine_settings_title)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupButtons() {
        binding.fabAdd.setOnClickListener {
            val intent = Intent(this, SearchEngineAddActivity::class.java)
            startActivity(intent)
        }
        binding.fabPreviewSort.setOnClickListener {
            QuickSearchActivity.start(this, previewSortMode = true)
        }
    }

    // ---------------------------------------------------------------
    // 显示设置：行/列数步进 + 5 个开关
    // ---------------------------------------------------------------

    private suspend fun setupDisplaySection() {
        rowCount = settingsStore.readKey(KEY_ROW_COUNT, 1)
        columnCount = settingsStore.readKey(KEY_COLUMN_COUNT, 5)
        renderCounts()

        binding.rowCountMinus.setOnClickListener { stepCount(isRow = true, delta = -1) }
        binding.rowCountPlus.setOnClickListener { stepCount(isRow = true, delta = 1) }
        binding.columnCountMinus.setOnClickListener { stepCount(isRow = false, delta = -1) }
        binding.columnCountPlus.setOnClickListener { stepCount(isRow = false, delta = 1) }

        bindSwitch(binding.showLabelSwitch, KEY_ENGINE_SHOW_LABEL, true)
        bindSwitch(binding.bottomLayoutSwitch, KEY_BOTTOM_INPUT_LAYOUT, false)
        bindSwitch(binding.doubleColumnSwitch, KEY_RESULT_DOUBLE_COLUMN, false)
        bindSwitch(binding.keyboardAnimationSwitch, KEY_KEYBOARD_FOLLOW_ANIMATION, false)
        bindSwitch(binding.imageBadgeSwitch, KEY_SHOW_IMAGE_BADGE, true)
    }

    private fun stepCount(isRow: Boolean, delta: Int) {
        if (isRow) {
            val next = (rowCount + delta).coerceIn(MIN_ROW_COUNT, MAX_ROW_COUNT)
            if (next == rowCount) return
            rowCount = next
            lifecycleScope.launch { settingsStore.saveKey(KEY_ROW_COUNT, next) }
        } else {
            val next = (columnCount + delta).coerceIn(MIN_COLUMN_COUNT, MAX_COLUMN_COUNT)
            if (next == columnCount) return
            columnCount = next
            lifecycleScope.launch { settingsStore.saveKey(KEY_COLUMN_COUNT, next) }
        }
        renderCounts()
    }

    private fun renderCounts() {
        binding.rowCountValue.text = rowCount.toString()
        binding.columnCountValue.text = columnCount.toString()
        binding.rowCountMinus.isEnabled = rowCount > MIN_ROW_COUNT
        binding.rowCountPlus.isEnabled = rowCount < MAX_ROW_COUNT
        binding.columnCountMinus.isEnabled = columnCount > MIN_COLUMN_COUNT
        binding.columnCountPlus.isEnabled = columnCount < MAX_COLUMN_COUNT
    }

    // ---------------------------------------------------------------
    // 功能设置：6 个开关（通讯录开关带权限校验）
    // ---------------------------------------------------------------

    private suspend fun setupFunctionSection() {
        bindSwitch(binding.clipboardContentSwitch, KEY_SHOW_CLIPBOARD_CONTENT, true)
        bindSwitch(binding.historySwitch, KEY_SEARCH_HISTORY, true)
        bindSwitch(binding.settingsShortcutsSwitch, KEY_SEARCH_SETTINGS_SHORTCUTS, false)
        bindSwitch(binding.enterFirstSwitch, KEY_ENTER_FIRST_RESULT, false)
        bindSwitch(binding.keepLastSwitch, KEY_KEEP_LAST_SEARCH_RESULT, true)

        binding.contactSwitch.isChecked = settingsStore.readKey(KEY_SEARCH_CONTACT, false)
        binding.contactSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !ContactSearchHelper.hasContactPermission(this)) {
                contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            } else {
                lifecycleScope.launch {
                    settingsStore.saveKey(KEY_SEARCH_CONTACT, isChecked)
                    if (isChecked) {
                        ContactSearchHelper.syncContactsToDatabase(
                            applicationContext,
                            force = true,
                        )
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // 启动设置：默认搜索页开关（开启前确认）
    // ---------------------------------------------------------------

    private suspend fun setupStartupSection() {
        binding.defaultSearchPageSwitch.isChecked =
            settingsStore.readKey(KEY_DEFAULT_SEARCH_PAGE, false)
        binding.defaultSearchPageSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!isChecked) {
                lifecycleScope.launch { settingsStore.saveKey(KEY_DEFAULT_SEARCH_PAGE, false) }
                return@setOnCheckedChangeListener
            }
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.search_engine_settings_default_search_page_dialog_title)
                .setMessage(R.string.search_engine_settings_default_search_page_dialog_message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.launch {
                        settingsStore.saveKey(KEY_DEFAULT_SEARCH_PAGE, true)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .setOnCancelListener { binding.defaultSearchPageSwitch.isChecked = false }
                .show()
        }
    }

    // ---------------------------------------------------------------
    // 导入导出 + 隐藏结果
    // ---------------------------------------------------------------

    private fun setupTransferSection() {
        binding.importEnginesRow.setOnClickListener { showImportDialog() }
        binding.exportEnginesRow.setOnClickListener { exportSearchEngines() }
        binding.hiddenResultsRow.setOnClickListener {
            startActivity(Intent(this, HiddenSearchResultsActivity::class.java))
        }
    }

    private fun showImportDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.search_engine_import_title)
            .setMessage(R.string.search_engine_import_message)
            .setView(R.layout.search_engine_dialog_edit_text)
            .setPositiveButton(R.string.search_engine_import_action) { dialog, _ ->
                val editText = (dialog as AlertDialog)
                    .findViewById<EditText>(R.id.edit_text)
                val json = editText?.text?.toString() ?: ""
                importSearchEngines(json)
            }
            .setNegativeButton(R.string.search_engine_cancel, null)
            .show()
    }

    private fun importSearchEngines(json: String) {
        if (json.isEmpty()) {
            Toast.makeText(this, R.string.search_engine_import_empty, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val appContext = applicationContext
            try {
                val engines = SearchEngine.toModelList(json)
                if (engines.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            appContext,
                            R.string.search_engine_import_json_error,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }
                SearchEngineDataManager.importSearchEngines(
                    appContext,
                    engines,
                    replaceExisting = false,
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appContext,
                        R.string.search_engine_import_success,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        appContext,
                        appContext.getString(
                            R.string.search_engine_import_failed,
                            e.localizedMessage ?: "",
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun exportSearchEngines() {
        lifecycleScope.launch(Dispatchers.IO) {
            val appContext = applicationContext
            val json = SearchEngineDataManager.exportSearchEnginesJson(appContext)
            withContext(Dispatchers.Main) {
                val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
                val clip = ClipData.newPlainText(
                    appContext.getString(R.string.search_engine_clipboard_label),
                    json,
                )
                clipboard.setPrimaryClip(clip)
                Toast.makeText(
                    appContext,
                    R.string.search_engine_clipboard_copied,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** 统一绑定开关：初始读库，切换即写库。 */
    private suspend fun bindSwitch(
        switch: com.google.android.material.materialswitch.MaterialSwitch,
        key: String,
        default: Boolean,
    ) {
        switch.isChecked = settingsStore.readKey(key, default)
        switch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch { settingsStore.saveKey(key, isChecked) }
        }
    }

    // ---------------------------------------------------------------
    // 引擎列表管理
    // ---------------------------------------------------------------

    private fun setupRecyclerView() {
        binding.engineRecycler.linear().setup {
            addType<SearchEngine>(R.layout.search_engine_manage_item)

            onBind {
                val itemBinding = SearchEngineManageItemBinding.bind(itemView)
                val model = getModel<SearchEngine>()

                // Set engine name
                itemBinding.engineName.text = model.name

                // Set engine type tag
                itemBinding.engineType.text = when (model.engineType) {
                    EngineType.DIRECT_LINK -> getString(R.string.search_engine_type_direct_link)
                    EngineType.SHARE_TO_APP -> getString(R.string.search_engine_type_share)
                    EngineType.SHARE_IMAGE_TO_APP -> getString(R.string.search_engine_type_share_image)
                    EngineType.JUMP_TO_ACTIVITY -> getString(R.string.search_engine_type_jump_activity)
                    EngineType.EXTERN_JUMP_LINK -> getString(R.string.search_engine_type_extern_jump)
                }

                // Set default flag
                val isDefault = model.tag == defaultEngineTag
                itemBinding.defaultBadge.visibility = if (isDefault) View.VISIBLE else View.GONE
                // Set hidden flag
                itemBinding.hideBadge.visibility =
                    if (model.showInSearchEngineList) View.GONE else View.VISIBLE

                setEngineIcon(applicationContext, itemBinding.engineIcon, model, showImageBadge)
            }

            onClick(R.id.engine_container) {
                val engine = getModel<SearchEngine>()
                editSearchEngine(engine)
            }

            onLongClick(R.id.engine_container) {
                val engine = getModel<SearchEngine>()
                showEngineOptionsDialog(engine)
            }

            R.id.drag_handle.onFastClick {
                val engine = getModel<SearchEngine>()
                showEngineOptionsDialog(engine)
            }
        }
    }

    private fun loadSearchEngines() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val engines = SearchEngineDataManager.readSearchEngineList(applicationContext)
            defaultEngineTag = SearchEngineDataManager.getDefaultEngineTag(applicationContext)
            showImageBadge = settingsStore.readKey(KEY_SHOW_IMAGE_BADGE, true)

            withContext(Dispatchers.Main) {
                searchEngines.clear()
                searchEngines.addAll(engines)
                binding.engineRecycler.models = searchEngines

                binding.progressBar.visibility = View.GONE
                binding.emptyView.visibility = if (engines.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun editSearchEngine(engine: SearchEngine) {
        val intent = Intent(this, SearchEngineAddActivity::class.java).apply {
            putExtra("editMode", true)
            putExtra("engine", engine.toJson())
        }
        startActivity(intent)
    }

    private fun showEngineOptionsDialog(engine: SearchEngine) {
        val isDefault = engine.tag == defaultEngineTag
        val hideOption = if (engine.showInSearchEngineList) {
            getString(R.string.search_engine_option_hide)
        } else {
            getString(R.string.search_engine_option_show)
        }
        val optionEdit = getString(R.string.search_engine_option_edit)
        val optionDelete = getString(R.string.search_engine_option_delete)
        val optionSetDefault = getString(R.string.search_engine_option_set_default)
        val options = mutableListOf(
            optionEdit,
            hideOption,
            optionDelete,
        )

        if (!isDefault) {
            options.add(0, optionSetDefault)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(engine.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    optionSetDefault -> setAsDefault(engine)
                    optionEdit -> editSearchEngine(engine)
                    optionDelete -> showDeleteConfirmDialog(engine)
                    getString(R.string.search_engine_option_hide) ->
                        switchSearchEngineShowInList(engine, false)
                    getString(R.string.search_engine_option_show) ->
                        switchSearchEngineShowInList(engine, true)
                }
            }
            .show()
    }

    private fun setAsDefault(engine: SearchEngine) {
        lifecycleScope.launch(Dispatchers.IO) {
            SearchEngineDataManager.setDefaultEngineTag(applicationContext, engine.tag)
            defaultEngineTag = engine.tag
            withContext(Dispatchers.Main) {
                binding.engineRecycler.bindingAdapter.let { it.notifyItemRangeChanged(0, it.itemCount) }
            }
        }
    }

    private fun showDeleteConfirmDialog(engine: SearchEngine) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.search_engine_delete_title)
            .setMessage(getString(R.string.search_engine_delete_message, engine.name))
            .setPositiveButton(R.string.search_engine_option_delete) { _, _ ->
                deleteSearchEngine(engine)
            }
            .setNegativeButton(R.string.search_engine_cancel, null)
            .show()
    }

    private fun switchSearchEngineShowInList(engine: SearchEngine, isShow: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            engine.showInSearchEngineList = isShow
            // Save and reorder
            SearchEngineDataManager.saveSearchEngineList(applicationContext, searchEngines)
            searchEngines.clear()
            searchEngines.addAll(SearchEngineDataManager.readSearchEngineList(applicationContext))
            withContext(Dispatchers.Main) {
                binding.engineRecycler.models = searchEngines
            }
        }
    }

    private fun deleteSearchEngine(engine: SearchEngine) {
        lifecycleScope.launch(Dispatchers.IO) {
            SearchEngineDataManager.removeSearchEngine(applicationContext, engine.tag)

            withContext(Dispatchers.Main) {
                searchEngines.remove(engine)
                binding.engineRecycler.models = searchEngines
                binding.emptyView.visibility =
                    if (searchEngines.isEmpty()) View.VISIBLE else View.GONE

                Toast.makeText(
                    this@SearchEngineSettingsActivity,
                    R.string.search_engine_deleted,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
