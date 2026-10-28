package io.github.lumiagg.gestureevo.ui.demo

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.gesture.config.GestureActionDataManager
import io.github.lumiagg.gestureevo.settings.ActionViewSettingsActivity
import io.github.lumiagg.gestureevo.settings.AdvancedSettingsActivity
import io.github.lumiagg.gestureevo.settings.AppearanceSettingsActivity
import io.github.lumiagg.gestureevo.settings.BackgroundStabilityActivity
import io.github.lumiagg.gestureevo.settings.DataBackActivity
import io.github.lumiagg.gestureevo.settings.FreeFormSettingsActivity
import io.github.lumiagg.gestureevo.settings.FreeTriggerSettingsActivity
import io.github.lumiagg.gestureevo.settings.InspireSettingsActivity
import io.github.lumiagg.gestureevo.settings.SearchEngineSettingsActivity
import io.github.lumiagg.gestureevo.settings.SettingsSearchNavigator
import io.github.lumiagg.gestureevo.settings.SetupChecklist
import io.github.lumiagg.gestureevo.settings.evoAccessibilityServiceIsEnable
import io.github.lumiagg.gestureevo.settings.ui.AboutActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 首页重构 Demo B（Compose 实现）。
 *
 * 与 [HomeDemoViewActivity] 同一信息结构：折叠大标题 + 状态 hero 卡 +
 * 四组「组标题在外、行在卡内」列表 + M3 NavigationBar。
 * 差异在观感与手感：动态取色、渐变 hero、Compose 滚动与点击反馈。
 */
class HomeDemoComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DemoTheme {
                DemoHomeScreen()
            }
        }
    }
}

private data class DemoRow(
    val titleRes: Int,
    val subtitle: String?,
    val iconRes: Int,
    val onClick: () -> Unit,
)

