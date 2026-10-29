package io.github.lumiagg.gestureevo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.gesture.config.GestureActionDataManager
import io.github.lumiagg.gestureevo.search.config.SearchEngine
import io.github.lumiagg.gestureevo.search.config.SearchEngineDataManager
import io.github.lumiagg.gestureevo.settings.ActionViewSettingsActivity
import io.github.lumiagg.gestureevo.settings.AdvancedSettingsActivity
import io.github.lumiagg.gestureevo.settings.AppearanceSettingsActivity
import io.github.lumiagg.gestureevo.settings.BackgroundStabilityActivity
import io.github.lumiagg.gestureevo.settings.DataBackActivity
import io.github.lumiagg.gestureevo.settings.FreeFormSettingsActivity
import io.github.lumiagg.gestureevo.settings.FreeFormSettingsActivity.Companion.KEY_USE_MI_WINDOW
import io.github.lumiagg.gestureevo.settings.FreeTriggerSettingsActivity
import io.github.lumiagg.gestureevo.settings.InspireSettingsActivity
import io.github.lumiagg.gestureevo.settings.SearchEngineSettingsActivity
import io.github.lumiagg.gestureevo.settings.SettingsSearchActivity
import io.github.lumiagg.gestureevo.settings.SettingsSearchNavigator
import io.github.lumiagg.gestureevo.settings.SetupChecklist
import io.github.lumiagg.gestureevo.settings.evoAccessibilityServiceIsEnable
import io.github.lumiagg.gestureevo.settings.ui.AboutActivity
import io.github.lumiagg.gestureevo.ui.compose.EvoTheme
import io.github.lumiagg.gestureevo.ui.compose.RowSpec
import io.github.lumiagg.gestureevo.ui.compose.SectionCard
import io.github.lumiagg.gestureevo.ui.compose.StatusCard
import io.github.lumiagg.gestureevo.ui.home.FeatureGuideActivity
import io.github.lumiagg.gestureevo.ui.home.FeatureGuideActivity.Companion.GUIDE_GESTURE
import io.github.lumiagg.gestureevo.ui.home.FeatureGuideActivity.Companion.GUIDE_SCREEN
import io.github.lumiagg.gestureevo.ui.home.FeatureGuideActivity.Companion.GUIDE_SEARCH
import io.github.lumiagg.gestureevo.ui.home.FeatureGuideActivity.Companion.GUIDE_SETTINGS
import io.github.lumiagg.gestureevo.utils.Rom
import io.github.lumiagg.gestureevo.utils.ThemeManager
import io.github.lumiagg.gestureevo.utils.readKey
import io.github.lumiagg.gestureevo.utils.settingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 应用主界面（Compose 版）：四个一级页面 + 页头搜索/引导 + M3 底栏。
 *
 * 结构与原 Preference 首页一一对应（状态卡 + 分组入口卡），深链协议不变：
 * [EXTRA_TARGET_TAB] 直达 tab；[SettingsSearchNavigator.EXTRA_TARGET_PREFERENCE_KEY]
 * 切 tab 并滚动到目标分组。
 */
class HomeActivity : ComponentActivity() {

    /** 深链待处理导航：tab 直达 / 带锚点的 tab 定位。 */
    private val pendingTab = mutableStateOf<Int?>(null)
    private val pendingAnchor = mutableStateOf<String?>(null)

