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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.gesture.config.GestureActionDataManager
import io.github.lumiagg.gestureevo.search.config.SearchEngine
import io.github.lumiagg.gestureevo.search.config.SearchEngineDataManager
import io.github.lumiagg.gestureevo.settings.ActionViewSettingsActivity
import io.github.lumiagg.gestureevo.settings.AdvancedSettingsActivity
import io.github.lumiagg.gestureevo.settings.AppearanceSettingsActivity
import io.github.lumiagg.gestureevo.settings.BackgroundStabilityActivity
import io.github.lumiagg.gestureevo.settings.DataBackActivity
import io.github.lumiagg.gestureevo.settings.DataStatsActivity
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
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.compose.RowSpec
import io.github.lumiagg.gestureevo.ui.miuix.FloatingBottomBar
import io.github.lumiagg.gestureevo.ui.home.FeatureGuideActivity
import io.github.lumiagg.gestureevo.ui.home.FeatureGuideActivity.Companion.GUIDE_OVERVIEW
import io.github.lumiagg.gestureevo.ui.home.FeatureGuideActivity.Companion.GUIDE_CONTROL
import io.github.lumiagg.gestureevo.ui.home.FeatureGuideActivity.Companion.GUIDE_CONTENT
import io.github.lumiagg.gestureevo.ui.home.FeatureGuideActivity.Companion.GUIDE_MANAGEMENT
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
            MiuixEvoTheme {
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

        const val TAB_OVERVIEW = 0
        const val TAB_CONTROL = 1
        const val TAB_CONTENT = 2
        const val TAB_MANAGEMENT = 3

        // Legacy names remain source-compatible for deep links and callers.
        const val TAB_GESTURE = TAB_CONTROL
        const val TAB_SCREEN = TAB_CONTENT
        const val TAB_SEARCH = TAB_CONTENT
        const val TAB_SETTINGS = TAB_MANAGEMENT

        /** 设置搜索锚点 → (tab, 真实卡片索引)。 */
        private val ANCHORS: Map<String, Pair<Int, Int>> = mapOf(
            "preferenceSetupStatus" to (TAB_OVERVIEW to 0),
            "preferenceAccessibility" to (TAB_OVERVIEW to 0),
            "preferenceSetupCheck" to (TAB_OVERVIEW to 0),
            "preferenceBackgroundStability" to (TAB_OVERVIEW to 0),
            "preferenceConfigTrigger" to (TAB_CONTROL to 0),
            "preferenceActionView" to (TAB_CONTROL to 1),
            "preferenceFreeform" to (TAB_CONTROL to 2),
            "root_inspire" to (TAB_CONTENT to 0),
            "menu_screen_recognition" to (TAB_CONTENT to 0),
            "menu_screen_share" to (TAB_CONTENT to 2),
            "menu_screen_pointer_keyboard" to (TAB_CONTENT to 3),
            "menu_screen_notification" to (TAB_CONTENT to 3),
            "root_search" to (TAB_CONTENT to 0),
            "menu_search_engines" to (TAB_CONTENT to 1),
            "menu_search_settings" to (TAB_CONTENT to 1),
            "menu_search_import" to (TAB_CONTENT to 3),
            "menu_search_export" to (TAB_CONTENT to 3),
            "preferenceAppearanceEntry" to (TAB_MANAGEMENT to 0),
            "preferenceData" to (TAB_MANAGEMENT to 2),
            "preferenceAdvanced" to (TAB_MANAGEMENT to 1),
            "preferenceAbout" to (TAB_MANAGEMENT to 3),
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
    R.string.ia_tab_overview,
    R.string.ia_tab_control,
    R.string.ia_tab_content,
    R.string.ia_tab_management,
)

private val GUIDE_BY_TAB = arrayOf(
    GUIDE_OVERVIEW, GUIDE_CONTROL, GUIDE_CONTENT, GUIDE_MANAGEMENT,
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
    val navigationPreferences = remember(context) { context.getSharedPreferences("home_navigation", Context.MODE_PRIVATE) }
    val floatingBottomBar = remember(navigationPreferences, refreshKey) {
        navigationPreferences.getBoolean("floating_bottom_bar", true)
    }
    val listStates = remember { List(4) { LazyListState() } }
    val safePendingTab = pendingTab?.coerceIn(HomeActivity.TAB_OVERVIEW, HomeActivity.TAB_MANAGEMENT)
    val scrollBehavior = MiuixScrollBehavior()

    LaunchedEffect(safePendingTab, pendingAnchor) {
        safePendingTab?.let {
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
        modifier = Modifier.fillMaxSize(),
        popupHost = { },
        contentWindowInsets = WindowInsets.systemBars.add(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(
                title = stringResource(TAB_TITLES[currentTab]),
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
            FloatingBottomBar(
                selectedIndex = currentTab,
                onItemSelected = { currentTab = it },
                floating = floatingBottomBar,
            )
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
                    .scrollEndHaptic()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = innerPadding.calculateBottomPadding() + 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                overscrollEffect = null,
            ) {
                when (tab) {
                    HomeActivity.TAB_OVERVIEW -> OverviewTabItems(status, context)
                    HomeActivity.TAB_CONTROL -> ControlTabItems(context)
                    HomeActivity.TAB_CONTENT -> ContentTabItems(status.engineCount, context, scope) { showImportDialog = true }
                    HomeActivity.TAB_MANAGEMENT -> ManagementTabItems(context)
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

@Composable
private fun SectionCard(rows: List<RowSpec>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        rows.forEach { row ->
            ArrowPreference(
                title = row.title,
                summary = row.subtitle,
                startAction = {
                    MiuixIcon(
                        imageVector = ImageVector.vectorResource(row.iconRes),
                        contentDescription = null,
                        modifier = Modifier.padding(end = 6.dp).size(24.dp),
                        tint = MiuixTheme.colorScheme.onBackground,
                    )
                },
                onClick = row.onClick,
            )
        }
    }
}

@Composable
private fun StatusCard(
    label: String,
    value: String,
    summary: String? = null,
    actionLabel: String? = null,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
        onClick = onClick,
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            MiuixText(text = value, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(1.dp))
            MiuixText(text = label, fontSize = 15.sp)
            if (summary != null) MiuixText(text = summary, fontSize = 15.sp)
            if (actionLabel != null) MiuixText(text = actionLabel, fontSize = 15.sp)
        }
    }
}

private fun anchorItemIndex(tab: Int, key: String): Int = when (key) {
    "preferenceAccessibility", "preferenceBackgroundStability" -> 0
    "preferenceData" -> 2
    "preferenceAdvanced" -> 1
    "preferenceAbout" -> 3
    "preferenceActionView" -> 1
    "preferenceFreeform" -> 2
    "menu_screen_share" -> 2
    "menu_screen_pointer_keyboard", "menu_screen_notification" -> 3
    "menu_search_import", "menu_search_export" -> 3
    else -> 0
}

private fun LazyListScope.OverviewTabItems(status: HomeStatus, context: Context) {
    item {
        SectionCard(listOf(
            RowSpec(
                iconRes = R.drawable.icon_service,
                title = stringResource(R.string.ia_overview_status),
                subtitle = stringResource(if (status.accessibility) R.string.home_badge_accessibility_on else R.string.home_badge_accessibility_off),
            ) {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            RowSpec(R.drawable.baseline_miscellaneous_services_24, stringResource(R.string.ia_background), stringResource(R.string.ia_not_connected)) {
                context.startActivity(Intent(context, BackgroundStabilityActivity::class.java))
            },
        ))
    }
    item {
        SectionCard(listOf(
            RowSpec(
                iconRes = R.drawable.ic_little_window,
                title = stringResource(R.string.ia_overview_config),
                subtitle = stringResource(R.string.home_badge_freeform_mode, status.freeformMode),
            ) {
                context.startActivity(Intent(context, FreeFormSettingsActivity::class.java))
            },
        ))
    }
    item {
        SectionCard(listOf(
            RowSpec(
                iconRes = R.drawable.baseline_pie_chart_24,
                title = stringResource(R.string.ia_overview_usage),
                subtitle = stringResource(R.string.data_stats_entry_summary),
            ) {
                context.startActivity(Intent(context, DataStatsActivity::class.java))
            },
        ))
    }
}
private fun LazyListScope.ControlTabItems(context: Context) {
    item { SectionCard(listOf(
        RowSpec(R.drawable.baseline_gesture_24, stringResource(R.string.ia_trigger_mapping), stringResource(R.string.home_summary_gesture)) { context.startActivity(Intent(context, FreeTriggerSettingsActivity::class.java)) }
    )) }
    item { SectionCard(listOf(RowSpec(R.drawable.baseline_pie_chart_24, stringResource(R.string.ia_wheel_layout), stringResource(R.string.home_summary_action_view)) { context.startActivity(Intent(context, ActionViewSettingsActivity::class.java)) })) }
    item { SectionCard(listOf(RowSpec(R.drawable.baseline_keyboard_24, stringResource(R.string.ia_pointer_keyboard), stringResource(R.string.home_menu_pointer_keyboard_summary)) { openInspire(context, intArrayOf(R.id.PointerCard, R.id.keyboardTypeCard)) })) }
    item { SectionCard(listOf(RowSpec(R.drawable.ic_little_window, stringResource(R.string.ia_window_behavior), stringResource(R.string.home_badge_freeform_mode, "")) { context.startActivity(Intent(context, FreeFormSettingsActivity::class.java)) })) }
}

private fun LazyListScope.ContentTabItems(engineCount: Int, context: Context, scope: CoroutineScope, onImport: () -> Unit) {
    item { SectionCard(listOf(RowSpec(R.drawable.baseline_qr_code_scanner_24, stringResource(R.string.ia_recognition_extract), stringResource(R.string.home_menu_recognition_title)) { openInspire(context, intArrayOf(R.id.screenContentSwitchCard)) })) }
    item { SectionCard(listOf(RowSpec(R.drawable.baseline_search_24, stringResource(R.string.ia_search_config), stringResource(R.string.home_badge_engine_count, engineCount)) { context.startActivity(SettingsSearchNavigator.deepLinkIntent(context, SearchEngineSettingsActivity::class.java, R.id.engineListCard)) })) }
    item { SectionCard(listOf(RowSpec(R.drawable.baseline_push_pin_24, stringResource(R.string.ia_pin_quick_send), stringResource(R.string.home_menu_pinpool_summary)) { openInspire(context, intArrayOf(R.id.pinPoolCard)) })) }
    item { SectionCard(listOf(RowSpec(R.drawable.ic_baseline_share_24, stringResource(R.string.ia_share_output), stringResource(R.string.home_menu_share_summary)) { openInspire(context, intArrayOf(R.id.defaultShareBehaviorCard, R.id.appSelectionCard)) }, RowSpec(R.drawable.content_paste_go_20px, stringResource(R.string.search_engine_settings_import), stringResource(R.string.search_engine_settings_import_summary), onClick = onImport), RowSpec(R.drawable.ic_baseline_content_copy_24, stringResource(R.string.search_engine_settings_export), stringResource(R.string.search_engine_settings_export_summary)) { exportSearchEngines(context, scope) })) }
}

private fun LazyListScope.ManagementTabItems(context: Context) {
    item { SectionCard(listOf(RowSpec(R.drawable.baseline_settings_24, stringResource(R.string.ia_interface_navigation), stringResource(R.string.home_summary_about)) { context.startActivity(Intent(context, AppearanceSettingsActivity::class.java)) })) }
    item { SectionCard(listOf(RowSpec(R.drawable.baseline_miscellaneous_services_24, stringResource(R.string.ia_external_auth), stringResource(R.string.home_summary_advanced)) { context.startActivity(Intent(context, AdvancedSettingsActivity::class.java)) })) }
    item { SectionCard(listOf(RowSpec(R.drawable.baseline_save_24, stringResource(R.string.ia_data_maintenance), stringResource(R.string.home_summary_data)) { context.startActivity(Intent(context, DataBackActivity::class.java)) })) }
    item { SectionCard(listOf(RowSpec(R.drawable.baseline_info_24, stringResource(R.string.settings_about_title), stringResource(R.string.home_summary_about)) { context.startActivity(Intent(context, AboutActivity::class.java)) })) }
}

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
        StatusCard(
            label = stringResource(R.string.hero_tools_label),
            value = stringResource(R.string.home_menu_recognition_title),
            summary = stringResource(R.string.hero_tools_summary),
            onClick = { openInspire(context, intArrayOf(R.id.screenContentSwitchCard)) },
        )
    }
    item {
        SectionCard(
            rows = listOf(
                RowSpec(
                    iconRes = R.drawable.ic_baseline_share_24,
                    title = stringResource(R.string.home_menu_share_title),
                    subtitle = stringResource(R.string.home_menu_share_summary),
                ) { openInspire(context, intArrayOf(R.id.defaultShareBehaviorCard, R.id.appSelectionCard)) },
                RowSpec(
                    iconRes = R.drawable.baseline_keyboard_24,
                    title = stringResource(R.string.home_menu_pointer_keyboard_title),
                    subtitle = stringResource(R.string.home_menu_pointer_keyboard_summary),
                ) { openInspire(context, intArrayOf(R.id.PointerCard, R.id.keyboardTypeCard)) },
                RowSpec(
                    iconRes = R.drawable.baseline_push_pin_24,
                    title = stringResource(R.string.home_menu_pinpool_title),
                    subtitle = stringResource(R.string.home_menu_pinpool_summary),
                ) { openInspire(context, intArrayOf(R.id.pinPoolCard)) },
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
        StatusCard(
            label = stringResource(R.string.hero_search_label),
            value = stringResource(R.string.home_badge_engine_count, engineCount),
            summary = stringResource(R.string.hero_search_summary),
            onClick = {
                context.startActivity(
                    SettingsSearchNavigator.deepLinkIntent(
                        context,
                        SearchEngineSettingsActivity::class.java,
                        R.id.engineListCard,
                    )
                )
            },
        )
    }
    item {
        SectionCard(
            rows = listOf(
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
                RowSpec(
                    iconRes = R.drawable.ic_baseline_cancel_24,
                    title = stringResource(R.string.hidden_search_results_title),
                ) {
                    context.startActivity(
                        SettingsSearchNavigator.deepLinkIntent(
                            context,
                            SearchEngineSettingsActivity::class.java,
                            R.id.hiddenResultsCard,
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

private fun LazyListScope.SettingsTabItems(context: Context) {
    item {
        StatusCard(
            label = stringResource(R.string.appearance_status_label),
            value = stringResource(
                when (ThemeManager.themeMode) {
                    ThemeManager.MODE_LIGHT -> R.string.theme_mode_light
                    ThemeManager.MODE_DARK -> R.string.theme_mode_dark
                    else -> R.string.theme_mode_system
                }
            ),
            summary = stringResource(
                if (ThemeManager.usesWallpaperColors) R.string.theme_summary_wallpaper
                else R.string.theme_summary_default
            ),
            onClick = {
                context.startActivity(Intent(context, AppearanceSettingsActivity::class.java))
            },
        )
    }
    item {
        SectionCard(
            rows = listOf(
                RowSpec(
                    iconRes = R.drawable.baseline_save_24,
                    title = stringResource(R.string.data_backup_title),
                    subtitle = stringResource(R.string.home_summary_data),
                ) {
                    context.startActivity(Intent(context, DataBackActivity::class.java))
                },
                RowSpec(
                    iconRes = R.drawable.baseline_pie_chart_24,
                    title = stringResource(R.string.data_stats_entry_title),
                    subtitle = stringResource(R.string.data_stats_entry_summary),
                ) {
                    context.startActivity(Intent(context, DataStatsActivity::class.java))
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
