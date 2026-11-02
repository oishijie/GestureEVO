package io.github.lumiagg.gestureevo.settings

import android.content.Context
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.appcompat.app.AppCompatDelegate
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.settings.ui.showColorPickerDialog
import io.github.lumiagg.gestureevo.ui.compose.MiuixEvoTheme
import io.github.lumiagg.gestureevo.ui.miuix.MiuixCard
import io.github.lumiagg.gestureevo.ui.miuix.MiuixScreen
import io.github.lumiagg.gestureevo.utils.DynamicColorUtils
import io.github.lumiagg.gestureevo.utils.ThemeManager
import io.github.lumiagg.gestureevo.utils.icon.IconHelper
import io.github.lumiagg.gestureevo.utils.readKey
import io.github.lumiagg.gestureevo.utils.saveKey
import io.github.lumiagg.gestureevo.utils.settingsStore
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 外观设置。
 *
 * 三个分组：
 * 1. 主题与色彩：主题模式 / 动态取色（壁纸取色）/ 语言 / 全局图标圆角；
 * 2. 模糊渲染：一键总开关 + 9 个界面的独立开关；
 * 3. 界面外观：指针形状、大小、点击反馈，以及三处背景色。
 */
class AppearanceSettingsActivity : AppCompatActivity() {

    companion object {
        const val KEY_APP_LANGUAGE = "key_app_language"

        /** 图标包与应用图标覆盖：入口已移除，仍供 [IconHelper] 读取历史设置。 */
        const val KEY_ICON_PACK = "key_icon_pack"
        const val KEY_APP_ICON_OVERRIDES = "key_app_icon_overrides"

        const val KEY_ICON_IMAGE_ROUND_PERCENT = "key_global_icon_image_round_percent"
        const val KEY_WHEEL_BACKGROUND_COLOR = "key_wheel_background_color"
        const val KEY_WHEEL_BACKGROUND_BLUR = "key_wheel_background_blur"
        const val KEY_SEARCH_BACKGROUND_COLOR = "key_search_background_color"
        const val KEY_SEARCH_BACKGROUND_BLUR = "key_search_background_blur"
        const val KEY_SEARCH_CARD_BACKGROUND_BLUR = "key_search_card_background_blur"
        const val KEY_KEYBOARD_BACKGROUND_BLUR = "key_keyboard_background_blur"
        const val KEY_KEYBOARD_CARD_BACKGROUND_BLUR = "key_keyboard_card_background_blur"
        const val KEY_PIN_POOL_BACKGROUND_BLUR = "key_pin_pool_background_blur"
        const val KEY_PIN_POOL_CARD_BACKGROUND_BLUR = "key_pin_pool_card_background_blur"
        const val KEY_SCREEN_CONTENT_BACKGROUND_BLUR = "key_screen_content_background_blur"
        const val KEY_SCREEN_CONTENT_CARD_BACKGROUND_BLUR = "key_screen_content_card_background_blur"
        const val KEY_POINTER_COLOR = "key_pointer_color"
        const val KEY_POINTER_SHAPE = "key_pointer_shape"
        const val KEY_POINTER_SIZE = "key_pointer_size"
        const val KEY_POINTER_CLICK_FEEDBACK = "key_pointer_click_feedback"

        /** 指针形状与尺寸的单一取值来源。 */
        const val POINTER_SHAPE_DOT = "dot"
        const val POINTER_SHAPE_SMALL_DOT = "small_dot"
        const val POINTER_SHAPE_RING = "ring"
        const val POINTER_SHAPE_CROSS = "cross"
        const val POINTER_SHAPE_ARROW = "arrow"
        val POINTER_SHAPE_KEYS = listOf(
            POINTER_SHAPE_DOT,
            POINTER_SHAPE_SMALL_DOT,
            POINTER_SHAPE_RING,
            POINTER_SHAPE_CROSS,
            POINTER_SHAPE_ARROW,
        )
        const val POINTER_SIZE_DEFAULT = 30
        const val POINTER_SIZE_MIN = 16
        const val POINTER_SIZE_MAX = 48

        /** 模糊总开关：关闭时 9 个分区开关整体失效（默认开启，兼容无此键的旧数据）。 */
        const val KEY_BLUR_ENABLED = "key_blur_enabled"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiuixEvoTheme {
                AppearanceScreen(
                    activity = this,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                )
            }
        }
    }
}

