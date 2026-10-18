package io.github.lumiagg.gestureevo.ui.home

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.search.config.SearchEngine
import io.github.lumiagg.gestureevo.search.config.SearchEngineDataManager
import io.github.lumiagg.gestureevo.settings.SearchEngineSettingsActivity
import io.github.lumiagg.gestureevo.settings.SettingsSearchNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 一级页面：搜索。
 *
 * 以手势页为模板——文字分组标题 + 分组下条目行：
 * - 「搜索」分组：深链打开原版 SearchEngineSettingsActivity 的引擎列表分区卡；
 * - 「设置」分组：深链打开原版页显示 / 功能 / 启动三张分区卡；
 * - 「导入与导出」分组：直接执行导入（对话框）/ 导出（剪贴板）。
 *
 * 预览排序模式已并入搜索引擎设置页右下角 FAB（与新增引擎 FAB 并排）。
 */
class SearchHomeFragment : BaseHomeFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.home_search_prefs, rootKey)

        bindGuideCard("card_search_guide", R.string.home_guide_summary_search) {
            FeatureGuideActivity.intent(requireContext(), FeatureGuideActivity.GUIDE_SEARCH)
        }

        // 引擎个数徽章：导入/删除引擎后返回本页即可看到最新计数。
        refreshEngineBadge()
        registerStatusRefresh { refreshEngineBadge() }

        // 搜索（引擎列表）→ 原版搜索设置页，深链只呈现引擎列表分区卡。
        findPreference<Preference>("menu_search_engines")?.setOnPreferenceClickListener {
            startActivity(
                SettingsSearchNavigator.deepLinkIntent(
                    requireContext(),
                    SearchEngineSettingsActivity::class.java,
                    R.id.engineListCard,
                )
            )
            true
        }

        // 设置 → 原版搜索设置页，深链展开显示/功能/启动三张分区卡。
        findPreference<Preference>("menu_search_settings")?.setOnPreferenceClickListener {
            startActivity(
                SettingsSearchNavigator.deepLinkIntent(
                    requireContext(),
                    SearchEngineSettingsActivity::class.java,
                    R.id.displayCard,
                    R.id.functionCard,
                    R.id.startupCard,
                )
            )
            true
        }

        // 导入与导出：直接执行动作（与原版页内两行行为一致）。
        findPreference<Preference>("menu_search_import")?.setOnPreferenceClickListener {
            showImportDialog()
            true
        }
        findPreference<Preference>("menu_search_export")?.setOnPreferenceClickListener {
            exportSearchEngines()
            true
        }
    }

    // ---------------------------------------------------------------
    // 导入与导出（逻辑与原版 SearchEngineSettingsActivity 一致）
    // ---------------------------------------------------------------

    /** 刷新「搜索列表」卡的引擎个数徽章。 */
    private fun refreshEngineBadge() {
        lifecycleScope.launch {
            val count = withContext(Dispatchers.IO) {
                SearchEngineDataManager.readSearchEngineList(requireContext()).size
            }
            findPreference<HomeEntryPreference>("menu_search_engines")?.setBadge(
                getString(R.string.home_badge_engine_count, count)
            )
        }
    }

    private fun showImportDialog() {
        MaterialAlertDialogBuilder(requireContext())
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
            Toast.makeText(
                requireContext(),
                R.string.search_engine_import_empty,
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val appContext = requireContext().applicationContext
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
            val appContext = requireContext().applicationContext
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
}
