package io.github.lumiagg.gestureevo.settings

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.slider.Slider
import io.github.lumiagg.gestureevo.R
import io.github.lumiagg.gestureevo.databinding.ActivityAppearanceSettingsBinding
import io.github.lumiagg.gestureevo.settings.ui.installPageHeader
import io.github.lumiagg.gestureevo.settings.ui.showAnchoredSelectMenu
import io.github.lumiagg.gestureevo.settings.ui.showColorPickerDialog
import io.github.lumiagg.gestureevo.utils.DynamicColorUtils
import io.github.lumiagg.gestureevo.utils.ThemeManager
import io.github.lumiagg.gestureevo.utils.icon.IconHelper
import io.github.lumiagg.gestureevo.utils.readKey
import io.github.lumiagg.gestureevo.utils.saveKey
import io.github.lumiagg.gestureevo.utils.settingsStore
import kotlinx.coroutines.launch

/**
 * 外观设置。
 *
 * 三个分组：
 * 1. 主题与色彩：语言 / 主题模式 / 动态取色（壁纸取色）/ 全局图标圆角；
 * 2. 模糊渲染：一键总开关 + 9 个界面的独立开关；
 * 3. 界面外观：指针、轮盘、搜索的背景色。
 */
class AppearanceSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppearanceSettingsBinding

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

        /** 模糊总开关：关闭时 9 个分区开关整体失效（默认开启，兼容无此键的旧数据）。 */
        const val KEY_BLUR_ENABLED = "key_blur_enabled"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppearanceSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        // 深链收敛最先执行（视图树已可查，不等读库），避免首帧按布局默认状态
        // 渲染出全部分区卡，等协程读完库才收敛。
        SettingsSearchNavigator.focusAnchorIfNeeded(binding.root, intent)

        lifecycleScope.launch {
            initializeViews()
            setupListeners()
        }
    }

    private fun setupToolbar() {
        installPageHeader(this, binding.toolbar, binding.toolbarLayout, R.string.appearance_settings_title)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.toolbar.title = getString(R.string.appearance_settings_title)
    }

    private suspend fun initializeViews() {
        setupThemeModeRow()
        setupDynamicColorRow()
        setupLanguageRow()
        setupBlurRows()
        setupIconCornerSlider()
        refreshColorSwatches()
    }

    // ---------------------------------------------------------------- 主题与色彩

    private fun setupThemeModeRow() {
        val labels = themeModeLabels()
        binding.themeModeDropdownRow.setValue(
            labels.getOrElse(ThemeManager.themeMode) { labels.first() }
        )
    }

    private fun themeModeLabels() = listOf(
        getString(R.string.theme_mode_system),
        getString(R.string.theme_mode_light),
        getString(R.string.theme_mode_dark),
    )

    /** 动态取色：唯一配色来源。系统不支持壁纸取色时置灰并给出说明。 */
    private fun setupDynamicColorRow() = with(binding) {
        val supported = ThemeManager.wallpaperColorsSupported
        dynamicColorSwitch.isChecked = ThemeManager.dynamicColorEnabled
        dynamicColorSwitch.isEnabled = supported
        dynamicColorRow.isEnabled = supported
        dynamicColorHint.isVisible = !supported
    }

    private suspend fun setupLanguageRow() {
        val labels = listOf(
            getString(R.string.appearance_settings_language_follow_system),
            getString(R.string.appearance_settings_language_chinese),
            getString(R.string.appearance_settings_language_english),
        )
        val tags = listOf("", "zh-CN", "en")
        val savedTag = settingsStore.readKey(KEY_APP_LANGUAGE, "")
        var selected = tags.indexOf(savedTag).takeIf { it >= 0 } ?: 0
        binding.languageRow.setValue(labels[selected])
        binding.languageRow.onRowClick = {
            showAnchoredSelectMenu(binding.languageRow, labels, selected) { index ->
                selected = index
                binding.languageRow.setValue(labels[index])
                lifecycleScope.launch {
                    settingsStore.saveKey(KEY_APP_LANGUAGE, tags[index])
                    val locales = if (tags[index].isEmpty()) {
                        LocaleListCompat.getEmptyLocaleList()
                    } else {
                        LocaleListCompat.forLanguageTags(tags[index])
                    }
                    AppCompatDelegate.setApplicationLocales(locales)
                }
            }
        }
    }

    private suspend fun setupIconCornerSlider() {
        val savedPercent = settingsStore.readKey(KEY_ICON_IMAGE_ROUND_PERCENT, 0f)
        binding.iconImageRoundPercentSlider.value = savedPercent
        binding.iconImageRoundPercentValue.text =
            getString(R.string.display_percent, (savedPercent * 100).toInt())
    }

    // ---------------------------------------------------------------- 模糊渲染

    /** 9 个子开关与对应 binding 的配对表。 */
    private fun blurSwitches() = listOf(
        KEY_WHEEL_BACKGROUND_BLUR to binding.blurWheelSwitch,
        KEY_SEARCH_BACKGROUND_BLUR to binding.blurSearchSwitch,
        KEY_SEARCH_CARD_BACKGROUND_BLUR to binding.blurSearchCardSwitch,
        KEY_KEYBOARD_BACKGROUND_BLUR to binding.blurKeyboardSwitch,
        KEY_KEYBOARD_CARD_BACKGROUND_BLUR to binding.blurKeyboardCardSwitch,
        KEY_PIN_POOL_BACKGROUND_BLUR to binding.blurPinPoolSwitch,
        KEY_PIN_POOL_CARD_BACKGROUND_BLUR to binding.blurPinPoolCardSwitch,
        KEY_SCREEN_CONTENT_BACKGROUND_BLUR to binding.blurScreenContentSwitch,
        KEY_SCREEN_CONTENT_CARD_BACKGROUND_BLUR to binding.blurScreenContentCardSwitch,
    )

    /** 9 个分区行的容器，母开关关闭时整组置灰。 */
    private fun blurRows() = listOf(
        binding.blurWheelRow,
        binding.blurSearchRow,
        binding.blurSearchCardRow,
        binding.blurKeyboardRow,
        binding.blurKeyboardCardRow,
        binding.blurPinPoolRow,
        binding.blurPinPoolCardRow,
        binding.blurScreenContentRow,
        binding.blurScreenContentCardRow,
    )

    private suspend fun setupBlurRows() {
        blurSwitches().forEach { (key, switch) ->
            switch.isChecked = settingsStore.readKey(key, false)
            switch.setOnCheckedChangeListener { _, value ->
                lifecycleScope.launch {
                    settingsStore.saveKey(key, value)
                }
            }
        }
        val masterOn = settingsStore.readKey(KEY_BLUR_ENABLED, true)
        binding.blurMasterSwitch.isChecked = masterOn
        applyBlurRowsEnabled(masterOn)
    }

    /** 母开关关闭时整组置灰：用视觉表达「全部停用」，不再占用一行说明文字。 */
    private fun applyBlurRowsEnabled(enabled: Boolean) {
        blurRows().forEach { it.isEnabled = enabled }
    }

    // ---------------------------------------------------------------- 界面外观

    private suspend fun refreshColorSwatches() {
        binding.wheelColorBtn.setSwatchColor(resolveWheelBackgroundColor())
        binding.searchColorBtn.setSwatchColor(resolveSearchBackgroundColor())
        val pointer = settingsStore.readKey(KEY_POINTER_COLOR, 0)
        binding.pointerColorBtn.setSwatchColor(
            if (pointer == 0) DynamicColorUtils.primaryColor else pointer
        )
    }

    private suspend fun resolveWheelBackgroundColor(): Int {
        val savedColor = settingsStore.readKey(KEY_WHEEL_BACKGROUND_COLOR, 0)
        if (savedColor != 0) return savedColor
        DynamicColorUtils.refreshSurfaceColors(applicationContext)
        return DynamicColorUtils.surfaceContainerHighestColor
    }

    private suspend fun resolveSearchBackgroundColor(): Int {
        val savedColor = settingsStore.readKey(KEY_SEARCH_BACKGROUND_COLOR, 0)
        if (savedColor != 0) return savedColor
        DynamicColorUtils.refreshSurfaceColors(applicationContext)
        return DynamicColorUtils.surfaceContainerHighestColor
    }

    // ---------------------------------------------------------------- 监听器

    private fun setupListeners() {
        binding.iconImageRoundPercentSlider.addOnChangeListener { _, value, _ ->
            binding.iconImageRoundPercentValue.text =
                getString(R.string.display_percent, (value * 100).toInt())
        }
        binding.iconImageRoundPercentSlider.addOnSliderTouchListener(object :
            Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {}

            override fun onStopTrackingTouch(slider: Slider) {
                lifecycleScope.launch {
                    settingsStore.saveKey(KEY_ICON_IMAGE_ROUND_PERCENT, slider.value)
                    IconHelper.iconImageRoundPercent = slider.value
                }
            }
        })

        binding.themeModeDropdownRow.onRowClick = {
            val labels = themeModeLabels()
            showAnchoredSelectMenu(
                binding.themeModeDropdownRow,
                labels,
                ThemeManager.themeMode,
            ) { index ->
                lifecycleScope.launch {
                    ThemeManager.saveThemeMode(applicationContext, index)
                    binding.themeModeDropdownRow.setValue(labels[index])
                    applyThemeChangeAndRecreate()
                }
            }
        }

        binding.dynamicColorSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                ThemeManager.saveDynamicColor(applicationContext, isChecked)
                applyThemeChangeAndRecreate()
            }
        }

        binding.blurMasterSwitch.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                settingsStore.saveKey(KEY_BLUR_ENABLED, isChecked)
                applyBlurRowsEnabled(isChecked)
            }
        }

        binding.wheelColorBtn.setOnClickListener {
            lifecycleScope.launch {
                val initialColor = resolveWheelBackgroundColor()
                showColorPickerDialog(
                    activity = this@AppearanceSettingsActivity,
                    initialColor = initialColor,
                    enableAlpha = true,
                    onColorSelected = { color ->
                        lifecycleScope.launch {
                            settingsStore.saveKey(KEY_WHEEL_BACKGROUND_COLOR, color)
                            refreshColorSwatches()
                        }
                    },
                    onColorPreview = { }
                )
            }
        }
        binding.wheelColorBtn.setOnLongClickListener {
            lifecycleScope.launch {
                settingsStore.saveKey(KEY_WHEEL_BACKGROUND_COLOR, 0)
                refreshColorSwatches()
            }
            true
        }

        binding.searchColorBtn.setOnClickListener {
            lifecycleScope.launch {
                val initialColor = resolveSearchBackgroundColor()
                showColorPickerDialog(
                    activity = this@AppearanceSettingsActivity,
                    initialColor = initialColor,
                    enableAlpha = true,
                    onColorSelected = { color ->
                        lifecycleScope.launch {
                            settingsStore.saveKey(KEY_SEARCH_BACKGROUND_COLOR, color)
                            refreshColorSwatches()
                        }
                    },
                    onColorPreview = { }
                )
            }
        }
        binding.searchColorBtn.setOnLongClickListener {
            lifecycleScope.launch {
                settingsStore.saveKey(KEY_SEARCH_BACKGROUND_COLOR, 0)
                refreshColorSwatches()
            }
            true
        }

        binding.pointerColorBtn.setOnClickListener {
            lifecycleScope.launch {
                val savedColor = settingsStore.readKey(KEY_POINTER_COLOR, 0)
                val initialColor =
                    if (savedColor == 0) DynamicColorUtils.primaryColor else savedColor
                showColorPickerDialog(
                    activity = this@AppearanceSettingsActivity,
                    initialColor = initialColor,
                    enableAlpha = true,
                    onColorSelected = { color ->
                        lifecycleScope.launch {
                            settingsStore.saveKey(KEY_POINTER_COLOR, color)
                            refreshColorSwatches()
                        }
                    },
                    onColorPreview = { }
                )
            }
        }
        binding.pointerColorBtn.setOnLongClickListener {
            lifecycleScope.launch {
                settingsStore.saveKey(KEY_POINTER_COLOR, 0)
                refreshColorSwatches()
            }
            true
        }
    }

    /** 主题偏好变更：刷新夜间模式与发布色，并重建当前页使 XML 主题属性重新解析。 */
    private fun applyThemeChangeAndRecreate() {
        ThemeManager.onThemePreferenceChanged()
        DynamicColorUtils.refreshSurfaceColors(applicationContext)
        recreate()
    }
}