    /** 生命周期刷新键：每次 onResume 重新读取徽章数据（权限可能被外部改动）。 */
    private val resumeKey = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNavigateIntent(intent)
        setContent {
            EvoTheme {
                HomeRoot(
                    pendingTab = pendingTab.value,
                    pendingAnchor = pendingAnchor.value,
                    refreshKey = resumeKey.value,
                    onTabConsumed = { pendingTab.value = null },
                    onAnchorConsumed = { pendingAnchor.value = null },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeKey.value++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNavigateIntent(intent)
    }

    private fun handleNavigateIntent(intent: Intent?) {
        val targetTab = intent?.getIntExtra(EXTRA_TARGET_TAB, -1) ?: -1
        if (targetTab >= 0) {
            intent?.removeExtra(EXTRA_TARGET_TAB)
            pendingTab.value = targetTab
            return
        }
        val key = intent?.getStringExtra(SettingsSearchNavigator.EXTRA_TARGET_PREFERENCE_KEY) ?: return
        intent?.removeExtra(SettingsSearchNavigator.EXTRA_TARGET_PREFERENCE_KEY)
        val tab = ANCHORS[key]?.first ?: return
        pendingTab.value = tab
        pendingAnchor.value = key
    }

    companion object {
        /** 直达一级页面：值为 [TAB_GESTURE] 至 [TAB_SETTINGS]。 */
        const val EXTRA_TARGET_TAB = "home_target_tab"

        const val TAB_GESTURE = 0
        const val TAB_SCREEN = 1
        const val TAB_SEARCH = 2
        const val TAB_SETTINGS = 3

        /** 设置搜索锚点 → (tab, 分组内条目序号)。 */
        private val ANCHORS: Map<String, Pair<Int, Int>> = mapOf(
            "preferenceSetupStatus" to (TAB_GESTURE to 0),
            "preferenceAccessibility" to (TAB_GESTURE to 1),
            "preferenceConfigTrigger" to (TAB_GESTURE to 1),
            "preferenceActionView" to (TAB_GESTURE to 1),
            "preferenceFreeform" to (TAB_GESTURE to 1),
            "root_inspire" to (TAB_SCREEN to 0),
            "menu_screen_recognition" to (TAB_SCREEN to 0),
            "menu_screen_share" to (TAB_SCREEN to 0),
            "menu_screen_pointer_keyboard" to (TAB_SCREEN to 0),
            "menu_screen_notification" to (TAB_SCREEN to 0),
            "root_search" to (TAB_SEARCH to 0),
            "menu_search_engines" to (TAB_SEARCH to 0),
            "menu_search_settings" to (TAB_SEARCH to 0),
            "menu_search_import" to (TAB_SEARCH to 0),
            "menu_search_export" to (TAB_SEARCH to 0),
            "preferenceAppearanceEntry" to (TAB_SETTINGS to 0),
            "preferenceSetupCheck" to (TAB_SETTINGS to 0),
            "preferenceBackgroundStability" to (TAB_SETTINGS to 0),
            "preferenceData" to (TAB_SETTINGS to 0),
            "preferenceAdvanced" to (TAB_SETTINGS to 0),
            "preferenceAbout" to (TAB_SETTINGS to 0),
        )

        fun tabForAnchor(key: String): Int? = ANCHORS[key]?.first
    }
}

private data class HomeStatus(
    val pending: Int = 0,
    val accessibility: Boolean = false,
    val wheelCount: Int = 0,
    val engineCount: Int = 0,
    val freeformMode: String = "",
)

private val TAB_TITLES = intArrayOf(
    R.string.home_title_gesture,
    R.string.home_title_screen,
    R.string.home_title_search,
    R.string.home_title_settings,
)

private val GUIDE_BY_TAB = arrayOf(
    GUIDE_GESTURE, GUIDE_SCREEN, GUIDE_SEARCH, GUIDE_SETTINGS,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeRoot(
    pendingTab: Int?,
    pendingAnchor: String?,
    refreshKey: Int,
    onTabConsumed: () -> Unit,
    onAnchorConsumed: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentTab by rememberSaveable { mutableIntStateOf(0) }
    var anchor by remember { mutableStateOf<String?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    val listStates = remember { List(4) { LazyListState() } }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    LaunchedEffect(pendingTab, pendingAnchor) {
        pendingTab?.let {
            currentTab = it
            onTabConsumed()
        }
        pendingAnchor?.let {
            anchor = it
            onAnchorConsumed()
        }
    }

    LaunchedEffect(anchor, currentTab) {
        anchor?.let { key ->
            val tab = HomeActivity.tabForAnchor(key)
            if (tab != null && tab == currentTab) {
                listStates[currentTab].animateScrollToItem(anchorItemIndex(tab, key))
            }
        }
    }

    val status by produceState(HomeStatus(), refreshKey) {
        value = withContext(Dispatchers.IO) {
            HomeStatus(
                pending = SetupChecklist.pendingCount(context),
                accessibility = evoAccessibilityServiceIsEnable(context),
                wheelCount = runCatching {
                    GestureActionDataManager.readActionListTags(context).size
                }.getOrDefault(0),
                engineCount = runCatching {
                    SearchEngineDataManager.readSearchEngineList(context).size
                }.getOrDefault(0),
                freeformMode = freeformModeLabel(context),
            )
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        stringResource(TAB_TITLES[currentTab]),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = {
                        context.startActivity(
                            FeatureGuideActivity.intent(context, GUIDE_BY_TAB[currentTab])
                        )
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_help_24),
                            contentDescription = stringResource(R.string.home_guide_title),
                        )
                    }
                    IconButton(onClick = {
                        context.startActivity(Intent(context, SettingsSearchActivity::class.java))
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.baseline_search_24),
                            contentDescription = stringResource(R.string.settings_search_hint),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            val tabs = listOf(
                Triple(R.string.home_tab_gesture, R.drawable.baseline_gesture_24, HomeActivity.TAB_GESTURE),
                Triple(R.string.home_tab_screen, R.drawable.baseline_screenshot_24, HomeActivity.TAB_SCREEN),
                Triple(R.string.home_tab_search, R.drawable.baseline_search_24, HomeActivity.TAB_SEARCH),
                Triple(R.string.home_tab_settings, R.drawable.baseline_settings_24, HomeActivity.TAB_SETTINGS),
            )
            NavigationBar {
                tabs.forEach { (labelRes, iconRes, index) ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        icon = { Icon(painterResource(iconRes), contentDescription = null) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Crossfade(
            targetState = currentTab,
            modifier = Modifier.fillMaxSize(),
            label = "home_tab",
        ) { tab ->
            LazyColumn(
                state = listStates[tab],
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (tab) {
                    HomeActivity.TAB_GESTURE -> GestureTabItems(status, context)
                    HomeActivity.TAB_SCREEN -> ScreenTabItems(context)
                    HomeActivity.TAB_SEARCH -> SearchTabItems(
                        status.engineCount,
                        context,
                        scope,
                        onImport = { showImportDialog = true },
                    )
                    else -> SettingsTabItems(status, context)
                }
            }
        }
    }

    if (showImportDialog) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text(stringResource(R.string.search_engine_import_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.search_engine_import_message))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    importSearchEngines(context, text, scope)
                }) { Text(stringResource(R.string.search_engine_import_action)) }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text(stringResource(R.string.search_engine_cancel))
                }
            },
        )
    }
}

private fun anchorItemIndex(tab: Int, key: String): Int =
    if (tab == HomeActivity.TAB_GESTURE && key != "preferenceSetupStatus") 1 else 0

private fun LazyListScope.GestureTabItems(status: HomeStatus, context: Context) {
    item {
        StatusCard(
            label = stringResource(R.string.setup_check_entry_title),
            value = if (status.pending > 0) {
                stringResource(R.string.demo_hero_pending, status.pending)
            } else {
                stringResource(R.string.demo_hero_done)
            },
            summary = stringResource(R.string.home_badge_wheel_count, status.wheelCount) + " · " +
                stringResource(
                    if (status.accessibility) R.string.demo_hero_accessibility_on
                    else R.string.demo_hero_accessibility_off
                ),
            actionLabel = if (status.pending > 0) {
                stringResource(R.string.setup_check_action_review)
            } else {
                null
            },
            onClick = {
                context.startActivity(Intent(context, BackgroundStabilityActivity::class.java))
            },
        )
    }
    item {
        SectionCard(
            rows = listOf(
                RowSpec(
                    iconRes = R.drawable.icon_service,
                    title = stringResource(R.string.home_status_service_title),
                    subtitle = stringResource(
                        if (status.accessibility) R.string.home_badge_accessibility_on
                        else R.string.home_badge_accessibility_off
                    ),
                ) {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                },
                RowSpec(
                    iconRes = R.drawable.baseline_gesture_24,
                    title = stringResource(R.string.settings_gesture_title),
                    subtitle = stringResource(R.string.home_summary_gesture),
                ) {
                    context.startActivity(Intent(context, FreeTriggerSettingsActivity::class.java))
                },
                RowSpec(
                    iconRes = R.drawable.baseline_pie_chart_24,
                    title = stringResource(R.string.action_view_settings_title),
                    subtitle = stringResource(R.string.home_summary_action_view),
                ) {
                    context.startActivity(Intent(context, ActionViewSettingsActivity::class.java))
                },
                RowSpec(
                    iconRes = R.drawable.ic_little_window,
                    title = stringResource(R.string.freeform_settings_title),
                    subtitle = stringResource(R.string.home_badge_freeform_mode, status.freeformMode),
                ) {
                    context.startActivity(Intent(context, FreeFormSettingsActivity::class.java))
                },
            )
        )
    }
}

private fun LazyListScope.ScreenTabItems(context: Context) {
    item {
        SectionCard(
            rows = listOf(
                RowSpec(
                    iconRes = R.drawable.baseline_qr_code_scanner_24,
                    title = stringResource(R.string.home_menu_recognition_title),
                    subtitle = stringResource(R.string.home_menu_recognition_summary),
                ) { openInspire(context, intArrayOf(R.id.screenContentSwitchCard)) },
                RowSpec(
                    iconRes = R.drawable.ic_baseline_share_24,
                    title = stringResource(R.string.home_menu_share_title),
                    subtitle = stringResource(R.string.home_menu_share_summary),
                ) { openInspire(context, intArrayOf(R.id.defaultShareBehaviorCard, R.id.appSelectionCard)) },
                RowSpec(
                    iconRes = R.drawable.baseline_keyboard_24,
                    title = stringResource(R.string.home_menu_pointer_keyboard_title),
                    subtitle = stringResource(R.string.home_menu_pointer_keyboard_summary),
                ) { openInspire(context, intArrayOf(R.id.PointerCard, R.id.pinPoolCard, R.id.keyboardTypeCard)) },
                RowSpec(
                    iconRes = R.drawable.baseline_circle_notifications_24,
                    title = stringResource(R.string.home_menu_notification_title),
                    subtitle = stringResource(R.string.home_menu_notification_summary),
                ) { openInspire(context, intArrayOf(R.id.notificationCard)) },
            )
        )
    }
}

private fun openInspire(context: Context, cardIds: IntArray) {
    context.startActivity(
        SettingsSearchNavigator.deepLinkIntent(
            context,
            InspireSettingsActivity::class.java,
            *cardIds,
        )
    )
}

private fun LazyListScope.SearchTabItems(
    engineCount: Int,
    context: Context,
    scope: CoroutineScope,
    onImport: () -> Unit,
) {
    item {
        SectionCard(
            rows = listOf(
                RowSpec(
                    iconRes = R.drawable.baseline_search_24,
                    title = stringResource(R.string.home_menu_engines_title),
                    subtitle = stringResource(R.string.home_badge_engine_count, engineCount),
                ) {
                    context.startActivity(
                        SettingsSearchNavigator.deepLinkIntent(
                            context,
                            SearchEngineSettingsActivity::class.java,
                            R.id.engineListCard,
                        )
                    )
                },
                RowSpec(
                    iconRes = R.drawable.baseline_settings_24,
                    title = stringResource(R.string.home_menu_search_settings_title),
                    subtitle = stringResource(R.string.home_menu_search_settings_summary),
                ) {
                    context.startActivity(
                        SettingsSearchNavigator.deepLinkIntent(
                            context,
                            SearchEngineSettingsActivity::class.java,
                            R.id.displayCard,
                            R.id.functionCard,
                            R.id.startupCard,
                        )
                    )
                },
            )
        )
    }
    item {
        SectionCard(
            rows = listOf(
                RowSpec(
                    iconRes = R.drawable.content_paste_go_20px,
                    title = stringResource(R.string.search_engine_settings_import),
                    subtitle = stringResource(R.string.search_engine_settings_import_summary),
                ) { onImport() },
                RowSpec(
                    iconRes = R.drawable.ic_baseline_content_copy_24,
                    title = stringResource(R.string.search_engine_settings_export),
                    subtitle = stringResource(R.string.search_engine_settings_export_summary),
                ) { exportSearchEngines(context, scope) },
            )
        )
    }
}

private fun LazyListScope.SettingsTabItems(status: HomeStatus, context: Context) {
    item {
        SectionCard(
            rows = listOf(
                RowSpec(
                    iconRes = R.drawable.baseline_auto_awesome_24,
                    title = stringResource(R.string.home_status_theme_title),
                    subtitle = stringResource(
                        R.string.home_summary_theme_entry,
                        stringResource(
                            if (ThemeManager.usesWallpaperColors) R.string.theme_summary_wallpaper
                            else R.string.theme_summary_default
                        )
                    ),
                ) {
                    context.startActivity(Intent(context, AppearanceSettingsActivity::class.java))
                },
                RowSpec(
                    iconRes = R.drawable.icon_lock,
                    title = stringResource(R.string.setup_check_entry_title),
                    subtitle = if (status.pending > 0) {
                        stringResource(R.string.setup_check_entry_summary_pending, status.pending)
                    } else {
                        stringResource(R.string.setup_check_entry_summary_done)
                    },
                ) {
                    context.startActivity(Intent(context, BackgroundStabilityActivity::class.java))
                },
                RowSpec(
                    iconRes = R.drawable.baseline_save_24,
                    title = stringResource(R.string.data_backup_title),
                    subtitle = stringResource(R.string.home_summary_data),
                ) {
                    context.startActivity(Intent(context, DataBackActivity::class.java))
                },
                RowSpec(
                    iconRes = R.drawable.baseline_settings_24,
                    title = stringResource(R.string.advanced_settings_title),
                    subtitle = stringResource(R.string.home_summary_advanced),
                ) {
                    context.startActivity(Intent(context, AdvancedSettingsActivity::class.java))
                },
                RowSpec(
                    iconRes = R.drawable.baseline_info_24,
                    title = stringResource(R.string.settings_about_title),
                    subtitle = stringResource(R.string.home_summary_about),
                ) {
                    context.startActivity(Intent(context, AboutActivity::class.java))
                },
            )
        )
    }
}

// ---------------------------------------------------------------- 搜索导入导出
// （逻辑与原 SearchHomeFragment / SearchEngineSettingsActivity 一致）

private fun importSearchEngines(context: Context, json: String, scope: CoroutineScope) {
    if (json.isEmpty()) {
        Toast.makeText(context, R.string.search_engine_import_empty, Toast.LENGTH_SHORT).show()
        return
    }
    scope.launch(Dispatchers.IO) {
        val appContext = context.applicationContext
        try {
            val engines = SearchEngine.toModelList(json)
            if (engines.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, R.string.search_engine_import_json_error, Toast.LENGTH_SHORT).show()
                }
                return@launch
            }
            SearchEngineDataManager.importSearchEngines(appContext, engines, replaceExisting = false)
            withContext(Dispatchers.Main) {
                Toast.makeText(appContext, R.string.search_engine_import_success, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    appContext,
                    appContext.getString(R.string.search_engine_import_failed, e.localizedMessage ?: ""),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}

private fun exportSearchEngines(context: Context, scope: CoroutineScope) {
    scope.launch(Dispatchers.IO) {
        val appContext = context.applicationContext
        val json = SearchEngineDataManager.exportSearchEnginesJson(appContext)
        withContext(Dispatchers.Main) {
            val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(
                ClipData.newPlainText(appContext.getString(R.string.search_engine_clipboard_label), json)
            )
            Toast.makeText(appContext, R.string.search_engine_clipboard_copied, Toast.LENGTH_SHORT).show()
        }
    }
}

/** 解析当前生效的小窗模式名（与 FreeFormSettingsActivity 下拉框的码表一致）。 */
private suspend fun freeformModeLabel(context: Context): String {
    val useMiWindow = context.settingsStore.readKey(KEY_USE_MI_WINDOW, false)
    val code = if (useMiWindow) MODE_SUNSHINE else Rom.getFreeFormCode(context)
    val labelRes = when (code) {
        CODE_NUBIA -> R.string.freeform_mode_6_nubia
        CODE_FLYME -> R.string.freeform_mode_5_flyme
        CODE_OPPO -> R.string.freeform_mode_2_oppo
        CODE_EMUI -> R.string.freeform_mode_3_hw
        CODE_VIVO -> R.string.freeform_mode_4_vivo_share
        MODE_SUNSHINE -> R.string.freeform_mode_7_sunshine
        else -> R.string.freeform_mode_1
    }
    return context.getString(labelRes)
}

/** 与 FreeFormSettingsActivity.setupDropdownMenu 的码表保持一致。 */
private const val MODE_SUNSHINE = -3
private const val CODE_NUBIA = -2
private const val CODE_FLYME = 11
private const val CODE_OPPO = 100
private const val CODE_EMUI = 102
private const val CODE_VIVO = 106