/** 模糊分区开关：设置键 → (标题, 说明)。顺序与原 XML 一致。 */
private val blurItems = listOf(
    Triple(
        AppearanceSettingsActivity.KEY_WHEEL_BACKGROUND_BLUR,
        R.string.blur_item_wheel,
        R.string.blur_item_wheel_desc,
    ),
    Triple(
        AppearanceSettingsActivity.KEY_SEARCH_BACKGROUND_BLUR,
        R.string.blur_item_search,
        R.string.blur_item_search_desc,
    ),
    Triple(
        AppearanceSettingsActivity.KEY_SEARCH_CARD_BACKGROUND_BLUR,
        R.string.blur_item_search_card,
        R.string.blur_item_search_card_desc,
    ),
    Triple(
        AppearanceSettingsActivity.KEY_KEYBOARD_BACKGROUND_BLUR,
        R.string.blur_item_keyboard,
        R.string.blur_item_keyboard_desc,
    ),
    Triple(
        AppearanceSettingsActivity.KEY_KEYBOARD_CARD_BACKGROUND_BLUR,
        R.string.blur_item_keyboard_card,
        R.string.blur_item_keyboard_card_desc,
    ),
    Triple(
        AppearanceSettingsActivity.KEY_PIN_POOL_BACKGROUND_BLUR,
        R.string.blur_item_pin_pool,
        R.string.blur_item_pin_pool_desc,
    ),
    Triple(
        AppearanceSettingsActivity.KEY_PIN_POOL_CARD_BACKGROUND_BLUR,
        R.string.blur_item_pin_pool_card,
        R.string.blur_item_pin_pool_card_desc,
    ),
    Triple(
        AppearanceSettingsActivity.KEY_SCREEN_CONTENT_BACKGROUND_BLUR,
        R.string.blur_item_screen_content,
        R.string.blur_item_screen_content_desc,
    ),
    Triple(
        AppearanceSettingsActivity.KEY_SCREEN_CONTENT_CARD_BACKGROUND_BLUR,
        R.string.blur_item_screen_content_card,
        R.string.blur_item_screen_content_card_desc,
    ),
)