@Composable
private fun DemoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) darkColorScheme() else lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DemoHomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var pending by remember { mutableIntStateOf(0) }
    var accessibility by remember { mutableStateOf(false) }
    var wheelCount by remember { mutableIntStateOf(0) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val status = withContext(Dispatchers.IO) {
            Triple(
                SetupChecklist.pendingCount(context),
                evoAccessibilityServiceIsEnable(context),
                runCatching {
                    GestureActionDataManager.readActionListTags(context).size
                }.getOrDefault(0),
            )
        }
        pending = status.first
        accessibility = status.second
        wheelCount = status.third
    }

    val openInspire: (IntArray) -> Unit = { cardIds ->
        context.startActivity(
            SettingsSearchNavigator.deepLinkIntent(
                context,
                InspireSettingsActivity::class.java,
                *cardIds,
            )
        )
    }

    val sections: List<Pair<Int, List<DemoRow>>> = listOf(
        R.string.home_tab_gesture to listOf(
            DemoRow(
                R.string.home_status_service_title,
                stringResource(
                    if (accessibility) R.string.home_badge_accessibility_on
                    else R.string.home_badge_accessibility_off
                ),
                R.drawable.icon_service,
            ) { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            DemoRow(R.string.settings_gesture_title, stringResource(R.string.home_summary_gesture), R.drawable.baseline_gesture_24) {
                context.startActivity(Intent(context, FreeTriggerSettingsActivity::class.java))
            },
            DemoRow(R.string.action_view_settings_title, stringResource(R.string.home_summary_action_view), R.drawable.baseline_pie_chart_24) {
                context.startActivity(Intent(context, ActionViewSettingsActivity::class.java))
            },
            DemoRow(R.string.freeform_settings_title, stringResource(R.string.home_summary_freeform), R.drawable.ic_little_window) {
                context.startActivity(Intent(context, FreeFormSettingsActivity::class.java))
            },
        ),
        R.string.home_tab_screen to listOf(
            DemoRow(R.string.home_menu_recognition_title, stringResource(R.string.home_menu_recognition_summary), R.drawable.baseline_qr_code_scanner_24) {
                openInspire(intArrayOf(R.id.screenContentSwitchCard))
            },
            DemoRow(R.string.home_menu_share_title, stringResource(R.string.home_menu_share_summary), R.drawable.ic_baseline_share_24) {
                openInspire(intArrayOf(R.id.defaultShareBehaviorCard, R.id.appSelectionCard))
            },
            DemoRow(R.string.home_menu_pointer_keyboard_title, stringResource(R.string.home_menu_pointer_keyboard_summary), R.drawable.baseline_keyboard_24) {
                openInspire(intArrayOf(R.id.PointerCard, R.id.pinPoolCard, R.id.keyboardTypeCard))
            },
            DemoRow(R.string.home_menu_notification_title, stringResource(R.string.home_menu_notification_summary), R.drawable.baseline_circle_notifications_24) {
                openInspire(intArrayOf(R.id.notificationCard))
            },
        ),
        R.string.home_tab_search to listOf(
            DemoRow(R.string.home_menu_engines_title, stringResource(R.string.home_menu_engines_summary), R.drawable.baseline_search_24) {
                context.startActivity(
                    SettingsSearchNavigator.deepLinkIntent(
                        context,
                        SearchEngineSettingsActivity::class.java,
                        R.id.engineListCard,
                    )
                )
            },
            DemoRow(R.string.home_menu_search_settings_title, stringResource(R.string.home_menu_search_settings_summary), R.drawable.baseline_settings_24) {
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
            DemoRow(R.string.demo_row_search_io, stringResource(R.string.search_engine_settings_import_summary), R.drawable.ic_baseline_content_copy_24) {
                context.startActivity(Intent(context, SearchEngineSettingsActivity::class.java))
            },
        ),
        R.string.home_tab_settings to listOf(
            DemoRow(R.string.home_status_theme_title, null, R.drawable.baseline_auto_awesome_24) {
                context.startActivity(Intent(context, AppearanceSettingsActivity::class.java))
            },
            DemoRow(
                R.string.setup_check_entry_title,
                if (pending > 0) stringResource(R.string.setup_check_entry_summary_pending, pending)
                else stringResource(R.string.setup_check_entry_summary_done),
                R.drawable.icon_lock,
            ) { context.startActivity(Intent(context, BackgroundStabilityActivity::class.java)) },
            DemoRow(R.string.data_backup_title, stringResource(R.string.home_summary_data), R.drawable.baseline_save_24) {
                context.startActivity(Intent(context, DataBackActivity::class.java))
            },
            DemoRow(R.string.advanced_settings_title, stringResource(R.string.home_summary_advanced), R.drawable.baseline_settings_24) {
                context.startActivity(Intent(context, AdvancedSettingsActivity::class.java))
            },
            DemoRow(R.string.settings_about_title, stringResource(R.string.home_summary_about), R.drawable.baseline_info_24) {
                context.startActivity(Intent(context, AboutActivity::class.java))
            },
        ),
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.app_name),
                        fontWeight = FontWeight.Bold,
                    )
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            val tabs = listOf(
                Triple(R.string.home_tab_gesture, R.drawable.baseline_gesture_24, 0),
                Triple(R.string.home_tab_screen, R.drawable.baseline_screenshot_24, 1),
                Triple(R.string.home_tab_search, R.drawable.baseline_search_24, 2),
                Triple(R.string.home_tab_settings, R.drawable.baseline_settings_24, 3),
            )
            NavigationBar {
                tabs.forEach { (labelRes, iconRes, index) ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            scope.launch {
                                listState.animateScrollToItem(1 + index * 2)
                            }
                        },
                        icon = { Icon(painterResource(iconRes), contentDescription = null) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                HeroCard(
                    pending = pending,
                    accessibility = accessibility,
                    wheelCount = wheelCount,
                ) {
                    context.startActivity(Intent(context, BackgroundStabilityActivity::class.java))
                }
            }
            sections.forEach { (titleRes, rows) ->
                item {
                    Text(
                        stringResource(titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
                    )
                }
                item { GroupCard(rows) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroCard(
    pending: Int,
    accessibility: Boolean,
    wheelCount: Int,
    onAction: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val gradient = remember(colors) {
        Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer))
    }
    Card(
        onClick = onAction,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(20.dp),
        ) {
            Text(
                stringResource(R.string.demo_hero_label),
                style = MaterialTheme.typography.labelLarge,
                color = colors.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (pending > 0) stringResource(R.string.demo_hero_pending, pending)
                else stringResource(R.string.demo_hero_done),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.home_badge_wheel_count, wheelCount) + " · " + stringResource(
                    if (accessibility) R.string.demo_hero_accessibility_on
                    else R.string.demo_hero_accessibility_off
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onPrimaryContainer.copy(alpha = 0.8f),
            )
            if (pending > 0) {
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = onAction) {
                    Text(stringResource(R.string.demo_hero_action))
                }
            }
        }
    }
}

@Composable
private fun GroupCard(rows: List<DemoRow>) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 66.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                DemoRowItem(row)
            }
        }
    }
}

@Composable
private fun DemoRowItem(row: DemoRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = row.onClick)
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(row.iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(row.titleRes),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            row.subtitle?.let { subtitle ->
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