@Composable
private fun AppearanceScreen(activity: AppCompatActivity, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navigationPreferences = remember(context) {
        context.getSharedPreferences("home_navigation", Context.MODE_PRIVATE)
    }
    var floatingBottomBar by remember(navigationPreferences) {
        mutableStateOf(navigationPreferences.getBoolean("floating_bottom_bar", true))
    }

    var themeMode by remember { mutableIntStateOf(ThemeManager.themeMode) }
    var dynamicColor by remember { mutableStateOf(ThemeManager.dynamicColorEnabled) }
    var languageIndex by remember { mutableIntStateOf(0) }
    var iconRoundPercent by remember { mutableFloatStateOf(0f) }
    var blurMaster by remember { mutableStateOf(true) }
    val blurStates = remember { mutableStateMapOf<String, Boolean>() }
    var pointerShapeIndex by remember { mutableIntStateOf(0) }
    var pointerSize by remember { mutableIntStateOf(AppearanceSettingsActivity.POINTER_SIZE_DEFAULT) }
    var clickFeedback by remember { mutableStateOf(true) }
    var wheelColor by remember { mutableIntStateOf(0) }
    var searchColor by remember { mutableIntStateOf(0) }
    var pointerColor by remember { mutableIntStateOf(0) }

    val themeModeLabels = listOf(
        stringResource(R.string.theme_mode_system),
        stringResource(R.string.theme_mode_light),
        stringResource(R.string.theme_mode_dark),
    )
    val languageLabels = listOf(
        stringResource(R.string.appearance_settings_language_follow_system),
        stringResource(R.string.appearance_settings_language_chinese),
        stringResource(R.string.appearance_settings_language_english),
    )
    val languageTags = listOf("", "zh-CN", "en")
    val pointerShapeLabels = listOf(
        stringResource(R.string.appearance_settings_pointer_shape_dot),
        stringResource(R.string.appearance_settings_pointer_shape_small_dot),
        stringResource(R.string.appearance_settings_pointer_shape_ring),
        stringResource(R.string.appearance_settings_pointer_shape_cross),
        stringResource(R.string.appearance_settings_pointer_shape_arrow),
    )

    fun recreateForTheme() {
        ThemeManager.onThemePreferenceChanged()
        DynamicColorUtils.refreshSurfaceColors(activity.applicationContext)
        activity.recreate()
    }

    suspend fun refreshSwatches() {
        val savedWheel = context.settingsStore.readKey(
            AppearanceSettingsActivity.KEY_WHEEL_BACKGROUND_COLOR, 0
        )
        wheelColor = if (savedWheel != 0) {
            savedWheel
        } else {
            DynamicColorUtils.refreshSurfaceColors(context)
            DynamicColorUtils.surfaceContainerHighestColor
        }
        val savedSearch = context.settingsStore.readKey(
            AppearanceSettingsActivity.KEY_SEARCH_BACKGROUND_COLOR, 0
        )
        searchColor = if (savedSearch != 0) {
            savedSearch
        } else {
            DynamicColorUtils.refreshSurfaceColors(context)
            DynamicColorUtils.surfaceContainerHighestColor
        }
        val savedPointer = context.settingsStore.readKey(
            AppearanceSettingsActivity.KEY_POINTER_COLOR, 0
        )
        pointerColor = if (savedPointer == 0) DynamicColorUtils.primaryColor else savedPointer
    }

    LaunchedEffect(Unit) {
        themeMode = ThemeManager.themeMode
        dynamicColor = ThemeManager.dynamicColorEnabled
        languageIndex = languageTags.indexOf(
            context.settingsStore.readKey(AppearanceSettingsActivity.KEY_APP_LANGUAGE, "")
        ).takeIf { it >= 0 } ?: 0
        iconRoundPercent = context.settingsStore.readKey(
            AppearanceSettingsActivity.KEY_ICON_IMAGE_ROUND_PERCENT, 0f
        )
        blurMaster = context.settingsStore.readKey(AppearanceSettingsActivity.KEY_BLUR_ENABLED, true)
        blurItems.forEach { (key, _, _) ->
            blurStates[key] = context.settingsStore.readKey(key, false)
        }
        pointerShapeIndex = AppearanceSettingsActivity.POINTER_SHAPE_KEYS.indexOf(
            context.settingsStore.readKey(
                AppearanceSettingsActivity.KEY_POINTER_SHAPE,
                AppearanceSettingsActivity.POINTER_SHAPE_DOT,
            )
        ).takeIf { it >= 0 } ?: 0
        pointerSize = context.settingsStore.readKey(
            AppearanceSettingsActivity.KEY_POINTER_SIZE,
            AppearanceSettingsActivity.POINTER_SIZE_DEFAULT,
        ).coerceIn(AppearanceSettingsActivity.POINTER_SIZE_MIN, AppearanceSettingsActivity.POINTER_SIZE_MAX)
        clickFeedback = context.settingsStore.readKey(
            AppearanceSettingsActivity.KEY_POINTER_CLICK_FEEDBACK, true
        )
        refreshSwatches()
    }

    MiuixScreen(
        title = stringResource(R.string.appearance_settings_title),
        onBack = onBack,
    ) {
        // ---------------- 主题与色彩
        item {
            MiuixCard {
                OverlayDropdownPreference(
                    title = stringResource(R.string.theme_mode_title),
                    summary = stringResource(
                        if (ThemeManager.usesWallpaperColors) R.string.theme_summary_wallpaper
                        else R.string.theme_summary_default
                    ),
                    items = themeModeLabels,
                    selectedIndex = themeMode,
                    onSelectedIndexChange = { index ->
                        scope.launch {
                            ThemeManager.saveThemeMode(activity.applicationContext, index)
                            themeMode = index
                            recreateForTheme()
                        }
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.dynamic_color_title),
                    summary = stringResource(R.string.dynamic_color_title_desc),
                    checked = dynamicColor,
                    enabled = ThemeManager.wallpaperColorsSupported,
                    onCheckedChange = { checked ->
                        scope.launch {
                            ThemeManager.saveDynamicColor(activity.applicationContext, checked)
                            dynamicColor = checked
                            recreateForTheme()
                        }
                    },
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.appearance_settings_language_title),
                    items = languageLabels,
                    selectedIndex = languageIndex,
                    onSelectedIndexChange = { index ->
                        scope.launch {
                            languageIndex = index
                            context.settingsStore.saveKey(
                                AppearanceSettingsActivity.KEY_APP_LANGUAGE,
                                languageTags[index],
                            )
                            val locales = if (languageTags[index].isEmpty()) {
                                LocaleListCompat.getEmptyLocaleList()
                            } else {
                                LocaleListCompat.forLanguageTags(languageTags[index])
                            }
                            AppCompatDelegate.setApplicationLocales(locales)
                        }
                    },
                )
                SliderRow(
                    title = stringResource(R.string.appearance_settings_global_icon_corner),
                    valueText = stringResource(R.string.display_percent, (iconRoundPercent * 100).toInt()),
                    value = iconRoundPercent,
                    valueRange = 0f..1f,
                    onValueChange = { iconRoundPercent = it },
                    onValueChangeFinished = {
                        scope.launch {
                            context.settingsStore.saveKey(
                                AppearanceSettingsActivity.KEY_ICON_IMAGE_ROUND_PERCENT,
                                iconRoundPercent,
                            )
                            IconHelper.iconImageRoundPercent = iconRoundPercent
                        }
                    },
                )
            }
        }

        // ---------------- 模糊渲染
        item {
            MiuixCard {
                SwitchPreference(
                    title = stringResource(R.string.blur_master_title),
                    summary = stringResource(R.string.blur_master_title_desc),
                    checked = blurMaster,
                    onCheckedChange = { checked ->
                        scope.launch {
                            context.settingsStore.saveKey(
                                AppearanceSettingsActivity.KEY_BLUR_ENABLED, checked
                            )
                            blurMaster = checked
                        }
                    },
                )
                blurItems.forEach { (key, titleRes, summaryRes) ->
                    SwitchPreference(
                        title = stringResource(titleRes),
                        summary = stringResource(summaryRes),
                        checked = blurStates[key] ?: false,
                        enabled = blurMaster,
                        onCheckedChange = { checked ->
                            scope.launch {
                                context.settingsStore.saveKey(key, checked)
                                blurStates[key] = checked
                            }
                        },
                    )
                }
            }
        }

        // ---------------- 界面外观
        item {
            MiuixCard {
                SwitchPreference(
                    title = stringResource(R.string.floating_bottom_bar_title),
                    summary = stringResource(R.string.floating_bottom_bar_summary),
                    checked = floatingBottomBar,
                    onCheckedChange = { checked ->
                        navigationPreferences.edit().putBoolean("floating_bottom_bar", checked).apply()
                        floatingBottomBar = checked
                    },
                )
                OverlayDropdownPreference(
                    title = stringResource(R.string.appearance_settings_pointer_shape),
                    items = pointerShapeLabels,
                    selectedIndex = pointerShapeIndex,
                    onSelectedIndexChange = { index ->
                        scope.launch {
                            pointerShapeIndex = index
                            context.settingsStore.saveKey(
                                AppearanceSettingsActivity.KEY_POINTER_SHAPE,
                                AppearanceSettingsActivity.POINTER_SHAPE_KEYS[index],
                            )
                        }
                    },
                )
                SliderRow(
                    title = stringResource(R.string.appearance_settings_pointer_size),
                    valueText = stringResource(R.string.display_dp, pointerSize),
                    value = pointerSize.toFloat(),
                    valueRange = AppearanceSettingsActivity.POINTER_SIZE_MIN.toFloat()..
                        AppearanceSettingsActivity.POINTER_SIZE_MAX.toFloat(),
                    onValueChange = { pointerSize = it.toInt() },
                    onValueChangeFinished = {
                        scope.launch {
                            context.settingsStore.saveKey(
                                AppearanceSettingsActivity.KEY_POINTER_SIZE, pointerSize
                            )
                        }
                    },
                )
                SwitchPreference(
                    title = stringResource(R.string.appearance_settings_pointer_click_feedback),
                    summary = stringResource(R.string.appearance_settings_pointer_click_feedback_summary),
                    checked = clickFeedback,
                    onCheckedChange = { checked ->
                        scope.launch {
                            context.settingsStore.saveKey(
                                AppearanceSettingsActivity.KEY_POINTER_CLICK_FEEDBACK, checked
                            )
                            clickFeedback = checked
                        }
                    },
                )
                ColorRow(
                    title = stringResource(R.string.appearance_settings_pointer_color),
                    color = pointerColor,
                    onPick = {
                        scope.launch {
                            showColorPickerDialog(
                                activity = activity,
                                initialColor = pointerColor,
                                enableAlpha = true,
                                onColorSelected = { color ->
                                    scope.launch {
                                        context.settingsStore.saveKey(
                                            AppearanceSettingsActivity.KEY_POINTER_COLOR, color
                                        )
                                        refreshSwatches()
                                    }
                                },
                                onColorPreview = {},
                            )
                        }
                    },
                    onReset = {
                        scope.launch {
                            context.settingsStore.saveKey(
                                AppearanceSettingsActivity.KEY_POINTER_COLOR, 0
                            )
                            refreshSwatches()
                        }
                    },
                )
                ColorRow(
                    title = stringResource(R.string.appearance_settings_wheel_color),
                    color = wheelColor,
                    onPick = {
                        scope.launch {
                            showColorPickerDialog(
                                activity = activity,
                                initialColor = wheelColor,
                                enableAlpha = true,
                                onColorSelected = { color ->
                                    scope.launch {
                                        context.settingsStore.saveKey(
                                            AppearanceSettingsActivity.KEY_WHEEL_BACKGROUND_COLOR, color
                                        )
                                        refreshSwatches()
                                    }
                                },
                                onColorPreview = {},
                            )
                        }
                    },
                    onReset = {
                        scope.launch {
                            context.settingsStore.saveKey(
                                AppearanceSettingsActivity.KEY_WHEEL_BACKGROUND_COLOR, 0
                            )
                            refreshSwatches()
                        }
                    },
                )
                ColorRow(
                    title = stringResource(R.string.appearance_settings_search_color),
                    color = searchColor,
                    onPick = {
                        scope.launch {
                            showColorPickerDialog(
                                activity = activity,
                                initialColor = searchColor,
                                enableAlpha = true,
                                onColorSelected = { color ->
                                    scope.launch {
                                        context.settingsStore.saveKey(
                                            AppearanceSettingsActivity.KEY_SEARCH_BACKGROUND_COLOR, color
                                        )
                                        refreshSwatches()
                                    }
                                },
                                onColorPreview = {},
                            )
                        }
                    },
                    onReset = {
                        scope.launch {
                            context.settingsStore.saveKey(
                                AppearanceSettingsActivity.KEY_SEARCH_BACKGROUND_COLOR, 0
                            )
                            refreshSwatches()
                        }
                    },
                )
            }
        }
    }
}

/** 滑杆行：与 Miuix 卡片内其它行同高，标题在左、当前值在右。 */
@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = title, fontSize = 16.sp)
            Text(
                text = valueText,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
        )
    }
}

/** 颜色行：点击取色，长按恢复默认（与原实现一致）。 */
@Composable
private fun ColorRow(
    title: String,
    color: Int,
    onPick: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onPick, onLongClick = onReset)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.baseline_circle_24),
            contentDescription = null,
            tint = Color(color),
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(8.dp))
    }
}
